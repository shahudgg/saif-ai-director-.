package com.example.studio.director

import java.util.UUID

/** The four AI workflows exposed by the SAIF AI workspace. */
enum class DirectorWorkflow(val title: String, val subtitle: String) {
    ZERO_TO_VIDEO("ZERO TO VIDEO", "Topic or script → researched, animated, narrated video"),
    VIDEO_TO_EDITING("VIDEO TO EDITING", "Analyze your footage and build a reversible edit plan (no new narration)"),
    VIDEO_EDIT_WITH_VOICE("VIDEO EDITING WITH AI VOICE", "Edit your footage and add AI narration based on what is in the video"),
    VOICE_OVER_TO_VIDEO("VOICE OVER TO VIDEO", "Your voice-over stays untouched; visuals are built around it");

    val needsSourceVideo: Boolean get() = this == VIDEO_TO_EDITING || this == VIDEO_EDIT_WITH_VOICE
    val needsSourceAudio: Boolean get() = this == VOICE_OVER_TO_VIDEO
    /** Workflows that must NEVER synthesize replacement narration. */
    val forbidsGeneratedNarration: Boolean get() = this == VIDEO_TO_EDITING || this == VOICE_OVER_TO_VIDEO
    val generatesNarration: Boolean get() = this == ZERO_TO_VIDEO || this == VIDEO_EDIT_WITH_VOICE
}

enum class ScriptMode { TOPIC_NEEDS_RESEARCH, FULL_SCRIPT_PRESERVE, PARTIAL_SCRIPT_EXPAND, BRIEF_NEEDS_CLARIFICATION }

enum class DirectorStage {
    COLLECTING_BRIEF, AWAITING_SOURCE, ANALYZING_SOURCE, RESEARCHING, WRITING_SCRIPT, AWAITING_SCRIPT_APPROVAL,
    PLANNING_SCENES, GENERATING_ANIMATIONS, FINDING_ASSETS, CHECKING_ASSETS, GENERATING_NARRATION,
    BUILDING_TIMELINE, READY_FOR_PREVIEW, FAILED
}

/** One answerable question. Presets + always a Custom option. */
data class QuestionCard(
    val key: String,
    val title: String,
    val options: List<String>,
    val optional: Boolean = true,
    val allowCustom: Boolean = true,
    val customHint: String = "Type your own answer"
) {
    companion object { const val CUSTOM = "Custom" }
}

/** Everything the director has learned about the creative brief. Never discarded between turns. */
data class DirectorBrief(
    val workflow: DirectorWorkflow,
    val prompt: String = "",
    val scriptMode: ScriptMode? = null,
    val userScript: String = "",
    val durationSec: Int? = null,
    val narrationLanguage: String? = null,
    val onScreenLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val researchLanguage: String? = null,
    val audience: String? = null,
    val aspect: String? = null,
    val style: String? = null,
    val subtitles: String? = null,
    val assetPolicy: String? = null,
    val voiceStyle: String? = null,
    val pace: String? = null,
    val motionLevel: String? = null,
    val narrationMode: String? = null,
    val avoid: String? = null,
    val sourceUri: String? = null,
    val skipped: Set<String> = emptySet(),
    val answers: Map<String, String> = emptyMap()
) {
    fun answered(key: String): Boolean = answers.containsKey(key) || skipped.contains(key)
}

data class ChatTurn(
    val id: String = UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
    val card: QuestionCard? = null,
    val cardAnswered: String? = null,
    val isError: Boolean = false
)

/** A real job status. [fraction] is null when progress cannot be computed honestly. */
data class JobStatus(
    val stage: DirectorStage,
    val label: String,
    val fraction: Float? = null,
    val detail: String = "",
    val failed: Boolean = false,
    val retryable: Boolean = false
)
