package com.example

import com.example.ui.components.MicConfig
import com.example.ui.components.MicState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicStateMachineTest {

    @Test
    fun testMicConfigDefaultsMeetSpecifications() {
        val config = MicConfig()
        assertEquals(16000, config.sampleRate)
        assertEquals(20, config.frameDurationMs)
        assertEquals(0.55f, config.speechStartThreshold, 0.001f)
        assertEquals(0.35f, config.speechHoldThreshold, 0.001f)
        assertEquals(200L, config.speechStartMinDurationMs)
        assertEquals(800L, config.postSpeechSilenceTimeoutMs) // ~800ms silence endpointing
        assertEquals(6000L, config.initialNoSpeechTimeoutMs) // 6s initial timeout
        assertEquals(60000L, config.maxSessionCapMs) // 60s hard cap
        assertEquals(300L, config.preRollDurationMs) // 300ms pre-roll
        assertEquals(300L, config.minSpeechDurationMs)
    }

    @Test
    fun testMicStateHierarchy() {
        val states = listOf(
            MicState.IDLE,
            MicState.LISTENING,
            MicState.SPEECH,
            MicState.ENDPOINT,
            MicState.TRANSCRIBING,
            MicState.DONE,
            MicState.ERROR
        )
        assertTrue(states.contains(MicState.SPEECH))
        assertTrue(states.contains(MicState.ENDPOINT))
        assertTrue(states.contains(MicState.TRANSCRIBING))
    }
}
