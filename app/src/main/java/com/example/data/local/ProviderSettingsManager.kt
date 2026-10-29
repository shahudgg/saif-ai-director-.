package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.llm.KeyStoreCrypto
import com.example.llm.ModelLimitsStore
import org.json.JSONArray
import org.json.JSONObject

data class ProviderState(
    var providerName: String = "Gemini",
    var apiKeys: List<String> = emptyList(),
    var activeModel: String = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL,
    var buildModel: String = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL,
    var currentKeyIndex: Int = 0,
    var allowFallbackToOtherProviders: Boolean = true
)

object ProviderSettingsManager {
    private const val PREFS_NAME = "ProviderSettingsPrefs"
    private const val KEY_STATE = "provider_state_v8"
    private const val LEGACY_KEY_STATE = "provider_state_v7"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ModelLimitsStore.init(context)
    }

    private fun sanitizeModelName(provider: String, model: String): String {
        val trimmed = model.trim()
        val isGemini = provider.equals("Gemini", ignoreCase = true)
        if (isGemini) {
            // Only retired families are rewritten. Newer models (2.5, 3.x...) are the user's choice and must be kept as typed.
            if (trimmed.isBlank() || com.example.llm.GeminiModelCatalog.isRetired(trimmed)) {
                return com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
            }
            return trimmed
        }
        if (provider.contains("atria", ignoreCase = true)) {
            if (trimmed.isBlank() || trimmed.equals("atria-1", true) || trimmed.equals("atria-dawn", true) || trimmed.contains("dawn", true)) {
                return if (trimmed.contains("FP8", true)) "Atria-Dawn-Preview-FP8" else "Atria-Dawn-Preview"
            }
        }
        if (provider.contains("inception", ignoreCase = true)) {
            if (trimmed.isBlank() || trimmed.contains("merlin", true)) {
                return "mercury-2.5"
            }
        }
        if (provider.contains("Ashna", ignoreCase = true) || provider.contains("Asana", ignoreCase = true)) {
            if (trimmed.isBlank() || trimmed.contains("gemini", true)) {
                return "ashna-x1"
            }
            return trimmed
        }
        if (trimmed.isBlank() || (trimmed.contains("gemini-", ignoreCase = true) && !provider.contains("OpenRouter", ignoreCase = true) && !provider.contains("Ashna", ignoreCase = true))) {
            return com.example.llm.ProviderProfile.getById(provider).defaultFallbackModels.firstOrNull() ?: "default"
        }
        return trimmed
    }

    private fun sanitizeBuildModelName(provider: String, model: String): String {
        val trimmed = model.trim()
        val isGemini = provider.equals("Gemini", ignoreCase = true)
        if (isGemini) {
            // Only retired families are rewritten. Newer models (2.5, 3.x...) are the user's choice and must be kept as typed.
            if (trimmed.isBlank() || com.example.llm.GeminiModelCatalog.isRetired(trimmed)) {
                return com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
            }
            return trimmed
        }
        if (provider.contains("atria", ignoreCase = true)) {
            if (trimmed.isBlank() || trimmed.equals("atria-1", true) || trimmed.equals("atria-dawn", true) || trimmed.contains("dawn", true)) {
                return if (trimmed.contains("FP8", true)) "Atria-Dawn-Preview-FP8" else "Atria-Dawn-Preview"
            }
        }
        if (provider.contains("inception", ignoreCase = true)) {
            if (trimmed.isBlank() || trimmed.contains("merlin", true)) {
                return "mercury-2.5"
            }
        }
        if (provider.contains("Ashna", ignoreCase = true) || provider.contains("Asana", ignoreCase = true)) {
            if (trimmed.isBlank() || trimmed.contains("gemini", true)) {
                return "ashna-x1"
            }
            return trimmed
        }
        if (trimmed.isBlank() || (trimmed.contains("gemini-", ignoreCase = true) && !provider.contains("OpenRouter", ignoreCase = true) && !provider.contains("Ashna", ignoreCase = true))) {
            return com.example.llm.ProviderProfile.getById(provider).defaultFallbackModels.firstOrNull() ?: "default"
        }
        return trimmed
    }

    fun hasCustomUserKey(): Boolean {
        val state = loadState()
        return state.apiKeys.any { it.isNotBlank() && !it.contains("MY_") }
    }

    fun loadState(): ProviderState {
        val jsonStr = prefs?.getString(KEY_STATE, null)
            ?: prefs?.getString(LEGACY_KEY_STATE, null)

        if (jsonStr == null) {
            return ProviderState(
                providerName = "Gemini",
                activeModel = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL,
                buildModel = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL,
                apiKeys = emptyList(),
                allowFallbackToOtherProviders = true
            )
        }
        return try {
            val json = JSONObject(jsonStr)
            val rawKeys = jsonArrayToList(json.optJSONArray("apiKeys")).filter { it.isNotBlank() }

            // Decrypt keys that were encrypted with KeyStore; plain keys are read as-is (seamless migration)
            val parsedKeys = rawKeys.map { rawKey ->
                if (rawKey.startsWith("ENC:")) {
                    KeyStoreCrypto.decrypt(rawKey).ifBlank { rawKey.removePrefix("ENC:") }.trim()
                } else {
                    rawKey.trim()
                }
            }.filter { it.isNotBlank() && !it.contains("MY_") }

            val rawModel = json.optString("activeModel", com.example.llm.GeminiModelCatalog.DEFAULT_MODEL)
            val rawBuildModel = json.optString("buildModel", com.example.llm.GeminiModelCatalog.DEFAULT_MODEL)
            var savedProvider = json.optString("providerName", "Gemini")
            val allowFallback = json.optBoolean("allowFallbackToOtherProviders", true)

            // Clean template placeholders
            val cleanedKeys = parsedKeys.filter { k ->
                val trimmed = k.trim()
                !trimmed.contains("MY_")
            }

            if (savedProvider.equals("OpenCode Zen", ignoreCase = true) || savedProvider.equals("OpenCode", ignoreCase = true)) {
                savedProvider = "OpenCode.ai"
            }
            if (savedProvider.equals("InceptionLabs", ignoreCase = true) || savedProvider.equals("Inception", ignoreCase = true)) {
                savedProvider = "inceptionlabs.ai"
            }
            if (savedProvider.equals("Atria ASI", ignoreCase = true) || savedProvider.equals("Atria", ignoreCase = true)) {
                savedProvider = "api.atria-asi.ai"
            }
            if (savedProvider.equals("Asana AI", ignoreCase = true) || savedProvider.equals("Asana", ignoreCase = true)) {
                savedProvider = "Ashna AI"
            }

            ProviderState(
                providerName = savedProvider,
                apiKeys = cleanedKeys,
                activeModel = sanitizeModelName(savedProvider, rawModel),
                buildModel = sanitizeBuildModelName(savedProvider, rawBuildModel),
                currentKeyIndex = json.optInt("currentKeyIndex", 0),
                allowFallbackToOtherProviders = allowFallback
            )
        } catch (e: Exception) {
            ProviderState(
                providerName = "Gemini",
                activeModel = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL,
                buildModel = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL,
                apiKeys = emptyList(),
                allowFallbackToOtherProviders = true
            )
        }
    }

    fun saveState(state: ProviderState) {
        val json = JSONObject().apply {
            put("providerName", state.providerName)
            val cleanKeys = state.apiKeys
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.contains("MY_") }
            put("apiKeys", listToJsonArray(cleanKeys))
            put("activeModel", state.activeModel)
            put("buildModel", state.buildModel)
            put("currentKeyIndex", state.currentKeyIndex)
            put("allowFallbackToOtherProviders", state.allowFallbackToOtherProviders)
        }
        prefs?.edit()?.putString(KEY_STATE, json.toString())?.apply()
        // Keep SAIF AI AUTOMATION in sync: running voice sessions reconnect with the new key / model.
        runCatching { com.jarvis.assistant.SaifAutomation.onHostApiChanged() }
    }

    fun getActiveProvider(): String = loadState().providerName
    fun getActiveModel(): String = loadState().activeModel
    fun getActiveApiKey(): String = loadState().apiKeys.firstOrNull() ?: ""

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        val list = mutableListOf<String>()
        if (array != null) {
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        }
        return list
    }

    private fun listToJsonArray(list: List<String>): JSONArray {
        val array = JSONArray()
        for (item in list) {
            array.put(item)
        }
        return array
    }
}
