package com.example.studio.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SfxSynthTest {

    @Test fun `every sound kind is audible, bounded and the requested length`() {
        for (kind in SfxSynth.KINDS) {
            val ms = SfxSynth.defaultDurationMs(kind)
            val s = SfxSynth.generate(kind, ms)
            assertEquals("$kind length", ms * SfxSynth.RATE / 1000L, s.size.toLong(), 2.0)
            val peak = s.maxOf { abs(it) }
            assertTrue("$kind silent (peak $peak)", peak > 0.2f)
            assertTrue("$kind clips (peak $peak)", peak <= 0.95f)
            assertTrue("$kind has NaN", s.none { it.isNaN() })
            assertTrue("$kind starts with a click", abs(s.first()) < 0.05f && abs(s.last()) < 0.05f)
        }
    }

    @Test fun `generation is deterministic`() {
        assertArrayEquals(SfxSynth.generate("whoosh", 400, 3), SfxSynth.generate("whoosh", 400, 3), 0f)
    }

    @Test fun `wav header is valid`() {
        val s = SfxSynth.generate("pop")
        val wav = SfxSynth.toWav(s)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4)); assertEquals("data", String(wav, 36, 4))
        assertEquals(44 + s.size * 2, wav.size)
    }
}
