package com.example.studio.director

/** One narration chunk with its planned time window. Narration always comes from the approved script, never from the model. */
data class SceneOutline(val id: String, val startMs: Long, val endMs: Long, val narration: String)

/** Detects corrupted model output (tokenizer garbage, wrong script) so it is never accepted as content. */
object ModelOutputGuard {
    private val badTokens = Regex("<unk>|<pad>|</?s>|<\\|[a-z_]+\\|>|\\uFFFD", RegexOption.IGNORE_CASE)

    /** Returns a human-readable reason when [text] looks corrupted, otherwise null. */
    fun reason(text: String, language: String? = null, requireScript: Boolean = false): String? {
        if (text.isBlank()) return null
        if (badTokens.containsMatchIn(text)) return "contains corrupted tokens such as <unk>"
        val letters = text.count { it.isLetter() }
        if (letters >= 12) {
            val lang = language?.trim()?.lowercase().orEmpty()
            val arabic = text.count { it in '\u0600'..'\u06FF' }
            if (arabic * 100 / letters > 20 && lang != "arabic" && lang != "urdu") return "is written in the wrong script (Arabic)"
            if (requireScript && lang == "hindi" && letters >= 60) {
                val deva = text.count { it in '\u0900'..'\u097F' }
                if (deva * 100 / letters < 30) return "is not written in Hindi (Devanagari)"
            }
        }
        return null
    }
}

object ScenePlanner {
    /** Splits the script into scenes (paragraphs, or sentence groups) and gives each a duration proportional to its word count. */
    fun outline(script: String, totalMs: Long, maxScenes: Int = 24): List<SceneOutline> {
        var parts = script.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3) {
            val sentences = script.trim().split(Regex("(?<=[.!?\u0964])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            val grouped = mutableListOf<String>()
            val cur = StringBuilder(); var words = 0
            for (s in sentences) {
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(s); words += SpeechTiming.countWords(s)
                if (words >= 28) { grouped += cur.toString(); cur.setLength(0); words = 0 }
            }
            if (cur.isNotEmpty()) grouped += cur.toString()
            if (grouped.size > parts.size) parts = grouped
        }
        while (parts.size > maxScenes) parts = parts.chunked(2).map { it.joinToString(" ") }
        if (parts.isEmpty()) return emptyList()
        val weights = parts.map { SpeechTiming.countWords(it).coerceAtLeast(1) }
        val sum = weights.sum()
        val total = totalMs.coerceAtLeast(parts.size * 1800L)
        var cursor = 0L
        return parts.mapIndexed { i, p ->
            val end = if (i == parts.lastIndex) maxOf(total, cursor + 1800L) else cursor + maxOf(1800L, total * weights[i] / sum)
            SceneOutline("s${i + 1}", cursor, end, p).also { cursor = end }
        }
    }

    /** A simple but real renderable scene, used only when the model could not produce valid visuals for a batch. */
    fun fallbackScene(o: SceneOutline, idx: Int): Scene {
        val title = o.narration.split(Regex("\\s+")).take(7).joinToString(" ")
        val dur = o.endMs - o.startMs
        val graph = SceneGraph(elements = listOf(
            Element(id = "ring$idx", type = "ring", x = 0.5f, y = 0.38f, w = 0.3f, color = "#7C4DFF",
                anims = listOf(Anim("scale", 0.6f, 1f, 0L, minOf(900L, dur), "overshoot"))),
            Element(id = "title$idx", type = "text", x = 0.5f, y = 0.62f, w = 0.84f, size = 0.055f, color = "#FFFFFF", text = title,
                anims = listOf(Anim("alpha", 0f, 1f, 0L, 500L, "easeOut")))
        ))
        return Scene(id = o.id, startMs = o.startMs, endMs = o.endMs, narration = o.narration,
            visualObjective = "Simple title card (automatic fallback)", graph = graph)
    }

    fun sceneText(s: Scene): String = buildString {
        append(s.caption).append(' ').append(s.visualObjective).append(' ')
        s.assets.forEach { append(it.description).append(' ') }
        s.cues.forEach { append(it.description).append(' ') }
        s.graph?.elements?.forEach { e -> append(e.text).append(' '); e.labels.forEach { l -> append(l).append(' ') } }
    }
}
