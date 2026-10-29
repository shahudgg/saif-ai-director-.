package com.example.studio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoftLimiterTest {
    @Test fun limiterIsContinuousAcrossFullScale() {
        // The old limiter jumped from 1.0 to 0.5 just above full scale, which crackled on loud voice + effects.
        var prev = AudioMixer.softLimit(0f)
        var x = 0.001f
        while (x < 3f) {
            val y = AudioMixer.softLimit(x)
            assertTrue("jump at $x: $prev -> $y", abs(y - prev) < 0.02f)
            assertTrue(y <= 1.0f)
            prev = y; x += 0.001f
        }
    }

    @Test fun quietSignalsPassUnchangedAndSignIsKept() {
        assertEquals(0.5f, AudioMixer.softLimit(0.5f), 1e-6f)
        assertEquals(-0.5f, AudioMixer.softLimit(-0.5f), 1e-6f)
        assertTrue(AudioMixer.softLimit(-2f) < -0.9f)
    }
}
