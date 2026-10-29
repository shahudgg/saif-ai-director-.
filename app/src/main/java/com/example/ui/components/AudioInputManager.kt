package com.example.ui.components

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioInputManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    var outputFile: File? = null
        private set
    var isRecording = false
        private set

    fun startRecording() {
        if (isRecording) return
        outputFile = File(context.cacheDir, "chat_voice_input.m4a")

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
                isRecording = true
                Log.d("AudioInputManager", "Started recording to ${outputFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e("AudioInputManager", "Failed to start recording", e)
                releaseRecorder()
            }
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return outputFile
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioInputManager", "Stop failed", e)
        } finally {
            releaseRecorder()
        }
        return outputFile
    }

    fun release() {
        releaseRecorder()
    }

    private fun releaseRecorder() {
        isRecording = false
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {}
        mediaRecorder = null
    }
}
