package com.example.studio.render

/**
 * Pure caption logic (no Android classes): chunking into short timed groups, wrapping to a maximum width and
 * auto-fitting the font size. Used by the shared renderer for both preview and export, so a long sentence is
 * never drawn as one overflowing line.
 */
object CaptionChunker {

    data class Chunk(val text: String, val startUs: Long, val endUs: Long) {
        val words: List<String> get() = text.split(' ').filter { it.isNotEmpty() }
    }

    /**
     * Splits [text] into groups of [minWords]..[maxWords] words (at most [maxChars] characters each, unless a single
     * word is longer) and spreads [durationUs] over them in proportion to their length, so the caption follows the
     * narration pace. Punctuation ends a group early when the group already has [minWords] words.
     */
    fun chunk(text: String, startUs: Long, durationUs: Long, minWords: Int = 2, maxWords: Int = 4, maxChars: Int = 26): List<Chunk> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || durationUs <= 0) return emptyList()
        val groups = ArrayList<MutableList<String>>()
        var cur = ArrayList<String>()
        for (w in words) {
            val tentativeChars = (cur + w).joinToString(" ").length
            if (cur.isNotEmpty() && (cur.size >= maxWords || tentativeChars > maxChars)) { groups += cur; cur = ArrayList() }
            cur += w
            val endsSentence = w.last() in ".!?,;:\u0964\u2026"
            if (endsSentence && cur.size >= minWords) { groups += cur; cur = ArrayList() }
        }
        if (cur.isNotEmpty()) groups += cur
        // Avoid a lonely single-word tail: merge it into the previous group.
        if (groups.size >= 2 && groups.last().size < minWords) {
            val tail = groups.removeAt(groups.lastIndex)
            groups.last().addAll(tail)
        }
        val weights = groups.map { g -> g.joinToString(" ").length.coerceAtLeast(1) }
        val total = weights.sum().toDouble()
        var cursor = startUs
        var acc = 0.0
        return groups.mapIndexed { i, g ->
            acc += weights[i]
            val end = if (i == groups.lastIndex) startUs + durationUs else startUs + (durationUs * acc / total).toLong()
            Chunk(g.joinToString(" "), cursor, end).also { cursor = end }
        }
    }

    /** The chunk visible at [tUs] (the last one is held until the end of the item). */
    fun active(chunks: List<Chunk>, tUs: Long): Chunk? {
        if (chunks.isEmpty()) return null
        return chunks.firstOrNull { tUs >= it.startUs && tUs < it.endUs } ?: if (tUs >= chunks.last().endUs) chunks.last() else chunks.first()
    }

    /** Index of the word being spoken at [tUs] inside [chunk] (share of characters elapsed). */
    fun activeWordIndex(chunk: Chunk, tUs: Long): Int {
        val ws = chunk.words
        if (ws.size <= 1) return 0
        val span = (chunk.endUs - chunk.startUs).coerceAtLeast(1L)
        val p = ((tUs - chunk.startUs).toDouble() / span).coerceIn(0.0, 0.999)
        val total = ws.sumOf { it.length + 1 }.toDouble()
        var acc = 0.0
        ws.forEachIndexed { i, w -> acc += (w.length + 1) / total; if (p < acc) return i }
        return ws.lastIndex
    }
}

object CaptionLayout {

    data class Fit(val lines: List<String>, val textSize: Float)

    /** Greedy word wrap. A single word wider than [maxWidth] is hard-broken by characters so nothing can overflow. */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val lines = ArrayList<String>()
        var cur = ""
        fun pushWord(word: String) {
            var w = word
            while (measure(w) > maxWidth && w.length > 1) {
                var cut = w.length - 1
                while (cut > 1 && measure(w.substring(0, cut)) > maxWidth) cut--
                if (cur.isNotEmpty()) { lines += cur; cur = "" }
                lines += w.substring(0, cut)
                w = w.substring(cut)
            }
            val trial = if (cur.isEmpty()) w else "$cur $w"
            if (cur.isNotEmpty() && measure(trial) > maxWidth) { lines += cur; cur = w } else cur = trial
        }
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { pushWord(it) }
        if (cur.isNotEmpty()) lines += cur
        return lines
    }

    /**
     * Shrinks the font from [startSize] toward [minSize] until the text fits in [maxLines] lines within [maxWidth].
     * If it still does not fit at [minSize], the text is truncated with an ellipsis rather than overflowing.
     */
    fun fit(text: String, maxWidth: Float, maxLines: Int, startSize: Float, minSize: Float, measure: (String, Float) -> Float): Fit {
        var size = startSize
        while (true) {
            val lines = wrap(text, maxWidth) { measure(it, size) }
            if (lines.size <= maxLines) return Fit(lines, size)
            if (size <= minSize) {
                val kept = lines.take(maxLines).toMutableList()
                var last = kept.last()
                while (last.length > 1 && measure("$last\u2026", size) > maxWidth) last = last.dropLast(1)
                kept[kept.lastIndex] = "$last\u2026"
                return Fit(kept, size)
            }
            size = maxOf(minSize, size * 0.93f)
        }
    }
}
