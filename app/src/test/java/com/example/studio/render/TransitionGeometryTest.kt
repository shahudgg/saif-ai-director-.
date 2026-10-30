package com.example.studio.render

import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ItemAnimation
import com.example.studio.model.StudioProject
import org.junit.Assert.*
import org.junit.Test

class TransitionGeometryTest {
    @Test fun posesAreBoundedDeterministicAndEndAtFullFrame() {
        val expected = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        for (pose in listOf(TransitionGeometry::pageFlip, TransitionGeometry::cubeSpin, TransitionGeometry::depthWipe)) {
            assertArrayEquals(expected, pose(1f), 0.0001f)
            repeat(101) { i ->
                val p = i / 100f
                val a = pose(p)
                assertArrayEquals(a, pose(p), 0f)
                assertTrue(a.all { it.isFinite() && it in 0f..1f })
            }
        }
    }

    @Test fun seededGlitchStripsAreMonotonicAndCoverExactlyAtEndpoints() {
        repeat(30) { strip ->
            assertEquals(0f, TransitionGeometry.glitchStrip(0f, 42, strip), 0f)
            assertEquals(1f, TransitionGeometry.glitchStrip(1f, 42, strip), 0f)
            val samples = (0..100).map { TransitionGeometry.glitchStrip(it / 100f, 42, strip) }
            assertTrue(samples.zipWithNext().all { (a, b) -> a <= b })
        }
        assertTrue((0..30).any { TransitionGeometry.glitchStrip(.5f, 42, it) != TransitionGeometry.glitchStrip(.5f, 43, it) })
    }

    @Test fun transitionRetainsPriorFrameOnlyDuringEntry() {
        val a = CodeSceneItem("a", "code", 0L, 1_000_000L, title = "A")
        val b = CodeSceneItem("b", "code", 1_000_000L, 1_000_000L, title = "B", animIn = ItemAnimation("pageflip", 500_000L))
        val project = StudioProject(items = listOf(a, b))
        assertEquals(listOf("a"), RenderPlan.at(project, 999_999L).visibleLayers.map { it.item.id })
        val during = RenderPlan.at(project, 1_100_000L).visibleLayers
        assertEquals(listOf("a", "b"), during.map { it.item.id })
        assertEquals(999_999L, during.first().localTimeUs)
        assertEquals(1f, during.last().opacity, 0f)
        assertEquals(listOf("b"), RenderPlan.at(project, 1_500_000L).visibleLayers.map { it.item.id })
        assertEquals(listOf("b"), RenderPlan.at(project.copy(items = listOf(a, b.copy(startUs = 1_010_000L))), 1_100_000L).visibleLayers.map { it.item.id })
    }
}
