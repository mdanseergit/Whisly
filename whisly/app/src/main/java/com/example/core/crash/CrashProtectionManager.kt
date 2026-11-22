package com.example.core.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.local.WhislyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash-Loop Protection & Safe Mode Manager for Whisly.
 *
 * Implements Phase 2 specifications:
 * - Detects 2 consecutive launch crashes within the 5-second launch window.
 * - Activates Safe Mode on the 3rd attempt, bypassing heavy AI engine initialization.
 * - Captures full uncaught exception stack traces to internal disk (`crash_report.txt`).
 * - Provides non-destructive model cache clearing (strictly preserves history and settings).
 */
object CrashProtectionManager {
    private const val PREFS_NAME = "whisly_crash_protection"
    private const val KEY_FAILURE_COUNT = "launch_failure_count"
    private const val KEY_LAST_LAUNCH_TIME = "last_launch_time"
    private const val KEY_LAST_CRASH_TRACE = "last_crash_trace"
    private const val CRASH_FILE_NAME = "crash_report.txt"
    const val LAUNCH_STABILITY_WINDOW_MS = 5000L

    private var defaultHandlerInstalled = false

    fun installCrashHandler(context: Context) {
        if (defaultHandlerInstalled) return
        defaultHandlerInstalled = true
        val appContext = context.applicationContext
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                recordCrash(appContext, thread, throwable)
            }
            oldHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timestamp = sdf.format(Date())

        val reportBuilder = StringBuilder()
        reportBuilder.append("=== WHISLY CRASH REPORT ===\n")
        reportBuilder.append("Timestamp: ").append(timestamp).append("\n")
        reportBuilder.append("Package: ").append(context.packageName).append("\n")
        reportBuilder.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        reportBuilder.append("Brand/Product: ").append(Build.BRAND).append(" / ").append(Build.PRODUCT).append("\n")
        reportBuilder.append("Android SDK: ").append(Build.VERSION.SDK_INT).append(" (Release ").append(Build.VERSION.RELEASE).append(")\n")
        reportBuilder.append("Supported ABIs: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n")
        reportBuilder.append("Thread: ").append(thread.name).append(" (id=").append(thread.id).append(")\n")
        reportBuilder.append("Exception: ").append(throwable.javaClass.name).append("\n")
        reportBuilder.append("Message: ").append(throwable.message).append("\n")
        reportBuilder.append("\n--- STACK TRACE ---\n")
        reportBuilder.append(stackTrace)
        reportBuilder.append("\n===========================\n")

        val fullReport = reportBuilder.toString()

        runCatching {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            file.writeText(fullReport, Charsets.UTF_8)
        }

        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_CRASH_TRACE, fullReport).commit()
        }
    }

    /**
     * Checks if repeated launch failures occurred.
     * Returns `true` if Safe Mode should be engaged (>= 2 previous consecutive uncompleted launches).
     */
    fun checkAndRecordLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val failureCount = prefs.getInt(KEY_FAILURE_COUNT, 0)
        val lastLaunchTime = prefs.getLong(KEY_LAST_LAUNCH_TIME, 0L)
        val now = System.currentTimeMillis()

        // Only treat as a crash-loop if launches are rapid (within 30s). Normal reopen hours/days
        // later resets the counter so closing the app quickly is not misclassified as a crash.
        val rapidRelaunch = lastLaunchTime > 0L && (now - lastLaunchTime) <= 30_000L
        val effectiveCount = if (rapidRelaunch) failureCount else 0

        if (effectiveCount >= 2) {
            return true
        }

        prefs.edit()
            .putInt(KEY_FAILURE_COUNT, effectiveCount + 1)
            .putLong(KEY_LAST_LAUNCH_TIME, now)
            .commit()

        return false
    }

    /**
     * Called when the app successfully completes launch past the stability threshold (e.g. 5 seconds)
     * and enters stable idle state.
     */
    fun markLaunchSuccessful(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FAILURE_COUNT, 0).apply()
    }

    fun resetCrashLoop(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FAILURE_COUNT, 0).apply()
    }

    fun getCrashReport(context: Context): String {
        return runCatching {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists() && file.length() > 0) {
                file.readText(Charsets.UTF_8)
            } else {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(KEY_LAST_CRASH_TRACE, null) ?: "No crash details recorded."
            }
        }.getOrDefault("No crash details recorded.")
    }

    /**
     * Non-destructive model cache cleanup.
     * Clears downloaded model weights from disk and resets installation flags in Room DB.
     * Strictly preserves translation history, saved phrases, and app settings.
     */
    fun clearDownloadedModelPacks(context: Context, onComplete: () -> Unit) {
        val appContext = context.applicationContext
        resetCrashLoop(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // Delete files in models directory
                val modelsDir = File(appContext.filesDir, "models")
                if (modelsDir.exists()) {
                    modelsDir.deleteRecursively()
                }
                // Delete files in packs directory
                val packsDir = File(appContext.filesDir, "packs")
                if (packsDir.exists()) {
                    packsDir.deleteRecursively()
                }

                // Reset Room DB installed models while preserving history and settings
                val db = WhislyDatabase.getInstance(appContext)
                val dao = db.whislyDao()
                dao.deleteAllInstalledModels()
                dao.resetAllLanguageInstallStates()
            }
            CoroutineScope(Dispatchers.Main).launch {
                onComplete()
            }
        }
    }
}
