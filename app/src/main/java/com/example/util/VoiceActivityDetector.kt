package com.example.util

import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Intelligent Voice Activity Detector (VAD) with:
 * - Dynamic adaptive noise floor tracking (ignores steady fan noise, AC hum, room drone).
 * - Zero-Crossing Rate (ZCR) feature extraction (rejects sharp clicks, keyboard taps, door thuds).
 * - High-pass / speech-band filtering ratio.
 * - Temporal frame continuity filtering (requires sustained voiced frames).
 * - Smart End-of-Speech pause detection.
 * - Hardware acoustic barge-in / speech interruption detector for when TTS is speaking.
 */
class VoiceActivityDetector(
    private val sampleRate: Int = 16000
) {
    companion object {
        private const val TAG = "VAD"
        private const val BASE_MIN_SPEECH_RMS = 95.0
        private const val BASE_BARGE_IN_RMS = 180.0
        private const val MIN_VOICED_FRAMES_TO_START = 3 // Requires sustained speech onset
        private const val MIN_BARGE_IN_FRAMES = 4 // Requires sustained voice before interrupting TTS
        private const val SILENCE_TIMEOUT_MS = 800L
        private const val BARGE_IN_GRACE_PERIOD_MS = 250L
    }

    data class VadResult(
        val isSpeechFrame: Boolean,
        val isSpeechActive: Boolean,
        val isEndOfSpeech: Boolean,
        val isInterruption: Boolean,
        val rms: Double,
        val normalizedAmplitude: Float,
        val ambientNoiseFloor: Double,
        val snrDb: Double = 0.0
    )

    private var ambientNoiseFloorRms: Double = 45.0
    private var consecutiveVoicedFrames: Int = 0
    private var consecutiveBargeInFrames: Int = 0
    private var silenceDurationMs: Long = 0L
    private var isSpeechActive: Boolean = false
    private var ttsStartTimestamp: Long = 0L
    private var wasTtsSpeaking: Boolean = false

    fun reset() {
        consecutiveVoicedFrames = 0
        consecutiveBargeInFrames = 0
        silenceDurationMs = 0L
        isSpeechActive = false
    }

    fun notifySpeechStarted() {
        isSpeechActive = true
        consecutiveVoicedFrames = 1
        silenceDurationMs = 0L
    }

    /**
     * Analyzes a PCM 16-bit mono audio chunk.
     * @param pcmBytes The raw PCM byte array
     * @param isTtsSpeaking Whether text-to-speech is currently playing
     */
    fun processFrame(pcmBytes: ByteArray, isTtsSpeaking: Boolean): VadResult {
        if (pcmBytes.isEmpty()) {
            return VadResult(false, isSpeechActive, false, false, 0.0, 0f, ambientNoiseFloorRms)
        }

        // Track TTS playback state changes for grace period
        val now = System.currentTimeMillis()
        if (isTtsSpeaking && !wasTtsSpeaking) {
            ttsStartTimestamp = now
        }
        wasTtsSpeaking = isTtsSpeaking

        val sampleCount = pcmBytes.size / 2
        var sumSquares = 0.0
        var zeroCrossings = 0
        var prevSample = 0
        var deltaEnergySum = 0.0

        for (i in 0 until sampleCount) {
            val idx = i * 2
            val sample = (pcmBytes[idx].toInt() and 0xFF) or (pcmBytes[idx + 1].toInt() shl 8)
            val shortSample = sample.toShort().toInt()
            sumSquares += shortSample.toDouble() * shortSample.toDouble()

            if (i > 0) {
                if ((shortSample >= 0 && prevSample < 0) || (shortSample < 0 && prevSample >= 0)) {
                    zeroCrossings++
                }
                val diff = shortSample - prevSample
                deltaEnergySum += diff.toDouble() * diff.toDouble()
            }
            prevSample = shortSample
        }

        val rms = sqrt(sumSquares / sampleCount)
        val zcr = zeroCrossings.toDouble() / sampleCount.toDouble()
        val speechBandRatio = if (sumSquares > 0.0) deltaEnergySum / sumSquares else 0.0
        val frameDurationMs = (sampleCount.toDouble() / sampleRate.toDouble() * 1000.0).toLong()

        // 1. Dynamic Noise Floor Adaptation (when not actively speaking)
        if (!isSpeechActive && !isTtsSpeaking) {
            ambientNoiseFloorRms = (ambientNoiseFloorRms * 0.97) + (rms * 0.03)
            if (ambientNoiseFloorRms < 30.0) ambientNoiseFloorRms = 30.0
            if (ambientNoiseFloorRms > 1200.0) ambientNoiseFloorRms = 1200.0
        }

        val snrDb = if (ambientNoiseFloorRms > 0) {
            20.0 * log10((rms.coerceAtLeast(1.0)) / ambientNoiseFloorRms)
        } else 0.0

        // 2. Human Voice Acoustic Criteria:
        // - RMS significantly above dynamic ambient floor
        // - ZCR in the human vocal tract range (0.02 to 0.52)
        // - Rejects pure electrical rumbles and sharp clicks
        val speechThreshold = maxOf(BASE_MIN_SPEECH_RMS, ambientNoiseFloorRms * 1.25 + 16.0)
        val isVoicedAcoustic = (rms >= speechThreshold) && (zcr in 0.02..0.52) && (speechBandRatio > 0.01)

        var isInterruption = false
        var isEndOfSpeech = false

        // 3. Barge-In Interruption Detection (When AI is speaking)
        if (isTtsSpeaking) {
            val ttsElapsed = now - ttsStartTimestamp
            if (ttsElapsed > BARGE_IN_GRACE_PERIOD_MS) {
                val interruptionThreshold = maxOf(BASE_BARGE_IN_RMS, ambientNoiseFloorRms * 1.5 + 35.0)
                if (rms >= interruptionThreshold && snrDb >= 8.0 && (zcr in 0.025..0.50)) {
                    consecutiveBargeInFrames++
                    if (consecutiveBargeInFrames >= MIN_BARGE_IN_FRAMES) {
                        isInterruption = true
                        isSpeechActive = true
                        consecutiveVoicedFrames = MIN_VOICED_FRAMES_TO_START
                        silenceDurationMs = 0L
                        Log.d(TAG, "Human interruption confirmed! RMS: $rms, Threshold: $interruptionThreshold")
                    }
                } else {
                    consecutiveBargeInFrames = maxOf(0, consecutiveBargeInFrames - 1)
                }
            }
        } else {
            consecutiveBargeInFrames = 0

            // 4. Normal Speech Detection
            if (isVoicedAcoustic) {
                consecutiveVoicedFrames++
                if (consecutiveVoicedFrames >= MIN_VOICED_FRAMES_TO_START) {
                    isSpeechActive = true
                    silenceDurationMs = 0L
                }
            } else {
                consecutiveVoicedFrames = maxOf(0, consecutiveVoicedFrames - 1)
                if (isSpeechActive) {
                    silenceDurationMs += frameDurationMs
                    if (silenceDurationMs >= SILENCE_TIMEOUT_MS) {
                        isEndOfSpeech = true
                        isSpeechActive = false
                        silenceDurationMs = 0L
                    }
                }
            }
        }

        val normalizedAmplitude = ((rms - ambientNoiseFloorRms) / 1000.0)
            .coerceIn(0.0, 1.0)
            .toFloat()

        return VadResult(
            isSpeechFrame = isVoicedAcoustic,
            isSpeechActive = isSpeechActive,
            isEndOfSpeech = isEndOfSpeech,
            isInterruption = isInterruption,
            rms = rms,
            normalizedAmplitude = normalizedAmplitude,
            ambientNoiseFloor = ambientNoiseFloorRms,
            snrDb = snrDb
        )
    }
}
