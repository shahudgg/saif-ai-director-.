package com.example.stt

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance audio DSP pre-processing for speech-to-text input.
 * Strips DC offset, applies 80Hz high-pass filter (fan/wind rumble suppression),
 * and normalizes volume towards -3 dBFS with a peak limiter.
 */
object AudioPrep {

    /**
     * Processes raw 16 kHz mono 16-bit PCM byte array.
     */
    fun processPcm(pcmBytes: ByteArray, sampleRate: Int = 16000): ByteArray {
        if (pcmBytes.isEmpty()) return pcmBytes

        val numSamples = pcmBytes.size / 2
        val samples = ShortArray(numSamples)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        // 1. Calculate DC offset and Peak
        var sum = 0.0
        var maxAbs = 0
        for (s in samples) {
            sum += s
            val a = abs(s.toInt())
            if (a > maxAbs) maxAbs = a
        }
        val dcOffset = (sum / numSamples).toInt()

        // 2. High-pass filter (RC filter around ~80Hz) and DC removal
        val alpha = 0.97f // ~80Hz cutoff at 16kHz
        var prevInput = 0f
        var prevOutput = 0f
        val filtered = FloatArray(numSamples)
        var filteredMax = 0f

        for (i in 0 until numSamples) {
            val input = (samples[i] - dcOffset).toFloat()
            val output = alpha * (prevOutput + input - prevInput)
            prevInput = input
            prevOutput = output
            filtered[i] = output
            val a = abs(output)
            if (a > filteredMax) filteredMax = a
        }

        // 3. Gentle Peak Normalization with soft limiter (max +6 dB / 2.0x gain)
        // Replaced aggressive 4.0x gain boost to eliminate amplification of background noise and distortion of consonants.
        val targetPeak = 21000f
        val gain = if (filteredMax > 800f) {
            min(targetPeak / filteredMax, 2.0f) // Cap maximum gain boost at 2x (+6 dB)
        } else {
            1.0f
        }

        val outBuffer = ByteBuffer.allocate(pcmBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        for (f in filtered) {
            val amplified = f * gain
            // Soft clipping / limiter
            val clamped = max(-32767f, min(32767f, amplified)).toInt().toShort()
            outBuffer.putShort(clamped)
        }

        return outBuffer.array()
    }

    /**
     * Wraps processed PCM bytes into standard 16 kHz mono 16-bit WAV header.
     */
    fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray {
        val channels = 1
        val bitDepth = 16
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitDepth / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitDepth / 8).toByte(); header[33] = 0
        header[34] = bitDepth.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        val output = ByteArrayOutputStream(header.size + pcmData.size)
        output.write(header)
        output.write(pcmData)
        return output.toByteArray()
    }

    /**
     * Calculates RMS and estimated Signal-to-Noise Ratio (SNR) in dB from a PCM byte array.
     */
    fun calculateRmsAndSnr(pcmBytes: ByteArray): Pair<Double, Double> {
        val numSamples = pcmBytes.size / 2
        if (numSamples == 0) return Pair(0.0, 0.0)

        var sumSquares = 0.0
        val samples = ShortArray(numSamples)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        for (s in samples) {
            sumSquares += s.toDouble() * s.toDouble()
        }
        val rms = kotlin.math.sqrt(sumSquares / numSamples)
        // Assume baseline ambient floor around 50 RMS for estimating SNR
        val estimatedFloor = 50.0
        val snrDb = 20.0 * kotlin.math.log10((rms.coerceAtLeast(1.0)) / estimatedFloor)
        return Pair(rms, snrDb)
    }
}
