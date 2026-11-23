package com.example.ai.translation

import com.example.ai.registry.CapabilityRegistry
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production On-Device Neural Translation Engine powered by Google ML Kit.
 *
 * - Zero-cost, runs 100% on-device with official neural models.
 * - Zero API keys, zero cloud servers required.
 * - Supports sentence-level translation across English and major Indian languages
 *   (Tamil, Hindi, Bengali, Marathi, Telugu, Kannada, Gujarati, Urdu).
 * - Enforces fail-closed behavior: unsupported languages (e.g. Santali 'sat') or missing models
 *   return null / error rather than fabricating transliterated text.
 */
object RealOnDeviceTranslationService {

    private val translators = ConcurrentHashMap<String, Translator>()
    private val modelManager by lazy { RemoteModelManager.getInstance() }

    /** Tag comes only from the single capability registry; this class owns no language list. */
    fun mapToTranslateLanguage(langCode: String): String? =
        CapabilityRegistry.mlKitTranslationTag(langCode)

    fun isLanguageSupported(langCode: String): Boolean {
        return mapToTranslateLanguage(langCode) != null
    }

    fun isPairSupported(sourceCode: String, targetCode: String): Boolean {
        if (sourceCode.equals(targetCode, ignoreCase = true)) return false
        val src = mapToTranslateLanguage(sourceCode) ?: return false
        val tgt = mapToTranslateLanguage(targetCode) ?: return false
        return src != tgt
    }

    suspend fun isModelDownloaded(langCode: String): Boolean {
        val mlKitLang = mapToTranslateLanguage(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlKitLang).build()
        return try {
            awaitTask(modelManager.isModelDownloaded(model))
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadLanguageModel(langCode: String, wifiOnly: Boolean = false): Boolean {
        val mlKitLang = mapToTranslateLanguage(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlKitLang).build()
        val conditions = DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()
        return try {
            awaitTask(modelManager.download(model, conditions))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteLanguageModel(langCode: String): Boolean {
        val mlKitLang = mapToTranslateLanguage(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlKitLang).build()
        return try {
            awaitTask(modelManager.deleteDownloadedModel(model))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun translate(
        sentence: String,
        sourceLang: String,
        targetLang: String
    ): String? = withContext(Dispatchers.IO) {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return@withContext null
        if (sourceLang.equals(targetLang, ignoreCase = true)) return@withContext null

        val src = mapToTranslateLanguage(sourceLang) ?: return@withContext null
        val tgt = mapToTranslateLanguage(targetLang) ?: return@withContext null
        val pairKey = "$src->$tgt"

        val translator = translators.getOrPut(pairKey) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
            Translation.getClient(options)
        }

        try {
            val conditions = DownloadConditions.Builder().build()
            awaitTask(translator.downloadModelIfNeeded(conditions))
            val result = awaitTask(translator.translate(trimmed))
            val cleanResult = result?.trim().orEmpty()

            // Echo guard: Never return the identical input as a translation
            if (cleanResult.isEmpty() || cleanResult.equals(trimmed, ignoreCase = true)) {
                null
            } else {
                cleanResult
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { result ->
            if (cont.isActive) {
                cont.resume(result)
            }
        }.addOnFailureListener { exception ->
            if (cont.isActive) {
                cont.resumeWithException(exception)
            }
        }.addOnCanceledListener {
            if (cont.isActive) {
                cont.cancel()
            }
        }
    }
}

