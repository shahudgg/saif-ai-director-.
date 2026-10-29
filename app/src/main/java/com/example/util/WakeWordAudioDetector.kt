package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * Continuous, energy-efficient audio listener for custom wake-up word ("Hey Saif" or custom).
 * When detected, automatically triggers the Gemini Assistant Sheet and Corner Glow.
 */
object WakeWordAudioDetector {
    private const val TAG = "WakeWordAudioDetector"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private val detectorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isRunning = false

    fun start(context: Context) {
        if (isRunning) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start WakeWord detector: RECORD_AUDIO not granted")
            return
        }

        isRunning = true
        listeningJob = detectorScope.launch(Dispatchers.IO) {
            Log.d(TAG, "WakeWordAudioDetector background listening started")
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufSize <= 0) {
                isRunning = false
                return@launch
            }

            val buffer = ShortArray(1024)

            while (isActive && isRunning) {
                // If wake word is disabled or assistant is already active, pause detector loop
                if (!WakeWordManager.isWakeWordEnabledFlow.value || WakeWordManager.isAssistantOverlayActive.value) {
                    delay(500)
                    continue
                }

                var localRecord: AudioRecord? = null
                try {
                    localRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        minBufSize * 2
                    )

                    if (localRecord.state != AudioRecord.STATE_INITIALIZED) {
                        localRecord.release()
                        delay(2000)
                        continue
                    }

                    audioRecord = localRecord
                    localRecord.startRecording()

                    var speechBuffer = ByteArrayOutputStream()
                    var isVoiceActive = false
                    var voiceFramesCount = 0
                    var silenceFramesCount = 0
                    val speechThreshold = 650.0 // RMS threshold for voice

                    while (isActive && isRunning && !WakeWordManager.isAssistantOverlayActive.value && WakeWordManager.isWakeWordEnabledFlow.value) {
                        val readSize = localRecord.read(buffer, 0, buffer.size)
                        if (readSize <= 0) {
                            delay(30)
                            continue
                        }

                        // Calculate frame RMS
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = kotlin.math.sqrt(sum / readSize)

                        if (rms > speechThreshold) {
                            if (!isVoiceActive) {
                                isVoiceActive = true
                                speechBuffer = ByteArrayOutputStream()
                                voiceFramesCount = 0
                                silenceFramesCount = 0
                            }

                            // Write raw bytes
                            val byteBuf = ByteArray(readSize * 2)
                            for (i in 0 until readSize) {
                                val s = buffer[i].toInt()
                                byteBuf[i * 2] = (s and 0xFF).toByte()
                                byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }
                            speechBuffer.write(byteBuf)
                            voiceFramesCount++
                        } else if (isVoiceActive) {
                            // User was speaking, now a quiet frame
                            silenceFramesCount++
                            val byteBuf = ByteArray(readSize * 2)
                            for (i in 0 until readSize) {
                                val s = buffer[i].toInt()
                                byteBuf[i * 2] = (s and 0xFF).toByte()
                                byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }
                            speechBuffer.write(byteBuf)

                            // If we have at least 0.8s of speech and ~0.7s of silence, process chunk!
                            if (voiceFramesCount >= 12 && silenceFramesCount >= 10) {
                                val pcmData = speechBuffer.toByteArray()
                                isVoiceActive = false
                                voiceFramesCount = 0
                                silenceFramesCount = 0

                                // Transcribe and match wake word
                                launch(Dispatchers.IO) {
                                    processPotentialWakeWord(context, pcmData)
                                }
                            } else if (voiceFramesCount > 70) {
                                // Cap at ~4.5 seconds
                                val pcmData = speechBuffer.toByteArray()
                                isVoiceActive = false
                                voiceFramesCount = 0
                                silenceFramesCount = 0
                                launch(Dispatchers.IO) {
                                    processPotentialWakeWord(context, pcmData)
                                }
                            }
                        }

                        delay(10)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in wake word audio loop: ${e.message}")
                } finally {
                    try {
                        localRecord?.stop()
                        localRecord?.release()
                    } catch (_: Exception) {}
                    audioRecord = null
                }

                delay(1000)
            }
        }
    }

    private suspend fun processPotentialWakeWord(context: Context, pcmData: ByteArray) {
        if (pcmData.isEmpty()) return
        try {
            // 1. Fast path: Direct on-device acoustic phonetic envelope match
            val isAcousticMatch = WakeWordManager.matchesAcousticWakeWord(pcmData, SAMPLE_RATE)
            if (isAcousticMatch) {
                Log.i(TAG, "On-device acoustic phonetic signature detected wake word!")
                withContext(Dispatchers.Main) {
                    WakeWordManager.triggerAssistant(context, null)
                }
                return
            }

            // 2. Transcription path for custom phrases or multi-word queries
            val wavBytes = AudioTranscriptionEngine.wrapPcmToWav(pcmData, SAMPLE_RATE, 1, 16)
            val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            val apiKey = AudioTranscriptionEngine.resolveActiveApiKey()

            val result = AudioTranscriptionEngine.transcribeWav(apiKey, base64Audio, wavBytes)
            val transcript = result.getOrNull()?.trim() ?: ""
            Log.d(TAG, "Transcribed background snippet: '$transcript'")

            if (transcript.isNotBlank() && WakeWordManager.matchesWakeWord(transcript)) {
                Log.i(TAG, "WAKE WORD MATCHED! Triggering assistant: '$transcript'")
                val extractedQuery = WakeWordManager.extractQueryAfterWakeWord(transcript)
                withContext(Dispatchers.Main) {
                    WakeWordManager.triggerAssistant(context, extractedQuery)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing potential wake word: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        listeningJob?.cancel()
        listeningJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        Log.d(TAG, "WakeWordAudioDetector stopped")
    }
}
