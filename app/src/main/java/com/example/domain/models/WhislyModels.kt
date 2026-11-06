package com.example.domain.models

enum class ModelTaskType(val label: String) {
    STT("Speech Recognition"),
    MT("Translation"),
    TTS("Speech Synthesis")
}

enum class TtsCapabilityStatus(val label: String) {
    VITS_RASA_VERIFIED("Voice Available"),
    SYSTEM_FALLBACK_ONLY("Offline Voice Available"),
    UNAVAILABLE("Voice not available for this language")
}

enum class PackInstallStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    UPDATE_AVAILABLE,
    CORRUPTED,
    FAILED
}

enum class PipelineStage(val statusLabel: String) {
    IDLE("Tap to speak"),
    RECORDING("Listening…"),
    PREPROCESSING("Processing audio…"),
    STT_INFERENCE("Transcribing speech…"),
    AWAITING_CONFIRMATION("Review or confirm transcript"),
    NORMALIZING("Preparing text…"),
    MT_INFERENCE("Translating…"),
    TTS_INFERENCE("Preparing voice…"),
    PLAYING_AUDIO("Speaking translation…"),
    COMPLETED("Translation ready"),
    ERROR("Action required")
}

enum class PipelineErrorType(
    val title: String,
    val userMessage: String,
    val recoverable: Boolean = true
) {
    MIC_PERMISSION_DENIED(
        "Microphone Access Needed",
        "Allow microphone access to translate your voice, or use Text Mode to type."
    ),
    NO_MICROPHONE_AVAILABLE(
        "No Microphone Found",
        "No microphone was detected on this device. Use Text Mode to type your sentence."
    ),
    UNSUPPORTED_SOURCE_LANGUAGE(
        "Language Pack Required",
        "Download the speech pack for this language in Language Packs to listen offline."
    ),
    UNSUPPORTED_LANGUAGE_DIRECTION(
        "Translation Not Available",
        "Offline translation for this language direction is not available."
    ),
    UNSUPPORTED_TARGET_VOICE(
        "Voice Not Available",
        "Voice not available for this language"
    ),
    MODEL_MISSING(
        "Language Pack Required",
        "Download the required language packs to translate offline."
    ),
    MODEL_CORRUPTED(
        "Language Pack Needs Repair",
        "The downloaded language pack could not be verified. Please download it again."
    ),
    DOWNLOAD_INTERRUPTED(
        "Download Paused",
        "The download was interrupted. Your existing language packs remain intact."
    ),
    LOW_STORAGE(
        "Not Enough Storage",
        "Free up storage space on your device to download this language pack."
    ),
    MODEL_INCOMPATIBLE_RUNTIME(
        "Update Required",
        "This language pack requires a newer version of Whisly."
    ),
    SPEECH_NOT_DETECTED(
        "Couldn't Hear That",
        "Couldn't hear that. Try again."
    ),
    AUDIO_TOO_QUIET(
        "Audio Too Quiet",
        "Couldn't hear that clearly. Hold the phone closer and try again."
    ),
    AUDIO_CLIPPING(
        "Audio Too Loud",
        "The microphone signal clipped. Speak slightly further from the microphone."
    ),
    STT_LOW_CONFIDENCE(
        "Couldn't Hear That",
        "Couldn't hear that. Try again."
    ),
    STT_FAILURE(
        "Couldn't Hear That",
        "Couldn't hear that. Try again."
    ),
    TRANSLATION_FAILURE(
        "Translation Unavailable",
        "Could not translate this sentence offline. Check that the language packs are downloaded."
    ),
    TTS_FAILURE(
        "Voice Not Available",
        "Voice not available for this language"
    ),
    AUDIO_PLAYBACK_INTERRUPTED(
        "Playback Paused",
        "Audio playback was interrupted. Tap Play to listen again."
    ),
    NETWORK_UNAVAILABLE(
        "Offline",
        "No internet connection. Downloaded language packs continue to work offline."
    ),
    BACKEND_UNAVAILABLE(
        "Download Server Unavailable",
        "Could not reach the language pack server. Downloaded packs continue to work offline."
    ),
    LICENSE_EXPIRED(
        "Subscription Expired",
        "Your organization license has expired. Offline translation remains available."
    )
}

data class LanguageManifestInfo(
    val code: String,
    val indicTransTag: String,
    val englishName: String,
    val nativeName: String,
    val scriptName: String,
    val sttModelId: String,
    val mtModelId: String,
    val ttsModelId: String?,
    val sttAvailable: Boolean,
    val mtAvailable: Boolean,
    val ttsCapability: TtsCapabilityStatus,
    val sttSizeBytes: Long,
    val ttsSizeBytes: Long,
    val sharedMtSizeBytes: Long,
    val version: String,
    val sampleGreetingNative: String,
    val sampleGreetingEnglish: String
)

data class ModelManifest(
    val modelId: String,
    val languageCode: String,
    val taskType: ModelTaskType,
    val version: String,
    val downloadUrl: String,
    val sha256Checksum: String,
    val fileSizeBytes: Long,
    val requiredRuntime: String,
    val minMemoryMb: Int,
    val licenseName: String,
    val attribution: String,
    val dependencies: List<String>,
    val compatibilityInfo: String,
    val signatureHex: String
)

data class ConversationTurn(
    val id: String,
    val speakerLabel: String,
    val isPersonA: Boolean,
    val sourceLangCode: String,
    val targetLangCode: String,
    val originalText: String,
    val translatedText: String,
    val timestamp: Long,
    val confidence: Float,
    val ttsAvailable: Boolean
)
