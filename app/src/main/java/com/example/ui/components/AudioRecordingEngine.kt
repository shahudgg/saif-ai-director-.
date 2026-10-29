package com.example.ui.components

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AudioRecordingEngine(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    private val _maxAmplitude = MutableStateFlow(0f)
    val maxAmplitude: StateFlow<Float> = _maxAmplitude
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isRecording = false

    fun startRecording() {
        if (isRecording) return
        outputFile = File(context.cacheDir, "temp_voice.m4a")
        
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
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
                isRecording = true
                Log.d("AudioEngine", "Started recording to ${outputFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e("AudioEngine", "Failed to start recording: ${e.message}")
                releaseRecorder()
            }
        }

        if (isRecording) {
            scope.launch {
                while (isActive && isRecording) {
                    try {
                        val amplitude = mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
                        _maxAmplitude.value = amplitude
                    } catch (e: Exception) {}
                    delay(50)
                }
            }
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return outputFile
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error stopping recorder: ${e.message}")
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
