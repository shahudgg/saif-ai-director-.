package com.example.studio.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 9: the sequential asset hunt stores every creative decision (position, size, fit, role, glow, drift,
 * corner, chroma key) in a compact `@pl=` tag inside `TimelineItem.aiWhy`. These tests keep that tag honest —
 * if it stops round-tripping, every placed asset silently becomes a plain full-frame backdrop again.
 */
class AssetPlacementTest {

    @Test fun clampPullsNonsenseBackIntoTheFrame() {
        val wild = AssetPlacement(
            x = 9f, y = -9f, width = 0f, height = 40f,
            rotation = 900f, opacity = 5f, glow = 3f, blur = -2f,
            brightness = 0f, saturation = 99f, corner = 4f, drift = 7f,
            fit = "banana", role = "banana", blend = "banana",
            animIn = "banana", animOut = "banana",
            glowColor = "not-a-colour", chromaColor = "#12345",
            animInMs = 99_999L, animOutMs = -5L
        )
        val p = AssetPlacements.clamp(wild)
        assertTrue("x must stay near the frame", p.x in -0.2f..1.2f)
        assertTrue("y must stay near the frame", p.y in -0.2f..1.2f)
        assertTrue("width must be visible", p.width >= 0.04f)
        assertTrue("height is capped", p.height <= 2f)
        assertEquals(180f, p.rotation, 0.001f)
        assertEquals(1f, p.opacity, 0.001f)
        assertEquals(1f, p.glow, 0.001f)
        assertEquals(0f, p.blur, 0.001f)
        assertEquals(0.2f, p.brightness, 0.001f)
        assertEquals(2.5f, p.saturation, 0.001f)
        assertEquals(0.5f, p.corner, 0.001f)
        assertEquals(1f, p.drift, 0.001f)
        assertEquals("cover", p.fit)
        assertEquals("backdrop", p.role)
        assertEquals("Normal", p.blend)
        assertEquals("none", p.animIn)
        assertEquals("none", p.animOut)
        assertEquals("#00E5FF", p.glowColor)
        assertEquals("#00FF00", p.chromaColor)
        assertEquals(3000L, p.animInMs)
        assertEquals(0L, p.animOutMs)
    }

    @Test fun tagRoundTripsTheGeometryAndTheLook() {
        val p = AssetPlacement(
            x = 0.5f, y = 0.38f, width = 0.52f, height = 0.34f,
            fit = "contain", role = "sticker", glow = 0.4f, drift = 0.25f,
            rotation = 6f, opacity = 0.9f, shadow = 0.3f, blur = 0.1f,
            vignette = 0.2f, corner = 0.15f, chromaKey = true,
            chromaColor = "#00FF00", chromaTolerance = 0.3f
        )
        val tag = AssetPlacements.encodeTag(p)
        assertTrue("tag must start with @pl=, got: $tag", tag.startsWith("@pl="))
        val back = AssetPlacements.decodeTag(tag)
        assertNotNull("a written tag must always read back", back)
        back!!
        assertEquals(p.x, back.x, 0.005f)
        assertEquals(p.y, back.y, 0.005f)
        assertEquals(p.width, back.width, 0.005f)
        assertEquals(p.height, back.height, 0.005f)
        assertEquals(p.fit, back.fit)
        assertEquals(p.role, back.role)
        assertEquals(p.glow, back.glow, 0.005f)
        assertEquals(p.drift, back.drift, 0.005f)
        assertEquals(p.rotation, back.rotation, 0.5f)
        assertEquals(p.opacity, back.opacity, 0.005f)
        assertEquals(p.shadow, back.shadow, 0.005f)
        assertEquals(p.blur, back.blur, 0.005f)
        assertEquals(p.vignette, back.vignette, 0.005f)
        assertEquals(p.corner, back.corner, 0.005f)
        assertTrue(back.chromaKey)
        assertEquals(p.chromaTolerance, back.chromaTolerance, 0.005f)
    }

    /**
     * Regression: `encodeTag` writes exactly five fields before the optional extras, but `decodeTag` used to
     * require six and so returned null for EVERY plain placement — losing position, size, fit and role on reload.
     */
    @Test fun minimalFiveFieldTagStillDecodes() {
        val p = AssetPlacement(x = 0.25f, y = 0.75f, width = 0.4f, height = 0.3f, fit = "contain", role = "sticker")
        val tag = AssetPlacements.encodeTag(p)
        assertEquals("a bare placement has exactly five fields", 5, tag.removePrefix("@pl=").split(',').size)
        val back = AssetPlacements.decodeTag(tag)
        assertNotNull("the five-field tag must decode", back)
        assertEquals(0.25f, back!!.x, 0.005f)
        assertEquals("sticker", back.role)
        assertEquals("contain", back.fit)
    }

    @Test fun tagSurvivesInsideTheAiWhyString() {
        val p = AssetPlacement(x = 0.5f, y = 0.4f, width = 0.6f, height = 0.4f, fit = "cover", role = "sticker")
        val aiWhy = "director:s3:asset:hunter: cash burst for the crash beat @pl=" +
            AssetPlacements.encodeTag(p).removePrefix("@pl=") + " grp=2"
        val back = AssetPlacements.of(aiWhy)
        assertNotNull("the tag must be found inside a real aiWhy string", back)
        assertEquals("sticker", back!!.role)
        assertEquals(0.6f, back.width, 0.005f)
    }

    @Test fun decodeRejectsItemsWithoutATag() {
        assertNull(AssetPlacements.decodeTag(null))
        assertNull(AssetPlacements.decodeTag("director:s1:asset:legacy item with no placement"))
        assertNull(AssetPlacements.decodeTag("@pl=0.5,0.5"))
        assertNull(AssetPlacements.of("manual:user moved this"))
    }

    @Test fun isBackdropTagKeepsLegacyItemsBehavingAsBefore() {
        // Old projects have no tag: they fall back to the ":asset:" marker and render full-frame.
        assertTrue(AssetPlacements.isBackdropTag("director:s1:asset:some b-roll"))
        assertFalse(AssetPlacements.isBackdropTag("director:s1:graphics:generated"))
        assertFalse(AssetPlacements.isBackdropTag(null))
        // A tagged sticker is NOT a backdrop, even though its aiWhy still says ":asset:".
        val sticker = AssetPlacements.encodeTag(
            AssetPlacement(width = 0.5f, height = 0.35f, fit = "contain", role = "sticker")
        )
        assertFalse(AssetPlacements.isBackdropTag("director:s1:asset:x $sticker"))
        // A tagged backdrop is.
        val backdrop = AssetPlacements.encodeTag(AssetPlacement(role = "backdrop", width = 1f, height = 1f))
        assertTrue(AssetPlacements.isBackdropTag("director:s1:asset:x $backdrop"))
    }

    @Test fun defaultsFollowWhatTheFileActuallyIs() {
        val transparentPng = AssetPlacements.defaultFor("image", "png", transparent = true, aspect = "9:16")
        assertEquals("a transparent PNG is a sticker, never a backdrop", "sticker", transparentPng.role)
        assertEquals("contain", transparentPng.fit)
        assertTrue("a wide sticker is kept inside a narrow frame", transparentPng.width <= 0.62f)
        assertNotEqualsNone(transparentPng.animIn)

        val photo = AssetPlacements.defaultFor("image", "jpg", transparent = false, aspect = "16:9")
        assertEquals("an opaque photo fills the frame", "backdrop", photo.role)
        assertEquals(1f, photo.width, 0.001f)
        assertEquals("cover", photo.fit)

        val clip = AssetPlacements.defaultFor("video", "mp4", aspect = "9:16")
        assertEquals("video is always forced to a backdrop", "backdrop", clip.role)
        assertEquals("cover", clip.fit)

        val wide = AssetPlacements.defaultFor("image", "webp", transparent = true, aspect = "16:9")
        assertEquals("sticker", wide.role)
        assertTrue("a landscape frame may use the full sticker width", wide.width > 0.5f)
    }

    private fun assertNotEqualsNone(actual: String) =
        assertFalse("expected a real entrance animation, got 'none'", actual == "none")

    @Test fun referenceSizeMapsEverySupportedAspect() {
        assertEquals(1920 to 1080, AssetPlacements.referenceSize("16:9"))
        assertEquals(1080 to 1920, AssetPlacements.referenceSize("9:16"))
        assertEquals(1080 to 1080, AssetPlacements.referenceSize("1:1"))
        assertEquals(1080 to 1350, AssetPlacements.referenceSize("4:5"))
        assertEquals(1440 to 1080, AssetPlacements.referenceSize("4:3"))
        assertEquals(1920 to 817, AssetPlacements.referenceSize("2.35:1"))
        // Unknown or missing aspect must fall back to vertical, never crash or return 0.
        assertEquals(1080 to 1920, AssetPlacements.referenceSize(null))
        assertEquals(1080 to 1920, AssetPlacements.referenceSize("weird"))
    }

    @Test fun keyframesAreSortedUniqueAndNeverASingleFrozenPose() {
        val keys = listOf(
            PlacementKey(tMs = 900L, x = 0.7f),
            PlacementKey(tMs = 0L, x = 0.2f),
            PlacementKey(tMs = 900L, x = 0.7f),          // duplicate time
            PlacementKey(tMs = 450L, x = 0.5f, easing = "banana"),
            PlacementKey(tMs = -10L, x = 0.1f),          // before the clip
            PlacementKey(tMs = 1200L, x = 9f, scale = 90f, rotation = 900f, opacity = 3f)
        )
        val out = AssetPlacements.sanitizeKeys(keys)
        assertEquals("the duplicate time and the negative time are dropped", 4, out.size)
        assertEquals(listOf(0L, 450L, 900L, 1200L), out.map { it.tMs })
        assertEquals("an unknown easing falls back to a supported one", "easeOut", out[1].easing)
        assertTrue("an in-range pose is left alone", out[2].x <= 1.2f)
        // The last key asked for an impossible pose; every number must come back inside the safe ranges.
        assertEquals(1.2f, out[3].x, 0.001f)
        assertEquals(4f, out[3].scale, 0.001f)
        assertEquals(180f, out[3].rotation, 0.001f)
        assertEquals(1f, out[3].opacity, 0.001f)

        // One keyframe would freeze the asset at a single pose, which is never what a designer means.
        assertTrue(AssetPlacements.sanitizeKeys(listOf(PlacementKey(tMs = 100L))).isEmpty())
        assertTrue(AssetPlacements.sanitizeKeys(emptyList()).isEmpty())
    }

    @Test fun clampCapsKeyframeCountAtTwelve() {
        val many = (0 until 40).map { PlacementKey(tMs = it * 10L) }
        val p = AssetPlacements.clamp(AssetPlacement(keyframes = many))
        assertTrue("at most twelve keyframes survive", p.keyframes.size <= 12)
    }

    /**
     * Regression: the tag is DATA, so it must not follow the device locale. A comma-decimal locale used to write
     * "0,50" which `decodeTag` then split into two fields, corrupting the placement.
     */
    @Test fun tagNeverContainsALocaleDecimalComma() {
        val tag = AssetPlacements.encodeTag(
            AssetPlacement(x = 0.5f, y = 0.25f, width = 0.75f, height = 0.125f, glow = 0.5f, role = "sticker")
        )
        assertFalse("no decimal comma may appear in the tag: $tag", tag.contains("0,5"))
        assertFalse("no decimal comma may appear in the tag: $tag", tag.contains("0,2"))
        assertNotNull(AssetPlacements.decodeTag(tag))
    }

    @Test fun stickerAndBackdropPresetsAreSelfConsistent() {
        assertTrue(AssetPlacement.BACKDROP.isBackdrop)
        assertFalse(AssetPlacement.STICKER.isBackdrop)
        assertEquals("sticker", AssetPlacement.STICKER.role)
        // Both presets must already be inside the clamp ranges, so clamping is a no-op on them.
        assertEquals(AssetPlacement.BACKDROP, AssetPlacements.clamp(AssetPlacement.BACKDROP))
        assertEquals(AssetPlacement.STICKER, AssetPlacements.clamp(AssetPlacement.STICKER))
    }
}
