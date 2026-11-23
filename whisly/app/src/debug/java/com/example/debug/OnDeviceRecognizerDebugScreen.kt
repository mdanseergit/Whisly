package com.example.debug

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEBUG-ONLY screen. Lives in src/debug so it is not shipped in release builds.
 *
 * Reports on-device recognizer support/install state per language using the official
 * Android APIs, can trigger a language-pack download from inside the app (no Settings
 * intent is launched), can run pinned evaluation WAV clips from
 * `context.filesDir/eval_clips/`, and exports raw results as CSV via the share sheet.
 *
 * All runtime outcomes are UNMEASURED until a human runs this on a real device.
 */
data class DebugLangRow(
    val code: String,
    val tag: String,
    var installed: String = "UNMEASURED",
    var pending: String = "UNMEASURED",
    var supported: String = "UNMEASURED",
    var online: String = "UNMEASURED",
)

data class DebugResultRow(
    val clip: String,
    val language: String,
    val reference: String,
    val hypothesis: String,
    val confidence: String,
    val error: String,
    val elapsedMs: Long,
)

private val DEBUG_LANGUAGES = listOf(
    "en" to "en-US", "hi" to "hi-IN", "bn" to "bn-IN", "ta" to "ta-IN",
    "te" to "te-IN", "kn" to "kn-IN", "ml" to "ml-IN", "mr" to "mr-IN",
    "gu" to "gu-IN", "pa" to "pa-IN", "ur" to "ur-PK", "or" to "or-IN",
    "as" to "as-IN", "sat" to "sat-IN",
)

private val mainHandler = Handler(Looper.getMainLooper())

private fun reportIntent(tag: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)

@Composable
fun OnDeviceRecognizerDebugRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var availability by remember { mutableStateOf("UNMEASURED") }
    var supportError by remember { mutableStateOf("") }
    var langRows by remember {
        mutableStateOf(DEBUG_LANGUAGES.map { (code, tag) -> DebugLangRow(code, tag) })
    }
    var status by remember { mutableStateOf("Not run yet — all results UNMEASURED") }
    var results by remember { mutableStateOf(listOf<DebugResultRow>()) }
    var running by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "RECORD_AUDIO granted" else "RECORD_AUDIO denied — clip runner cannot use mic path"
    }

    fun refreshSupport() {
        status = "Checking on-device recognition support..."
        mainHandler.post {
            availability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) "AVAILABLE" else "NOT AVAILABLE on this device/API"
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                status = "checkRecognitionSupport requires API 33+; support rows stay UNMEASURED"
                return@post
            }
            val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            // Keep the recognizer alive until the async callback arrives; destroy() only then.
            probe.checkRecognitionSupport(
                reportIntent("en-US"), context.mainExecutor,
                object : RecognitionSupportCallback {
                    private fun finish() = probe.destroy()
                    override fun onSupportResult(support: RecognitionSupport) {
                        val installed = support.installedOnDeviceLanguages.toSet()
                        val pending = support.pendingOnDeviceLanguages.toSet()
                        val supported = support.supportedOnDeviceLanguages.toSet()
                        val online = support.onlineLanguages.toSet()
                        langRows = DEBUG_LANGUAGES.map { (code, tag) ->
                            DebugLangRow(
                                code = code, tag = tag,
                                installed = if (installed.any { it.startsWith(code, true) }) "YES" else "NO",
                                pending = if (pending.any { it.startsWith(code, true) }) "YES" else "NO",
                                supported = if (supported.any { it.startsWith(code, true) }) "YES" else "NO",
                                online = if (online.any { it.startsWith(code, true) }) "YES" else "NO",
                            )
                        }
                        status = "Support report measured on this device"
                        finish()
                    }
                    override fun onError(error: Int) {
                        supportError = "checkRecognitionSupport error=$error"
                        status = supportError
                        finish()
                    }
                }
            )
        }
    }

    fun download(row: DebugLangRow) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            status = "triggerModelDownload requires API 33+"
            return
        }
        mainHandler.post {
            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                rec.triggerModelDownload(reportIntent(row.tag), context.mainExecutor,
                    object : android.speech.ModelDownloadListener {
                        private fun finish() = rec.destroy()
                        override fun onProgress(progress: Int) {
                            status = "${row.code} download progress=$progress%"
                        }
                        override fun onSuccess() {
                            status = "${row.code} download SUCCESS — re-run Refresh"
                            finish()
                        }
                        override fun onScheduled() {
                            status = "${row.code} download SCHEDULED (no further updates)"
                            finish()
                        }
                        override fun onError(error: Int) {
                            status = "${row.code} download ERROR=$error"
                            finish()
                        }
                    })
            } else {
                @Suppress("DEPRECATION")
                rec.triggerModelDownload(reportIntent(row.tag))
                status = "${row.code} download requested (API 33, no progress listener)"
                rec.destroy()
            }
        }
    }

    suspend fun runClips() {
        val dir = File(context.filesDir, "eval_clips")
        val clips = dir.listFiles { f -> f.extension.equals("wav", true) }?.sortedBy { it.name }.orEmpty()
        if (clips.isEmpty()) {
            status = "No WAV clips in ${dir.absolutePath} — results stay UNMEASURED"
            return
        }
        val out = mutableListOf<DebugResultRow>()
        withContext(Dispatchers.Main) {
            for (clip in clips) {
                status = "Running ${clip.name} (${out.size + 1}/${clips.size})"
                val tag = clip.name.substringBefore("_").let { code ->
                    DEBUG_LANGUAGES.firstOrNull { it.first.equals(code, true) }?.second ?: "en-US"
                }
                val pfd = ParcelFileDescriptor.open(clip, ParcelFileDescriptor.MODE_READ_ONLY)
                val done = CompletableDeferred<DebugResultRow>()
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                val t0 = System.currentTimeMillis()
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        done.complete(DebugResultRow(clip.name, tag, "", "", "", "ERROR=$error", System.currentTimeMillis() - t0))
                    }
                    override fun onResults(bundle: Bundle) {
                        val text = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        val conf = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                            ?.firstOrNull()?.let { "%.3f".format(Locale.US, it) } ?: "NO_CONFIDENCE"
                        done.complete(DebugResultRow(clip.name, tag, "", text, conf, "", System.currentTimeMillis() - t0))
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = reportIntent(tag)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                try {
                    recognizer.startListening(intent)
                    out.add(done.await())
                } finally {
                    recognizer.destroy()
                    pfd.close()
                    results = out.toList()
                }
            }
        }
        status = "Ran ${out.size} clips — results are real device measurements"
    }

    fun exportCsv() {
        if (results.isEmpty()) {
            status = "No results to export"
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "whisly_ondevice_eval_$stamp.csv")
        val csv = buildString {
            append("clip,language,reference,hypothesis,confidence,error,elapsed_ms\n")
            results.forEach {
                fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
                append(listOf(it.clip, it.language, it.reference, it.hypothesis, it.confidence, it.error, it.elapsedMs.toString())
                    .joinToString(",") { v -> esc(v) }).append('\n')
            }
        }
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share on-device eval CSV"))
    }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        refreshSupport()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("On-device recognizer probe (DEBUG)", style = MaterialTheme.typography.titleMedium)
        }
        Text("Availability: $availability")
        Text("Status: $status")
        if (supportError.isNotEmpty()) Text(supportError)

        Row {
            Button(onClick = { refreshSupport() }) { Text("Refresh support") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { exportCsv() }, enabled = results.isNotEmpty()) { Text("Export CSV") }
        }
        Text("Clip runner reads WAV files from ${File(context.filesDir, "eval_clips").absolutePath}")
        Button(onClick = { scope.launch { running = true; runClips(); running = false } }, enabled = !running) {
            Text(if (running) "Running..." else "Run evaluation clips")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Per-language support", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(langRows) { row ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${row.code} / ${row.tag}")
                    Text("installed=${row.installed} pending=${row.pending} supported=${row.supported} online=${row.online}")
                    Button(onClick = { download(row) }, enabled = row.supported == "YES" && row.installed != "YES") {
                        Text("Download language pack (in-app)")
                    }
                }
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Results (${results.size})", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(results) { r ->
                Text("${r.clip} ${r.language}: ${r.hypothesis.ifEmpty { r.error }} conf=${r.confidence} ${r.elapsedMs}ms")
            }
        }
    }
}
