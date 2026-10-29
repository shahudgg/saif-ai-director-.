package com.example.studio.director

import com.example.studio.model.ItemAnimation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 9: a placed asset keeps FULL editor control - position, size, keyframes, glow, blend and grade. These
 * tests pin the conversion from the normalized [AssetPlacement] into the timeline's own `ItemTransform`,
 * `ItemEffects`, `TransformKeyframe` and `ItemAnimation` values, because that conversion is where an asset
 * silently loses its animation or spins to the wrong angle.
 */
class PlacementApplierTest {

    private fun asset(
        kind: String = "image", ext: String = "png", transparent: Boolean = true,
        placement: AssetPlacement? = null
    ) = ResolvedAsset(
        sceneId = "s0", requestId = "lib-s0:visual", kind = kind, localPath = "/tmp/a.$ext",
        source = "telegram:x", verified = true, ext = ext, transparent = transparent, placement = placement
    )

    @Test fun transparentPngBecomesAStickerAndVideoIsForcedToABackdrop() {
        val sticker = PlacementApplier.of(asset(kind = "image", ext = "png", transparent = true), "9:16", 4000L)
        assertEquals("sticker", sticker.role)
        assertTrue("a sticker never covers the whole frame", sticker.width < 0.98f)

        // A model that asks for a small sticker made of an MP4 would produce a tiny floating video window,
        // which the preview cannot play - so clips are always full-frame.
        val clip = PlacementApplier.of(
            asset(kind = "video", ext = "mp4", transparent = false,
                placement = AssetPlacement(x = 0.2f, y = 0.2f, width = 0.3f, height = 0.2f, role = "sticker", fit = "contain")),
            "9:16", 4000L
        )
        assertEquals("backdrop", clip.role)
        assertEquals(1f, clip.width, 0.001f)
        assertEquals(1f, clip.height, 0.001f)
        assertEquals("cover", clip.fit)
    }

    @Test fun keyframesOutsideTheSceneAreDroppedSoNothingFreezes() {
        val long = AssetPlacement(
            role = "sticker", width = 0.5f, height = 0.4f,
            keyframes = listOf(PlacementKey(0L), PlacementKey(1500L), PlacementKey(9000L))
        )
        val p = PlacementApplier.of(asset(placement = long), "9:16", sceneDurationMs = 2000L)
        assertEquals("the 9 s keyframe is outside a 2 s scene", 2, p.keyframes.size)
        assertEquals(listOf(0L, 1500L), p.keyframes.map { it.tMs })
    }

    @Test fun anExplicitPlacementSurvivesTheRoundTrip() {
        val wanted = AssetPlacement(
            x = 0.3f, y = 0.7f, width = 0.45f, height = 0.3f, fit = "contain", role = "sticker",
            glow = 0.5f, drift = 0.2f, rotation = 8f, opacity = 0.9f, blend = "Screen"
        )
        val p = PlacementApplier.of(asset(placement = wanted), "9:16", 4000L)
        assertEquals(0.3f, p.x, 0.001f)
        assertEquals(0.7f, p.y, 0.001f)
        assertEquals(0.45f, p.width, 0.001f)
        assertEquals("contain", p.fit)
        assertEquals("sticker", p.role)
        assertEquals(0.5f, p.glow, 0.001f)
        assertEquals("Screen", p.blend)
    }

    @Test fun effectsCarryGlowShadowBlurAndGrade() {
        val p = AssetPlacement(
            glow = 0.6f, glowColor = "#FF00AA", shadow = 0.4f, blur = 0.2f,
            brightness = 1.2f, contrast = 0.9f, saturation = 1.4f, hue = 25f,
            vignette = 0.3f, chromaKey = true
        )
        val fx = PlacementApplier.effects(p)
        assertEquals(0.6f, fx.neonGlow, 0.001f)
        assertEquals(0x00FF00AAL, fx.neonColorHex and 0x00FFFFFFL)
        assertEquals(0.4f, fx.dropShadow, 0.001f)
        assertEquals(0.2f, fx.blur, 0.001f)
        assertEquals(1.2f, fx.brightness, 0.001f)
        assertEquals(0.9f, fx.contrast, 0.001f)
        assertEquals(1.4f, fx.saturation, 0.001f)
        assertEquals(25f, fx.hue, 0.001f)
        assertEquals(0.3f, fx.vignette, 0.001f)
        assertTrue(fx.chromaKey)
    }

    @Test fun anUnparsableGlowColourFallsBackInsteadOfCrashing() {
        val fx = PlacementApplier.effects(AssetPlacement(glow = 0.5f, glowColor = "not-a-colour"))
        // clamp() already replaced it, but the converter must be safe on its own too.
        assertEquals(0xFF00E5FFL, fx.neonColorHex)
    }

    /**
     * Regression guard. `transform` copies the placement rotation into `ItemTransform.rotation`, so the preview
     * must use THAT value alone. It used to add `p.rotation + t.rotation` and spun every placed asset twice as
     * far as the exported video did.
     */
    @Test fun transformCarriesThePlacementRotationExactlyOnce() {
        val p = AssetPlacement(rotation = 12f, opacity = 0.8f, blend = "Screen", role = "sticker")
        val t = PlacementApplier.transform(p)
        assertEquals("the rotation appears once, not twice", 12f, t.rotation, 0.001f)
        assertEquals(0.8f, t.opacity, 0.001f)
        assertEquals("Screen", t.blendMode)
        // Position and size live in the placement box, so the base offset must stay zero or the asset is doubled up.
        assertEquals(0f, t.x, 0.001f)
        assertEquals(0f, t.y, 0.001f)
        assertEquals(1f, t.scale, 0.001f)
    }

    @Test fun keyframeTimesAreAbsoluteTimelineMicroseconds() {
        val p = AssetPlacement(
            x = 0.5f, y = 0.5f, role = "sticker", width = 0.5f, height = 0.4f,
            keyframes = listOf(
                PlacementKey(tMs = 0L, x = 0.2f, y = 0.5f, scale = 0.8f),
                PlacementKey(tMs = 1000L, x = 0.8f, y = 0.5f, scale = 1.2f, rotation = 20f, opacity = 0.5f)
            )
        )
        val startUs = 5_000_000L           // the clip starts 5 s into the timeline
        val keys = PlacementApplier.keyframes(p, startUs, "9:16")
        assertEquals(2, keys.size)
        // TransformKeyframe.timeUs is ABSOLUTE, while PlacementKey.tMs is local to the clip.
        assertEquals(5_000_000L, keys[0].timeUs)
        assertEquals(6_000_000L, keys[1].timeUs)
        assertEquals(1.2f, keys[1].scale, 0.001f)
        assertEquals(20f, keys[1].rotation, 0.001f)
        assertEquals(0.5f, keys[1].opacity, 0.001f)
    }

    @Test fun keyframeOffsetsArePixelsInTheReferenceAspectSpace() {
        val p = AssetPlacement(
            x = 0.5f, y = 0.5f, role = "sticker",
            keyframes = listOf(PlacementKey(0L, x = 0.5f, y = 0.5f), PlacementKey(500L, x = 1f, y = 0.5f))
        )
        val vertical = PlacementApplier.keyframes(p, 0L, "9:16")
        // 9:16 reference is 1080x1920, so moving from x=0.5 to x=1.0 is +540 px and y stays put.
        assertEquals(0f, vertical[0].x, 0.5f)
        assertEquals(540f, vertical[1].x, 0.5f)
        assertEquals(0f, vertical[1].y, 0.5f)

        val wide = PlacementApplier.keyframes(p, 0L, "16:9")
        // 16:9 reference is 1920x1080, so the same normalized move is +960 px.
        assertEquals(960f, wide[1].x, 0.5f)
    }

    @Test fun aSingleKeyframeProducesNoAnimationAtAll() {
        val one = AssetPlacement(keyframes = listOf(PlacementKey(0L)))
        assertTrue(PlacementApplier.keyframes(one, 0L, "9:16").isEmpty())
        assertTrue(PlacementApplier.keyframes(AssetPlacement(), 0L, "9:16").isEmpty())
    }

    @Test fun entranceAndExitAnimationsAreConvertedToMicroseconds() {
        val p = AssetPlacement(animIn = "popIn", animInMs = 380L, animOut = "fadeOut", animOutMs = 260L)
        val inAnim = PlacementApplier.animIn(p)
        val outAnim = PlacementApplier.animOut(p)
        assertEquals(ItemAnimation("popIn", 380_000L, "easeOut"), inAnim)
        assertEquals(ItemAnimation("fadeOut", 260_000L, "easeIn"), outAnim)
    }

    @Test fun noEntranceAnimationMeansNoAnimationObject() {
        assertNull(PlacementApplier.animIn(AssetPlacement(animIn = "none", animInMs = 400L)))
        assertNull(PlacementApplier.animIn(AssetPlacement(animIn = "", animInMs = 400L)))
        assertNull(PlacementApplier.animIn(AssetPlacement(animIn = "popIn", animInMs = 0L)))
        assertNull(PlacementApplier.animOut(AssetPlacement(animOut = "none", animOutMs = 300L)))
        assertNull(PlacementApplier.animOut(AssetPlacement(animOut = "fadeOut", animOutMs = 0L)))
    }

    @Test fun describeReportsWhatTheOwnerCanSeeInTheChat() {
        val p = AssetPlacement(
            x = 0.5f, y = 0.38f, width = 0.52f, height = 0.34f, fit = "contain", role = "sticker",
            glow = 0.4f, blend = "Screen", animIn = "popIn", animOut = "fadeOut",
            keyframes = listOf(PlacementKey(0L), PlacementKey(500L))
        )
        val text = PlacementApplier.describe(p)
        assertTrue("role is named: $text", text.contains("sticker"))
        assertTrue("position is named: $text", text.contains("50%,38%"))
        assertTrue("size is named for a sticker: $text", text.contains("52x34%"))
        assertTrue("fit is named: $text", text.contains("contain"))
        assertTrue("glow is named: $text", text.contains("glow40%"))
        assertTrue("keyframes are counted: $text", text.contains("2 keyframes"))
        assertTrue("entrance is named: $text", text.contains("in:popIn"))
        assertTrue("exit is named: $text", text.contains("out:fadeOut"))
        assertTrue("blend is named: $text", text.contains("blend:Screen"))
    }

    @Test fun describeKeepsABackdropShort() {
        val text = PlacementApplier.describe(AssetPlacement(role = "backdrop", width = 1f, height = 1f))
        assertTrue(text.startsWith("backdrop"))
        assertFalse("a backdrop has no box size to report", text.contains("%x"))
        assertFalse(text.contains("glow"))
        assertFalse(text.contains("keyframes"))
    }
}
