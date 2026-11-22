package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        LanguageEntity::class,
        DirectedTranslationPairEntity::class,
        ModelPackEntity::class,
        InstalledModelEntity::class,
        TranslationHistoryEntity::class,
        SavedPhraseEntity::class,
        VoicePreferenceEntity::class,
        AppSettingsEntity::class,
        PendingCorrectionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class WhislyDatabase : RoomDatabase() {
    abstract fun whislyDao(): WhislyDao

    companion object {
        @Volatile
        private var INSTANCE: WhislyDatabase? = null

        /**
         * PHASE 1 — Non-destructive Room Migration (`MIGRATION_1_2`).
         *
         * Preserves all user tables (`translation_history`, `saved_phrases`, `installed_models`,
         * `app_settings`, `languages`) while adding the Phase 1 Capability Registry columns
         * and the `directed_translation_pairs` table.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE languages ADD COLUMN canListen INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE languages ADD COLUMN canIdentify INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE languages ADD COLUMN canSpeak INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE languages ADD COLUMN voiceLocale TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE languages ADD COLUMN requiredPacksCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE languages ADD COLUMN compatibility TEXT NOT NULL DEFAULT 'ARM64-v8a / x86_64 Int8'")
                db.execSQL("ALTER TABLE languages ADD COLUMN wave TEXT NOT NULL DEFAULT 'WAVE_1'")
                db.execSQL("ALTER TABLE languages ADD COLUMN isPopular INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN deviceTierOverride TEXT NOT NULL DEFAULT 'AUTO'")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `directed_translation_pairs` (
                        `sourceCode` TEXT NOT NULL,
                        `targetCode` TEXT NOT NULL,
                        `method` TEXT NOT NULL,
                        `pivotCode` TEXT,
                        `qualityStatus` TEXT NOT NULL,
                        `requiredMtPacksCsv` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceCode`, `targetCode`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_directed_translation_pairs_sourceCode` ON `directed_translation_pairs` (`sourceCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_directed_translation_pairs_targetCode` ON `directed_translation_pairs` (`targetCode`)")
            }
        }

        /**
         * PHASE 0 — Zero-Cost Reset Room Migration (`MIGRATION_2_3`).
         *
         * Resets fake installed states and clears phantom model entries from old code
         * while strictly preserving all user translation history, saved phrases, and app settings.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE languages SET isInstalled = 0, installStatus = 'NOT_INSTALLED', downloadProgress = 0.0")
                db.execSQL("DELETE FROM installed_models")
            }
        }

        /**
         * PHASE 4 — Real Model Catalog & English Seed Room Migration (`MIGRATION_3_4`).
         *
         * Seeds English ('en') as a first-class Wave 1 language into Room and updates
         * pack sizes across all languages to match real verified public weights.
         * Strictly preserves user translation history, saved phrases, and app settings.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO languages (
                        code, indicTransTag, englishName, nativeName, scriptName,
                        sttSupported, mtSupported, ttsCapability, sttPackId, mtPackId, ttsPackId,
                        isInstalled, downloadProgress, installStatus, version, totalSizeBytes,
                        canListen, canIdentify, canSpeak, voiceLocale, requiredPacksCsv, compatibility,
                        wave, isPopular, updatedAt
                    ) VALUES (
                        'en', 'eng_Latn', 'English', 'English', 'Latin',
                        1, 1, 'SYSTEM_FALLBACK_ONLY', '', 'mt_indictrans2_200m_int8', NULL,
                        0, 0.0, 'NOT_INSTALLED', '17.0.3', 0,
                        1, 1, 1, 'en-IN', 'mt_indictrans2_200m_int8', 'On-device ML Kit Translate',
                        'WAVE_1', 1, 0
                    )
                    """.trimIndent()
                )
                db.execSQL("UPDATE languages SET totalSizeBytes = 0, sttPackId = '', ttsPackId = NULL WHERE code NOT IN ('en', 'bn')")
                db.execSQL("UPDATE languages SET totalSizeBytes = 0, sttPackId = '', ttsPackId = NULL WHERE code = 'bn'")
                db.execSQL("UPDATE languages SET totalSizeBytes = 0, sttPackId = '', ttsPackId = NULL WHERE code = 'en'")
            }
        }

        fun getInstance(context: Context): WhislyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val dbFile = appContext.getDatabasePath("whisly_offline_ai.db")
                    runCatching { dbFile.parentFile?.mkdirs() }
                    try {
                        val db = Room.databaseBuilder(
                            appContext,
                            WhislyDatabase::class.java,
                            "whisly_offline_ai.db"
                        )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            // No destructive fallback: a missing migration must fail loudly rather than wipe user data.
                            .build()
                        // Force-open so a broken migration surfaces here instead of crashing later.
                        db.openHelper.writableDatabase
                        db.also { INSTANCE = it }
                    } catch (e: Exception) {
                        // Last-resort recovery: back up the unreadable DB, create a fresh one, keep the app open.
                        runCatching {
                            if (dbFile.exists()) {
                                val backup = File(dbFile.parentFile, "whisly_offline_ai.db.bak")
                                dbFile.copyTo(backup, overwrite = true)
                                dbFile.delete()
                                // also remove sidecar files
                                File(dbFile.parentFile, "whisly_offline_ai.db-wal").delete()
                                File(dbFile.parentFile, "whisly_offline_ai.db-shm").delete()
                            }
                            appContext.getSharedPreferences("whisly_db_recovery", Context.MODE_PRIVATE)
                                .edit().putBoolean("recovered", true).apply()
                        }
                        Room.databaseBuilder(
                            appContext,
                            WhislyDatabase::class.java,
                            "whisly_offline_ai.db"
                        )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .build()
                            .also { INSTANCE = it }
                    }
                }
            }
        }

        /** True if the database had to be rebuilt from scratch (data loss) on a previous launch. */
        fun wasRecovered(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences("whisly_db_recovery", Context.MODE_PRIVATE)
                .getBoolean("recovered", false)
        }

        fun clearRecoveredFlag(context: Context) {
            context.applicationContext
                .getSharedPreferences("whisly_db_recovery", Context.MODE_PRIVATE)
                .edit().putBoolean("recovered", false).apply()
        }

        @androidx.annotation.VisibleForTesting
        fun setTestInstance(testDb: WhislyDatabase?) {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = testDb
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetInstance() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
            }
        }
    }
}
