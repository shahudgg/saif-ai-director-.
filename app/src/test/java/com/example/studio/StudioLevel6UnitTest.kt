package com.example.studio

import com.example.studio.model.AudioItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.TrackType
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import com.example.studio.state.StudioAction
import com.example.ui.components.SaifAiEditingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioLevel6UnitTest {

    @Test
    fun testStudioSilenceAutoCut() {
        val videoItem = VideoItem(
            id = "vid_1",
            laneId = "lane_video_1",
            startUs = 0L,
            durationUs = 6_000_000L,
            mediaUri = "content://media/1",
            label = "Talking Head"
        )
        val audioItem = AudioItem(
            id = "audio_1",
            laneId = "lane_audio_1",
            startUs = 0L,
            durationUs = 6_000_000L,
            label = "Podcast Audio"
        )

        val project = StudioProject.createEmpty("Studio Test").copy(
            items = listOf(videoItem, audioItem)
        )

        val cutProject = SaifAiEditingEngine.applySilenceAutoCutToStudioProject(project)

        assertNotNull(cutProject)
        val cutVideo = cutProject.items.first { it is VideoItem }
        val cutAudio = cutProject.items.first { it is AudioItem }

        assertTrue("Video duration should be trimmed", cutVideo.durationUs < 6_000_000L)
        assertTrue("Audio duration should be trimmed", cutAudio.durationUs < 6_000_000L)
    }

    @Test
    fun testStudioAutoCaptionsGeneration() {
        val voiceItem = VoiceItem(
            id = "voice_1",
            laneId = "lane_voice_1",
            startUs = 1_000_000L,
            durationUs = 3_500_000L,
            text = "Welcome to the future of editing",
            speakerTone = "Energetic",
            label = "Voiceover 1"
        )

        val project = StudioProject.createEmpty("Caption Test").copy(
            items = listOf(voiceItem)
        )

        val captionedProject = SaifAiEditingEngine.generateStudioAutoCaptions(project)

        val textItems = captionedProject.items.filterIsInstance<TextItem>()
        assertTrue("Should generate text items on subtitle lane", textItems.isNotEmpty())
        assertEquals("Welcome to the future of editing", textItems[0].text)
        assertEquals("lane_text_1", textItems[0].laneId)
        assertEquals("ai", textItems[0].origin)
        assertEquals("Hormozi Neon", textItems[0].stylePreset)
    }

    @Test
    fun testStudioSmartDuckingAndReframe() {
        val audioItem = AudioItem(
            id = "audio_bgm",
            laneId = "lane_audio_1",
            startUs = 0L,
            durationUs = 10_000_000L,
            audioDucking = false,
            duckingAmount = 0.0f
        )

        val project = StudioProject.createEmpty("DSP Test").copy(
            aspect = "16:9",
            items = listOf(audioItem)
        )

        // 1. Smart Ducking
        val ducked = SaifAiEditingEngine.applySmartAudioDuckingToStudioProject(project)
        val duckedAudio = ducked.items.first { it is AudioItem } as AudioItem
        assertTrue(duckedAudio.audioDucking)
        assertEquals(0.35f, duckedAudio.duckingAmount, 0.01f)

        // 2. Auto-Reframe
        val reframed = SaifAiEditingEngine.applyAutoReframeToStudioProject(project, "9:16")
        assertEquals("9:16", reframed.aspect)
    }

    @Test
    fun testReplaceProjectActionReversibility() {
        val initialProject = StudioProject.createEmpty("Action Reversibility")
        val updatedProject = initialProject.copy(name = "Updated by Level 6")

        val action = StudioAction.ReplaceProject(updatedProject)
        val applied = action.apply(initialProject)

        assertEquals("Updated by Level 6", applied.name)
    }
}
