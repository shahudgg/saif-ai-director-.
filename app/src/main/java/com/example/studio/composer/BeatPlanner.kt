package com.example.studio.composer

import com.example.studio.director.SceneOutline
import com.example.studio.director.SpeechTiming
import org.json.JSONObject

/**
 * Pacing: turns the approved narration into short visual beats (about 2.5-3.5 s each) so something new happens
 * every few seconds. A 58 s video becomes ~18 beats, not 3 long scenes.
 */
object BeatPlanner {

    const val TARGET_BEAT_MS = 3000L
    const val MIN_BEAT_MS = 1800L

    private val clauseSplit = Regex("(?<=[,;:\\u2014\\u2013])\\s+|\\s+(?=(?:aur|but|lekin|magar|kyunki|because|jabki|which|jo)\\s)", RegexOption.IGNORE_CASE)

    fun sentences(script: String): List<String> =
        script.replace(Regex("\\s*\\n+\\s*"), " ").trim()
            .split(Regex("(?<=[.!?\\u0964\\u2026])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Splits/merges text pieces so each beat holds [minWords]..[maxWords] words. */
    fun pieces(script: String, minWords: Int, maxWords: Int): List<String> {
        fun wc(s: String) = SpeechTiming.countWords(s)
        val out = ArrayList<String>()
        for (sentence in sentences(script)) {
            if (wc(sentence) <= maxWords) { out += sentence; continue }
            // long sentence: split at clauses first, then by word count
            val clauses = sentence.split(clauseSplit).map { it.trim() }.filter { it.isNotEmpty() }
            val acc = ArrayList<String>()
            var cur = ""
            for (c in clauses) {
                val trial = if (cur.isEmpty()) c else "$cur $c"
                if (cur.isNotEmpty() && wc(trial) > maxWords) { acc += cur; cur = c } else cur = trial
            }
            if (cur.isNotEmpty()) acc += cur
            for (a in acc) {
                if (wc(a) <= maxWords + 3) out += a
                else {
                    val ws = a.split(Regex("\\s+"))
                    val parts = Math.ceil(ws.size / maxWords.toDouble()).toInt()
                    val per = Math.ceil(ws.size / parts.toDouble()).toInt()
                    ws.chunked(per).forEach { out += it.joinToString(" ") }
                }
            }
        }
        // "Myth: ..." and "Fact: ..." belong on one screen
        val paired = ArrayList<String>()
        var k = 0
        while (k < out.size) {
            if (k + 1 < out.size && Regex("^(myth|mith)\\b", RegexOption.IGNORE_CASE).containsMatchIn(out[k]) &&
                Regex("^(fact|sach|truth)\\b", RegexOption.IGNORE_CASE).containsMatchIn(out[k + 1])) { paired += "${out[k]} ${out[k + 1]}"; k += 2 } else { paired += out[k]; k++ }
        }
        out.clear(); out.addAll(paired)
        // merge too-short pieces forward (or back at the end)
        val merged = ArrayList<String>()
        var i = 0
        while (i < out.size) {
            var cur = out[i]
            while (wc(cur) < minWords && i + 1 < out.size && wc(cur) + wc(out[i + 1]) <= maxWords + 3) { i++; cur = "$cur ${out[i]}" }
            if (wc(cur) < minWords && merged.isNotEmpty() && wc(merged.last()) + wc(cur) <= maxWords + 3) merged[merged.lastIndex] = "${merged.last()} $cur" else merged += cur
            i++
        }
        return merged
    }

    /**
     * Beat outlines with durations proportional to spoken length. [targetBeatMs] sets how many beats a video gets;
     * real narration length later re-times each beat (SceneTiming.retime) so audio and visuals stay in sync.
     */
    fun outline(
        script: String, totalMs: Long, language: String? = null, pace: String? = null,
        targetBeatMs: Long = TARGET_BEAT_MS, maxBeats: Int = 60
    ): List<SceneOutline> {
        val wpm = SpeechTiming.wordsPerMinute(language, pace)
        val targetWords = (targetBeatMs / 1000.0 * wpm / 60.0).toInt().coerceIn(4, 14)
        val minW = (targetWords * 0.55).toInt().coerceAtLeast(3)
        val maxW = (targetWords * 1.5).toInt().coerceAtLeast(minW + 2)
        var parts = pieces(script, minW, maxW)
        while (parts.size > maxBeats) parts = parts.chunked(2).map { it.joinToString(" ") }
        if (parts.isEmpty()) return emptyList()
        val weights = parts.map { SpeechTiming.countWords(it).coerceAtLeast(1) }
        val sum = weights.sum()
        val total = totalMs.coerceAtLeast(parts.size * MIN_BEAT_MS)
        var cursor = 0L
        return parts.mapIndexed { i, p ->
            val end = if (i == parts.lastIndex) maxOf(total, cursor + MIN_BEAT_MS) else cursor + maxOf(MIN_BEAT_MS, total * weights[i] / sum)
            SceneOutline("s${i + 1}", cursor, end, p).also { cursor = end }
        }
    }

    // ------------------------------------------------------------------ model directives (weak-model friendly)

    /** What a model may decide for one beat: only the intent and short strings. Layout stays local. */
    data class Directive(val index: Int, val intent: Intent?, val headline: String?, val support: String?, val keywords: List<String>, val items: List<String>)

    /** Tolerant parser: accepts {"beats":[...]} or a bare array, ignores unknown fields, never throws. */
    fun parseDirectives(raw: String): List<Directive> = runCatching {
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1) ?: raw
        val s = fence.indexOfFirst { it == '{' || it == '[' }
        val e = fence.indexOfLast { it == '}' || it == ']' }
        if (s < 0 || e <= s) return@runCatching emptyList<Directive>()
        val body = fence.substring(s, e + 1)
        val arr = if (body.trimStart().startsWith("[")) org.json.JSONArray(body) else JSONObject(body).optJSONArray("beats") ?: org.json.JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            Directive(
                index = o.optInt("i", i + 1), intent = Intent.parse(o.optString("intent", "")),
                headline = o.optString("headline", "").takeIf { it.isNotBlank() }, support = o.optString("support", "").takeIf { it.isNotBlank() },
                keywords = strs("keywords"), items = strs("items")
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Combines the model directive with local analysis. Anything missing, too long or implausible falls back to the
     * deterministic analyzer, so a weak model can only improve a beat, never break it.
     */
    fun toBeat(o: SceneOutline, index: Int, total: Int, d: Directive?, guardReason: (String) -> String?): Beat {
        val local = LocalBeatAnalyzer.analyze(o.narration, index, total, o.endMs - o.startMs)
        if (d == null) return local
        fun clean(s: String?, maxWords: Int): String? =
            s?.trim()?.takeIf { it.isNotEmpty() && SpeechTiming.countWords(it) <= maxWords && guardReason(it) == null }
        val intent = d.intent ?: local.intent
        val safeIntent = when {
            index == 0 -> Intent.HOOK
            // the model cannot invent a data scene the narration has no numbers/items for
            intent == Intent.CHART_BAR && local.numbers.size < 2 -> local.intent
            intent == Intent.CHART_LINE && local.numbers.size < 2 -> local.intent
            intent == Intent.TIMELINE && local.numberTokens.count { Regex("^(19|20)\\d{2}$").matches(it) } < 2 -> local.intent
            (intent == Intent.LIST || intent == Intent.PROCESS_STEPS) && maxOf(local.items.size, d.items.size) < 2 -> local.intent
            else -> intent
        }
        val items = d.items.map { it.trim() }.filter { it.isNotEmpty() && SpeechTiming.countWords(it) <= 7 && guardReason(it) == null }.take(4).ifEmpty { local.items }
        return local.copy(
            intent = safeIntent,
            headline = if (safeIntent == Intent.BIG_STAT) local.headline else (clean(d.headline, 6) ?: local.headline),
            support = clean(d.support, 18) ?: local.support,
            keywords = d.keywords.filter { guardReason(it) == null }.take(6).ifEmpty { local.keywords },
            items = items
        )
    }

    /** Short prompt for weak/standard models: choose an intent and one short headline per beat. Nothing about layout. */
    fun directivePrompt(language: String, style: String, beats: List<SceneOutline>, total: Int): String = buildString {
        append("You are choosing the on-screen visual idea for short beats of a narrated video.\n")
        append("For EACH beat reply with: i (the beat number), intent, headline (max 4 words, in $language), support (max 12 words), keywords (max 4), items (list of up to 4 short points, only if the beat is a list or steps).\n")
        append("intent must be one of: HOOK, BIG_STAT, LIST, COMPARISON, PROCESS_STEPS, TIMELINE, QUOTE, CHART_BAR, CHART_LINE, NETWORK_DIAGRAM, MAP_LIKE_ZOOM, MYTH_VS_FACT, QUESTION, CTA, STATEMENT.\n")
        append("Rules: beat 1 is HOOK, the last beat is CTA. Use numbers only if the narration has them. Vary the intents. Style: $style.\n")
        append("Reply with ONLY JSON: {\"beats\":[{\"i\":1,\"intent\":\"HOOK\",\"headline\":\"...\",\"support\":\"...\",\"keywords\":[\"..\"],\"items\":[]}]}\n\nBeats:\n")
        beats.forEach { o -> append(o.id.removePrefix("s")).append(": ").append(o.narration).append('\n') }
        append("(Total beats in the video: $total)\n")
    }
}
