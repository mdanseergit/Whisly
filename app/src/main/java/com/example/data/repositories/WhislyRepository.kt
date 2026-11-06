package com.example.data.repositories

import com.example.ai.modelmanager.WhislyModelManager
import com.example.ai.registry.CapabilityRegistry
import com.example.ai.translation.IndicLanguageCatalog
import com.example.core.security.KeystoreCryptoManager
import com.example.data.local.AppSettingsEntity
import com.example.data.local.DirectedTranslationPairEntity
import com.example.data.local.InstalledModelEntity
import com.example.data.local.LanguageEntity
import com.example.data.local.ModelPackEntity
import com.example.data.local.PendingCorrectionEntity
import com.example.data.local.SavedPhraseEntity
import com.example.data.local.TranslationHistoryEntity
import com.example.data.local.VoicePreferenceEntity
import com.example.data.local.WhislyDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class DecryptedHistoryItem(
    val id: String,
    val sourceLangCode: String,
    val targetLangCode: String,
    val originalText: String,
    val translatedText: String,
    val mode: String,
    val sttConfidence: Float,
    val inferenceLatencyMs: Long,
    val createdAt: Long
)

class WhislyRepository(
    private val dao: WhislyDao,
    val modelManager: WhislyModelManager
) {
    val languagesFlow: Flow<List<LanguageEntity>> = dao.observeLanguages()
    val modelPacksFlow: Flow<List<ModelPackEntity>> = dao.observeModelPacks()
    val installedModelsFlow: Flow<List<InstalledModelEntity>> = dao.observeInstalledModels()
    val savedPhrasesFlow: Flow<List<SavedPhraseEntity>> = dao.observeSavedPhrases()
    val settingsFlow: Flow<AppSettingsEntity?> = dao.observeSettings()
    val pendingCorrectionsFlow: Flow<List<PendingCorrectionEntity>> = dao.observePendingCorrections()
    val voicePreferencesFlow: Flow<List<VoicePreferenceEntity>> = dao.observeVoicePreferences()

    val decryptedHistoryFlow: Flow<List<DecryptedHistoryItem>> = dao.observeHistory().map { list ->
        list.map { entity ->
            DecryptedHistoryItem(
                id = entity.id,
                sourceLangCode = entity.sourceLangCode,
                targetLangCode = entity.targetLangCode,
                originalText = KeystoreCryptoManager.decryptText(entity.originalTextEncrypted),
                translatedText = KeystoreCryptoManager.decryptText(entity.translatedTextEncrypted),
                mode = entity.mode,
                sttConfidence = entity.sttConfidence,
                inferenceLatencyMs = entity.inferenceLatencyMs,
                createdAt = entity.createdAt
            )
        }
    }

    /**
     * Seeds the language registry, directed translation pairs, and model pack catalogue on first launch.
     * Does NOT seed fake user history or starter saved phrases into production user tables.
     */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val existingLangs = dao.getAllLanguages()
        if (existingLangs.isEmpty()) {
            dao.insertLanguages(IndicLanguageCatalog.buildInitialLanguageEntities())
            dao.insertModelPacks(IndicLanguageCatalog.buildInitialModelPacks())
        }
        val existingPairs = dao.getAllDirectedPairs()
        if (existingPairs.isEmpty()) {
            dao.insertDirectedPairs(CapabilityRegistry.buildDirectedPairEntities())
        }
        val existingSettings = dao.getSettingsSnapshot()
        if (existingSettings == null) {
            dao.upsertSettings(AppSettingsEntity())
        }
        modelManager.bootstrapPreinstalledManifests()
    }

    suspend fun getDirectedPair(sourceCode: String, targetCode: String): DirectedTranslationPairEntity? =
        withContext(Dispatchers.IO) {
            dao.getDirectedPair(sourceCode, targetCode)
        }

    suspend fun recordEncryptedHistory(
        sourceLang: String,
        targetLang: String,
        originalText: String,
        translatedText: String,
        mode: String,
        confidence: Float,
        latencyMs: Long
    ) = withContext(Dispatchers.IO) {
        val entry = TranslationHistoryEntity(
            sourceLangCode = sourceLang,
            targetLangCode = targetLang,
            originalTextEncrypted = KeystoreCryptoManager.encryptText(originalText),
            translatedTextEncrypted = KeystoreCryptoManager.encryptText(translatedText),
            mode = mode,
            sttConfidence = confidence,
            inferenceLatencyMs = latencyMs
        )
        dao.insertHistory(entry)
    }

    suspend fun deleteHistoryItem(id: String) = withContext(Dispatchers.IO) {
        dao.deleteHistoryById(id)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
    }

    suspend fun savePhrase(
        sourceLang: String,
        targetLang: String,
        originalText: String,
        translatedText: String,
        category: String = "General",
        phoneticHint: String = ""
    ) = withContext(Dispatchers.IO) {
        dao.insertSavedPhrase(
            SavedPhraseEntity(
                sourceLangCode = sourceLang,
                targetLangCode = targetLang,
                originalText = originalText,
                translatedText = translatedText,
                category = category,
                phoneticHint = phoneticHint
            )
        )
    }

    suspend fun deleteSavedPhrase(id: String) = withContext(Dispatchers.IO) {
        dao.deleteSavedPhraseById(id)
    }

    suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) = withContext(Dispatchers.IO) {
        val current = dao.getSettingsSnapshot() ?: AppSettingsEntity()
        val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
        dao.upsertSettings(updated)
    }

    suspend fun updateVoicePreference(preference: VoicePreferenceEntity) = withContext(Dispatchers.IO) {
        dao.upsertVoicePreference(preference)
    }

    suspend fun recordUserCorrection(
        sourceLang: String,
        targetLang: String,
        originalStt: String,
        correctedStt: String,
        modelTranslated: String,
        correctedTranslated: String?,
        consentedToSync: Boolean
    ) = withContext(Dispatchers.IO) {
        dao.insertPendingCorrection(
            PendingCorrectionEntity(
                sourceLangCode = sourceLang,
                targetLangCode = targetLang,
                originalSttText = originalStt,
                userCorrectedText = correctedStt,
                modelTranslatedText = modelTranslated,
                userCorrectedTranslation = correctedTranslated,
                userConsented = consentedToSync,
                syncedToBackend = false
            )
        )
    }
}
