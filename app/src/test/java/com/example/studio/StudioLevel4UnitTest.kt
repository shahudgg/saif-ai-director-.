package com.example.studio

import com.example.studio.model.ItemTransform
import com.example.studio.model.StudioProject
import com.example.studio.model.TransformKeyframe
import com.example.studio.model.VideoItem
import com.example.studio.render.LayerMath
import com.example.studio.state.StudioAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class StudioLevel4UnitTest {

    @Test
    fun testStudioVideoItemSpeedRampAndTransition() {
        val videoItem = VideoItem(
            id = "vid_1",
            laneId = "lane_video_1",
            startUs = 0L,
            durationUs = 5_000_000L,
            mediaUri = "content://media/1",
            speedRampCurve = "Hero Moment",
            transitionIn = "Whip Pan Rush",
            transitionDurationUs = 600_000L,
            keyframes = listOf(
                TransformKeyframe(timeUs = 0L, scale = 1.0f, x = 0f, y = 0f),
                TransformKeyframe(timeUs = 2_500_000L, scale = 1.3f, x = 50f, y = -30f),
                TransformKeyframe(timeUs = 5_000_000L, scale = 1.0f, x = 0f, y = 0f)
            )
        )

        assertEquals("Hero Moment", videoItem.speedRampCurve)
        assertEquals("Whip Pan Rush", videoItem.transitionIn)
        assertEquals(600_000L, videoItem.transitionDurationUs)
        assertEquals(3, videoItem.keyframes.size)

        // Interpolation at t = 2.5s (should match keyframe 2)
        val interpolated = LayerMath.interpolateTransform(videoItem.transform, videoItem.keyframes, 2_500_000L)
        assertEquals(1.3f, interpolated.scale, 0.05f)
        assertEquals(50f, interpolated.x, 2.0f)
        assertEquals(-30f, interpolated.y, 2.0f)
    }

    @Test
    fun testStudioActionUpdateSpeedRampAndTransition() {
        val videoItem = VideoItem(
            id = "vid_action_test",
            laneId = "lane_video_1",
            startUs = 1_000_000L,
            durationUs = 4_000_000L,
            mediaUri = "content://media/2"
        )

        var project = StudioProject.createEmpty("Action Test").copy(items = listOf(videoItem))
        assertEquals("Linear", (project.items[0] as VideoItem).speedRampCurve)
        assertEquals("None", (project.items[0] as VideoItem).transitionIn)

        // Apply UpdateSpeedRamp action
        val rampAction = StudioAction.UpdateSpeedRamp(
            itemId = "vid_action_test",
            speedRampCurve = "Bullet Time",
            playbackSpeed = 1.5f
        )
        project = rampAction.apply(project)
        val updatedItem = project.items[0] as VideoItem
        assertEquals("Bullet Time", updatedItem.speedRampCurve)
        assertEquals(1.5f, updatedItem.playbackSpeed, 0.01f)

        // Apply UpdateTransition action
        val transAction = StudioAction.UpdateTransition(
            itemId = "vid_action_test",
            transitionIn = "Cyberpunk Glitch",
            transitionDurationUs = 500_000L
        )
        project = transAction.apply(project)
        val transitionItem = project.items[0] as VideoItem
        assertEquals("Cyberpunk Glitch", transitionItem.transitionIn)
        assertEquals(500_000L, transitionItem.transitionDurationUs)
    }

    @Test
    fun testSpeedRampCumulativeVelocityCalculations() {
        val heroPoints = listOf(
            0.0f to 2.0f,
            0.3f to 0.35f,
            0.7f to 0.35f,
            1.0f to 2.5f
        )

        val pQuarter = LayerMath.calculateSpeedRampProgress(heroPoints, 0.25f)
        val pHalf = LayerMath.calculateSpeedRampProgress(heroPoints, 0.5f)
        val pThreeQuarter = LayerMath.calculateSpeedRampProgress(heroPoints, 0.75f)

        assertTrue("Progress must increase: $pQuarter < $pHalf", pQuarter < pHalf)
        assertTrue("Progress must increase: $pHalf < $pThreeQuarter", pHalf < pThreeQuarter)
    }
}
