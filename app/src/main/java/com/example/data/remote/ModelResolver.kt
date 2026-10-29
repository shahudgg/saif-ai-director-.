package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.llm.LlmGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Dynamically discovers and caches supported Gemini models for this API key.
 * Caches model catalog for 12 hours and falls back to verified active Gemini 3.x models.
 */
object ModelResolver {
    private const val TAG = "ModelResolver"
    private const val PREFS_NAME = "model_resolver_prefs"
    private const val KEY_CACHED_MODELS = "cached_gemini_models"
    private const val KEY_CACHE_TIME = "cached_models_timestamp"
    private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val deadModels = ConcurrentHashMap.newKeySet<String>()
    private val availableModels = ConcurrentHashMap.newKeySet<String>()
    private var lastFetchTime = 0L

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedJson = prefs.getString(KEY_CACHED_MODELS, null)
            val timestamp = prefs.getLong(KEY_CACHE_TIME, 0L)
            val now = System.currentTimeMillis()

            if (!cachedJson.isNullOrBlank() && now - timestamp < CACHE_TTL_MS) {
                val array = org.json.JSONArray(cachedJson)
                for (i in 0 until array.length()) {
                    val m = array.getString(i)
                    if (m.isNotBlank()) availableModels.add(m)
                }
                lastFetchTime = timestamp
                Log.d(TAG, "Loaded ${availableModels.size} cached models from storage")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load model cache: ${e.message}")
        }
    }

    suspend fun refreshCatalog(apiKey: String, context: Context? = null) {
        if (apiKey.isBlank()) return
        val now = System.currentTimeMillis()
        if (availableModels.isNotEmpty() && now - lastFetchTime < CACHE_TTL_MS) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val bodyString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    val modelsArray = json.optJSONArray("models")
                    if (modelsArray != null) {
                        val names = mutableListOf<String>()
                        for (i in 0 until modelsArray.length()) {
                            val obj = modelsArray.optJSONObject(i) ?: continue
                            val name = obj.optString("name", "").removePrefix("models/")
                            if (name.isNotBlank()) {
                                names.add(name)
                                availableModels.add(name)
                            }
                        }
                        lastFetchTime = now
                        context?.let { ctx ->
                            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString(KEY_CACHED_MODELS, org.json.JSONArray(names).toString())
                                .putLong(KEY_CACHE_TIME, now)
                                .apply()
                        }
                        Log.d(TAG, "Discovered ${availableModels.size} remote Gemini models")
                    }
                } else {
                    Log.w(TAG, "Models list request returned HTTP ${response.code}: $bodyString")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error refreshing model catalog: ${e.message}")
            }
        }
    }

    fun markDead(model: String) {
        val clean = model.trim().removePrefix("models/")
        if (clean.isNotBlank()) {
            deadModels.add(clean)
            availableModels.remove(clean)
            LlmGateway.markModelDead("Gemini", clean)
            Log.w(TAG, "Marked model '$clean' as dead for current session")
        }
    }

    fun markDead(provider: String, model: String) {
        val clean = model.trim().removePrefix("models/")
        if (clean.isNotBlank()) {
            deadModels.add(clean)
            availableModels.remove(clean)
            LlmGateway.markModelDead(provider, clean)
            Log.w(TAG, "Marked model '$provider/$clean' as dead for current session")
        }
    }

    fun isDead(model: String): Boolean {
        val clean = model.trim().removePrefix("models/")
        return deadModels.contains(clean) || LlmGateway.isModelDead("Gemini", clean)
    }

    fun resolve(role: ModelRole): String {
        val candidates = when (role) {
            ModelRole.AGENT -> listOf(
                GeminiModels.AGENT,
                "gemini-3.8-flash",
                "gemini-3.5-flash",
                "gemini-2.5-flash"
            )
            ModelRole.FAST, ModelRole.ROUTER, ModelRole.VERIFIER -> listOf(
                GeminiModels.FAST,
                "gemini-3.5-flash-lite",
                "gemini-2.5-flash-lite",
                GeminiModels.AGENT
            )
            ModelRole.CHAT -> GeminiModels.CHAT_FALLBACKS
            ModelRole.LIVE -> listOf(
                GeminiModels.LIVE,
                "gemini-3.5-flash"
            )
            ModelRole.TRANSCRIBE -> GeminiModels.TRANSCRIPTION_FALLBACKS
        }

        // Return first candidate that is not dead, prioritizing discovered available models
        for (cand in candidates) {
            if (!deadModels.contains(cand)) {
                if (availableModels.isEmpty() || availableModels.contains(cand)) {
                    return cand
                }
            }
        }

        // Fallback: pick first candidate not in dead list
        for (cand in candidates) {
            if (!deadModels.contains(cand)) return cand
        }

        // Ultimate default
        return when (role) {
            ModelRole.AGENT -> GeminiModels.AGENT
            ModelRole.FAST, ModelRole.ROUTER, ModelRole.VERIFIER -> GeminiModels.FAST
            ModelRole.LIVE -> GeminiModels.LIVE
            ModelRole.TRANSCRIBE -> GeminiModels.TRANSCRIBE
            else -> GeminiModels.FAST
        }
    }
}
