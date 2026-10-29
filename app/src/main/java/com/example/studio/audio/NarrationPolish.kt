package com.example.studio.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Cleans up text-to-speech clips so narration sounds like one continuous voice instead of chopped fragments:
 * removes the long silent head/tail the TTS engine adds, removes DC offset, fades the edges (no clicks) and brings every
 * clip to the same loudness. Pure Kotlin on 16-bit PCM, so it is unit-testable on the JVM.
 */
object NarrationPolish {
    class Pcm(val samples: ShortArray, val sampleRate: Int)

    /** Reads canonical 16-bit PCM WAV (mono or stereo, stereo is averaged to mono). Null when the file is not supported. */
    fun readWav(bytes: ByteArray): Pcm? {
        if (bytes.size < 44) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var pos = 12
        var channels = 1; var rate = 0; var bits = 16; var format = 1
        var dataStart = -1; var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val len = bb.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                format = bb.getShort(body).toInt() and 0xFFFF
                channels = bb.getShort(body + 2).toInt() and 0xFFFF
                rate = bb.getInt(body + 4)
                bits = bb.getShort(body + 14).toInt() and 0xFFFF
            } else if (id == "data") {
                dataStart = body
                dataLen = if (len <= 0 || body + len > bytes.size) bytes.size - body else len
                break
            }
            if (len < 0) return null
            pos = body + len + (len and 1)
        }
        if (dataStart < 0 || rate <= 0 || bits != 16 || channels !in 1..2 || (format != 1 && format != 0xFFFE)) return null
        val frames = dataLen / (2 * channels)
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            if (channels == 1) out[i] = bb.getShort(dataStart + i * 2)
            else out[i] = ((bb.getShort(dataStart + i * 4).toInt() + bb.getShort(dataStart + i * 4 + 2).toInt()) / 2).toShort()
        }
        return Pcm(out, rate)
    }

    fun writeWav(p: Pcm): ByteArray {
        val dataLen = p.samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII)); bb.putInt(36 + dataLen); bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII)); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(p.sampleRate); bb.putInt(p.sampleRate * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray(Charsets.US_ASCII)); bb.putInt(dataLen)
        for (s in p.samples) bb.putShort(s)
        return bb.array()
    }

    private fun rms(x: ShortArray, from: Int, to: Int): Double {
        if (to <= from) return 0.0
        var acc = 0.0
        for (i in from until to) { val v = x[i].toDouble(); acc += v * v }
        return sqrt(acc / (to - from))
    }

    /**
     * Trims silence (keeping [leadMs] before and [tailMs] after the speech), fades the edges and normalises loudness
     * to about [targetRms] (peak-limited). Clips that are entirely quiet are returned unchanged.
     */
    fun polish(
        pcm: Pcm, leadMs: Int = 40, tailMs: Int = 70, fadeInMs: Int = 8, fadeOutMs: Int = 30,
        targetRms: Double = 3800.0, maxGain: Double = 6.0
    ): Pcm {
        val n = pcm.samples.size
        if (n == 0) return pcm
        val rate = pcm.sampleRate
        // remove DC offset
        var mean = 0.0
        for (s in pcm.samples) mean += s
        mean /= n
        val x = ShortArray(n) { (pcm.samples[it] - mean).toInt().coerceIn(-32768, 32767).toShort() }

        val win = max(1, rate / 100)                       // 10 ms windows
        val wins = n / win
        if (wins < 3) return Pcm(x, rate)
        val env = DoubleArray(wins) { rms(x, it * win, (it + 1) * win) }
        val peakEnv = env.maxOrNull() ?: 0.0
        if (peakEnv < 80.0) return Pcm(x, rate)
        val thr = max(90.0, peakEnv * 0.04)
        var first = env.indexOfFirst { it > thr }
        var last = env.indexOfLast { it > thr }
        if (first < 0 || last < first) return Pcm(x, rate)

        val lead = leadMs * rate / 1000; val tail = tailMs * rate / 1000
        val start = max(0, first * win - lead)
        val end = min(n, (last + 1) * win + tail)
        val body = x.copyOfRange(start, end)

        // loudness: speech-only RMS (windows above threshold), peak-limited
        var acc = 0.0; var cnt = 0
        for (i in first..last) if (env[i] > thr) { acc += env[i] * env[i]; cnt++ }
        val speechRms = if (cnt > 0) sqrt(acc / cnt) else peakEnv
        var peak = 1
        for (s in body) peak = max(peak, abs(s.toInt()))
        var gain = min(targetRms / max(1.0, speechRms), maxGain)
        gain = min(gain, 0.92 * 32767.0 / peak)

        val fin = fadeInMs * rate / 1000; val fout = fadeOutMs * rate / 1000
        val out = ShortArray(body.size)
        for (i in body.indices) {
            var g = gain
            if (fin > 0 && i < fin) g *= i.toDouble() / fin
            val rem = body.size - 1 - i
            if (fout > 0 && rem < fout) g *= rem.toDouble() / fout
            out[i] = (body[i] * g).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Pcm(out, rate)
    }

    /** Polishes a WAV file in place. Returns false (file untouched) when it is not a supported WAV. */
    fun polishFile(file: File): Boolean {
        return try {
            val pcm = readWav(file.readBytes()) ?: return false
            val res = polish(pcm)
            if (res.samples.isEmpty()) return false
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(writeWav(res))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            true
        } catch (_: Exception) { false }
    }

    fun durationMs(p: Pcm): Long = p.samples.size * 1000L / p.sampleRate
}
