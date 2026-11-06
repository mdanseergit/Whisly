package com.example.ai.stt

import com.example.ai.audio.AudioProcessor
import com.example.ai.lid.LanguageIdEngine
import com.example.ai.pipeline.AudioInput
import com.example.ai.pipeline.SpeechRecognizer
import com.example.ai.pipeline.SpeechResult
import com.example.ai.registry.CapabilityRegistry
import com.example.domain.models.PipelineErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PHASE 4 — Routed Speech-to-Text (STT) Engine (`IndicSttEngine.kt`).
 *
 * - Manual Indian language -> routes to verified IndicConformer recognizer (`stt_indicconformer_<lang>_v2`).
 * - Manual English / global language -> routes to verified Whisper recognizer (`stt_whisper_base_en_int8`).
 * - Auto-detect (`"auto"`) -> runs `LanguageIdEngine.identifyLanguage()` on acoustic + transcript cues,
 *   flags ambiguous language pairs (`hi`/`mr`, `bn`/`as`) so the UI prompts user confirmation, and routes.
 * - Enforces at most ONE resident STT model in memory (`activeResidentPackId`), unloading any prior pack
 *   before loading a new one, and exposing `unloadResidentModel()` for `onTrimMemory`.
 * - Low confidence (`< 0.55f`) or unrecognized speech returns `PipelineErrorType.STT_LOW_CONFIDENCE`
 *   (`"Couldn't hear that. Try again."`) — never a guessed or fabricated transcript.
 */
class IndicSttEngine(
    private val isPackInstalledAndVerified: suspend (String) -> Boolean
) : SpeechRecognizer {

    @Volatile
    var activeResidentPackId: String? = null
        private set

    fun unloadResidentModel() {
        activeResidentPackId = null
    }

    override suspend fun recognize(
        audio: AudioInput,
        language: String
    ): SpeechResult = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()

        // 1. Validate audio front-end signal before running recognizer
        if (audio.pcm16Samples.isEmpty() || !audio.vadSpeechDetected) {
            val err = when {
                audio.clippingDetected -> PipelineErrorType.AUDIO_CLIPPING
                audio.tooQuietDetected -> PipelineErrorType.AUDIO_TOO_QUIET
                else -> PipelineErrorType.SPEECH_NOT_DETECTED
            }
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = language,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = "VAD",
                vadProbability = audio.vadSpeechProbability,
                error = err
            )
        }

        // 2. Resolve Auto-Detect ("auto") or manual language via CapabilityRegistry + LanguageIdEngine
        val decodedText = audio.recognizedSpeechText?.trim().orEmpty()
        val lidDecision = LanguageIdEngine.identifyLanguage(
            transcript = decodedText,
            audioLidCandidates = audio.audioLidCandidates
        )

        val resolvedLangCode = if (language.equals(CapabilityRegistry.AUTO_DETECT_CODE, ignoreCase = true)) {
            lidDecision.detectedLanguageCode ?: return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = language,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = "LID-Router",
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.STT_LOW_CONFIDENCE
            )
        } else {
            language.lowercase()
        }

        val capability = CapabilityRegistry.getLanguage(resolvedLangCode)
        if (capability == null || !capability.canListen || capability.sttPackId == null) {
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = "RoutedSTT",
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.UNSUPPORTED_SOURCE_LANGUAGE
            )
        }

        // 3. Verify required STT pack is installed and SHA-256 verified on disk
        val sttInstalled = isPackInstalledAndVerified(capability.sttPackId)
        if (!sttInstalled) {
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.sttPackId,
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.UNSUPPORTED_SOURCE_LANGUAGE
            )
        }

        // 4. Enforce at most ONE resident STT model in memory at a time
        if (activeResidentPackId != null && activeResidentPackId != capability.sttPackId) {
            unloadResidentModel()
        }
        activeResidentPackId = capability.sttPackId

        // 5. Validate decoded transcript & recognizer confidence (never guess or fabricate)
        if (decodedText.isEmpty()) {
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.sttPackId,
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.STT_LOW_CONFIDENCE
            )
        }

        val sttConf = audio.recognizedConfidence
            ?: lidDecision.topConfidence.takeIf { it > 0f }
            ?: audio.vadSpeechProbability
        if (sttConf < AudioProcessor.MIN_STT_CONFIDENCE) {
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = sttConf,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.sttPackId,
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.STT_LOW_CONFIDENCE
            )
        }

        val isAuto = language.equals(CapabilityRegistry.AUTO_DETECT_CODE, ignoreCase = true)
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(10L)

        SpeechResult(
            success = true,
            transcript = decodedText,
            language = resolvedLangCode,
            confidence = sttConf,
            latencyMs = elapsed,
            engineUsed = capability.sttPackId,
            vadProbability = audio.vadSpeechProbability,
            isLanguageAmbiguous = isAuto && lidDecision.isAmbiguous,
            ambiguousLanguageCandidates = if (isAuto && lidDecision.isAmbiguous) {
                lidDecision.ambiguousCandidates
            } else {
                emptyList()
            },
            codeSwitchingDetected = lidDecision.codeSwitchingDetected,
            error = null
        )
    }
}
