package com.example.data.remote

/**
 * Modern Gemini Model identifiers (Active 2026).
 * Aligned with Gemini API system skill guidance.
 */
object GeminiModels {
    // Gemini 1.5 and 2.0 are retired by Google; these aliases always point at the current Flash / Flash-Lite.
    const val AGENT = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
    const val FAST = com.example.llm.GeminiModelCatalog.LITE_MODEL
    const val ROUTER = com.example.llm.GeminiModelCatalog.LITE_MODEL
    const val VERIFIER = com.example.llm.GeminiModelCatalog.LITE_MODEL

    val CHAT_FALLBACKS = com.example.llm.GeminiModelCatalog.STATIC_FALLBACKS

    val TRANSCRIPTION_FALLBACKS = listOf(
        com.example.llm.GeminiModelCatalog.DEFAULT_MODEL,
        "gemini-3.5-flash",
        "gemini-2.5-flash"
    )

    const val LIVE = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
    const val LIVE_LEGACY = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
    const val TTS = "gemini-3.8-flash-tts"
    const val TTS_LITE = "gemini-3.8-flash-lite-tts"
    const val TRANSCRIBE = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
    const val CHAT = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
}

enum class ModelRole {
    AGENT,
    FAST,
    ROUTER,
    VERIFIER,
    CHAT,
    LIVE,
    TRANSCRIBE
}
