package com.example.studio.director

/** Settings carried with one creative brief; they never change app-wide voice settings. */
data class BackgroundMusic(val uri: String, val name: String, val durationMs: Long, val volume: Float = 0.2f)

object GuidedBriefOptions {
    const val VOICE = "narrationVoiceId"
    const val MUSIC_URI = "musicUri"
    const val MUSIC_NAME = "musicName"
    const val MUSIC_DURATION = "musicDurationMs"
    const val MUSIC_VOLUME = "musicVolume"
    val METADATA_KEYS = setOf(VOICE, MUSIC_URI, MUSIC_NAME, MUSIC_DURATION, MUSIC_VOLUME, "styleSeed", "styleSignature")

    fun withVoice(brief: DirectorBrief, voiceId: String?): DirectorBrief = brief.copy(
        answers = if (voiceId.isNullOrBlank()) brief.answers - VOICE else brief.answers + (VOICE to voiceId)
    )

    fun withMusic(brief: DirectorBrief, music: BackgroundMusic?): DirectorBrief {
        val cleared = brief.answers - setOf(MUSIC_URI, MUSIC_NAME, MUSIC_DURATION, MUSIC_VOLUME)
        if (music == null) return brief.copy(answers = cleared)
        require(music.uri.isNotBlank() && music.durationMs > 0) { "Music must have a readable source and duration" }
        return brief.copy(answers = cleared + mapOf(
            MUSIC_URI to music.uri, MUSIC_NAME to music.name.take(120), MUSIC_DURATION to music.durationMs.toString(),
            MUSIC_VOLUME to safeVolume(music.volume).toString()
        ))
    }

    fun music(brief: DirectorBrief): BackgroundMusic? {
        val uri = brief.answers[MUSIC_URI]?.takeIf { it.isNotBlank() } ?: return null
        val duration = brief.answers[MUSIC_DURATION]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val volume = brief.answers[MUSIC_VOLUME]?.toFloatOrNull()?.let(::safeVolume) ?: 0.2f
        return BackgroundMusic(uri, brief.answers[MUSIC_NAME]?.ifBlank { "Selected music" } ?: "Selected music", duration, volume)
    }

    fun safeVolume(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.2f
}
