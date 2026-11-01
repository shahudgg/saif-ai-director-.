package com.example.studio.director

import kotlin.math.max
import kotlin.math.roundToInt

/** Spoken-pace model. Words-per-minute depends on language and pace; never a single fixed constant. */
object SpeechTiming {
    private val baseWpm = mapOf(
        "english" to 150, "hindi" to 135, "hinglish" to 140, "spanish" to 160, "french" to 155,
        "german" to 130, "arabic" to 120, "japanese" to 125, "portuguese" to 155, "bengali" to 125,
        "tamil" to 115, "urdu" to 135
    )

    fun wordsPerMinute(language: String?, pace: String?): Int {
        val base = baseWpm[language?.trim()?.lowercase()] ?: 140
        val factor = when (pace?.trim()?.lowercase()) {
            "slow", "calm" -> 0.85
            "fast", "energetic" -> 1.15
            else -> 1.0
        }
        return (base * factor).roundToInt().coerceAtLeast(60)
    }

    fun targetWords(durationSec: Int, language: String?, pace: String?): Int =
        ((durationSec / 60.0) * wordsPerMinute(language, pace)).roundToInt().coerceAtLeast(1)

    fun countWords(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    fun estimateSeconds(text: String, language: String?, pace: String?): Double =
        countWords(text) * 60.0 / wordsPerMinute(language, pace)

    /** Returns a warning when [durationSec] cannot be filled/fit by [materialWords] of source material. */
    fun feasibilityWarning(durationSec: Int, materialWords: Int, language: String?, pace: String?): String? {
        if (materialWords <= 0) return null
        val needed = targetWords(durationSec, language, pace)
        return when {
            materialWords > needed * 1.35 ->
                "Your script is ~${materialWords} words but $durationSec s fits only ~$needed words at this pace. " +
                    "Choose: shorten the narration, or make the video about ${(estimateSeconds("x ".repeat(materialWords), language, pace)).roundToInt()} s."
            materialWords < needed * 0.55 ->
                "Your material is ~${materialWords} words, but $durationSec s needs ~$needed. Choose: expand with more detail, or make the video shorter."
            else -> null
        }
    }

    fun parseDuration(input: String): Int? {
        val t = input.trim().lowercase()
        Regex("^(\\d{1,3}):(\\d{2})$").find(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        val m = Regex("(\\d+)\\s*(?:m|min|minute|minutes)").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val s = Regex("(\\d+)\\s*(?:s|sec|second|seconds)").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (m != null || s != null) return (m ?: 0) * 60 + (s ?: 0)
        return t.toIntOrNull()?.takeIf { it in 5..7200 }
    }
}

/** Decides which question to ask next. Infers from prompt/earlier answers; never asks everything. */
object DirectorQuestions {

    fun classifyScript(prompt: String): ScriptMode {
        val words = SpeechTiming.countWords(prompt)
        val hasSceneMarkers = Regex("(?im)^\\s*(scene|narrator|voiceover|vo)\\b|\\[(scene|narrator)").containsMatchIn(prompt)
        return when {
            words >= 180 || hasSceneMarkers -> ScriptMode.FULL_SCRIPT_PRESERVE
            words in 60..179 && prompt.count { it == '.' || it == '।' } >= 4 -> ScriptMode.PARTIAL_SCRIPT_EXPAND
            words < 3 -> ScriptMode.BRIEF_NEEDS_CLARIFICATION
            else -> ScriptMode.TOPIC_NEEDS_RESEARCH
        }
    }

    fun detectLanguage(text: String): String? {
        val deva = text.count { it in '\u0900'..'\u097F' }
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val hinglishWords = Regex("(?i)\\b(kaise|kya|hai|karo|banao|aur|mujhe|video|ke liye|nahi|bhai)\\b").findAll(text).count()
        return when {
            deva > latin / 2 && deva > 5 -> "Hindi"
            hinglishWords >= 2 -> "Hinglish"
            latin > 20 -> "English"
            else -> null
        }
    }

    fun inferAspect(text: String): String? {
        val t = text.lowercase()
        return when {
            Regex("shorts?|reels?|tiktok|9:16|vertical").containsMatchIn(t) -> "9:16"
            Regex("youtube video|16:9|landscape|documentary|horizontal").containsMatchIn(t) -> "16:9"
            Regex("1:1|square|instagram post").containsMatchIn(t) -> "1:1"
            else -> null
        }
    }

    private fun durationCard(wf: DirectorWorkflow, aspect: String?): QuestionCard {
        val short = aspect == "9:16"
        val opts = if (short) listOf("30 seconds", "45 seconds", "60 seconds", "90 seconds")
        else listOf("1 minute", "3 minutes", "5 minutes", "10 minutes")
        return QuestionCard("duration", "How long should the video be?", opts, optional = false,
            customHint = "Exact length, e.g. 2:30 or 4 min 20 sec")
    }

    private val languageCard = QuestionCard("language", "Which language for narration?",
        listOf("English", "Hindi", "Hinglish", "Spanish"), optional = false, customHint = "Any language your voice provider supports")

    /** The next unanswered, materially useful question, or null when the brief is ready. */
    fun next(brief: DirectorBrief): QuestionCard? {
        val wf = brief.workflow
        val aspect = brief.aspect ?: inferAspect(brief.prompt)
        val needsNarrationLanguage = wf.generatesNarration || wf == DirectorWorkflow.VOICE_OVER_TO_VIDEO
        val queue = mutableListOf<QuestionCard>()

        if (wf == DirectorWorkflow.ZERO_TO_VIDEO && brief.scriptMode != ScriptMode.FULL_SCRIPT_PRESERVE) {
            queue += durationCard(wf, aspect)
        }
        if (wf == DirectorWorkflow.VIDEO_TO_EDITING || wf == DirectorWorkflow.VIDEO_EDIT_WITH_VOICE) {
            queue += QuestionCard("goal", "What do you want to achieve with this video?",
                listOf("Tighter, faster pacing", "Social-media cut", "Educational polish", "Cinematic look"))
            queue += QuestionCard("duration", "Target length of the edit?",
                listOf("Keep original length", "Cut ~25% shorter", "Cut ~50% shorter", "Under 60 seconds"))
        }
        if (needsNarrationLanguage && wf != DirectorWorkflow.VOICE_OVER_TO_VIDEO && brief.narrationLanguage == null) {
            queue += languageCard
        }
        if (wf == DirectorWorkflow.VIDEO_EDIT_WITH_VOICE) {
            queue += QuestionCard("narrationMode", "How should narration combine with the original audio?",
                listOf("Replace original speech", "Add narration, keep original audio", "Mix dialogue + narration", "Narrate only silent parts"), optional = false)
        }
        queue += QuestionCard("aspect", "Aspect ratio?", listOf("9:16", "16:9", "1:1", "4:5"))
        queue += QuestionCard("style", "Editing style?",
            listOf("Educational explainer", "Documentary", "Cinematic", "Motivational"))
        queue += QuestionCard("audience", "Who is the audience?",
            listOf("General public", "Students", "Professionals", "Kids / beginners"))
        queue += QuestionCard("subtitles", "Subtitles?",
            listOf("Yes - word highlight", "Yes - simple lines", "No subtitles", "Only key phrases"))
        if (wf != DirectorWorkflow.VIDEO_TO_EDITING) {
            queue += QuestionCard("motionLevel", "Motion graphics level?",
                listOf("Minimal", "Balanced", "Heavy", "Footage-first with accents"))
        }
        if (wf.generatesNarration) {
            queue += QuestionCard("voiceStyle", "Voice style and pace?",
                listOf("Calm and clear", "Energetic", "Warm storyteller", "Serious documentary"))
        }
        queue += QuestionCard("assetPolicy", "Where should visuals come from?",
            listOf("My uploaded assets", "Find from asset library", "Generate original graphics", "Mix of all"))
        queue += QuestionCard("avoid", "Anything to avoid?",
            listOf("Nothing specific", "No memes", "No loud sound effects", "No stock-looking visuals"))

        // Required questions first, optional ones only while the brief is thin.
        val unanswered = queue.filter { !brief.answered(it.key) && !alreadyKnown(brief, it.key, aspect) }
        val required = unanswered.firstOrNull { !it.optional }
        if (required != null) return required
        val askedOptional = brief.answers.keys.count { it !in GuidedBriefOptions.METADATA_KEYS } + brief.skipped.size
        return if (askedOptional < MAX_OPTIONAL_QUESTIONS) unanswered.firstOrNull() else null
    }

    const val MAX_OPTIONAL_QUESTIONS = 5

    private fun alreadyKnown(b: DirectorBrief, key: String, aspect: String?): Boolean = when (key) {
        "aspect" -> b.aspect != null || aspect != null
        "duration" -> b.durationSec != null
        "language" -> b.narrationLanguage != null
        "style" -> b.style != null
        "audience" -> b.audience != null
        "subtitles" -> b.subtitles != null
        else -> false
    }

    /** Applies an answer to the brief without discarding anything previously collected. */
    fun apply(brief: DirectorBrief, key: String, answer: String): DirectorBrief {
        val a = answer.trim()
        val withAnswer = brief.copy(answers = brief.answers + (key to a), skipped = brief.skipped - key)
        return when (key) {
            "duration" -> withAnswer.copy(durationSec = SpeechTiming.parseDuration(
                a.replace("seconds", "sec").replace("minutes", "min").replace("minute", "min")) ?: brief.durationSec)
            "language" -> withAnswer.copy(narrationLanguage = a,
                onScreenLanguage = brief.onScreenLanguage ?: a, subtitleLanguage = brief.subtitleLanguage ?: a,
                researchLanguage = brief.researchLanguage ?: a)
            "aspect" -> withAnswer.copy(aspect = a)
            "style" -> withAnswer.copy(style = a)
            "audience" -> withAnswer.copy(audience = a)
            "subtitles" -> withAnswer.copy(subtitles = a)
            "motionLevel" -> withAnswer.copy(motionLevel = a)
            "voiceStyle" -> withAnswer.copy(voiceStyle = a,
                pace = when { a.contains("energetic", true) -> "fast"; a.contains("calm", true) -> "slow"; else -> brief.pace })
            "narrationMode" -> withAnswer.copy(narrationMode = a)
            "assetPolicy" -> withAnswer.copy(assetPolicy = a)
            "avoid" -> withAnswer.copy(avoid = a)
            else -> withAnswer
        }
    }

    fun skip(brief: DirectorBrief, key: String): DirectorBrief = brief.copy(skipped = brief.skipped + key)

    /** Seeds the brief from the first user message using only what it actually says. */
    fun seed(brief: DirectorBrief, message: String): DirectorBrief {
        val mode = if (brief.workflow == DirectorWorkflow.ZERO_TO_VIDEO) classifyScript(message) else null
        val lang = detectLanguage(message)
        return brief.copy(
            prompt = if (brief.prompt.isBlank()) message else brief.prompt,
            scriptMode = mode ?: brief.scriptMode,
            userScript = if (mode == ScriptMode.FULL_SCRIPT_PRESERVE || mode == ScriptMode.PARTIAL_SCRIPT_EXPAND) message else brief.userScript,
            aspect = brief.aspect ?: inferAspect(message),
            narrationLanguage = brief.narrationLanguage ?: lang.takeIf { brief.workflow.generatesNarration }
        )
    }

    fun summary(b: DirectorBrief): String = buildString {
        appendLine("Brief — ${b.workflow.title}")
        if (b.prompt.isNotBlank()) appendLine("• Topic/Request: ${b.prompt.take(140)}${if (b.prompt.length > 140) "…" else ""}")
        b.scriptMode?.let { appendLine("• Script handling: ${it.name.lowercase().replace('_', ' ')}") }
        b.durationSec?.let { appendLine("• Duration: ${it / 60}:${(it % 60).toString().padStart(2, '0')}") }
        b.narrationLanguage?.let { appendLine("• Narration language: $it") }
        b.aspect?.let { appendLine("• Aspect: $it") }
        b.style?.let { appendLine("• Style: $it") }
        b.audience?.let { appendLine("• Audience: $it") }
        b.subtitles?.let { appendLine("• Subtitles: $it") }
        b.assetPolicy?.let { appendLine("• Visual source: $it") }
        b.avoid?.let { appendLine("• Avoid: $it") }
        b.answers[GuidedBriefOptions.VOICE]?.let { appendLine("• Narration voice: $it") }
        GuidedBriefOptions.music(b)?.let { appendLine("• Music: ${it.name} (${it.durationMs / 1000}s available, no looping)") }
    }.trim()
}
