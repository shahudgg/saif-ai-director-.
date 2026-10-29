package com.example.studio.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 9 added nineteen new element primitives and fourteen new per-element look controls (glow, radius, shadow,
 * blend, gradient, skew, letterSpacing, weight, align, dash, outline, path). Every one of them is model-supplied,
 * so [clamped] is the only thing standing between a bad reply and a scene that draws outside the frame, asks the
 * renderer for a billion particles, or names a blend mode the canvas does not have.
 */
class ElementClampTest {

    private val wild = Element(
        id = "wild", type = "rect",
        x = 9f, y = -9f, w = 99f, h = -1f,
        size = 9f, strokeW = 9f, x2 = 9f, y2 = -9f,
        count = 999_999, seed = 0,
        glow = 9f, radius = 9f, shadow = -1f,
        blend = "banana", skew = 9f, letterSpacing = 9f,
        weight = "banana", align = "banana",
        dash = 9f, outline = -1f, path = "banana",
        values = (0 until 60).map { it.toFloat() },
        labels = (0 until 60).map { "label $it" },
        anims = (0 until 30).map { Anim("x", 0f, 1f, 0L, 100L) },
        text = "x".repeat(500)
    ).clamped()

    @Test fun geometryIsPulledBackInsideTheFrame() {
        assertTrue("x stays within half a frame of overflow", wild.x in -0.5f..1.5f)
        assertTrue("y stays within half a frame of overflow", wild.y in -0.5f..1.5f)
        assertTrue("width can never be negative", wild.w in 0f..3f)
        assertTrue("height can never be negative", wild.h in 0f..3f)
        assertTrue("x2/y2 are clamped too", wild.x2 in -0.5f..1.5f && wild.y2 in -0.5f..1.5f)
        assertEquals(1.5f, wild.x, 0.001f)
        assertEquals(-0.5f, wild.y, 0.001f)
        assertEquals(0f, wild.h, 0.001f)
    }

    @Test fun textMetricsStayDrawable() {
        assertEquals(0.4f, wild.size, 0.001f)
        assertEquals(0.08f, wild.strokeW, 0.001f)
        assertEquals(0.6f, wild.letterSpacing, 0.001f)
        assertEquals(1f, wild.skew, 0.001f)
    }

    @Test fun particleCountIsCappedSoACannotAskForABillionDots() {
        assertEquals(240, wild.count)
    }

    @Test fun aZeroSeedBecomesOneSoHashedMotionNeverCollapses() {
        assertEquals("seed 0 makes every pseudo-random element identical", 1, wild.seed)
        assertEquals(7, Element(id = "e", type = "particles", seed = 7).clamped().seed)
    }

    @Test fun lookControlsAreClampedToZeroToOne() {
        assertEquals(1f, wild.glow, 0.001f)
        assertEquals(0.5f, wild.radius, 0.001f)
        assertEquals(0f, wild.shadow, 0.001f)
        assertEquals(1f, wild.dash, 0.001f)
        assertEquals(0f, wild.outline, 0.001f)
    }

    @Test fun unknownEnumsFallBackToTheDefaultNotToGarbage() {
        assertEquals("Normal", wild.blend)
        assertEquals("bold", wild.weight)
        assertEquals("center", wild.align)
        assertEquals("an unknown motion path is dropped", "", wild.path)
    }

    @Test fun everyWhitelistedEnumSurvivesClamping() {
        SUPPORTED_ELEMENT_BLENDS.forEach { b ->
            assertEquals("blend $b must survive", b, Element(id = "e", type = "rect", blend = b).clamped().blend)
        }
        SUPPORTED_ELEMENT_WEIGHTS.forEach { w ->
            assertEquals("weight $w must survive", w, Element(id = "e", type = "text", weight = w).clamped().weight)
        }
        SUPPORTED_ELEMENT_ALIGNS.forEach { a ->
            assertEquals("align $a must survive", a, Element(id = "e", type = "text", align = a).clamped().align)
        }
        SUPPORTED_ELEMENT_PATHS.forEach { p ->
            assertEquals("path '$p' must survive", p, Element(id = "e", type = "orbit", path = p).clamped().path)
        }
    }

    @Test fun listsAreTruncatedToWhatTheRendererCanDraw() {
        assertEquals(40, wild.values.size)
        assertEquals(40, wild.labels.size)
        assertEquals(14, wild.anims.size)
        assertEquals(280, wild.text.length)
    }

    @Test fun aWellFormedElementPassesThroughUnchanged() {
        val good = Element(
            id = "g", type = "radar", x = 0.3f, y = 0.4f, w = 0.5f, h = 0.5f,
            size = 0.05f, strokeW = 0.004f, count = 40, seed = 7,
            glow = 0.4f, radius = 0.12f, shadow = 0.3f, blend = "Screen",
            skew = 0.2f, letterSpacing = 0.05f, weight = "black", align = "left",
            dash = 0.5f, outline = 0.5f, path = "sine",
            values = listOf(1f, 2f, 3f), labels = listOf("a", "b", "c"),
            anims = listOf(Anim("progress", 0f, 1f, 0L, 900L)),
            text = "Radar sweep"
        )
        assertEquals("clamping must be a no-op on valid input", good, good.clamped())
    }

    @Test fun clampingIsIdempotent() {
        val once = wild
        assertEquals(once, once.clamped())
    }

    @Test fun defaultsAreInsideEveryClampRange() {
        val d = Element(id = "d", type = "text").clamped()
        assertEquals(Element(id = "d", type = "text"), d)
        assertEquals("bold", d.weight)
        assertEquals("center", d.align)
        assertEquals("Normal", d.blend)
        assertEquals(0.12f, d.radius, 0.001f)
        assertEquals(0f, d.glow, 0.001f)
        assertEquals("", d.path)
    }

    @Test fun newRound9ElementTypesAreAllWhitelisted() {
        val added = listOf(
            "wave", "radar", "steps", "table", "quote", "ticker", "arcdial", "spotlight", "halftone",
            "scanline", "confetti", "beam", "ribbon", "compare", "heat", "spiral", "orbit", "stack",
            "badge", "barcode"
        )
        added.forEach { t ->
            assertTrue("'$t' must be a supported element type", t in SUPPORTED_ELEMENT_TYPES)
        }
        assertEquals("20 original + 20 advanced primitives", 40, SUPPORTED_ELEMENT_TYPES.size)
    }

    @Test fun newRound9TextEffectsAreAllWhitelisted() {
        listOf("scramble", "slide", "scale", "blur", "neon", "shadow3d", "karaoke", "bounce").forEach { fx ->
            assertTrue("'$fx' must be a supported text effect", fx in SUPPORTED_TEXT_FX)
        }
        listOf("typewriter", "words", "counter", "glow", "outline", "rise", "pop", "fade").forEach { fx ->
            assertTrue("the original effect '$fx' must still be supported", fx in SUPPORTED_TEXT_FX)
        }
    }

    @Test fun newBlendModesMatchWhatTheTimelineUnderstands() {
        // The scene renderer and the timeline's ItemTransform.blendMode must agree, or a scene exports differently
        // from the way it previews.
        SUPPORTED_BLEND_MODES.forEach { b ->
            assertTrue("'$b' must be a supported element blend", b in SUPPORTED_ELEMENT_BLENDS)
        }
    }
}
