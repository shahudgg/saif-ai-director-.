package com.example.studio.model

/**
 * Microsecond-precision timeline item model for SAIF AI Video Studio.
 * All time calculations are based on Long microseconds (Us) to prevent float drift.
 */
sealed interface TimelineItem {
    val id: String
    val laneId: String
    val trackType: TrackType
    val startUs: Long
    val durationUs: Long
    val transform: ItemTransform
    val effects: ItemEffects
    val animIn: ItemAnimation?
    val animOut: ItemAnimation?
    val keyframes: List<TransformKeyframe>
    val locked: Boolean
    val label: String
    val origin: String // "manual" or "ai"
    val aiWhy: String?

    fun copyWithTiming(newStartUs: Long, newDurationUs: Long): TimelineItem
    fun copyWithLane(newLaneId: String): TimelineItem
    fun copyWithTransform(newTransform: ItemTransform): TimelineItem
    fun copyWithEffects(newEffects: ItemEffects): TimelineItem
    fun copyWithKeyframes(newKeyframes: List<TransformKeyframe>): TimelineItem
}

enum class TrackType(val displayName: String, val colorHex: Long) {
    CODE("Code Animation", 0xFF8B5CF6),       // Violet
    VIDEO("Video / B-Roll", 0xFF3B82F6),       // Blue
    VISUAL("Visuals & Stickers", 0xFF06B6D4),  // Cyan
    TEXT("Text & Subtitles", 0xFFF59E0B),      // Amber
    VOICE("Voiceover", 0xFF10B981),            // Green
    AUDIO("SFX & BGM", 0xFFEC4899)             // Pink
}

data class TransformKeyframe(
    val timeUs: Long,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f
)

data class ItemTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val blendMode: String = "Normal" // Normal, Screen, Multiply, Overlay, Add, Lighten, Darken
)

data class ItemEffects(
    val neonGlow: Float = 0f,
    val neonColorHex: Long = 0xFF00E5FF,
    val dropShadow: Float = 0f,
    val blur: Float = 0f,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val vignette: Float = 0f,
    val chromaKey: Boolean = false
)

data class ItemAnimation(
    val type: String,
    val durationUs: Long = 300_000L,
    val easing: String = "easeOutBack"
)

data class VideoItem(
    override val id: String,
    override val laneId: String,
    override val startUs: Long,
    override val durationUs: Long,
    val mediaUri: String,
    val sourceInUs: Long = 0L,
    val sourceOutUs: Long = durationUs,
    val naturalWidth: Int = 1080,
    val naturalHeight: Int = 1920,
    val naturalFps: Float = 30f,
    val playbackSpeed: Float = 1.0f,
    val speedRampCurve: String = "Linear",
    val volume: Float = 1.0f,
    val transitionIn: String = "None",
    val transitionDurationUs: Long = 300_000L,
    override val transform: ItemTransform = ItemTransform(),
    override val effects: ItemEffects = ItemEffects(),
    override val animIn: ItemAnimation? = null,
    override val animOut: ItemAnimation? = null,
    override val keyframes: List<TransformKeyframe> = emptyList(),
    override val locked: Boolean = false,
    override val label: String = "Video Clip",
    override val origin: String = "manual",
    override val aiWhy: String? = null
) : TimelineItem {
    override val trackType: TrackType = TrackType.VIDEO
    override fun copyWithTiming(newStartUs: Long, newDurationUs: Long): VideoItem =
        copy(startUs = newStartUs, durationUs = newDurationUs)
    override fun copyWithLane(newLaneId: String): VideoItem = copy(laneId = newLaneId)
    override fun copyWithTransform(newTransform: ItemTransform): VideoItem = copy(transform = newTransform)
    override fun copyWithEffects(newEffects: ItemEffects): VideoItem = copy(effects = newEffects)
    override fun copyWithKeyframes(newKeyframes: List<TransformKeyframe>): VideoItem = copy(keyframes = newKeyframes)
}

data class ImageItem(
    override val id: String,
    override val laneId: String,
    override val startUs: Long,
    override val durationUs: Long,
    val mediaUri: String,
    val transitionIn: String = "None",
    val transitionDurationUs: Long = 300_000L,
    override val transform: ItemTransform = ItemTransform(),
    override val effects: ItemEffects = ItemEffects(),
    override val animIn: ItemAnimation? = null,
    override val animOut: ItemAnimation? = null,
    override val keyframes: List<TransformKeyframe> = emptyList(),
    override val locked: Boolean = false,
    override val label: String = "Image",
    override val origin: String = "manual",
    override val aiWhy: String? = null
) : TimelineItem {
    override val trackType: TrackType = TrackType.VISUAL
    override fun copyWithTiming(newStartUs: Long, newDurationUs: Long): ImageItem =
        copy(startUs = newStartUs, durationUs = newDurationUs)
    override fun copyWithLane(newLaneId: String): ImageItem = copy(laneId = newLaneId)
    override fun copyWithTransform(newTransform: ItemTransform): ImageItem = copy(transform = newTransform)
    override fun copyWithEffects(newEffects: ItemEffects): ImageItem = copy(effects = newEffects)
    override fun copyWithKeyframes(newKeyframes: List<TransformKeyframe>): ImageItem = copy(keyframes = newKeyframes)
}

data class TextItem(
    override val id: String,
    override val laneId: String,
    override val startUs: Long,
    override val durationUs: Long,
    val text: String,
    val fontStyle: String = "sans-serif",
    val fontSizeSp: Float = 24f,
    val textColorHex: Long = 0xFFFFFFFF,
    val outlineColorHex: Long = 0xFF000000,
    val outlineWidth: Float = 2f,
    val isBold: Boolean = true,
    val hasBackgroundBox: Boolean = true,
    val boxColorHex: Long = 0x88000000,
    val isKaraoke: Boolean = false,
    val stylePreset: String = "Hormozi Neon",
    override val transform: ItemTransform = ItemTransform(),
    override val effects: ItemEffects = ItemEffects(),
    override val animIn: ItemAnimation? = null,
    override val animOut: ItemAnimation? = null,
    override val keyframes: List<TransformKeyframe> = emptyList(),
    override val locked: Boolean = false,
    override val label: String = text.take(16),
    override val origin: String = "manual",
    override val aiWhy: String? = null
) : TimelineItem {
    override val trackType: TrackType = TrackType.TEXT
    override fun copyWithTiming(newStartUs: Long, newDurationUs: Long): TextItem =
        copy(startUs = newStartUs, durationUs = newDurationUs)
    override fun copyWithLane(newLaneId: String): TextItem = copy(laneId = newLaneId)
    override fun copyWithTransform(newTransform: ItemTransform): TextItem = copy(transform = newTransform)
    override fun copyWithEffects(newEffects: ItemEffects): TextItem = copy(effects = newEffects)
    override fun copyWithKeyframes(newKeyframes: List<TransformKeyframe>): TextItem = copy(keyframes = newKeyframes)
}

data class VoiceItem(
    override val id: String,
    override val laneId: String,
    override val startUs: Long,
    override val durationUs: Long,
    val audioUri: String? = null,
    val text: String = "",
    val speakerTone: String = "Energetic",
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val audioDucking: Boolean = false,
    override val transform: ItemTransform = ItemTransform(),
    override val effects: ItemEffects = ItemEffects(),
    override val animIn: ItemAnimation? = null,
    override val animOut: ItemAnimation? = null,
    override val keyframes: List<TransformKeyframe> = emptyList(),
    override val locked: Boolean = false,
    override val label: String = if (text.isNotBlank()) text.take(16) else "Voiceover",
    override val origin: String = "ai",
    override val aiWhy: String? = null
) : TimelineItem {
    override val trackType: TrackType = TrackType.VOICE
    override fun copyWithTiming(newStartUs: Long, newDurationUs: Long): VoiceItem =
        copy(startUs = newStartUs, durationUs = newDurationUs)
    override fun copyWithLane(newLaneId: String): VoiceItem = copy(laneId = newLaneId)
    override fun copyWithTransform(newTransform: ItemTransform): VoiceItem = copy(transform = newTransform)
    override fun copyWithEffects(newEffects: ItemEffects): VoiceItem = copy(effects = newEffects)
    override fun copyWithKeyframes(newKeyframes: List<TransformKeyframe>): VoiceItem = copy(keyframes = newKeyframes)
}

data class AudioItem(
    override val id: String,
    override val laneId: String,
    override val startUs: Long,
    override val durationUs: Long,
    val audioUri: String? = null,
    val isSfx: Boolean = true,
    val volume: Float = 0.8f,
    val speed: Float = 1.0f,
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
    val audioDucking: Boolean = false,
    val duckingAmount: Float = 0.35f,
    override val transform: ItemTransform = ItemTransform(),
    override val effects: ItemEffects = ItemEffects(),
    override val animIn: ItemAnimation? = null,
    override val animOut: ItemAnimation? = null,
    override val keyframes: List<TransformKeyframe> = emptyList(),
    override val locked: Boolean = false,
    override val label: String = if (isSfx) "SFX" else "BGM Track",
    override val origin: String = "manual",
    override val aiWhy: String? = null
) : TimelineItem {
    override val trackType: TrackType = TrackType.AUDIO
    override fun copyWithTiming(newStartUs: Long, newDurationUs: Long): AudioItem =
        copy(startUs = newStartUs, durationUs = newDurationUs)
    override fun copyWithLane(newLaneId: String): AudioItem = copy(laneId = newLaneId)
    override fun copyWithTransform(newTransform: ItemTransform): AudioItem = copy(transform = newTransform)
    override fun copyWithEffects(newEffects: ItemEffects): AudioItem = copy(effects = newEffects)
    override fun copyWithKeyframes(newKeyframes: List<TransformKeyframe>): AudioItem = copy(keyframes = newKeyframes)
}

data class CodeSceneItem(
    override val id: String,
    override val laneId: String,
    override val startUs: Long,
    override val durationUs: Long,
    val title: String,
    val htmlCode: String = "",
    val animationType: String = "GSAP Motion Graphics",
    val isOverlay: Boolean = false,
    val proxyVideoPath: String? = null,
    override val transform: ItemTransform = ItemTransform(),
    override val effects: ItemEffects = ItemEffects(),
    override val animIn: ItemAnimation? = null,
    override val animOut: ItemAnimation? = null,
    override val keyframes: List<TransformKeyframe> = emptyList(),
    override val locked: Boolean = false,
    override val label: String = title,
    override val origin: String = "ai",
    override val aiWhy: String? = null
) : TimelineItem {
    override val trackType: TrackType = TrackType.CODE
    override fun copyWithTiming(newStartUs: Long, newDurationUs: Long): CodeSceneItem =
        copy(startUs = newStartUs, durationUs = newDurationUs)
    override fun copyWithLane(newLaneId: String): CodeSceneItem = copy(laneId = newLaneId)
    override fun copyWithTransform(newTransform: ItemTransform): CodeSceneItem = copy(transform = newTransform)
    override fun copyWithEffects(newEffects: ItemEffects): CodeSceneItem = copy(effects = newEffects)
    override fun copyWithKeyframes(newKeyframes: List<TransformKeyframe>): CodeSceneItem = copy(keyframes = newKeyframes)
}
