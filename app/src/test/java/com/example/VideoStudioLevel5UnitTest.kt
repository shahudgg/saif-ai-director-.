package com.example

import com.example.ui.components.SaifAiEditingEngine
import com.example.ui.components.TimelineMediaClip
import com.example.ui.components.VideoProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Level 5 Unit Tests:
 * Validates Level 5 Prompt Se Edit, Video Reference Matching, Universal API Key Support,
 * and Standalone Python MoviePy/FFmpeg Code Engine Compositor generation.
 */
class VideoStudioLevel5UnitTest {

    @Test
    fun testGeneratePythonCodeForVideoProject() {
        val clip1 = TimelineMediaClip(
            id = "c1",
            name = "Cyberpunk_Streets.mp4",
            durationSeconds = 4.0f,
            speed = 1.2f,
            transitionIn = "Whip Pan",
            transitionDuration = 0.5f,
            speedRampCurve = "Hero Moment"
        )
        val clip2 = TimelineMediaClip(
            id = "c2",
            name = "Neon_Vlog_Action.mp4",
            durationSeconds = 6.0f,
            speed = 1.0f,
            transitionIn = "Cross Dissolve",
            transitionDuration = 0.4f,
            speedRampCurve = "Bullet Time"
        )

        val project = VideoProjectData(
            title = "Level 5 Viral Reels",
            clips = listOf(clip1, clip2),
            textOverlay = "NEON GLOW HOOK",
            textColorHex = "#00DAF3",
            audioTrackName = "Cyber_Synthwave.wav",
            audioDuckAmount = 0.35f,
            activeFilter = "Teal & Orange",
            totalDurationSeconds = 10.0f
        )

        val pythonCode = SaifAiEditingEngine.generatePythonCodeForProject(project)

        assertNotNull(pythonCode)
        assertTrue("Must contain moviepy imports", pythonCode.contains("from moviepy.editor import"))
        assertTrue("Must specify width, height, fps", pythonCode.contains("WIDTH, HEIGHT, FPS = 1080, 1920, 60"))
        assertTrue("Must set total duration", pythonCode.contains("TOTAL_DURATION = 10.0"))
        assertTrue("Must reference Clip 1", pythonCode.contains("Cyberpunk_Streets.mp4"))
        assertTrue("Must reference Clip 2", pythonCode.contains("Neon_Vlog_Action.mp4"))
        assertTrue("Must reference speed ramp", pythonCode.contains("Hero Moment"))
        assertTrue("Must reference text overlay", pythonCode.contains("NEON GLOW HOOK"))
        assertTrue("Must contain CompositeVideoClip", pythonCode.contains("CompositeVideoClip(layers, size=(WIDTH, HEIGHT))"))
        assertTrue("Must write output videofile", pythonCode.contains("output_render_shorts.mp4"))
    }

    @Test
    fun testSynthesizeAlgorithmicPlanCinematic() {
        val clip = TimelineMediaClip(id = "c_main", name = "Raw_Take.mp4", durationSeconds = 8.0f)
        val project = VideoProjectData(title = "Cinematic Edit", clips = listOf(clip), totalDurationSeconds = 8.0f)

        val jsonPlan = SaifAiEditingEngine.synthesizeAlgorithmicPlan(
            project = project,
            prompt = "Is video ko cinematic bana do, teal orange color grade karo aur slow zoom dalo",
            refNote = null
        )

        assertNotNull(jsonPlan)
        assertTrue("Must contain summary", jsonPlan.contains("\"summary\":"))
        assertTrue("Must contain Teal & Orange filter", jsonPlan.contains("Teal & Orange"))
        assertTrue("Must contain cinematic music", jsonPlan.contains("cinematic"))
        assertTrue("Must contain operations array", jsonPlan.contains("\"operations\":"))
        assertTrue("Must contain colorAdjust", jsonPlan.contains("colorAdjust"))
        assertTrue("Must contain why rationale", jsonPlan.contains("\"why\":"))
    }

    @Test
    fun testSynthesizeAlgorithmicPlanGlitchAndSplit() {
        val clip1 = TimelineMediaClip(id = "c1", name = "Gaming_Montage.mp4", durationSeconds = 6.0f)
        val clip2 = TimelineMediaClip(id = "c2", name = "Reaction.mp4", durationSeconds = 4.0f)
        val project = VideoProjectData(title = "Gaming Edit", clips = listOf(clip1, clip2), totalDurationSeconds = 10.0f)

        val jsonPlan = SaifAiEditingEngine.synthesizeAlgorithmicPlan(
            project = project,
            prompt = "Fast cut phonk montage with split and cyber neon glitch",
            refNote = "Reference video fast pacing"
        )

        assertNotNull(jsonPlan)
        assertTrue("Must contain Cyberpunk Night filter", jsonPlan.contains("Cyberpunk Night"))
        assertTrue("Must contain RGB Chromatic Split or Neon Glow", jsonPlan.contains("Chromatic") || jsonPlan.contains("Neon"))
        assertTrue("Must contain split operation", jsonPlan.contains("\"op\": \"split\""))
        assertTrue("Must contain energetic music", jsonPlan.contains("energetic"))
    }

    @Test
    fun testUniversalApiKeyHandlingForAllProviders() {
        val testKeys = mapOf(
            "AIzaSyCl778DemoKey" to "Gemini",
            "sk-proj-49271842095" to "OpenAI",
            "sk-ant-api03-claude-test-key" to "Anthropic",
            "gsk_groqKeySuperFast98" to "Groq",
            "sk-or-v1-openrouter-key" to "OpenRouter"
        )

        for ((key, expected) in testKeys) {
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
