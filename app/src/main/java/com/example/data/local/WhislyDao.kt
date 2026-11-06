package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WhislyDao {

    // 1. Languages
    @Query("SELECT * FROM languages ORDER BY CASE WHEN code = 'ta' THEN 0 WHEN code = 'bn' THEN 1 WHEN code = 'hi' THEN 2 WHEN code = 'en' THEN 3 ELSE 4 END, englishName ASC")
    fun observeLanguages(): Flow<List<LanguageEntity>>

    @Query("SELECT * FROM languages")
    suspend fun getAllLanguages(): List<LanguageEntity>

    @Query("SELECT * FROM languages WHERE code = :code LIMIT 1")
    suspend fun getLanguageByCode(code: String): LanguageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguages(languages: List<LanguageEntity>)

    @Update
    suspend fun updateLanguage(language: LanguageEntity)

    // 1b. Directed Translation Pairs (Phase 1)
    @Query("SELECT * FROM directed_translation_pairs WHERE sourceCode = :sourceCode AND targetCode = :targetCode LIMIT 1")
    suspend fun getDirectedPair(sourceCode: String, targetCode: String): DirectedTranslationPairEntity?

    @Query("SELECT * FROM directed_translation_pairs")
    suspend fun getAllDirectedPairs(): List<DirectedTranslationPairEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectedPairs(pairs: List<DirectedTranslationPairEntity>)

    // 2. Model Packs
    @Query("SELECT * FROM model_packs ORDER BY languageCode ASC, taskType ASC")
    fun observeModelPacks(): Flow<List<ModelPackEntity>>

    @Query("SELECT * FROM model_packs WHERE languageCode = :langCode OR packId = :sharedMtId")
    suspend fun getPacksForLanguage(langCode: String, sharedMtId: String = "mt_indictrans2_200m_int8"): List<ModelPackEntity>

    @Query("SELECT * FROM model_packs WHERE packId = :packId LIMIT 1")
    suspend fun getModelPackById(packId: String): ModelPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModelPacks(packs: List<ModelPackEntity>)

    // 3. Installed Models
    @Query("SELECT * FROM installed_models ORDER BY installedAt DESC")
    fun observeInstalledModels(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_models")
    suspend fun getInstalledModelsSnapshot(): List<InstalledModelEntity>

    @Query("SELECT * FROM installed_models WHERE packId = :packId LIMIT 1")
    suspend fun getInstalledModelByPackId(packId: String): InstalledModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstalledModel(model: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE packId = :packId")
    suspend fun deleteInstalledModelByPackId(packId: String)

    // 4. Translation History
    @Query("SELECT * FROM translation_history ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<TranslationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: TranslationHistoryEntity)

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("DELETE FROM translation_history")
    suspend fun clearAllHistory()

    // 5. Saved Phrases
    @Query("SELECT * FROM saved_phrases ORDER BY createdAt DESC")
    fun observeSavedPhrases(): Flow<List<SavedPhraseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPhrase(phrase: SavedPhraseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedPhrases(phrases: List<SavedPhraseEntity>)

    @Query("DELETE FROM saved_phrases WHERE id = :id")
    suspend fun deleteSavedPhraseById(id: String)

    // 6. Voice Preferences
    @Query("SELECT * FROM voice_preferences")
    fun observeVoicePreferences(): Flow<List<VoicePreferenceEntity>>

    @Query("SELECT * FROM voice_preferences WHERE languageCode = :langCode LIMIT 1")
    suspend fun getVoicePreference(langCode: String): VoicePreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoicePreference(preference: VoicePreferenceEntity)

    // 7. App Settings
    @Query("SELECT * FROM app_settings WHERE id = 'default_settings' LIMIT 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 'default_settings' LIMIT 1")
    suspend fun getSettingsSnapshot(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)

    // 8. Pending Corrections
    @Query("SELECT * FROM pending_corrections ORDER BY createdAt DESC")
    fun observePendingCorrections(): Flow<List<PendingCorrectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingCorrection(correction: PendingCorrectionEntity)

    @Query("DELETE FROM pending_corrections WHERE syncedToBackend = 1")
    suspend fun clearSyncedCorrections()
}
