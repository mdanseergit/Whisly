package com.example.ai.stt

import org.json.JSONObject

/**
 * Signed-package manifest for user-imported STT models.
 *
 * The manifest is plain JSON inside the `.zip`. It is validated before any byte is installed.
 * Every declared file must match its SHA-256 and byte size, so a corrupt or swapped package is
 * rejected instead of being silently used.
 */
data class SttPackageManifest(
    val packId: String,
    val engine: String,
    val languages: List<String>,
    val numThreads: Int,
    val encoder: String,
    val decoder: String?,
    val joiner: String?,
    val tokens: String,
    val license: String,
    val attribution: String,
    val files: List<FileEntry>
) {
    data class FileEntry(val path: String, val sha256: String, val size: Long)
}

sealed interface ManifestResult {
    data class Valid(val manifest: SttPackageManifest) : ManifestResult
    data class Invalid(val reason: String) : ManifestResult
}

object SttManifestParser {

    const val SCHEMA_VERSION = 1
    val SUPPORTED_ENGINES = setOf("whisper", "transducer", "nemo_ctc")
    private val SAFE_PACK_ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    private val SAFE_FILE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")
    private val SHA256 = Regex("^[0-9a-fA-F]{64}$")

    fun parse(json: String): ManifestResult {
        val root = runCatching { JSONObject(json) }
            .getOrElse { return ManifestResult.Invalid("manifest is not valid JSON") }

        val schema = root.optInt("schemaVersion", 0)
        if (schema != SCHEMA_VERSION) {
            return ManifestResult.Invalid("unsupported schemaVersion $schema")
        }

        val packId = root.optString("packId").trim().lowercase()
        if (!SAFE_PACK_ID.matches(packId)) {
            return ManifestResult.Invalid("invalid packId")
        }

        val engine = root.optString("engine").trim().lowercase()
        if (engine !in SUPPORTED_ENGINES) {
            return ManifestResult.Invalid("unsupported engine '$engine'")
        }

        val license = root.optString("license").trim()
        if (license.isBlank()) {
            return ManifestResult.Invalid("license is required")
        }
        val attribution = root.optString("attribution").trim()
        if (attribution.isBlank()) {
            return ManifestResult.Invalid("attribution is required")
        }

        val filesArray = root.optJSONArray("files")
            ?: return ManifestResult.Invalid("files array is required")
        if (filesArray.length() == 0) return ManifestResult.Invalid("files array is empty")

        val files = ArrayList<SttPackageManifest.FileEntry>(filesArray.length())
        for (i in 0 until filesArray.length()) {
            val obj = filesArray.optJSONObject(i)
                ?: return ManifestResult.Invalid("files[$i] is not an object")
            val path = obj.optString("path").trim()
            if (!SAFE_FILE_NAME.matches(path)) {
                return ManifestResult.Invalid("unsafe file path '$path'")
            }
            val sha = obj.optString("sha256").trim()
            if (!SHA256.matches(sha)) {
                return ManifestResult.Invalid("invalid sha256 for '$path'")
            }
            val size = obj.optLong("size", -1L)
            if (size <= 0L) {
                return ManifestResult.Invalid("invalid size for '$path'")
            }
            files.add(SttPackageManifest.FileEntry(path, sha.lowercase(), size))
        }

        val duplicates = files.groupBy { it.path }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            return ManifestResult.Invalid("duplicate file entries: ${duplicates.joinToString()}")
        }

        val encoder = root.optString("encoder").trim()
        val decoder = root.optString("decoder").trim().ifBlank { null }
        val joiner = root.optString("joiner").trim().ifBlank { null }
        val tokens = root.optString("tokens").trim()

        val declared = files.map { it.path }.toSet()
        fun requireFile(name: String?, role: String): String? {
            if (name.isNullOrBlank()) return "$role is required for engine '$engine'"
            if (!SAFE_FILE_NAME.matches(name)) return "$role has an unsafe file name"
            if (name !in declared) return "$role '$name' is not listed in files"
            return null
        }

        requireFile(encoder, "encoder")?.let { return ManifestResult.Invalid(it) }
        requireFile(tokens, "tokens")?.let { return ManifestResult.Invalid(it) }
        when (engine) {
            "whisper" -> requireFile(decoder, "decoder")?.let { return ManifestResult.Invalid(it) }
            "transducer" -> {
                requireFile(decoder, "decoder")?.let { return ManifestResult.Invalid(it) }
                requireFile(joiner, "joiner")?.let { return ManifestResult.Invalid(it) }
            }
        }

        if (files.none { it.path.uppercase().startsWith("LICENSE") }) {
            return ManifestResult.Invalid("a LICENSE file must be included in the package")
        }

        val languages = ArrayList<String>()
        root.optJSONArray("languages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val code = arr.optString(i).trim().lowercase()
                if (code.isNotEmpty() && !languages.contains(code)) languages.add(code)
            }
        }
        if (languages.isEmpty()) {
            return ManifestResult.Invalid("languages array is required")
        }

        return ManifestResult.Valid(
            SttPackageManifest(
                packId = packId,
                engine = engine,
                languages = languages,
                numThreads = root.optInt("numThreads", 2).coerceIn(1, 4),
                encoder = encoder,
                decoder = decoder,
                joiner = joiner,
                tokens = tokens,
                license = license,
                attribution = attribution,
                files = files
            )
        )
    }
}
