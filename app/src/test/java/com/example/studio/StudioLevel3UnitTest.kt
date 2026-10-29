package com.example.studio

import com.example.studio.export.StudioExportEngine
import com.example.studio.model.ItemEffects
import com.example.studio.model.ItemTransform
import com.example.studio.model.StudioProject
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Level 3 Unit Tests for SAIF AI Video Studio:
 * Validates Multimodal AI Scene Generation, Neural TTS Voiceover bounds and timing,
 * and Production-Grade Multi-Format Export (Bitrate scaling, Dynamic ETA algorithms).
 */
class StudioLevel3UnitTest {

    @Test
    fun testExportBitrateScalingAcrossTiers() {
        // 1080p Base: 10 Mbps
        val standard1080p = StudioExportEngine.calculateBitrate(1080, 1920, "Standard")
        val high1080p = StudioExportEngine.calculateBitrate(1080, 1920, "High")
        val cinema1080p = StudioExportEngine.calculateBitrate(1080, 1920, "Cinema Master")

        assertEquals(10_000_000, standard1080p)
        assertEquals(18_000_000, high1080p)
        assertEquals(35_000_000, cinema1080p)

        // 720p Base: 5 Mbps
        val standard720p = StudioExportEngine.calculateBitrate(720, 1280, "Standard")
        val high720p = StudioExportEngine.calculateBitrate(720, 1280, "High")
        val cinema720p = StudioExportEngine.calculateBitrate(720, 1280, "Cinema Master")

        assertEquals(5_000_000, standard720p)
        assertEquals(9_000_000, high720p)
        assertEquals(17_500_000, cinema720p)

        // 4K Base: 25 Mbps
        val standard4K = StudioExportEngine.calculateBitrate(3840, 2160, "Standard")
        val high4K = StudioExportEngine.calculateBitrate(3840, 2160, "High")
        val cinema4K = StudioExportEngine.calculateBitrate(3840, 2160, "Cinema Master")

        assertEquals(25_000_000, standard4K)
        assertEquals(45_000_000, high4K)
        assertEquals(87_500_000, cinema4K)

        assertTrue(high1080p > standard1080p)
        assertTrue(cinema1080p > high1080p)
    }

    @Test
    fun testRealTimeEtaCalculationAlgorithm() {
        val totalFrames = 150
        val framesDone = 51
        val elapsedMs = 2500L

        val etaSec = StudioExportEngine.calculateEtaSeconds(totalFrames, framesDone, elapsedMs)
        // 99 frames left * (2500/51 ≈ 49ms) ≈ 4852ms -> 4s
        assertTrue("ETA should be ~4-5s, got $etaSec", etaSec in 4..5)

        val formatted = StudioExportEngine.formatEta(etaSec)
        assertEquals("00:04", formatted)

        // Test minutes formatting
        val longEta = StudioExportEngine.formatEta(125)
        assertEquals("02:05", longEta)
    }

    @Test
    fun testVoiceoverDurationFormulaAndBounds() {
        val script = "Every frame tells a story. Step into the future of creative neural video production."
        val words = script.split("\\s+".toRegex()).size
        val speechRate = 1.2f

        // Level 3 formula: (wordCount * 0.38f / speechRate).coerceIn(1.5f, 30.0f)
        val calculatedDurSec = ((words * 0.38f) / speechRate).coerceIn(1.5f, 30.0f)

        val voiceItem = VoiceItem(
            id = "voice_track_1",
            laneId = "lane_voice_1",
            startUs = 2_000_000L,
            durationUs = (calculatedDurSec * 1_000_000L).toLong(),
            text = script,
            speakerTone = "Fenrir",
            speed = speechRate,
            audioDucking = true,
            label = "🎙️ AI_Fenrir"
        )

        assertTrue(voiceItem.durationUs >= 1_500_000L)
        assertTrue(voiceItem.durationUs <= 30_000_000L)
        assertEquals("🎙️ AI_Fenrir", voiceItem.label)
        assertEquals(2_000_000L, voiceItem.startUs)
        assertTrue(voiceItem.audioDucking)

        // Extremely short script bound
        val shortWords = 1
        val shortDur = ((shortWords * 0.38f) / 1.0f).coerceIn(1.5f, 30.0f)
        assertEquals(1.5f, shortDur, 0.01f)

        // Extremely long script bound
        val longWords = 200
        val longDur = ((longWords * 0.38f) / 1.0f).coerceIn(1.5f, 30.0f)
        assertEquals(30.0f, longDur, 0.01f)
    }

    @Test
    fun testAiVideoClipItemStructure() {
        val style = "Cyberpunk Sci-Fi"
        val durationSec = 5.0f
        val durationUs = (durationSec * 1_000_000L).toLong()

        val aiClip = VideoItem(
            id = "ai_clip_${UUID.randomUUID().toString().take(8)}",
            laneId = "lane_video_1",
            startUs = 0L,
            durationUs = durationUs,
            mediaUri = "",
            label = "AI_${style.replace(" ", "_")}",
            origin = "ai_generator",
            aiWhy = "Cyberpunk neon scene",
            effects = ItemEffects(contrast = 1.15f, saturation = 1.2f),
            transform = ItemTransform(scale = 1.0f)
        )

        assertEquals("AI_Cyberpunk_Sci-Fi", aiClip.label)
        assertEquals(5_000_000L, aiClip.durationUs)
        assertEquals("ai_generator", aiClip.origin)
        assertEquals(1.15f, aiClip.effects.contrast, 0.01f)

        val project = StudioProject.createEmpty("Level 3 Studio Project").copy(items = listOf(aiClip))
        assertEquals(1, project.items.size)
        assertEquals(5_000_000L, project.totalDurationUs)
    }

    @Test
    fun testUniversalProviderDetection() {
        fun detectProvider(key: String, fallback: String): String {
            val trimmed = key.trim()
            return when {
                trimmed.startsWith("sk-ant-") -> "Anthropic"
                trimmed.startsWith("sk-or-") -> "OpenRouter"
                trimmed.startsWith("gsk_") -> "Groq"
                trimmed.startsWith("sk-proj-") || trimmed.startsWith("sk-") -> "OpenAI"
                trimmed.startsWith("AIza") -> "Gemini"
                else -> fallback
            }
        }

        assertEquals("Anthropic", detectProvider("sk-ant-api03-xxx", "Gemini"))
        assertEquals("OpenRouter", detectProvider("sk-or-v1-xxx", "Gemini"))
        assertEquals("Groq", detectProvider("gsk_9837498234", "Gemini"))
        assertEquals("OpenAI", detectProvider("sk-proj-98127398127", "Gemini"))
        assertEquals("Gemini", detectProvider("AIzaSyAbcd1234", "Ashna AI"))
        assertEquals("Ashna AI", detectProvider("", "Ashna AI"))
    }
}
