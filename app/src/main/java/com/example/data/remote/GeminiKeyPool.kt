package com.example.data.remote

import android.util.Log
import com.example.data.local.ProviderSettingsManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe key pool for Gemini API keys.
 * Filters exclusively for valid Google Gemini keys (starting with AIza or clean tokens),
 * and rotates on 429 / 403 quota exhaustion with backoff tracking.
 */
object GeminiKeyPool {
    private const val TAG = "GeminiKeyPool"

    private val keyIndex = AtomicInteger(0)
    private val keyCoolDownUntil = ConcurrentHashMap<String, Long>()

    fun isGeminiKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isBlank() || trimmed.contains("MY_") || trimmed.contains("your_key", ignoreCase = true)) {
            return false
        }
        // Exclude foreign provider prefixes
        if (trimmed.startsWith("gsk_") || trimmed.startsWith("sk-or-") || trimmed.startsWith("sk-ant-") || trimmed.startsWith("sk-")) {
            return false
        }
        // Gemini keys typically start with AIza or are 39-character alphanumeric tokens
        return trimmed.startsWith("AIza") || (trimmed.length in 35..45 && !trimmed.contains(" "))
    }

    fun getAllValidKeys(): List<String> {
        val keys = mutableListOf<String>()

        // 1. From ProviderSettingsManager (Gemini provider or general active keys)
        try {
            val state = ProviderSettingsManager.loadState()
            for (k in state.apiKeys) {
                val clean = k.trim()
                if (isGeminiKey(clean) && !keys.contains(clean)) {
                    keys.add(clean)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading provider keys: ${e.message}")
        }

        // 2. From BuildConfig
        try {
            val buildConfigKey = com.example.BuildConfig.GEMINI_API_KEY.trim()
            if (isGeminiKey(buildConfigKey) && !keys.contains(buildConfigKey)) {
                keys.add(buildConfigKey)
            }
        } catch (e: Exception) {
            // Ignored if not configured
        }

        // 3. From AdminConfigManager
        try {
            val adminConfig = com.example.data.local.AdminConfigManager.globalConfig.value
            val adminKey = adminConfig.defaultApiKey.trim()
            if (isGeminiKey(adminKey) && !keys.contains(adminKey)) {
                keys.add(adminKey)
            }
        } catch (e: Exception) {
            // Ignored
        }

        return keys
    }

    fun getNextKey(): String? {
        val keys = getAllValidKeys()
        if (keys.isEmpty()) return null

        val now = System.currentTimeMillis()
        val startIndex = keyIndex.getAndIncrement()

        // Scan for a key that is not in cooldown
        for (i in keys.indices) {
            val candidate = keys[(startIndex + i) % keys.size]
            val coolUntil = keyCoolDownUntil[candidate] ?: 0L
            if (now >= coolUntil) {
                return candidate
            }
        }

        // If all are in cooldown, pick the one with earliest cooldown expiration
        return keys.minByOrNull { keyCoolDownUntil[it] ?: 0L } ?: keys.first()
    }

    fun reportError(key: String, statusCode: Int, retryAfterSeconds: Long = 0L) {
        val clean = key.trim()
        if (clean.isBlank()) return

        val backoffMs = when {
            retryAfterSeconds > 0 -> retryAfterSeconds * 1000L
            statusCode == 429 -> 45_000L // 45 seconds for rate limit
            statusCode == 403 -> 300_000L // 5 minutes for quota/auth restriction
            else -> 15_000L
        }

        val until = System.currentTimeMillis() + backoffMs
        keyCoolDownUntil[clean] = until
        Log.w(TAG, "Gemini key marked in cooldown for ${backoffMs / 1000}s (status $statusCode)")
    }
}
