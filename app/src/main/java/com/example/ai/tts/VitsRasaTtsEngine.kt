package com.example.ai.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.LruCache
import com.example.ai.audio.AudioProcessor
import com.example.ai.pipeline.AudioResult
import com.example.ai.pipeline.SpeechSynthesizer
import com.example.ai.registry.CapabilityRegistry
import com.example.domain.models.PipelineErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * PHASE 6 — Routed Text-to-Speech (TTS) Engine (`VitsRasaTtsEngine.kt`).
 *
 * - Completely removed the formant sine-wave generator (`synthesizeIndicPhonemeProsodyPcm`) from `src/main`.
 * - Enforces [CapabilityRegistry] (`canSpeak`): languages with no voice (e.g., Santali `sat` or Wave 3)
 *   return `PipelineErrorType.UNSUPPORTED_TARGET_VOICE` (`"Voice not available for this language"`)
 *   while keeping the translated text visible.
 * - Synthesizes real PCM audio via:
 *   1. An attached on-device neural synthesis session (`neuralVocoderDelegate`) at native 22,050 Hz / 24,000 Hz, OR
 *   2. Android's verified offline `TextToSpeech.synthesizeToFile` parsed from its RIFF WAV header at its
 *      actual native sample rate (never hardcoding 16,000 Hz).
 * - Maintains a size-limited 8 MB LRU PCM audio cache (`pcmCache`) for instant replay.
 */
class VitsRasaTtsEngine(
    private val context: Context,
    private val audioProcessor: AudioProcessor,
    private val isPackInstalledAndVerified: suspend (String) -> Boolean,
    private val neuralVocoderDelegate: ((text: String, languageCode: String, voiceId: String) -> Pair<ShortArray, Int>?)? = null
) : SpeechSynthesizer {

    companion object {
        const val VITS_NATIVE_SAMPLE_RATE_HZ = 22050
        const val KOKORO_NATIVE_SAMPLE_RATE_HZ = 24000
        private const val MAX_CACHE_BYTES = 8 * 1024 * 1024 // 8 MB LRU cache
    }

    data class CachedAudioEntry(
        val pcm16: ShortArray,
        val sampleRateHz: Int
    )

    private val pcmCache = object : LruCache<String, CachedAudioEntry>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedAudioEntry): Int {
            return (value.pcm16.size * 2).coerceAtLeast(1)
        }
    }

    private var systemTts: TextToSpeech? = null
    private var systemTtsReady = false

    init {
        try {
            systemTts = TextToSpeech(context.applicationContext) { status ->
                systemTtsReady = (status == TextToSpeech.SUCCESS)
            }
        } catch (_: Exception) {
            systemTtsReady = false
        }
    }

    fun clearAudioCache() {
        pcmCache.evictAll()
    }

    fun isSystemVoiceAvailableOffline(languageCodeOrLocale: String): Boolean {
        if (!systemTtsReady || systemTts == null) return false
        return runCatching {
            val tag = if (languageCodeOrLocale.contains("-")) {
                languageCodeOrLocale
            } else {
                "$languageCodeOrLocale-IN"
            }
            val locale = Locale.forLanguageTag(tag)
            val avail = systemTts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (avail < TextToSpeech.LANG_AVAILABLE) return@runCatching false
            val voices = systemTts?.voices.orEmpty()
            voices.any { v ->
                v.locale.language.equals(locale.language, ignoreCase = true) &&
                    !v.isNetworkConnectionRequired
            } || avail >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)
    }

    override suspend fun synthesize(
        text: String,
        language: String,
        voiceId: String
    ): AudioResult = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()
        val capability = CapabilityRegistry.getLanguage(language)

        if (text.isBlank() || capability == null) {
            return@withContext AudioResult(
                success = false,
                pcm16Audio = ShortArray(0),
                sampleRateHz = VITS_NATIVE_SAMPLE_RATE_HZ,
                durationMs = 0L,
                language = language,
                voiceId = voiceId,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = "RoutedTTS",
                error = PipelineErrorType.TTS_FAILURE
            )
        }

        // 1. Check CapabilityRegistry: if language has no voice (e.g., Santali `sat` or Wave 3), return UNSUPPORTED_TARGET_VOICE
        if (!capability.canSpeak || capability.ttsPackId == null) {
            return@withContext AudioResult(
                success = false,
                pcm16Audio = ShortArray(0),
                sampleRateHz = VITS_NATIVE_SAMPLE_RATE_HZ,
                durationMs = 0L,
                language = language,
                voiceId = voiceId,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = "UnsupportedVoice",
                usedSystemFallback = false,
                error = PipelineErrorType.UNSUPPORTED_TARGET_VOICE
            )
        }

        val cacheKey = "${language.lowercase()}:${voiceId}:${text.trim()}"
        pcmCache.get(cacheKey)?.let { cached ->
            val durationMs = ((cached.pcm16.size.toDouble() / cached.sampleRateHz) * 1000.0).toLong()
            return@withContext AudioResult(
                success = true,
                pcm16Audio = cached.pcm16,
                sampleRateHz = cached.sampleRateHz,
                durationMs = durationMs,
                language = language,
                voiceId = voiceId,
                latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(2L),
                engineUsed = capability.ttsPackId
            )
        }

        val ttsInstalled = isPackInstalledAndVerified(capability.ttsPackId)
        val offlineSystemVoiceReady = isSystemVoiceAvailableOffline(capability.voiceLocale)
        if (!ttsInstalled && !offlineSystemVoiceReady) {
            return@withContext AudioResult(
                success = false,
                pcm16Audio = ShortArray(0),
                sampleRateHz = VITS_NATIVE_SAMPLE_RATE_HZ,
                durationMs = 0L,
                language = language,
                voiceId = voiceId,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.ttsPackId,
                error = PipelineErrorType.MODEL_MISSING
            )
        }

        // 2. Try attached neural vocoder session delegate first, then verified Android offline TextToSpeech WAV synthesis
        val synthesizedPair: Pair<ShortArray, Int>? = neuralVocoderDelegate?.invoke(text.trim(), language, voiceId)
            ?: synthesizeViaOfflineSystemTtsToWav(text.trim(), capability.voiceLocale)

        if (synthesizedPair == null || synthesizedPair.first.isEmpty()) {
            return@withContext AudioResult(
                success = false,
                pcm16Audio = ShortArray(0),
                sampleRateHz = if (language.equals("en", ignoreCase = true)) {
                    KOKORO_NATIVE_SAMPLE_RATE_HZ
                } else {
                    VITS_NATIVE_SAMPLE_RATE_HZ
                },
                durationMs = 0L,
                language = language,
                voiceId = voiceId,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.ttsPackId,
                usedSystemFallback = offlineSystemVoiceReady,
                error = PipelineErrorType.UNSUPPORTED_TARGET_VOICE
            )
        }

        val (pcmBuffer, nativeRateHz) = synthesizedPair
        pcmCache.put(cacheKey, CachedAudioEntry(pcmBuffer, nativeRateHz))
        val durationMs = ((pcmBuffer.size.toDouble() / nativeRateHz) * 1000.0).toLong()
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(10L)

        AudioResult(
            success = true,
            pcm16Audio = pcmBuffer,
            sampleRateHz = nativeRateHz,
            durationMs = durationMs,
            language = language,
            voiceId = voiceId,
            latencyMs = elapsed,
            engineUsed = capability.ttsPackId,
            usedSystemFallback = offlineSystemVoiceReady && neuralVocoderDelegate == null
        )
    }

    /**
     * Synthesizes speech using Android's offline `TextToSpeech.synthesizeToFile` and parses
     * the RIFF WAV header to extract the exact native sample rate (`sampleRateHz`) and 16-bit PCM samples.
     */
    private suspend fun synthesizeViaOfflineSystemTtsToWav(
        text: String,
        voiceLocaleTag: String
    ): Pair<ShortArray, Int>? = withContext(Dispatchers.IO) {
        val tts = systemTts ?: return@withContext null
        if (!systemTtsReady || !isSystemVoiceAvailableOffline(voiceLocaleTag)) return@withContext null

        val tempWav = File(context.cacheDir, "tts_synth_${System.currentTimeMillis()}.wav")
        try {
            val locale = Locale.forLanguageTag(voiceLocaleTag)
            tts.language = locale
            val uttId = "whisly_wav_${System.currentTimeMillis()}"
            val completed = withTimeoutOrNull(4000L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == uttId && cont.isActive) cont.resume(true)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == uttId && cont.isActive) cont.resume(false)
                        }
                    })
                    val res = tts.synthesizeToFile(text, Bundle(), tempWav, uttId)
                    if (res != TextToSpeech.SUCCESS && cont.isActive) {
                        cont.resume(false)
                    }
                }
            } ?: false

            if (!completed || !tempWav.exists() || tempWav.length() <= 44L) {
                return@withContext null
            }
            parseWavFileToPcm16(tempWav.readBytes())
        } catch (_: Exception) {
            null
        } finally {
            runCatching { tempWav.delete() }
        }
    }

    /**
     * Parses a standard 16-bit PCM RIFF WAV byte array and returns `(pcm16Samples, nativeSampleRateHz)`.
     */
    fun parseWavFileToPcm16(wavBytes: ByteArray): Pair<ShortArray, Int>? {
        if (wavBytes.size <= 44) return null
        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = buffer.getInt(24).coerceIn(8000, 48000)
        val pcmByteCount = wavBytes.size - 44
        val sampleCount = pcmByteCount / 2
        if (sampleCount <= 0) return null
        val shorts = ShortArray(sampleCount)
        buffer.position(44)
        buffer.asShortBuffer().get(shorts)
        return shorts to sampleRate
    }

    suspend fun speakOutLoud(
        text: String,
        languageCode: String,
        pcmAudio: ShortArray,
        sampleRateHz: Int = VITS_NATIVE_SAMPLE_RATE_HZ,
        speechSpeed: Float = 1.0f
    ): Boolean {
        if (text.isBlank()) return false
        val capability = CapabilityRegistry.getLanguage(languageCode)
        if (capability == null || !capability.canSpeak) {
            return false
        }
        if (pcmAudio.isNotEmpty()) {
            return audioProcessor.playPcmAudio(pcmAudio, sampleRateHz, speechSpeed)
        }
        val synth = synthesize(text, languageCode, "default")
        if (!synth.success || synth.pcm16Audio.isEmpty()) {
            return false
        }
        return audioProcessor.playPcmAudio(synth.pcm16Audio, synth.sampleRateHz, speechSpeed)
    }

    fun stopSpeaking() {
        try {
            systemTts?.stop()
        } catch (_: Exception) {
        }
        audioProcessor.stopPlayback()
    }

    fun shutdown() {
        stopSpeaking()
        clearAudioCache()
        try {
            systemTts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
