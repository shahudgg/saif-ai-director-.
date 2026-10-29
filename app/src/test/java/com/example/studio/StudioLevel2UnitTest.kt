package com.example.studio

import com.example.studio.audio.AudioMixer
import com.example.studio.export.StudioExportEngine
import com.example.studio.model.AudioItem
import com.example.studio.model.ImageItem
import com.example.studio.model.ItemTransform
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.TransformKeyframe
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import com.example.studio.render.LayerMath
import com.example.studio.state.StudioAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioLevel2UnitTest {

    @Test
    fun testAudioMixerDuckingAndFades() {
        val bgmItem = AudioItem(
            id = "bgm_1",
            laneId = "lane_audio_1",
            startUs = 0L,
            durationUs = 10_000_000L,
            volume = 1.0f,
            fadeInUs = 1_000_000L,
            fadeOutUs = 1_000_000L,
            audioDucking = true,
            duckingAmount = 0.5f,
            isSfx = false
        )

        // Fade in test at 0.5s (halfway through 1s fade in)
        val fadeInGain = AudioMixer.calculateEffectiveGain(bgmItem, 500_000L, isVoiceActiveAtTime = false)
        assertEquals(0.5f, fadeInGain, 0.05f)

        // Normal playback without voice at 5.0s
        val fullGain = AudioMixer.calculateEffectiveGain(bgmItem, 5_000_000L, isVoiceActiveAtTime = false)
        assertEquals(1.0f, fullGain, 0.01f)

        // Ducking active with voice at 5.0s -> 1.0f * (1 - 0.5) = 0.5f
        val duckedGain = AudioMixer.calculateEffectiveGain(bgmItem, 5_000_000L, isVoiceActiveAtTime = true)
        assertEquals(0.5f, duckedGain, 0.01f)
    }

    @Test
    fun testAudioMixerSoftLimiter() {
        // High volume overlap (1.2 + 0.9 = 2.1)
        val (left, right) = AudioMixer.mixStereoSample(listOf(1.2f, 0.9f), listOf(1.0f, 1.0f))
        // Verify limited sample does not overflow Short.MAX_VALUE
        assertTrue(left <= Short.MAX_VALUE)
        assertTrue(right <= Short.MAX_VALUE)
    }

    @Test
    fun testKeyframeInterpolation() {
        val base = ItemTransform(x = 0f, y = 0f, scale = 1f, rotation = 0f, opacity = 1f)
        val keyframes = listOf(
            TransformKeyframe(timeUs = 0L, x = 0f, scale = 1.0f),
            TransformKeyframe(timeUs = 2_000_000L, x = 200f, scale = 2.0f)
        )

        // At midpoint 1s (1_000_000 Us)
        val interpolated = LayerMath.interpolateTransform(base, keyframes, 1_000_000L)
        // With easeinoutquad at t=0.5, value is exactly halfway
        assertEquals(100f, interpolated.x, 1.0f)
        assertEquals(1.5f, interpolated.scale, 0.05f)
    }

    @Test
    fun testActionRippleDelete() {
        val item1 = VideoItem(id = "v1", laneId = "lane_video_1", startUs = 0L, durationUs = 3_000_000L, mediaUri = "")
        val item2 = VideoItem(id = "v2", laneId = "lane_video_1", startUs = 3_000_000L, durationUs = 4_000_000L, mediaUri = "")
        val item3 = VideoItem(id = "v3", laneId = "lane_video_1", startUs = 7_000_000L, durationUs = 5_000_000L, mediaUri = "")

        val project = StudioProject.createEmpty("Ripple Test").copy(items = listOf(item1, item2, item3))

        // Ripple delete item 2 (duration 4s)
        val result = StudioAction.RippleDelete("v2").apply(project)
        assertEquals(2, result.items.size)

        // Item 3 should have shifted left from 7s to 3s
        val shiftedItem3 = result.items.first { it.id == "v3" }
        assertEquals(3_000_000L, shiftedItem3.startUs)
    }

    @Test
    fun testActionDuplicate() {
        val item = TextItem(id = "t1", laneId = "lane_text_1", startUs = 1_000_000L, durationUs = 2_000_000L, text = "Hello")
        val project = StudioProject.createEmpty("Dup Test").copy(items = listOf(item))

        val result = StudioAction.DuplicateItem("t1").apply(project)
        assertEquals(2, result.items.size)

        val duplicate = result.items.first { it.id != "t1" }
        assertEquals(3_000_000L, duplicate.startUs)
        assertEquals(2_000_000L, duplicate.durationUs)
    }

    @Test
    fun testActionBringForwardAndSendBackward() {
        val item1 = ImageItem(id = "i1", laneId = "lane_visual_1", startUs = 0L, durationUs = 2_000_000L, mediaUri = "")
        val item2 = ImageItem(id = "i2", laneId = "lane_visual_1", startUs = 0L, durationUs = 2_000_000L, mediaUri = "")
        val project = StudioProject.createEmpty("Z-Order Test").copy(items = listOf(item1, item2))

        // Bring Forward item1 (swaps i1 and i2)
        val forwarded = StudioAction.BringForward("i1").apply(project)
        assertEquals("i2", forwarded.items[0].id)
        assertEquals("i1", forwarded.items[1].id)

        // Send Backward item1 (swaps back)
        val backward = StudioAction.SendBackward("i1").apply(forwarded)
        assertEquals("i1", backward.items[0].id)
        assertEquals("i2", backward.items[1].id)
    }

    @Test
    fun testApplyStyleToAllCaptions() {
        val cap1 = TextItem(id = "c1", laneId = "lane_text_1", startUs = 0L, durationUs = 2_000_000L, text = "Caption 1", stylePreset = "Minimal")
        val cap2 = TextItem(id = "c2", laneId = "lane_text_1", startUs = 2_000_000L, durationUs = 2_000_000L, text = "Caption 2", stylePreset = "Minimal")
        val other = VideoItem(id = "v1", laneId = "lane_video_1", startUs = 0L, durationUs = 4_000_000L, mediaUri = "")

        val project = StudioProject.createEmpty("Batch Style").copy(items = listOf(cap1, cap2, other))

        val styled = StudioAction.ApplyStyleToAllCaptions(
            stylePreset = "Hormozi Neon",
            fontSizeSp = 28f,
            textColorHex = 0xFF00E5FF,
            outlineColorHex = 0xFF000000,
            outlineWidth = 3f
        ).apply(project)

        val updatedCaps = styled.items.filterIsInstance<TextItem>()
        assertEquals(2, updatedCaps.size)
        assertTrue(updatedCaps.all { it.stylePreset == "Hormozi Neon" })
        assertTrue(updatedCaps.all { it.fontSizeSp == 28f })
        assertTrue(updatedCaps.all { it.textColorHex == 0xFF00E5FF })
    }

    @Test
    fun testSrtSubtitleExportFormatting() {
        val cap1 = TextItem(id = "c1", laneId = "lane_text_1", startUs = 1_500_000L, durationUs = 2_000_000L, text = "First Line")
        val project = StudioProject.createEmpty("Srt Test").copy(items = listOf(cap1))

        val srt = StudioExportEngine.exportSubtitlesSrt(project)
        assertTrue(srt.contains("1"))
        assertTrue(srt.contains("00:00:01,500 --> 00:00:03,500"))
        assertTrue(srt.contains("First Line"))
    }
}
