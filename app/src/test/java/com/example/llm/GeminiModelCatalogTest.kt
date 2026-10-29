package com.example.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelCatalogTest {
    @Test fun retiredFamiliesAreDetected() {
        listOf("gemini-1.5-flash", "models/gemini-1.5-pro", "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-pro", "gemini-1.0-pro")
            .forEach { assertTrue(it, GeminiModelCatalog.isRetired(it)) }
        listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-flash-latest", "gemini-3.8-flash-lite").forEach { assertFalse(it, GeminiModelCatalog.isRetired(it)) }
    }

    @Test fun staticFallbacksNeverContainRetiredModels() {
        assertTrue(GeminiModelCatalog.STATIC_FALLBACKS.none { GeminiModelCatalog.isRetired(it) })
    }

    @Test fun parseModelsKeepsOnlyUsableTextModelsBestFirst() {
        val body = """{"models":[
          {"name":"models/gemini-1.5-flash","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.5-flash","supportedGenerationMethods":["generateContent","countTokens"]},
          {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.8-flash-tts","supportedGenerationMethods":["generateContent"]},
          {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},
          {"name":"models/gemini-flash-latest","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.5-pro","supportedGenerationMethods":["countTokens"]}
        ]}"""
        val got = GeminiModelCatalog.parseModels(body)
        assertEquals("gemini-flash-latest", got.first())
        assertFalse(got.contains("gemini-1.5-flash"))
        assertFalse(got.contains("gemini-3.8-flash-tts"))
        assertFalse(got.contains("gemini-3.5-pro"))
        assertTrue(got.indexOf("gemini-3.5-flash") < got.indexOf("gemini-2.5-flash"))
        assertTrue(got.indexOf("gemini-3.5-flash") < got.indexOf("gemini-3.5-flash-lite"))
    }

    @Test fun garbageResponseGivesEmptyList() {
        assertTrue(GeminiModelCatalog.parseModels("not json").isEmpty())
        assertTrue(GeminiModelCatalog.parseModels("{}").isEmpty())
    }

    @Test fun chooseFromSkipsTriedAndDeadModels() {
        val live = listOf("gemini-flash-latest", "gemini-3.5-flash", "gemini-2.5-flash")
        assertEquals("gemini-3.5-flash", GeminiModelCatalog.chooseFrom(live, setOf("gemini-flash-latest")))
        assertEquals("gemini-2.5-flash", GeminiModelCatalog.chooseFrom(live, setOf("gemini-flash-latest"), setOf("models/gemini-3.5-flash")))
        assertNull(GeminiModelCatalog.chooseFrom(live, live.toSet()))
    }
}
