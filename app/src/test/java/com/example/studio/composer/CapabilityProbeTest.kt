package com.example.studio.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityProbeTest {

    @Test fun `big model names are STRONG and small ones are LIGHT`() {
        assertEquals(Strategy.STRONG, CapabilityProbe.heuristic("Groq", "llama-3.3-70b-versatile"))
        assertEquals(Strategy.STRONG, CapabilityProbe.heuristic("OpenAI", "gpt-4o"))
        assertEquals(Strategy.STRONG, CapabilityProbe.heuristic("Anthropic", "claude-sonnet"))
        assertEquals(Strategy.LIGHT, CapabilityProbe.heuristic("Groq", "llama-3.1-8b-instant"))
        assertEquals(Strategy.LIGHT, CapabilityProbe.heuristic("OpenAI", "gpt-4o-mini"))
        assertEquals(Strategy.STANDARD, CapabilityProbe.heuristic("Custom", "some-unknown-model"))
    }

    @Test fun `probe results override optimistic names`() {
        assertEquals(Strategy.LIGHT, CapabilityProbe.decide(jsonOk = false, hindiOk = true, latencyMs = 500, hint = Strategy.STRONG))
        assertEquals(Strategy.STANDARD, CapabilityProbe.decide(jsonOk = true, hindiOk = false, latencyMs = 500, hint = Strategy.STRONG))
        assertEquals(Strategy.STRONG, CapabilityProbe.decide(jsonOk = true, hindiOk = true, latencyMs = 3_000, hint = Strategy.STRONG))
    }

    @Test fun `very slow strong models are downgraded and small models can be promoted when fast`() {
        assertEquals(Strategy.STANDARD, CapabilityProbe.decide(true, true, 60_000, Strategy.STRONG))
        assertEquals(Strategy.STANDARD, CapabilityProbe.decide(true, true, 2_000, Strategy.LIGHT))
        assertEquals(Strategy.LIGHT, CapabilityProbe.decide(true, true, 20_000, Strategy.LIGHT))
    }
}
