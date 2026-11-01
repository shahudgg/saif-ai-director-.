package com.example.studio.director

import com.example.studio.audio.AudioMixer
import com.example.studio.model.AudioItem
import com.example.studio.model.StudioProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GuidedBriefOptionsTest {
    private fun brief() = DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO, prompt = "Explain solar energy", durationSec = 60,
        narrationLanguage = "English", aspect = "9:16", answers = mapOf("one" to "x", "two" to "x", "three" to "x", "four" to "x"))

    @Test fun audioMetadataDoesNotConsumeBriefQuestions() {
        val original = brief()
        val next = DirectorQuestions.next(original)
        assertNotNull(next)
        val withVoice = GuidedBriefOptions.withVoice(original, "Kabir")
        val configured = GuidedBriefOptions.withMusic(withVoice, BackgroundMusic("content://test/music", "Track", 8_000))
        assertEquals(next!!.key, DirectorQuestions.next(configured)!!.key)
        assertEquals("Kabir", configured.answers[GuidedBriefOptions.VOICE])
        assertEquals("content://test/music", GuidedBriefOptions.music(configured)!!.uri)
        assertEquals(original.answers, GuidedBriefOptions.withMusic(GuidedBriefOptions.withVoice(configured, null), null).answers)
    }

    @Test fun presetAnswersSurviveTheFirstTopicAndDoNotReappearAsQuestions() {
        var b = DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO)
        b = DirectorQuestions.apply(b, "duration", "60 seconds")
        b = DirectorQuestions.apply(b, "aspect", "9:16")
        b = DirectorQuestions.apply(b, "style", "Documentary")
        b = DirectorQuestions.apply(b, "audience", "Students")
        b = DirectorQuestions.seed(b, "Explain how solar power works for beginners")
        assertEquals(60, b.durationSec)
        assertEquals("9:16", b.aspect)
        assertEquals("Documentary", b.style)
        assertEquals("Students", b.audience)
        assertEquals("subtitles", DirectorQuestions.next(b)!!.key)
    }

    @Test fun musicIsRealBoundedEditableAndDoesNotDeleteManualAudio() {
        val manual = AudioItem("manual", "lane_audio_1", 0, 2_000_000, audioUri = "file:///manual.wav", origin = "manual")
        val base = StudioProject.createEmpty("test").copy(items = listOf(manual))
        val (p, notes) = TimelineAssembler.withBackgroundMusic(base, 10_000, BackgroundMusic("content://music", "track", 4_000, 0.4f))
        val generated = p.items.filterIsInstance<AudioItem>().single { it.id != "manual" }
        assertEquals("content://music", generated.audioUri)
        assertEquals(4_000_000L, generated.durationUs)
        assertEquals(0.4f, generated.volume, 0.0001f)
        assertTrue(generated.audioDucking)
        assertTrue(notes.single().contains("not looped"))
        assertTrue(p.items.any { it.id == "manual" })
        assertEquals(0f, AudioMixer.calculateEffectiveGain(generated, 0, false), 0f)
        assertTrue(AudioMixer.calculateEffectiveGain(generated, 2_000_000, true) < AudioMixer.calculateEffectiveGain(generated, 2_000_000, false))
        val (longer, _) = TimelineAssembler.withBackgroundMusic(p, 10_000, BackgroundMusic("content://new", "new", 30_000, 2f))
        assertEquals(2, longer.items.filterIsInstance<AudioItem>().size)
        val replaced = longer.items.filterIsInstance<AudioItem>().single { it.id != "manual" }
        assertEquals(10_000_000L, replaced.durationUs)
        assertEquals(1f, replaced.volume, 0f)
        assertEquals(listOf("manual"), TimelineAssembler.withBackgroundMusic(longer, 10_000, null).first.items.map { it.id })
    }

    @Test fun musicMetadataIsDefensiveAndRejectsInvalidInputs() {
        val b = brief()
        val malformed = b.copy(answers = mapOf(GuidedBriefOptions.MUSIC_URI to "content://x", GuidedBriefOptions.MUSIC_DURATION to "nope"))
        assertNull(GuidedBriefOptions.music(malformed))
        val safe = GuidedBriefOptions.withMusic(b, BackgroundMusic("content://x", "x", 1000, Float.NaN))
        assertEquals(0.2f, GuidedBriefOptions.music(safe)!!.volume, 0f)
        assertTrue(runCatching { GuidedBriefOptions.withMusic(b, BackgroundMusic("", "x", 1000)) }.isFailure)
        val (empty, notes) = TimelineAssembler.withBackgroundMusic(StudioProject.createEmpty(), 1000, BackgroundMusic("", "x", 1000))
        assertTrue(empty.items.isEmpty())
        assertTrue(notes.single().contains("not added"))
    }
}
