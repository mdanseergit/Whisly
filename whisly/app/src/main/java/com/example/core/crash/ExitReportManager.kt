package com.example.core.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historical Process Exit Reason Manager (Phase 0, Requirement 6).
 *
 * Captures historical exit metadata from Android's `ActivityManager.getHistoricalProcessExitReasons` (API 30+):
 * - Exit reason code (CRASH, CRASH_NATIVE, LOW_MEMORY, ANR, SIGNALED, etc.)
 * - Timestamp, importance level, and status code
 * - Stack trace summary / system description (strictly excluding user content)
 * - Enables no-PC diagnostic reporting directly on the user's phone.
 */
object ExitReportManager {
    private const val EXIT_REPORT_FILE = "historical_exit_report.txt"

    fun captureHistoricalExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return

        runCatching {
            val exitList = am.getHistoricalProcessExitReasons(appContext.packageName, 0, 10)
            if (exitList.isNullOrEmpty()) return@runCatching

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val sb = StringBuilder()
            sb.append("=== WHISLY HISTORICAL EXIT REPORT (ApplicationExitInfo) ===\n")
            sb.append("Generated: ").append(sdf.format(Date())).append("\n")
            sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            sb.append("Android SDK: ").append(Build.VERSION.SDK_INT).append(" (Release ").append(Build.VERSION.RELEASE).append(")\n")
            sb.append("Supported ABIs: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n\n")

            exitList.forEachIndexed { index, exitInfo ->
                sb.append("--- EXIT ENTRY #").append(index + 1).append(" ---\n")
                sb.append("Timestamp: ").append(sdf.format(Date(exitInfo.timestamp))).append("\n")
                sb.append("PID: ").append(exitInfo.pid).append(", UID: ").append(exitInfo.realUid).append("\n")
                sb.append("Process State: ").append(getImportanceName(exitInfo.importance)).append("\n")
                sb.append("Exit Reason: ").append(exitInfo.reason).append(" -> ").append(getReasonName(exitInfo.reason)).append("\n")
                sb.append("Status / Signal: ").append(exitInfo.status).append("\n")
                if (!exitInfo.description.isNullOrBlank()) {
                    sb.append("Description: ").append(exitInfo.description).append("\n")
                }

                val traceSummary = readTraceSummary(exitInfo)
                if (traceSummary.isNotBlank()) {
                    sb.append("Trace Summary:\n").append(traceSummary).append("\n")
                }
                sb.append("\n")
            }
            sb.append("============================================================\n")

            val file = File(appContext.filesDir, EXIT_REPORT_FILE)
            file.writeText(sb.toString(), Charsets.UTF_8)
        }
    }

    private fun readTraceSummary(exitInfo: ApplicationExitInfo): String {
        return runCatching {
            val inputStream = exitInfo.traceInputStream ?: return@runCatching ""
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                val lines = mutableListOf<String>()
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null && count < 40) {
                    val l = line?.trim()
                    if (!l.isNullOrBlank()) {
                        lines.add(l)
                        count++
                    }
                }
                lines.joinToString("\n")
            }
        }.getOrDefault("")
    }

    fun getExitReport(context: Context): String {
        val file = File(context.filesDir, EXIT_REPORT_FILE)
        return if (file.exists() && file.length() > 0) {
            runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("No historical exit report file.")
        } else {
            "No historical exit events recorded on this device yet (API 30+ ActivityManager history)."
        }
    }

    private fun getReasonName(reason: Int): String {
        return when (reason) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF (App called finish/exitProcess)"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (Process killed by Linux signal)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (LowMemoryKiller / OOM)"
            ApplicationExitInfo.REASON_CRASH -> "CRASH (Java/Kotlin unhandled exception)"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (Native signal / SIGSEGV / SIGABRT)"
            ApplicationExitInfo.REASON_ANR -> "ANR (Application Not Responding)"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            14 -> "FREEZER (App frozen by system)"
            else -> "UNKNOWN ($reason)"
        }
    }

    private fun getImportanceName(importance: Int): String {
        return when (importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND (Active on screen)"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
            else -> "CODE ($importance)"
        }
    }
}
