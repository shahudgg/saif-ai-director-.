package com.example.studio.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpatialSceneTest {
    @Test fun spatialTypesAreActuallyWhitelistedAndHandled() {
        val names = setOf("depthStack", "parallax", "extrusion", "perspectiveQuad", "perspectiveCard", "cube")
        assertTrue(SUPPORTED_ELEMENT_TYPES.containsAll(names))
        assertTrue(names.all(Scene3DFx::handles))
    }

    @Test fun cubeHasVisibleShadedFacesInBackToFrontOrder() {
        val faces = SpatialMath.cube(200f, 160f, 120f, -18f, 24f)
        assertEquals(3, faces.size)
        assertTrue(faces.all { it.normal.z > 0f })
        assertEquals(faces.sortedBy { it.depth }, faces)
        assertTrue(faces.map { SpatialMath.light(it.normal) }.distinct().size > 1)
    }

    @Test fun projectionHasPerspectiveAndASafeNearPlane() {
        val far = SpatialMath.project(SpatialMath.Vec3(10f, 5f, -1f), 3f)
        val near = SpatialMath.project(SpatialMath.Vec3(10f, 5f, 1f), 3f)
        val clipped = SpatialMath.project(SpatialMath.Vec3(10f, 5f, 99f), 3f)
        assertTrue(near.scale > far.scale)
        assertTrue(clipped.x.isFinite() && clipped.y.isFinite() && clipped.scale.isFinite())
        assertNotEquals(near.x, far.x, 0.001f)
    }

    @Test fun motionIsDeterministicAndChangesWithTimeAndSeed() {
        val a = SpatialMath.specularPosition(42, 1234L)
        assertEquals(a, SpatialMath.specularPosition(42, 1234L), 0f)
        assertNotEquals(a, SpatialMath.specularPosition(42, 2234L), 0.001f)
        assertNotEquals(a, SpatialMath.specularPosition(22000, 1234L), 0.001f)
        assertEquals(SpatialMath.cube(100f, 100f, 80f, -20f, 25f), SpatialMath.cube(100f, 100f, 80f, -20f, 25f))
    }

    @Test fun orbitAndTiltMoveLayersAtDifferentDepths() {
        val behind = SpatialMath.rotate(SpatialMath.Vec3(0f, 0f, -1f), 10f, 20f)
        val inFront = SpatialMath.rotate(SpatialMath.Vec3(0f, 0f, 1f), 10f, 20f)
        assertTrue(behind.x * inFront.x < 0f)
        assertTrue(behind.y * inFront.y < 0f)
    }

    @Test fun schemaRoundTripsAllSpatialControlsAndOldDefaults() {
        val old = SceneJson.parseGraph(org.json.JSONObject("""{"elements":[{"id":"old","type":"rect"}]}"""))
        assertEquals(CameraMove(), old.camera)
        assertEquals(0f, old.elements.single().depth, 0f)
        val graph = SceneGraph(camera = CameraMove(dollyFrom = -0.1f, dollyTo = 0.4f, orbitFrom = -15f,
            orbitTo = 25f, tiltFrom = 5f, tiltTo = -10f), elements = listOf(Element("c", "cube", depth = 0.6f,
            rotationX = 12f, rotationY = 35f, extrusion = 0.7f, specular = 0.8f, rimLight = 0.5f,
            anims = listOf(Anim("rotationY", 0f, 90f, 0L, 1000L)))))
        val decoded = SceneJson.decodeFromTimeline(SceneJson.encodeForTimeline(graph))!!
        assertEquals(graph, decoded)
        assertTrue(SceneValidator.fatal(SceneValidator.validate(SceneManifest(scenes = listOf(Scene("s", 0L, 2000L, graph = decoded))))).isEmpty())
        assertFalse(SceneJson.encodeForTimeline(old).contains("dollyFrom"))
    }

    @Test fun untrustedSpatialParametersAreBounded() {
        val e = Element("e", "cube", depth = 9f, rotationX = 999f, rotationY = -999f,
            extrusion = 8f, specular = -2f, rimLight = 4f).clamped()
        assertEquals(1f, e.depth, 0f); assertEquals(360f, e.rotationX, 0f); assertEquals(-360f, e.rotationY, 0f)
        assertEquals(1f, e.extrusion, 0f); assertEquals(0f, e.specular, 0f); assertEquals(1f, e.rimLight, 0f)
    }
}
