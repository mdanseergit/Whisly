package com.example.ai.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.ai.pipeline.AudioResult
import com.example.ai.pipeline.SpeechSynthesizer
import com.example.ai.registry.CapabilityRegistry
import com.example.domain.models.PipelineErrorType
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Temporary output voice for the first bundled-STT proof.
 *
 * This is the platform Android TextToSpeech engine, not a bundled neural voice. It contains no
 * model weights and no synthetic phoneme/formant audio. Actual voice availability, language
 * coverage, and output quality are device-dependent and remain UNMEASURED until a phone audit is
 * pasted into the evidence record. A neural TTS engine is a later step.
 */
class SystemTextToSpeechEngine(private val context: Context) : SpeechSynthesizer {

    @Volatile
    private var engine: TextToSpeech? = null

    @Volatile
    private var initStatus: Int = TextToSpeech.ERROR

    @Volatile
    private var pendingContinuation: Continuation<Boolean>? = null

    private val initMutex = Mutex()
    private val utteranceIds = AtomicInteger(1)

    override suspend fun synthesize(
        text: String,
        language: String,
        voiceId: String
    ): AudioResult {
        if (text.isBlank()) {
            return audioFailure(language, voiceId, PipelineErrorType.TTS_FAILURE, "blank text")
        }
        if (isUnsupportedLanguage(language)) {
            return audioFailure(
                language,
                voiceId,
                PipelineErrorType.UNSUPPORTED_TARGET_VOICE,
                "no stated device voice mapping"
            )
        }

        val startedAt = System.currentTimeMillis()
        val tts = ensureInitialized()
            ?: return audioFailure(language, voiceId, PipelineErrorType.TTS_FAILURE, "engine init failed")

        val locale = localeFor(language)
            ?: return audioFailure(language, voiceId, PipelineErrorType.UNSUPPORTED_TARGET_VOICE, "invalid locale")
        val availability = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            return audioFailure(
                language,
                voiceId,
                PipelineErrorType.UNSUPPORTED_TARGET_VOICE,
                "device voice not available"
            )
        }

        return AudioResult(
            success = true,
            pcm16Audio = ShortArray(0),
            sampleRateHz = NO_PCM_SAMPLE_RATE_HZ,
            durationMs = 0L,
            language = language,
            voiceId = "system_tts",
            latencyMs = System.currentTimeMillis() - startedAt,
            engineUsed = "Android TextToSpeech (temporary)",
            usedSystemFallback = true,
            error = null
        )
    }

    suspend fun speakOutLoud(
        text: String,
        languageCode: String,
        pcmAudio: ShortArray = ShortArray(0),
        sampleRateHz: Int = NO_PCM_SAMPLE_RATE_HZ,
        speechSpeed: Float = 1.0f
    ): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignoredPcm = pcmAudio
        @Suppress("UNUSED_PARAMETER")
        val ignoredRate = sampleRateHz
        if (text.isBlank() || isUnsupportedLanguage(languageCode)) return false

        val tts = ensureInitialized() ?: return false
        val locale = localeFor(languageCode) ?: return false
        val availability = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            return false
        }

        runCatching { tts.setLanguage(locale) }
        runCatching { tts.setSpeechRate(speechSpeed.coerceIn(0.25f, 3.0f)) }

        val utteranceId = "whisly_${utteranceIds.getAndIncrement()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                completePending(true)
            }

            override fun onError(utteranceId: String?) {
                completePending(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                completePending(false)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                completePending(false)
            }
        })

        return withTimeoutOrNull(SPEECH_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                pendingContinuation = cont
                cont.invokeOnCancellation {
                    pendingContinuation = null
                    runCatching { tts.stop() }
                }
                val result = runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId) }
                if (result.isFailure || result.getOrDefault(-1) != TextToSpeech.SUCCESS) {
                    completePending(false)
                }
            }
        } ?: run {
            completePending(false)
            false
        }
    }

    fun stopSpeaking() {
        completePending(false)
        runCatching { engine?.stop() }
    }

    fun clearAudioCache() {
        // The platform engine owns its own buffers; there is no Whisly PCM cache to clear.
    }

    fun shutdown() {
        completePending(false)
        runCatching { engine?.shutdown() }
        engine = null
        initStatus = TextToSpeech.ERROR
    }

    private fun completePending(success: Boolean) {
        val continuation = pendingContinuation ?: return
        pendingContinuation = null
        continuation.resume(success)
    }

    private suspend fun ensureInitialized(): TextToSpeech? = initMutex.withLock {
        val existing = engine
        if (existing != null && initStatus == TextToSpeech.SUCCESS) return@withLock existing
        if (existing != null) {
            runCatching { existing.shutdown() }
            engine = null
        }

        initStatus = INIT_NOT_YET
        val created = runCatching {
            TextToSpeech(context.applicationContext) { status -> initStatus = status }
        }.getOrNull() ?: return@withLock null
        engine = created

        val ready = withTimeoutOrNull(INIT_TIMEOUT_MS) {
            while (initStatus == INIT_NOT_YET) {
                delay(20L)
            }
            initStatus == TextToSpeech.SUCCESS
        }
        if (ready == true) created else null
    }

    private fun isUnsupportedLanguage(languageCode: String): Boolean =
        languageCode.isBlank() || languageCode.equals("auto", ignoreCase = true)

    /** Locale is registry-owned; no TTS-local language table remains. */
    private fun localeFor(languageCode: String): Locale? = runCatching {
        CapabilityRegistry.ttsLocale(languageCode)?.let { Locale.forLanguageTag(it) }
            ?: Locale.forLanguageTag(languageCode)
    }.getOrNull()

    private fun audioFailure(
        language: String,
        voiceId: String,
        error: PipelineErrorType,
        detail: String
    ): AudioResult {
        Log.w(TAG, "system TTS unavailable: $detail")
        return AudioResult(
            success = false,
            pcm16Audio = ShortArray(0),
            sampleRateHz = NO_PCM_SAMPLE_RATE_HZ,
            durationMs = 0L,
            language = language,
            voiceId = voiceId,
            latencyMs = 0L,
            engineUsed = "Android TextToSpeech (temporary)",
            usedSystemFallback = true,
            error = error
        )
    }

    private companion object {
        const val TAG = "SystemTts"
        const val NO_PCM_SAMPLE_RATE_HZ = 0
        const val INIT_NOT_YET = Int.MIN_VALUE
        const val INIT_TIMEOUT_MS = 5_000L
        const val SPEECH_TIMEOUT_MS = 30_000L
    }
}
