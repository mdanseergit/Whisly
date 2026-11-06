package com.example.ai.translation

import com.example.ai.registry.CapabilityRegistry
import com.example.data.local.LanguageEntity
import com.example.data.local.ModelPackEntity
import com.example.domain.models.LanguageManifestInfo

/**
 * PHASE 1 & PHASE 11 — Delegating facade over [CapabilityRegistry].
 *
 * All hard-coded language lists, `semanticCorpus` tables, `wordLexicon` maps,
 * and sample greeting strings have been removed from `src/main`.
 * [CapabilityRegistry] is the single source of truth for language capabilities
 * and directed translation pairs.
 */
object IndicLanguageCatalog {

    const val SUPPORTED_LANGUAGE_COUNT = 12
    const val SHARED_MT_PACK_ID = CapabilityRegistry.PACK_MT_INDIC_INDIC
    const val SHARED_MT_SIZE_BYTES = CapabilityRegistry.SIZE_MT_INDIC_INDIC_BYTES

    val languages: List<LanguageManifestInfo>
        get() = CapabilityRegistry.indianLanguages.map { CapabilityRegistry.toLanguageManifestInfo(it) }

    fun getLanguageInfo(code: String): LanguageManifestInfo? {
        val entry = CapabilityRegistry.getLanguage(code) ?: return null
        return CapabilityRegistry.toLanguageManifestInfo(entry)
    }

    fun buildInitialLanguageEntities(): List<LanguageEntity> {
        return CapabilityRegistry.buildInitialLanguageEntities(preInstalledCodes = setOf("ta", "bn", "hi"))
    }

    fun buildInitialModelPacks(): List<ModelPackEntity> {
        return CapabilityRegistry.buildInitialModelPacks()
    }
}
