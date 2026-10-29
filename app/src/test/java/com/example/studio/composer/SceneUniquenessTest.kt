package com.example.studio.composer

import com.example.studio.director.CameraMove
import com.example.studio.director.Element
import com.example.studio.director.SceneGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 9: the owner's complaint was that every video - and every scene inside one video - came out looking like
 * the same template. `SceneLint.tooSimilar` / `SceneUniqueness` are the gate that stops it: a scene is only
 * accepted when its primitive family, layout, palette and camera are genuinely its own.
 */
class SceneUniquenessTest {

    private fun el(type: String, x: Float, y: Float, color: String = "#FFFFFF") =
        Element(id = "$type-$x-$y", type = type, x = x, y = y, color = color)

    private fun graph(vararg elements: Element, camera: CameraMove = CameraMove()) =
        SceneGraph(camera = camera, elements = elements.toList())

    /** rect + text, the most common AI scene. */
    private val a = graph(el("rect", 0.2f, 0.2f), el("text", 0.5f, 0.5f))
    /** byte-for-byte the same design. */
    private val aCopy = graph(el("rect", 0.2f, 0.2f), el("text", 0.5f, 0.5f))
    /** the same recipe nudged a little to the right and down. */
    private val aShifted = graph(el("rect", 0.3f, 0.3f), el("text", 0.6f, 0.6f))
    /** the same recipe, nudged, with a different camera move. */
    private val aShiftedOtherCamera = graph(
        el("rect", 0.3f, 0.3f), el("text", 0.6f, 0.6f),
        camera = CameraMove(zoomFrom = 1f, zoomTo = 1.3f)
    )
    /** a genuinely different primitive family. */
    private val different = graph(el("circle", 0.2f, 0.2f), el("text", 0.5f, 0.5f))
    /** a richer scene: radar + steps + text, nothing like the others. */
    private val rich = graph(el("radar", 0.3f, 0.4f), el("steps", 0.7f, 0.6f), el("text", 0.5f, 0.15f))

    // ---------------------------------------------------------------- fingerprint

    @Test fun fingerprintIgnoresDecorSoGlowAndParticlesCannotDisguiseACopy() {
        val plain = fingerprintOf(a)
        val decorated = fingerprintOf(graph(el("rect", 0.2f, 0.2f), el("text", 0.5f, 0.5f),
            el("particles", 0.5f, 0.5f), el("glow", 0.1f, 0.1f), el("vignette", 0.5f, 0.5f)))
        assertEquals("decor is not part of the layout signature", plain, decorated)
    }

    private fun fingerprintOf(g: SceneGraph) = SceneLint.fingerprint(g)

    @Test fun fingerprintIsCoarseSoSmallNudgesDoNotCountAsANewDesign() {
        assertEquals(fingerprintOf(a), fingerprintOf(aCopy))
        assertTrue("positions are quantised", fingerprintOf(a).all { it.contains('@') })
    }

    @Test fun fingerprintIsEmptyForADecorOnlyScene() {
        assertTrue(fingerprintOf(graph(el("particles", 0.5f, 0.5f), el("grain", 0.5f, 0.5f))).isEmpty())
        assertTrue(fingerprintOf(SceneGraph()).isEmpty())
    }

    // ---------------------------------------------------------------- sameLook

    @Test fun identicalScenesLookTheSame() {
        assertTrue(SceneLint.sameLook(a, aCopy))
        assertTrue(SceneLint.sameLook(a, a))
    }

    @Test fun differentFamiliesNeverLookTheSame() {
        assertFalse(SceneLint.sameLook(a, different))
        assertFalse(SceneLint.sameLook(a, rich))
    }

    @Test fun emptyScenesNeverLookTheSame() {
        assertFalse("two empty scenes are not a copy of each other", SceneLint.sameLook(SceneGraph(), SceneGraph()))
        assertFalse(SceneLint.sameLook(a, SceneGraph()))
    }

    // ---------------------------------------------------------------- structure / family

    @Test fun structureIgnoresPositionsButCountsPrimitivesPaletteAndCamera() {
        assertEquals("the same recipe in different places is the same structure",
            SceneLint.structure(a), SceneLint.structure(aShifted))
        assertFalse("a different camera is a different structure",
            SceneLint.structure(a) == SceneLint.structure(aShiftedOtherCamera))
        assertFalse("a different primitive family is a different structure",
            SceneLint.structure(a) == SceneLint.structure(different))
    }

    @Test fun structureSeesRecoloursAsADifferentDesign() {
        val recoloured = graph(el("rect", 0.2f, 0.2f, "#FF0000"), el("text", 0.5f, 0.5f, "#00FF00"))
        assertFalse("palette is part of the structure", SceneLint.structure(a) == SceneLint.structure(recoloured))
        assertEquals("but the primitive recipe is not", SceneLint.family(a), SceneLint.family(recoloured))
    }

    @Test fun familyCountsPrimitivesOnly() {
        assertEquals("rect1+text1", SceneLint.family(a))
        assertEquals(SceneLint.family(a), SceneLint.family(aShifted))
        assertEquals("circle1+text1", SceneLint.family(different))
        assertFalse(SceneLint.family(a) == SceneLint.family(different))
        assertEquals("decor does not change the family", SceneLint.family(a),
            SceneLint.family(graph(el("rect", 0.2f, 0.2f), el("text", 0.5f, 0.5f), el("flash", 0.5f, 0.5f))))
    }

    @Test fun familyIsBlankForADecorOnlyScene() {
        assertEquals("", SceneLint.family(graph(el("particles", 0.5f, 0.5f))))
        assertEquals("", SceneLint.family(SceneGraph()))
    }

    // ---------------------------------------------------------------- tooSimilar

    @Test fun anExactCopyIsRejected() {
        assertTrue(SceneLint.tooSimilar(a, aCopy))
    }

    @Test fun theSameRecipeShiftedALittleIsRejected() {
        // This is the case a plain fingerprint check misses: same shapes, moved by a few percent.
        assertTrue("same family + same structure is a template repeat", SceneLint.tooSimilar(a, aShifted))
        assertFalse(SceneLint.sameLook(a, aShifted))
    }

    @Test fun aDifferentCameraMakesTheSameLayoutAcceptable() {
        assertFalse("same boxes but a different camera move reads as a different scene",
            SceneLint.tooSimilar(a, aShiftedOtherCamera))
    }

    @Test fun aDifferentPrimitiveFamilyIsAlwaysAcceptable() {
        assertFalse(SceneLint.tooSimilar(a, different))
        assertFalse(SceneLint.tooSimilar(a, rich))
        assertFalse(SceneLint.tooSimilar(different, rich))
    }

    @Test fun recolouringTheSameLayoutIsRejected() {
        val recoloured = graph(el("rect", 0.2f, 0.2f, "#FF0000"), el("text", 0.5f, 0.5f, "#00FF00"))
        assertTrue("same layout, new colours is still the same template", SceneLint.tooSimilar(a, recoloured))
    }

    @Test fun tooSimilarIsSymmetric() {
        assertEquals(SceneLint.tooSimilar(a, aShifted), SceneLint.tooSimilar(aShifted, a))
        assertEquals(SceneLint.tooSimilar(a, different), SceneLint.tooSimilar(different, a))
        assertEquals(SceneLint.tooSimilar(a, aShiftedOtherCamera), SceneLint.tooSimilar(aShiftedOtherCamera, a))
    }

    @Test fun emptyScenesAreNeverTooSimilar() {
        assertFalse(SceneLint.tooSimilar(SceneGraph(), SceneGraph()))
        assertFalse(SceneLint.tooSimilar(a, SceneGraph()))
        assertFalse(SceneLint.tooSimilar(graph(el("particles", 0.5f, 0.5f)), graph(el("glow", 0.5f, 0.5f))))
    }

    // ---------------------------------------------------------------- SceneUniqueness

    @Test fun repeatsOfNamesEveryAcceptedSceneTheCandidateCopies() {
        val accepted = listOf(a, different, aCopy, rich)
        val hits = SceneUniqueness.repeatsOf(aCopy, accepted)
        assertEquals(listOf(0, 2), hits)
    }

    @Test fun repeatsOfIsEmptyForAFreshDesign() {
        assertTrue(SceneUniqueness.repeatsOf(rich, listOf(a, different)).isEmpty())
        assertTrue(SceneUniqueness.repeatsOf(a, emptyList()).isEmpty())
    }

    @Test fun repeatsOfPutsTheClosestCopyFirst() {
        // Both are repeats of `a`, but the exact copy shares the whole fingerprint while the shifted one only
        // shares one element - so the exact copy must be reported as the worst offender.
        val accepted = listOf(aShifted, aCopy)
        val hits = SceneUniqueness.repeatsOf(a, accepted)
        assertEquals("both repeats are reported", 2, hits.size)
        assertEquals("the exact copy outranks the shifted one", 1, hits.first())
    }

    @Test fun reasonNamesTheSceneThatWasCopiedAndAsksForANewFamily() {
        val why = SceneUniqueness.reason(aCopy, listOf(a), indexInVideo = 2)
        assertNotNull(why)
        why!!
        assertTrue("it names the new scene: $why", why.contains("scene 3"))
        assertTrue("it names the scene that was copied: $why", why.contains("scene 1"))
        assertTrue("it tells the model what to do: $why", why.contains("Redesign"))
        assertTrue("it offers concrete alternatives: $why", why.contains("radar"))
    }

    @Test fun reasonIsNullWhenTheDesignIsFresh() {
        assertNull(SceneUniqueness.reason(rich, listOf(a, different), indexInVideo = 0))
        assertNull(SceneUniqueness.reason(a, emptyList(), indexInVideo = 0))
    }

    @Test fun collisionsListsEverySimilarPairExactlyOnce() {
        val pairs = SceneUniqueness.collisions(listOf(a, aCopy, different, aShifted))
        assertTrue("scene 1 and 2 are the same design", (0 to 1) in pairs)
        assertTrue("scene 1 and 4 are the same recipe shifted", (0 to 3) in pairs)
        assertFalse("the circle scene is nobody's copy", pairs.any { it.first == 2 || it.second == 2 })
        assertEquals("each pair is reported once, low index first", pairs.size, pairs.distinct().size)
        assertTrue(pairs.all { it.first < it.second })
    }

    @Test fun collisionsSkipsScenesThatHaveNoGraph() {
        val pairs = SceneUniqueness.collisions(listOf(a, null, aCopy, null))
        assertEquals(listOf(0 to 2), pairs)
        assertTrue(SceneUniqueness.collisions(listOf(null, null)).isEmpty())
        assertTrue(SceneUniqueness.collisions(emptyList()).isEmpty())
    }

    @Test fun collisionsIsCleanForAWellDesignedVideo() {
        val radar = graph(el("radar", 0.3f, 0.4f), el("text", 0.5f, 0.85f))
        val steps = graph(el("steps", 0.5f, 0.5f), el("text", 0.2f, 0.2f), el("arcdial", 0.8f, 0.3f))
        val quote = graph(el("quote", 0.5f, 0.5f), el("line", 0.5f, 0.7f))
        assertTrue("a video built from different families has no collisions",
            SceneUniqueness.collisions(listOf(radar, steps, quote, rich)).isEmpty())
    }
}
