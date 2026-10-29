package com.example

import android.app.Application
import com.example.data.local.ProviderSettingsManager
import com.jarvis.assistant.SaifApiConfig
import com.jarvis.assistant.SaifAutomation

class SaifApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProviderSettingsManager.init(this)

        // Round 9: the memory recall of SAIF AI AUTOMATION is now an app-wide backend. Every feature (chat, live
        // baatcheet, video director, builder, agent) learns into it and recalls from it.
        com.example.util.SaifMemoryCore.init(this)
        com.example.utils.TTSManager.init(this)

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

        // The live engine speaks with the voice the user picked in SAIF AI Settings (SAIF ADVANCE VOICE by default),
        // not with a voice chosen inside the automation module.
        SaifAutomation.hostVoiceResolver = { com.example.utils.SaifVoice.liveVoiceName(this) }
        SaifAutomation.hostMemoryResolver = { feature -> com.example.util.SaifMemoryCore.promptBlock(feature) }
        SaifAutomation.hostAutoLanguageResolver = { com.example.util.SaifMemoryCore.autoLanguage.value }

        // Live baatcheet must work on EVERY API provider, not only on Gemini Live.
        //   • Gemini key        -> the automation module's own full-duplex Gemini Live WebSocket.
        //   • any other provider -> SAIF AI's turn-based engine (this registration): SttRouter picks
        //     Groq Whisper / OpenAI Whisper / Gemini for speech-to-text with fallbacks across every
        //     saved key, AIApiUtility.executeWithFallback produces the reply on whichever provider is
        //     configured, TTSManager speaks it on-device, and SaifLiveScreen hands the newest screen
        //     frame to the model as an image instead of streaming it over a socket.
        SaifAutomation.hostLiveEngine = SaifHostLiveEngine
    }
}
