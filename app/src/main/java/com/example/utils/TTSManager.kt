package com.example.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.ui.components.VOICE_PROFILES
import com.example.ui.components.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

object TTSManager : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var queuedText: String? = null
    private var queuedContext: Context? = null

    private val _currentlySpeakingText = MutableStateFlow<String?>(null)
    val currentlySpeakingText: StateFlow<String?> = _currentlySpeakingText

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    fun isSpeakingText(text: String): Boolean {
        return _currentlySpeakingText.value == text
    }

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    private val utteranceCallbacks = java.util.concurrent.ConcurrentHashMap<String, Pair<(() -> Unit)?, (() -> Unit)?>>()
    private var lastAppliedVoiceId: String = ""
    private var lastAppliedIsHindi: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    if (utteranceId != null) {
                        utteranceCallbacks[utteranceId]?.first?.invoke()
                    }
                }
                override fun onDone(utteranceId: String?) {
                    _currentlySpeakingText.value = null
                    if (utteranceId != null) {
                        val cb = utteranceCallbacks.remove(utteranceId)?.second
                        cb?.invoke()
                    }
                    if (utteranceCallbacks.isEmpty()) {
                        _isSpeaking.value = false
                    }
                }
                override fun onError(utteranceId: String?) {
                    _currentlySpeakingText.value = null
                    if (utteranceId != null) {
                        val cb = utteranceCallbacks.remove(utteranceId)?.second
                        cb?.invoke()
                    }
                    if (utteranceCallbacks.isEmpty()) {
                        _isSpeaking.value = false
                    }
                }
            })
            queuedText?.let { text ->
                queuedContext?.let { ctx ->
                    speak(text, ctx)
                }
                queuedText = null
                queuedContext = null
            }
        } else {
            Log.e("TTSManager", "Initialization failed")
        }
    }

    fun isHinglishOrHindi(text: String): Boolean {
        if (text.any { it in '\u0900'..'\u097F' }) return true
        val lower = text.lowercase()
        val hinglishKeywords = hashSetOf(
            "aap", "aapka", "aapki", "aapke", "aapse", "mai", "main", "mera", "meri", "mere",
            "hum", "humara", "humari", "kaise", "kaisa", "kaisi", "kya", "kyun", "kyu",
            "kab", "kahan", "kidhar", "kaun", "kis", "kisi", "bilkul", "theek", "thik",
            "accha", "achha", "achhi", "achhe", "baat", "karenge", "karein", "karo", "karna",
            "batao", "bataiye", "btaiye", "suno", "bolo", "samjho", "samajh", "pata",
            "nahi", "nhi", "haan", "han", "bahut", "bhot", "bohot", "yaar", "yar", "bhai",
            "dost", "madad", "shukriya", "namaste", "namaskar", "aaj", "kal", "abhi",
            "thoda", "zyada", "chahiye", "hoga", "hogi", "honge", "hai", "hain", "hoon",
            "tha", "thi", "the", "wala", "wali", "wale", "dekho", "dekhiye",
            "chal", "raha", "rahi", "rahe", "gaya", "gayi", "gaye", "bana", "banao", "kariye"
        )
        val tokens = lower.split(Regex("[^a-z0-9]+"))
        return tokens.any { it in hinglishKeywords }
    }

    fun applyProfile(
        profile: VoiceProfile,
        isHinglish: Boolean = false,
        hasDevanagari: Boolean = false,
        hasExcitement: Boolean = false
    ) {
        lastAppliedVoiceId = profile.id
        lastAppliedIsHindi = isHinglish || hasDevanagari

        // 1. Configure Language / Locale
        if (hasDevanagari) {
            val hiLocale = Locale("hi", "IN")
            val langAvailable = tts?.isLanguageAvailable(hiLocale)
            if (langAvailable != TextToSpeech.LANG_MISSING_DATA && langAvailable != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = hiLocale
            } else {
                tts?.language = Locale.Builder().setLanguage("en").setRegion("IN").build()
            }
        } else if (isHinglish) {
            // For Romanized Hinglish, Indian English locale provides the smoothest phonetic pronunciation without stuttering
            val enInLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
            val avail = tts?.isLanguageAvailable(enInLocale)
            if (avail != TextToSpeech.LANG_MISSING_DATA && avail != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = enInLocale
            } else {
                tts?.language = Locale("hi", "IN")
            }
        } else {
            val enInLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
            val avail = tts?.isLanguageAvailable(enInLocale)
            if (avail != TextToSpeech.LANG_MISSING_DATA && avail != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = enInLocale
            } else {
                tts?.language = Locale.US
            }
        }

        // 2. Select optimal voice matching profile target, accent, and gender
        val voices = tts?.voices
        if (voices != null && voices.isNotEmpty()) {
            var selectedVoice: android.speech.tts.Voice? = null
            val isMale = profile.gender.equals("Male", ignoreCase = true)

            // Step A: Check profile preferred matches
            for (target in profile.targetEngineNameMatches) {
                val match = voices.find { it.name.equals(target, ignoreCase = true) }
                if (match != null) {
                    selectedVoice = match
                    break
                }
            }

            // Step B: Partial match from targets
            if (selectedVoice == null) {
                for (target in profile.targetEngineNameMatches) {
                    val match = voices.find { it.name.contains(target, ignoreCase = true) }
                    if (match != null) {
                        selectedVoice = match
                        break
                    }
                }
            }

            // Step C: If Hinglish, find Indian English or Hindi voice with matching gender
            if (selectedVoice == null && isHinglish) {
                selectedVoice = voices.filter { v ->
                    val n = v.name.lowercase()
                    (n.contains("en-in") || n.contains("en_in") || n.contains("hi-in") || n.contains("hi_in")) &&
                    if (isMale) (n.contains("male") || n.contains("ene") || n.contains("hia"))
                    else (n.contains("female") || n.contains("cxx") || n.contains("hic"))
                }.maxByOrNull { if (it.isNetworkConnectionRequired) 0 else 1 }
            }

            // Step D: Any voice matching gender
            if (selectedVoice == null) {
                selectedVoice = voices.find { v ->
                    val n = v.name.lowercase()
                    if (isMale) n.contains("male") else n.contains("female")
                }
            }

            if (selectedVoice != null) {
                try {
                    tts?.voice = selectedVoice
                    Log.d("TTSManager", "Applied voice: ${selectedVoice.name} (Hinglish: $isHinglish)")
                } catch (e: Exception) {
                    Log.w("TTSManager", "Error setting voice: ${e.message}")
                }
            }
        }

        // 3. Emotional dynamic pitch & speed rate
        val emotionalPitch = if (hasExcitement) {
            (profile.pitch * 1.05f).coerceIn(0.70f, 1.30f)
        } else {
            profile.pitch
        }
        val smoothRate = profile.speechRate.coerceIn(0.90f, 1.08f)

        tts?.setPitch(emotionalPitch)
        tts?.setSpeechRate(smoothRate)
    }

    fun previewVoice(context: Context, text: String, voiceId: String) {
        if (!isReady) {
            init(context)
            return
        }
        val clean = cleanTextForSpeech(text)
        val hasDevanagari = clean.any { it in '\u0900'..'\u097F' }
        val isHinglish = isHinglishOrHindi(clean)
        val hasExcitement = clean.contains("!") || clean.contains("Bilkul", ignoreCase = true)

        val profile = VOICE_PROFILES.find { it.id == voiceId || (voiceId == "Fenrir" && it.id == "Kabir") } ?: VOICE_PROFILES.first()
        applyProfile(profile, isHinglish = isHinglish, hasDevanagari = hasDevanagari, hasExcitement = hasExcitement)

        _currentlySpeakingText.value = text
        val utteranceId = "preview_id_${System.currentTimeMillis()}"
        val params = android.os.Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun speak(text: String, context: Context, onStart: (() -> Unit)? = null, onDone: (() -> Unit)? = null) {
        speakInternal(text, context, queueMode = TextToSpeech.QUEUE_FLUSH, onStart, onDone)
    }

    fun speakQueued(text: String, context: Context, onStart: (() -> Unit)? = null, onDone: (() -> Unit)? = null) {
        speakInternal(text, context, queueMode = TextToSpeech.QUEUE_ADD, onStart, onDone)
    }

    private fun speakInternal(
        text: String,
        context: Context,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = cleanTextForSpeech(text)
        if (cleanText.isBlank()) {
            _currentlySpeakingText.value = null
            onDone?.invoke()
            return
        }

        _currentlySpeakingText.value = text

        val utteranceId = "LIVE_SPEECH_${System.currentTimeMillis()}_${cleanText.hashCode()}"
        if (onStart != null || onDone != null) {
            utteranceCallbacks[utteranceId] = Pair(onStart, onDone)
        }

        if (!isReady) {
            queuedText = cleanText
            queuedContext = context
            if (tts == null) {
                init(context)
            }
            return
        }

        val prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE)
        val selectedVoiceId = prefs.getString("selected_voice_id", "Kabir") ?: "Kabir"

        val hasDevanagari = cleanText.any { it in '\u0900'..'\u097F' }
        val isHinglish = isHinglishOrHindi(cleanText)
        val hasExcitement = cleanText.contains("!") || cleanText.contains("Bilkul", ignoreCase = true) || cleanText.contains("Super", ignoreCase = true)
        val profile = VOICE_PROFILES.find { it.id == selectedVoiceId || (selectedVoiceId == "Fenrir" && it.id == "Kabir") } ?: VOICE_PROFILES.first()
        
        applyProfile(profile, isHinglish = isHinglish, hasDevanagari = hasDevanagari, hasExcitement = hasExcitement)

        val params = android.os.Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        tts?.speak(cleanText, queueMode, params, utteranceId)
    }

    fun stop() {
        _isSpeaking.value = false
        _currentlySpeakingText.value = null
        utteranceCallbacks.clear()
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("TTSManager", "Error stopping TTS: ${e.message}")
        }
    }

    /**
     * Sanitizes text specifically for natural voice speech output:
     * - Strips all emojis and pictographs so the TTS engine never reads emoji names (e.g. "smiling face with smiling eyes").
     * - Strips markdown image tags and raw URLs so image generation links aren't read out as code.
     * - Strips code blocks and programming code syntax so TTS doesn't vocalize brackets and symbols.
     * - Cleans up markdown formatting (bold, italics, headers, bullets).
     */
    fun cleanTextForSpeech(rawText: String): String {
        if (rawText.isBlank()) return ""

        val hasImageMarkdown = rawText.contains("![") ||
                rawText.contains("image.pollinations.ai", ignoreCase = true) ||
                rawText.contains("<img", ignoreCase = true)

        var text = rawText

        // Guard: If raw text is an API error message, speak a brief user-friendly notification
        val trimmed = text.trimStart()
        if (trimmed.startsWith("❌") || trimmed.contains("**API Error:**", ignoreCase = true) || trimmed.contains("API Error: All configured attempts failed", ignoreCase = true)) {
            return "API connection me dikkat aayi hai. Kripya Settings me API key check karein."
        }

        // Guard: If raw text is a source code file (starts with package, import, class, etc.), do not read code
        if (trimmed.startsWith("package ") || trimmed.startsWith("import ") ||
            trimmed.startsWith("class ") || trimmed.startsWith("fun ") ||
            trimmed.startsWith("public class ") || trimmed.startsWith("<!DOCTYPE") ||
            trimmed.startsWith("<html")) {
            return "Aapka code generate kar diya hai sir."
        }

        // 1. Remove markdown images: ![alt](url)
        text = text.replace(Regex("""!\[.*?\]\([^\)]*\)"""), " ")

        // 2. Remove HTML image tags: <img ...>
        text = text.replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), " ")

        // 3. Handle code blocks: ```lang ... ```
        text = text.replace(Regex("""```[\s\S]*?```""")) { matchResult ->
            val block = matchResult.value
            // If code block contains an image link or pollinations url, completely strip it
            if (block.contains("image.pollinations.ai", ignoreCase = true) || block.contains("![")) {
                ""
            } else {
                // Programming code block: omit from voice so TTS doesn't read brackets and symbols
                " "
            }
        }

        // 4. Remove inline code backticks: `code` -> code
        text = text.replace(Regex("""`([^`]+)`"""), "$1")

        // 5. Convert markdown links to just their label: [text](url) -> text
        text = text.replace(Regex("""\[([^\]]+)\]\([^\)]*\)"""), "$1")

        // 6. Remove raw URLs: http://..., https://..., www....
        text = text.replace(Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE), " ")

        // 7. Strip markdown headers, lists, quotes, rules
        text = text.replace(Regex("""(?m)^#{1,6}\s+"""), "")
        text = text.replace(Regex("""(?m)^[\s]*[-*+]\s+"""), "")
        text = text.replace(Regex("""(?m)^[\s]*\d+\.\s+"""), "")
        text = text.replace(Regex("""(?m)^>\s*"""), "")
        text = text.replace(Regex("""(?m)^[-*_]{3,}\s*$"""), "")

        // 8. Strip bold, italic, strikethrough markers
        text = text.replace(Regex("""\*{1,3}([^*]+)\*{1,3}"""), "$1")
        text = text.replace(Regex("""_{1,3}([^_]+)_{1,3}"""), "$1")
        text = text.replace(Regex("""~~([^~]+)~~"""), "$1")

        // 9. Strip leftover markdown/code symbols: * # ` ~ _ > | { } [ ] \ < >
        text = text.replace(Regex("""[*#`~_>|{}\[\]\\<>]"""), " ")

        // 10. Strip ALL emojis and pictographs so TTS never pronounces emoji names
        text = removeEmojis(text)

        // 11. Normalize spaces and fix spacing before punctuation marks
        text = text.replace(Regex("""\s+"""), " ")
        text = text.replace(Regex("""\s+([,.:;!?])"""), "$1").trim()

        // 13. Smooth Hinglish phonetics and collapse stuttering characters
        text = smoothTextForHinglish(text)

        if (text.isBlank() && hasImageMarkdown) {
            return "Maine aapke liye image generate kar di hai."
        }

        return text
    }

    /**
     * Smooths phonetic clusters and chat shorthand for natural, stutter-free Hinglish delivery
     */
    fun smoothTextForHinglish(rawText: String): String {
        var text = rawText

        // 1. Collapse 3+ repeated characters (e.g. hrrllloo -> hello, sooooo -> soo, kyyyya -> kya)
        text = text.replace(Regex("""([a-zA-Z])\1{2,}""")) { match ->
            val ch = match.value[0]
            "$ch$ch"
        }

        // 2. Normalize symbols that trip up TTS engines into natural spoken words
        text = text.replace("&", " aur ")
            .replace("/", " ya ")
            .replace("+", " plus ")
            .replace("%", " percent ")
            .replace("~", " ")
            .replace("=", " barabar ")
            .replace("|", " ")

        // 3. Normalize chat shorthand to phonetically clear, smooth words that TTS pronounces without hesitation
        val replacements = listOf(
            Regex("""\bhr+l+o+\b""", RegexOption.IGNORE_CASE) to "hello",
            Regex("""\bbtaiye\b""", RegexOption.IGNORE_CASE) to "bataiye",
            Regex("""\bbtao\b""", RegexOption.IGNORE_CASE) to "batao",
            Regex("""\bnhi\b""", RegexOption.IGNORE_CASE) to "nahi",
            Regex("""\bnhn\b""", RegexOption.IGNORE_CASE) to "nahi",
            Regex("""\bnahe\b""", RegexOption.IGNORE_CASE) to "nahi",
            Regex("""\bbhot\b""", RegexOption.IGNORE_CASE) to "bahut",
            Regex("""\bbohot\b""", RegexOption.IGNORE_CASE) to "bahut",
            Regex("""\bbht\b""", RegexOption.IGNORE_CASE) to "bahut",
            Regex("""\bkro\b""", RegexOption.IGNORE_CASE) to "karo",
            Regex("""\bkrna\b""", RegexOption.IGNORE_CASE) to "karna",
            Regex("""\bkrein\b""", RegexOption.IGNORE_CASE) to "karein",
            Regex("""\bkr\b""", RegexOption.IGNORE_CASE) to "kar",
            Regex("""\bkregi\b""", RegexOption.IGNORE_CASE) to "karegi",
            Regex("""\bkrega\b""", RegexOption.IGNORE_CASE) to "karega",
            Regex("""\byrr+\b""", RegexOption.IGNORE_CASE) to "yaar",
            Regex("""\byar\b""", RegexOption.IGNORE_CASE) to "yaar",
            Regex("""\bplz\b""", RegexOption.IGNORE_CASE) to "please",
            Regex("""\bpls\b""", RegexOption.IGNORE_CASE) to "please",
            Regex("""\bthnx\b""", RegexOption.IGNORE_CASE) to "dhanyawaad",
            Regex("""\bthx\b""", RegexOption.IGNORE_CASE) to "dhanyawaad",
            Regex("""\bkyu\b""", RegexOption.IGNORE_CASE) to "kyon",
            Regex("""\bkyun\b""", RegexOption.IGNORE_CASE) to "kyon",
            Regex("""\bachhi\b""", RegexOption.IGNORE_CASE) to "acchi",
            Regex("""\bachhe\b""", RegexOption.IGNORE_CASE) to "acche",
            Regex("""\bthk\b""", RegexOption.IGNORE_CASE) to "theek",
            Regex("""\bsb\b""", RegexOption.IGNORE_CASE) to "sab",
            Regex("""\bhnn\b""", RegexOption.IGNORE_CASE) to "haan",
            Regex("""\bhn\b""", RegexOption.IGNORE_CASE) to "haan",
            Regex("""\bapka\b""", RegexOption.IGNORE_CASE) to "aapka",
            Regex("""\bapki\b""", RegexOption.IGNORE_CASE) to "aapki",
            Regex("""\bapke\b""", RegexOption.IGNORE_CASE) to "aapke",
            Regex("""\bapse\b""", RegexOption.IGNORE_CASE) to "aapse",
            Regex("""\bchahiye\b""", RegexOption.IGNORE_CASE) to "chaahiye",
            Regex("""\bsmjh\b""", RegexOption.IGNORE_CASE) to "samajh",
            Regex("""\bsmjho\b""", RegexOption.IGNORE_CASE) to "samjho",
            Regex("""\bkch\b""", RegexOption.IGNORE_CASE) to "kuch",
            Regex("""\blg\b""", RegexOption.IGNORE_CASE) to "lag",
            Regex("""\brha\b""", RegexOption.IGNORE_CASE) to "raha",
            Regex("""\brhi\b""", RegexOption.IGNORE_CASE) to "rahi",
            Regex("""\brhe\b""", RegexOption.IGNORE_CASE) to "rahe"
        )
        for ((regex, replacement) in replacements) {
            text = text.replace(regex, replacement)
        }

        // 4. Convert multi-dots or stutter punctuation into smooth breathing comma pauses
        text = text.replace(Regex("""\.\.\.+"""), ", ")
        text = text.replace(Regex("""\?\?+"""), "? ")
        text = text.replace(Regex("""!!+"""), "! ")
        text = text.replace(Regex("""--+"""), ", ")

        // 5. Ensure comma and periods have a trailing space for natural breath pauses
        text = text.replace(Regex("""([,.:;!?])(?=[a-zA-Z0-9])"""), "$1 ")
        text = text.replace(Regex("""\s+"""), " ").trim()

        return text
    }

    private fun removeEmojis(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        val len = input.length
        while (i < len) {
            val codePoint = input.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            if (!isEmojiCodePoint(codePoint)) {
                sb.appendCodePoint(codePoint)
            }
            i += charCount
        }
        return sb.toString()
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return when {
            // Emoticons (1F600 - 1F64F)
            codePoint in 0x1F600..0x1F64F -> true
            // Miscellaneous Symbols and Pictographs (1F300 - 1F5FF)
            codePoint in 0x1F300..0x1F5FF -> true
            // Transport and Map Symbols (1F680 - 1F6FF)
            codePoint in 0x1F680..0x1F6FF -> true
            // Supplemental Symbols and Pictographs (1F900 - 1F9FF)
            codePoint in 0x1F900..0x1F9FF -> true
            // Symbols and Pictographs Extended-A (1FA70 - 1FAFF)
            codePoint in 0x1FA70..0x1FAFF -> true
            // Other Plane 1 Emoji / Pictograph blocks (1F000 - 1FA6F)
            codePoint in 0x1F000..0x1FA6F -> true
            // Regional Indicator Symbols (Flags: 1F1E6 - 1F1FF)
            codePoint in 0x1F1E6..0x1F1FF -> true
            // Miscellaneous Symbols (2600 - 26FF, e.g. ☀️, ☁️, ☂️, ☕, ⚡, ⚽)
            codePoint in 0x2600..0x26FF -> true
            // Dingbats (2700 - 27BF, e.g. ✂️, ✈️, ✉️, ✌️, ✨, ❄️, ❤️)
            codePoint in 0x2700..0x27BF -> true
            // Miscellaneous Technical (2300 - 23FF, e.g. ⌚, ⌛, ⏰, ⏱️)
            codePoint in 0x2300..0x23FF -> true
            // Geometric Shapes & Arrows often used as emojis (2B00 - 2BFF, e.g. ⭐, ⭕)
            codePoint in 0x2B00..0x2BFF -> true
            // Arrows (2190 - 21FF) and Supplemental Arrows (2900 - 297F)
            codePoint in 0x2190..0x21FF -> true
            codePoint in 0x2900..0x297F -> true
            // Variation Selectors (FE00 - FE0F, e.g. \uFE0F)
            codePoint in 0xFE00..0xFE0F -> true
            // Zero Width Joiner (200D)
            codePoint == 0x200D -> true
            // Combining Enclosing Keycap (20E3)
            codePoint == 0x20E3 -> true
            // Enclosed Ideographic Supplement
            codePoint in 0x1F200..0x1F2FF -> true
            // Supplemental Punctuation / Waves
            codePoint in 0x3030..0x303D -> true
            else -> false
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
