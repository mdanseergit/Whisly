package com.example.ai.stt

import com.example.ai.registry.CapabilityRegistry
import java.text.Normalizer

/**
 * Single fail-closed transcript firewall for both the main microphone and conversation paths.
 *
 * This is deliberately conservative. The bundled sherpa-onnx Whisper result exposes no
 * per-token probability or log-probability, so [confidence] is null for every current-model
 * result and the outcome is always [LOW_CONFIDENCE]. A future engine may only reach [PASS] by
 * supplying both a measured probability from per-token evidence and an explicit acceptance
 * threshold from evaluation; no proxy score, VAD value, SNR value, or default is accepted.
 *
 * Garbage filtering uses only measured classes from `docs/DIAGNOSIS9.md` §5.1–5.2: every bracketed
 * annotation (`[MUSIC]`, `[SUMMING]`, `[SOMI]`, ...) and every `<|...|>` language tag is stripped,
 * text is NFC-normalised, and the selected language's registry script blocks must cover at least
 * 95% of letters. Digits and punctuation are neutral; Latin is only valid for the Latin language.
 */
object TranscriptFirewall {

    enum class Outcome {
        PASS,
        LOW_CONFIDENCE,
        FAIL
    }

    const val LOW_CONFIDENCE_LABEL = "Not sure: check and edit"
    const val FAILURE_MESSAGE = "Couldn't understand. Try again."
    const val MIN_SCRIPT_RATIO = 0.95

    data class Result(
        val outcome: Outcome,
        val text: String,
        val confidence: Float?
    )

    /**
     * Strips only annotation classes measured in the diagnosis. It never translates, transliterates,
     * or invents text: after stripping, the script gate decides whether the remainder is usable.
     */
    fun sanitize(raw: String): String {
        val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val noLanguageTags = nfc.replace(Regex("<\\|[^>]*\\|>"), " ")
        val noAnnotations = noLanguageTags.replace(Regex("\\[[^]]*\\]"), " ")
        return noAnnotations.replace(Regex("[\\s\\p{Z}]+"), " ").trim()
    }

    /** Canonical entry point: one sanitizer, one script gate, one confidence rule. */
    fun evaluateForLanguage(
        raw: String,
        languageCode: String,
        confidence: Float?,
        acceptanceThreshold: Float? = null
    ): Result {
        val text = sanitize(raw)
        if (text.isEmpty()) return Result(Outcome.FAIL, "", confidence)
        if (!CapabilityRegistry.textMatchesLanguageScript(text, languageCode)) {
            return Result(Outcome.FAIL, "", confidence)
        }
        val outcome = when {
            confidence == null || acceptanceThreshold == null -> Outcome.LOW_CONFIDENCE
            confidence >= acceptanceThreshold -> Outcome.PASS
            else -> Outcome.LOW_CONFIDENCE
        }
        return Result(outcome, text, confidence)
    }

    /**
     * Backward-compatible outcome-only view used by the registry-independent unit tests. The
     * canonical path is [evaluateForLanguage], which also returns the sanitised editable text.
     */
    fun evaluate(
        transcript: String,
        confidence: Float?,
        acceptanceThreshold: Float? = null
    ): Outcome = evaluateForLanguage(transcript, "en", confidence, acceptanceThreshold).outcome
}
