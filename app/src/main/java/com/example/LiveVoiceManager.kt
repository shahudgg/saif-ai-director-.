package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Context
import android.content.Intent

object LiveVoiceManager {
    val isAppInForeground = MutableStateFlow(false)
    val isLiveModeActive = MutableStateFlow(false)
    var currentSessionId: String? = null
    val engineFlow = MutableStateFlow<LiveVoiceSessionManager?>(null)
    var engine: LiveVoiceSessionManager?
        get() = engineFlow.value
        set(value) { engineFlow.value = value }

    fun toggleLiveVoice(context: Context) {
        if (isLiveModeActive.value) {
            stopLiveVoice(context)
        } else {
            startLiveVoice(context)
        }
    }

    private fun startLiveVoice(context: Context) {
        val intent = Intent(context, LiveVoiceService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        isLiveModeActive.value = true
    }

    fun stopLiveVoice(context: Context) {
        try {
            com.example.agent.PhoneAgentRuntime.cancelAll()
        } catch (ignored: Exception) {}
        val intent = Intent(context, LiveVoiceService::class.java)
        context.stopService(intent)
        engine?.stop()
        engine = null
        isLiveModeActive.value = false
    }
}
