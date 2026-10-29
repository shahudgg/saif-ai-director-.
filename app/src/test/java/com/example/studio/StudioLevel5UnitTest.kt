package com.example.studio

import com.example.studio.model.AudioItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import com.example.ui.components.SaifAiEditingEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioLevel5UnitTest {

    @Test
    fun testGeneratePythonCodeForStudioProjectMultiTrack() {
        val videoItem = VideoItem(
            id = "vid_1",
            laneId = "lane_video_1",
            startUs = 0L,
            durationUs = 5_000_000L,
            mediaUri = "content://media/1",
            speedRampCurve = "Hero Moment",
            transitionIn = "Whip Pan Rush",
            transitionDurationUs = 500_000L,
            label = "Main Hero Video"
        )

        val textItem = TextItem(
            id = "text_1",
            laneId = "lane_text_1",
            startUs = 1_000_000L,
            durationUs = 3_000_000L,
            text = "Level 5 Unlocked!",
            label = "Subtitle Hook"
        )

        val voiceItem = VoiceItem(
            id = "voice_1",
            laneId = "lane_voice_1",
            startUs = 500_000L,
            durationUs = 4_000_000L,
            audioUri = "content://audio/tts",
            text = "Watch this video till the end",
            speakerTone = "Energetic",
            label = "Voiceover Track"
        )

        val audioItem = AudioItem(
            id = "audio_1",
            laneId = "lane_audio_1",
            startUs = 0L,
            durationUs = 8_000_000L,
            audioUri = "content://audio/bgm",
            volume = 0.8f,
            label = "Synthwave BGM"
        )

        val project = StudioProject.createEmpty("Studio Level 5 Project").copy(
            items = listOf(videoItem, textItem, voiceItem, audioItem)
        )

        val pythonCode = SaifAiEditingEngine.generatePythonCodeForStudioProject(project)

        assertNotNull(pythonCode)
        assertTrue("Contains MoviePy imports", pythonCode.contains("from moviepy.editor import"))
        assertTrue("Contains render_studio_project", pythonCode.contains("def render_studio_project():"))
        assertTrue("Reflects 8.0s duration", pythonCode.contains("TOTAL_DURATION = 8.0"))
        assertTrue("Contains Video Layer", pythonCode.contains("Main Hero Video"))
        assertTrue("Contains Subtitle Layer with text", pythonCode.contains("Level 5 Unlocked!"))
        assertTrue("Contains Voiceover info", pythonCode.contains("Watch this video") || pythonCode.contains("Energetic"))
        assertTrue("Contains composite clip", pythonCode.contains("CompositeVideoClip(visual_layers"))
        assertTrue("Writes studio_rendered.mp4", pythonCode.contains("studio_rendered.mp4"))
    }
}
