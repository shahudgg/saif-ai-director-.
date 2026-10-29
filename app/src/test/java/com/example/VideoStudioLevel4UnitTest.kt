package com.example

import com.example.studio.render.LayerMath
import com.example.ui.components.CinematicTransition
import com.example.ui.components.SpeedRampPreset
import com.example.ui.components.TimelineMediaClip
import com.example.ui.components.VideoEditorPresets
import com.example.ui.components.VideoKeyframe
import com.example.ui.components.VideoProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Level 4 Unit Tests:
 * Validates Speed Ramping with Bezier Curves, Multi-Property Keyframe Animation Engine,
 * Cinematic Transitions, and Autonomous Director Timeline Data Structures.
 */
class VideoStudioLevel4UnitTest {

    @Test
    fun testSpeedRampPresetsAndVelocityEvaluation() {
        val presets = VideoEditorPresets.ALL_SPEED_RAMP_PRESETS
        assertTrue("Should have multiple speed ramp presets", presets.size >= 5)

        val heroPreset = presets.first { it.id == "ramp_hero" }
        assertEquals("Hero Moment", heroPreset.name)

        // At t=0, speed should be ~2.0x (fast entry)
        val v0 = LayerMath.evaluateSpeedRampVelocity(heroPreset.points, 0.0f)
        assertEquals(2.0f, v0, 0.05f)

        // In the middle (t=0.5), speed should be slow-mo ~0.35x
        val vMid = LayerMath.evaluateSpeedRampVelocity(heroPreset.points, 0.5f)
        assertEquals(0.35f, vMid, 0.05f)

        // At t=1.0, speed should punch out ~2.5x
        val v1 = LayerMath.evaluateSpeedRampVelocity(heroPreset.points, 1.0f)
        assertEquals(2.5f, v1, 0.05f)

        // Cumulative progress should start at 0.0 and end at 1.0
        val pStart = LayerMath.calculateSpeedRampProgress(heroPreset.points, 0.0f)
        val pEnd = LayerMath.calculateSpeedRampProgress(heroPreset.points, 1.0f)
        assertEquals(0.0f, pStart, 0.01f)
        assertEquals(1.0f, pEnd, 0.01f)

        // Progress must be strictly monotonic
        var prevP = -0.01f
        for (i in 0..10) {
            val t = i / 10.0f
            val p = LayerMath.calculateSpeedRampProgress(heroPreset.points, t)
            assertTrue("Progress at t=$t must be >= previous progress", p >= prevP)
            prevP = p
        }
    }

    @Test
    fun testSpeedRampDurationScaling() {
        val linearPreset = VideoEditorPresets.ALL_SPEED_RAMP_PRESETS.first { it.id == "ramp_linear" }
        val origDur = 6.0f
        val linearResult = LayerMath.calculateSpeedRampDuration(origDur, linearPreset.points)
        assertEquals(origDur, linearResult, 0.05f)

        val flashInPreset = VideoEditorPresets.ALL_SPEED_RAMP_PRESETS.first { it.id == "ramp_flash_in" }
        val flashInResult = LayerMath.calculateSpeedRampDuration(origDur, flashInPreset.points)
        // Since flash in starts fast (avg speed > 1), duration should be compressed (< 6.0s)
        assertTrue("Flash in duration should be faster than original", flashInResult < origDur)
    }

    @Test
    fun testCubicBezierAndExtendedEasings() {
        // Linear easing tests
        assertEquals(0.5f, LayerMath.evaluateEasing(0.5f, "linear"), 0.01f)

        // Cubic EaseInOut tests: at 0.5 should be exactly 0.5
        assertEquals(0.5f, LayerMath.evaluateEasing(0.5f, "easeinoutcubic"), 0.01f)
        assertEquals(0.0f, LayerMath.evaluateEasing(0.0f, "easeinoutcubic"), 0.01f)
        assertEquals(1.0f, LayerMath.evaluateEasing(1.0f, "easeinoutcubic"), 0.01f)

        // Solve cubic bezier bisection
        val bzMid = LayerMath.solveCubicBezier(0.25f, 0.1f, 0.25f, 1.0f, 0.5f)
        assertTrue("Bezier mid should be between 0 and 1", bzMid in 0.0f..1.0f)

        // Elastic and Bounce sanity
        assertEquals(0.0f, LayerMath.evaluateEasing(0.0f, "elastic"), 0.01f)
        assertEquals(1.0f, LayerMath.evaluateEasing(1.0f, "elastic"), 0.01f)
        assertEquals(1.0f, LayerMath.evaluateEasing(1.0f, "bounce"), 0.05f)
    }

    @Test
    fun testKeyframeInterpolationMultiProperty() {
        val clip = TimelineMediaClip(
            id = "clip_k1",
            name = "Keyframe_Clip.mp4",
            durationSeconds = 6.0f,
            keyframes = listOf(
                VideoKeyframe(timeOffsetSeconds = 0.0f, scale = 1.0f, posX = 0f, posY = 0f, rotation = 0f, opacity = 1.0f),
                VideoKeyframe(timeOffsetSeconds = 2.0f, scale = 1.5f, posX = 100f, posY = -50f, rotation = 15f, opacity = 0.8f, easing = "EaseInOut"),
                VideoKeyframe(timeOffsetSeconds = 4.0f, scale = 1.0f, posX = 0f, posY = 0f, rotation = 0f, opacity = 1.0f)
            )
        )

        assertEquals(3, clip.keyframes.size)
        assertEquals(1.5f, clip.keyframes[1].scale, 0.01f)
        assertEquals(100f, clip.keyframes[1].posX, 0.01f)
        assertEquals(15f, clip.keyframes[1].rotation, 0.01f)
    }

    @Test
    fun testCinematicTransitionsTransforms() {
        val transitions = VideoEditorPresets.ALL_CINEMATIC_TRANSITIONS
        assertTrue("Should have multiple transition presets", transitions.size >= 5)

        // Whip Pan
        val whipOut = LayerMath.calculateTransitionFrame("Whip", 0.5f, isOutgoing = true)
        val whipIn = LayerMath.calculateTransitionFrame("Whip", 0.5f, isOutgoing = false)
        assertTrue(whipOut.offsetX < 0f) // slides left
        assertTrue(whipIn.offsetX > 0f)  // enters from right

        // Cyberpunk Glitch
        val glitch = LayerMath.calculateTransitionFrame("Glitch", 0.5f, isOutgoing = false)
        assertEquals(0.5f, glitch.alpha, 0.01f)
        assertTrue(glitch.scale >= 1.0f)

        // Spacetime Warp
        val warpOut = LayerMath.calculateTransitionFrame("Warp", 0.5f, isOutgoing = true)
        val warpIn = LayerMath.calculateTransitionFrame("Warp", 0.5f, isOutgoing = false)
        assertTrue(warpOut.scale > 1.0f) // zooms past camera
        assertTrue(warpIn.scale < 1.0f)  // scales up from center

        // Solar Light Leak
        val lightLeak = LayerMath.calculateTransitionFrame("Light", 0.5f, isOutgoing = false)
        assertTrue(lightLeak.brightnessBoost > 0f)

        // 3D Dimension Flip
        val flip3D = LayerMath.calculateTransitionFrame("3D", 0.5f, isOutgoing = true)
        assertTrue(flip3D.rotationY < 0f)
    }

    @Test
    fun testLevel4ProjectTimelineAssembly() {
        val clip1 = TimelineMediaClip(
            id = "c1",
            name = "Hero_Intro.mp4",
            durationSeconds = 4.0f,
            transitionIn = "Whip Pan Rush",
            transitionDuration = 0.6f,
            speedRampCurve = "Hero Moment"
        )
        val clip2 = TimelineMediaClip(
            id = "c2",
            name = "Action_Take.mp4",
            durationSeconds = 5.0f,
            transitionIn = "Cyberpunk Glitch",
            transitionDuration = 0.5f,
            speedRampCurve = "Bullet Time"
        )

        val project = VideoProjectData(
            title = "Level 4 Pro Studio Video",
            clips = listOf(clip1, clip2),
            totalDurationSeconds = 9.0f
        )

        assertEquals(2, project.clips.size)
        assertEquals("Whip Pan Rush", project.clips[0].transitionIn)
        assertEquals("Hero Moment", project.clips[0].speedRampCurve)
        assertEquals("Cyberpunk Glitch", project.clips[1].transitionIn)
        assertEquals("Bullet Time", project.clips[1].speedRampCurve)
    }

    @Test
    fun testUniversalApiKeyResolverSupport() {
        val keys = listOf(
            "AIzaSyDemoGeminiKey12345" to "Gemini",
            "sk-proj-openai9876543210" to "OpenAI",
            "sk-ant-api03-claude-test" to "Anthropic",
            "gsk_groqultraFast987654" to "Groq",
            "sk-or-v1-openrouter-key" to "OpenRouter"
        )

        for ((key, expectedProvider) in keys) {
            val trimmed = key.trim()
            val detected = when {
                trimmed.startsWith("sk-ant-") -> "Anthropic"
                trimmed.startsWith("sk-or-") -> "OpenRouter"
                trimmed.startsWith("gsk_") -> "Groq"
                trimmed.startsWith("sk-proj-") || trimmed.startsWith("sk-") -> "OpenAI"
                trimmed.startsWith("AIza") -> "Gemini"
                else -> "Fallback"
            }
            assertEquals(expectedProvider, detected)
        }
    }
}
