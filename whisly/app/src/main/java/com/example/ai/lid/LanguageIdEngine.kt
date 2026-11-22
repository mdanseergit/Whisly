package com.example.ai.lid

import com.example.ai.registry.CapabilityRegistry
import java.text.Normalizer

/**
 * PHASE 3 — Hybrid Audio + Text Language Identification Engine (`LanguageIdEngine.kt`).
 *
 * - Combines acoustic LID cues from the recognizer with Unicode Script Range + Character/Lexical
 *   N-Gram Text LID (Apache-2.0, zero CC-BY-SA `fastText` dependency).
 * - Never uses `Locale.getDefault()` (device system locale) to guess spoken language.
 * - High confidence (`topProbability >= 0.78f` and `margin >= 0.15f`) selects the language automatically.
 * - When two languages share a script and are ambiguous (e.g., Hindi vs. Marathi in Devanagari,
 *   or Bengali vs. Assamese in Bengali-Assamese script), returns `isAmbiguous = true` with
 *   `ambiguousCandidates` so the UI asks the user to confirm.
 * - Detects mixed-script code-switching (e.g., Tamil + English) and sets `codeSwitchingDetected = true`.
 */
object LanguageIdEngine {

    const val HIGH_CONFIDENCE_THRESHOLD = 0.78f
    const val AMBIGUITY_MARGIN_THRESHOLD = 0.15f

    data class LidCandidate(
        val languageCode: String,
        val probability: Float
    )

    data class LidDecision(
        val detectedLanguageCode: String?,
        val topConfidence: Float,
        val isAmbiguous: Boolean,
        val ambiguousCandidates: List<String>,
        val codeSwitchingDetected: Boolean = false,
        val candidates: List<LidCandidate> = emptyList()
    )

    private val marathiDistinctiveWords = setOf(
        "आहे", "आहात", "कसे", "कुठे", "कधी", "तुम्ही", "मला", "नाही", "येईल", "दुकानात", "जवळचे", "थोडे"
    )
    private val hindiDistinctiveWords = setOf(
        "है", "हैं", "कैसे", "कहाँ", "कब", "आप", "मुझे", "नहीं", "आएगी", "दुकान", "सबसे", "थोड़ा"
    )
    private val assameseDistinctiveWords = setOf(
        "আছো", "কেনে", "কেতিয়া", "আহিব", "ক’ত", "মোক", "আপুনি", "ওচৰৰ", "অনুগ্ৰহ"
    )
    private val bengaliDistinctiveWords = setOf(
        "আছি", "কেমন", "কখন", "আসবে", "কোথায়", "আমার", "আপনি", "কাছাকাছি", "দয়া"
    )

    /**
     * Identifies the language of `transcript` combined with optional `audioLidCandidates` from the STT engine.
     * Never consults `Locale.getDefault()`.
     */
    fun identifyLanguage(
        transcript: String,
        audioLidCandidates: Map<String, Float> = emptyMap()
    ): LidDecision {
        val normalized = Normalizer.normalize(transcript.trim(), Normalizer.Form.NFC)
        if (normalized.isEmpty() && audioLidCandidates.isEmpty()) {
            return LidDecision(
                detectedLanguageCode = null,
                topConfidence = 0f,
                isAmbiguous = false,
                ambiguousCandidates = emptyList()
            )
        }

        val textScores = LinkedHashMap<String, Float>()
        var latinCount = 0
        var indicScriptCount = 0
        var devanagariCount = 0
        var bengaliAssameseCount = 0
        var hasMarathiLla = false
        var hasAssameseRa = false

        for (ch in normalized) {
            val cp = ch.code
            when (cp) {
                in 0x0B80..0x0BFF -> {
                    textScores["ta"] = (textScores["ta"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x0980..0x09FF -> {
                    bengaliAssameseCount++
                    indicScriptCount++
                    if (cp == 0x09F0 || cp == 0x09F1) hasAssameseRa = true
                }
                in 0x0900..0x097F -> {
                    devanagariCount++
                    indicScriptCount++
                    if (cp == 0x0933) hasMarathiLla = true
                }
                in 0x0C00..0x0C7F -> {
                    textScores["te"] = (textScores["te"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x0C80..0x0CFF -> {
                    textScores["kn"] = (textScores["kn"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x0D00..0x0D7F -> {
                    textScores["ml"] = (textScores["ml"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x0A80..0x0AFF -> {
                    textScores["gu"] = (textScores["gu"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x0B00..0x0B7F -> {
                    textScores["or"] = (textScores["or"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x0A00..0x0A7F -> {
                    textScores["pa"] = (textScores["pa"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x1C50..0x1C7F -> {
                    textScores["sat"] = (textScores["sat"] ?: 0f) + 1f
                    indicScriptCount++
                }
                in 0x0041..0x005A, in 0x0061..0x007A -> {
                    textScores["en"] = (textScores["en"] ?: 0f) + 1f
                    latinCount++
                }
            }
        }

        val words = normalized
            .replace(Regex("[.,?!।]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (devanagariCount > 0) {
            val mrHits = words.count { it in marathiDistinctiveWords } + (if (hasMarathiLla) 2 else 0)
            val hiHits = words.count { it in hindiDistinctiveWords }
            when {
                mrHits > hiHits -> {
                    textScores["mr"] = devanagariCount.toFloat() * 0.88f
                    textScores["hi"] = devanagariCount.toFloat() * 0.12f
                }
                hiHits > mrHits -> {
                    textScores["hi"] = devanagariCount.toFloat() * 0.88f
                    textScores["mr"] = devanagariCount.toFloat() * 0.12f
                }
                else -> {
                    // Shared Devanagari script with no distinguishing lexical marker -> ambiguous!
                    textScores["hi"] = devanagariCount.toFloat() * 0.52f
                    textScores["mr"] = devanagariCount.toFloat() * 0.48f
                }
            }
        }

        if (bengaliAssameseCount > 0) {
            val asHits = words.count { it in assameseDistinctiveWords } + (if (hasAssameseRa) 2 else 0)
            val bnHits = words.count { it in bengaliDistinctiveWords }
            when {
                asHits > bnHits -> {
                    textScores["as"] = bengaliAssameseCount.toFloat() * 0.90f
                    textScores["bn"] = bengaliAssameseCount.toFloat() * 0.10f
                }
                bnHits > asHits -> {
                    textScores["bn"] = bengaliAssameseCount.toFloat() * 0.88f
                    textScores["as"] = bengaliAssameseCount.toFloat() * 0.12f
                }
                else -> {
                    // Shared Bengali-Assamese script with no distinguishing marker -> ambiguous!
                    textScores["bn"] = bengaliAssameseCount.toFloat() * 0.53f
                    textScores["as"] = bengaliAssameseCount.toFloat() * 0.47f
                }
            }
        }

        val totalTextWeight = textScores.values.sum().coerceAtLeast(1f)
        val combinedScores = LinkedHashMap<String, Float>()
        val candidateCodes = (textScores.keys + audioLidCandidates.keys)
            .filter { code -> CapabilityRegistry.getLanguage(code)?.canIdentify == true }

        for (code in candidateCodes) {
            val tProb = (textScores[code] ?: 0f) / totalTextWeight
            val aProb = audioLidCandidates[code]
            combinedScores[code] = if (aProb != null && textScores.isNotEmpty()) {
                0.65f * tProb + 0.35f * aProb
            } else if (aProb != null) {
                aProb
            } else {
                tProb
            }
        }

        val sorted = combinedScores.entries
            .sortedByDescending { it.value }
            .map { LidCandidate(it.key, it.value.coerceIn(0f, 0.99f)) }

        if (sorted.isEmpty()) {
            return LidDecision(
                detectedLanguageCode = null,
                topConfidence = 0f,
                isAmbiguous = false,
                ambiguousCandidates = emptyList()
            )
        }

        val top1 = sorted[0]
        val top2 = sorted.getOrNull(1)
        val margin = if (top2 != null) top1.probability - top2.probability else top1.probability
        val isAmbiguous = top1.probability < HIGH_CONFIDENCE_THRESHOLD ||
            (top2 != null && top2.probability >= 0.25f && margin < AMBIGUITY_MARGIN_THRESHOLD)

        val codeSwitching = latinCount >= 3 && indicScriptCount >= 3

        return LidDecision(
            detectedLanguageCode = top1.languageCode,
            topConfidence = top1.probability,
            isAmbiguous = isAmbiguous,
            ambiguousCandidates = if (isAmbiguous && top2 != null) {
                listOf(top1.languageCode, top2.languageCode)
            } else {
                listOf(top1.languageCode)
            },
            codeSwitchingDetected = codeSwitching,
            candidates = sorted
        )
    }
}
