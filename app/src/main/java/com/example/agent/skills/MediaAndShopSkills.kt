package com.example.agent.skills

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.agent.ScreenObserver
import com.example.util.SaifNotificationListenerService
import kotlinx.coroutines.delay
import org.json.JSONObject

object PlayStoreSkill : Skill {
    override val name: String = "play_store_update_all"
    const val PLAY_STORE_PKG = "com.android.vending"

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val intent = env.context.packageManager.getLaunchIntentForPackage(PLAY_STORE_PKG)
        if (intent == null) return SkillResult(false, "Google Play Store not found")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        env.context.startActivity(intent)

        val service = env.service ?: return SkillResult(true, "Play Store opened")
        service.waitForPackage(PLAY_STORE_PKG, 4000)
        service.waitForIdle(2000, 300)
        delay(600)

        // 1. Click Profile icon (account/avatar at top right)
        val profileClicked = service.clickElementByQuery("Google Account") || service.clickElementByQuery("Account") || service.clickElementByQuery("Profile")
        delay(1000)
        service.waitForIdle(1500, 300)

        // 2. Click "Manage apps & device"
        val manageClicked = service.clickElementByQuery("Manage apps & device") || service.clickElementByQuery("Manage apps") || service.clickElementByQuery("ऐप और डिवाइस प्रबंधित करें")
        delay(1200)
        service.waitForIdle(1500, 300)

        // 3. Click "Update all"
        val updateAllClicked = service.clickElementByQuery("Update all") || service.clickElementByQuery("सभी अपडेट करें") || service.clickElementByQuery("See details")

        return SkillResult(true, "Play Store opened to update all applications", data = mapOf("updateAllTriggered" to updateAllClicked))
    }
}

object NotificationsSkill : Skill {
    override val name: String = "notifications"

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val action = args.optString("action", "read").lowercase()
        val appFilter = args.optString("app", "").ifBlank { null }
        val limit = args.optInt("limit", 10)

        if (!SaifNotificationListenerService.isNotificationAccessGranted(env.context)) {
            return SkillResult(false, "Notification access permission is not granted. Please enable it in Settings.", data = mapOf("needPermission" to true))
        }

        return when (action) {
            "read", "summarize" -> {
                val summary = SaifNotificationListenerService.getSummaryText(env.context, appFilter)
                val items = SaifNotificationListenerService.getRecentNotifications(appFilter).take(limit)
                SkillResult(true, summary, data = mapOf("count" to items.size, "notifications" to items))
            }
            "clear" -> {
                // Clear notification tray
                val service = env.service
                service?.openNotificationsShade()
                delay(800)
                service?.clickElementByQuery("Clear all")
                SkillResult(true, "Cleared active notifications")
            }
            else -> {
                val summary = SaifNotificationListenerService.getSummaryText(env.context, appFilter)
                SkillResult(true, summary)
            }
        }
    }
}

object MediaCapcutSkill : Skill {
    override val name: String = "capcut_new_project"

    fun getLatestMediaUri(context: android.content.Context): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idIdx)
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val subAction = args.optString("type", name)
        if (subAction == "gallery_latest") {
            val mediaUri = getLatestMediaUri(env.context)
            return if (mediaUri != null) {
                SkillResult(true, "Found latest media: $mediaUri", data = mapOf("uri" to mediaUri.toString()))
            } else {
                SkillResult(false, "No photos or videos found in gallery")
            }
        }

        // CapCut New Project
        val pkg = AppSkill.resolvePackage(AppSkill.ContextWrapper(env.context), "CapCut")
        if (pkg.isNullOrBlank()) {
            return SkillResult(false, "CapCut app is not installed on this device.")
        }

        val launchIntent = env.context.packageManager.getLaunchIntentForPackage(pkg) ?: return SkillResult(false, "Could not launch CapCut")
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        env.context.startActivity(launchIntent)

        val service = env.service ?: return SkillResult(true, "CapCut launched")
        service.waitForPackage(pkg, 4000)
        service.waitForIdle(2000, 300)
        delay(800)

        // Tap "New project"
        val newProjClicked = service.clickElementByQuery("New project") || service.clickElementByQuery("new project") || service.clickElementByQuery("नया प्रोजेक्ट")
        delay(1200)
        service.waitForIdle(2000, 300)

        // Tap first media thumbnail in gallery tab
        service.performHumanTap(service.resources.displayMetrics.widthPixels * 0.25f, service.resources.displayMetrics.heightPixels * 0.35f)
        delay(800)

        // Tap "Add" button
        service.clickElementByQuery("Add") || service.clickElementByQuery("जोड़ें")
        service.waitForIdle(2000, 300)

        return SkillResult(true, "CapCut new project created with latest media", data = mapOf("created" to newProjClicked))
    }
}

object DescribeScreenSkill : Skill {
    override val name: String = "describe_screen"

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val snapshot = ScreenObserver.capture(withScreenshot = false)
        val text = snapshot.toCompactText(2000)
        val currentApp = env.service?.getCurrentAppLabel(env.context) ?: snapshot.packageName
        val description = "Currently open on $currentApp. Visible elements: ${snapshot.elements.size} items detected."
        return SkillResult(true, description, data = mapOf("snapshot" to text, "app" to currentApp))
    }
}
