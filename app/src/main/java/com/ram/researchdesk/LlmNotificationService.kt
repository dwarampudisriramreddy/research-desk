package com.ram.researchdesk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

private const val CHANNEL_ID = "llm_download"
private const val NOTIF_ID = 1001

class LlmNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                createChannel()
                startForeground(NOTIF_ID, buildNotification("Downloading AI model...", 0, false))
            }
            ACTION_INIT -> {
                updateNotification("Initializing AI model...", 100, false)
            }
            ACTION_PROGRESS -> {
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "Working..."
                updateNotification(text, progress, progress in 1..99)
            }
            ACTION_READY -> {
                val backend = intent.getStringExtra(EXTRA_BACKEND) ?: ""
                updateNotification("AI ready ($backend)", 100, false)
                stopSelf()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Model",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Model download and initialization progress"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LlmNotificationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Research Desk")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .apply {
                if (indeterminate) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(100, progress, false)
                }
            }
            .build()
    }

    private fun updateNotification(text: String, progress: Int, indeterminate: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text, progress, indeterminate))
    }

    companion object {
        const val ACTION_DOWNLOAD = "download"
        const val ACTION_INIT = "init"
        const val ACTION_PROGRESS = "progress"
        const val ACTION_READY = "ready"
        const val ACTION_STOP = "stop"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TEXT = "text"
        const val EXTRA_BACKEND = "backend"

        fun startDownload(context: Context) {
            val intent = Intent(context, LlmNotificationService::class.java).apply {
                action = ACTION_DOWNLOAD
            }
            context.startForegroundService(intent)
        }

        fun updateProgress(context: Context, progress: Int, text: String) {
            val intent = Intent(context, LlmNotificationService::class.java).apply {
                action = ACTION_PROGRESS
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_TEXT, text)
            }
            context.startService(intent)
        }

        fun markReady(context: Context, backend: String) {
            val intent = Intent(context, LlmNotificationService::class.java).apply {
                action = ACTION_READY
                putExtra(EXTRA_BACKEND, backend)
            }
            context.startService(intent)
        }

        fun markInit(context: Context) {
            val intent = Intent(context, LlmNotificationService::class.java).apply {
                action = ACTION_INIT
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LlmNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
