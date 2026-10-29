package com.example.utils

import android.content.Context
import com.example.ui.components.VOICE_PROFILES
import com.example.ui.components.VoiceProfile

/**
 * ONE voice for the whole app.
 *
 * Before this, three places picked a voice on their own: the chat read `VoiceSettings/selected_voice_id`,
 * the video director read `saif_brand_kit/voice` (and only accepted "Kabir" or "Priya"), and the live
 * conversation asked Gemini Live for its own voice name. So changing the voice in Settings did not change the
 * video narration, and the automation engine's voice was not available at all.
 *
 * [SaifVoice] is now the single source of truth. Every speaking feature (chat replies, live baatcheet, video
 * narration, previews, the automation engine) asks here first, so one choice really applies everywhere — and
 * **SAIF ADVANCE VOICE** is the default.
 */
object SaifVoice {

    /** Id of the SAIF AI Automation voice, now the app's default voice everywhere. */
    const val SAIF_ADVANCE = "SaifAdvance"

    /** Fallback when nothing was ever chosen. */
    const val DEFAULT = SAIF_ADVANCE

    private const val VOICE_PREFS = "VoiceSettings"
    private const val BRAND_PREFS = "saif_brand_kit"
    private const val KEY_SELECTED = "selected_voice_id"

    /** The voice name the live engine asks a Gemini-Live provider for, per app voice. */
    private val LIVE_VOICE_NAMES = mapOf(
        SAIF_ADVANCE to "Puck",      // the SAIF AI Automation voice
        "Kabir" to "Charon",         // deep, authoritative
        "Priya" to "Aoede"           // bright, warm
    )

    /** Id of the voice the user picked in Settings. Never returns an id that no longer exists. */
    fun selectedId(context: Context): String {
        val saved = context.applicationContext
            .getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, null)
        val known = VOICE_PROFILES.map { it.id }
        return when {
            saved != null && saved in known -> saved
            // An old install that never chose a voice gets the new default.
            saved == null -> DEFAULT
            else -> DEFAULT
        }
    }

    /** The full profile of the selected voice. */
    fun selected(context: Context): VoiceProfile =
        VOICE_PROFILES.firstOrNull { it.id == selectedId(context) } ?: VOICE_PROFILES.first()

    fun select(context: Context, id: String) {
        val c = context.applicationContext
        c.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SELECTED, id).apply()
        // Keep the video director's own copy in sync so an older code path cannot pick a different voice.
        c.getSharedPreferences(BRAND_PREFS, Context.MODE_PRIVATE).edit().putString("voice", id).apply()
    }

    /**
     * The voice the video director should narrate with. Reads the SAME setting as the chat, and only falls back
     * to the brand-kit copy when Settings has nothing (an install upgraded from an older build).
     */
    fun narrationVoiceId(context: Context): String {
        val c = context.applicationContext
        val fromSettings = c.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, null)
        val known = VOICE_PROFILES.map { it.id }
        if (fromSettings != null && fromSettings in known) return fromSettings
        val fromBrand = c.getSharedPreferences(BRAND_PREFS, Context.MODE_PRIVATE).getString("voice", null)
        return if (fromBrand != null && fromBrand in known) fromBrand else DEFAULT
    }

    fun narrationProfile(context: Context): VoiceProfile =
        VOICE_PROFILES.firstOrNull { it.id == narrationVoiceId(context) } ?: VOICE_PROFILES.first()

    /** Voice name to request from a Gemini-Live style provider for the selected app voice. */
    fun liveVoiceName(context: Context): String = LIVE_VOICE_NAMES[selectedId(context)] ?: "Puck"

    /** Every voice name the live engine may ask for, so a provider that does not know one can try the next. */
    fun liveVoiceFallbacks(context: Context): List<String> {
        val first = liveVoiceName(context)
        return (listOf(first) + LIVE_VOICE_NAMES.values).distinct()
    }

    /** Human name shown in reports and toasts. */
    fun displayName(context: Context): String = selected(context).name
}
