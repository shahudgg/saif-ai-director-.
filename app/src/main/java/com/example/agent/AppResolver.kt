package com.example.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.util.Locale

/**
 * High-performance installed application resolver with phonetic, Hinglish, and alias matching.
 */
object AppResolver {
    private const val TAG = "AppResolver"

    data class AppEntry(
        val label: String,
        val packageName: String,
        val activityName: String
    )

    private val KNOWN_ALIASES = mapOf(
        "youtube" to listOf("yutub", "u tube", "you tube", "yt", "com.google.android.youtube"),
        "whatsapp" to listOf("watsapp", "watsap", "whatapp", "whats up", "com.whatsapp"),
        "instagram" to listOf("insta", "ig", "com.instagram.android"),
        "capcut" to listOf("cap cut", "kepkat", "video editor", "com.lemon.lvoverseas", "com.lemon.kdp"),
        "play store" to listOf("playstore", "store", "google play", "market", "com.android.vending"),
        "chrome" to listOf("browser", "google chrome", "internet", "com.android.chrome"),
        "spotify" to listOf("music", "songs", "gaana", "spotifi", "com.spotify.music"),
        "camera" to listOf("cam", "photo", "video camera", "tasveer"),
        "settings" to listOf("setting", "phone settings"),
        "gallery" to listOf("photos", "google photos", "album", "com.google.android.apps.photos"),
        "phone" to listOf("dialer", "call", "telephone", "contacts"),
        "messages" to listOf("sms", "text", "inbox", "com.google.android.apps.messaging")
    )

    private var cachedApps: List<AppEntry>? = null
    private var lastCacheTime = 0L

    fun getInstalledApps(context: Context, forceRefresh: Boolean = false): List<AppEntry> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedApps != null && (now - lastCacheTime < 60000)) {
            return cachedApps!!
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val list = mutableListOf<AppEntry>()
        try {
            val activities = pm.queryIntentActivities(intent, 0)
            for (resolveInfo in activities) {
                val pkg = resolveInfo.activityInfo.packageName
                val act = resolveInfo.activityInfo.name
                val label = resolveInfo.loadLabel(pm).toString()
                list.add(AppEntry(label = label, packageName = pkg, activityName = act))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying launcher activities: ${e.message}")
        }

        cachedApps = list
        lastCacheTime = now
        return list
    }

    /**
     * Resolves an app query string to the best matching installed package name.
     */
    fun resolvePackage(context: Context, appQuery: String): String? {
        val clean = appQuery.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[.,!?;:"']"""), "")
            .trim()
        if (clean.isBlank()) return null

        val apps = getInstalledApps(context)

        // 1. Direct package name match
        val directPkg = apps.firstOrNull { it.packageName.equals(clean, ignoreCase = true) }
        if (directPkg != null) return directPkg.packageName

        // 2. Direct label exact match
        val exactLabel = apps.firstOrNull { it.label.equals(clean, ignoreCase = true) }
        if (exactLabel != null) return exactLabel.packageName

        // 3. Known alias dictionary lookup
        for ((targetApp, aliases) in KNOWN_ALIASES) {
            val matchesTarget = clean == targetApp || clean.contains(targetApp) || targetApp.contains(clean)
            val matchesAlias = aliases.any { alias ->
                clean == alias || clean.contains(alias) || alias.contains(clean)
            }

            if (matchesTarget || matchesAlias) {
                // Find installed app matching package or label
                for (aliasCandidate in listOf(targetApp) + aliases) {
                    val found = apps.firstOrNull {
                        it.packageName.contains(aliasCandidate, ignoreCase = true) ||
                        it.label.contains(aliasCandidate, ignoreCase = true)
                    }
                    if (found != null) return found.packageName
                }
            }
        }

        // 4. Substring and fuzzy label match
        val substringMatch = apps.firstOrNull {
            it.label.lowercase(Locale.ROOT).contains(clean) || clean.contains(it.label.lowercase(Locale.ROOT))
        }
        if (substringMatch != null) return substringMatch.packageName

        return null
    }
}
