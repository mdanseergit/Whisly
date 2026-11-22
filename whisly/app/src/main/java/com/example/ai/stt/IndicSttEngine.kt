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
    private val isPackInstalledAndVerified: suspend (String) -> Boolean,
    private val acousticRecognizerDelegate: (suspend (pcm16Samples: ShortArray, sampleRateHz: Int, languageCode: String) -> Pair<String, Float>?)? = null
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

        // 2. Resolve language (manual or Auto-Detect)
        val resolvedLangCode = if (language.equals(CapabilityRegistry.AUTO_DETECT_CODE, ignoreCase = true)) {
            val audioLidCandidate = audio.audioLidCandidates.maxByOrNull { it.value }?.key
            audioLidCandidate ?: "en"
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
        val sttInstalled = isPackInstalledAndVerified(capability.sttPackId) ||
            isPackInstalledAndVerified("stt_indicconformer_${resolvedLangCode}_v2")
        if (!sttInstalled) {
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.sttPackId,
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.MODEL_MISSING
            )
        }

        // 4. Enforce at most ONE resident STT model in memory at a time
        if (activeResidentPackId != null && activeResidentPackId != capability.sttPackId) {
            unloadResidentModel()
        }
        activeResidentPackId = capability.sttPackId

        // 5. Run acoustic inference on the voiced segment only (pre-roll + speech + hangover).
        //    Long silences must never reach the model, and a failed decode is never a silence claim.
        val decoder = acousticRecognizerDelegate
            ?: return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.sttPackId,
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.STT_MODEL_MISSING
            )

        val segment = if (audio.voicedSegmentPcm16.isNotEmpty()) {
            audio.voicedSegmentPcm16
        } else {
            audio.pcm16Samples
        }
        val inferenceResult = decoder(segment, audio.sampleRateHz, resolvedLangCode)
        if (inferenceResult == null) {
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.sttPackId,
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.STT_FAILURE
            )
        }
        val decodedText = inferenceResult.first.trim()
        val sttConf = inferenceResult.second

        if (decodedText.isEmpty()) {
            return@withContext SpeechResult(
                success = false,
                transcript = "",
                language = resolvedLangCode,
                confidence = null,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = capability.sttPackId,
                vadProbability = audio.vadSpeechProbability,
                error = PipelineErrorType.STT_FAILURE
            )
        }

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

        // 6. Text LID verification & disambiguation if auto-detect was requested
        val isAuto = language.equals(CapabilityRegistry.AUTO_DETECT_CODE, ignoreCase = true)
        val lidDecision = if (isAuto) {
            LanguageIdEngine.identifyLanguage(
                transcript = decodedText,
                audioLidCandidates = audio.audioLidCandidates
            )
        } else null

        val finalLanguageCode = if (isAuto && lidDecision?.detectedLanguageCode != null) {
            lidDecision.detectedLanguageCode
        } else {
            resolvedLangCode
        }

        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(10L)

        SpeechResult(
            success = true,
            transcript = decodedText,
            language = finalLanguageCode,
            confidence = sttConf,
            latencyMs = elapsed,
            engineUsed = capability.sttPackId,
            vadProbability = audio.vadSpeechProbability,
            isLanguageAmbiguous = isAuto && (lidDecision?.isAmbiguous == true),
            ambiguousLanguageCandidates = if (isAuto && lidDecision?.isAmbiguous == true) {
                lidDecision.ambiguousCandidates
            } else {
                emptyList()
            },
            codeSwitchingDetected = lidDecision?.codeSwitchingDetected ?: false,
            error = null
        )
    }
}
