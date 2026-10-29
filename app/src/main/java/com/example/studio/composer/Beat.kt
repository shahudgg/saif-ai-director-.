package com.example.studio.composer

/**
 * One visual beat of the video (a sentence or two of narration shown for roughly 2-4 seconds).
 * The model (or local rules when the model fails) only picks an [Intent] and short text; the local composer designs the layout.
 */
enum class Intent {
    HOOK, BIG_STAT, LIST, COMPARISON, PROCESS_STEPS, TIMELINE, QUOTE, CHART_BAR, CHART_LINE,
    NETWORK_DIAGRAM, MAP_LIKE_ZOOM, MYTH_VS_FACT, QUESTION, CTA,
    /** Generic key-phrase scene used when nothing more specific applies. */
    STATEMENT;

    companion object {
        fun parse(raw: String?): Intent? {
            val k = raw?.trim()?.uppercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
            return values().firstOrNull { it.name == k }
                ?: when (k) {
                    "STAT", "NUMBER", "STATISTIC" -> BIG_STAT
                    "STEPS", "PROCESS", "HOW_TO" -> PROCESS_STEPS
                    "COMPARE", "VS" -> COMPARISON
                    "BAR", "BAR_CHART" -> CHART_BAR
                    "LINE", "LINE_CHART", "TREND", "GROWTH" -> CHART_LINE
                    "NETWORK", "DIAGRAM", "NEURAL" -> NETWORK_DIAGRAM
                    "MAP", "WORLD", "ZOOM" -> MAP_LIKE_ZOOM
                    "MYTH", "FACT", "MYTHBUSTER" -> MYTH_VS_FACT
                    "OUTRO", "FOLLOW", "CALL_TO_ACTION" -> CTA
                    "INTRO", "OPENING" -> HOOK
                    "KEY_POINT", "POINT", "TEXT", "TITLE" -> STATEMENT
                    else -> null
                }
        }
    }
}

data class Beat(
    val intent: Intent,
    val headline: String,
    val support: String = "",
    val keywords: List<String> = emptyList(),
    val numbers: List<Float> = emptyList(),
    val items: List<String> = emptyList(),
    val emotion: String = "neutral",
    val durationMs: Long = 3000L,
    val index: Int = 0,
    val total: Int = 1,
    /** Raw number tokens as spoken/written (e.g. "73%", "2017", "1.5 billion"), used for big-stat text. */
    val numberTokens: List<String> = emptyList()
)

/** Background/accent palettes. Adjacent beats use different palettes so the backdrop keeps changing subtly. */
data class Palette(
    val bgTop: String, val bgBottom: String, val accent: String, val accent2: String,
    val text: String = "#FFFFFF", val muted: String = "#C4BED6", val card: String = "#2B2445",
    val good: String = "#2EE59D", val bad: String = "#FF5C7A"
)

object Palettes {
    val ALL = listOf(
        Palette("#0B0A14", "#1B1330", "#7C4DFF", "#00E5FF"),
        Palette("#071A1F", "#0B2F3A", "#00E5FF", "#FFD54F"),
        Palette("#160B1E", "#2D1240", "#FF4DA6", "#7C4DFF"),
        Palette("#0A1420", "#13294B", "#4DA3FF", "#00F5A0"),
        Palette("#1A0F0A", "#33180F", "#FF8A3D", "#FFD54F"),
        Palette("#081611", "#0F2E22", "#00F5A0", "#4DA3FF")
    )

    fun pick(index: Int, emotion: String): Palette {
        val base = when (emotion) { "alert" -> 4; "wonder" -> 1; "excited" -> 2; else -> index }
        // Alternate so two neighbours never share a palette.
        return ALL[(base + (if (emotion == "neutral") 0 else index)).mod(ALL.size)]
    }
}

/** Deterministic text analysis: works with no model at all (English + Hinglish). */
object LocalBeatAnalyzer {

    private val stop = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "to", "of", "in", "on", "at", "for", "and", "or", "but", "with", "this", "that", "it", "as", "by", "from", "you", "your", "we", "our", "they", "their", "so", "if", "then", "than", "can", "will", "just", "very",
        "hai", "hain", "ka", "ki", "ke", "ko", "se", "me", "mein", "par", "pe", "ye", "yeh", "wo", "woh", "aur", "ya", "to", "toh", "bhi", "kya", "kyun", "kyunki", "jo", "tha", "thi", "the", "ho", "hota", "hoti", "hote", "karta", "karti", "karte", "kar", "kare", "nahi", "nahin", "ek", "is", "us", "iss", "uss",
        "hum", "aap", "tum", "main", "mai", "mera", "meri", "tera", "teri", "apna", "apni", "hi", "na", "jab", "tab", "ab", "phir", "fir", "lekin", "magar", "jaise", "kaise", "kuch", "sab", "bahut", "bohot", "wala", "wali", "wale", "rha", "rahi", "raha", "rahe", "gaya", "gayi", "liye", "isliye", "agar", "toh"
    )

    private val numberRx = Regex("""\d[\d,]*(?:\.\d+)?\s?(?:%|percent|x|k|lakh|crore|million|billion|trillion|cr|m|b)?""", RegexOption.IGNORE_CASE)
    private val yearRx = Regex("""\b(19|20)\d{2}\b""")

    fun words(s: String): List<String> =
        s.split(Regex("[^\\p{L}\\p{N}%'\\u0900-\\u097F]+")).filter { it.isNotBlank() }

    fun keywords(s: String, max: Int = 6): List<String> {
        val ws = words(s)
        val freq = ws.groupingBy { it.lowercase() }.eachCount()
        return ws.filter { it.lowercase() !in stop && it.length > 2 }
            .distinctBy { it.lowercase() }
            .sortedByDescending { (freq[it.lowercase()] ?: 1) * 3 + it.length }
            .take(max)
    }

    /** A short on-screen phrase (max [maxWords] words) in original word order, picked from the most informative words. */
    fun headline(s: String, maxWords: Int = 4): String {
        val clean = s.trim().trimEnd('.', '!', ',', ';', ':')
        val ws = words(clean)
        if (ws.size <= maxWords) return clean.replace(Regex("\\s+"), " ")
        val keep = keywords(clean, maxWords).map { it.lowercase() }.toSet()
        val picked = ws.filter { it.lowercase() in keep }.take(maxWords)
        return (picked.ifEmpty { ws.take(maxWords) }).joinToString(" ")
    }

    fun numbers(s: String): List<Pair<String, Float>> = numberRx.findAll(s).mapNotNull { m ->
        val tok = m.value.trim()
        val digits = tok.takeWhile { it.isDigit() || it == ',' || it == '.' }.replace(",", "")
        digits.toFloatOrNull()?.let { tok to it }
    }.toList()

    private fun listItems(s: String): List<String> {
        val body = s.substringAfter(':', s)
        val parts = body.split(Regex("""\s*(?:,|;|\band\b|\baur\b|\bphir\b|\bfir\b|\bthen\b|\bnext\b|\buske baad\b)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim().trim('.', '!', '?') }.filter { it.length in 2..42 }
        return parts.take(4)
    }

    fun emotion(s: String): String {
        val t = s.lowercase()
        return when {
            Regex("danger|khatar|risk|scam|fraud|dhokha|warning|careful|galat|nuksan|loss|fail").containsMatchIn(t) -> "alert"
            Regex("wow|amazing|shocking|incredible|unbelievable|kamaal|zabardast|gazab|insane|huge").containsMatchIn(t) || t.contains('!') -> "excited"
            Regex("future|space|universe|brain|dimag|duniya|world|secret|raaz").containsMatchIn(t) -> "wonder"
            else -> "neutral"
        }
    }

    /** Picks the intent from the sentence itself. [index]/[total] decide hook and call-to-action. */
    fun analyze(sentence: String, index: Int, total: Int, durationMs: Long): Beat {
        val s = sentence.trim()
        val lower = s.lowercase()
        val nums = numbers(s)
        val years = yearRx.findAll(s).map { it.value }.toList()
        val list = listItems(s)

        val intent: Intent = when {
            index == 0 -> Intent.HOOK
            index == total - 1 && total > 1 &&
                Regex("follow|comment|subscribe|share|like|save|bata[ao]|batao|niche|pin|link").containsMatchIn(lower) -> Intent.CTA
            Regex("\\bmyth\\b|\\bmith\\b|galat fehmi|misconception").containsMatchIn(lower) && Regex("\\bfact\\b|\\bsach\\b|\\btruth\\b|\\bhaqiqat\\b").containsMatchIn(lower) -> Intent.MYTH_VS_FACT
            s.contains('?') -> Intent.QUESTION
            Regex("\\bvs\\b|\\bversus\\b|\\bjabki\\b|\\bwhereas\\b|\\bcompared\\b|\\bke mukable\\b|\\bbanam\\b").containsMatchIn(lower) -> Intent.COMPARISON
            years.size >= 2 -> Intent.TIMELINE
            Regex("grow|growth|badh|increase|trend|rise|decline|ghat|girawat|over time|saal me").containsMatchIn(lower) && nums.size >= 2 -> Intent.CHART_LINE
            nums.size >= 3 -> Intent.CHART_BAR
            nums.isNotEmpty() && (s.contains('%') || nums[0].second >= 100f || Regex("\\bx\\b|times|guna|gunaa").containsMatchIn(lower)) -> Intent.BIG_STAT
            Regex("step|pehle|first|phir|fir |then|uske baad|finally|aakhir").containsMatchIn(lower) && list.size >= 2 -> Intent.PROCESS_STEPS
            list.size >= 3 -> Intent.LIST
            s.contains('"') || s.contains('\u201C') || Regex("\\bkaha\\b|\\bsaid\\b|\\bquote\\b").containsMatchIn(lower) -> Intent.QUOTE
            Regex("neural|network|layer|attention|transformer|neuron|connect|model|algorithm|\\bai\\b|\\bllm\\b|gpt|brain").containsMatchIn(lower) -> Intent.NETWORK_DIAGRAM
            Regex("world|duniya|global|country|desh|countries|map|india|usa|china|continent|everywhere|har jagah").containsMatchIn(lower) -> Intent.MAP_LIKE_ZOOM
            nums.isNotEmpty() -> Intent.BIG_STAT
            else -> Intent.STATEMENT
        }

        val head = when (intent) {
            Intent.BIG_STAT -> nums.firstOrNull()?.first ?: headline(s)
            Intent.HOOK -> headline(s, 5)
            Intent.CTA -> "FOLLOW"
            else -> headline(s, 4)
        }
        val tokens = nums.map { it.first }
        return Beat(
            intent = intent, headline = head,
            support = s.take(110), keywords = keywords(s), numbers = nums.map { it.second },
            items = list, emotion = emotion(s), durationMs = durationMs, index = index, total = total, numberTokens = tokens
        )
    }

    /** Breaks the pure-statement streak so neighbouring beats do not look the same. */
    fun diversify(beats: List<Beat>): List<Beat> {
        val rotation = listOf(Intent.STATEMENT, Intent.NETWORK_DIAGRAM, Intent.LIST, Intent.PROCESS_STEPS)
        var rot = 0
        return beats.mapIndexed { i, b ->
            val prev = beats.getOrNull(i - 1)?.intent
            if (b.intent == Intent.STATEMENT && prev == Intent.STATEMENT) {
                val alt = rotation[rot++ % rotation.size]
                // Only swap to an intent whose content we can actually provide.
                if (alt == Intent.LIST && b.items.size < 2) b else if (alt == Intent.PROCESS_STEPS && b.items.size < 2) b else b.copy(intent = alt)
            } else b
        }
    }
}
