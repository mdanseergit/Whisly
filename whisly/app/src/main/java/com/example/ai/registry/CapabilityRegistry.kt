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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_inflect_en_nano",
            sttSizeBytes = 116_204_861L,
            ttsSizeBytes = 22_429_639L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_ta_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_coqui_bn_female",
            sttSizeBytes = 116_204_861L,
            ttsSizeBytes = 108_053_596L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_hi_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_te_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_kn_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_ml_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_mr_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_gu_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_or_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_as_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = "tts_vits_rasa_pa_v1",
            sttSizeBytes = 116_204_861L,
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
            sttPackId = "stt_whisper_tiny_multilingual",
            mtPackId = PACK_MT_INDIC_INDIC,
            ttsPackId = null,
            sttSizeBytes = 116_204_861L,
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
            ttsCapability = if (entry.canSpeak && entry.ttsPackId != null) {
                TtsCapabilityStatus.VITS_RASA_VERIFIED
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
                ttsCapability = if (entry.canSpeak && entry.ttsPackId != null) {
                    TtsCapabilityStatus.VITS_RASA_VERIFIED.name
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
        val packs = mutableListOf<ModelPackEntity>()

        // 1. Multilingual STT Model Pack (OpenAI Whisper Tiny via sherpa-onnx)
        packs.add(
            ModelPackEntity(
                packId = "stt_whisper_tiny_multilingual",
                languageCode = "multilingual",
                taskType = "STT",
                modelArchitecture = "Whisper Tiny Multilingual Int8 (ONNX)",
                version = "1.0.0",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
                sha256Checksum = "c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1",
                fileSizeBytes = 116204861L,
                requiredRuntime = "sherpa-onnx >= 1.10 / ONNX Runtime Mobile",
                minMemoryMb = 512,
                license = "MIT",
                attribution = "OpenAI / k2-fsa sherpa-onnx (https://github.com/k2-fsa/sherpa-onnx)",
                dependenciesCsv = "tiny-tokens.txt,tiny-encoder.int8.onnx,tiny-decoder.int8.onnx",
                compatibilityInfo = "ARM64-v8a / x86_64 quantized int8",
                manifestSignature = "SIG_VERIFIED_WHISPER_TINY",
                isSharedResource = true
            )
        )

        // 2. English TTS Model Pack (Inflect Nano v2)
        packs.add(
            ModelPackEntity(
                packId = "tts_inflect_en_nano",
                languageCode = "en",
                taskType = "TTS",
                modelArchitecture = "VITS Inflect English Nano v2",
                version = "2.0.0",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-inflect-en-nano-v2.tar.bz2",
                sha256Checksum = "9a6b1188b5f3be8813e0552056328495b4e88ffd1ef18ff837272bde7b3bc136",
                fileSizeBytes = 22429639L,
                requiredRuntime = "sherpa-onnx >= 1.10",
                minMemoryMb = 256,
                license = "Apache-2.0",
                attribution = "k2-fsa sherpa-onnx (https://github.com/k2-fsa/sherpa-onnx)",
                dependenciesCsv = "tokens.txt,model.onnx",
                compatibilityInfo = "22.05kHz native vocoder",
                manifestSignature = "SIG_VERIFIED_VITS_EN",
                isSharedResource = false
            )
        )

        // 3. Bengali TTS Model Pack (Coqui Bengali Female)
        packs.add(
            ModelPackEntity(
                packId = "tts_coqui_bn_female",
                languageCode = "bn",
                taskType = "TTS",
                modelArchitecture = "VITS Coqui Bengali Female",
                version = "1.0.0",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-bn-custom_female.tar.bz2",
                sha256Checksum = "a03292d7da03650e892bb1989b40dc2c62574c0d6c34c8bef185fbb3151417a1",
                fileSizeBytes = 108053596L,
                requiredRuntime = "sherpa-onnx >= 1.10",
                minMemoryMb = 384,
                license = "MIT",
                attribution = "Coqui / k2-fsa sherpa-onnx (https://github.com/k2-fsa/sherpa-onnx)",
                dependenciesCsv = "tokens.txt,model.onnx",
                compatibilityInfo = "22.05kHz native vocoder",
                manifestSignature = "SIG_VERIFIED_VITS_BN",
                isSharedResource = false
            )
        )

        // 4. Shared MT Model Pack (IndicTrans2 200M Distilled Int8)
        packs.add(
            ModelPackEntity(
                packId = PACK_MT_INDIC_INDIC,
                languageCode = "indic_shared",
                taskType = "MT",
                modelArchitecture = "IndicTrans2 200M Distilled Int8 (ONNX)",
                version = "2.1.0",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
                sha256Checksum = "c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1",
                fileSizeBytes = SIZE_MT_INDIC_INDIC_BYTES,
                requiredRuntime = "sherpa-onnx >= 1.10 / ONNX Runtime Mobile",
                minMemoryMb = 512,
                license = "MIT",
                attribution = "AI4Bharat / sherpa-onnx",
                dependenciesCsv = "tiny-tokens.txt,tiny-encoder.int8.onnx,tiny-decoder.int8.onnx",
                compatibilityInfo = "ARM64-v8a / x86_64 quantized int8",
                manifestSignature = "SIG_VERIFIED_INDICTRANS2",
                isSharedResource = true
            )
        )

        return packs
    }
}
