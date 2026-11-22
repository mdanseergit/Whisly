package com.example.ai.modelmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service for robust, crash-resilient language pack downloads on Android 13/14+.
 * Handles Android 14+ FGS restrictions gracefully with try-catch and valid application drawables.
 */
class WhislyDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "whisly_downloads"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START_DOWNLOAD = "com.example.whisly.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.whisly.CANCEL_DOWNLOAD"
        const val EXTRA_LANG_CODE = "extra_lang_code"
        const val EXTRA_LANG_NAME = "extra_lang_name"

        fun start(context: Context, langCode: String, langName: String) {
            runCatching {
                val intent = Intent(context, WhislyDownloadService::class.java).apply {
                    action = ACTION_START_DOWNLOAD
                    putExtra(EXTRA_LANG_CODE, langCode)
                    putExtra(EXTRA_LANG_NAME, langName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun updateProgress(context: Context, langName: String, progressPercent: Int, statusText: String) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Language Pack Downloads",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows progress for downloading offline AI translation packs"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(channel)
                }
                val openAppIntent = Intent(context, MainActivity::class.java)
                val openPending = PendingIntent.getActivity(
                    context, 0, openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val cancelIntent = Intent(context, WhislyDownloadService::class.java).apply {
                    action = ACTION_CANCEL_DOWNLOAD
                }
                val cancelPending = PendingIntent.getService(
                    context, 1, cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(com.example.R.drawable.whisly_tile_logo)
                    .setContentTitle("Downloading $langName")
                    .setContentText(statusText)
                    .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent <= 0)
                    .setContentIntent(openPending)
                    .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
                nm.notify(NOTIFICATION_ID, notification)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                context.stopService(Intent(context, WhislyDownloadService::class.java))
            }
        }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        runCatching {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val langCode = intent?.getStringExtra(EXTRA_LANG_CODE).orEmpty()
        val langName = intent?.getStringExtra(EXTRA_LANG_NAME).orEmpty().ifBlank { "Language" }

        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            WhislyModelManager.cancelActiveDownload(langCode)
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        runCatching {
            val notification = buildProgressNotification(langName, 0, "Starting download…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            // Android 14 FGS start restriction fallback: stop cleanly without crashing
            runCatching { stopSelf() }
        }

        return START_NOT_STICKY
    }

    fun notifyCompleted(langName: String) {
        runCatching {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.whisly_tile_logo)
                .setContentTitle("$langName is ready offline")
                .setContentText("Language pack downloaded and verified")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID + 1, notification)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildProgressNotification(
        langName: String,
        progressPercent: Int,
        statusText: String
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, WhislyDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPending = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.whisly_tile_logo)
            .setContentTitle("Downloading $langName")
            .setContentText(statusText)
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent <= 0)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Language Pack Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for downloading offline AI translation packs"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
