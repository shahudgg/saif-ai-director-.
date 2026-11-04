package com.example.flow

import com.example.studio.director.Scene
import com.example.studio.director.SceneManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 14 tests for the Flow pipeline — pure logic, no device and no network:
 * what may become an asset, the plan JSON, pixel-driven transitions, placement decisions and the
 * prompt planner's fallback. These are the parts that broke in the previous build (profile photo
 * imported as an asset, prompts from the topic, no verification, no transitions).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowPipelineTest {

    // ---------------------------------------------------------------- media rules

    @Test
    fun profilePhotoAndChromeAreNeverAssets() {
        // The exact shape Google uses for the signed-in account avatar
        assertFalse(FlowMediaRules.isGeneratedCandidate("https://lh3.googleusercontent.com/a/ACg8ocLxyz=s96-c", 512, 512))
        assertFalse(FlowMediaRules.isGeneratedCandidate("https://lh3.googleusercontent.com/a-/ALV-UjX123", 512, 512))
        assertFalse(FlowMediaRules.isGeneratedCandidate("https://www.gstatic.com/images/branding/logo.png", 800, 800))
        assertFalse(FlowMediaRules.isGeneratedCandidate("https://flow.google.com/assets/icon-play.svg", 800, 800))
        // Real generated picture on the content CDN
        assertTrue(FlowMediaRules.isGeneratedCandidate("https://flow-content.googleusercontent.com/v1/abc123=w1024", 1024, 1536))
        // Too small to be a video frame
        assertFalse(FlowMediaRules.isGeneratedCandidate("https://flow-content.googleusercontent.com/v1/x", 120, 120))
        // Inside header/nav chrome
        assertFalse(FlowMediaRules.isGeneratedCandidate("https://flow-content.googleusercontent.com/v1/ok", 900, 900, insideChrome = true))
    }

    @Test
    fun extensionIsReadFromMagicBytesNotFromTheUrl() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)
        assertEquals("png", FlowMediaRules.extensionFor(png, "https://x/y.bin"))
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals("jpg", FlowMediaRules.extensionFor(jpg, "https://x/y"))
    }

    // ---------------------------------------------------------------- plan JSON

    @Test
    fun planSurvivesJsonRoundTrip() {
        val review = FlowReview(width = 1024, height = 1536, cropLoss = 0.02f, brightness = 0.2f,
            sharpness = 0.4f, needsGlow = true, glowStrength = 0.2f, needsBgRemove = true,
            paletteHex = "#102030", verdict = "OK · 1024x1536", aiVerdict = "FIT — matches narration")
        val plan = FlowPlan.fromSlotList("p1", "topic", "9:16", "neon", listOf(
            FlowSlot(index = 0, sceneId = "s1", role = FlowRoles.COVER, prompt = "a", sceneMs = 4000, status = FlowSlotStatus.VERIFIED,
                imagePath = "/tmp/a.png", imageHash = "abc", review = review),
            FlowSlot(index = 1, sceneId = "s1", role = FlowRoles.KEYFRAME, prompt = "b", sceneMs = 4000)
        ), listOf("note"))
            .withTransitions(listOf(FlowTransition(0, 1, "whip", 400, "different palette")))

        val decoded = FlowJson.planFromJson(FlowJson.planToJson(plan).toString())
        assertNotNull(decoded)
        assertEquals(2, decoded!!.slots.size)
        assertEquals(FlowRoles.KEYFRAME, decoded.slots[1].role)
        assertEquals(4000L, decoded.slots[0].sceneMs)
        assertEquals("whip", decoded.transitions[0].type)
        assertEquals(true, decoded.slots[0].review?.needsBgRemove)
        assertEquals("FIT — matches narration", decoded.slots[0].review?.aiVerdict)
        assertEquals(1, decoded.finishedCount)
    }

    // ---------------------------------------------------------------- transitions

    private fun review(palette: String, brightness: Float, sharpness: Float = 0.3f) =
        FlowReview(paletteHex = palette, brightness = brightness, sharpness = sharpness)

    @Test
    fun brightnessJumpPlansAFlashAndMatchingPalettesPlanADissolve() {
        val dark = FlowSlot(index = 0, sceneId = "s1", review = review("#101010", 0.10f))
        val bright = FlowSlot(index = 1, sceneId = "s2", review = review("#F0F0F0", 0.90f))
        val flash = FlowTransitions.between(dark, bright, 1, 4000L)
        assertEquals("flash", flash.type)
        assertTrue(flash.ms in 220L..600L)

        val a = FlowSlot(index = 0, sceneId = "s1", review = review("#6A4C93", 0.5f))
        val b = FlowSlot(index = 1, sceneId = "s2", review = review("#6C4E95", 0.52f))
        assertEquals("dissolve", FlowTransitions.between(a, b, 1, 4000L).type)
    }

    @Test
    fun longSceneGetsASlowPush() {
        val a = FlowSlot(index = 0, sceneId = "s1", review = review("#6A4C93", 0.5f))
        val b = FlowSlot(index = 1, sceneId = "s2", review = review("#3A2C73", 0.55f))
        val t = FlowTransitions.between(a, b, 1, 9000L)
        assertEquals("slideUp", t.type)
        assertEquals(600L, t.ms)
    }

    @Test
    fun everyPlannedTransitionExistsInTheRendererVocabulary() {
        val slots = listOf(
            FlowSlot(index = 0, sceneId = "s1", review = review("#000000", 0.1f)),
            FlowSlot(index = 1, sceneId = "s2", review = review("#FFFFFF", 0.9f)),
            FlowSlot(index = 2, sceneId = "s3", review = review("#00FF00", 0.5f, sharpness = 0.9f)),
            FlowSlot(index = 3, sceneId = "s4", review = review("#0000FF", 0.5f, sharpness = 0.9f))
        )
        val plan = FlowTransitions.plan(slots, listOf(3000L, 4000L, 5000L, 6000L))
        assertEquals(4, plan.size)
        plan.forEach { assertTrue("unknown transition " + it.type, it.type in com.example.studio.director.SUPPORTED_TRANSITIONS) }
    }

    // ---------------------------------------------------------------- placement

    @Test
    fun darkBackgroundGetsGlowAndFullFrameCover() {
        val slot = FlowSlot(index = 0, sceneId = "s1", role = FlowRoles.BACKGROUND,
            review = FlowReview(brightness = 0.12f, contrast = 0.1f, needsGlow = true, glowStrength = 0.25f,
                fit = "cover", paletteHex = "#101418"))
        val p = FlowPlacement.forSlot(slot, 5000L, "flash")
        assertEquals("backdrop", p.role)
        assertEquals(1f, p.width, 0.0001f)
        assertTrue(p.glow > 0.1f)
        assertTrue(p.keyframes.size >= 2)
        assertEquals("fadeIn", p.animIn)
        assertTrue(p.keyframes.last().tMs <= 5000L)
    }

    @Test
    fun flatBackgroundKeyVisualBecomesAChromaKeyedSticker() {
        val slot = FlowSlot(index = 1, sceneId = "s2", role = FlowRoles.KEYFRAME,
            review = FlowReview(cornerUniform = true, needsBgRemove = true, fit = "contain",
                brightness = 0.5f, contrast = 0.3f, paletteHex = "#22AA55"))
        val p = FlowPlacement.forSlot(slot, 4000L, "whip")
        assertEquals("sticker", p.role)
        assertTrue(p.chromaKey)
        assertTrue(p.width < 1f)
    }

    // ---------------------------------------------------------------- prompt planner

    @Test
    fun promptsFollowTheScenesAndFallBackWhenTheModelFails() = runBlocking {
        val scenes = (1..4).map { i ->
            Scene(id = "s$i", startMs = (i - 1) * 4000L, endMs = i * 4000L,
                narration = "Narration number $i about the ocean and its depth",
                visualObjective = "beat: ocean depth $i")
        }
        val manifest = SceneManifest(scenes = scenes)
        val planner = FlowPromptPlanner(ask = { Result.failure(IllegalStateException("no model")) })
        val plan = planner.build("p1", "ocean", "9:16", "cinematic", manifest, "script").getOrThrow()
        // one background per scene + a key visual for scene 2 (i % 3 == 1)
        assertEquals(5, plan.slots.size)
        assertEquals(FlowRoles.COVER, plan.slots[0].role)
        assertTrue(plan.slots[0].prompt.contains("ocean depth 1", ignoreCase = true))
        assertTrue(plan.slots[0].prompt.contains("9:16"))
        assertTrue(plan.slots[0].prompt.contains("No text"))
        assertTrue(plan.slots.any { it.role == FlowRoles.KEYFRAME && it.prompt.contains("chroma", true) })
        assertTrue(plan.notes.any { it.contains("locally") })
        assertEquals(4000L, plan.slots[0].sceneMs)
        assertEquals(manifest.scenes.size, plan.slots.map { it.sceneId }.distinct().size)
    }

    @Test
    fun modelPromptsAreUsedWhenTheModelAnswersValidJson() = runBlocking {
        val scenes = (1..2).map { i -> Scene(id = "s$i", startMs = (i - 1) * 3000L, endMs = i * 3000L,
            narration = "narration $i", visualObjective = "beat $i") }
        val json = """{"images":[{"sceneId":"s1","prompt":"a glowing whale in deep blue water, 35mm"},{"sceneId":"s2","prompt":"coral reef at sunrise, 50mm"}]}"""
        val planner = FlowPromptPlanner(ask = { Result.success(json) })
        val plan = planner.build("p1", "ocean", "9:16", "cinematic", SceneManifest(scenes = scenes), "").getOrThrow()
        assertTrue(plan.slots[0].prompt.startsWith("a glowing whale"))
        assertTrue(plan.slots[1].prompt.startsWith("coral reef"))
    }
}
