package com.example.audio

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class LiveVadGateTest {
    private fun frame(amplitude: Int): ByteArray {
        val bytes = ByteArray(640)
        for (i in 0 until 320) {
            val sample = (amplitude * sin(2.0 * PI * 440.0 * i / 16000.0)).toInt()
            bytes[i * 2] = sample.toByte()
            bytes[i * 2 + 1] = (sample shr 8).toByte()
        }
        return bytes
    }

    @Test fun strongSpeechInterruptsWithinFiveFramesWhileWeakEchoIsSuppressed() {
        val gate = VadGate(minimumBargeInRms = 700.0)
        repeat(8) { assertFalse(gate.processFrame(frame(150), true) { emptyList() }.isInterruption) }
        gate.reset()
        val decisions = (1..5).map { gate.processFrame(frame(2500), true) { listOf(frame(2500)) } }
        assertTrue("Barge-in should fire within 100ms", decisions.any { it.isInterruption })
    }

    @Test fun completedTurnRetainsSpeechStatisticsRatherThanTrailingSilence() {
        val gate = VadGate()
        repeat(30) { gate.processFrame(frame(1800), false) { emptyList() } }
        val finished = (1..30).map { gate.processFrame(frame(0), false) { emptyList() } }
            .firstOrNull { it.isEndOfSpeech }
        assertNotNull(finished)
        assertTrue(finished!!.voicedRatio > 0.2f)
        assertTrue(finished.snrDb > 2.5)
        assertNotNull(finished.completedAudio)
    }
}
