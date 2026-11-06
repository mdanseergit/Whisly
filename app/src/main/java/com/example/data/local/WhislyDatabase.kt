package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = false
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

        fun getInstance(context: Context): WhislyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WhislyDatabase::class.java,
                    "whisly_offline_ai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
