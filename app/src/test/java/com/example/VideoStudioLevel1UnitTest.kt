package com.example

import com.example.studio.model.ImageItem
import com.example.studio.model.StudioProject
import com.example.studio.model.StudioProjectMigrator
import com.example.studio.model.TrackType
import com.example.studio.model.VideoItem
import com.example.studio.render.LayerMath
import com.example.studio.state.StudioAction
import com.example.ui.components.VideoProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStudioLevel1UnitTest {

    @Test
    fun testNewProjectIsCompletelyEmpty() {
        // A new project must have 0 items, 0s duration, and 6 empty lanes with no demo content
        val project = StudioProject.createEmpty(name = "Untitled 1")

        assertEquals(0, project.items.size)
        assertEquals(0L, project.totalDurationUs)
        assertEquals(0.0f, project.totalDurationSeconds, 0.001f)
        assertEquals(6, project.lanes.size)

        // Verify each lane has zero demo items
        project.lanes.forEach { lane ->
            val laneItems = project.items.filter { it.laneId == lane.id }
            assertTrue("Lane ${lane.name} must be completely empty", laneItems.isEmpty())
        }
    }

    @Test
    fun testTwoSameNameProjectsAreIndependent() {
        val proj1 = StudioProject.createEmpty(name = "My Project")
        val proj2 = StudioProject.createEmpty(name = "My Project")

        assertNotEquals(proj1.id, proj2.id)
        assertEquals(proj1.name, proj2.name)
    }

    @Test
    fun testMigratorWithoutDemoContent() {
        // Legacy project with 0 items
        val legacyEmpty = VideoProjectData(
            projectName = "Legacy Blank",
            clips = emptyList(),
            codeTracks = emptyList(),
            visualAssetTracks = emptyList(),
            textSubtitleTracks = emptyList(),
            voiceoverTracks = emptyList(),
            soundAudioTracks = emptyList()
        )

        val migrated = StudioProjectMigrator.migrate(legacyEmpty)
        assertEquals(0, migrated.items.size)
        assertEquals(0L, migrated.totalDurationUs)
    }

    @Test
    fun testLayerMathTimePixelConversionAndSnapping() {
        val zoomDpPerSec = 60f
        val density = 2.0f

        // 1.5 seconds in microseconds = 1_500_000 Us
        val timeUs = 1_500_000L
        val px = LayerMath.timeUsToPixel(timeUs, zoomDpPerSec, density)
        // 1.5s * 60dp/s * 2.0 = 180px
        assertEquals(180f, px, 0.01f)

        val convertedTimeUs = LayerMath.pixelToTimeUs(px, zoomDpPerSec, density)
        assertEquals(timeUs, convertedTimeUs)

        // Snapping test: target is 1_480_000 Us, snap boundary is 1_500_000 Us
        val targets = listOf(0L, 1_500_000L, 3_000_000L)
        val snapResult = LayerMath.findSnapPoint(
            targetUs = 1_480_000L,
            snapTargets = targets,
            zoomDpPerSec = zoomDpPerSec,
            density = density
        )
        assertTrue(snapResult.isSnapped)
        assertEquals(1_500_000L, snapResult.snappedTimeUs)
    }

    @Test
    fun testUndoRedoActionHistory() {
        val project = StudioProject.createEmpty("Undo Test")
        val item = VideoItem(
            id = "test_item_1",
            laneId = "lane_video_1",
            startUs = 0L,
            durationUs = 5_000_000L,
            mediaUri = "content://media/1"
        )

        // Apply AddItem action
        val withItem = StudioAction.AddItem(item).apply(project)
        assertEquals(1, withItem.items.size)
        assertEquals(5_000_000L, withItem.totalDurationUs)

        // Apply RemoveItem action
        val reverted = StudioAction.RemoveItem("test_item_1").apply(withItem)
        assertEquals(0, reverted.items.size)
        assertEquals(0L, reverted.totalDurationUs)
    }

    @Test
    fun testExactTimeFormatting() {
        assertEquals("00:00.0", LayerMath.formatTimeDisplay(0L))
        assertEquals("00:03.5", LayerMath.formatTimeDisplay(3_500_000L))
        assertEquals("01:15.8", LayerMath.formatTimeDisplay(75_800_000L))
        assertEquals("02:00.0", LayerMath.formatTimeDisplay(120_000_000L))
    }
}
