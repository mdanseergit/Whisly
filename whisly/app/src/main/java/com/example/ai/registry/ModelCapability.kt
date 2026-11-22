package com.example.ai.registry

/**
 * Task-aware model taxonomy (per the production spec).
 *
 * A model may support one or more languages and one or more tasks, as declared in its verified
 * manifest. STT and TTS readiness are tracked independently.
 */
enum class ModelType {
    ASR_MODEL,
    TTS_VOICE_MODEL,
    MULTILINGUAL_ASR_MODEL,
    MULTILINGUAL_TTS_MODEL,
    VAD_MODEL,
    OPTIONAL_PUNCTUATION_MODEL
}

/**
 * Explicit capability states. Never collapse multiple tasks into one boolean.
 */
enum class CapabilityStatus {
    NOT_AVAILABLE,
    AVAILABLE_TO_DOWNLOAD,
    IMPORT_REQUIRED,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    READY,
    LOAD_FAILED,
    INFERENCE_FAILED,
    UNAVAILABLE
}
