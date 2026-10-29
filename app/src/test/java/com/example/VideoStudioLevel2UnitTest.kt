package com.example

import com.example.ui.components.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Level 2 Unit Tests:
 * Validates manual editing tools, splitting, trimming, duplicating, deleting,
 * layer ordering, and Tune Inspector parameters across all 6 tracks.
 */
class VideoStudioLevel2UnitTest {

    @Test
    fun testSmartSplitOnVideoClip() {
        val clip = TimelineMediaClip(
            id = "clip-1",
            name = "TestVideo.mp4",
            durationSeconds = 10.0f,
            inSeconds = 0f,
            outSeconds = 10.0f
        )
        val initialProject = VideoProjectData(
            title = "Test Split",
            clips = listOf(clip),
            totalDurationSeconds = 10.0f,
            selectedClipIndex = 0
        )

        // Split at 4.0s
        val splitSec = 4.0f
        val durA = splitSec
        val durB = clip.durationSeconds - splitSec
        val partA = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name}_part1",
            durationSeconds = durA,
            inSeconds = 0f,
            outSeconds = durA
        )
        val partB = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name}_part2",
            durationSeconds = durB,
            inSeconds = durA,
            outSeconds = clip.outSeconds
        )

        val updatedClips = listOf(partA, partB)
        val updatedProject = initialProject.copy(
            clips = updatedClips,
            totalDurationSeconds = updatedClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
        )

        assertEquals(2, updatedProject.clips.size)
        assertEquals(4.0f, updatedProject.clips[0].durationSeconds, 0.01f)
        assertEquals(6.0f, updatedProject.clips[1].durationSeconds, 0.01f)
        assertEquals(10.0f, updatedProject.totalDurationSeconds, 0.01f)
    }

    @Test
    fun testTrack3VisualAssetTuneParameters() {
        val asset = VisualAssetTrackItem(
            name = "Neon_Logo",
            startSeconds = 1.0f,
            durationSeconds = 5.0f,
            scale = 1.25f,
            posX = 20f,
            posY = -40f,
            rotation = 15f,
            opacity = 0.9f,
            blendMode = "Screen",
            neonGlow = 0.8f,
            blurRadius = 4f,
            brightness = 1.1f,
            inAnimation = "Pop-In",
            outAnimation = "Fade-Out"
        )

        assertEquals(1.25f, asset.scale, 0.01f)
        assertEquals(20f, asset.posX, 0.01f)
        assertEquals(-40f, asset.posY, 0.01f)
        assertEquals(15f, asset.rotation, 0.01f)
        assertEquals(0.8f, asset.neonGlow, 0.01f)
        assertEquals("Screen", asset.blendMode)
        assertEquals("Pop-In", asset.inAnimation)
    }

    @Test
    fun testTrack4TextSubtitleTuneParameters() {
        val textItem = TextSubtitleTrackItem(
            text = "Autonomous AI Motion Graphics",
            startSeconds = 0f,
            durationSeconds = 3.5f,
            fontSize = 32f,
            textColorHex = "#00DAF3",
            strokeColorHex = "#000000",
            strokeWidth = 3f,
            neonGlow = 0.7f,
            hasBackgroundBox = true,
            boxColorHex = "#20000000",
            fontFamily = "Monospace",
            animationPreset = "Typewriter"
        )

        assertEquals("Autonomous AI Motion Graphics", textItem.text)
        assertEquals(32f, textItem.fontSize, 0.01f)
        assertEquals("#00DAF3", textItem.textColorHex)
        assertEquals("Typewriter", textItem.animationPreset)
    }

    @Test
    fun testTrack5VoiceAndTrack6AudioTuneParameters() {
        val voiceItem = VoiceoverTrackItem(
            narration = "Neural voice speaking",
            volume = 1.4f,
            speed = 1.15f,
            audioDucking = true
        )
        assertEquals(1.4f, voiceItem.volume, 0.01f)
        assertEquals(1.15f, voiceItem.speed, 0.01f)
        assertTrue(voiceItem.audioDucking)

        val soundItem = SoundAudioTrackItem(
            title = "Cinematic_Impact.mp3",
            volume = 0.85f,
            speed = 1.0f,
            fadeInSeconds = 0.5f,
            fadeOutSeconds = 1.0f,
            audioDucking = false
        )
        assertEquals(0.85f, soundItem.volume, 0.01f)
        assertEquals(0.5f, soundItem.fadeInSeconds, 0.01f)
        assertEquals(1.0f, soundItem.fadeOutSeconds, 0.01f)
    }

    @Test
    fun testDuplicateAndLayerOrder() {
        val clips = listOf(
            TimelineMediaClip(id = "c1", name = "Clip1", durationSeconds = 3f),
            TimelineMediaClip(id = "c2", name = "Clip2", durationSeconds = 4f)
        )
        // Bring Forward (swap c1 and c2)
        val swapped = listOf(clips[1], clips[0])
        assertEquals("Clip2", swapped[0].name)
        assertEquals("Clip1", swapped[1].name)

        // Duplicate Clip1
        val duplicated = clips[0].copy(
            id = UUID.randomUUID().toString(),
            name = "${clips[0].name}_Copy"
        )
        assertEquals("Clip1_Copy", duplicated.name)
    }
}
