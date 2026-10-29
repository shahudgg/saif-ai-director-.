package com.example.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.stt.AudioPrep
import com.example.stt.SttProfile
import com.example.stt.SttResult
import com.example.stt.SttRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backward-compatible facade over the modernized com.example.stt engine.
 * Ensures all existing call sites (mic button, live voice, chat attachments)
 * seamlessly benefit from SttRouter, AudioPrep DSP, and TranscriptGuard.
 */
object AudioTranscriptionEngine {
    private const val TAG = "AudioTranscriptionEngine"

    fun resolveActiveApiKey(): String {
        val providerState = com.example.data.local.ProviderSettingsManager.loadState()
        val keys = providerState.apiKeys
        val configuredKey = if (keys.isNotEmpty()) keys[providerState.currentKeyIndex % keys.size] else ""
        return configuredKey.trim()
    }

    fun wrapPcmToWav(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1, bitDepth: Int = 16): ByteArray {
        val prepped = AudioPrep.processPcm(pcmData, sampleRate)
        return AudioPrep.pcmToWav(prepped, sampleRate)
    }

    suspend fun transcribeAudioFile(file: File, apiKey: String = "", context: Context? = null): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(Exception("Audio file is empty or missing."))
        }
        val rawBytes = file.readBytes()
        val wavBytes = if (file.extension.equals("wav", ignoreCase = true)) {
            rawBytes
        } else {
            rawBytes
        }

        val durationMs = if (wavBytes.size > 44) {
            ((wavBytes.size - 44) * 1000L) / (16000 * 2)
        } else {
            2000L
        }

        val result = SttRouter.transcribe(
            wavBytes = wavBytes,
            profile = SttProfile.DICTATION,
            context = context,
            durationMs = durationMs
        )
        if (result.isSuccess) {
            Result.success(result.text)
        } else {
            Result.failure(Exception(result.rejectedReason ?: "Transcription failed"))
        }
    }

    suspend fun transcribeWav(
        apiKey: String,
        base64Audio: String,
        rawWavBytes: ByteArray,
        context: Context? = null,
        durationMs: Long? = null,
        snrDb: Double? = null,
        voicedRatio: Float? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (rawWavBytes.isEmpty()) {
            return@withContext Result.failure(Exception("Audio buffer is empty."))
        }

        val pcmLength = if (rawWavBytes.size > 44) rawWavBytes.size - 44 else rawWavBytes.size
        val computedDurationMs = durationMs ?: ((pcmLength * 1000L) / (16000 * 2)).coerceAtLeast(200L)

        val computedSnr = snrDb ?: run {
            if (rawWavBytes.size > 44) {
                val pcm = rawWavBytes.copyOfRange(44, rawWavBytes.size)
                AudioPrep.calculateRmsAndSnr(pcm).second
            } else null
        }

        val result = SttRouter.transcribe(
            wavBytes = rawWavBytes,
            profile = SttProfile.LIVE_COMMAND,
            context = context,
            durationMs = computedDurationMs,
            snrDb = computedSnr,
            voicedRatio = voicedRatio
        )

        if (result.isSuccess) {
            Result.success(result.text)
        } else {
            Result.failure(Exception(result.rejectedReason ?: "Transcription failed"))
        }
    }
}
