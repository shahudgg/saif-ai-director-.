package com.example

import com.jarvis.assistant.SaifApiConfig
import com.jarvis.assistant.SaifAutomation
import com.jarvis.assistant.data.model.ConversationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 10: live baatcheet has to work on EVERY API provider, not only on Gemini Live.
 *
 * `SaifAutomation.startLive` routes the session like this:
 *
 *   • no key at all           -> no engine; the Settings panel is opened instead
 *   • a Gemini key            -> the automation module's full-duplex Gemini Live WebSocket
 *   • any other provider key  -> SAIF AI's own turn-based engine (`SaifHostLiveEngine`): `SttRouter`
 *     for speech-to-text (Groq Whisper / OpenAI Whisper / Gemini, with fallbacks across every saved
 *     key), `AIApiUtility.executeWithFallback` for the reply, on-device `TTSManager` for the voice,
 *     and `SaifLiveScreen` so a shared screen reaches the model as an image instead of a socket
 *     stream.
 *
 * The tests only touch `SaifApiConfig` (a plain data class) and read SAIF AI's own live state, so
 * they run as plain JVM JUnit tests with no Android runtime and no JSON APIs.
 */
class LiveEngineRoutingTest {

    /** The engine a live session runs on, exactly as `SaifAutomation.startLive` decides it. */
    private enum class Engine { NONE, GEMINI_LIVE, HOST_TURN_BASED }

    private fun engineFor(provider: String, apiKey: String): Engine {
        val cfg = SaifApiConfig(provider = provider, apiKey = apiKey, model = "test-model")
        if (!cfg.hasKey) return Engine.NONE
        return if (cfg.isGemini) Engine.GEMINI_LIVE else Engine.HOST_TURN_BASED
    }

    @Test
    fun aGeminiKeyStillUsesTheFullDuplexLiveSocket() {
        assertEquals(Engine.GEMINI_LIVE, engineFor("Gemini", "AIzaSy-test-key"))
    }

    @Test
    fun everyOtherProviderFallsBackToTheHostTurnBasedEngine() {
        val providers = listOf(
            "OpenAI", "openai", "Anthropic", "Claude", "Groq", "OpenRouter",
            "Mistral", "DeepSeek", "Together AI", "Custom", "custom-endpoint", "LLM Studio"
        )
        for (provider in providers) {
            assertEquals(
                "$provider must not need the Gemini Live WebSocket",
                Engine.HOST_TURN_BASED,
                engineFor(provider, "sk-test-key")
            )
        }
    }

    @Test
    fun aGeminiProviderIsRecognisedWhateverWayItIsSpelled() {
        for (provider in listOf("gemini", "GEMINI", "Google Gemini", "gemini-2.5-pro", "Gemini Live")) {
            assertTrue("$provider should count as Gemini", SaifApiConfig(provider, "key", "").isGemini)
        }
        for (provider in listOf("OpenAI", "Anthropic", "Groq", "OpenRouter", "Custom")) {
            assertFalse("$provider should not count as Gemini", SaifApiConfig(provider, "key", "").isGemini)
        }
    }

    @Test
    fun aBlankProviderDefaultsToGeminiSoTheOldBehaviourIsUnchanged() {
        assertTrue(SaifApiConfig("", "AIzaSy-key", "").isGemini)
        assertEquals(Engine.GEMINI_LIVE, engineFor("", "AIzaSy-key"))
    }

    @Test
    fun withoutAnyKeyNoEngineStartsAndTheSettingsPanelIsOpened() {
        assertFalse(SaifApiConfig("OpenAI", "", "").hasKey)
        assertFalse(SaifApiConfig("OpenAI", "   ", "").hasKey)
        assertEquals(Engine.NONE, engineFor("OpenAI", ""))
        assertEquals(Engine.NONE, engineFor("Gemini", "  "))
    }

    // ---- the state contract the orb, the captions and the buttons read -----------------------

    @Test
    fun beforeASessionStartsTheLiveStateIsIdleAndNoEngineIsSelected() {
        assertEquals(SaifAutomation.EngineKind.NONE, SaifAutomation.activeEngine.value)
        assertFalse(SaifAutomation.isHostEngine)
        assertFalse(SaifAutomation.isLiveRunning.value)
        assertEquals(ConversationState.IDLE, SaifAutomation.orbState.value)
        assertEquals("READY", SaifAutomation.connectionStatus.value)
        assertEquals(0f, SaifAutomation.audioLevel.value, 0.0001f)
        assertFalse(SaifAutomation.isScreenSharing.value)
        assertFalse(SaifAutomation.isScreenSharePending)
        assertEquals("", SaifAutomation.userTranscript.value)
        assertEquals("", SaifAutomation.assistantTranscript.value)
    }

    @Test
    fun thePurpleMicOffersExactlyTwoModes() {
        val modes = SaifAutomation.LiveMode.values()
        assertEquals(2, modes.size)
        assertEquals("Normal voice", SaifAutomation.LiveMode.NORMAL.label)
        assertEquals("Share screen", SaifAutomation.LiveMode.SCREEN_SHARE.label)
    }

    @Test
    fun wantsScreenShareFollowsTheModeTheSessionWasStartedIn() {
        val previous = SaifAutomation.liveMode.value
        try {
            SaifAutomation.liveMode.value = SaifAutomation.LiveMode.NORMAL
            assertFalse(SaifAutomation.wantsScreenShare())
            SaifAutomation.liveMode.value = SaifAutomation.LiveMode.SCREEN_SHARE
            assertTrue(SaifAutomation.wantsScreenShare())
        } finally {
            SaifAutomation.liveMode.value = previous
        }
    }

    @Test
    fun theHostEngineIsRegisteredByTheApplicationClassNotByTheAutomationModule() {
        // SAIF AI's Application registers SaifHostLiveEngine; a plain JVM test never runs
        // Application.onCreate, so the slot must simply be assignable and start out empty.
        // (The important part: the automation module compiles without knowing com.example.*)
        val previous = SaifAutomation.hostLiveEngine
        try {
            SaifAutomation.hostLiveEngine = null
            assertNull(SaifAutomation.hostLiveEngine)
        } finally {
            SaifAutomation.hostLiveEngine = previous
        }
    }
}
