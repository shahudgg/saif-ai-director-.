package com.jarvis.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.ui.home.MainActivity

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

    /** Host saved new API settings: drop old live errors and reconnect the live session. */
    fun onHostApiChanged() {
        try {
            JarvisConversationService.clearLiveError()
            JarvisConversationService.restartSession()
        } catch (_: Throwable) {
        }
    }
}
