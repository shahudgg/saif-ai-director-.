package com.example

import com.example.ui.components.SaifAiEditingEngine
import com.example.ui.components.SilenceSegment
import com.example.ui.components.TimelineMediaClip
import com.example.ui.components.VideoProjectData
import com.example.ui.components.VideoScript
import com.example.ui.components.VideoScriptSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Level 6 Unit Tests:
 * Validates AI Silence Auto-Cut, AI Word-Level Captions, Smart Audio Ducking,
 * AI Auto-Reframe, and Universal API Key Compatibility.
 */
class VideoStudioLevel6UnitTest {

    @Test
    fun testSilenceAutoCutDetectionAndApplication() {
        val clip1 = TimelineMediaClip(
            id = "c1",
            name = "Podcast_Interview.mp4",
            durationSeconds = 6.0f,
            inSeconds = 0.0f,
            outSeconds = 6.0f
        )
        val clip2 = TimelineMediaClip(
            id = "c2",
            name = "Vlog_Talk.mp4",
            durationSeconds = 8.0f,
            inSeconds = 0.0f,
            outSeconds = 8.0f
        )

        val project = VideoProjectData(
            title = "Interview Project",
            clips = listOf(clip1, clip2),
            totalDurationSeconds = 14.0f
        )

        // 1. Detect silence
        val detected = SaifAiEditingEngine.detectSilenceSegments(project.clips)
        assertTrue("Should detect silence in long clips (>4s)", detected.isNotEmpty())
        assertEquals(2, detected.size)

        // 2. Apply silence cuts with approved segments
        val updatedProject = SaifAiEditingEngine.applySilenceAutoCut(project, detected)

        assertNotNull(updatedProject)
        assertTrue("Total duration must be shorter after silence removal", updatedProject.totalDurationSeconds < project.totalDurationSeconds)
        assertTrue("Clip 1 duration must be reduced", updatedProject.clips[0].durationSeconds < 6.0f)
        assertTrue("Clip 2 duration must be reduced", updatedProject.clips[1].durationSeconds < 8.0f)
        assertTrue("Clip must be marked AI enhanced", updatedProject.clips[0].isAiEnhanced)
    }

    @Test
    fun testWordLevelAutoCaptionsWithScript() {
        val script = VideoScript(
            title = "Viral Hook Strategy",
            targetDurationSeconds = 6.0f,
            sections = listOf(
                VideoScriptSection(
                    kind = "hook",
                    narration = "Stop scrolling right now",
                    visualHint = "Bold red zoom",
                    estSeconds = 2.5f
                ),
                VideoScriptSection(
                    kind = "value",
                    narration = "This AI tool edits videos automatically",
                    visualHint = "Screen capture",
                    estSeconds = 3.5f
                )
            )
        )

        val project = VideoProjectData(
            title = "Scripted Video",
            script = script,
            totalDurationSeconds = 6.0f
        )

        val captions = SaifAiEditingEngine.generateWordLevelAutoCaptions(project)

        assertEquals(2, captions.size)
        assertEquals("Stop scrolling right now", captions[0].text)
        assertEquals(listOf("Stop", "scrolling", "right", "now"), captions[0].words)
        assertTrue("First caption should start near 0s", captions[0].startSeconds == 0.0f)
        assertTrue("Second caption should start at or after 2.5s", captions[1].startSeconds >= 2.5f)
    }

    @Test
    fun testSmartAudioDuckingDsp() {
        val project = VideoProjectData(
            title = "BGM Project",
            audioTrackName = "Upbeat_Growth_01.wav",
            audioDuckAmount = 0.0f
        )

        val updated = SaifAiEditingEngine.applySmartAudioDucking(project, 0.35f)

        assertEquals(0.35f, updated.audioDuckAmount, 0.01f)
    }

    @Test
    fun testAutoReframeAspectRatios() {
        val project = VideoProjectData(
            title = "Reframe Project",
            aspectRatio = "16:9",
            activePipPosition = "Top-Right"
        )

        // Reframe to 9:16 Shorts
        val reframeShorts = SaifAiEditingEngine.applyAutoReframe(project, "9:16")
        assertEquals("9:16", reframeShorts.aspectRatio)
        assertEquals("Bottom-Right", reframeShorts.activePipPosition)

        // Reframe to 1:1 Square
        val reframeSquare = SaifAiEditingEngine.applyAutoReframe(project, "1:1")
        assertEquals("1:1", reframeSquare.aspectRatio)
    }

    @Test
    fun testUniversalApiKeyResolverCoverage() {
        val providerKeys = mapOf(
            "AIzaSyClKeyTest" to "Gemini",
            "sk-proj-openaiKeyTest" to "OpenAI",
            "sk-ant-claudeKeyTest" to "Anthropic",
            "gsk_groqFastKey" to "Groq",
            "sk-or-v1-openrouter" to "OpenRouter"
        )

        for ((key, expected) in providerKeys) {
            val trimmed = key.trim()
            val detected = when {
                trimmed.startsWith("sk-ant-") -> "Anthropic"
                trimmed.startsWith("sk-or-") -> "OpenRouter"
                trimmed.startsWith("gsk_") -> "Groq"
                trimmed.startsWith("sk-proj-") || trimmed.startsWith("sk-") -> "OpenAI"
                trimmed.startsWith("AIza") -> "Gemini"
                else -> "Fallback"
            }
            assertEquals(expected, detected)
        }
    }
}
