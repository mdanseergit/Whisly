package com.example.ai.evaluation

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * PHASE 2 — Pure Evaluation Metric Calculators (`AiEvaluationSuite.kt`).
 *
 * All benchmark datasets and held-out fixtures have been moved out of `src/main`
 * into `src/test` and `/scripts/run_eval_harness.py` so zero benchmark sentences
 * or test fixtures ship inside the production APK.
 */
object AiEvaluationSuite {

    // Proposed per-language minimum quality thresholds (documented in MODELS.md)
    const val PROPOSED_MAX_STT_WER_HIGH_RESOURCE = 25.0
    const val PROPOSED_MAX_STT_WER_LOW_RESOURCE = 30.0
    const val PROPOSED_MIN_MT_CHRF_PLUS_PLUS = 45.0
    const val PROPOSED_MAX_TTS_ROUNDTRIP_WER = 15.0

    data class SttEvalMetrics(
        val languageCode: String,
        val reference: String,
        val hypothesis: String,
        val werPercent: Double,
        val cerPercent: Double,
        val latencyMs: Long = 0L
    )

    data class MtEvalMetrics(
        val languagePair: String,
        val routingMethod: String = "DIRECT",
        val source: String,
        val reference: String,
        val hypothesis: String,
        val chrFPlusPlusScore: Double,
        val bleuScore: Double = 0.0,
        val latencyMs: Long
    )

    data class HeldOutBenchmarkReport(
        val totalPairsEvaluated: Int,
        val averageWerPercent: Double,
        val averageCerPercent: Double,
        val averageChrFPlusPlus: Double,
        val averageSttLatencyMs: Long,
        val averageMtLatencyMs: Long,
        val qualityGatePassed: Boolean,
        val perPairResults: List<MtEvalMetrics>
    )

    data class RuntimeTelemetrySnapshot(
        val usedRamMb: Long,
        val maxHeapMb: Long,
        val lastSttLatencyMs: Long,
        val lastMtLatencyMs: Long,
        val lastTtsLatencyMs: Long,
        val lastEndToEndLatencyMs: Long
    )

    /**
     * Computes Word Error Rate (`WER = (S + D + I) / N * 100`) using Wagner-Fischer Levenshtein distance.
     */
    fun computeWer(reference: String, hypothesis: String): Double {
        val refWords = reference.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val hypWords = hypothesis.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0.0 else 100.0
        val dist = editDistance(refWords, hypWords)
        return (dist.toDouble() / refWords.size.toDouble()) * 100.0
    }

    /**
     * Computes Character Error Rate (`CER`) across Unicode codepoints.
     */
    fun computeCer(reference: String, hypothesis: String): Double {
        val refChars = reference.trim().map { it.toString() }
        val hypChars = hypothesis.trim().map { it.toString() }
        if (refChars.isEmpty()) return if (hypChars.isEmpty()) 0.0 else 100.0
        val dist = editDistance(refChars, hypChars)
        return (dist.toDouble() / refChars.size.toDouble()) * 100.0
    }

    /**
     * Computes Language Identification (LID) top-1 accuracy percentage across `(expectedLang, predictedLang)` pairs.
     */
    fun computeLidAccuracy(predictions: List<Pair<String, String>>): Double {
        if (predictions.isEmpty()) return 0.0
        val correct = predictions.count { (expected, actual) ->
            expected.equals(actual, ignoreCase = true)
        }
        return (correct.toDouble() / predictions.size.toDouble()) * 100.0
    }

    /**
     * Computes chrF++ (character n-gram F-score for n=1..6 + word n-gram overlap for w=1..2, beta=2.0)
     * on a `[0, 100]` scale for Machine Translation evaluation.
     */
    fun computeChrFPlusPlus(reference: String, hypothesis: String, maxCharN: Int = 6, beta: Double = 2.0): Double {
        val refClean = reference.trim()
        val hypClean = hypothesis.trim()
        if (refClean.isEmpty() && hypClean.isEmpty()) return 100.0
        if (refClean.isEmpty() || hypClean.isEmpty()) return 0.0

        var totalPrecision = 0.0
        var totalRecall = 0.0
        var ordersCount = 0

        for (n in 1..maxCharN) {
            val refNgrams = extractCharNgrams(refClean, n)
            val hypNgrams = extractCharNgrams(hypClean, n)
            if (refNgrams.isNotEmpty() && hypNgrams.isNotEmpty()) {
                val overlap = countMultisetOverlap(refNgrams, hypNgrams)
                totalPrecision += overlap.toDouble() / hypNgrams.size
                totalRecall += overlap.toDouble() / refNgrams.size
                ordersCount++
            }
        }

        val refWords = refClean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val hypWords = hypClean.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (w in 1..2) {
            val refWordNgrams = extractWordNgrams(refWords, w)
            val hypWordNgrams = extractWordNgrams(hypWords, w)
            if (refWordNgrams.isNotEmpty() && hypWordNgrams.isNotEmpty()) {
                val overlap = countMultisetOverlap(refWordNgrams, hypWordNgrams)
                totalPrecision += overlap.toDouble() / hypWordNgrams.size
                totalRecall += overlap.toDouble() / refWordNgrams.size
                ordersCount++
            }
        }

        if (ordersCount == 0) return 0.0
        val avgP = totalPrecision / ordersCount
        val avgR = totalRecall / ordersCount
        if (avgP + avgR == 0.0) return 0.0
        val betaSq = beta * beta
        val fScore = ((1.0 + betaSq) * avgP * avgR) / ((betaSq * avgP) + avgR)
        return (fScore * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Secondary MT metric: smoothed sentence-level BLEU-4 on a `[0, 100]` scale.
     */
    fun computeBleu(reference: String, hypothesis: String): Double {
        val refWords = reference.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val hypWords = hypothesis.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (refWords.isEmpty() || hypWords.isEmpty()) return 0.0

        val maxOrder = min(4, min(refWords.size, hypWords.size))
        var logSum = 0.0
        for (n in 1..maxOrder) {
            val refNg = extractWordNgrams(refWords, n)
            val hypNg = extractWordNgrams(hypWords, n)
            val overlap = countMultisetOverlap(refNg, hypNg)
            val smoothedPrecision = (overlap + 1.0) / (hypNg.size + 1.0)
            logSum += (1.0 / maxOrder) * ln(smoothedPrecision)
        }
        val brevityPenalty = if (hypWords.size >= refWords.size) {
            1.0
        } else {
            exp(1.0 - (refWords.size.toDouble() / hypWords.size.toDouble()))
        }
        return (brevityPenalty * exp(logSum) * 100.0).coerceIn(0.0, 100.0)
    }

    fun captureRealRuntimeMemory(
        lastSttMs: Long,
        lastMtMs: Long,
        lastTtsMs: Long
    ): RuntimeTelemetrySnapshot {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        val maxBytes = runtime.maxMemory()
        return RuntimeTelemetrySnapshot(
            usedRamMb = usedBytes / (1024 * 1024),
            maxHeapMb = maxBytes / (1024 * 1024),
            lastSttLatencyMs = lastSttMs,
            lastMtLatencyMs = lastMtMs,
            lastTtsLatencyMs = lastTtsMs,
            lastEndToEndLatencyMs = lastSttMs + lastMtMs + lastTtsMs
        )
    }

    private fun extractCharNgrams(text: String, n: Int): List<String> {
        val stripped = text.replace(Regex("\\s+"), "")
        if (stripped.length < n) return listOf(stripped)
        val list = ArrayList<String>(max(1, stripped.length - n + 1))
        for (i in 0..(stripped.length - n)) {
            list.add(stripped.substring(i, i + n))
        }
        return list
    }

    private fun extractWordNgrams(words: List<String>, n: Int): List<String> {
        if (words.size < n) return emptyList()
        val list = ArrayList<String>(words.size - n + 1)
        for (i in 0..(words.size - n)) {
            list.add(words.subList(i, i + n).joinToString(" "))
        }
        return list
    }

    private fun countMultisetOverlap(a: List<String>, b: List<String>): Int {
        val countsA = a.groupingBy { it }.eachCount()
        val countsB = b.groupingBy { it }.eachCount()
        var overlap = 0
        for ((token, countA) in countsA) {
            val countB = countsB[token] ?: 0
            overlap += min(countA, countB)
        }
        return overlap
    }

    private fun <T> editDistance(a: List<T>, b: List<T>): Int {
        val dp = IntArray(b.size + 1) { it }
        for (i in 1..a.size) {
            var prevDiag = dp[0]
            dp[0] = i
            for (j in 1..b.size) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prevDiag + cost)
                prevDiag = temp
            }
        }
        return dp[b.size]
    }
}
