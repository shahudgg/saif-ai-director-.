package com.example.studio.composer

import com.example.studio.director.DirectorBrief
import com.example.studio.director.DirectorWorkflow
import org.junit.Assert.*
import org.junit.Test

class StyleDnaV2Test {
    @Test fun sameNormalizedBriefIsStableAndGeneratedSeedIsIgnored() {
        val a = DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO, prompt = "  BLACK   holes ", style = "Documentary", audience = "Curious adults")
        val b = a.copy(prompt = "black holes", style = " documentary ", answers = mapOf("styleSeed" to "1234"))
        assertEquals(StyleDNA.fromBrief(a), StyleDNA.fromBrief(b))
        assertEquals(StyleDNA.fromBrief(a).seed, StyleDNA.fromAnswer(StyleDNA.fromBrief(a).seed.toString())?.seed)
    }

    @Test fun fiveTopicsHaveDistinctSignatures() {
        val signatures = listOf("black holes", "urban gardening", "personal finance", "classical music", "ocean plastic")
            .map { StyleDNA.fromBrief(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO, prompt = it, style = "Documentary", audience = "Adults")).signature }
        assertEquals(5, signatures.toSet().size)
        assertTrue(signatures.all { it.startsWith("v2-") })
    }

    @Test fun sixtyScenesRemainUniqueAcrossTopicsAndAspects() {
        assertTrue(SceneVarietyComposer.ARCHETYPES.size >= 20)
        assertTrue(SceneVarietyComposer.LAYOUTS >= 3)
        for (aspect in listOf("9:16", "16:9", "1:1", "4:5")) for (topic in listOf("black holes", "urban gardening", "personal finance", "classical music", "ocean plastic")) {
            val dna = StyleDNA.fromBrief(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO, prompt = topic, aspect = aspect))
            val graphs = mutableListOf<com.example.studio.director.SceneGraph>()
            repeat(60) { i ->
                val beat = Beat(Intent.STATEMENT, "$topic ${i + 1}", support = "A different point about $topic number ${i + 1}.",
                    keywords = listOf(topic, "detail"), index = i, total = 60)
                val graph = SceneVarietyComposer.unique(beat, aspect, dna, null, graphs) ?: error("No valid scene $i for $topic ($aspect)")
                assertTrue(SceneUniqueness.repeatsOf(graph, graphs).isEmpty())
                val (w, h) = LayoutEngine.parseAspect(aspect)
                assertTrue(LayoutEngine.issues(graph, w, h, beat.durationMs).isEmpty())
                assertTrue(SceneLint.problems(graph, w, h).isEmpty())
                graphs += graph
            }
            assertTrue(SceneUniqueness.collisions(graphs).isEmpty())
            assertEquals(60, graphs.map(SceneLint::signature).toSet().size)
        }
    }

    @Test fun everyFallbackArchetypeHasThreeDistinctLayouts() {
        val beat = Beat(Intent.STATEMENT, "Subject", support = "A supporting detail", index = 0)
        val dna = StyleDNA.from(42)
        SceneVarietyComposer.ARCHETYPES.forEach { archetype ->
            val signatures = (0 until SceneVarietyComposer.LAYOUTS).map { layout ->
                SceneLint.signature(SceneVarietyComposer.compose(beat, "9:16", archetype, layout, dna, null))
            }
            assertEquals("Three layouts for ${archetype.name}", 3, signatures.toSet().size)
        }
    }

    @Test fun exhaustionFailsInsteadOfAcceptingCollision() {
        val beat = Beat(Intent.STATEMENT, "one subject", support = "Another detail", index = 0)
        val dna = StyleDNA.from(42)
        val known = mutableListOf<com.example.studio.director.SceneGraph>()
        repeat(3) { v -> known += SceneComposer.compose(beat, "9:16", variant = v, dna = dna) }
        SceneVarietyComposer.ARCHETYPES.forEach { archetype -> repeat(SceneVarietyComposer.LAYOUTS) { layout ->
            known += SceneVarietyComposer.compose(beat, "9:16", archetype, layout, dna, null)
        } }
        assertNull(SceneVarietyComposer.unique(beat, "9:16", dna, null, known))
    }
}
