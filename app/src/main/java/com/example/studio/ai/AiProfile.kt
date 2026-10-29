package com.example.studio.ai

import android.content.Context
import com.example.data.local.ProviderSettingsManager

/**
 * Universal BYOK (Bring Your Own Key) Profile for SAIF AI Video Studio.
 * Zero hardcoded models, zero developer-key fallbacks.
 * Uses the exact provider, key, and model set by the user in Settings.
 */
data class AiProfile(
    val provider: String,
    val apiKey: String,
    val activeModel: String,
    val customBaseUrl: String? = null
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && activeModel.isNotBlank()

    companion object {
        fun fromSettings(context: Context): AiProfile {
            ProviderSettingsManager.init(context)
            val state = ProviderSettingsManager.loadState()
            val provider = state.providerName.ifBlank { "Groq" }
            val key = state.apiKeys.firstOrNull { it.isNotBlank() } ?: ""
            val model = state.activeModel.ifBlank { "default" }

            return AiProfile(
                provider = provider,
                apiKey = key,
                activeModel = model
            )
        }
    }
}
