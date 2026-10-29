package com.example

import com.example.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class SaifAiEditingEngineTest {

    @Test
    fun testExecutePlanAppliesEditsCorrectly() {
        val initialClip = TimelineMediaClip(
            id = "clip_test_1",
            name = "Test Clip",
            durationSeconds = 10f,
            origin = "ai"
        )
        val initialProject = VideoProjectData(
            clips = listOf(initialClip),
            totalDurationSeconds = 10f
        )

        val plan = VideoEditPlan(
            summary = "Test Cinematic Edit",
            operations = listOf(
                EditOperation(
                    op = "speed",
                    clipId = "clip_test_1",
                    params = mapOf("speedFactor" to "1.5"),
                    why = "Pacing boost"
                ),
                EditOperation(
                    op = "stabilize",
                    clipId = "clip_test_1",
                    params = emptyMap(),
                    why = "Anti-jitter"
                )
            ),
            overlays = listOf(
                EditOverlay(
                    type = "text",
                    props = mapOf("text" to "HELLO NEON", "animation" to "Kinetic Pop")
                ),
                EditOverlay(
                    type = "filter",
                    props = mapOf("filterId" to "Teal & Orange")
                )
            ),
            captions = true,
            musicMood = "energetic"
        )

        val updated = SaifAiEditingEngine.executePlan(initialProject, plan)

        assertEquals(1.5f, updated.clips[0].speed, 0.01f)
        assertTrue(updated.isStabilized)
        assertEquals("HELLO NEON", updated.textOverlay)
        assertEquals("Teal & Orange", updated.activeFilter)
        assertEquals("Hyperdrive_Bass_Boost.wav", updated.audioTrackName)
        assertTrue(updated.autoCaptions.isNotEmpty())
    }

    @Test
    fun testTimelineMemoryProtectsManualEdits() {
        val manualClip = TimelineMediaClip(
            id = "manual_clip_1",
            name = "User Custom Clip",
            speed = 0.5f,
            origin = "manual" // Protected manual edit (Level 16)
        )
        val project = VideoProjectData(clips = listOf(manualClip))

        val plan = VideoEditPlan(
            summary = "AI Overwrite Attempt",
            operations = listOf(
                EditOperation(
                    op = "speed",
                    clipId = "manual_clip_1",
                    params = mapOf("speedFactor" to "2.0"),
                    why = "AI speed up"
                )
            )
        )

        val result = SaifAiEditingEngine.executePlan(project, plan)

        // Speed must remain untouched at 0.5f because origin is "manual"
        assertEquals(0.5f, result.clips[0].speed, 0.001f)
        assertEquals("manual", result.clips[0].origin)
    }

    @Test
    fun testSilenceSegmentDetection() {
        val clips = listOf(
            TimelineMediaClip(name = "Clip 1", durationSeconds = 8f),
            TimelineMediaClip(name = "Clip 2", durationSeconds = 2f) // Short clip should not trigger silence cut
        )
        val segments = SaifAiEditingEngine.detectSilenceSegments(clips)

        assertEquals(1, segments.size)
        assertEquals("Clip 1", segments[0].clipName)
        assertEquals(1.2f, segments[0].durationSeconds, 0.01f)
    }
}
