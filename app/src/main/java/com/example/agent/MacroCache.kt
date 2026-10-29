package com.example.agent

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object MacroCache {
    private const val TAG = "SAIF_AGENT"
    private const val PREFS_NAME = "agent_macro_cache"
    private const val MAX_ENTRIES = 50

    private var prefs: SharedPreferences? = null
    private val memoryCache = mutableMapOf<String, String>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val all = prefs?.all ?: emptyMap()
            for ((k, v) in all) {
                if (v is String) memoryCache[k] = v
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing MacroCache: ${e.message}")
        }
    }

    private fun normalizeKey(app: String, goal: String): String {
        val cleanGoal = goal.lowercase().replace(Regex("""[^a-z0-9 ]"""), "").trim()
        val tokens = cleanGoal.split(" ").filter { it.length > 2 }.take(4).joinToString("_")
        return "${app.lowercase()}:$tokens"
    }

    fun getHint(app: String, goal: String): String? {
        val key = normalizeKey(app, goal)
        return memoryCache[key]
    }

    fun put(app: String, goal: String, actions: List<String>) {
        if (actions.isEmpty()) return
        val key = normalizeKey(app, goal)
        val value = actions.take(6).joinToString(" -> ")
        memoryCache[key] = value
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun invalidate(app: String, goal: String) {
        val key = normalizeKey(app, goal)
        memoryCache.remove(key)
        prefs?.edit()?.remove(key)?.apply()
    }
}
