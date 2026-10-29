package com.example.audio

import android.content.Context
import com.example.agent.skills.ContactAliasStore
import java.util.Locale

/**
 * Phonetic, transliteration, and alias correction engine for transcribed voice commands.
 * Fixes typical Whisper / Speech recognizer mishearings:
 * - "u tube" / "yutub" -> "YouTube"
 * - "whats up" / "watsapp" -> "WhatsApp"
 * - "cap cut" -> "CapCut"
 * - "insta" -> "Instagram"
 * - Transliterated command verbs ("kholo", "chalao", "search karo")
 */
object TranscriptRepair {

    private val APP_REPAIRS = listOf(
        Regex("""\b(yutub|u tube|you tube|yt)\b""", RegexOption.IGNORE_CASE) to "YouTube",
        Regex("""\b(whats up|watsapp|watsap|whatapp|whtsapp)\b""", RegexOption.IGNORE_CASE) to "WhatsApp",
        Regex("""\b(insta|ig|instgram)\b""", RegexOption.IGNORE_CASE) to "Instagram",
        Regex("""\b(cap cut|kepkat|kapkat)\b""", RegexOption.IGNORE_CASE) to "CapCut",
        Regex("""\b(spotifi|spotifay)\b""", RegexOption.IGNORE_CASE) to "Spotify",
        Regex("""\b(playstore|pleystor|play stor)\b""", RegexOption.IGNORE_CASE) to "Play Store",
        Regex("""\b(krom|chrom|gugle chrom)\b""", RegexOption.IGNORE_CASE) to "Chrome"
    )

    private val VERB_REPAIRS = listOf(
        Regex("""\b(khola|khol|kholo|khol do|kholna)\b""", RegexOption.IGNORE_CASE) to "kholo",
        Regex("""\b(chala|chalao|chalu karo|play karo)\b""", RegexOption.IGNORE_CASE) to "chalao",
        Regex("""\b(band|band karo|ruk jao|rok do)\b""", RegexOption.IGNORE_CASE) to "band karo",
        Regex("""\b(khojo|dhoondo|search karo)\b""", RegexOption.IGNORE_CASE) to "search karo",
        Regex("""\b(bhejo|sand karo|send karo)\b""", RegexOption.IGNORE_CASE) to "bhejo"
    )

    /**
     * Repairs common acoustic artifacts in candidate device commands.
     * Leaves free natural conversations intact.
     */
    fun repair(rawText: String, context: Context? = null): String {
        if (rawText.isBlank()) return rawText

        var text = rawText.trim()

        // 1. App name repairs
        for ((regex, replacement) in APP_REPAIRS) {
            text = text.replace(regex, replacement)
        }

        // 2. Action verb repairs
        for ((regex, replacement) in VERB_REPAIRS) {
            text = text.replace(regex, replacement)
        }

        // 3. Contact name phonetic biasing if available
        if (context != null) {
            try {
                val contacts = ContactAliasStore.getSampleContactNames(context)
                for (contact in contacts) {
                    val cleanContact = contact.trim()
                    if (cleanContact.length >= 3) {
                        val pattern = Regex("""\b(${Regex.escape(cleanContact.lowercase(Locale.ROOT))})\b""", RegexOption.IGNORE_CASE)
                        text = text.replace(pattern, cleanContact)
                    }
                }
            } catch (ignored: Exception) {}
        }

        return text
    }
}
