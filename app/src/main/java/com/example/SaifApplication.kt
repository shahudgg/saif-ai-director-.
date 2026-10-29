package com.example

import android.app.Application
import com.example.data.local.ProviderSettingsManager
import com.jarvis.assistant.SaifApiConfig
import com.jarvis.assistant.SaifAutomation

class SaifApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProviderSettingsManager.init(this)

        // Link SAIF AI AUTOMATION to the API system of the Settings panel: whatever the user saved
        // there (provider, key, model) is what the automation engine uses.
        SaifAutomation.link(
            apiResolver = {
                val state = ProviderSettingsManager.loadState()
                val key = state.apiKeys.getOrNull(state.currentKeyIndex)?.takeIf { it.isNotBlank() }
                    ?: state.apiKeys.firstOrNull { it.isNotBlank() }
                    ?: ""
                SaifApiConfig(
                    provider = state.providerName,
                    apiKey = key,
                    model = state.activeModel
                )
            },
            openHostSettings = { HostNavigation.openSettingsRequest.value = true }
        )
    }
}
