package com.example.util

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class NotificationItem(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

class SaifNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SaifNotification"
        private val recentNotifications = mutableListOf<NotificationItem>()

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, SaifNotificationListenerService::class.java).flattenToString()
            return enabledListeners.contains(myComponent) || enabledListeners.contains(context.packageName)
        }

        fun openNotificationAccessSettings(context: Context) {
            try {
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                } else {
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                }
                val intent = Intent(action).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notification settings: ${e.message}")
            }
        }

        @Synchronized
        fun getRecentNotifications(packageFilter: String? = null): List<NotificationItem> {
            val filter = packageFilter?.lowercase()?.trim()
            return if (filter.isNullOrBlank()) {
                recentNotifications.takeLast(10).reversed()
            } else {
                recentNotifications.filter {
                    it.packageName.lowercase().contains(filter) || it.appName.lowercase().contains(filter)
                }.takeLast(5).reversed()
            }
        }

        @Synchronized
        fun getSummaryText(context: Context, packageFilter: String? = null): String {
            val list = getRecentNotifications(packageFilter)
            if (list.isEmpty()) {
                return if (!packageFilter.isNullOrBlank()) {
                    "$packageFilter ke liye koi naye notifications nahi hain."
                } else {
                    "Aapke phone par abhi koi naya notification nahi mila."
                }
            }

            val sb = StringBuilder()
            val prefix = if (!packageFilter.isNullOrBlank()) "$packageFilter ke notifications:" else "Recent notifications:"
            sb.append(prefix).append("\n")
            list.take(4).forEachIndexed { index, item ->
                sb.append("${index + 1}. [${item.appName}] ${item.title}: ${item.text}\n")
            }
            return sb.toString().trim()
        }

        @Synchronized
        private fun addNotification(item: NotificationItem) {
            recentNotifications.add(item)
            if (recentNotifications.size > 50) {
                recentNotifications.removeAt(0)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val pkg = sbn.packageName ?: ""
        // Skip self
        if (pkg == packageName) return

        val appName = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast(".")
        }

        val item = NotificationItem(
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )
        addNotification(item)
        Log.d(TAG, "New notification from $appName: $title - $text")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Can optionally handle removal
    }
}
