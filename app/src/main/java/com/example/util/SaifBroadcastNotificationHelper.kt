package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * High-priority broadcast and push notification manager for SAIF AI Studio.
 * Delivers real Android system notifications to user devices when triggered from Admin Dashboard.
 */
object SaifBroadcastNotificationHelper {
    private const val TAG = "SaifBroadcastNotificationHelper"
    const val CHANNEL_ID = "saif_admin_broadcast_channel"
    const val CHANNEL_NAME = "SAIF AI Broadcasts & Updates"
    private const val NOTIFICATION_ID_BASE = 8800

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Critical system announcements, model updates, and notifications from SAIF AI Admin"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun postBroadcastNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt()
    ): Boolean {
        return try {
            initNotificationChannel(context)

            // Verify permission on Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted yet")
                    // Still attempt or log
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_admin_notification", true)
                putExtra("notification_title", title)
                putExtra("notification_message", message)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val cleanTitle = title.ifBlank { "📢 SAIF AI Announcement" }
            val cleanMessage = message.ifBlank { "New update available from SAIF AI Studio." }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(cleanTitle)
                .setContentText(cleanMessage)
                .setStyle(NotificationCompat.BigTextStyle().bigText(cleanMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, builder.build())
                Log.i(TAG, "Broadcast notification successfully posted: $cleanTitle")
                true
            } else {
                Log.w(TAG, "Cannot post notification: missing POST_NOTIFICATIONS permission")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post broadcast notification", e)
            false
        }
    }
}
