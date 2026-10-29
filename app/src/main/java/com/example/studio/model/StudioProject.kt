package com.example.studio.model

import java.util.UUID

/**
 * Multi-lane timeline lane descriptor.
 */
data class StudioLane(
    val id: String = UUID.randomUUID().toString(),
    val trackType: TrackType,
    val name: String = trackType.displayName,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isLocked: Boolean = false,
    val heightDp: Int = 48
)

/**
 * Root project data model v2 for SAIF AI Video Studio.
 * Completely empty on initial creation — absolutely zero demo content.
 */
data class StudioProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aspect: String = "9:16", // 9:16, 16:9, 1:1, 4:5
    val fps: Int = 30,
    val lanes: List<StudioLane> = defaultEmptyLanes(),
    val items: List<TimelineItem> = emptyList(),
    val markersUs: List<Long> = emptyList(),
    val schemaVersion: Int = 2
) {
    /** Total project duration in microseconds calculated from the outermost item boundary */
    val totalDurationUs: Long
        get() = items.maxOfOrNull { it.startUs + it.durationUs } ?: 0L

    val totalDurationSeconds: Float
        get() = totalDurationUs / 1_000_000f

    companion object {
        fun defaultEmptyLanes(): List<StudioLane> = listOf(
            StudioLane(id = "lane_code_1", trackType = TrackType.CODE, name = "Code Animations"),
            StudioLane(id = "lane_video_1", trackType = TrackType.VIDEO, name = "Video / B-Roll"),
            StudioLane(id = "lane_visual_1", trackType = TrackType.VISUAL, name = "Visuals & Overlays"),
            StudioLane(id = "lane_text_1", trackType = TrackType.TEXT, name = "Text & Subtitles"),
            StudioLane(id = "lane_voice_1", trackType = TrackType.VOICE, name = "AI Voiceover"),
            StudioLane(id = "lane_audio_1", trackType = TrackType.AUDIO, name = "SFX & Audio")
        )

        /**
         * Create a brand-new, guaranteed empty project with unique ID and name.
         */
        fun createEmpty(name: String = "Untitled"): StudioProject =
            StudioProject(
                id = UUID.randomUUID().toString(),
                name = name,
                lanes = defaultEmptyLanes(),
                items = emptyList()
            )
    }
}
