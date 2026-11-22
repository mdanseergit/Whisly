package com.example.ai.stt

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.ai.audio.AudioProcessor
import com.example.ai.pipeline.AudioInput
import com.example.ai.registry.CapabilityRegistry
import com.example.domain.models.PipelineErrorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Result of one decode attempt. [confidence] is null only when the engine reports no probability. */
sealed interface SttDecode {
    data class Text(val text: String, val confidence: Float?, val engine: String) : SttDecode
    data class Failure(val error: PipelineErrorType, val detail: String = "") : SttDecode
}

/** Deterministic outcome of one push-to-talk session. */
sealed interface SttOutcome {
    data class Transcript(
        val text: String,
        val confidence: Float?,
        val engine: String,
        val latencyMs: Long,
        val languageCode: String
    ) : SttOutcome

    data class Failure(val error: PipelineErrorType, val detail: String = "") : SttOutcome
    data object Cancelled : SttOutcome
}

fun interface SttDecoder {
    suspend fun decode(pcm16: ShortArray, sampleRateHz: Int, languageCode: String): SttDecode
}

/**
 * Push-to-talk speech recognizer.
 *
 * This class owns exactly one microphone session at a time and is the only component that may
 * open/close [AudioProcessor] capture for the main and conversation screens.
 *
 * Truth rules enforced here:
 * 1. A transcript is returned only after a real decoder produced non-blank text from real audio.
 * 2. "Couldn't hear that" is returned only when captured audio was analysed and contained no speech.
 * 3. Missing model, unsupported language, microphone failure and inference failure each keep their
 *    own error type; they are never collapsed into a no-speech message.
 * 4. Spoken fillers are never deleted. The final transcript is the engine's own text.
 */
class WhislyOfflineSpeechRecognizer(
    private val context: Context,
    private val audioProcessor: AudioProcessor,
    private val decoder: SttDecoder? = null
) {
    companion object {
        private const val TAG = "WhislyOfflineSTT"
        const val RECOGNITION_TIMEOUT_MS = 30_000L
        private const val MIN_USABLE_DURATION_MS = 200L
        private const val DIGITAL_SILENCE_PEAK = 0.0015f
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _latestTranscript = MutableStateFlow<String?>(null)
    val latestTranscript: StateFlow<String?> = _latestTranscript.asStateFlow()

    private val _lastError = MutableStateFlow<PipelineErrorType?>(null)
    val lastError: StateFlow<PipelineErrorType?> = _lastError.asStateFlow()

    private val _lastOutcome = MutableStateFlow<SttOutcome?>(null)
    val lastOutcome: StateFlow<SttOutcome?> = _lastOutcome.asStateFlow()

    private var activeLanguageCode: String = "ta"

    /** Owns the capture job. Cancelled when the session ends so no reader outlives it. */
    private var captureScope: CoroutineScope? = null

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeInput: AudioInput? = null

    fun isAvailable(): Boolean = audioProcessor.hasMicrophoneHardware()

    /**
     * Opens the microphone. Returns null on success or the exact error that prevented capture.
     * Never starts a second capture session while one is running.
     */
    fun startListening(
        languageCode: String,
        onRms: (Float) -> Unit = {},
        onElapsed: (Long) -> Unit = {}
    ): PipelineErrorType? {
        if (_isListening.value) {
            // Idempotent: a second tap while listening is a stop request handled by the caller.
            return null
        }
        if (!audioProcessor.hasRecordPermission()) {
            return fail(PipelineErrorType.MIC_PERMISSION_DENIED)
        }
        if (!audioProcessor.hasMicrophoneHardware()) {
            return fail(PipelineErrorType.NO_MICROPHONE_AVAILABLE)
        }
        if (audioProcessor.isMicrophoneMuted()) {
            return fail(PipelineErrorType.MICROPHONE_MUTED)
        }

        val cap = CapabilityRegistry.getLanguage(languageCode)
        if (cap != null && !cap.canListen) {
            return fail(PipelineErrorType.UNSUPPORTED_SOURCE_LANGUAGE)
        }

        activeLanguageCode = languageCode
        cancelled = false
        _latestTranscript.value = null
        _lastError.value = null
        _lastOutcome.value = null
        activeInput = null

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        captureScope = scope
        val status = audioProcessor.startRecordingDetailed(scope) { level, elapsedMs ->
            onRms(level)
            onElapsed(elapsedMs)
        }

        return when (status) {
            AudioProcessor.MicStartStatus.STARTED -> {
                _isListening.value = true
                null
            }
            AudioProcessor.MicStartStatus.ALREADY_RUNNING -> {
                _isListening.value = true
                null
            }
            AudioProcessor.MicStartStatus.NO_PERMISSION ->
                fail(PipelineErrorType.MIC_PERMISSION_DENIED)
            AudioProcessor.MicStartStatus.NO_HARDWARE ->
                fail(PipelineErrorType.NO_MICROPHONE_AVAILABLE)
            AudioProcessor.MicStartStatus.DEAD_OBJECT,
            AudioProcessor.MicStartStatus.INIT_FAILED ->
                fail(PipelineErrorType.MIC_INIT_FAILED)
        }.also { if (it != null) releaseCaptureScope() }
    }

    private fun releaseCaptureScope() {
        captureScope?.cancel()
        captureScope = null
    }

    /**
     * Stops capture and performs VAD + decoding exactly once. Suspends until an outcome exists.
     */
    suspend fun stopAndRecognize(): SttOutcome {
        if (!_isListening.value) {
            return _lastOutcome.value ?: SttOutcome.Cancelled
        }
        _isListening.value = false

        val audio = withContext(Dispatchers.IO) { audioProcessor.stopRecording() }
        releaseCaptureScope()
        activeInput = audio

        if (cancelled) {
            audioProcessor.resetWaveform()
            return SttOutcome.Cancelled
        }

        val outcome = withContext(Dispatchers.Default) {
            withTimeoutOrNull(RECOGNITION_TIMEOUT_MS) { evaluate(audio) }
                ?: SttOutcome.Failure(PipelineErrorType.STT_TIMEOUT, "decode exceeded ${RECOGNITION_TIMEOUT_MS}ms")
        }

        audioProcessor.resetWaveform()
        _lastOutcome.value = outcome
        when (outcome) {
            is SttOutcome.Transcript -> {
                _latestTranscript.value = outcome.text
                _lastError.value = null
            }
            is SttOutcome.Failure -> {
                _lastError.value = outcome.error
                _latestTranscript.value = null
            }
            SttOutcome.Cancelled -> Unit
        }
        return outcome
    }

    /** Aborts the session without producing a transcript or an error banner. */
    fun cancel() {
        cancelled = true
        _isListening.value = false
        audioProcessor.stopRecording()
        audioProcessor.resetWaveform()
        _latestTranscript.value = null
    }

    fun lastDiagnostics(): AudioInput? = activeInput

    /** DEBUG only: the last captured audio as a standard 16 kHz mono WAV. */
    fun debugWavFile(): File? =
        if (BuildConfig.DEBUG) File(context.cacheDir, "debug_last_recording.wav") else null

    private fun fail(error: PipelineErrorType): PipelineErrorType {
        _lastError.value = error
        _lastOutcome.value = SttOutcome.Failure(error)
        return error
    }

    private suspend fun evaluate(audio: AudioInput): SttOutcome {
        val startMs = System.currentTimeMillis()

        if (BuildConfig.DEBUG) {
            saveDebugRecording(audio.pcm16Samples, audio.sampleRateHz)
            Log.d(
                TAG,
                "VAD: detected=${audio.vadSpeechDetected} prob=${audio.vadSpeechProbability} " +
                    "samples=${audio.pcm16Samples.size} voicedMs=${audio.voicedDurationMs} " +
                    "durMs=${audio.durationMs} peak=${audio.peakAmplitude} rms=${audio.rmsEnergy} " +
                    "clip=${audio.clippingDetected} quiet=${audio.tooQuietDetected} " +
                    "allZero=${audio.isAllZeroStream} segment=${audio.voicedSegmentPcm16.size} " +
                    "source=${audio.audioSource}"
            )
        }

        // 1. Hardware-level failures are never reported as "no speech".
        if (audio.pcm16Samples.isEmpty()) {
            return SttOutcome.Failure(
                if (audio.durationMs < MIN_USABLE_DURATION_MS) {
                    PipelineErrorType.SPEECH_NOT_DETECTED
                } else {
                    PipelineErrorType.MIC_INIT_FAILED
                },
                "no samples captured"
            )
        }
        if (audio.isAllZeroStream) {
            return SttOutcome.Failure(PipelineErrorType.MICROPHONE_MUTED, "all-zero PCM stream")
        }
        if (audio.durationMs in 1 until MIN_USABLE_DURATION_MS) {
            return SttOutcome.Failure(PipelineErrorType.SPEECH_NOT_DETECTED, "capture too short")
        }
        if (audio.clippingDetected) {
            return SttOutcome.Failure(PipelineErrorType.AUDIO_CLIPPING, "peak=${audio.peakAmplitude}")
        }

        // 2. Real audio was analysed: only now may we report silence.
        if (!audio.vadSpeechDetected) {
            val err = if (audio.peakAmplitude < DIGITAL_SILENCE_PEAK) {
                PipelineErrorType.AUDIO_TOO_QUIET
            } else {
                PipelineErrorType.SPEECH_NOT_DETECTED
            }
            return SttOutcome.Failure(err, "vad=false peak=${audio.peakAmplitude}")
        }

        // 3. Speech exists. A missing engine is a model problem, not a hearing problem.
        val activeDecoder = decoder
            ?: return SttOutcome.Failure(PipelineErrorType.STT_MODEL_MISSING, "no decoder installed")

        val segment = if (audio.voicedSegmentPcm16.isNotEmpty()) {
            audio.voicedSegmentPcm16
        } else {
            audio.pcm16Samples
        }

        val decoded = try {
            activeDecoder.decode(segment, audio.sampleRateHz, activeLanguageCode)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            return SttOutcome.Failure(PipelineErrorType.STT_FAILURE, t.javaClass.simpleName)
        }

        return when (decoded) {
            is SttDecode.Failure -> SttOutcome.Failure(decoded.error, decoded.detail)
            is SttDecode.Text -> {
                val text = decoded.text.trim()
                val confidence = decoded.confidence
                when {
                    text.isEmpty() ->
                        SttOutcome.Failure(PipelineErrorType.STT_FAILURE, "engine returned blank text")
                    confidence != null && confidence < AudioProcessor.MIN_STT_CONFIDENCE ->
                        SttOutcome.Failure(
                            PipelineErrorType.STT_LOW_CONFIDENCE,
                            "confidence=$confidence"
                        )
                    else -> SttOutcome.Transcript(
                        text = text,
                        confidence = confidence,
                        engine = decoded.engine,
                        latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                        languageCode = activeLanguageCode
                    )
                }
            }
        }
    }

    private fun saveDebugRecording(pcm16: ShortArray, sampleRateHz: Int) {
        if (pcm16.isEmpty()) return
        runCatching {
            val wavFile = File(context.cacheDir, "debug_last_recording.wav")
            FileOutputStream(wavFile).use { fos ->
                val byteCount = pcm16.size * 2
                val totalDataLen = byteCount + 36
                val byteRate = sampleRateHz * 2

                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(totalDataLen)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16)
                header.putShort(1)
                header.putShort(1)
                header.putInt(sampleRateHz)
                header.putInt(byteRate)
                header.putShort(2)
                header.putShort(16)
                header.put("data".toByteArray())
                header.putInt(byteCount)

                fos.write(header.array())

                val dataBuffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
                for (s in pcm16) dataBuffer.putShort(s)
                fos.write(dataBuffer.array())
            }
        }
    }
}
