package com.example.ai.stt

import android.content.Context
import android.util.Log
import com.example.domain.models.PipelineErrorType
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Independent on-device STT runtime backed by sherpa-onnx (Apache-2.0).
 *
 * No model is bundled. A package becomes usable only after the user imports a verified model
 * package (see [ModelImportManager]) or a verified pack downloader installs one. Directories that
 * fail manifest validation are ignored, so a corrupt pack can never silently run.
 *
 * Supported model kinds (selected by the manifest):
 *  - `whisper`     : encoder.int8.onnx + decoder.int8.onnx + tokens.txt (multilingual)
 *  - `transducer`  : encoder/decoder/joiner (IndicConformer / zipformer transducer)
 *  - `nemo_ctc`    : a single .onnx model + tokens.txt
 */
class SherpaOnnxSttEngine(private val context: Context) : SttDecoder {

    companion object {
        private const val TAG = "SherpaStt"
        const val MANIFEST_NAME = "whisly-stt.json"
        private val SAFE_NAME = Regex("^[A-Za-z0-9._-]+$")
    }

    data class SttModel(
        val packId: String,
        val engine: String,
        val languages: Set<String>,
        val directory: File,
        val encoder: File?,
        val decoder: File?,
        val joiner: File?,
        val tokens: File,
        val numThreads: Int,
        val license: String,
        val attribution: String
    )

    private val modelsRoot: File
        get() = File(context.filesDir, "whisly_models/stt").apply { mkdirs() }

    private val decodeMutex = Mutex()

    @Volatile
    private var residentPackId: String? = null
    private var resident: OfflineRecognizer? = null

    /** Reads every installed package directory and keeps only valid manifests. */
    fun installedModels(): List<SttModel> {
        val dirs = modelsRoot.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readManifest(it) }.sortedBy { it.packId }
    }

    fun hasModelFor(languageCode: String): Boolean = findModel(languageCode) != null

    fun modelSummary(languageCode: String): String? = findModel(languageCode)?.let {
        "${it.packId} (${it.engine})"
    }

    /** Preferred model for a language: exact match first, then a model explicitly listing it. */
    private fun findModel(languageCode: String): SttModel? {
        val code = languageCode.lowercase()
        val models = installedModels()
        return models.firstOrNull { it.languages.contains(code) }
            ?: models.firstOrNull { it.languages.isEmpty() && it.engine == "whisper" }
    }

    override suspend fun decode(
        pcm16: ShortArray,
        sampleRateHz: Int,
        languageCode: String
    ): SttDecode = withContext(Dispatchers.Default) {
        if (pcm16.isEmpty()) {
            return@withContext SttDecode.Failure(PipelineErrorType.SPEECH_NOT_DETECTED, "empty segment")
        }
        val model = findModel(languageCode)
            ?: return@withContext SttDecode.Failure(
                PipelineErrorType.STT_MODEL_MISSING,
                "no installed model for '$languageCode'"
            )

        decodeMutex.withLock {
            var stream: OfflineStream? = null
            try {
                val recognizer = obtainRecognizer(model)
                    ?: return@withLock SttDecode.Failure(
                        PipelineErrorType.STT_FAILURE,
                        "recognizer init failed for ${model.packId}"
                    )

                val samples = FloatArray(pcm16.size)
                for (i in pcm16.indices) {
                    samples[i] = (pcm16[i] / 32768.0f).coerceIn(-1f, 1f)
                }

                stream = recognizer.createStream()
                stream.acceptWaveform(samples, sampleRateHz)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                val text = result.text.trim()

                if (text.isEmpty()) {
                    SttDecode.Failure(PipelineErrorType.STT_FAILURE, "blank decode")
                } else {
                    SttDecode.Text(text = text, confidence = null, engine = model.packId)
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "sherpa-onnx native library unavailable for this ABI", e)
                SttDecode.Failure(PipelineErrorType.STT_FAILURE, "native runtime unavailable")
            } catch (e: Throwable) {
                Log.e(TAG, "decode failed", e)
                SttDecode.Failure(PipelineErrorType.STT_FAILURE, e.javaClass.simpleName)
            } finally {
                runCatching { stream?.release() }
            }
        }
    }

    /** Frees native memory. Called on app backgrounding / trim-memory. */
    fun unload() {
        runCatching { resident?.release() }
        resident = null
        residentPackId = null
    }

    private fun obtainRecognizer(model: SttModel): OfflineRecognizer? {
        if (residentPackId == model.packId && resident != null) return resident
        runCatching { resident?.release() }
        resident = null
        residentPackId = null

        val created = runCatching { OfflineRecognizer(null, buildConfig(model)) }.getOrNull()
        if (created != null) {
            resident = created
            residentPackId = model.packId
        }
        return created
    }

    private fun buildConfig(model: SttModel): OfflineRecognizerConfig {
        val modelConfig = OfflineModelConfig().apply {
            tokens = model.tokens.absolutePath
            numThreads = model.numThreads
            when (model.engine) {
                "whisper" -> whisper = OfflineWhisperModelConfig().apply {
                    encoder = model.encoder?.absolutePath.orEmpty()
                    decoder = model.decoder?.absolutePath.orEmpty()
                    language = if (model.languages.size == 1) model.languages.first() else ""
                    task = "transcribe"
                }
                "transducer" -> transducer = OfflineTransducerModelConfig().apply {
                    encoder = model.encoder?.absolutePath.orEmpty()
                    decoder = model.decoder?.absolutePath.orEmpty()
                    joiner = model.joiner?.absolutePath.orEmpty()
                }
                "nemo_ctc" -> nemo = OfflineNemoEncDecCtcModelConfig().apply {
                    this.model = model.encoder?.absolutePath.orEmpty()
                }
                else -> error("unsupported engine ${model.engine}")
            }
        }
        return OfflineRecognizerConfig().apply {
            this.modelConfig = modelConfig
            decodingMethod = "greedy_search"
        }
    }

    private fun readManifest(dir: File): SttModel? {
        val manifestFile = File(dir, MANIFEST_NAME)
        if (!manifestFile.isFile) return null
        return runCatching {
            val json = JSONObject(manifestFile.readText())
            val packId = json.optString("packId")
            val engine = json.optString("engine").lowercase()
            if (!SAFE_NAME.matches(packId)) return@runCatching null
            if (engine !in setOf("whisper", "transducer", "nemo_ctc")) return@runCatching null

            val tokens = json.optString("tokens")
            if (tokens.isBlank()) return@runCatching null
            val tokensFile = safeChild(dir, tokens) ?: return@runCatching null
            if (!tokensFile.isFile) return@runCatching null

            val encoderName = json.optString("encoder")
            val decoderName = json.optString("decoder")
            val joinerName = json.optString("joiner")

            val encoder = if (encoderName.isBlank()) null else safeChild(dir, encoderName)
            val decoder = if (decoderName.isBlank()) null else safeChild(dir, decoderName)
            val joiner = if (joinerName.isBlank()) null else safeChild(dir, joinerName)

            val requiredOk = when (engine) {
                "whisper" -> encoder?.isFile == true && decoder?.isFile == true
                "transducer" -> encoder?.isFile == true && decoder?.isFile == true && joiner?.isFile == true
                else -> encoder?.isFile == true
            }
            if (!requiredOk) return@runCatching null

            val languages = mutableSetOf<String>()
            json.optJSONArray("languages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val code = arr.optString(i).trim().lowercase()
                    if (code.isNotEmpty()) languages.add(code)
                }
            }

            SttModel(
                packId = packId,
                engine = engine,
                languages = languages,
                directory = dir,
                encoder = encoder,
                decoder = decoder,
                joiner = joiner,
                tokens = tokensFile,
                numThreads = json.optInt("numThreads", 2).coerceIn(1, 4),
                license = json.optString("license"),
                attribution = json.optString("attribution")
            )
        }.getOrNull()
    }

    /** Resolves a manifest-declared file name without allowing path traversal. */
    private fun safeChild(dir: File, name: String): File? {
        if (name.contains("..") || name.contains('/') || name.contains('\\')) return null
        if (!SAFE_NAME.matches(name)) return null
        return File(dir, name)
    }
}
