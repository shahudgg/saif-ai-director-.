package com.example.studio.render

import com.example.studio.composer.Beat
import com.example.studio.composer.Intent
import com.example.studio.director.SfxPlanner
import com.example.studio.director.TransitionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueAndTransitionTest {

    private fun beat(i: Int, intent: Intent, dur: Long = 3000L) = Beat(intent, "x", items = listOf("a", "b", "c"), numberTokens = listOf("2017", "2020", "2023"), durationMs = dur, index = i, total = 20)

    @Test fun `consecutive transitions never repeat and the first beat has none`() {
        var prev: String? = null
        assertEquals("none", TransitionPlanner.pick(beat(0, Intent.HOOK), null))
        for (i in 1..40) {
            val t = TransitionPlanner.pick(beat(i, Intent.values()[i % Intent.values().size]), prev)
            assertNotEquals("beat $i repeated $t", prev, t)
            prev = t
        }
    }

    @Test fun `every planned transition is renderable`() {
        val known = com.example.studio.director.SUPPORTED_TRANSITIONS
        for (i in 1..60) assertTrue(TransitionPlanner.pick(beat(i, Intent.values()[i % Intent.values().size]), null) in known)
    }

    @Test fun `cues stay inside the scene and are not cluttered`() {
        for (intent in Intent.values()) {
            val cues = SfxPlanner.cuesFor(beat(3, intent), "whoosh")
            assertTrue("$intent: ${cues.size} cues", cues.size in 1..4)
            cues.forEach { assertTrue("$intent cue at ${it.atMs}", it.atMs in 0 until 3000L) }
        }
    }

    @Test fun `short beats drop cues that would fall outside them`() {
        SfxPlanner.cuesFor(beat(3, Intent.BIG_STAT, dur = 1800L), "zoomBlur").forEach { assertTrue(it.atMs < 1800L) }
    }

    @Test fun `geometric transitions are recognised and plain fades are not`() {
        assertTrue(TransitionFx.isGeometric("whip")); assertTrue(TransitionFx.isGeometric("iris"))
        assertTrue(!TransitionFx.isGeometric("fade")); assertTrue(!TransitionFx.isGeometric("dissolve")); assertTrue(!TransitionFx.isGeometric(null))
    }
}
