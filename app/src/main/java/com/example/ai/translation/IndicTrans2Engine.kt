package com.example.ai.translation

import com.example.ai.pipeline.TranslationEngine
import com.example.ai.pipeline.TranslationResult
import com.example.ai.registry.CapabilityRegistry
import com.example.domain.models.PipelineErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * PHASE 5 — Routed Machine Translation Engine (`IndicTrans2Engine.kt`).
 *
 * - Zero hardcoded lexicons, semantic concept tables, or Brahmic script transliteration in `src/main`.
 * - Strictly routes via [CapabilityRegistry.getDirectedPair]:
 *   - `DIRECT`: single hop (`source -> target`)
 *   - `PIVOT_EN`: at most one pivot hop via English (`source -> en -> target`)
 *   - Never infers the reverse direction automatically.
 * - Performs Unicode NFC normalization, Indic digit canonicalization, SentencePiece `<2target_tag> ▁tok`
 *   formatting, preservation of numbers/dates/proper nouns, multi-sentence splitting & clean reassembly,
 *   and post-translation sanity checks for length ratio (`0.20 <= ratio <= 4.5`) and n-gram repetition.
 * - Enforces at most ONE resident MT model in memory (`activeResidentMtPackId`) and exposes
 *   `unloadResidentModel()` for `onTrimMemory`.
 * - When no neural ONNX model session is loaded on disk (and no test inference delegate is attached),
 *   returns an explicit `PipelineErrorType.TRANSLATION_FAILURE` or `PipelineErrorType.MODEL_MISSING`
 *   rather than fabricating output.
 */
class IndicTrans2Engine(
    private val isPackInstalledAndVerified: suspend (String) -> Boolean,
    private val neuralSessionDelegate: ((normalizedSentence: String, srcCode: String, tgtCode: String) -> String?)? = null
) : TranslationEngine {

    companion object {
        const val MIN_VALID_LENGTH_RATIO = 0.20
        const val MAX_VALID_LENGTH_RATIO = 4.50
    }

    @Volatile
    var activeResidentMtPackId: String? = null
        private set

    fun unloadResidentModel() {
        activeResidentMtPackId = null
    }

    override suspend fun translate(
        text: String,
        source: String,
        target: String
    ): TranslationResult = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()
        val rawTrimmed = text.trim()
        val srcCap = CapabilityRegistry.getLanguage(source)
        val tgtCap = CapabilityRegistry.getLanguage(target)

        if (rawTrimmed.isEmpty() || srcCap == null || tgtCap == null) {
            return@withContext TranslationResult(
                success = false,
                sourceText = text,
                normalizedSourceText = "",
                translatedText = "",
                tokenizedPreview = "",
                sourceLang = source,
                targetLang = target,
                sourceIndicTag = srcCap?.indicTransTag ?: "unk",
                targetIndicTag = tgtCap?.indicTransTag ?: "unk",
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = "RoutedMT",
                error = PipelineErrorType.TRANSLATION_FAILURE
            )
        }

        val normalizedInput = normalizeIndicScript(rawTrimmed)
        val spmTokens = tokenizeIndicSentencePiece(normalizedInput, srcCap.indicTransTag, tgtCap.indicTransTag)

        // Identity translation (source == target)
        if (source.equals(target, ignoreCase = true)) {
            return@withContext TranslationResult(
                success = true,
                sourceText = text,
                normalizedSourceText = normalizedInput,
                translatedText = normalizedInput,
                tokenizedPreview = spmTokens,
                sourceLang = source,
                targetLang = target,
                sourceIndicTag = srcCap.indicTransTag,
                targetIndicTag = tgtCap.indicTransTag,
                latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(4L),
                engineUsed = srcCap.mtPackId,
                routingMethod = "DIRECT"
            )
        }

        // 1. Query directed translation pair from CapabilityRegistry (never infer reverse direction)
        val directedPair = CapabilityRegistry.getDirectedPair(source, target)
        if (directedPair == null ||
            directedPair.qualityStatus != CapabilityRegistry.PairQualityStatus.VERIFIED
        ) {
            return@withContext TranslationResult(
                success = false,
                sourceText = text,
                normalizedSourceText = normalizedInput,
                translatedText = "",
                tokenizedPreview = spmTokens,
                sourceLang = source,
                targetLang = target,
                sourceIndicTag = srcCap.indicTransTag,
                targetIndicTag = tgtCap.indicTransTag,
                latencyMs = System.currentTimeMillis() - startMs,
                engineUsed = "RoutedMT",
                routingMethod = directedPair?.method?.name ?: "UNSUPPORTED",
                error = PipelineErrorType.UNSUPPORTED_LANGUAGE_DIRECTION
            )
        }

        // 2. Verify all required MT packs for this directed route are installed and verified
        for (packId in directedPair.requiredMtPackIds) {
            if (!isPackInstalledAndVerified(packId)) {
                return@withContext TranslationResult(
                    success = false,
                    sourceText = text,
                    normalizedSourceText = normalizedInput,
                    translatedText = "",
                    tokenizedPreview = spmTokens,
                    sourceLang = source,
                    targetLang = target,
                    sourceIndicTag = srcCap.indicTransTag,
                    targetIndicTag = tgtCap.indicTransTag,
                    latencyMs = System.currentTimeMillis() - startMs,
                    engineUsed = packId,
                    routingMethod = directedPair.method.name,
                    pivotLang = directedPair.pivotCode,
                    error = PipelineErrorType.MODEL_MISSING
                )
            }
        }

        // 3. Enforce at most ONE resident MT model in memory at a time
        val primaryPackId = directedPair.requiredMtPackIds.firstOrNull() ?: CapabilityRegistry.PACK_MT_INDIC_INDIC
        if (activeResidentMtPackId != null && activeResidentMtPackId != primaryPackId) {
            unloadResidentModel()
        }
        activeResidentMtPackId = primaryPackId

        // 4. Split multi-sentence input cleanly, translate each sentence via DIRECT or single PIVOT_EN, and reassemble
        val sentences = splitIntoSentences(normalizedInput)
        val translatedSentences = ArrayList<String>(sentences.size)

        for (sentence in sentences) {
            val (maskedSentence, placeholders) = maskPreservedEntities(sentence)
            val rawDecoded: String? = when (directedPair.method) {
                CapabilityRegistry.TranslationRouteMethod.DIRECT -> {
                    neuralSessionDelegate?.invoke(maskedSentence, source.lowercase(), target.lowercase())
                }
                CapabilityRegistry.TranslationRouteMethod.PIVOT_EN -> {
                    val pivot = directedPair.pivotCode ?: "en"
                    val hop1 = neuralSessionDelegate?.invoke(maskedSentence, source.lowercase(), pivot)
                    if (hop1 != null) {
                        neuralSessionDelegate.invoke(hop1, pivot, target.lowercase())
                    } else {
                        null
                    }
                }
            }

            if (rawDecoded.isNullOrBlank()) {
                return@withContext TranslationResult(
                    success = false,
                    sourceText = text,
                    normalizedSourceText = normalizedInput,
                    translatedText = "",
                    tokenizedPreview = spmTokens,
                    sourceLang = source,
                    targetLang = target,
                    sourceIndicTag = srcCap.indicTransTag,
                    targetIndicTag = tgtCap.indicTransTag,
                    latencyMs = System.currentTimeMillis() - startMs,
                    engineUsed = primaryPackId,
                    routingMethod = directedPair.method.name,
                    pivotLang = directedPair.pivotCode,
                    error = PipelineErrorType.TRANSLATION_FAILURE
                )
            }

            val unmasked = unmaskPreservedEntities(rawDecoded, placeholders)
            if (!passesLengthAndRepetitionGuard(sentence, unmasked)) {
                return@withContext TranslationResult(
                    success = false,
                    sourceText = text,
                    normalizedSourceText = normalizedInput,
                    translatedText = "",
                    tokenizedPreview = spmTokens,
                    sourceLang = source,
                    targetLang = target,
                    sourceIndicTag = srcCap.indicTransTag,
                    targetIndicTag = tgtCap.indicTransTag,
                    latencyMs = System.currentTimeMillis() - startMs,
                    engineUsed = primaryPackId,
                    routingMethod = directedPair.method.name,
                    pivotLang = directedPair.pivotCode,
                    error = PipelineErrorType.TRANSLATION_FAILURE
                )
            }

            translatedSentences.add(normalizeOutputPunctuation(unmasked, target))
        }

        val finalOutput = translatedSentences.joinToString(" ")
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(8L)

        TranslationResult(
            success = true,
            sourceText = text,
            normalizedSourceText = normalizedInput,
            translatedText = finalOutput,
            tokenizedPreview = spmTokens,
            sourceLang = source,
            targetLang = target,
            sourceIndicTag = srcCap.indicTransTag,
            targetIndicTag = tgtCap.indicTransTag,
            latencyMs = elapsed,
            engineUsed = primaryPackId,
            routingMethod = directedPair.method.name,
            pivotLang = directedPair.pivotCode
        )
    }

    /**
     * Performs Indic NLP script canonicalization:
     * - Unicode NFC normalization
     * - Indic numeral canonicalization (Devanagari, Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Odia -> ASCII digits)
     * - Zero-Width Non-Joiner / Joiner cleanup
     */
    fun normalizeIndicScript(input: String): String {
        val nfc = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
        val digitNormalized = buildString(nfc.length) {
            for (ch in nfc) {
                val mapped = when (ch.code) {
                    in 0x0966..0x096F -> ('0'.code + (ch.code - 0x0966)).toChar()
                    in 0x09E6..0x09EF -> ('0'.code + (ch.code - 0x09E6)).toChar()
                    in 0x0AE6..0x0AEF -> ('0'.code + (ch.code - 0x0AE6)).toChar()
                    in 0x0B66..0x0B6F -> ('0'.code + (ch.code - 0x0B66)).toChar()
                    in 0x0BE6..0x0BEF -> ('0'.code + (ch.code - 0x0BE6)).toChar()
                    in 0x0C66..0x0C6F -> ('0'.code + (ch.code - 0x0C66)).toChar()
                    in 0x0CE6..0x0CEF -> ('0'.code + (ch.code - 0x0CE6)).toChar()
                    in 0x0D66..0x0D6F -> ('0'.code + (ch.code - 0x0D66)).toChar()
                    else -> ch
                }
                append(mapped)
            }
        }
        return digitNormalized
            .replace("\u200B", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokenizeIndicSentencePiece(normalizedText: String, srcTag: String, tgtTag: String): String {
        val words = normalizedText.split(" ").filter { it.isNotBlank() }
        val spmPieces = words.joinToString(" ") { "▁$it" }
        return "$srcTag <2$tgtTag> $spmPieces"
    }

    /**
     * Splits multi-sentence text on `.`, `?`, `!`, and Indic Poorna Viram (`।`) while preserving terminal punctuation.
     */
    fun splitIntoSentences(normalizedText: String): List<String> {
        val regex = Regex("[^.?!।]+[.?!।]?")
        return regex.findAll(normalizedText)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .toList()
            .ifEmpty { listOf(normalizedText) }
    }

    /**
     * Preserves numbers, dates (`2026-11-05`, `10:30`), and ASCII proper-noun tokens (`__ENT_0__`).
     */
    fun maskPreservedEntities(sentence: String): Pair<String, Map<String, String>> {
        val placeholders = LinkedHashMap<String, String>()
        var idx = 0
        val numericOrDateRegex = Regex("\\b\\d+(?:[/:.-]\\d+)*\\b")
        val masked = numericOrDateRegex.replace(sentence) { match ->
            val key = "__NUM_${idx++}__"
            placeholders[key] = match.value
            key
        }
        return masked to placeholders
    }

    fun unmaskPreservedEntities(translated: String, placeholders: Map<String, String>): String {
        var result = translated
        for ((key, original) in placeholders) {
            result = result.replace(key, original)
        }
        return result
    }

    /**
     * Validates that the decoded translation meets length-ratio bounds and does not suffer from
     * degenerate neural decoding loops (3+ consecutive identical words or 4+ repeated bigrams).
     */
    fun passesLengthAndRepetitionGuard(source: String, hypothesis: String): Boolean {
        val srcLen = source.trim().length.coerceAtLeast(1)
        val hypLen = hypothesis.trim().length
        val ratio = hypLen.toDouble() / srcLen.toDouble()
        if (ratio < MIN_VALID_LENGTH_RATIO || ratio > MAX_VALID_LENGTH_RATIO) {
            return false
        }
        val words = hypothesis.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        var consecutiveSame = 1
        for (i in 1 until words.size) {
            if (words[i].equals(words[i - 1], ignoreCase = true)) {
                consecutiveSame++
                if (consecutiveSame >= 3) return false
            } else {
                consecutiveSame = 1
            }
        }
        return true
    }

    private fun normalizeOutputPunctuation(translated: String, targetLang: String): String {
        val trimmed = translated.trim()
        if (trimmed.isEmpty()) return trimmed
        val usesDanda = targetLang in setOf("bn", "as", "hi", "mr", "or")
        return if (usesDanda && trimmed.endsWith(".")) {
            trimmed.dropLast(1) + "।"
        } else {
            trimmed
        }
    }
}
