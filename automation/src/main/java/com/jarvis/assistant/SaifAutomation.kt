package com.jarvis.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.background.JarvisLiveError
import com.jarvis.assistant.ui.home.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** API settings of the host app (SAIF AI Settings panel) as seen by SAIF AI AUTOMATION. */
data class SaifApiConfig(
    val provider: String,
    val apiKey: String,
    val model: String
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()

    /** The voice engine speaks the Gemini Live protocol, so it needs a Gemini key. */
    val isGemini: Boolean get() = provider.isBlank() || provider.contains("gemini", ignoreCase = true)
}

/**
 * Link between the host app (SAIF AI) and the SAIF AI AUTOMATION module.
 * The host registers [link] once (Application.onCreate). After that the automation engine always
 * reads the key / provider / model from the host's Settings panel - no separate API screen.
 */
object SaifAutomation {

    private const val PREFS = "saif_automation_link"
    private const val KEY_HOST_LIGHT = "host_light_mode"

    @Volatile private var apiResolver: (() -> SaifApiConfig)? = null
    @Volatile private var settingsOpener: (() -> Unit)? = null

    fun link(apiResolver: () -> SaifApiConfig, openHostSettings: () -> Unit) {
        this.apiResolver = apiResolver
        this.settingsOpener = openHostSettings
    }

    fun currentApi(): SaifApiConfig {
        val cfg = try { apiResolver?.invoke() } catch (_: Throwable) { null }
        return cfg ?: SaifApiConfig(provider = "", apiKey = "", model = "")
    }

    /** True while the host has linked its settings (always the case inside SAIF AI). */
    val isLinked: Boolean get() = apiResolver != null

    var hostLightMode: Boolean
        get() = prefs().getBoolean(KEY_HOST_LIGHT, false)
        set(value) { prefs().edit().putBoolean(KEY_HOST_LIGHT, value).apply() }

    private fun prefs() = AutomationContext.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Called by the red "+" button. Returns false (and tells the user why) when the host Settings
     * panel has no API key yet; otherwise opens SAIF AI AUTOMATION.
     */
    fun launch(context: Context, hostLightMode: Boolean): Boolean {
        AutomationContext.set(context)
        this.hostLightMode = hostLightMode
        // Create the engine first so lifecycle callbacks are registered before the first automation screen opens.
        JarvisApp.instance.ensureSpeechModels()
        val cfg = currentApi()
        if (!cfg.hasKey) {
            Toast.makeText(
                context,
                "SAIF AI Settings (⚙) me pehle API key daalo, phir red + dabao.",
                Toast.LENGTH_LONG
            ).show()
            openHostSettings()
            return false
        }
        val intent = Intent(context, MainActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Closes every automation screen and asks the host to show its Settings panel. */
    fun openHostSettings() {
        try { JarvisApp.instance.finishAutomationScreens() } catch (_: Throwable) {}
        try { settingsOpener?.invoke() } catch (_: Throwable) {}
    }

    // ---- Round 9: the HOST owns the voice, the memory and the language policy -----------------------------------
    // SAIF AI's Application class sets these. The automation module asks the host instead of keeping a second copy
    // of each setting - that is what makes the merged feature behave like one app.

    /** Voice name to speak with, taken from SAIF AI Settings (default: SAIF ADVANCE VOICE). */
    var hostVoiceResolver: (() -> String)? = null

    /**
     * Memory recall for a feature ("live", "chat", "video", ...). The host answers from the app-wide
     * SAIF MEMORY CORE, so the live engine remembers what was learned anywhere in the app.
     */
    var hostMemoryResolver: ((feature: String) -> String)? = null

    /** AUTO LANGUAGE (moved into the host's Settings): answer in the language the user uses. */
    var hostAutoLanguageResolver: (() -> Boolean)? = null

    /** The voice the host wants, or null when the host did not register one. */
    fun hostVoiceName(): String? = try { hostVoiceResolver?.invoke() } catch (_: Throwable) { null }

    /** What the host remembers about [feature]; empty when the host did not register a memory. */
    fun hostMemory(feature: String): String =
        try { hostMemoryResolver?.invoke(feature)?.trim().orEmpty() } catch (_: Throwable) { "" }

    /** Whether the host wants automatic language detection. */
    fun hostAutoLanguage(): Boolean = try { hostAutoLanguageResolver?.invoke() ?: true } catch (_: Throwable) { true }

    /** Host saved new API settings: drop old live errors and reconnect the live session. */
    fun onHostApiChanged() {
        try {
            JarvisConversationService.clearLiveError()
            JarvisConversationService.restartSession()
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------
    // Round 9: the automation engine now runs INSIDE SAIF AI's own live-conversation
    // feature (the purple mic), not behind a separate red "+" button. The host picks a
    // mode and gets the engine's state through the flows below; the host draws the orb.
    // ---------------------------------------------------------------------------

    /** How the live conversation should run. */
    enum class LiveMode(val label: String, val description: String) {
        /** Talk only: microphone in, SAIF AI voice out. */
        NORMAL("Normal voice", "Sirf baat karein — mic se boliye, SAIF AI jawab dega"),
        /** Talk + look at the screen: the engine can see what is on screen and help with it. */
        SCREEN_SHARE("Share screen", "SAIF AI aapki screen dekh kar madad karega")
    }

    /** The mode the running session was started in. */
    val liveMode = MutableStateFlow(LiveMode.NORMAL)

    /** True while the automation engine owns the live conversation. */
    val isLiveRunning: StateFlow<Boolean> = JarvisConversationService.isSessionOn

    /** Orb state for the host's own orb view: idle | listening | thinking | speaking. */
    val orbState: StateFlow<com.jarvis.assistant.data.model.ConversationState> = JarvisConversationService.conversationState

    /** Connection text for the host UI (READY / CONNECTING / LIVE / error). */
    val connectionStatus: StateFlow<String> = JarvisConversationService.connectionStatus

    /** Mic energy 0..1, so the host's orb can react to the voice exactly like the automation orb did. */
    val audioLevel: StateFlow<Float> = JarvisConversationService.audioLevel

    /** Bass/high split of the mic, used by the orb's audio-reactive mode. */
    val audioFrequency: StateFlow<com.jarvis.assistant.audio.AudioFrequencyData> = JarvisConversationService.audioFrequency

    /** What the user just said, and what SAIF AI is answering, for the host's captions. */
    val userTranscript: StateFlow<String> = JarvisConversationService.liveUserTranscript
    val assistantTranscript: StateFlow<String> = JarvisConversationService.liveAssistantResponse

    /** True while the screen is being shared with the engine. */
    val isScreenSharing: StateFlow<Boolean> = JarvisConversationService.isScreenSharing

    /** A live error the host should show (missing key, WebSocket failure...). */
    val liveError: StateFlow<JarvisLiveError?> = JarvisConversationService.liveError

    /**
     * Starts the automation live engine WITHOUT opening any automation screen: the host draws its own orb.
     * Returns false (with a toast) when the host Settings panel has no API key yet.
     */
    fun startLive(context: Context, mode: LiveMode, hostLightMode: Boolean): Boolean {
        AutomationContext.set(context)
        this.hostLightMode = hostLightMode
        liveMode.value = mode
        JarvisApp.instance.ensureSpeechModels()
        val cfg = currentApi()
        if (!cfg.hasKey) {
            Toast.makeText(context, "SAIF AI Settings (⚙) me pehle API key daalo, phir live baatcheet shuru karo.", Toast.LENGTH_LONG).show()
            openHostSettings()
            return false
        }
        val intent = Intent(context, JarvisConversationService::class.java)
            .setAction(JarvisConversationService.ACTION_START_CONVERSATION)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, "Live session start nahi ho payi: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /** Stops the live engine and any screen capture. */
    fun stopLive(context: Context) {
        AutomationContext.set(context)
        try { stopScreenShare() } catch (_: Throwable) {}
        try {
            val intent = Intent(context, JarvisConversationService::class.java)
                .setAction(JarvisConversationService.ACTION_STOP_ALL)
            context.startService(intent)
        } catch (_: Throwable) {}
    }

    /** Mutes / unmutes the engine's microphone. */
    fun setMicMuted(muted: Boolean) {
        try { JarvisConversationService.setMicMuted(muted) } catch (_: Throwable) {}
    }

    /** Interrupts whatever the engine is saying (barge-in). */
    fun interrupt() {
        try { JarvisConversationService.interruptPlayback() } catch (_: Throwable) {}
    }

    /** Sends typed text into the running live session. */
    fun sendText(text: String) {
        try { JarvisConversationService.sendTextMessage(text) } catch (_: Throwable) {}
    }

    /**
     * Begins screen sharing with a MediaProjection the HOST already obtained (the host asks for the
     * permission, because the projection dialog must come from a visible activity).
     */
    fun startScreenShare(projection: android.media.projection.MediaProjection, metrics: android.util.DisplayMetrics): Boolean =
        try { JarvisConversationService.startScreenCapture(projection, metrics) } catch (_: Throwable) { false }

    /** Why the last screen-share attempt failed ("" when it worked or is still starting). */
    val lastScreenShareError: String
        get() = JarvisConversationService.lastScreenShareError

    /** True once frames are really flowing to the engine. */
    val isScreenSharingNow: StateFlow<Boolean>
        get() = com.jarvis.assistant.vision.ScreenCaptureManager.getInstance().isSharing

    fun stopScreenShare() {
        try { JarvisConversationService.stopScreenCapture() } catch (_: Throwable) {}
    }

    /** The engine can only see the screen after the host hands it a MediaProjection. */
    fun wantsScreenShare(): Boolean = liveMode.value == LiveMode.SCREEN_SHARE

    /** Clears a shown live error. */
    fun clearLiveError() {
        try { JarvisConversationService.clearLiveError() } catch (_: Throwable) {}
    }

    /** True when the automation engine's own screens are open (the host hides its orb then). */
    fun isServiceRunning(): Boolean = try { JarvisConversationService.isServiceRunning() } catch (_: Throwable) { false }
}
