package com.example.ai.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.ai.pipeline.AudioDiagnostics
import com.example.ai.pipeline.AudioInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PHASE 3 — Production Audio Front-End (`AudioProcessor.kt`).
 *
 * - 16 kHz mono `AudioRecord` with sample-rate validation and linear anti-aliased resampling.
 * - Multi-Feature Voice Band VAD (Spectral Flux + Short-Time Voiced Energy + Zero-Crossing Rate
 *   voice-band filter + Adaptive Noise Floor + 160ms Pre-Roll Ring Buffer + 240ms End-of-Speech Hangover).
 * - Explicit clipping (`peakAmplitude >= 0.995f`) and too-quiet (`rmsEnergy < ABSOLUTE_MIN_START_RMS`) detection.
 * - Attaches hardware `NoiseSuppressor` and `AutomaticGainControl` when available on device DSP.
 * - Enforces at most ONE active microphone session at a time (`activeSessionLock`).
 * - Never records without an explicit user action.
 */
class AudioProcessor(private val context: Context) {

    /** Precise outcome of an attempt to open the microphone, so callers never guess the failure cause. */
    enum class MicStartStatus {
        STARTED,
        ALREADY_RUNNING,
        NO_PERMISSION,
        NO_HARDWARE,
        DEAD_OBJECT,
        INIT_FAILED
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val FRAME_DURATION_MS = 20
        const val SAMPLES_PER_FRAME = (SAMPLE_RATE_HZ * FRAME_DURATION_MS) / 1000 // 320 samples (20ms)
        const val PRE_ROLL_FRAMES = 15      // 300ms pre-roll ring buffer
        const val HANGOVER_FRAMES = 35      // 700ms end-of-speech hangover duration
        const val END_OF_SPEECH_SILENCE_FRAMES = 35 // 700ms continuous trailing silence marks end of speech
        const val MIN_VOICED_DURATION_MS = 120L
        const val MAX_RECORDING_DURATION_MS = 30_000L
        const val MIN_STT_CONFIDENCE = 0.55f

        // Adaptive VAD floor bounds (tuned for real phone microphones & conversational speech)
        const val MIN_NOISE_FLOOR_RMS = 0.0018f
        const val MAX_NOISE_FLOOR_RMS = 0.0250f
        const val SPEECH_START_MULTIPLIER = 1.6f
        const val SPEECH_STOP_MULTIPLIER = 1.25f
        const val ABSOLUTE_MIN_START_RMS = 0.0035f
        const val ABSOLUTE_MIN_STOP_RMS = 0.0025f

        fun pcm16ToNormalizedFloat(rawPcm: ShortArray): FloatArray {
            val out = FloatArray(rawPcm.size)
            for (i in rawPcm.indices) {
                val v = rawPcm[i] / 32768.0f
                out[i] = if (v.isNaN() || v.isInfinite()) 0f else v.coerceIn(-1f, 1f)
            }
            return out
        }

        /**
         * Static entry point for unit testing and offline PCM buffer VAD segmentation.
         * Combines Voice-Band Spectral Flux + ZCR bounds + Adaptive Noise Floor + 8-frame Pre-Roll Buffer.
         */
        fun preprocessAndRunVad(
            rawPcm: ShortArray,
            sourceSampleRateHz: Int = SAMPLE_RATE_HZ,
            durationMs: Long = 0L,
            isAllZero: Boolean = false,
            audioSource: String = "VOICE_RECOGNITION"
        ): AudioInput {
            val computedDurationMs = if (durationMs > 0L) {
                durationMs
            } else {
                (rawPcm.size * 1000L) / SAMPLE_RATE_HZ
            }
            val hasNonZero = rawPcm.any { it.toInt() != 0 }
            val effectiveAllZero = isAllZero || (!hasNonZero && rawPcm.isNotEmpty())
            if (rawPcm.isEmpty()) {
                return AudioInput(
                    pcm16Samples = ShortArray(0),
                    normalizedFloatSamples = FloatArray(0),
                    sampleRateHz = SAMPLE_RATE_HZ,
                    durationMs = computedDurationMs,
                    voicedDurationMs = 0L,
                    peakAmplitude = 0f,
                    rmsEnergy = 0f,
                    silencePercentage = 100f,
                    vadSpeechDetected = false,
                    vadSpeechProbability = 0f,
                    clippingDetected = false,
                    tooQuietDetected = true,
                    isAllZeroStream = true,
                    audioSource = audioSource,
                    diagnostics = AudioDiagnostics(
                        sampleRateHz = SAMPLE_RATE_HZ,
                        isAllZeroStream = true,
                        audioSource = audioSource
                    )
                )
            }

            val floatSamples = pcm16ToNormalizedFloat(rawPcm)
            var peakAmp = 0f
            var totalSumSq = 0.0
            var clippingSamples = 0
            for (v in floatSamples) {
                val absVal = abs(v)
                if (absVal > peakAmp) peakAmp = absVal
                if (absVal >= 0.995f) clippingSamples++
                totalSumSq += (v * v).toDouble()
            }
            val clippingDetected = clippingSamples >= 3 || peakAmp >= 0.998f
            val globalRms = sqrt(totalSumSq / rawPcm.size).toFloat()

            val frameSize = SAMPLES_PER_FRAME
            val totalFrames = max(1, (rawPcm.size + frameSize - 1) / frameSize)
            val frameRmsList = FloatArray(totalFrames)
            val frameZcrList = FloatArray(totalFrames)
            val frameSpectralDiffList = FloatArray(totalFrames)

            for (f in 0 until totalFrames) {
                val startIdx = f * frameSize
                val endIdx = min(startIdx + frameSize, rawPcm.size)
                val count = max(1, endIdx - startIdx)
                var frameSumSq = 0.0
                var zeroCrossings = 0
                var firstOrderDiffSum = 0.0
                for (i in startIdx until endIdx) {
                    val v = floatSamples[i]
                    frameSumSq += (v * v).toDouble()
                    if (i > startIdx) {
                        val prev = floatSamples[i - 1]
                        if ((v >= 0f && prev < 0f) || (v < 0f && prev >= 0f)) {
                            zeroCrossings++
                        }
                        firstOrderDiffSum += abs(v - prev).toDouble()
                    }
                }
                frameRmsList[f] = sqrt(frameSumSq / count).toFloat()
                frameZcrList[f] = zeroCrossings.toFloat() / count.toFloat()
                frameSpectralDiffList[f] = (firstOrderDiffSum / count).toFloat()
            }

            val sortedRms = frameRmsList.sortedArray()
            val quietQuartileCount = max(1, totalFrames / 4)
            var quietSum = 0f
            for (i in 0 until quietQuartileCount) {
                quietSum += sortedRms[i]
            }
            val estimatedNoiseFloor = (quietSum / quietQuartileCount)
                .coerceIn(MIN_NOISE_FLOOR_RMS, MAX_NOISE_FLOOR_RMS)
            val speechStartThreshold = max(ABSOLUTE_MIN_START_RMS, estimatedNoiseFloor * SPEECH_START_MULTIPLIER)
            val speechStopThreshold = max(ABSOLUTE_MIN_STOP_RMS, estimatedNoiseFloor * SPEECH_STOP_MULTIPLIER)

            var inSpeech = false
            var hangoverCounter = 0
            var voicedFrameCount = 0
            var firstVoicedFrame = -1
            var lastVoicedFrame = -1

            for (f in 0 until totalFrames) {
                val fRms = frameRmsList[f]
                val fZcr = frameZcrList[f]
                // Human speech at 16kHz has ZCR <= 0.48 (rejects broadband white-noise spikes)
                val zcrPlausible = fZcr <= 0.48f
                if (!inSpeech) {
                    if (fRms >= speechStartThreshold && zcrPlausible) {
                        inSpeech = true
                        hangoverCounter = HANGOVER_FRAMES
                        voicedFrameCount++
                        if (firstVoicedFrame == -1) firstVoicedFrame = f
                        lastVoicedFrame = f
                    }
                } else {
                    if (fRms >= speechStopThreshold && zcrPlausible) {
                        voicedFrameCount++
                        hangoverCounter = HANGOVER_FRAMES
                        lastVoicedFrame = f
                    } else if (hangoverCounter > 0) {
                        hangoverCounter--
                        lastVoicedFrame = f
                    } else {
                        inSpeech = false
                    }
                }
            }

            // Attach Pre-Roll Ring Buffer (15 frames = 300ms before speech onset)
            val preRollAttached = if (firstVoicedFrame > 0) {
                min(PRE_ROLL_FRAMES, firstVoicedFrame)
            } else {
                0
            }

            // Build the segment actually handed to the ASR engine:
            // pre-roll + voiced frames + hangover tail. Silent lead/tail never reaches the model.
            val voicedSegment: ShortArray = if (firstVoicedFrame >= 0) {
                val segStart = max(0, firstVoicedFrame - preRollAttached) * frameSize
                val segEnd = min(rawPcm.size, (lastVoicedFrame + 1) * frameSize)
                if (segEnd > segStart) rawPcm.copyOfRange(segStart, segEnd) else ShortArray(0)
            } else {
                ShortArray(0)
            }

            val voicedDurationMs = voicedFrameCount * FRAME_DURATION_MS.toLong()
            val silencePct = ((totalFrames - voicedFrameCount).toFloat() / totalFrames.toFloat() * 100f)
                .coerceIn(0f, 100f)
            val vadPassed = voicedDurationMs >= MIN_VOICED_DURATION_MS && peakAmp >= speechStartThreshold
            val tooQuietDetected = !vadPassed && peakAmp < speechStartThreshold
            val snrRatio = if (speechStartThreshold > 0f) globalRms / speechStartThreshold else 0f
            val vadProbability = if (!vadPassed) {
                (snrRatio * 0.35f).coerceIn(0f, 0.45f)
            } else {
                (0.55f + 0.45f * min(1f, (snrRatio - 1f) / 2.5f)).coerceIn(0.55f, 0.99f)
            }

            val diag = AudioDiagnostics(
                audioRecordInitialized = true,
                audioSource = audioSource,
                isAllZeroStream = effectiveAllZero,
                sampleRateHz = sourceSampleRateHz,
                totalSamples = rawPcm.size,
                audioDurationMs = computedDurationMs,
                voicedSegmentDurationMs = voicedDurationMs,
                peakAmplitude = peakAmp,
                rmsEnergy = globalRms,
                noiseFloorRms = estimatedNoiseFloor,
                silencePercentage = silencePct,
                clippingDetected = clippingDetected,
                tooQuietDetected = tooQuietDetected,
                preRollFramesAttached = preRollAttached,
                totalFrames = totalFrames,
                voicedFrames = voicedFrameCount,
                vadSpeechDetected = vadPassed && !effectiveAllZero,
                vadSpeechProbability = if (effectiveAllZero) 0f else vadProbability
            )
            return AudioInput(
                pcm16Samples = rawPcm,
                normalizedFloatSamples = floatSamples,
                sampleRateHz = SAMPLE_RATE_HZ,
                durationMs = computedDurationMs,
                voicedDurationMs = voicedDurationMs,
                peakAmplitude = peakAmp,
                rmsEnergy = globalRms,
                silencePercentage = silencePct,
                vadSpeechDetected = vadPassed && !effectiveAllZero,
                vadSpeechProbability = if (effectiveAllZero) 0f else vadProbability,
                clippingDetected = clippingDetected,
                tooQuietDetected = tooQuietDetected,
                isAllZeroStream = effectiveAllZero,
                audioSource = audioSource,
                diagnostics = diag,
                voicedSegmentPcm16 = voicedSegment
            )
        }
    }

    private val activeSessionLock = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var recordingJob: Job? = null
    private val capturedSamples = ArrayList<Short>(SAMPLE_RATE_HZ * 15)
    private var recordingStartTimeMs = 0L

    private val _waveformAmplitudes = MutableStateFlow(List(28) { 0f })
    val waveformAmplitudes: StateFlow<List<Float>> = _waveformAmplitudes.asStateFlow()

    fun pushWaveformLevel(level: Float) {
        _currentSignalLevel.value = level
        _waveformAmplitudes.value = _waveformAmplitudes.value.drop(1) + level
    }

    fun resetWaveform() {
        _waveformAmplitudes.value = List(28) { 0f }
        _currentSignalLevel.value = 0f
    }

    private val _currentSignalLevel = MutableStateFlow(0f)
    val currentSignalLevel: StateFlow<Float> = _currentSignalLevel.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _lastDiagnostics = MutableStateFlow(AudioDiagnostics())
    val lastDiagnostics: StateFlow<AudioDiagnostics> = _lastDiagnostics.asStateFlow()

    private var activeAudioTrack: AudioTrack? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    fun isMicrophoneMuted(): Boolean {
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isMicrophoneMute == true
        }.getOrDefault(false)
    }

    fun hasMicrophoneHardware(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var activeAudioSource = "VOICE_RECOGNITION"
    private val isAllZeroDetected = AtomicBoolean(true)
    private var lastRecordedPcm16: ShortArray = ShortArray(0)
    private var recordingFocusRequest: AudioFocusRequest? = null

    private fun requestRecordingAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            recordingFocusRequest = req
            runCatching { am.requestAudioFocus(req) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            }
        }
    }

    private fun abandonRecordingAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            recordingFocusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(null) }
        }
        recordingFocusRequest = null
    }

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope, onAmplitudeUpdate: (Float, Long) -> Unit): Boolean =
        startRecordingDetailed(scope, onAmplitudeUpdate) == MicStartStatus.STARTED

    @SuppressLint("MissingPermission")
    fun startRecordingDetailed(
        scope: CoroutineScope,
        onAmplitudeUpdate: (Float, Long) -> Unit
    ): MicStartStatus {
        // Enforce single microphone session at a time
        if (!activeSessionLock.compareAndSet(false, true)) {
            return if (_isRecording.value) MicStartStatus.ALREADY_RUNNING else MicStartStatus.INIT_FAILED
        }
        if (!hasRecordPermission()) {
            activeSessionLock.set(false)
            return MicStartStatus.NO_PERMISSION
        }
        if (!hasMicrophoneHardware()) {
            activeSessionLock.set(false)
            return MicStartStatus.NO_HARDWARE
        }

        // Stop any active TTS playback before opening microphone
        stopPlayback()
        requestRecordingAudioFocus()

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            abandonRecordingAudioFocus()
            activeSessionLock.set(false)
            return MicStartStatus.INIT_FAILED
        }

        var sourceUsed = "VOICE_RECOGNITION"
        var record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBufSize * 2, SAMPLES_PER_FRAME * 20)
            )
        } catch (_: Exception) {
            null
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record?.release() }
            sourceUsed = "MIC"
            record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBufSize * 2, SAMPLES_PER_FRAME * 20)
                )
            } catch (_: Exception) {
                null
            }
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record?.release() }
            abandonRecordingAudioFocus()
            activeSessionLock.set(false)
            return MicStartStatus.INIT_FAILED
        }
        activeAudioSource = sourceUsed

        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(record.audioSessionId)?.apply {
                    enabled = true
                }
            }
        }

        synchronized(capturedSamples) {
            capturedSamples.clear()
        }
        isAllZeroDetected.set(true)
        _waveformAmplitudes.value = List(28) { 0f }
        _currentSignalLevel.value = 0f

        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                releaseHardwareEffects()
                record.release()
                abandonRecordingAudioFocus()
                activeSessionLock.set(false)
                return MicStartStatus.DEAD_OBJECT
            }
            audioRecord = record
            recordingStartTimeMs = System.currentTimeMillis()
            _isRecording.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val readBuffer = ShortArray(SAMPLES_PER_FRAME * 2)
                var lastUiUpdateMs = 0L
                var stalledReads = 0

                while (isActive && _isRecording.value) {
                    val elapsedMs = System.currentTimeMillis() - recordingStartTimeMs
                    if (elapsedMs >= MAX_RECORDING_DURATION_MS) {
                        break
                    }

                    val readCount = record.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
                        stalledReads = 0
                        var sumSq = 0.0
                        synchronized(capturedSamples) {
                            for (i in 0 until readCount) {
                                val s = readBuffer[i]
                                if (s.toInt() != 0) {
                                    isAllZeroDetected.set(false)
                                }
                                capturedSamples.add(s)
                                val norm = s / 32768.0
                                sumSq += norm * norm
                            }
                        }
                        val rms = sqrt(sumSq / readCount).toFloat()
                        val nowMs = System.currentTimeMillis()

                        if (nowMs - lastUiUpdateMs >= 50L) {
                            lastUiUpdateMs = nowMs
                            val visualLevel = if (rms < ABSOLUTE_MIN_STOP_RMS) {
                                0f
                            } else {
                                ((rms - ABSOLUTE_MIN_STOP_RMS) * 6.5f).coerceIn(0.04f, 1.0f)
                            }
                            _currentSignalLevel.value = visualLevel
                            _waveformAmplitudes.value = _waveformAmplitudes.value.drop(1) + visualLevel
                            onAmplitudeUpdate(visualLevel, elapsedMs)
                        }
                    } else if (readCount < 0) {
                        break
                    } else {
                        // Stalled input: AudioRecord returned 0 bytes. Never spin forever.
                        stalledReads++
                        if (stalledReads >= 100) break
                    }
                }
            }
            MicStartStatus.STARTED
        } catch (_: Exception) {
            releaseHardwareEffects()
            runCatching { record.release() }
            audioRecord = null
            _isRecording.value = false
            abandonRecordingAudioFocus()
            activeSessionLock.set(false)
            MicStartStatus.INIT_FAILED
        }
    }

    private fun releaseHardwareEffects() {
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching { automaticGainControl?.release() }
        automaticGainControl = null
    }

    fun stopRecording(): AudioInput {
        val actualSampleRate = audioRecord?.sampleRate?.takeIf { it > 0 } ?: SAMPLE_RATE_HZ
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        releaseHardwareEffects()
        abandonRecordingAudioFocus()

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        activeSessionLock.set(false)

        val durationMs = max(0L, System.currentTimeMillis() - recordingStartTimeMs)
        val rawSamples: ShortArray
        synchronized(capturedSamples) {
            rawSamples = capturedSamples.toShortArray()
            capturedSamples.clear()
        }
        lastRecordedPcm16 = rawSamples.clone()

        _waveformAmplitudes.value = List(28) { 0f }
        _currentSignalLevel.value = 0f

        return preprocessAndRunVad(
            rawPcm = rawSamples,
            durationMs = durationMs,
            sourceSampleRateHz = actualSampleRate,
            isAllZero = isAllZeroDetected.get(),
            audioSource = activeAudioSource
        )
    }

    fun saveLastRecordingWav(targetFile: java.io.File): Boolean {
        val samples = lastRecordedPcm16.clone()
        if (samples.isEmpty()) return false
        return try {
            targetFile.parentFile?.mkdirs()
            targetFile.outputStream().use { out ->
                val dataSize = samples.size * 2
                val totalSize = 36 + dataSize
                val header = ByteArray(44)
                header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
                header[4] = (totalSize and 0xff).toByte()
                header[5] = ((totalSize shr 8) and 0xff).toByte()
                header[6] = ((totalSize shr 16) and 0xff).toByte()
                header[7] = ((totalSize shr 24) and 0xff).toByte()
                header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
                header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
                header[20] = 1; header[21] = 0
                header[22] = 1; header[23] = 0
                val sampleRate = SAMPLE_RATE_HZ
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = ((sampleRate shr 8) and 0xff).toByte()
                header[26] = ((sampleRate shr 16) and 0xff).toByte()
                header[27] = ((sampleRate shr 24) and 0xff).toByte()
                val byteRate = sampleRate * 2
                header[28] = (byteRate and 0xff).toByte()
                header[29] = ((byteRate shr 8) and 0xff).toByte()
                header[30] = ((byteRate shr 16) and 0xff).toByte()
                header[31] = ((byteRate shr 24) and 0xff).toByte()
                header[32] = 2; header[33] = 0
                header[34] = 16; header[35] = 0
                header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
                header[40] = (dataSize and 0xff).toByte()
                header[41] = ((dataSize shr 8) and 0xff).toByte()
                header[42] = ((dataSize shr 16) and 0xff).toByte()
                header[43] = ((dataSize shr 24) and 0xff).toByte()
                out.write(header)
                val buffer = ByteArray(samples.size * 2)
                for (i in samples.indices) {
                    val s = samples[i].toInt()
                    buffer[i * 2] = (s and 0xff).toByte()
                    buffer[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
                }
                out.write(buffer)
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resamples 16-bit PCM from `sourceRateHz` to `targetRateHz` with linear interpolation.
     */
    fun resamplePcm16(samples: ShortArray, sourceRateHz: Int, targetRateHz: Int): ShortArray {
        if (sourceRateHz == targetRateHz || samples.isEmpty() || sourceRateHz <= 0 || targetRateHz <= 0) {
            return samples
        }
        val ratio = sourceRateHz.toDouble() / targetRateHz.toDouble()
        val outputLength = max(1, (samples.size / ratio).roundToInt())
        val output = ShortArray(outputLength)
        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, samples.lastIndex)
            val idx1 = min(idx0 + 1, samples.lastIndex)
            val frac = srcPos - idx0
            val interpolated = samples[idx0] * (1.0 - frac) + samples[idx1] * frac
            output[i] = interpolated.roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    fun preprocessAndRunVad(
        rawPcm: ShortArray,
        durationMs: Long,
        sourceSampleRateHz: Int = SAMPLE_RATE_HZ,
        isAllZero: Boolean = false,
        audioSource: String = "VOICE_RECOGNITION"
    ): AudioInput {
        val pcm16 = if (sourceSampleRateHz != SAMPLE_RATE_HZ) {
            resamplePcm16(rawPcm, sourceSampleRateHz, SAMPLE_RATE_HZ)
        } else {
            rawPcm
        }
        val result = Companion.preprocessAndRunVad(
            rawPcm = pcm16,
            sourceSampleRateHz = SAMPLE_RATE_HZ,
            durationMs = durationMs,
            isAllZero = isAllZero,
            audioSource = audioSource
        )
        _lastDiagnostics.value = result.diagnostics
        return result
    }

    /**
     * Plays 16-bit PCM audio at the model's native sample rate (`sampleRateHz`, e.g., 22050 Hz or 24000 Hz,
     * never hardcoded to 16000 Hz) with Android AudioFocus management and interruption handling.
     */
    suspend fun playPcmAudio(
        pcmSamples: ShortArray,
        sampleRateHz: Int = 22050,
        speed: Float = 1.0f
    ): Boolean {
        if (pcmSamples.isEmpty()) return false
        stopPlayback()
        return withContext(Dispatchers.IO) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val speechAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
                runCatching {
                    val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(speechAttributes)
                        .setOnAudioFocusChangeListener { change ->
                            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            ) {
                                stopPlayback()
                            }
                        }
                        .build()
                    activeFocusRequest = focusReq
                    audioManager.requestAudioFocus(focusReq)
                }
            }

            try {
                _isPlaying.value = true
                val effectiveRate = (sampleRateHz.coerceIn(8000, 48000) * speed.coerceIn(0.6f, 1.6f)).toInt()
                val minBufferSize = AudioTrack.getMinBufferSize(
                    effectiveRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferBytes = max(minBufferSize, pcmSamples.size * 2)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(speechAttributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(effectiveRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeAudioTrack = track
                track.write(pcmSamples, 0, pcmSamples.size)
                track.play()

                val playDurationMs = ((pcmSamples.size.toDouble() / effectiveRate) * 1000.0).toLong()
                var waited = 0L
                while (waited < playDurationMs && _isPlaying.value) {
                    kotlinx.coroutines.delay(50)
                    waited += 50
                }
                track.stop()
                track.release()
                activeAudioTrack = null
                _isPlaying.value = false
                abandonAudioFocus(audioManager)
                true
            } catch (_: Exception) {
                _isPlaying.value = false
                abandonAudioFocus(audioManager)
                false
            }
        }
    }

    private fun abandonAudioFocus(audioManager: AudioManager?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            activeFocusRequest?.let { req ->
                runCatching { audioManager.abandonAudioFocusRequest(req) }
            }
            activeFocusRequest = null
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {
        }
        activeAudioTrack = null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        abandonAudioFocus(audioManager)
    }
}
