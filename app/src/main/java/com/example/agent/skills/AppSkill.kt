package com.example.agent.skills

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.util.DeviceActionManager
import org.json.JSONObject

object AppSkill : Skill {
    override val name: String = "open_app"
    private const val TAG = "SAIF_AGENT"

    private val COMMON_APP_ALIASES = mapOf(
        "whatsapp" to listOf("watsapp", "watsap", "whatapp", "com.whatsapp"),
        "youtube" to listOf("yutub", "yt", "you tube", "com.google.android.youtube"),
        "instagram" to listOf("insta", "ig", "com.instagram.android"),
        "chrome" to listOf("browser", "google chrome", "com.android.chrome"),
        "play store" to listOf("playstore", "store", "google play", "com.android.vending"),
        "camera" to listOf("cam", "photo", "video camera"),
        "settings" to listOf("setting", "phone settings"),
        "capcut" to listOf("cap cut", "video editor"),
        "gpay" to listOf("google pay", "pay", "googlepay", "com.google.android.apps.nbu.paisa.user"),
        "phonepe" to listOf("phone pe", "com.phonepe.app"),
        "paytm" to listOf("pay tm", "net.one97.paytm"),
        "spotify" to listOf("music", "songs", "com.spotify.music"),
        "gallery" to listOf("photos", "google photos", "album", "com.google.android.apps.photos")
    )

    fun resolvePackage(context: ContextWrapper, appNameOrAlias: String): String? {
        val clean = appNameOrAlias.trim().lowercase()
        val pm = context.context.packageManager

        // 1. Direct package check
        try {
            pm.getPackageInfo(clean, 0)
            return clean
        } catch (ignored: Exception) {}

        // 2. Check alias map
        for ((target, aliases) in COMMON_APP_ALIASES) {
            if (clean == target || aliases.any { clean.contains(it) || it.contains(clean) }) {
                // Find installed package matching target or its aliases
                for (cand in listOf(target) + aliases) {
                    try {
                        pm.getPackageInfo(cand, 0)
                        return cand
                    } catch (ignored: Exception) {}
                }
            }
        }

        // 3. Scan installed launcher applications
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
        var bestMatch: String? = null
        var bestScore = 0

        for (info in apps) {
            val label = info.loadLabel(pm).toString().lowercase()
            val pkg = info.activityInfo.packageName.lowercase()

            if (label == clean || pkg == clean) {
                return info.activityInfo.packageName
            }
            if (label.contains(clean) || clean.contains(label)) {
                return info.activityInfo.packageName
            }
        }

        return bestMatch
    }

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val appName = args.optString("app_name", "").ifBlank {
            args.optString("app_name_or_package", "").ifBlank {
                args.optString("name", "").ifBlank {
                    args.optString("package", "")
                }
            }
        }
        if (appName.isBlank()) {
            return SkillResult(false, "App name not specified")
        }

        val launchResult = com.example.agent.AppLauncher.launchVerified(env.context, appName)
        if (launchResult.success) {
            return SkillResult(true, launchResult.message, data = mapOf("package" to launchResult.targetPackage))
        }

        val pkg = com.example.agent.AppResolver.resolvePackage(env.context, appName)
        if (pkg == null) {
            Log.w(TAG, "App '$appName' not installed on device")
            return SkillResult(
                false,
                "App '$appName' is not installed on this device. Would you like to install it from Play Store?",
                data = mapOf("offerPlayStore" to true, "appName" to appName)
            )
        }

        return SkillResult(false, launchResult.message)
    }

    class ContextWrapper(val context: android.content.Context)
}
