package com.example.ai.stt

import android.content.Context
import android.util.Log
import com.example.ai.audio.AudioProcessor
import com.example.domain.models.PipelineErrorType
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Bundled multilingual Whisper Tiny INT8 STT.
 *
 * The three model files are shipped inside the app under `assets/whisper/tiny/`. No download, ZIP
 * import, custom speech pack, or user-managed model path exists. A recognizer is created from the
 * Android AssetManager and rebuilt only when the requested Whisper language changes; all decode
 * work is serialized so at most one native model is resident at a time.
 */
class SherpaOnnxSttEngine(private val context: Context) : SttDecoder {

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var recognizerLanguage: String? = null

    private val recognizerMutex = Mutex()

    override suspend fun decode(
        pcm16: ShortArray,
        sampleRateHz: Int,
        languageCode: String
    ): SttDecode {
        if (pcm16.isEmpty()) {
            return SttDecode.Failure(PipelineErrorType.SPEECH_NOT_DETECTED, "empty segment")
        }
        if (sampleRateHz != AudioProcessor.SAMPLE_RATE_HZ) {
            return SttDecode.Failure(
                PipelineErrorType.STT_FAILURE,
                "unexpected sample rate $sampleRateHz"
            )
        }

        val whisperLanguage = normalizeLanguage(languageCode)
            ?: return SttDecode.Failure(
                PipelineErrorType.UNSUPPORTED_SOURCE_LANGUAGE,
                "Whisper Tiny has no language token for $languageCode"
            )

        return withContext(Dispatchers.Default) {
            recognizerMutex.withLock {
                val current = ensureRecognizerLocked(whisperLanguage)
                    ?: return@withLock SttDecode.Failure(
                        PipelineErrorType.STT_MODEL_MISSING,
                        "bundled Whisper Tiny assets are unavailable"
                    )

                val samples = FloatArray(pcm16.size) { index -> pcm16[index] / 32768.0f }
                val result = try {
                    val stream = current.createStream()
                    try {
                        stream.acceptWaveform(samples, AudioProcessor.SAMPLE_RATE_HZ)
                        current.decode(stream)
                        current.getResult(stream)
                    } finally {
                        runCatching { stream.release() }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Whisper Tiny decode failed", error)
                    return@withLock SttDecode.Failure(
                        PipelineErrorType.STT_FAILURE,
                        error.message ?: "native decode failed"
                    )
                }

                val text = result.text.trim()
                if (text.isEmpty()) {
                    SttDecode.Failure(
                        PipelineErrorType.STT_LOW_CONFIDENCE,
                        "Whisper Tiny returned empty text"
                    )
                } else {
                    Log.d(TAG, "decoded ${pcm16.size} samples, detected language=${result.lang}")
                    // Whisper's Kotlin result exposes no probability; confidence stays null rather
                    // than inventing a number.
                    SttDecode.Text(text, null, ENGINE_NAME)
                }
            }
        }
    }

    /** Public for tests and capability checks; no native model is loaded by this call. */
    fun supportsLanguage(languageCode: String): Boolean = isLanguageSupported(languageCode)

    @Synchronized
    fun unload() {
        recognizer?.let { runCatching { it.release() } }
        recognizer = null
        recognizerLanguage = null
    }

    private fun ensureRecognizerLocked(whisperLanguage: String): OfflineRecognizer? {
        val existing = recognizer
        if (existing != null && recognizerLanguage == whisperLanguage) return existing
        if (existing != null) {
            runCatching { existing.release() }
            recognizer = null
            recognizerLanguage = null
        }
        if (!bundledAssetsPresent()) return null

        val created = try {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = AudioProcessor.SAMPLE_RATE_HZ,
                    featureDim = WHISPER_FEATURE_DIM
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$ASSET_DIR/tiny-encoder.int8.onnx",
                        decoder = "$ASSET_DIR/tiny-decoder.int8.onnx",
                        language = whisperLanguage,
                        task = "transcribe",
                        tailPaddings = WHISPER_TAIL_PADDINGS
                    ),
                    tokens = "$ASSET_DIR/tiny-tokens.txt",
                    numThreads = NUM_THREADS,
                    modelType = "whisper"
                )
            )
            OfflineRecognizer(assetManager = context.assets, config = config)
        } catch (error: Exception) {
            Log.e(TAG, "failed to create bundled Whisper Tiny recognizer", error)
            null
        } ?: return null

        recognizer = created
        recognizerLanguage = whisperLanguage
        return created
    }

    private fun bundledAssetsPresent(): Boolean = runCatching {
        val names = context.assets.list(ASSET_DIR)?.toSet().orEmpty()
        REQUIRED_ASSETS.all { it in names }
    }.getOrDefault(false)

    /**
     * Whisper's published multilingual vocabulary contains these Whisly source codes. Other
     * registry languages remain available for typing but are UNAVAILABLE for speech. `en-IN`
     * normalizes to `en`.
     */
    private fun normalizeLanguage(languageCode: String): String? {
        val normalized = languageCode.lowercase(Locale.US).substringBefore('-')
        return normalized.takeIf { it in WHISPER_LANGUAGE_CODES }
    }

    companion object {
        const val TAG = "SherpaStt"

        @JvmStatic
        fun isLanguageSupported(languageCode: String): Boolean =
            languageCode.lowercase(Locale.US).substringBefore('-') in WHISPER_LANGUAGE_CODES

        const val ENGINE_NAME = "sherpa-onnx Whisper Tiny INT8 multilingual"
        const val ASSET_DIR = "whisper/tiny"
        const val WHISPER_FEATURE_DIM = 80
        const val WHISPER_TAIL_PADDINGS = 1000
        const val NUM_THREADS = 2
        val REQUIRED_ASSETS = setOf(
            "tiny-encoder.int8.onnx",
            "tiny-decoder.int8.onnx",
            "tiny-tokens.txt"
        )
        val WHISPER_LANGUAGE_CODES = setOf(
            "as", "bn", "en", "gu", "hi", "kn", "ml", "mr", "ne", "pa", "sa", "sd", "ta", "te",
            "ur"
        )
    }
}
