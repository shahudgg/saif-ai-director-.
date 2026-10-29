package com.example.agent

import android.content.Context
import android.util.Log
import org.json.JSONObject

object PlaybookStore {
    private const val TAG = "SAIF_AGENT"
    private val playbooks = mutableMapOf<String, String>()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        try {
            val jsonString = context.assets.open("agent_playbooks.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg)
                val hint = obj?.optString("hints", "") ?: ""
                if (hint.isNotBlank()) {
                    playbooks[pkg.lowercase()] = hint
                }
            }
            isLoaded = true
            Log.d(TAG, "PlaybookStore loaded ${playbooks.size} app playbooks")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load agent playbooks: ${e.message}")
        }
    }

    fun getHint(packageName: String): String? {
        val clean = packageName.lowercase().trim()
        if (clean.isBlank()) return null
        return playbooks[clean] ?: playbooks.entries.firstOrNull { clean.contains(it.key) || it.key.contains(clean) }?.value
    }
}
