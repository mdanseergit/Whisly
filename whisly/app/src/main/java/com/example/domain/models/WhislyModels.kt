package com.example.domain.models

enum class ModelTaskType(val label: String) {
    STT("Speech Recognition"),
    MT("Translation"),
    TTS("Speech Synthesis")
}

enum class TtsCapabilityStatus(val label: String) {
    SYSTEM_FALLBACK_ONLY("Device voice, temporary"),
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
    ERROR("Try again")
}

sealed class UnifiedTranslationState {
    data class NeedsPack(val languageName: String, val packSizeDisplay: String) : UnifiedTranslationState()
    object Ready : UnifiedTranslationState()
    data class Listening(val micLevel: Float = 0f) : UnifiedTranslationState()
    object Transcribing : UnifiedTranslationState()
    object Translating : UnifiedTranslationState()
    object Speaking : UnifiedTranslationState()
    data class Error(val errorType: PipelineErrorType, val actionLabel: String? = null, val customMessage: String? = null) : UnifiedTranslationState()
    data class Success(val sourceText: String, val translatedText: String) : UnifiedTranslationState()
}

enum class PipelineErrorType(
    val title: String,
    val userMessage: String,
    val recoverable: Boolean = true
) {
    MIC_PERMISSION_DENIED(
        "Microphone permission needed",
        "Allow microphone access in Settings to translate speech."
    ),
    NO_MICROPHONE_AVAILABLE(
        "No Microphone Found",
        "No microphone detected. Use Text Mode to type."
    ),
    SAME_LANGUAGE_SOURCE_TARGET(
        "Same Language Selected",
        "Choose a different language to translate."
    ),
    MICROPHONE_MUTED(
        "Microphone is blocked or muted",
        "Check the microphone tile in Quick Settings."
    ),
    UNSUPPORTED_SOURCE_LANGUAGE(
        "Speech isn't available for this language yet",
        "You can still translate by typing."
    ),
    UNSUPPORTED_LANGUAGE_DIRECTION(
        "Translation Not Available",
        "This language pair isn't available offline yet."
    ),
    UNSUPPORTED_TARGET_VOICE(
        "Voice not available for this language",
        "Voice not available for this language"
    ),
    MODEL_MISSING(
        "Speech isn't ready for this language yet",
        ""
    ),
    STT_MODEL_MISSING(
        "Speech recognition isn't ready for this language yet",
        "You can still translate by typing."
    ),
    MIC_INIT_FAILED(
        "Microphone unavailable. Check your device.",
        "The microphone could not be opened. Another app may be using it."
    ),
    STT_TIMEOUT(
        "Speech recognition timed out. Try again.",
        "Recognition took too long. Try a shorter phrase."
    ),
    OFFLINE_SPEECH_PACK_MISSING(
        "Speech recognition isn't ready for this language yet",
        "You can still translate by typing."
    ),
    MODEL_CORRUPTED(
        "Language Pack Needs Repair",
        "The downloaded pack could not be verified. Please download again."
    ),
    DOWNLOAD_INTERRUPTED(
        "Download Paused",
        "The download was interrupted. Existing packs remain intact."
    ),
    LOW_STORAGE(
        "Not Enough Storage",
        "Free up storage space to download this pack."
    ),
    MODEL_INCOMPATIBLE_RUNTIME(
        "Update Required",
        "This language pack requires a newer version of Whisly."
    ),
    SPEECH_NOT_DETECTED(
        "Couldn't hear that. Try again.",
        ""
    ),
    AUDIO_TOO_QUIET(
        "Speak a little louder or closer",
        ""
    ),
    AUDIO_CLIPPING(
        "Audio Too Loud",
        "The microphone signal clipped. Speak slightly further away."
    ),
    STT_LOW_CONFIDENCE(
        "Couldn't understand. Try again.",
        ""
    ),
    STT_FAILURE(
        "Couldn't understand. Try again.",
        ""
    ),
    TRANSLATION_FAILURE(
        "Translation Failed",
        "Could not translate this text. Check offline packs and try again."
    ),
    TTS_FAILURE(
        "Voice not available for this language",
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
    val confidence: Float?,
    val ttsAvailable: Boolean
)

sealed interface DownloadState {
    object Idle : DownloadState
    object Queued : DownloadState
    data class Downloading(
        val bytesDone: Long = 0L,
        val bytesTotal: Long = 0L,
        val speedBytesPerSec: Long = 0L,
        val etaSeconds: Long = 0L,
        val indeterminate: Boolean = false
    ) : DownloadState {
        val progress: Float get() = if (indeterminate) 0f else if (bytesTotal > 0L) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    }
    data class Verifying(val bytesHashed: Long, val bytesTotal: Long) : DownloadState {
        val progress: Float get() = if (bytesTotal > 0L) (bytesHashed.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    }
    object Installing : DownloadState
    object Installed : DownloadState
    data class Failed(val reason: String) : DownloadState
    object Paused : DownloadState
    object Cancelled : DownloadState
}
