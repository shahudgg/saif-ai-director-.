package com.example.studio.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePlanningTest {
    @Test fun unkTokensAreFlagged() {
        assertNotNull(ModelOutputGuard.reason("hello <unk><unk> world"))
        assertNull(ModelOutputGuard.reason("A perfectly normal sentence about AI."))
    }

    @Test fun arabicScriptFlaggedForHindiRequest() {
        val arabic = "\u0643\u064A\u0627 \u0627\u0646\u062A\u0649 \u0643\u0641\u0631\u0627 \u062A\u0641\u0643\u0631 \u0625\u0646 \u0627\u0644\u0645\u0627\u0634"
        assertNotNull(ModelOutputGuard.reason(arabic, "Hindi"))
    }

    @Test fun outlineIsContiguousAndMatchesTotal() {
        val script = "One two three four five six.\n\nSeven eight nine ten eleven twelve thirteen.\n\nFourteen fifteen sixteen."
        val o = ScenePlanner.outline(script, 20_000L)
        assertEquals(3, o.size)
        assertEquals(0L, o.first().startMs)
        o.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        assertEquals(20_000L, o.last().endMs)
        o.forEach { assertTrue(it.endMs - it.startMs >= 1800L) }
    }

    @Test fun singleParagraphIsSplitIntoSentenceGroups() {
        val sentence = "This is a sentence with exactly ten words in it okay. "
        val o = ScenePlanner.outline(sentence.repeat(12), 60_000L)
        assertTrue(o.size > 1)
    }

    @Test fun fallbackSceneIsValid() {
        val o = SceneOutline("s1", 0L, 4000L, "Machines learn patterns from data")
        val m = SceneManifest(scenes = listOf(ScenePlanner.fallbackScene(o, 0)))
        assertTrue(SceneValidator.fatal(SceneValidator.validate(m)).isEmpty())
    }
}
