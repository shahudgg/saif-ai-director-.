package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.stt.AudioPrep
import com.example.stt.SttProfile
import com.example.stt.SttRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * State machine for the in-chat microphone dictation engine.
 */
enum class MicState {
    IDLE,
    LISTENING,
    SPEECH,
    ENDPOINT,
    TRANSCRIBING,
    DONE,
    ERROR
}

/**
 * Configuration parameters for in-chat microphone dictation.
 */
data class MicConfig(
    val sampleRate: Int = 16000,
    val frameDurationMs: Int = 20, // 320 samples per 20ms frame at 16kHz
    val speechStartThreshold: Float = 0.55f, // Voice activity probability to start
    val speechHoldThreshold: Float = 0.35f,  // Voice activity probability to hold
    val speechStartMinDurationMs: Long = 200L, // Sustained speech required to transition to SPEECH
    val postSpeechSilenceTimeoutMs: Long = 800L, // Auto-stop after ~800ms silence
    val initialNoSpeechTimeoutMs: Long = 6000L,  // Auto-stop after 6s of complete silence
    val maxSessionCapMs: Long = 60000L,          // Hard cap 60 seconds
    val preRollDurationMs: Long = 300L,          // 300ms pre-roll ring buffer
    val minSpeechDurationMs: Long = 300L         // Minimum voiced audio to transcribe
)

/**
 * High-performance, low-latency in-chat dictation microphone engine.
 * Features dedicated urgent audio thread, circular pre-roll buffer,
 * hysteresis VAD (800ms silence endpointing), throttled RMS, and SttRouter dispatching.
 */
class DirectSpeechInputEngine(
    private val context: Context,
    private val config: MicConfig = MicConfig()
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    private val _micState = MutableStateFlow(MicState.IDLE)
    val micState: StateFlow<MicState> = _micState.asStateFlow()

    @Volatile
    var isListening: Boolean = false
        private set

    companion object {
        private const val TAG = "DirectSpeechEngine"
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * Starts listening directly from the device microphone via native AudioRecord.
     * ZERO Google SpeechRecognizer / external dialogs.
     *
     * @param onRmsChanged Real-time RMS (0f to 10f) throttled for wave / glow UI animations.
     * @param onInterimResult Live transcribed text as user speaks.
     * @param onFinalResult Final transcription when user completes sentence.
     * @param onStateChanged Called when listening state toggles.
     */
    @SuppressLint("MissingPermission")
    fun startListening(
        onRmsChanged: (Float) -> Unit,
        onInterimResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onStateChanged: (Boolean) -> Unit
    ) {
        if (isListening) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            _micState.value = MicState.ERROR
            return
        }

        val frameSamples = (config.sampleRate * config.frameDurationMs) / 1000
        val minBufferSize = AudioRecord.getMinBufferSize(config.sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid AudioRecord minBufferSize: $minBufferSize")
            _micState.value = MicState.ERROR
            return
        }

        val bufferSizeInBytes = max(minBufferSize * 2, frameSamples * 4)

        // 1. Hardware Microphone Setup: prefer VOICE_RECOGNITION, fallback to MIC
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                config.sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            null
        } ?: try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            null
        }

        val record = audioRecord
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed initializing hardware AudioRecord")
            _micState.value = MicState.ERROR
            return
        }

        // Attach hardware audio effects if available on device
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(record.audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect initialization note: ${e.message}")
        }

        isListening = true
        _micState.value = MicState.LISTENING
        onStateChanged(true)

        recordingJob = scope.launch(Dispatchers.IO) {
            // Set urgent audio thread priority
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (ignored: Exception) {}

            val preRollFramesCount = (config.preRollDurationMs / config.frameDurationMs).toInt()
            val preRollRingBuffer = Array(max(1, preRollFramesCount)) { ShortArray(frameSamples) }
            var preRollWriteIdx = 0

            val pcmOutput = ByteArrayOutputStream()
            val frameBuffer = ShortArray(frameSamples)

            var isSpeechActive = false
            var speechSustainedMs = 0L
            var consecutiveSilenceMs = 0L
            var totalVoicedMs = 0L

            val sessionStartTime = System.currentTimeMillis()
            var lastRmsEmissionTime = 0L
            var smoothedRms = 120.0
            var noiseFloor = 100.0

            try {
                record.startRecording()
                Log.d(TAG, "Hardware mic recording started (source=${record.audioSource})")

                while (isActive && isListening) {
                    val readSamples = record.read(frameBuffer, 0, frameSamples)
                    if (readSamples <= 0) {
                        continue
                    }

                    // 1. Frame Energy and Feature Extraction
                    var sumEnergy = 0.0
                    var zeroCrossings = 0
                    var prevSample = 0
                    for (i in 0 until readSamples) {
                        val s = frameBuffer[i].toInt()
                        sumEnergy += (s.toDouble() * s.toDouble())
                        if ((s >= 0 && prevSample < 0) || (s < 0 && prevSample >= 0)) {
                            zeroCrossings++
                        }
                        prevSample = s
                    }
                    val frameRms = sqrt(sumEnergy / readSamples)

                    // Exponential smoothing for RMS & noise floor tracking
                    smoothedRms = smoothedRms * 0.75 + frameRms * 0.25
                    if (frameRms < noiseFloor * 1.25) {
                        noiseFloor = noiseFloor * 0.95 + frameRms * 0.05
                    }
                    noiseFloor = noiseFloor.coerceIn(30.0, 450.0)

                    // 2. Compute Voice Activity Probability
                    val snrRatio = smoothedRms / max(1.0, noiseFloor)
                    val zcrRate = zeroCrossings.toDouble() / readSamples
                    var speechProbability = when {
                        snrRatio > 3.0 -> 0.95f
                        snrRatio > 2.0 -> 0.75f
                        snrRatio > 1.4 && zcrRate in 0.03..0.45 -> 0.60f
                        snrRatio > 1.2 -> 0.40f
                        else -> 0.10f
                    }

                    // 3. Hysteresis VAD State Machine
                    val now = System.currentTimeMillis()
                    if (isSpeechActive) {
                        if (speechProbability >= config.speechHoldThreshold) {
                            consecutiveSilenceMs = 0L
                            totalVoicedMs += config.frameDurationMs
                        } else {
                            consecutiveSilenceMs += config.frameDurationMs
                        }

                        // Write PCM to active utterance buffer
                        appendSamplesToStream(pcmOutput, frameBuffer, readSamples)

                        // Check 800ms silence endpointing
                        if (consecutiveSilenceMs >= config.postSpeechSilenceTimeoutMs) {
                            Log.d(TAG, "Silence endpoint detected (${consecutiveSilenceMs}ms). Stopping recording.")
                            _micState.value = MicState.ENDPOINT
                            break
                        }
                    } else {
                        // Check if voice begins
                        if (speechProbability >= config.speechStartThreshold) {
                            speechSustainedMs += config.frameDurationMs
                            if (speechSustainedMs >= config.speechStartMinDurationMs) {
                                isSpeechActive = true
                                _micState.value = MicState.SPEECH
                                Log.d(TAG, "Speech active detected. Flushing pre-roll buffer (${preRollFramesCount * config.frameDurationMs}ms).")

                                // Flush pre-roll circular buffer so first word consonants are never lost
                                for (i in 0 until preRollFramesCount) {
                                    val idx = (preRollWriteIdx + i) % preRollFramesCount
                                    appendSamplesToStream(pcmOutput, preRollRingBuffer[idx], frameSamples)
                                }
                                appendSamplesToStream(pcmOutput, frameBuffer, readSamples)
                                totalVoicedMs += (config.preRollDurationMs + speechSustainedMs)
                            }
                        } else {
                            speechSustainedMs = max(0L, speechSustainedMs - config.frameDurationMs)
                            consecutiveSilenceMs += config.frameDurationMs

                            // Keep pre-roll circular buffer updated
                            System.arraycopy(frameBuffer, 0, preRollRingBuffer[preRollWriteIdx], 0, frameSamples)
                            preRollWriteIdx = (preRollWriteIdx + 1) % preRollFramesCount

                            // Initial silence timeout (6s)
                            if (consecutiveSilenceMs >= config.initialNoSpeechTimeoutMs) {
                                Log.d(TAG, "Initial silence timeout reached (${consecutiveSilenceMs}ms). Auto-stopping.")
                                break
                            }
                        }
                    }

                    // 4. Throttled UI Animation RMS update (max 30 fps)
                    if (now - lastRmsEmissionTime >= 33L) {
                        lastRmsEmissionTime = now
                        val normalizedLevel = ((smoothedRms - noiseFloor) / 1400.0).coerceIn(0.0, 1.0).toFloat()
                        val scaledRms = normalizedLevel * 10f
                        withContext(Dispatchers.Main) {
                            if (isListening) onRmsChanged(scaledRms)
                        }
                    }

                    // Safety cap timeout
                    if (now - sessionStartTime >= config.maxSessionCapMs) {
                        Log.d(TAG, "Max session cap reached (${config.maxSessionCapMs}ms). Auto-stopping.")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording loop error: ${e.message}")
            } finally {
                stopInternal()

                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        onRmsChanged(0f)
                        onStateChanged(false)
                    }

                    // Transcribe if voiced audio exists
                    val capturedPcm = pcmOutput.toByteArray()
                    if (capturedPcm.size >= (config.minSpeechDurationMs * config.sampleRate * 2 / 1000) && isSpeechActive) {
                        _micState.value = MicState.TRANSCRIBING
                        val cleanWav = AudioPrep.pcmToWav(AudioPrep.processPcm(capturedPcm, config.sampleRate), config.sampleRate)
                        val sttResult = SttRouter.transcribe(
                            wavBytes = cleanWav,
                            profile = SttProfile.DICTATION,
                            context = context,
                            durationMs = totalVoicedMs
                        )

                        withContext(Dispatchers.Main) {
                            if (sttResult.isSuccess) {
                                _micState.value = MicState.DONE
                                onFinalResult(sttResult.text)
                            } else {
                                _micState.value = MicState.IDLE
                                if (sttResult.rejectedReason != null && !sttResult.rejectedReason.contains("Ghost phrase") && !sttResult.rejectedReason.contains("Empty")) {
                                    Toast.makeText(context, sttResult.rejectedReason, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        _micState.value = MicState.IDLE
                    }
                }
            }
        }
    }

    private fun appendSamplesToStream(out: ByteArrayOutputStream, samples: ShortArray, count: Int) {
        val byteBuf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            byteBuf.putShort(samples[i])
        }
        out.write(byteBuf.array(), 0, count * 2)
    }

    /**
     * Stops listening immediately and triggers transcription of captured audio.
     */
    fun stopListening() {
        if (!isListening) return
        isListening = false
        recordingJob?.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        isListening = false
        try {
            audioRecord?.stop()
        } catch (ignored: Exception) {}
        try {
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null

        try {
            noiseSuppressor?.release()
        } catch (ignored: Exception) {}
        noiseSuppressor = null

        try {
            agc?.release()
        } catch (ignored: Exception) {}
        agc = null
    }

    fun release() {
        stopListening()
    }
}
