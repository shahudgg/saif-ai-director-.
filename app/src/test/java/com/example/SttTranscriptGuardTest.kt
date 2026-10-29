package com.example

import com.example.stt.AudioPrep
import com.example.stt.TranscriptGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttTranscriptGuardTest {

    @Test
    fun testGhostPhrasesRejectedOnLowDuration() {
        val decision1 = TranscriptGuard.evaluate("Thank you.", durationMs = 800L)
        assertFalse(decision1.isAccepted)
        assertTrue(decision1.rejectionReason?.contains("Ghost phrase") == true)

        val decision2 = TranscriptGuard.evaluate("Thanks for watching!", durationMs = 1200L)
        assertFalse(decision2.isAccepted)

        val decision3 = TranscriptGuard.evaluate("धन्यवाद", durationMs = 900L)
        assertFalse(decision3.isAccepted)
    }

    @Test
    fun testHighNoSpeechProbRejected() {
        val decision = TranscriptGuard.evaluate("hello world", noSpeechProb = 0.85)
        assertFalse(decision.isAccepted)
        assertTrue(decision.rejectionReason?.contains("no_speech_prob") == true)
    }

    @Test
    fun testLowAvgLogprobRejected() {
        val decision = TranscriptGuard.evaluate("some noisy speech", avgLogProb = -1.5)
        assertFalse(decision.isAccepted)
        assertTrue(decision.rejectionReason?.contains("logprob") == true)
    }

    @Test
    fun testRepetitiveLoopsRejected() {
        val decision1 = TranscriptGuard.evaluate("you you you you you")
        assertFalse(decision1.isAccepted)
        assertTrue(decision1.rejectionReason?.contains("Repetitive") == true)

        val decision2 = TranscriptGuard.evaluate("thank you thank you thank you thank you")
        assertFalse(decision2.isAccepted)
    }

    @Test
    fun testLegitimateHinglishCommandsAccepted() {
        val decision1 = TranscriptGuard.evaluate("YouTube kholo", durationMs = 1800L, avgLogProb = -0.2)
        assertTrue(decision1.isAccepted)
        assertEquals("YouTube kholo", decision1.sanitizedText)

        val decision2 = TranscriptGuard.evaluate("Papa ko call karo", durationMs = 2200L, avgLogProb = -0.15)
        assertTrue(decision2.isAccepted)
        assertEquals("Papa ko call karo", decision2.sanitizedText)

        val decision3 = TranscriptGuard.evaluate("What is the capital of Japan?", durationMs = 3000L, avgLogProb = -0.1)
        assertTrue(decision3.isAccepted)
        assertEquals("What is the capital of Japan?", decision3.sanitizedText)
    }

    @Test
    fun testAudioPrepRemovesDcOffsetAndGeneratesValidWav() {
        // Synthesize 100ms of 16kHz audio with a severe DC offset (+5000)
        val numSamples = 1600
        val pcm = ByteArray(numSamples * 2)
        val dcOffset = 5000.toShort()
        for (i in 0 until numSamples) {
            pcm[i * 2] = (dcOffset.toInt() and 0xff).toByte()
            pcm[i * 2 + 1] = ((dcOffset.toInt() shr 8) and 0xff).toByte()
        }

        val cleanedPcm = AudioPrep.processPcm(pcm, 16000)
        assertEquals(pcm.size, cleanedPcm.size)

        val wav = AudioPrep.pcmToWav(cleanedPcm, 16000)
        assertEquals(44 + cleanedPcm.size, wav.size)
        // Verify standard RIFF and WAVE magic headers
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])
    }
}
