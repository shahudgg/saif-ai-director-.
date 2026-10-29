package com.example.builder.agent

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
import com.example.R
import kotlinx.coroutines.*

/**
 * Foreground Service for running long autonomous builds safely in the background
 * without getting killed by the Android OS memory pressure.
 */
class BuildService : Service() {

    companion object {
        const val CHANNEL_ID = "saif_build_agent_channel"
        const val NOTIF_ID = 9001
        const val ACTION_STOP = "com.example.builder.agent.ACTION_STOP"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_STATUS = "extra_status"

        @Volatile
        var isBuildRunning = false
            private set

        fun start(context: Context, appName: String, initialStatus: String) {
            val intent = Intent(context, BuildService::class.java).apply {
                putExtra(EXTRA_PROJECT_NAME, appName)
                putExtra(EXTRA_STATUS, initialStatus)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, status: String) {
            if (!isBuildRunning) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notif = createNotification(context, status)
            nm?.notify(NOTIF_ID, notif)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BuildService::class.java)
            context.stopService(intent)
            isBuildRunning = false
        }

        private fun createNotification(context: Context, status: String): android.app.Notification {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            val stopIntent = Intent(context, BuildService::class.java).apply {
                action = ACTION_STOP
            }
            val pendingStop = PendingIntent.getService(
                context,
                1,
                stopIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SAIF AI Build Agent")
                .setContentText(status)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingOpen)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isBuildRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            isBuildRunning = false
            return START_NOT_STICKY
        }

        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Building application..."
        val notification = createNotification(this, status)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAIF AI Build Agent Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live build task progress when generating mobile apps"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isBuildRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
