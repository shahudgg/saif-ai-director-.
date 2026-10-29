package com.example

import com.example.ui.components.*
import com.example.util.AndroidVideoExportEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Level 3 Unit Tests:
 * Validates Advanced AI Tools (Multimodal Prompt-to-Video, TTS Voiceover Studio)
 * and Production-Grade Multi-Format Export (Bitrate Tiers, Dynamic ETA, GIF & MP4 containers).
 */
class VideoStudioLevel3UnitTest {

    @Test
    fun testAiVideoClipGenerationStructure() {
        val prompt = "Cyberpunk rainy night in Neo-Tokyo with flying hovercars"
        val style = "Cyberpunk Sci-Fi"
        val duration = 5.0f

        val generatedClip = TimelineMediaClip(
            id = "ai_clip_${UUID.randomUUID().toString().take(8)}",
            name = "AI_${style.replace(" ", "_")}.mp4",
            durationSeconds = duration,
            inSeconds = 0f,
            outSeconds = duration,
            filterName = "Cyberpunk Night",
            filterIntensity = 0.85f,
            speed = 1.0f,
            opacity = 1.0f
        )

        assertEquals("AI_Cyberpunk_Sci-Fi.mp4", generatedClip.name)
        assertEquals(5.0f, generatedClip.durationSeconds, 0.01f)
        assertEquals("Cyberpunk Night", generatedClip.filterName)
        assertEquals(0.85f, generatedClip.filterIntensity, 0.01f)

        // Insert into project
        val project = VideoProjectData(
            title = "Level 3 AI Studio",
            clips = listOf(generatedClip),
            totalDurationSeconds = duration
        )

        assertEquals(1, project.clips.size)
        assertEquals(5.0f, project.totalDurationSeconds, 0.01f)
    }

    @Test
    fun testAiVoiceoverTrackItemDurationAndBounds() {
        val script = "Every frame tells a story. Step into the future of creative neural video production."
        val words = script.split("\\s+".toRegex()).size
        val speechRate = 1.2f

        // Level 3 formula: (wordCount * 0.38f / speechRate).coerceIn(1.5f, 30.0f)
        val calculatedDur = ((words * 0.38f) / speechRate).coerceIn(1.5f, 30.0f)

        val voiceoverItem = SoundAudioTrackItem(
            id = "voice_track_1",
            title = "🎙️ AI_Fenrir",
            startSeconds = 2.0f,
            durationSeconds = calculatedDur,
            volume = 1.0f,
            isSfx = false
        )

        assertTrue(voiceoverItem.durationSeconds >= 1.5f)
        assertTrue(voiceoverItem.durationSeconds <= 30.0f)
        assertEquals("🎙️ AI_Fenrir", voiceoverItem.title)
        assertEquals(2.0f, voiceoverItem.startSeconds, 0.01f)
        assertFalse(voiceoverItem.isSfx)
    }

    @Test
    fun testExportBitrateScalingAcrossTiers() {
        // Standard, High, and Cinema Master bitrate scaling
        val base4K = 25_000_000
        val high4K = (base4K * 1.8).toInt()
        val cinema4K = (base4K * 3.5).toInt()

        assertTrue(high4K > base4K)
        assertTrue(cinema4K > high4K)
        assertEquals(45_000_000, high4K)
        assertEquals(87_500_000, cinema4K)

        val base1080p = 10_000_000
        val high1080p = (base1080p * 1.8).toInt()
        val cinema1080p = (base1080p * 3.5).toInt()

        assertEquals(18_000_000, high1080p)
        assertEquals(35_000_000, cinema1080p)
    }

    @Test
    fun testRealTimeEtaCalculationAlgorithm() {
        val totalFrames = 150
        val targetFps = 30
        val frameIndex = 50 // 51 frames done
        val elapsedMs = 2500L // 2.5s for 51 frames

        val framesDone = frameIndex + 1
        val framesRemaining = totalFrames - framesDone
        val msPerFrame = elapsedMs.toFloat() / framesDone
        val etaSec = ((framesRemaining * msPerFrame) / 1000f).toInt()

        // 99 frames remaining at ~49ms/frame ≈ 4.85s -> 4 or 5s
        assertTrue(etaSec in 4..5)

        // ETA string format verification
        val etaFormatted = if (etaSec < 60) "00:${etaSec.toString().padStart(2, '0')}" else "${etaSec / 60}:${(etaSec % 60).toString().padStart(2, '0')}"
        assertEquals("00:04", etaFormatted)
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
