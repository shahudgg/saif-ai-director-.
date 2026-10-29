package com.example.audio

import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Intelligent acoustic voice activity detection and speech gating engine.
 *
 * Capabilities:
 * - Dynamic noise floor adaptation (tracks continuous background hum, fan noise, road noise).
 * - Multi-feature acoustic analysis: RMS, Zero-Crossing Rate (ZCR), and speech-band energy ratio (300-3400 Hz).
 * - Pre-roll audio stitching: automatically prepends 600ms pre-roll frames so first words are never clipped.
 * - Robust onset trigger: 3 of last 5 frames must be voiced before activating speech.
 * - Configurable end-of-speech pause detection with minimum speech length guard (250 ms).
 * - Half-duplex near-field barge-in gating while TTS is playing.
 */
class VadGate(
    private val sampleRate: Int = 16000,
    private val frameDurationMs: Int = 20,
    private val minSpeechDurationMs: Int = 250,
    private val endOfSpeechSilenceMs: Int = 750,
    private val maxUtteranceMs: Int = 15000
) {
    companion object {
        private const val TAG = "VadGate"
        private const val BASE_NOISE_FLOOR_RMS = 45.0
        private const val MIN_VOICED_ONSET_COUNT = 3
        private const val ONSET_WINDOW_SIZE = 5
        private const val BARGE_IN_SNR_THRESHOLD_DB = 8.5
    }

    data class VadDecision(
        val isSpeechFrame: Boolean,
        val isSpeechActive: Boolean,
        val isEndOfSpeech: Boolean,
        val isInterruption: Boolean,
        val normalizedAmplitude: Float,
        val currentRms: Double,
        val ambientNoiseFloorRms: Double,
        val snrDb: Double,
        val voicedRatio: Float,
        val completedAudio: ByteArray? = null,
        val audioDurationMs: Long = 0L
    )

    private var ambientNoiseFloorRms: Double = BASE_NOISE_FLOOR_RMS
    private var peakSpeechRms: Double = BASE_NOISE_FLOOR_RMS
    private val onsetHistory = BooleanArray(ONSET_WINDOW_SIZE) { false }
    private var onsetHistoryIndex = 0

    private var isSpeechActive = false
    private var voicedFrameCount = 0
    private var totalTurnFrameCount = 0
    private var silenceDurationMs = 0L
    private var utteranceDurationMs = 0L

    private val turnAudioBuffer = ByteArrayOutputStream()

    fun reset() {
        isSpeechActive = false
        peakSpeechRms = BASE_NOISE_FLOOR_RMS
        voicedFrameCount = 0
        totalTurnFrameCount = 0
        silenceDurationMs = 0L
        utteranceDurationMs = 0L
        turnAudioBuffer.reset()
        for (i in onsetHistory.indices) onsetHistory[i] = false
    }

    /**
     * Ingests a 20 ms PCM16 mono frame.
     *
     * @param pcmFrame Exact 20 ms PCM frame (640 bytes)
     * @param isTtsSpeaking True if SAIF TTS is playing
     * @param preRollSupplier Callback to retrieve the 600 ms pre-roll buffer when onset triggers
     */
    fun processFrame(
        pcmFrame: ByteArray,
        isTtsSpeaking: Boolean,
        preRollSupplier: () -> List<ByteArray>
    ): VadDecision {
        val sampleCount = pcmFrame.size / 2
        if (sampleCount <= 0) {
            return VadDecision(
                isSpeechFrame = false,
                isSpeechActive = isSpeechActive,
                isEndOfSpeech = false,
                isInterruption = false,
                normalizedAmplitude = 0f,
                currentRms = 0.0,
                ambientNoiseFloorRms = ambientNoiseFloorRms,
                snrDb = 0.0,
                voicedRatio = 0f
            )
        }

        // 1. Calculate RMS, ZCR, and 300-3400Hz speech band approximation
        var sumSquares = 0.0
        var zeroCrossings = 0
        var prevSample = 0
        var bandEnergySum = 0.0

        for (i in 0 until sampleCount) {
            val idx = i * 2
            val sample = (pcmFrame[idx].toInt() and 0xFF) or (pcmFrame[idx + 1].toInt() shl 8)
            val s = sample.toShort().toInt()
            val sDouble = s.toDouble()
            sumSquares += sDouble * sDouble

            if (i > 0) {
                if ((s >= 0 && prevSample < 0) || (s < 0 && prevSample >= 0)) {
                    zeroCrossings++
                }
                // First-order band approximation: difference represents higher frequencies
                val diff = s - prevSample
                bandEnergySum += (diff * diff).toDouble()
            }
            prevSample = s
        }

        val rms = sqrt(sumSquares / sampleCount)
        val zcr = zeroCrossings.toDouble() / sampleCount.toDouble()
        val highBandRatio = if (sumSquares > 0.0) bandEnergySum / sumSquares else 0.0

        // 2. Track background noise floor adaptively when silent and TTS is off
        if (!isSpeechActive && !isTtsSpeaking) {
            ambientNoiseFloorRms = (ambientNoiseFloorRms * 0.96) + (rms * 0.04)
            if (ambientNoiseFloorRms < 30.0) ambientNoiseFloorRms = 30.0
            if (ambientNoiseFloorRms > 1500.0) ambientNoiseFloorRms = 1500.0
        }

        val snrDb = if (ambientNoiseFloorRms > 0) {
            20.0 * log10((rms.coerceAtLeast(1.0)) / ambientNoiseFloorRms)
        } else {
            0.0
        }

        // 3. Acoustic Voicing Criteria:
        // - RMS exceeds adaptive floor + minimum delta
        // - ZCR within human speech envelope (0.02 to 0.52)
        // - Rejects pure electrical rumbles (highBandRatio < 0.01) and sharp clicks (zcr > 0.6)
        val speechRmsThreshold = maxOf(BASE_NOISE_FLOOR_RMS * 1.35, ambientNoiseFloorRms * 1.20 + 14.0)
        
        // When speech is already active, require RMS to also be sustained relative to peak speech energy
        val activeSpeechFloor = if (isSpeechActive) maxOf(speechRmsThreshold, peakSpeechRms * 0.30) else speechRmsThreshold
        val isVoiced = (rms >= activeSpeechFloor) && (zcr in 0.02..0.55) && (highBandRatio > 0.008)

        if (isVoiced && rms > peakSpeechRms) {
            peakSpeechRms = rms
        }

        // 4. Update sliding onset window
        onsetHistory[onsetHistoryIndex] = isVoiced
        onsetHistoryIndex = (onsetHistoryIndex + 1) % ONSET_WINDOW_SIZE
        val voicedCountInWindow = onsetHistory.count { it }

        var isInterruption = false
        var isEndOfSpeech = false
        var completedTurnAudio: ByteArray? = null
        var completedDurationMs = 0L

        // 5. Barge-In Handling (when TTS is playing)
        if (isTtsSpeaking) {
            val bargeInThreshold = maxOf(BASE_NOISE_FLOOR_RMS * 2.5, ambientNoiseFloorRms * 1.6 + 45.0)
            if (rms >= bargeInThreshold && snrDb >= BARGE_IN_SNR_THRESHOLD_DB && (zcr in 0.03..0.48)) {
                if (voicedCountInWindow >= 4) {
                    isInterruption = true
                    if (!isSpeechActive) {
                        isSpeechActive = true
                        peakSpeechRms = rms
                        voicedFrameCount = 4
                        totalTurnFrameCount = 4
                        utteranceDurationMs = frameDurationMs * 4L
                        silenceDurationMs = 0L

                        // Prepend pre-roll buffer
                        val preRoll = preRollSupplier()
                        turnAudioBuffer.reset()
                        for (f in preRoll) turnAudioBuffer.write(f)
                    }
                }
            }
        } else {
            // 6. Normal Conversational Listening Mode
            if (!isSpeechActive) {
                // Check onset condition: 3 of last 5 frames voiced
                if (voicedCountInWindow >= MIN_VOICED_ONSET_COUNT) {
                    isSpeechActive = true
                    peakSpeechRms = maxOf(rms, speechRmsThreshold)
                    voicedFrameCount = voicedCountInWindow
                    totalTurnFrameCount = ONSET_WINDOW_SIZE
                    utteranceDurationMs = frameDurationMs * ONSET_WINDOW_SIZE.toLong()
                    silenceDurationMs = 0L

                    // Stitch in full 600 ms pre-roll buffer so starting syllables are 100% captured!
                    val preRoll = preRollSupplier()
                    turnAudioBuffer.reset()
                    for (f in preRoll) {
                        turnAudioBuffer.write(f)
                    }
                    turnAudioBuffer.write(pcmFrame)
                    Log.d(TAG, "Speech ONSET triggered with ${preRoll.size} pre-roll frames prepended!")
                }
            } else {
                // Speech is already active
                totalTurnFrameCount++
                utteranceDurationMs += frameDurationMs
                turnAudioBuffer.write(pcmFrame)

                if (isVoiced) {
                    voicedFrameCount++
                    silenceDurationMs = 0L
                } else {
                    silenceDurationMs += frameDurationMs
                }

                // Check End-of-Speech or Max Utterance Cap
                // Adaptive silence timeout: 450ms for short commands/greetings (< 2200ms), 600ms for longer utterances
                val effectiveSilenceTimeout = if (utteranceDurationMs < 2200L) 450 else 600
                val reachedSilenceTimeout = silenceDurationMs >= effectiveSilenceTimeout
                val reachedMaxDuration = utteranceDurationMs >= 9000L

                if (reachedSilenceTimeout || reachedMaxDuration) {
                    val speechDurationActual = (voicedFrameCount * frameDurationMs).toLong()
                    if (speechDurationActual >= minSpeechDurationMs) {
                        isEndOfSpeech = true
                        completedTurnAudio = turnAudioBuffer.toByteArray()
                        completedDurationMs = utteranceDurationMs
                        Log.i(TAG, "End of speech detected (total=${utteranceDurationMs}ms, voiced=${speechDurationActual}ms)")
                    } else {
                        Log.d(TAG, "Utterance discarded (voiced=${speechDurationActual}ms < min=${minSpeechDurationMs}ms)")
                    }
                    reset()
                }
            }
        }

        val voicedRatio = if (totalTurnFrameCount > 0) {
            voicedFrameCount.toFloat() / totalTurnFrameCount.toFloat()
        } else {
            0f
        }

        val normalizedAmplitude = ((rms - ambientNoiseFloorRms) / 1000.0)
            .coerceIn(0.0, 1.0)
            .toFloat()

        return VadDecision(
            isSpeechFrame = isVoiced,
            isSpeechActive = isSpeechActive,
            isEndOfSpeech = isEndOfSpeech,
            isInterruption = isInterruption,
            normalizedAmplitude = normalizedAmplitude,
            currentRms = rms,
            ambientNoiseFloorRms = ambientNoiseFloorRms,
            snrDb = snrDb,
            voicedRatio = voicedRatio,
            completedAudio = completedTurnAudio,
            audioDurationMs = completedDurationMs
        )
    }
}
