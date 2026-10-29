package com.example.studio.composer

import com.example.studio.director.Element
import com.example.studio.director.SceneGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneQualityTest {
    private fun g(vararg e: Element) = SceneGraph("#0B0A14", "#1B1330", elements = e.toList())

    // ---- SceneLint: the bad scenes seen in the user's video (empty yellow block, plain rectangles)
    @Test fun emptyFlatBlockIsRejected() {
        val bad = g(Element("r1", "rect", 0.5f, 0.4f, 0.8f, 0.6f, color = "#FFFF00"), Element("r2", "rect", 0.3f, 0.2f, 0.2f, 0.1f), Element("r3", "rect", 0.7f, 0.2f, 0.2f, 0.1f))
        val p = SceneLint.problems(bad, 9, 16)
        assertTrue(p.toString(), p.isNotEmpty())
    }

    @Test fun rectanglesWithoutLabelsExplainNothing() {
        val bad = g(Element("a", "rect", 0.25f, 0.3f, 0.3f, 0.15f), Element("b", "rect", 0.75f, 0.3f, 0.3f, 0.15f), Element("c", "rect", 0.5f, 0.55f, 0.3f, 0.15f),
            Element("t", "text", 0.5f, 0.1f, 0.8f, text = "Order types", size = 0.06f))
        assertTrue(SceneLint.problems(bad, 9, 16).any { it.contains("rectangles") })
    }

    @Test fun adoptCardsRenamesRectsThatHoldText() {
        val graph = g(Element("rect1", "rect", 0.5f, 0.3f, 0.6f, 0.2f), Element("lbl", "text", 0.5f, 0.31f, 0.5f, text = "Limit order", size = 0.05f))
        val out = SceneLint.adoptCards(graph, 9, 16)
        assertTrue(out.elements.first { it.type == "rect" }.id.startsWith("card"))
    }

    @Test fun rectWithoutTextKeepsItsId() {
        val graph = g(Element("rect1", "rect", 0.5f, 0.3f, 0.6f, 0.2f), Element("lbl", "text", 0.5f, 0.6f, 0.5f, text = "Far away", size = 0.05f))
        assertEquals("rect1", SceneLint.adoptCards(graph, 9, 16).elements.first { it.type == "rect" }.id)
    }

    @Test fun sameLayoutIsDetectedAndDifferentOneIsNot() {
        val a = g(Element("a", "rect", 0.5f, 0.3f, 0.6f, 0.2f), Element("b", "text", 0.5f, 0.3f, 0.5f, text = "One", size = 0.05f), Element("c", "circle", 0.2f, 0.5f, 0.1f, 0.1f))
        val b = g(Element("x", "rect", 0.5f, 0.3f, 0.6f, 0.2f), Element("y", "text", 0.5f, 0.3f, 0.5f, text = "Two", size = 0.05f), Element("z", "circle", 0.2f, 0.5f, 0.1f, 0.1f))
        val c = g(Element("p", "bars", 0.5f, 0.5f, 0.8f, 0.4f, values = listOf(1f, 2f)), Element("q", "text", 0.5f, 0.1f, 0.8f, text = "Chart", size = 0.06f))
        assertTrue(SceneLint.sameLook(a, b))
        assertFalse(SceneLint.sameLook(a, c))
    }

    // ---- StyleDNA: every video gets its own look
    @Test fun dnaIsDeterministicPerSeedAndDiffersBetweenSeeds() {
        assertEquals(StyleDNA.from(42), StyleDNA.from(42))
        val looks = (1..30).map { StyleDNA.from(it).palettes.map { p -> p.accent } + StyleDNA.from(it).backdrop }.toSet()
        assertTrue("expected many different looks, got ${looks.size}", looks.size >= 20)
    }

    @Test fun neighbouringBeatsNeverShareAPalette() {
        val dna = StyleDNA.from(7)
        for (i in 0 until 12) assertNotEquals(dna.palette(i, "neutral"), dna.palette(i + 1, "neutral"))
    }

    @Test fun answerRoundTrip() {
        val seed = StyleDNA.newSeed()
        assertEquals(StyleDNA.from(seed.toInt()), StyleDNA.fromAnswer(seed))
        assertNull(StyleDNA.fromAnswer(null)); assertNull(StyleDNA.fromAnswer("abc"))
    }

    // ---- composer with DNA still produces valid, clean scenes
    @Test fun composedScenesWithDnaStayLayoutClean() {
        val beat = Beat(Intent.STATEMENT, "Risk management matters", "Protect your money with a stop loss", listOf("risk", "money"), durationMs = 3200L, index = 2, total = 10)
        for (seed in 1..12) for (aspect in listOf("9:16", "16:9", "1:1")) {
            val graph = SceneComposer.compose(beat, aspect, dna = StyleDNA.from(seed))
            val (aw, ah) = LayoutEngine.parseAspect(aspect)
            val issues = LayoutEngine.issues(graph, aw, ah, 3200L)
            assertTrue("seed=$seed aspect=$aspect: $issues", issues.isEmpty())
        }
    }

    @Test fun iconWatermarkAppearsForTopicWords() {
        val beat = Beat(Intent.HOOK, "Trading can destroy your money", "market", listOf("trading", "market"), durationMs = 3000L, index = 0, total = 8)
        val graph = SceneComposer.compose(beat, "9:16", dna = StyleDNA.from(3))
        assertTrue(graph.elements.any { it.type == "icon" && it.text == "chart" })
    }

    // ---- IconPicker
    @Test fun iconPickerUnderstandsEnglishAndHinglish() {
        assertEquals("chart", IconPicker.pick(listOf("stock", "market")))
        assertEquals("warning", IconPicker.pick(listOf("galti", "mat", "karo")))
        assertEquals("coin", IconPicker.pick(listOf("paisa")))
        assertNull(IconPicker.pick(listOf("table", "banana")))
    }
}
