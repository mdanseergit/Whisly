package com.example.ai.stt

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

sealed interface ModelImportResult {
    data class Installed(
        val packId: String,
        val languages: List<String>,
        val totalBytes: Long
    ) : ModelImportResult

    data class Rejected(val reason: String) : ModelImportResult
}

/**
 * Zero-cost, network-free model installation.
 *
 * The user picks a `.zip` (for example with the system Files app). Nothing is installed until:
 *  1. the manifest parses and passes validation,
 *  2. every declared file is present exactly once,
 *  3. every file's byte size and SHA-256 match the manifest,
 *  4. a LICENSE file is included,
 *  5. enough free space exists.
 *
 * Files are written to a staging directory and renamed into place atomically. A failed import can
 * never leave a half-installed model that the runtime would pick up.
 */
class ModelImportManager(private val context: Context) {

    companion object {
        private const val STAGING_ROOT = "whisly_models_staging"
        private const val MODELS_ROOT = "whisly_models/stt"
        private const val MAX_ARCHIVE_BYTES = 4L * 1024L * 1024L * 1024L
    }

    private val modelsRoot: File
        get() = File(context.filesDir, MODELS_ROOT).apply { mkdirs() }

    private val stagingRoot: File
        get() = File(context.filesDir, STAGING_ROOT).apply { mkdirs() }

    fun installedPackIds(): List<String> =
        SherpaOnnxSttEngine(context).installedModels().map { it.packId }

    /** Removes an installed model package. Returns true when the directory is gone. */
    fun removePack(packId: String): Boolean {
        if (!packId.matches(Regex("^[a-z0-9][a-z0-9._-]{0,63}$"))) return false
        return runCatching { File(modelsRoot, packId).deleteRecursively() }.getOrDefault(false)
    }

    suspend fun importZip(
        uri: Uri,
        onProgress: (bytesHashed: Long, bytesTotal: Long) -> Unit = { _, _ -> }
    ): ModelImportResult = withContext(Dispatchers.IO) {
        val cacheCopy = File(context.cacheDir, "whisly_import_${System.currentTimeMillis()}.zip")
        var staging: File? = null
        try {
            val declaredSize = querySize(uri)
            if (declaredSize > MAX_ARCHIVE_BYTES) {
                return@withContext ModelImportResult.Rejected("package is larger than 4 GB")
            }

            // 1. Copy the content URI somewhere we can seek in.
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheCopy).use { output -> input.copyTo(output, 128 * 1024) }
            } ?: return@withContext ModelImportResult.Rejected("could not open the selected file")

            ZipFile(cacheCopy).use { zip ->
                val manifestEntry = findUniqueEntry(zip, "whisly-stt.json")
                    ?: return@withContext ModelImportResult.Rejected(
                        "package has no whisly-stt.json manifest at its root"
                    )
                val manifestJson = zip.getInputStream(manifestEntry).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                val manifest = when (val parsed = SttManifestParser.parse(manifestJson)) {
                    is ManifestResult.Invalid -> return@withContext ModelImportResult.Rejected(parsed.reason)
                    is ManifestResult.Valid -> parsed.manifest
                }

                val totalBytes = manifest.files.sumOf { it.size }
                val freeBytes = runCatching {
                    val stat = StatFs(context.filesDir.absolutePath)
                    stat.availableBlocksLong * stat.blockSizeLong
                }.getOrDefault(0L)
                if (freeBytes < totalBytes + totalBytes / 4 + 32L * 1024 * 1024) {
                    return@withContext ModelImportResult.Rejected("not enough free storage for this package")
                }

                // 2. Verify and extract into staging.
                staging = File(stagingRoot, "stt-${manifest.packId}-${System.currentTimeMillis()}")
                staging.mkdirs()

                var hashed = 0L
                onProgress(0L, totalBytes)
                for (entry in manifest.files) {
                    val zipEntry = findUniqueEntry(zip, entry.path)
                        ?: return@withContext ModelImportResult.Rejected(
                            "missing file '${entry.path}' in package"
                        )
                    if (zipEntry.size >= 0L && zipEntry.size != entry.size) {
                        return@withContext ModelImportResult.Rejected(
                            "size mismatch for '${entry.path}'"
                        )
                    }
                    val target = File(staging, entry.path)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var written = 0L
                    zip.getInputStream(zipEntry).use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                                written += read
                                hashed += read
                                onProgress(hashed, totalBytes)
                            }
                        }
                    }
                    if (written != entry.size) {
                        return@withContext ModelImportResult.Rejected(
                            "size mismatch for '${entry.path}'"
                        )
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(entry.sha256, ignoreCase = true)) {
                        return@withContext ModelImportResult.Rejected(
                            "SHA-256 mismatch for '${entry.path}'"
                        )
                    }
                }

                // 3. Write the normalized runtime manifest.
                val runtimeManifest = JSONObject().apply {
                    put("schemaVersion", SttManifestParser.SCHEMA_VERSION)
                    put("packId", manifest.packId)
                    put("engine", manifest.engine)
                    put("languages", JSONArray(manifest.languages))
                    put("numThreads", manifest.numThreads)
                    put("encoder", manifest.encoder)
                    manifest.decoder?.let { put("decoder", it) }
                    manifest.joiner?.let { put("joiner", it) }
                    put("tokens", manifest.tokens)
                    put("license", manifest.license)
                    put("attribution", manifest.attribution)
                }
                File(staging, SherpaOnnxSttEngine.MANIFEST_NAME)
                    .writeText(runtimeManifest.toString(2), Charsets.UTF_8)

                // 4. Atomic install.
                val target = File(modelsRoot, manifest.packId)
                val backup = File(modelsRoot, "${manifest.packId}.prev")
                var hadBackup = false
                if (target.exists()) {
                    backup.deleteRecursively()
                    if (target.renameTo(backup)) hadBackup = true
                }
                if (!staging.renameTo(target)) {
                    if (hadBackup) backup.renameTo(target)
                    return@withContext ModelImportResult.Rejected("could not install the package")
                }
                if (hadBackup) backup.deleteRecursively()
                staging = null

                ModelImportResult.Installed(
                    packId = manifest.packId,
                    languages = manifest.languages,
                    totalBytes = totalBytes
                )
            }
        } catch (e: Exception) {
            ModelImportResult.Rejected(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { staging?.deleteRecursively() }
            runCatching { cacheCopy.delete() }
        }
    }

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                0L
            }
        } ?: 0L
    }.getOrDefault(0L)

    /** Finds exactly one non-directory entry matching [name] at the root or inside one folder. */
    private fun findUniqueEntry(zip: ZipFile, name: String): java.util.zip.ZipEntry? {
        val matches = zip.entries().asSequence()
            .filter { !it.isDirectory && (it.name == name || it.name.endsWith("/$name")) }
            .toList()
        return matches.singleOrNull()
    }
}
