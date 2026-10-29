package com.example.studio.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Master Clock managing playback synchronization in microseconds.
 */
class MasterClock(
    private val scope: CoroutineScope
) {
    private val _currentTimeUs = MutableStateFlow(0L)
    val currentTimeUs: StateFlow<Long> = _currentTimeUs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    var totalDurationUs: Long = 0L

    private var playbackJob: Job? = null

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.Default) {
            var lastRealTimeNs = System.nanoTime()
            while (_isPlaying.value) {
                delay(16) // ~60fps tick
                val nowNs = System.nanoTime()
                val deltaUs = ((nowNs - lastRealTimeNs) / 1000L * _speed.value).toLong()
                lastRealTimeNs = nowNs

                val nextTime = _currentTimeUs.value + deltaUs
                if (totalDurationUs > 0 && nextTime >= totalDurationUs) {
                    if (_isLooping.value) {
                        _currentTimeUs.value = 0L
                    } else {
                        _currentTimeUs.value = totalDurationUs
                        _isPlaying.value = false
                        break
                    }
                } else {
                    _currentTimeUs.value = nextTime
                }
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(timeUs: Long) {
        _currentTimeUs.value = timeUs.coerceIn(0L, totalDurationUs.coerceAtLeast(0L))
    }

    fun jumpStart() {
        seekTo(0L)
    }

    fun jumpEnd() {
        seekTo(totalDurationUs)
    }

    fun stepFrames(frameCount: Int, fps: Int = 30) {
        val frameDurationUs = 1_000_000L / fps
        seekTo(_currentTimeUs.value + (frameCount * frameDurationUs))
    }

    fun stepSeconds(deltaSeconds: Float) {
        val deltaUs = (deltaSeconds * 1_000_000L).toLong()
        seekTo(_currentTimeUs.value + deltaUs)
    }

    fun setSpeed(newSpeed: Float) {
        _speed.value = newSpeed.coerceIn(0.25f, 4.0f)
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }
}
