package com.example.studio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class NarrationPolishTest {
    private val rate = 22050

    /** 0.7 s silence, 1.2 s of a 220 Hz "voice", 0.7 s silence: what TTS engines hand back. */
    private fun voiceWithSilence(amp: Double = 6000.0): ShortArray {
        val sil = (0.7 * rate).toInt(); val tone = (1.2 * rate).toInt()
        return ShortArray(sil + tone + sil) { i -> if (i in sil until sil + tone) (amp * sin(2 * PI * 220 * i / rate)).toInt().toShort() else 0 }
    }

    @Test fun trimsLongSilenceAtBothEnds() {
        val out = NarrationPolish.polish(NarrationPolish.Pcm(voiceWithSilence(), rate))
        val ms = NarrationPolish.durationMs(out)
        assertTrue("expected ~1.3 s, was $ms ms", ms in 1200L..1450L)
    }

    @Test fun edgesFadeSoThereAreNoClicks() {
        val out = NarrationPolish.polish(NarrationPolish.Pcm(voiceWithSilence(), rate)).samples
        assertTrue(abs(out.first().toInt()) < 200)
        assertTrue(abs(out.last().toInt()) < 200)
    }

    @Test fun quietAndLoudClipsEndUpEquallyLoud() {
        fun rms(x: ShortArray) = Math.sqrt(x.map { it.toDouble() * it }.average())
        val quiet = rms(NarrationPolish.polish(NarrationPolish.Pcm(voiceWithSilence(1500.0), rate)).samples)
        val loud = rms(NarrationPolish.polish(NarrationPolish.Pcm(voiceWithSilence(12000.0), rate)).samples)
        assertTrue("quiet=$quiet loud=$loud", quiet / loud in 0.6..1.6)
    }

    @Test fun entirelySilentClipIsLeftAlone() {
        val out = NarrationPolish.polish(NarrationPolish.Pcm(ShortArray(rate), rate))
        assertEquals(rate, out.samples.size)
    }

    @Test fun wavRoundTripKeepsSamplesAndRate() {
        val src = NarrationPolish.Pcm(ShortArray(1000) { (it * 7 % 2000 - 1000).toShort() }, 24000)
        val back = NarrationPolish.readWav(NarrationPolish.writeWav(src))
        assertNotNull(back)
        assertEquals(24000, back!!.sampleRate)
        assertEquals(src.samples.toList(), back.samples.toList())
    }

    @Test fun nonWavBytesAreRejected() {
        assertEquals(null, NarrationPolish.readWav(ByteArray(100)))
    }
}
