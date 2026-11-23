package com.example.ai.stt

/**
 * Three-outcome transcript firewall.
 *
 * This is deliberately conservative. The bundled sherpa-onnx Whisper result exposes no
 * per-token probability or log-probability, so [confidence] is null for every current-model
 * result and the outcome is always [LOW_CONFIDENCE]. A future engine may only reach [PASS] by
 * supplying both a measured probability from per-token evidence and an explicit acceptance
 * threshold from evaluation; no proxy score, VAD value, SNR value, or default is accepted.
 */
object TranscriptFirewall {

    enum class Outcome {
        PASS,
        LOW_CONFIDENCE,
        FAIL
    }

    const val LOW_CONFIDENCE_LABEL = "Not sure: check and edit"
    const val FAILURE_MESSAGE = "Couldn't understand. Try again."

    /**
     * [acceptanceThreshold] must be null until a real per-token/log-prob signal has been measured
     * for the active engine. Supplying a threshold here is an explicit, evidence-backed decision;
     * it is never guessed by this class.
     */
    fun evaluate(
        transcript: String,
        confidence: Float?,
        acceptanceThreshold: Float? = null
    ): Outcome {
        if (transcript.isBlank()) return Outcome.FAIL
        if (confidence == null || acceptanceThreshold == null) return Outcome.LOW_CONFIDENCE
        return if (confidence >= acceptanceThreshold) Outcome.PASS else Outcome.LOW_CONFIDENCE
    }
}
