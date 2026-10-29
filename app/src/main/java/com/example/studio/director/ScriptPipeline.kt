package com.example.studio.director

import com.example.studio.composer.Strategy

/** Scores hook candidates locally, so even a weak model's five options end up with the best one on screen. */
object HookScorer {
    private val curiosity = Regex("kabhi socha|secret|raaz|nobody|koi nahi|galat|shocking|you won't|you wont|sirf|pata hai|pata nahi|truth|sach|hidden|mistake|never|insane|crazy|asli|dhoka|stop|warning|chhupa", RegexOption.IGNORE_CASE)
    private val cliche = Regex("^(welcome|hello|hi guys|hey guys|namaste dosto|aaj hum|today we|in this video|is video me|aaj ki video)", RegexOption.IGNORE_CASE)
    private val secondPerson = Regex("\\b(aap|aapko|aapke|tum|tumhe|you|your)\\b", RegexOption.IGNORE_CASE)

    fun score(hook: String): Int {
        val h = hook.trim()
        if (h.isEmpty()) return Int.MIN_VALUE
        val words = SpeechTiming.countWords(h)
        var s = 0
        if (Regex("\\d").containsMatchIn(h)) s += 2
        if (h.contains('?')) s += 1
        if (curiosity.containsMatchIn(h)) s += 2
        if (secondPerson.containsMatchIn(h)) s += 1
        s += when (words) { in 6..14 -> 2; in 4..5, in 15..18 -> 0; else -> -2 }
        if (cliche.containsMatchIn(h)) s -= 4
        return s
    }

    fun best(options: List<String>): String? = options.map { it.trim() }.filter { it.isNotEmpty() }.maxByOrNull { score(it) }
}

/** Deterministic script review. Issues drive one targeted rewrite (strong models) or automatic fixes (weak models). */
object ScriptCritic {
    data class Review(val issues: List<String>, val words: Int, val targetWords: Int)

    private val ctaRx = Regex("follow|comment|subscribe|share|save|like|bata[ao]|batao|niche", RegexOption.IGNORE_CASE)
    private val statRx = Regex("\\d[\\d,.]*\\s?(%|percent|million|billion|crore|lakh|trillion)", RegexOption.IGNORE_CASE)

    fun sentences(s: String): List<String> = com.example.studio.composer.BeatPlanner.sentences(s)

    fun review(script: String, targetWords: Int, hasResearch: Boolean, researchDigest: String? = null): Review {
        val issues = ArrayList<String>()
        val sents = sentences(script)
        val words = SpeechTiming.countWords(script)
        if (sents.isEmpty()) return Review(listOf("The script is empty."), 0, targetWords)
        if (targetWords > 0) {
            if (words > targetWords * 1.15) issues += "Too long: $words words, target is about $targetWords. Cut the weakest sentences."
            if (words < targetWords * 0.8) issues += "Too short: $words words, target is about $targetWords. Add concrete detail or an example."
        }
        val first = sents.first()
        if (SpeechTiming.countWords(first) > 18) issues += "The opening sentence is too long. Open with a punchy hook of at most 14 words."
        if (HookScorer.score(first) < 1) issues += "The hook is weak. Start with a surprising fact, a bold claim or a question the viewer needs answered."
        val longOnes = sents.filter { SpeechTiming.countWords(it) > 26 }
        if (longOnes.isNotEmpty()) issues += "${longOnes.size} sentence(s) are too long to say in one breath (over 26 words). Split them."
        if (!sents.takeLast(2).any { ctaRx.containsMatchIn(it) }) issues += "The ending has no call to follow and comment."
        // repetition of 4-grams
        val toks = script.lowercase().split(Regex("[^\\p{L}\\p{N}\\u0900-\\u097F]+")).filter { it.isNotBlank() }
        val grams = HashMap<String, Int>()
        for (i in 0..toks.size - 4) grams.merge(toks.subList(i, i + 4).joinToString(" "), 1, Int::plus)
        grams.entries.firstOrNull { it.value >= 3 }?.let { issues += "The phrase \"${it.key}\" repeats ${it.value} times. Vary the wording." }
        // generic filler: with real research available, a good script is full of numbers, names, dates and places
        if (hasResearch && !researchDigest.isNullOrBlank() && sents.size >= 8) {
            val concrete = sents.count { Regex("\\d").containsMatchIn(it) || Regex("\\b[A-Z][a-z]{2,}").containsMatchIn(it.drop(1)) }
            if (concrete * 100 / sents.size < 30) issues += "The script is too generic: only $concrete of ${sents.size} sentences carry a number, name, date or place. Replace vague lines with specific facts from the research."
        }
        // unsupported statistics
        val stats = statRx.findAll(script).map { it.value.trim() }.toList()
        if (stats.isNotEmpty()) {
            if (!hasResearch) issues += "Statistics (${stats.take(3).joinToString(", ")}) appear without research. Remove them or phrase them as qualitative claims."
            else if (researchDigest != null) {
                val unsupported = stats.filter { st -> st.filter { it.isDigit() }.let { d -> d.isNotEmpty() && !researchDigest.contains(d) } }
                if (unsupported.isNotEmpty()) issues += "These numbers are not in the researched facts: ${unsupported.take(3).joinToString(", ")}. Remove them."
            }
        }
        return Review(issues, words, targetWords)
    }

    private fun ctaFor(language: String?): String = when (language?.trim()?.lowercase()) {
        "hindi", "hinglish", null, "" -> "Agar ye pasand aaya toh follow karo, aur comment me batao ki agla topic kya ho."
        else -> "If this helped, follow for more and comment which topic you want next."
    }

    /** Fixes that need no model: split over-long sentences at commas, trim to length (keeping hook and ending), ensure a CTA. */
    fun autoFix(script: String, targetWords: Int, language: String?): String {
        var sents = sentences(script).flatMap { s ->
            if (SpeechTiming.countWords(s) <= 26) listOf(s)
            else {
                val parts = s.split(Regex("(?<=,)\\s+")).filter { it.isNotBlank() }
                if (parts.size < 2) listOf(s) else {
                    val mid = parts.size / 2
                    listOf(parts.take(mid).joinToString(" ").trimEnd(',', ' ') + ".", parts.drop(mid).joinToString(" "))
                }
            }
        }
        if (targetWords > 0) {
            var guard = 0
            while (SpeechTiming.countWords(sents.joinToString(" ")) > targetWords * 1.15 && sents.size > 4 && guard++ < 50) {
                // drop the longest middle sentence (never the hook or the last two)
                val idx = (1 until sents.size - 2).maxByOrNull { SpeechTiming.countWords(sents[it]) } ?: break
                sents = sents.filterIndexed { i, _ -> i != idx }
            }
        }
        if (!sents.takeLast(2).any { ctaRx.containsMatchIn(it) }) sents = sents + ctaFor(language)
        return sents.joinToString(" ")
    }
}

/** Turns any script into the plain narration that is actually spoken: no citations, labels, markdown or stage directions. */
object VoiceoverScript {
    fun clean(script: String): String {
        var s = script
        s = s.replace(Regex("\\[(?:EDIT|NOTE|SFX|MUSIC|VISUAL|B-?ROLL|PAUSE)[^\\]]*\\]", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("\\[\\d{1,2}(?:\\s*,\\s*\\d{1,2})*\\]"), "")
        s = s.replace(Regex("\\((?:pause|beat|sfx|music|visual|b-?roll|cut to)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("(?m)^\\s*(?:#{1,6}\\s*|[-*•]\\s+|\\d+[.)]\\s+)"), "")
        s = s.replace(Regex("(?im)^\\s*(?:scene|beat|shot|hook|intro|outro|cta|narrator|voice[- ]?over|vo)\\s*\\d*\\s*[:\\-–]\\s*"), "")
        s = s.replace(Regex("[*_`~]+"), "")
        s = s.replace(Regex("https?://\\S+"), "")
        s = s.replace(Regex("[\\p{So}\\p{Cs}]"), "")
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.lines().joinToString("\n") { it.trim() }.replace(Regex("\\n{3,}"), "\n\n").trim()
        return s
    }
}

/** Orchestrates: facts -> hook options -> script -> review -> (one) rewrite. [ask] returns raw model text. */
class ScriptPipeline(private val ask: suspend (String) -> Result<String>, private val guard: (String) -> String?) {

    data class Outcome(val script: String, val hook: String?, val issuesLeft: List<String>, val notes: List<String>)

    private fun parseHooks(raw: String): List<String> {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1) ?: raw
        runCatching {
            val a = org.json.JSONArray(fenced.substring(fenced.indexOf('['), fenced.lastIndexOf(']') + 1))
            return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        }
        return fenced.lines().map { it.replace(Regex("^\\s*(?:\\d+[.)]|[-*•])\\s*"), "").trim().trim('"') }.filter { it.length in 12..160 }.take(8)
    }

    /** Up to five hook candidates for the topic (model output filtered for corruption). */
    suspend fun hookOptions(b: DirectorBrief, digest: String?): List<String> =
        ask(DirectorPrompts.hookOptions(b, digest)).getOrNull()?.takeIf { guard(it) == null }?.let { parseHooks(it) }?.filter { guard(it) == null } ?: emptyList()

    suspend fun run(b: DirectorBrief, digest: String?, targetWords: Int, strategy: Strategy?): Result<Outcome> {
        val notes = ArrayList<String>()
        val lang = b.narrationLanguage
        // 1) hook options, scored locally
        var hook: String? = null
        if (strategy != null) {
            val r = ask(DirectorPrompts.hookOptions(b, digest))
            r.getOrNull()?.takeIf { guard(it) == null }?.let { raw ->
                val opts = parseHooks(raw).filter { guard(it) == null }
                hook = HookScorer.best(opts)
                if (opts.isNotEmpty()) notes += "Picked a hook from ${opts.size} options."
            }
        }
        // 2) write the script around the chosen hook
        var script: String? = null
        var lastErr: Throwable? = null
        for (attempt in 1..2) {
            val extra = lastErr?.let { "\nThe previous attempt was rejected: ${it.message}. Write clean narration only, in the requested language." } ?: ""
            // App-wide memory: the channel name, niche, tone and past projects SAIF AI learned anywhere else in the
            // app are used while writing this script, so a repeat owner is not asked to explain themselves again.
            val memory = com.example.util.SaifMemoryCore.promptBlock(
                com.example.util.SaifMemoryCore.Features.VIDEO, limit = 12
            )
            val memoryBlock = if (memory.isBlank()) "" else "\n" + memory + "\nUse this only as background. Never name these facts in the narration unless the video is about them."
            val r = ask(DirectorPrompts.scriptV2(b, digest, targetWords, hook) + memoryBlock + extra)
            if (r.isFailure) { lastErr = r.exceptionOrNull(); continue }
            val text = VoiceoverScript.clean(r.getOrThrow())
            if (text.length <= 40) { lastErr = IllegalStateException("Model returned an empty script"); continue }
            val bad = guard(text) ?: ModelOutputGuard.reason(text, lang, requireScript = true)
            if (bad != null) { lastErr = IllegalStateException("the script $bad"); continue }
            script = text; break
        }
        var current = script ?: return Result.failure(lastErr ?: IllegalStateException("Script writing failed"))

        // 3) review; one targeted rewrite for capable models, deterministic fixes otherwise
        val hasResearch = digest != null
        var review = ScriptCritic.review(current, targetWords, hasResearch, digest)
        if (review.issues.isNotEmpty() && (strategy == Strategy.STRONG || strategy == Strategy.STANDARD)) {
            val r = ask(DirectorPrompts.rewrite(b, current, review.issues, targetWords))
            r.getOrNull()?.let { VoiceoverScript.clean(it) }?.takeIf { it.length > 40 && guard(it) == null && ModelOutputGuard.reason(it, lang, requireScript = true) == null }?.let { rewritten ->
                val rv = ScriptCritic.review(rewritten, targetWords, hasResearch, digest)
                if (rv.issues.size < review.issues.size) { current = rewritten; review = rv; notes += "Rewrote the script to fix ${review.issues.size} review issue(s)." }
            }
        }
        if (review.issues.isNotEmpty()) {
            val fixed = ScriptCritic.autoFix(current, targetWords, lang)
            val rv = ScriptCritic.review(fixed, targetWords, hasResearch, digest)
            if (rv.issues.size <= review.issues.size) { current = fixed; review = rv }
        }
        return Result.success(Outcome(current, hook, review.issues, notes))
    }
}
