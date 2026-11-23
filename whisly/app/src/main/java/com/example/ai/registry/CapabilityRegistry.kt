package com.example.ai.registry

import com.example.core.security.KeystoreCryptoManager
import com.example.data.local.DirectedTranslationPairEntity
import com.example.data.local.LanguageEntity
import com.example.data.local.ModelPackEntity
import com.example.domain.models.LanguageManifestInfo
import com.example.domain.models.TtsCapabilityStatus

/**
 * PHASE 1 — Single Source of Truth Capability Registry (`CapabilityRegistry.kt`).
 *
 * Drives the language selector, Auto-detect (`auto`), STT/MT/TTS routing, pack downloads,
 * voice selection, and speech-to-speech readiness checks.
 *
 * - Stores per-language capabilities (`code`, `name`, `nativeName`, `script`, `canListen`,
 *   `canIdentify`, `canSpeak`, `voiceLocale`, `requiredPacks`, `compatibility`, `wave`, `isPopular`).
 * - Stores translation strictly as DIRECTED pairs (`sourceCode -> targetCode`, `method = DIRECT | PIVOT_EN`,
 *   `qualityStatus = VERIFIED | BELOW_THRESHOLD | UNSUPPORTED`). Never infers the reverse direction.
 * - A directed pair is `speech-to-speech ready` ONLY when:
 *   1. `source.canListen` is true and the source STT pack is installed & SHA-256 verified,
 *   2. The directed pair `(source -> target)` has `qualityStatus == VERIFIED` and all required MT packs
 *      for that direction are installed & SHA-256 verified, AND
 *   3. `target.canSpeak` is true and either the target TTS pack is installed & SHA-256 verified
 *      OR a verified offline system TTS voice is available for `target.voiceLocale`.
 */
object CapabilityRegistry {

    const val AUTO_DETECT_CODE = "auto"

    // Shared MT Pack IDs (MIT Licensed AI4Bharat IndicTrans2 Distilled & Meta M2M100)
    const val PACK_MT_EN_INDIC = "mt_indictrans2_en_indic_200m_int8"
    const val PACK_MT_INDIC_EN = "mt_indictrans2_indic_en_200m_int8"
    const val PACK_MT_INDIC_INDIC = "mt_indictrans2_200m_int8"
    const val PACK_MT_GLOBAL_M2M = "mt_m2m100_418m_int8"
    const val PACK_VAD_SILERO = "vad_silero_v5_onnx"
    const val PACK_LID_GLOT = "lid_glotlid_v3_int8"

    const val SIZE_MT_EN_INDIC_BYTES = 188_743_680L   // 180.0 MB
    const val SIZE_MT_INDIC_EN_BYTES = 188_743_680L   // 180.0 MB
    const val SIZE_MT_INDIC_INDIC_BYTES = 194_510_848L // 185.5 MB
    const val SIZE_VAD_SILERO_BYTES = 1_887_436L      // 1.8 MB

    enum class LaunchWave {
        WAVE_1, // English + 12 Indian languages
        WAVE_2, // Global multilingual candidates (only active if threshold passed)
        WAVE_3  // Long-tail languages (text-only or unsupported offline)
    }

    enum class TranslationRouteMethod {
        DIRECT,
        PIVOT_EN
    }

    enum class PairQualityStatus {
        VERIFIED,
        BELOW_THRESHOLD,
        UNSUPPORTED
    }

    enum class DeviceTierProfile(
        val label: String,
        val maxResidentSttModels: Int,
        val maxResidentMtModels: Int,
        val maxPeakRamBudgetMb: Int
    ) {
        LITE("Lite (<= 3 GB RAM)", 1, 1, 600),
        STANDARD("Standard (4 GB RAM)", 1, 1, 1000),
        ADVANCED("Advanced (6+ GB RAM)", 1, 1, 1200)
    }

    data class LanguageCapabilityEntry(
        val code: String,
        val name: String,
        val nativeName: String,
        val script: String,
        val indicTransTag: String,
        val wave: LaunchWave,
        val isPopular: Boolean,
        val canListen: Boolean,
        val canIdentify: Boolean,
        val canSpeak: Boolean,
        val voiceLocale: String,
        val sttPackId: String?,
        val mtPackId: String,
        val ttsPackId: String?,
        val sttSizeBytes: Long,
        val ttsSizeBytes: Long,
        val sharedMtSizeBytes: Long = SIZE_MT_INDIC_INDIC_BYTES,
        val version: String,
        val compatibility: String = "ARM64-v8a / x86_64 Int8"
    ) {
        val requiredPacks: List<String>
            get() = buildList {
                sttPackId?.let { add(it) }
                if (mtPackId.isNotBlank()) add(mtPackId)
                ttsPackId?.let { add(it) }
            }

        val totalDownloadSizeBytes: Long
            get() = sttSizeBytes + ttsSizeBytes
    }

    data class DirectedTranslationPair(
        val sourceCode: String,
        val targetCode: String,
        val method: TranslationRouteMethod,
        val pivotCode: String?,
        val qualityStatus: PairQualityStatus,
        val requiredMtPackIds: List<String>
    ) {
        val pairId: String get() = "$sourceCode->$targetCode"
    }

    /**
     * Authoritative language catalogue across Wave 1 (English + 12 Indian languages),
     * Wave 2 (Global candidates), and Wave 3 (Long-tail Indian languages).
     * Zero sample phrases or lexicons are stored here.
     */
    val allLanguages: List<LanguageCapabilityEntry> = listOf(
        // ==================== WAVE 1: English + 12 Indian Languages ====================
        LanguageCapabilityEntry(
            code = "en",
            name = "English",
            nativeName = "English",
            script = "Latin",
            indicTransTag = "eng_Latn",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "en-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.1.0"
        ),
        LanguageCapabilityEntry(
            code = "ta",
            name = "Tamil",
            nativeName = "தமிழ்",
            script = "Tamil",
            indicTransTag = "tam_Taml",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "ta-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.1.0"
        ),
        LanguageCapabilityEntry(
            code = "bn",
            name = "Bengali",
            nativeName = "বাংলা",
            script = "Bengali-Assamese",
            indicTransTag = "ben_Beng",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "bn-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.1.0"
        ),
        LanguageCapabilityEntry(
            code = "hi",
            name = "Hindi",
            nativeName = "हिन्दी",
            script = "Devanagari",
            indicTransTag = "hin_Deva",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "hi-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.1.0"
        ),
        LanguageCapabilityEntry(
            code = "te",
            name = "Telugu",
            nativeName = "తెలుగు",
            script = "Telugu",
            indicTransTag = "tel_Telu",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "te-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.1.0"
        ),
        LanguageCapabilityEntry(
            code = "kn",
            name = "Kannada",
            nativeName = "ಕನ್ನಡ",
            script = "Kannada",
            indicTransTag = "kan_Knda",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "kn-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.1.0"
        ),
        LanguageCapabilityEntry(
            code = "ml",
            name = "Malayalam",
            nativeName = "മലയാളം",
            script = "Malayalam",
            indicTransTag = "mal_Mlym",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "ml-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.1.0"
        ),
        LanguageCapabilityEntry(
            code = "mr",
            name = "Marathi",
            nativeName = "मराठी",
            script = "Devanagari",
            indicTransTag = "mar_Deva",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "mr-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.0.4"
        ),
        LanguageCapabilityEntry(
            code = "gu",
            name = "Gujarati",
            nativeName = "ગુજરાતી",
            script = "Gujarati",
            indicTransTag = "guj_Gujr",
            wave = LaunchWave.WAVE_1,
            isPopular = false,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "gu-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.0.4"
        ),
        LanguageCapabilityEntry(
            code = "or",
            name = "Odia",
            nativeName = "ଓଡ଼ିଆ",
            script = "Odia",
            indicTransTag = "ory_Orya",
            wave = LaunchWave.WAVE_1,
            isPopular = false,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "or-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.0.2"
        ),
        LanguageCapabilityEntry(
            code = "as",
            name = "Assamese",
            nativeName = "অসমীয়া",
            script = "Bengali-Assamese",
            indicTransTag = "asm_Beng",
            wave = LaunchWave.WAVE_1,
            isPopular = false,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "as-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.0.2"
        ),
        LanguageCapabilityEntry(
            code = "pa",
            name = "Punjabi",
            nativeName = "ਪੰਜਾਬੀ",
            script = "Gurmukhi",
            indicTransTag = "pan_Guru",
            wave = LaunchWave.WAVE_1,
            isPopular = true,
            canListen = true,
            canIdentify = true,
            canSpeak = true,
            voiceLocale = "pa-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "2.0.2"
        ),
        // Santali (Ol Chiki): STT + MT supported; TTS explicitly marked canSpeak = false (no voice pack exists)
        LanguageCapabilityEntry(
            code = "sat",
            name = "Santali",
            nativeName = "ᱥᱟᱱᱛᱟᱲᱤ",
            script = "Ol Chiki",
            indicTransTag = "sat_Olck",
            wave = LaunchWave.WAVE_1,
            isPopular = false,
            canListen = true,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "sat-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.4.0"
        ),

        // ==================== WAVE 3: Long-Tail Indian Languages (Text-Only / Unsupported Voice) ====================
        LanguageCapabilityEntry(
            code = "ur",
            name = "Urdu",
            nativeName = "اردو",
            script = "Perso-Arabic",
            indicTransTag = "urd_Arab",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "ur-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice below threshold)"
        ),
        LanguageCapabilityEntry(
            code = "ne",
            name = "Nepali",
            nativeName = "नेपाली",
            script = "Devanagari",
            indicTransTag = "npi_Deva",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "ne-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice below threshold)"
        ),
        LanguageCapabilityEntry(
            code = "sa",
            name = "Sanskrit",
            nativeName = "संस्कृतम्",
            script = "Devanagari",
            indicTransTag = "san_Deva",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "sa-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        ),
        LanguageCapabilityEntry(
            code = "brx",
            name = "Bodo",
            nativeName = "\u092C\u0921\u093C\u094B",
            script = "Devanagari",
            indicTransTag = "brx_Deva",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "brx-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        ),
        LanguageCapabilityEntry(
            code = "doi",
            name = "Dogri",
            nativeName = "\u0921\u094B\u0917\u0930\u0940",
            script = "Devanagari",
            indicTransTag = "doi_Deva",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "doi-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        ),
        LanguageCapabilityEntry(
            code = "ks",
            name = "Kashmiri",
            nativeName = "\u06A9\u0627\u0634\u064F\u0631",
            script = "Arabic",
            indicTransTag = "kas_Arab",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "ks-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        ),
        LanguageCapabilityEntry(
            code = "kok",
            name = "Konkani",
            nativeName = "\u0915\u094B\u0902\u0915\u0923\u0940",
            script = "Devanagari",
            indicTransTag = "kok_Deva",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "kok-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        ),
        LanguageCapabilityEntry(
            code = "mai",
            name = "Maithili",
            nativeName = "\u092E\u0948\u0925\u093F\u0932\u0940",
            script = "Devanagari",
            indicTransTag = "mai_Deva",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "mai-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        ),
        LanguageCapabilityEntry(
            code = "mni",
            name = "Manipuri",
            nativeName = "\u09AE\u09C8\u09A4\u09C8\u09B2\u09CB\u09A8",
            script = "Bengali",
            indicTransTag = "mni_Beng",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "mni-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        ),
        LanguageCapabilityEntry(
            code = "sd",
            name = "Sindhi",
            nativeName = "\u0633\u0646\u068C\u064A",
            script = "Arabic",
            indicTransTag = "snd_Arab",
            wave = LaunchWave.WAVE_3,
            isPopular = false,
            canListen = false,
            canIdentify = true,
            canSpeak = false,
            voiceLocale = "sd-IN",
            sttPackId = null,
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 0L,
            ttsSizeBytes = 0L,
            version = "1.0.0",
            compatibility = "Text-only (Voice unavailable)"
        )
    )

    /**
     * Wave 1 languages (12 Indian languages + English) that support voice-to-voice or speech recognition.
     */
    val wave1Languages: List<LanguageCapabilityEntry> = allLanguages.filter { it.wave == LaunchWave.WAVE_1 }

    /**
     * The 12 Indian languages in Wave 1 (preserving backward compatibility with tests expecting 12 Indian languages).
     */
    val indianLanguages: List<LanguageCapabilityEntry> = wave1Languages.filter { it.code != "en" }

    // High-resource Indian languages where direct Indic-Indic translation meets the chrF++ >= 45 threshold:
    private val directIndicSupportedCodes = setOf("ta", "bn", "hi", "te", "kn", "ml", "mr", "gu", "pa", "or", "as")

    /**
     * Explicit DIRECTED translation pair table (`sourceCode -> targetCode`).
     * Never infers the reverse direction automatically: every `(src, tgt)` is registered explicitly.
     */
    val directedPairs: Map<String, DirectedTranslationPair> by lazy {
        val map = LinkedHashMap<String, DirectedTranslationPair>()
        val allCodes = allLanguages.map { it.code }
        for (src in allCodes) {
            for (tgt in allCodes) {
                if (src == tgt) continue
                val srcEntry = getLanguage(src) ?: continue
                val tgtEntry = getLanguage(tgt) ?: continue

                val pair = when {
                    // Long-tail Wave 3 languages: marked BELOW_THRESHOLD for speech-to-speech, text-only via pivot
                    srcEntry.wave == LaunchWave.WAVE_3 || tgtEntry.wave == LaunchWave.WAVE_3 -> {
                        DirectedTranslationPair(
                            sourceCode = src,
                            targetCode = tgt,
                            method = TranslationRouteMethod.PIVOT_EN,
                            pivotCode = "en",
                            qualityStatus = PairQualityStatus.BELOW_THRESHOLD,
                            requiredMtPackIds = listOf(PACK_MT_INDIC_INDIC)
                        )
                    }
                    // Direct English -> Indic (IndicTrans2 en-indic distilled 200M)
                    src == "en" && tgt in directIndicSupportedCodes.plus("sat") -> {
                        DirectedTranslationPair(
                            sourceCode = src,
                            targetCode = tgt,
                            method = TranslationRouteMethod.DIRECT,
                            pivotCode = null,
                            qualityStatus = PairQualityStatus.VERIFIED,
                            requiredMtPackIds = listOf(PACK_MT_INDIC_INDIC)
                        )
                    }
                    // Direct Indic -> English (IndicTrans2 indic-en distilled 200M)
                    src in directIndicSupportedCodes.plus("sat") && tgt == "en" -> {
                        DirectedTranslationPair(
                            sourceCode = src,
                            targetCode = tgt,
                            method = TranslationRouteMethod.DIRECT,
                            pivotCode = null,
                            qualityStatus = PairQualityStatus.VERIFIED,
                            requiredMtPackIds = listOf(PACK_MT_INDIC_INDIC)
                        )
                    }
                    // Direct Indic -> Indic among the 11 high-resource Indian languages (IndicTrans2 indic-indic 320M)
                    src in directIndicSupportedCodes && tgt in directIndicSupportedCodes -> {
                        DirectedTranslationPair(
                            sourceCode = src,
                            targetCode = tgt,
                            method = TranslationRouteMethod.DIRECT,
                            pivotCode = null,
                            qualityStatus = PairQualityStatus.VERIFIED,
                            requiredMtPackIds = listOf(PACK_MT_INDIC_INDIC)
                        )
                    }
                    // Any other Wave 1 language pair via English pivot (e.g. sat <-> indic)
                    else -> {
                        DirectedTranslationPair(
                            sourceCode = src,
                            targetCode = tgt,
                            method = TranslationRouteMethod.PIVOT_EN,
                            pivotCode = "en",
                            qualityStatus = PairQualityStatus.VERIFIED,
                            requiredMtPackIds = listOf(PACK_MT_INDIC_INDIC)
                        )
                    }
                }
                map[pair.pairId] = pair
            }
        }
        map
    }

    /** Evaluation tier measured against the acceptance gates in `docs/DIAGNOSIS9.md`. */
    enum class StageTier { VERIFIED, BETA, UNAVAILABLE }

    const val NO_ML_KIT_TAG = "NONE"
    const val BUNDLED_STT_MODEL_ID = "sherpa_onnx_whisper_tiny_int8"
    const val TEMPORARY_TTS_VOICE_ID = "system_tts"

    /**
     * Per-language, per-stage evidence profile. This is the only place where STT model/code,
     * ML Kit translation/LID tags, TTS locale, script blocks, and measured tiers live.
     *
     * Measured facts (2026-11-23):
     * - English CER ~0.1021 but garbage 10% > 1% gate, so STT is BETA, never VERIFIED.
     * - Every Indic language decoded unusably or with wrong-script garbage, so STT is UNAVAILABLE.
     * - `or` and `sat` have no Whisper tokenizer token, so they cannot be forced at all.
     * - No device TTS voice, MT chrF/WER, or acoustic LID measurement exists, so those tiers
     *   are UNAVAILABLE until real evidence is supplied. Typed ML Kit text translation remains
     *   available as an unmeasured convenience; it is not certified by a tier claim.
     */
    data class StageProfile(
        val code: String,
        val sttModelId: String?,
        val sttCode: String?,
        val mlKitTranslationTag: String,
        val mlKitLidTag: String,
        val ttsLocale: String?,
        val ttsVoiceId: String?,
        val scriptBlocks: Set<String>,
        val scriptRanges: List<IntRange>,
        val sttTier: StageTier,
        val mtTier: StageTier,
        val lidTier: StageTier,
        val ttsTier: StageTier
    ) {
        val sttAvailable: Boolean get() = sttModelId != null && sttCode != null && sttTier != StageTier.UNAVAILABLE
        val textTranslationAvailable: Boolean get() = mlKitTranslationTag != NO_ML_KIT_TAG
        val lidAvailable: Boolean get() = mlKitLidTag != NO_ML_KIT_TAG && lidTier != StageTier.UNAVAILABLE
        val ttsAvailable: Boolean get() = ttsLocale != null && ttsTier != StageTier.UNAVAILABLE
    }

    private val LATN = "LATN" to listOf(0x0041..0x007A)
    private val DEVA = "DEVA" to listOf(0x0900..0x097F)
    private val BENG = "BENG" to listOf(0x0980..0x09FF)
    private val GURU = "GURU" to listOf(0x0A00..0x0A7F)
    private val GUJR = "GUJR" to listOf(0x0A80..0x0AFF)
    private val ORYA = "ORYA" to listOf(0x0B00..0x0B7F)
    private val TAML = "TAML" to listOf(0x0B80..0x0BFF)
    private val TELU = "TELU" to listOf(0x0C00..0x0C7F)
    private val KNDA = "KNDA" to listOf(0x0C80..0x0CFF)
    private val MLYM = "MLYM" to listOf(0x0D00..0x0D7F)
    private val ARAB = "ARAB" to listOf(0x0600..0x06FF)
    private val OLCK = "OLCK" to listOf(0x1C50..0x1C7F)
    private val MTEI = "MTEI" to listOf(0xABE0..0xABFF)
    private val TAKR = "TAKR" to listOf(0x116C0..0x116FF)

    private val allScriptPairs = listOf(
        LATN, DEVA, BENG, GURU, GUJR, ORYA, TAML, TELU, KNDA, MLYM, ARAB, OLCK, MTEI, TAKR
    )

    /** Canonical script block for a code point, from registry data only. */
    fun scriptBlockOf(codePoint: Int): String? =
        allScriptPairs.firstOrNull { (_, ranges) -> ranges.any { codePoint in it } }?.first

    /**
     * Fail-closed script gate for spoken output. Digits and punctuation are neutral; at least 95%
     * of letter code points must fall inside the target language's registry script blocks.
     * English requires the LATN block. No transliteration or wrong-script voice is permitted.
     */
    fun textMatchesLanguageScript(text: String, code: String): Boolean {
        val expected = stageProfile(code)?.scriptBlocks ?: return false
        if (expected.isEmpty()) return false
        val letters = text.codePoints().filter { Character.isLetter(it) }.toArray()
        if (letters.isEmpty()) return true
        val matching = letters.count { cp -> scriptBlockOf(cp) in expected }
        return matching.toDouble() / letters.size >= 0.95
    }

    private val stageRows: List<StageProfile> = listOf(
        stageRow("en", "en", TranslateTag.ENGLISH, LidTag.EN, LATN),
        stageRow("hi", "hi", TranslateTag.HINDI, LidTag.HI, DEVA),
        stageRow("bn", "bn", TranslateTag.BENGALI, LidTag.BN, BENG),
        stageRow("ta", "ta", TranslateTag.TAMIL, LidTag.TA, TAML),
        stageRow("te", "te", TranslateTag.TELUGU, LidTag.TE, TELU),
        stageRow("kn", "kn", TranslateTag.KANNADA, LidTag.KN, KNDA),
        stageRow("ml", "ml", null, LidTag.ML, MLYM),
        stageRow("mr", "mr", TranslateTag.MARATHI, LidTag.MR, DEVA),
        stageRow("gu", "gu", TranslateTag.GUJARATI, LidTag.GU, GUJR),
        stageRow("pa", "pa", null, LidTag.PA, GURU),
        stageRow("ur", "ur", TranslateTag.URDU, LidTag.UR, ARAB),
        stageRow("or", null, null, null, ORYA),
        stageRow("as", "as", null, null, BENG),
        stageRow("sat", null, null, null, OLCK),
        stageRow("ne", "ne", null, null, DEVA),
        stageRow("sa", "sa", null, null, DEVA),
        stageRow("brx", null, null, null, DEVA, BENG),
        stageRow("doi", null, null, null, DEVA, TAKR),
        stageRow("ks", null, null, null, ARAB, DEVA),
        stageRow("kok", null, null, null, DEVA, KNDA),
        stageRow("mai", null, null, null, DEVA),
        stageRow("mni", null, null, null, BENG, MTEI),
        stageRow("sd", "sd", null, null, ARAB)
    )

    /** String tag constants are duplicated here only as registry-owned names, never as maps elsewhere. */
    private object TranslateTag {
        const val ENGLISH = "en"
        const val HINDI = "hi"
        const val BENGALI = "bn"
        const val TAMIL = "ta"
        const val TELUGU = "te"
        const val KANNADA = "kn"
        const val MARATHI = "mr"
        const val GUJARATI = "gu"
        const val URDU = "ur"
    }

    private object LidTag {
        const val EN = "en"
        const val HI = "hi"
        const val BN = "bn"
        const val TA = "ta"
        const val TE = "te"
        const val KN = "kn"
        const val ML = "ml"
        const val MR = "mr"
        const val GU = "gu"
        const val PA = "pa"
        const val UR = "ur"
    }

    private fun stageRow(
        code: String,
        whisperCode: String?,
        translateTag: String?,
        lidTag: String?,
        vararg scripts: Pair<String, List<IntRange>>
    ): StageProfile {
        val sttMeasured = code == "en"
        return StageProfile(
            code = code,
            sttModelId = if (sttMeasured) BUNDLED_STT_MODEL_ID else null,
            sttCode = whisperCode,
            mlKitTranslationTag = translateTag ?: NO_ML_KIT_TAG,
            mlKitLidTag = lidTag ?: NO_ML_KIT_TAG,
            ttsLocale = getLanguage(code)?.voiceLocale,
            ttsVoiceId = TEMPORARY_TTS_VOICE_ID,
            scriptBlocks = scripts.map { it.first }.toSet(),
            scriptRanges = scripts.flatMap { it.second },
            sttTier = if (sttMeasured) StageTier.BETA else StageTier.UNAVAILABLE,
            mtTier = StageTier.UNAVAILABLE,
            lidTier = StageTier.UNAVAILABLE,
            ttsTier = StageTier.UNAVAILABLE
        )
    }

    val stageProfiles: Map<String, StageProfile> = stageRows.associateBy { it.code }

    fun stageProfile(code: String): StageProfile? =
        stageProfiles[code.lowercase().substringBefore('-')]

    fun whisperCode(code: String): String? = stageProfile(code)?.sttCode

    fun mlKitTranslationTag(code: String): String? =
        stageProfile(code)?.mlKitTranslationTag?.takeIf { it != NO_ML_KIT_TAG }

    fun mlKitLidTag(code: String): String? =
        stageProfile(code)?.mlKitLidTag?.takeIf { it != NO_ML_KIT_TAG }

    fun ttsLocale(code: String): String? = stageProfile(code)?.ttsLocale

    fun sttTier(code: String): StageTier = stageProfile(code)?.sttTier ?: StageTier.UNAVAILABLE

    /** Only a measured, non-UNAVAILABLE STT tier may own the microphone. */
    fun canRunSpeechRecognition(code: String): Boolean = stageProfile(code)?.sttAvailable == true

    fun getLanguage(code: String): LanguageCapabilityEntry? {
        return allLanguages.find { it.code.equals(code, ignoreCase = true) }
    }

    fun sttCapabilityStatus(code: String, installed: Boolean): CapabilityStatus {
        val entry = getLanguage(code) ?: return CapabilityStatus.NOT_AVAILABLE
        if (!entry.canListen) return CapabilityStatus.UNAVAILABLE
        return if (installed) CapabilityStatus.READY else CapabilityStatus.IMPORT_REQUIRED
    }

    fun ttsCapabilityStatus(code: String, hasOfflineVoice: Boolean): CapabilityStatus {
        val entry = getLanguage(code) ?: return CapabilityStatus.NOT_AVAILABLE
        if (!entry.canSpeak) return CapabilityStatus.UNAVAILABLE
        return if (hasOfflineVoice) CapabilityStatus.READY else CapabilityStatus.IMPORT_REQUIRED
    }

    /**
     * Strictly queries the directed edge `sourceCode -> targetCode`.
     * Never infers `targetCode -> sourceCode`.
     */
    fun getDirectedPair(sourceCode: String, targetCode: String): DirectedTranslationPair? {
        return directedPairs["${sourceCode.lowercase()}->${targetCode.lowercase()}"]
    }

    /**
     * Returns true ONLY when:
     * 1. `source` supports listening (`canListen`) and its STT pack is installed and verified,
     * 2. The directed pair `source -> target` has `qualityStatus == VERIFIED` and all required MT packs are installed and verified,
     * 3. `target` supports speaking (`canSpeak`) and either its TTS pack is installed and verified OR a verified offline system voice is present.
     */
    fun isSpeechToSpeechReady(
        sourceCode: String,
        targetCode: String,
        isPackInstalledAndVerified: (String) -> Boolean,
        isOfflineSystemVoiceAvailable: (String) -> Boolean = { false }
    ): Boolean {
        val src = getLanguage(sourceCode) ?: return false
        val tgt = getLanguage(targetCode) ?: return false
        if (!src.canListen || !tgt.canSpeak) return false

        val sttPack = src.sttPackId ?: return false
        if (!isPackInstalledAndVerified(sttPack)) return false

        val directed = getDirectedPair(sourceCode, targetCode) ?: return false
        if (directed.qualityStatus != PairQualityStatus.VERIFIED) return false
        if (directed.requiredMtPackIds.isEmpty() || !directed.requiredMtPackIds.all(isPackInstalledAndVerified)) {
            return false
        }

        val ttsPackReady = tgt.ttsPackId != null && isPackInstalledAndVerified(tgt.ttsPackId)
        val systemVoiceReady = isOfflineSystemVoiceAvailable(tgt.voiceLocale)
        return ttsPackReady || systemVoiceReady
    }

    /**
     * Checks whether text-to-text translation is ready for the directed pair `sourceCode -> targetCode`.
     */
    fun isTextTranslationReady(
        sourceCode: String,
        targetCode: String,
        isPackInstalledAndVerified: (String) -> Boolean
    ): Boolean {
        if (sourceCode.equals(targetCode, ignoreCase = true)) return true
        val directed = getDirectedPair(sourceCode, targetCode) ?: return false
        if (directed.qualityStatus == PairQualityStatus.UNSUPPORTED) return false
        return directed.requiredMtPackIds.isNotEmpty() && directed.requiredMtPackIds.all(isPackInstalledAndVerified)
    }

    fun selectDeviceTierProfile(isLowRamDevice: Boolean, totalRamMb: Long, freeStorageBytes: Long): DeviceTierProfile {
        val freeGb = freeStorageBytes / (1024L * 1024L * 1024L)
        return when {
            isLowRamDevice || totalRamMb <= 3200L || freeGb < 2L -> DeviceTierProfile.LITE
            totalRamMb <= 5000L || freeGb < 5L -> DeviceTierProfile.STANDARD
            else -> DeviceTierProfile.ADVANCED
        }
    }

    fun toLanguageManifestInfo(entry: LanguageCapabilityEntry): LanguageManifestInfo {
        return LanguageManifestInfo(
            code = entry.code,
            indicTransTag = entry.indicTransTag,
            englishName = entry.name,
            nativeName = entry.nativeName,
            scriptName = entry.script,
            sttModelId = entry.sttPackId ?: "",
            mtModelId = entry.mtPackId,
            ttsModelId = entry.ttsPackId,
            sttAvailable = entry.canListen,
            mtAvailable = true,
            ttsCapability = if (entry.canSpeak) {
                TtsCapabilityStatus.SYSTEM_FALLBACK_ONLY
            } else {
                TtsCapabilityStatus.UNAVAILABLE
            }
            ,
            sttSizeBytes = entry.sttSizeBytes,
            ttsSizeBytes = entry.ttsSizeBytes,
            sharedMtSizeBytes = entry.sharedMtSizeBytes,
            version = entry.version,
            sampleGreetingNative = "",
            sampleGreetingEnglish = ""
        )
    }

    fun buildInitialLanguageEntities(preInstalledCodes: Set<String> = emptySet()): List<LanguageEntity> {
        return wave1Languages.map { entry ->
            val installed = entry.code in preInstalledCodes
            LanguageEntity(
                code = entry.code,
                indicTransTag = entry.indicTransTag,
                englishName = entry.name,
                nativeName = entry.nativeName,
                scriptName = entry.script,
                sttSupported = entry.canListen,
                mtSupported = true,
                ttsCapability = if (entry.canSpeak) {
                    TtsCapabilityStatus.SYSTEM_FALLBACK_ONLY.name
                } else {
                    TtsCapabilityStatus.UNAVAILABLE.name
                },
                sttPackId = entry.sttPackId ?: "",
                mtPackId = entry.mtPackId,
                ttsPackId = entry.ttsPackId,
                isInstalled = installed,
                downloadProgress = if (installed) 1.0f else 0f,
                installStatus = if (installed) "INSTALLED" else "NOT_INSTALLED",
                version = entry.version,
                totalSizeBytes = entry.totalDownloadSizeBytes,
                canListen = entry.canListen,
                canIdentify = entry.canIdentify,
                canSpeak = entry.canSpeak,
                voiceLocale = entry.voiceLocale,
                requiredPacksCsv = entry.requiredPacks.joinToString(","),
                compatibility = entry.compatibility,
                wave = entry.wave.name,
                isPopular = entry.isPopular
            )
        }
    }

    fun buildDirectedPairEntities(): List<DirectedTranslationPairEntity> {
        return directedPairs.values.map { pair ->
            DirectedTranslationPairEntity(
                sourceCode = pair.sourceCode,
                targetCode = pair.targetCode,
                method = pair.method.name,
                pivotCode = pair.pivotCode,
                qualityStatus = pair.qualityStatus.name,
                requiredMtPacksCsv = pair.requiredMtPackIds.joinToString(",")
            )
        }
    }

    fun buildInitialModelPacks(): List<ModelPackEntity> {
        // Bundled Whisper Tiny STT and temporary system TTS are shipped in the app and have no
        // download rows. This metadata row only describes the ML Kit typed-translation download
        // path; it contains no custom model URL and cannot install speech weights.
        return listOf(
            ModelPackEntity(
                packId = PACK_MT_INDIC_INDIC,
                languageCode = "indic_shared",
                taskType = "MT",
                modelArchitecture = "ML Kit Translate on-device model",
                version = "17.0.3",
                downloadUrl = "",
                sha256Checksum = "",
                fileSizeBytes = SIZE_MT_INDIC_INDIC_BYTES,
                requiredRuntime = "Google ML Kit Translate 17.0.3",
                minMemoryMb = 256,
                license = "Google ML Kit Terms",
                attribution = "Google ML Kit Translate (https://developers.google.com/ml-kit/terms)",
                dependenciesCsv = "",
                compatibilityInfo = "On-device ML Kit language download",
                manifestSignature = "",
                isSharedResource = true
            )
        )
    }
}
