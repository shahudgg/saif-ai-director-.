package com.example.studio.director

import android.content.Context

/** Per-device production preferences that every generated video follows: caption look, accent colour and auto sound effects. */
object BrandKit {
    private const val PREFS = "saif_brand_kit"
    val ACCENTS = listOf("Auto" to "", "Violet" to "#7C4DFF", "Cyan" to "#00E5FF", "Pink" to "#FF4DA6", "Amber" to "#FF8A3D", "Green" to "#00F5A0")

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun captionStyle(c: Context): String = p(c).getString("caption", com.example.studio.render.CaptionStyles.HORMOZI) ?: com.example.studio.render.CaptionStyles.HORMOZI
    fun setCaptionStyle(c: Context, v: String) { p(c).edit().putString("caption", v).apply() }

    /** Hex like "#7C4DFF", or null for the automatic per-scene palettes. */
    fun accent(c: Context): String? = p(c).getString("accent", "")?.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }
    fun setAccent(c: Context, hex: String) { p(c).edit().putString("accent", hex).apply() }

    fun autoSfx(c: Context): Boolean = p(c).getBoolean("autoSfx", true)
    fun setAutoSfx(c: Context, on: Boolean) { p(c).edit().putBoolean("autoSfx", on).apply() }

    /** Voice used for every generated narration. Defaults to the app's own Kabir voice. */
    fun narrationVoice(c: Context): String = p(c).getString("voice", "Kabir")?.takeIf { it == "Kabir" || it == "Priya" } ?: "Kabir"
    fun setNarrationVoice(c: Context, v: String) { p(c).edit().putString("voice", v).apply() }
}
