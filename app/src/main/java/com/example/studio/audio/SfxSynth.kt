package com.example.studio.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural, licence-free sound effects (100% generated on the device, nothing downloaded, nothing copyrighted).
 * Used when no SFX exists in the Telegram library. Deterministic: same kind + seed gives the same samples.
 */
object SfxSynth {
    const val RATE = 48_000

    val KINDS = setOf("whoosh", "pop", "riser", "click", "hit", "ding", "glitch", "typing", "tick")

    fun defaultDurationMs(kind: String): Long = when (kind) {
        "whoosh" -> 450L; "pop" -> 160L; "riser" -> 900L; "click" -> 70L; "hit" -> 600L
        "ding" -> 700L; "glitch" -> 280L; "typing" -> 700L; "tick" -> 90L; else -> 400L
    }

    private class Noise(seed: Int) {
        private var s = seed * 747796405 + 2891336453L.toInt()
        fun next(): Float { s = s * 1664525 + 1013904223; return ((s ushr 8) and 0xFFFF) / 32768f - 1f }
    }

    /** Mono samples in -1..1. [durationMs] <= 0 uses the kind's default length. */
    fun generate(kind: String, durationMs: Long = 0L, seed: Int = 1): FloatArray {
        val ms = if (durationMs > 0) durationMs else defaultDurationMs(kind)
        val n = (ms * RATE / 1000L).toInt().coerceAtLeast(64)
        val out = FloatArray(n)
        val noise = Noise(seed + kind.hashCode())
        var lp = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val p = i.toFloat() / n
            out[i] = when (kind) {
                "whoosh" -> {                                   // band-limited noise swept upward then down, bell envelope
                    val f = 0.05f + 0.35f * sin((PI * p).toFloat())
                    lp += f * (noise.next() - lp)
                    lp * sin((PI * p).toFloat()).let { it * it } * 2.2f
                }
                "pop" -> sin(2f * PI.toFloat() * (520f - 380f * p) * t) * exp(-t * 38f)
                "riser" -> {
                    val f = 180f + 1500f * p * p
                    val tone = sin(2f * PI.toFloat() * f * t)
                    lp += 0.25f * (noise.next() - lp)
                    (0.5f * tone + 0.5f * lp) * (0.15f + 0.85f * p) * if (p > 0.97f) (1f - p) / 0.03f else 1f
                }
                "click" -> (noise.next() * 0.6f + sin(2f * PI.toFloat() * 1800f * t) * 0.4f) * exp(-t * 160f)
                "hit" -> sin(2f * PI.toFloat() * (90f - 40f * p) * t) * exp(-t * 7f) + noise.next() * 0.25f * exp(-t * 30f)
                "ding" -> (sin(2f * PI.toFloat() * 1318f * t) * 0.6f + sin(2f * PI.toFloat() * 1975f * t) * 0.3f) * exp(-t * 5.5f)
                "glitch" -> {
                    val step = (t * 60f).toInt()
                    val gate = if ((step * 2654435761L.toInt()) ushr 31 == 0) 1f else 0.15f
                    (if ((i / 24) % 2 == 0) noise.next() else sin(2f * PI.toFloat() * 700f * t)) * gate * (1f - p)
                }
                "typing" -> {
                    val period = 0.085f
                    val phase = t % period
                    (noise.next() * 0.7f + sin(2f * PI.toFloat() * 1400f * phase)) * exp(-phase * 120f) * 0.7f
                }
                "tick" -> sin(2f * PI.toFloat() * 2400f * t) * exp(-t * 200f)
                else -> 0f
            }
        }
        // De-click and normalise to a safe peak.
        val fade = (RATE * 0.004f).toInt().coerceAtMost(n / 4)
        for (i in 0 until fade) { val g = i.toFloat() / fade; out[i] *= g; out[n - 1 - i] *= g }
        val peak = out.maxOf { abs(it) }.takeIf { it > 1e-5f } ?: 1f
        val gain = min(1f, 0.89f / peak)
        for (i in 0 until n) out[i] = (out[i] * gain).coerceIn(-0.95f, 0.95f)
        return out
    }

    /** 16-bit PCM mono WAV bytes. */
    fun toWav(samples: FloatArray, rate: Int = RATE): ByteArray {
        val dataLen = samples.size * 2
        val bos = ByteArrayOutputStream(44 + dataLen)
        fun le32(v: Int) { bos.write(v and 0xFF); bos.write((v shr 8) and 0xFF); bos.write((v shr 16) and 0xFF); bos.write((v shr 24) and 0xFF) }
        fun le16(v: Int) { bos.write(v and 0xFF); bos.write((v shr 8) and 0xFF) }
        bos.write("RIFF".toByteArray()); le32(36 + dataLen); bos.write("WAVE".toByteArray())
        bos.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
        bos.write("data".toByteArray()); le32(dataLen)
        for (s in samples) le16((s * 32767f).toInt().coerceIn(-32768, 32767))
        return bos.toByteArray()
    }
}
