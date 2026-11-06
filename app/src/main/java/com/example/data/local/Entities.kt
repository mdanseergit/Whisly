package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "languages",
    indices = [Index(value = ["code"], unique = true)]
)
data class LanguageEntity(
    @PrimaryKey val code: String,
    val indicTransTag: String,
    val englishName: String,
    val nativeName: String,
    val scriptName: String,
    val sttSupported: Boolean,
    val mtSupported: Boolean,
    val ttsCapability: String, // VITS_RASA_VERIFIED, SYSTEM_FALLBACK_ONLY, UNAVAILABLE
    val sttPackId: String,
    val mtPackId: String,
    val ttsPackId: String?,
    val isInstalled: Boolean,
    val downloadProgress: Float = 0f,
    val installStatus: String = "NOT_INSTALLED",
    val version: String,
    val totalSizeBytes: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    // Phase 1 Capability Registry columns
    val canListen: Boolean = sttSupported,
    val canIdentify: Boolean = true,
    val canSpeak: Boolean = ttsCapability != "UNAVAILABLE",
    val voiceLocale: String = "$code-IN",
    val requiredPacksCsv: String = listOfNotNull(
        sttPackId.takeIf { it.isNotBlank() },
        mtPackId.takeIf { it.isNotBlank() },
        ttsPackId
    ).joinToString(","),
    val compatibility: String = "ARM64-v8a / x86_64 Int8",
    val wave: String = "WAVE_1",
    val isPopular: Boolean = true
)

/**
 * PHASE 1 — Directed Translation Pair Entity (`directed_translation_pairs`).
 *
 * Stores translation strictly as directed pairs `(sourceCode, targetCode)` with
 * `method = DIRECT | PIVOT_EN` and `qualityStatus = VERIFIED | BELOW_THRESHOLD | UNSUPPORTED`.
 * Never infers the reverse direction automatically.
 */
@Entity(
    tableName = "directed_translation_pairs",
    primaryKeys = ["sourceCode", "targetCode"],
    indices = [Index(value = ["sourceCode"]), Index(value = ["targetCode"])]
)
data class DirectedTranslationPairEntity(
    val sourceCode: String,
    val targetCode: String,
    val method: String, // DIRECT, PIVOT_EN
    val pivotCode: String?,
    val qualityStatus: String, // VERIFIED, BELOW_THRESHOLD, UNSUPPORTED
    val requiredMtPacksCsv: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "model_packs",
    indices = [Index(value = ["languageCode", "taskType"])]
)
data class ModelPackEntity(
    @PrimaryKey val packId: String,
    val languageCode: String, // or "indic_shared" for shared IndicTrans2 MT model
    val taskType: String, // STT, MT, TTS
    val modelArchitecture: String,
    val version: String,
    val downloadUrl: String,
    val sha256Checksum: String,
    val fileSizeBytes: Long,
    val requiredRuntime: String,
    val minMemoryMb: Int,
    val license: String,
    val attribution: String,
    val dependenciesCsv: String,
    val compatibilityInfo: String,
    val manifestSignature: String,
    val isSharedResource: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "installed_models",
    indices = [Index(value = ["packId"], unique = true)]
)
data class InstalledModelEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val packId: String,
    val languageCode: String,
    val taskType: String,
    val installedVersion: String,
    val localFilePath: String,
    val verifiedSha256: String,
    val fileSizeBytes: Long,
    val isVerified: Boolean,
    val installedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "translation_history",
    indices = [Index(value = ["createdAt"])]
)
data class TranslationHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceLangCode: String,
    val targetLangCode: String,
    val originalTextEncrypted: String,
    val translatedTextEncrypted: String,
    val mode: String, // VOICE, CONVERSATION, TEXT
    val sttConfidence: Float,
    val inferenceLatencyMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_phrases",
    indices = [Index(value = ["category"]), Index(value = ["createdAt"])]
)
data class SavedPhraseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceLangCode: String,
    val targetLangCode: String,
    val originalText: String,
    val translatedText: String,
    val phoneticHint: String = "",
    val category: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "voice_preferences"
)
data class VoicePreferenceEntity(
    @PrimaryKey val languageCode: String,
    val selectedVoiceId: String,
    val voiceGender: String, // FEMALE, MALE
    val speechSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val useSystemFallbackIfNeuralMissing: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "app_settings"
)
data class AppSettingsEntity(
    @PrimaryKey val id: String = "default_settings",
    val onboardingCompleted: Boolean = false,
    val defaultSourceLang: String = "ta",
    val defaultTargetLang: String = "bn",
    val offlineOnlyMode: Boolean = true,
    val speechSpeed: Float = 1.0f,
    val autoPlayTranslatedSpeech: Boolean = true,
    val downloadOverWifiOnly: Boolean = true,
    val consentToCorrectionSync: Boolean = false,
    val backendBaseUrl: String = "https://api.whisly.ai/api/v1",
    val organizationCode: String = "",
    val licenseStatus: String = "COMMUNITY_OFFLINE_VERIFIED",
    val deviceTierOverride: String = "AUTO", // AUTO, LITE, STANDARD, ADVANCED
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pending_corrections"
)
data class PendingCorrectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceLangCode: String,
    val targetLangCode: String,
    val originalSttText: String,
    val userCorrectedText: String,
    val modelTranslatedText: String,
    val userCorrectedTranslation: String?,
    val userConsented: Boolean,
    val syncedToBackend: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
