package com.example.studio.composer

import com.example.studio.director.Scene
import com.example.studio.director.SceneManifest
import com.example.studio.director.SceneValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneComposerTest {

    private val aspects = listOf("9:16", "16:9", "1:1", "4:5")

    private fun sample(intent: Intent, idx: Int = 1, durMs: Long = 3200L) = Beat(
        intent = intent,
        headline = when (intent) { Intent.BIG_STAT -> "73%"; Intent.CTA -> "FOLLOW"; else -> "Attention changes everything" },
        support = when (intent) {
            Intent.COMPARISON -> "Old rules based systems vs modern neural networks that learn"
            Intent.MYTH_VS_FACT -> "Myth: AI thinks like us. Fact: it predicts the next token"
            Intent.QUESTION -> "Kya machine sach me samajhti hai?"
            Intent.QUOTE -> "\"All models are wrong but some are useful\""
            Intent.CTA -> "Comment karke batao kaunsa topic next chahiye"
            else -> "Transformers use attention to weigh every word against every other word in the sentence"
        },
        keywords = listOf("attention", "tokens", "layers", "weights"),
        numbers = listOf(12f, 35f, 58f, 90f),
        numberTokens = listOf("2017", "2019", "2021", "2024").let { if (intent == Intent.BIG_STAT) listOf("73%") else it },
        items = listOf("Tokenize the text", "Compute attention", "Predict next word", "Repeat"),
        durationMs = durMs, index = idx, total = 12
    )

    private fun everyCase(block: (Intent, String, Int, Beat, com.example.studio.director.SceneGraph) -> Unit) {
        for (intent in Intent.values()) for (aspect in aspects) for (variant in 0..2) {
            val beat = sample(intent, idx = variant)
            block(intent, aspect, variant, beat, SceneComposer.compose(beat, aspect, variant))
        }
    }

    @Test fun `every intent and variant yields a valid scene graph in every aspect`() = everyCase { intent, aspect, v, beat, g ->
        val m = SceneManifest(scenes = listOf(Scene("s1", 0, beat.durationMs, graph = g)))
        val fatal = SceneValidator.fatal(SceneValidator.validate(m))
        assertTrue("$intent/$aspect/v$v invalid: $fatal", fatal.isEmpty())
    }

    @Test fun `layout is clean - no overflow, collisions, caption-zone text or low contrast`() = everyCase { intent, aspect, v, beat, g ->
        val (aw, ah) = LayoutEngine.parseAspect(aspect)
        val issues = LayoutEngine.issues(g, aw, ah, beat.durationMs)
        assertTrue("$intent/$aspect/v$v layout issues: $issues", issues.isEmpty())
    }

    @Test fun `text is always drawn after shapes so it cannot hide behind them`() = everyCase { intent, aspect, v, _, g ->
        val firstText = g.elements.indexOfFirst { it.type == "text" }
        val lastShape = g.elements.indexOfLast { it.type in setOf("rect", "circle", "ring", "donut", "bars", "linechart", "nodes") }
        assertTrue("$intent/$aspect/v$v text before shape", firstText < 0 || firstText > lastShape)
    }

    @Test fun `scene is never static - camera moves and elements animate for the whole scene`() = everyCase { intent, _, v, beat, g ->
        val cam = g.camera
        val camMoves = cam.zoomFrom != cam.zoomTo || cam.panXFrom != cam.panXTo || cam.panYFrom != cam.panYTo
        assertTrue("$intent/v$v camera static", camMoves)
        assertTrue("$intent/v$v has no animated element", g.elements.any { it.anims.isNotEmpty() })
        assertTrue("$intent/v$v particles missing", g.elements.any { it.type == "particles" })
        assertTrue("$intent/v$v too many elements ${g.elements.size}", g.elements.size <= LayoutEngine.MAX_ELEMENTS)
    }

    @Test fun `three variants of an intent are visibly different`() {
        for (intent in listOf(Intent.HOOK, Intent.BIG_STAT, Intent.LIST, Intent.COMPARISON, Intent.PROCESS_STEPS, Intent.MYTH_VS_FACT, Intent.QUESTION, Intent.CTA, Intent.STATEMENT, Intent.NETWORK_DIAGRAM)) {
            val sigs = (0..2).map { v ->
                SceneComposer.compose(sample(intent), "9:16", v).elements.joinToString("|") { "${it.type}@${"%.2f".format(it.x)},${"%.2f".format(it.y)}" }
            }.toSet()
            assertEquals("$intent variants are not distinct", 3, sigs.size)
        }
    }

    @Test fun `neighbouring beats use different palettes`() {
        val a = SceneComposer.compose(sample(Intent.STATEMENT, 0), "9:16")
        val b = SceneComposer.compose(sample(Intent.STATEMENT, 1), "9:16")
        assertTrue(a.bgTop != b.bgTop || a.bgBottom != b.bgBottom)
    }

    @Test fun `repair fixes a model-written graph with overflowing overlapping text`() {
        val long = "This is an extremely long headline that a weak model wrote without thinking about how wide the frame is"
        val g = com.example.studio.director.SceneGraph(
            elements = listOf(
                com.example.studio.director.Element("t1", "text", 0.5f, 0.30f, 0.9f, 0.1f, color = "#FFFFFF", text = long, size = 0.09f),
                com.example.studio.director.Element("t2", "text", 0.5f, 0.31f, 0.9f, 0.1f, color = "#222222", text = "second line on top of first", size = 0.06f),
                com.example.studio.director.Element("r1", "rect", 0.5f, 0.3f, 0.8f, 0.3f, color = "#444444")
            )
        )
        val fixed = LayoutEngine.repair(g, 9, 16, 3000L).graph
        assertTrue(LayoutEngine.issues(fixed, 9, 16, 3000L).toString(), LayoutEngine.issues(fixed, 9, 16, 3000L).isEmpty())
        assertEquals("text", fixed.elements.last().type)
    }

    @Test fun `local analyzer picks sensible intents without any model`() {
        val s = LocalBeatAnalyzer
        assertEquals(Intent.HOOK, s.analyze("AI kitna powerful hai?", 0, 10, 3000).intent)
        assertEquals(Intent.BIG_STAT, s.analyze("Almost 73% of companies already use AI", 2, 10, 3000).intent)
        assertEquals(Intent.COMPARISON, s.analyze("Humans learn slowly vs machines that learn fast", 3, 10, 3000).intent)
        assertEquals(Intent.QUESTION, s.analyze("Par kya machine sach me samajhti hai?", 4, 10, 3000).intent)
        assertEquals(Intent.CTA, s.analyze("Follow karo aur comment me batao", 9, 10, 3000).intent)
        assertEquals(Intent.TIMELINE, s.analyze("In 2017 transformers arrived and by 2023 chatbots exploded", 5, 10, 3000).intent)
    }

    @Test fun `analyzer headline is short and numbers are extracted`() {
        val b = LocalBeatAnalyzer.analyze("ChatGPT ne sirf 5 din me 1 million users bana liye", 2, 10, 3000)
        assertTrue(b.headline.split(" ").size <= 5)
        assertTrue(b.numbers.contains(1f) || b.numbers.contains(5f))
    }
}
