package com.example.studio.audio

import com.example.studio.model.AudioItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TimelineItem
import com.example.studio.model.TrackType
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import kotlin.math.sin

/**
 * High-performance deterministic 48 kHz 16-bit stereo Audio Mixer.
 * Handles volume levels (0-200%), fade ramps, dynamic ducking under voice,
 * and soft-knee peak limiting. Shared by both live playback and offline export.
 */
object AudioMixer {

    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM

    /**
     * Resolves the effective gain (0.0f - 2.0f) for an audio item at timestamp [tUs],
     * taking into account item volume, fade in/out curves, and voice ducking.
     */
    fun calculateEffectiveGain(
        item: TimelineItem,
        tUs: Long,
        isVoiceActiveAtTime: Boolean
    ): Float {
        val localUs = tUs - item.startUs
        if (localUs < 0 || localUs > item.durationUs) return 0f

        var baseVolume = when (item) {
            is AudioItem -> item.volume
            is VoiceItem -> item.volume
            is VideoItem -> item.volume
            else -> 1.0f
        }

        // Apply Fade In
        val fadeInUs = when (item) {
            is AudioItem -> item.fadeInUs
            else -> 0L
        }
        if (fadeInUs > 0 && localUs < fadeInUs) {
            val fadeRatio = (localUs.toFloat() / fadeInUs).coerceIn(0f, 1f)
            baseVolume *= fadeRatio
        }

        // Apply Fade Out
        val fadeOutUs = when (item) {
            is AudioItem -> item.fadeOutUs
            else -> 0L
        }
        val remainingUs = item.durationUs - localUs
        if (fadeOutUs > 0 && remainingUs < fadeOutUs) {
            val fadeRatio = (remainingUs.toFloat() / fadeOutUs).coerceIn(0f, 1f)
            baseVolume *= fadeRatio
        }

        // Apply Audio Ducking (lowers BGM / SFX volume when user voice or AI voice is speaking)
        if (isVoiceActiveAtTime) {
            val shouldDuck = when (item) {
                is AudioItem -> item.audioDucking || !item.isSfx // Auto-duck BGM under voice
                else -> false
            }
            if (shouldDuck) {
                val duckingAmount = if (item is AudioItem) item.duckingAmount else 0.35f
                baseVolume *= (1f - duckingAmount).coerceAtLeast(0.1f)
            }
        }

        return baseVolume.coerceIn(0f, 2.0f)
    }

    /**
     * Evaluates whether any voiceover (TrackType.VOICE) is speaking at timestamp [tUs].
     */
    fun isVoiceActive(project: StudioProject, tUs: Long): Boolean {
        return project.items.any { item ->
            item.trackType == TrackType.VOICE && tUs in item.startUs until (item.startUs + item.durationUs)
        }
    }

    /**
     * Soft limiter / compressor to prevent clipping when multiple loud audio tracks overlap.
     */
    fun softLimit(sample: Float): Float {
        // Continuous soft knee: linear up to 0.8, then smoothly approaches +-1.0 (the old version jumped from 1.0 to 0.5 = crackle).
        val a = kotlin.math.abs(sample)
        if (a <= 0.8f) return sample
        val over = (a - 0.8f) / 0.2f
        val shaped = 0.8f + 0.2f * kotlin.math.tanh(over.toDouble()).toFloat()
        return if (sample < 0f) -shaped else shaped
    }

    /**
     * Synthesizes or mixes stereo PCM frame samples for test and offline export verification.
     */
    fun mixStereoSample(
        sources: List<Float>,
        gains: List<Float>
    ): Pair<Short, Short> {
        var mixed = 0f
        for (i in sources.indices) {
            val g = if (i < gains.size) gains[i] else 1.0f
            mixed += sources[i] * g
        }
        val limited = softLimit(mixed).coerceIn(-1.0f, 1.0f)
        val shortVal = (limited * Short.MAX_VALUE).toInt().toShort()
        return Pair(shortVal, shortVal)
    }
}
