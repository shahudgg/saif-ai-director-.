package com.example.ui.components

import android.net.Uri

/**
 * Represents a media clip item on the timeline (Video or Photo).
 */
data class TimelineMediaClip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Media Clip",
    val uri: Uri? = null,
    val durationSeconds: Float = 6.0f,
    val isAiEnhanced: Boolean = false,
    val isPhoto: Boolean = false,
    val trackIndex: Int = 0,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val rotation: Float = 0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val isFlippedHorizontally: Boolean = false,
    val transitionIn: String = "None",
    val transitionDuration: Float = 1.0f, // Level 4
    val speedRampCurve: String = "Linear", // Level 4
    val filterName: String = "Original",   // Level 3
    val filterIntensity: Float = 0.8f,     // Level 3
    val keyframes: List<VideoKeyframe> = emptyList(), // Level 4
    val origin: String = "manual", // "manual" or "ai" (Level 16 Timeline Memory)
    val aiWhy: String = "",        // Plain-language reason for AI edit (Level 13)
    val inSeconds: Float = 0f,
    val outSeconds: Float = 6.0f,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val blendMode: String = "Normal", // Normal, Screen, Multiply, Overlay, Add
    val neonGlow: Float = 0f,         // 0 to 100
    val dropShadow: Float = 0f,       // 0 to 50
    val blurRadius: Float = 0f,       // 0 to 25
    val brightness: Float = 0f,       // -1.0 to 1.0
    val contrast: Float = 1.0f,       // 0.5 to 2.0
    val saturation: Float = 1.0f,     // 0.0 to 2.0
    val inAnimation: String = "Fade In", // Pop-in, Bounce, Slide, Fade In, 3D Flip
    val outAnimation: String = "Fade Out",
    val lastModified: Long = System.currentTimeMillis()
)

data class EditOperation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val op: String, // "trim", "speed", "crop", "colorAdjust", "chromaKey", "stabilize", "reverse", "volume", "autoDuck"
    val clipId: String,
    val startSeconds: Float? = null,
    val endSeconds: Float? = null,
    val params: Map<String, String> = emptyMap(),
    val why: String = ""
)

data class EditOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "text", "sticker", "broll", "effect", "filter", "sfx"
    val startSeconds: Float = 0f,
    val durationSeconds: Float = 4f,
    val props: Map<String, String> = emptyMap(),
    val why: String = ""
)

data class VideoEditPlan(
    val summary: String,
    val targetClips: List<String> = emptyList(),
    val operations: List<EditOperation> = emptyList(),
    val overlays: List<EditOverlay> = emptyList(),
    val musicMood: String? = null,
    val captions: Boolean = false,
    val why: String = ""
)

data class VideoScriptSection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val kind: String = "point", // "hook", "story", "point", "transition", "cta", "outro"
    val narration: String,
    val emotion: String = "excited", // "excited", "calm", "serious", "curious", "warm", "urgent"
    val estSeconds: Float = 6.0f,
    val visualHint: String = ""
)

data class VideoScript(
    val title: String,
    val targetDurationSeconds: Float = 30f,
    val tone: String = "Energetic",
    val language: String = "Hindi & English",
    val sections: List<VideoScriptSection> = emptyList()
)

data class VideoCaptionItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startSeconds: Float,
    val endSeconds: Float,
    val text: String,
    val highlightWordIndex: Int = 0,
    val words: List<String> = emptyList()
)

data class VideoBRollItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startSeconds: Float,
    val durationSeconds: Float,
    val prompt: String,
    val title: String,
    val imageUri: String? = null
)

data class TimelineMarkerItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampSeconds: Float,
    val label: String,
    val note: String = "",
    val isFeedback: Boolean = false
)

/**
 * Data structure holding the state of a video editing project.
 */
data class VideoProjectData(
    val projectName: String = "Untitled Video Project",
    val title: String = projectName,
    val resolution: String = "1080P",
    val fps: String = "60FPS",
    val totalDurationSeconds: Float = 0.0f,
    val currentPositionSeconds: Float = 0.0f,
    val clips: List<TimelineMediaClip> = emptyList(),
    val audioTrackName: String = "",
    val textOverlay: String = "",
    val effectOverlay: String = "",
    val selectedClipIndex: Int = 0,
    val aspectRatio: String = "9:16",
    val activeFilter: String = "Original",
    val filterIntensity: Float = 0.8f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val audioVolume: Float = 1.0f,
    val isStabilized: Boolean = false,
    val isChromaKeyEnabled: Boolean = false,
    val activeWatermark: String = "",
    val activeTransition: String = "Dissolve",
    val textColorHex: String = "#00DAF3",
    val textFontFamily: String = "Monospace",
    val textAnimation: String = "Glitch Fade",
    val overlayMediaUri: String? = null,
    val overlayOpacity: Float = 1.0f,
    val activePipPosition: String = "None",
    val script: VideoScript? = null,
    val activeEditPlan: VideoEditPlan? = null,
    val aiEditsOnlyFilter: Boolean = false,
    val audioDuckAmount: Float = 0.25f,
    val autoCaptions: List<VideoCaptionItem> = emptyList(),
    val bRollItems: List<VideoBRollItem> = emptyList(),
    val timelineMarkers: List<TimelineMarkerItem> = emptyList(),
    val selectedLanguage: String = "Hindi & English",
    val isProxyModeEnabled: Boolean = false,
    val timelineZoom: Float = 1.0f,
    val selectedTrackNumber: Int = 1,
    val selectedCardId: String = "",
    val codeTracks: List<CodeAnimationTrackItem> = emptyList(),
    val visualAssetTracks: List<VisualAssetTrackItem> = emptyList(),
    val textSubtitleTracks: List<TextSubtitleTrackItem> = emptyList(),
    val voiceoverTracks: List<VoiceoverTrackItem> = emptyList(),
    val soundAudioTracks: List<SoundAudioTrackItem> = emptyList(),
    /** Permanent unique id (UUID). Never derived from the name. */
    val projectId: String = java.util.UUID.randomUUID().toString()
) {
    fun getResolvedCodeTracks(): List<CodeAnimationTrackItem> = codeTracks
    fun getResolvedVisualAssets(): List<VisualAssetTrackItem> = visualAssetTracks
    fun getResolvedTextSubtitles(): List<TextSubtitleTrackItem> = textSubtitleTracks
    fun getResolvedVoiceoverTracks(): List<VoiceoverTrackItem> = voiceoverTracks
    fun getResolvedSoundAudioTracks(): List<SoundAudioTrackItem> = soundAudioTracks
}

/**
 * Track 1: Code Animation Item (HTML5/CSS3/GSAP Motion Graphics)
 */
data class CodeAnimationTrackItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val startSeconds: Float,
    val durationSeconds: Float,
    val htmlCode: String = "",
    val animationType: String = "3D Camera Perspective",
    val isProxyCached: Boolean = false,
    val scale: Float = 1.0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val neonGlow: Float = 0f,
    val inAnimation: String = "Pop-in",
    val outAnimation: String = "Fade Out"
)

/**
 * Track 3: Visual Asset Item (Overlays, Stickers, Telegram & Local Media)
 */
data class VisualAssetTrackItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val startSeconds: Float,
    val durationSeconds: Float,
    val assetType: String = "telegram_graphic",
    val imageUri: String? = null,
    val telegramFileId: String? = null,
    val scale: Float = 1.0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val blendMode: String = "Normal",
    val neonGlow: Float = 0f,
    val dropShadow: Float = 0f,
    val blurRadius: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val inAnimation: String = "Pop-in",
    val outAnimation: String = "Fade Out"
)

/**
 * Track 4: Text Subtitle Item (Kinetic Subtitles, Title Overlays)
 */
data class TextSubtitleTrackItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val startSeconds: Float = 0f,
    val durationSeconds: Float = 3.0f,
    val style: String = "Hormozi Neon",
    val fontFamily: String = "Montserrat",
    val fontSize: Float = 22f,
    val colorHex: String = "#00DAF3",
    val textColorHex: String = colorHex,
    val strokeColorHex: String = "#000000",
    val strokeWidth: Float = 2f,
    val hasOutline: Boolean = true,
    val neonGlow: Float = 40f,
    val hasBackgroundBox: Boolean = false,
    val backgroundBoxColorHex: String = "#80000000",
    val boxColorHex: String = backgroundBoxColorHex,
    val animation: String = "Hormozi Word-by-Word",
    val animationPreset: String = animation,
    val scale: Float = 1.0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val rotation: Float = 0f
)

/**
 * Track 5: AI Voiceover Item (TTS Spoken Sentences)
 */
data class VoiceoverTrackItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val narration: String,
    val startSeconds: Float = 0f,
    val durationSeconds: Float = 4.0f,
    val speakerTone: String = "Energetic",
    val audioUri: String? = null,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val isDuckingEnabled: Boolean = true,
    val audioDucking: Boolean = isDuckingEnabled
)

/**
 * Track 6: Sound Track Item (Sound Effects & Background Music)
 */
data class SoundAudioTrackItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val startSeconds: Float = 0f,
    val durationSeconds: Float = 5.0f,
    val isSfx: Boolean = false,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val isDuckingEnabled: Boolean = false,
    val audioDucking: Boolean = isDuckingEnabled,
    val audioUri: String? = null
)

/**
 * Level 4: Keyframe Animation point for spatial and temporal interpolation
 */
data class VideoKeyframe(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timeOffsetSeconds: Float,
    val scale: Float = 1.0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val easing: String = "EaseInOut" // Linear, EaseIn, EaseOut, EaseInOut, BezierCubic, Elastic
)

/**
 * Level 4: Speed Ramp Curve Preset definition with cubic bezier velocity points
 */
data class SpeedRampPreset(
    val id: String,
    val name: String,
    val description: String,
    val points: List<Pair<Float, Float>>, // normalized (t, speedMultiplier)
    val defaultDurationMultiplier: Float = 1.0f
)

/**
 * Level 4: Cinematic Transition item
 */
data class CinematicTransition(
    val id: String,
    val name: String,
    val category: String, // "Whip", "Glitch", "Dissolve", "Warp", "Light", "3D"
    val durationSeconds: Float = 1.0f,
    val intensity: Float = 1.0f,
    val curve: String = "CubicEaseInOut"
)

/**
 * Toolbar tools tab selector
 */
enum class VideoStudioToolTab {
    NONE,
    TUNE,
    EDIT,
    AUDIO,
    TEXT,
    OVERLAY,
    EFFECTS,
    FILTERS,
    ADJUST,
    RATIO,
    PRO_TOOLS
}

data class EffectItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val previewColor: Long,
    val isAi: Boolean = false
)

data class FilterItem(
    val id: String,
    val name: String,
    val tone: String,
    val tintColor: Long
)

data class AudioTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val duration: String,
    val bpm: Int
)

data class ProFeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val badge: String
)

object VideoEditorPresets {
    val ALL_EFFECTS = listOf(
        EffectItem("fx_flare", "Anamorphic Flare", "Cinematic Lens", "Cinematic horizontal blue light streak", 0xFF00DAF3, isAi = true),
        EffectItem("fx_neon", "Cyberpunk Neon Glow", "Trending", "Electric neon edge pulse with cyan/magenta", 0xFF7B2BFF, isAi = true),
        EffectItem("fx_vhs", "VHS Retro 1998", "Atmospheric / Retro", "Nostalgic analog tape tracking distortion", 0xFFFFC44D),
        EffectItem("fx_rgb", "RGB Chromatic Split", "Glitch & Sci-Fi", "Red, green and blue channel aberration shift", 0xFFFF4D6D, isAi = true),
        EffectItem("fx_grain", "Cinema 35mm Grain", "Atmospheric / Retro", "Organic film stock silver halide grain", 0xFFCCC3D9),
        EffectItem("fx_matrix", "Matrix Digital Rain", "Glitch & Sci-Fi", "Hacker cyber green falling glyphs", 0xFF35E6A0, isAi = true),
        EffectItem("fx_leak", "Golden Lens Leak", "Cinematic Lens", "Warm solar flare light wash on edges", 0xFFFFB703),
        EffectItem("fx_holo", "Hologram Scanlines", "Glitch & Sci-Fi", "Futuristic sci-fi HUD scanlines & flicker", 0xFF00B4D8, isAi = true),
        EffectItem("fx_zoom", "Dynamic Zoom Blur", "Trending", "Action radial zoom blur during motion", 0xFFE040FB),
        EffectItem("fx_slowmo", "Slow Mo Optical Flow", "AI Distortion", "Super silky frame-blended slow motion", 0xFF7C4DFF, isAi = true),
        EffectItem("fx_strobe", "Strobe Flash Party", "Trending", "Rhythmic white strobe pulse sync", 0xFFFFFFFF),
        EffectItem("fx_bokeh", "Floating Dust Bokeh", "Atmospheric / Retro", "Organic sunlit floating dust motes", 0xFFFFE082),
        EffectItem("fx_edge", "Edge Glow Neon", "Light & Glow", "High contrast luminescence edge highlight", 0xFF00E676),
        EffectItem("fx_thermal", "Thermal Heatmap FX", "AI Distortion", "Infrared predator heat vision false color", 0xFFFF3D00, isAi = true),
        EffectItem("fx_mirror", "Inception Mirror", "Glitch & Sci-Fi", "Kaleidoscopic quad reflection symmetry", 0xFF448AFF),
        EffectItem("fx_pixel", "Pixelate 8-Bit Retro", "Atmospheric / Retro", "Arcade gaming low-fi mosaic block grid", 0xFF69F0AE),
        EffectItem("fx_lightning", "Lightning Sparks", "Light & Glow", "Electric arc discharge bolts over footage", 0xFFFFFF00, isAi = true),
        EffectItem("fx_rain", "Raindrops On Lens", "Atmospheric / Retro", "Dynamic moisture droplets & glass refraction", 0xFF80D8FF),
        EffectItem("fx_comic", "Halftone Pop Comic", "Atmospheric / Retro", "Marvel vintage printed comic dot texture", 0xFFFF5252),
        EffectItem("fx_shake", "Shaky Handheld Cam", "Trending", "Realistic action camera tremor & recoil", 0xFFB0BEC5),
        EffectItem("fx_dream", "Dreamy Soft Glow", "Light & Glow", "Romantic diffused mist bloom highlights", 0xFFF48FB1),
        EffectItem("fx_vignette", "Dark Cinema Vignette", "Cinematic Lens", "Heavy letterbox edge falloff focus", 0xFF37474F),
        EffectItem("fx_prism", "Prism Refraction", "Cinematic Lens", "Split crystal rainbow dispersion", 0xFFB388FF),
        EffectItem("fx_blackhole", "Black Hole Warp", "AI Distortion", "Gravitational center spacetime pull", 0xFFD500F9, isAi = true),
        EffectItem("fx_scanlines", "Glitch Scanlines", "Glitch & Sci-Fi", "Digital corrupted data glitch burst", 0xFF00E5FF, isAi = true)
    )

    val ALL_FILTERS = listOf(
        FilterItem("flt_orig", "Original", "Natural Standard", 0xFFFFFFFF),
        FilterItem("flt_teal_orange", "Teal & Orange", "Hollywood Blockbuster", 0xFFFF8C42),
        FilterItem("flt_cyber", "Cyberpunk Night", "Vibrant Neon Blue & Pink", 0xFF00F5D4),
        FilterItem("flt_kodak", "Kodak 500T", "Vintage 35mm Film", 0xFFFFD166),
        FilterItem("flt_sunset", "Golden Hour", "Warm Sunset Glow", 0xFFFFB703),
        FilterItem("flt_noir", "Moody Noir", "High Contrast B&W", 0xFF8D99AE),
        FilterItem("flt_pastel", "Pastel Dream", "Anime Aesthetic Soft", 0xFFFFC6FF),
        FilterItem("flt_emerald", "Forest Emerald", "Deep Natural Greens", 0xFF06D6A0),
        FilterItem("flt_bleach", "Bleach Bypass", "Desaturated Gritty Look", 0xFFB7B7A4),
        FilterItem("flt_sepia", "Vintage 70s", "Warm Faded Nostalgia", 0xFFDDA15E)
    )

    val ALL_AUDIO_TRACKS = listOf(
        AudioTrackItem("aud_1", "Cyber_Synthwave.wav", "Neon Syndicate", "Synthwave", "02:45", 128),
        AudioTrackItem("aud_2", "Tokyo_Midnight_Drift.mp3", "Kowloon Sound", "Phonk / Drift", "02:18", 140),
        AudioTrackItem("aud_3", "Cinematic_Orchestra_808.wav", "Hans Cyber", "Cinematic Epic", "03:12", 95),
        AudioTrackItem("aud_4", "LoFi_Rainy_Afternoon.mp3", "Study Chills", "Lo-Fi Beats", "01:54", 84),
        AudioTrackItem("aud_5", "Hyperdrive_Bass_Boost.wav", "Overclocked", "EDM Bass", "02:30", 150),
        AudioTrackItem("aud_6", "Retro_80s_Arp.wav", "Analog Dreams", "Retro Wave", "02:05", 120),
        AudioTrackItem("aud_7", "Dark_Cyber_Club.mp3", "Underground", "Industrial", "03:40", 132),
        AudioTrackItem("aud_8", "Acoustic_Sunrise.mp3", "Morning Light", "Acoustic Ambient", "02:22", 100)
    )

    val ALL_PRO_FEATURES = listOf(
        ProFeatureItem("pro_captions", "AI Auto-Captions", "Generates animated synchronized subtitles using automatic speech-to-text with karaoke word highlights.", "AI Intelligence", "AI REALTIME"),
        ProFeatureItem("pro_chroma", "Chroma Key / Green Screen", "Ultra-clean background subtraction with spill suppression and backdrop replacement.", "VFX / Compositing", "PRO STUDIO"),
        ProFeatureItem("pro_stabilize", "AI Video Stabilizer", "Multi-axis gyroscopic stabilization algorithm removing handheld camera jitter.", "AI Intelligence", "GYRO SMOOTH"),
        ProFeatureItem("pro_speedramp", "Speed Ramp Curve Studio", "Dynamic bezier velocity curves (Hero Ramp, Bullet Time, Flash-Cut, Montage).", "Motion & Timing", "CURVE PRO"),
        ProFeatureItem("pro_beatsync", "AI Beat Detection Sync", "Audio transient spectral analyzer that auto-places cut marks on musical beat drops.", "Audio & Music", "AI AUDIO"),
        ProFeatureItem("pro_voicechanger", "AI Voice Changer & De-Noise", "Transforms audio into Studio Mic, Deep Cinema Voice, Cyber Cyborg, or removes background hum.", "Audio & Music", "NEURAL DSP"),
        ProFeatureItem("pro_keyframes", "Keyframe Animation Engine", "Full keyframing curve for Scale, Position, Rotation, and Opacity over time.", "Motion & Timing", "VFX TIMELINE"),
        ProFeatureItem("pro_motiontrack", "Motion Point Tracker", "AI tracks face, object or vehicle to anchor text, arrows or stickers in 3D space.", "AI Intelligence", "3D TRACKER"),
        ProFeatureItem("pro_transitions", "Cinematic Transitions (16+)", "Whip Pan, Zoom Glitch, Cross Dissolve, Light Flash, Spin Warp, Page Curl.", "VFX / Compositing", "CINEMA PACK"),
        ProFeatureItem("pro_opticalflow", "Optical Flow Slow Motion", "AI motion vector frame synthesis for artifact-free 0.1x super slow motion.", "Motion & Timing", "AI FRAMES"),
        ProFeatureItem("pro_rgbcurves", "RGB Color Curves Studio", "Pro-grade Master, Red, Green, and Blue spline curves for Hollywood color grading.", "Color & Grading", "PRO COLOR"),
        ProFeatureItem("pro_scenecut", "AI Scene Cut Detection", "Automated shot boundary detection splitting raw footage into separate takes.", "AI Intelligence", "AUTO CUT"),
        ProFeatureItem("pro_ducking", "Smart Audio Ducking", "Intelligently lowers background soundtrack volume whenever speech or voiceover is detected.", "Audio & Music", "SMART DUCK"),
        ProFeatureItem("pro_luts", "Custom 3D LUT Import", "Loads industry-standard Hollywood .cube LUT color look profiles.", "Color & Grading", "3D LUT"),
        ProFeatureItem("pro_hdr", "HDR10+ / Dolby Color", "Expands color reproduction to 10-bit Rec.2020 wide color gamut.", "Color & Grading", "10-BIT HDR"),
        ProFeatureItem("pro_glowoutline", "AI Silhouette Outline Glow", "Segments actor from background with animated neon electric contour stroke.", "AI Intelligence", "BODY AI"),
        ProFeatureItem("pro_watermark", "Dynamic Watermark Engine", "Custom transparent creator watermark with opacity and position anchors.", "Branding", "SIGNATURE"),
        ProFeatureItem("pro_stickers", "Animated Stickers & HUD", "30+ animated stickers, emojis, cyber arrows, and futuristic HUD graphics.", "Graphics & Overlay", "30+ PACK"),
        ProFeatureItem("pro_eq", "10-Band Audio Equalizer", "Precision frequency parametric EQ with Bass Booster, Vocal Enhance, and Treble presets.", "Audio & Music", "10-BAND EQ"),
        ProFeatureItem("pro_storyboard", "Script to Storyboard AI", "Transforms written ideas into paced scene edits and rhythm guidelines.", "AI Intelligence", "SCRIPT AI"),
        ProFeatureItem("pro_motionblur", "Cinematic Motion Blur", "180-degree shutter angle synthesis giving cinematic smoothness to fast pans.", "VFX / Compositing", "SHUTTER 180°"),
        ProFeatureItem("pro_freeze", "Freeze Frame & Action Climax", "Instant 2-second impact freeze frame with dramatic zoom accent.", "Motion & Timing", "CLIMAX FX")
    )

    val ALL_SPEED_RAMP_PRESETS = listOf(
        SpeedRampPreset(
            id = "ramp_hero",
            name = "Hero Moment",
            description = "Fast in, dramatic slow-mo center, fast punch out",
            points = listOf(0.0f to 2.0f, 0.3f to 0.35f, 0.7f to 0.35f, 1.0f to 2.5f)
        ),
        SpeedRampPreset(
            id = "ramp_bullet",
            name = "Bullet Time",
            description = "Real-time to ultra-slow matrix freeze to snap recovery",
            points = listOf(0.0f to 1.0f, 0.4f to 0.15f, 0.6f to 0.15f, 1.0f to 1.5f)
        ),
        SpeedRampPreset(
            id = "ramp_flash_in",
            name = "Flash In",
            description = "Explosive 3x intro decelerating to 1x normal speed",
            points = listOf(0.0f to 3.0f, 0.25f to 2.0f, 0.5f to 1.0f, 1.0f to 1.0f)
        ),
        SpeedRampPreset(
            id = "ramp_flash_out",
            name = "Flash Out",
            description = "Smooth 1x flow accelerating to 3x hyperdrive cut",
            points = listOf(0.0f to 1.0f, 0.5f to 1.0f, 0.75f to 2.2f, 1.0f to 3.5f)
        ),
        SpeedRampPreset(
            id = "ramp_montage",
            name = "Montage Rhythm",
            description = "Alternating pacing for beat-synced travel & action",
            points = listOf(0.0f to 1.6f, 0.3f to 0.6f, 0.6f to 1.8f, 1.0f to 1.0f)
        ),
        SpeedRampPreset(
            id = "ramp_linear",
            name = "Linear Flow",
            description = "Constant standard real-time 1.0x playback",
            points = listOf(0.0f to 1.0f, 1.0f to 1.0f)
        )
    )

    val ALL_CINEMATIC_TRANSITIONS = listOf(
        CinematicTransition(
            id = "trans_whip",
            name = "Whip Pan Rush",
            category = "Whip",
            durationSeconds = 0.6f,
            intensity = 1.0f,
            curve = "CubicEaseInOut"
        ),
        CinematicTransition(
            id = "trans_glitch",
            name = "Cyberpunk Glitch",
            category = "Glitch",
            durationSeconds = 0.5f,
            intensity = 1.2f,
            curve = "Elastic"
        ),
        CinematicTransition(
            id = "trans_dissolve",
            name = "Cross Dissolve",
            category = "Dissolve",
            durationSeconds = 0.8f,
            intensity = 1.0f,
            curve = "Linear"
        ),
        CinematicTransition(
            id = "trans_warp",
            name = "Spacetime Warp",
            category = "Warp",
            durationSeconds = 0.7f,
            intensity = 1.0f,
            curve = "EaseInOutQuad"
        ),
        CinematicTransition(
            id = "trans_light",
            name = "Solar Light Leak",
            category = "Light",
            durationSeconds = 0.6f,
            intensity = 1.5f,
            curve = "EaseOutCubic"
        ),
        CinematicTransition(
            id = "trans_3d",
            name = "3D Dimension Flip",
            category = "3D",
            durationSeconds = 0.9f,
            intensity = 1.0f,
            curve = "CubicEaseInOut"
        )
    )
}
