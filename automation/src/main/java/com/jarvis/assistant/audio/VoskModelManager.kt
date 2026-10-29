package com.jarvis.assistant.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.SpeakerModel
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object VoskModelManager {
    private const val TAG = "VoskModelManager"

    // The big Vosk models are not bundled in the source pack. If they are not found in assets they are
    // downloaded once (first run, needs internet) and kept in app storage.
    private const val EN_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val SPK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip"

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isSpeakerModelReady = MutableStateFlow(false)
    val isSpeakerModelReady: StateFlow<Boolean> = _isSpeakerModelReady.asStateFlow()

    @Volatile
    var model: Model? = null
        private set

    @Volatile
    var speakerModel: SpeakerModel? = null
        private set

    private val isInitializing = java.util.concurrent.atomic.AtomicBoolean(false)

    fun init(context: Context, scope: CoroutineScope) {
        if (model != null && speakerModel != null) return
        if (!isInitializing.compareAndSet(false, true)) return

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Unpack & load speech recognition model
                val targetDir = File(context.filesDir, "vosk-model")
                val markerFile = File(targetDir, "am/final.mdl")

                if (!markerFile.exists() || markerFile.length() == 0L) {
                    Log.i(TAG, "Unpacking Vosk model to internal storage: ${targetDir.absolutePath}")
                    targetDir.deleteRecursively()
                    targetDir.mkdirs()
                    if (hasBundledFile(context.assets, "model-en-us/am/final.mdl")) {
                        copyAssetFolder(context.assets, "model-en-us", targetDir)
                    } else {
                        Log.i(TAG, "Vosk model not bundled - downloading it once.")
                        downloadAndUnzip(EN_MODEL_URL, targetDir)
                    }
                    Log.i(TAG, "Vosk model unpacked successfully.")
                } else {
                    Log.i(TAG, "Vosk model already unpacked at: ${targetDir.absolutePath}")
                }

                val loadedModel = Model(targetDir.absolutePath)
                model = loadedModel
                _isModelReady.value = true
                Log.i(TAG, "Vosk Model ready in memory!")

                // 2. Unpack & load speaker recognition model (x-vector extractor)
                try {
                    val spkTargetDir = File(context.filesDir, "vosk-spk-model")
                    val spkMarkerFile = File(spkTargetDir, "final.ext.raw")

                    if (!spkMarkerFile.exists() || spkMarkerFile.length() == 0L) {
                        Log.i(TAG, "Unpacking Vosk Speaker model to: ${spkTargetDir.absolutePath}")
                        spkTargetDir.deleteRecursively()
                        spkTargetDir.mkdirs()
                        if (hasBundledFile(context.assets, "model-spk/final.ext.raw")) {
                            copyAssetFolder(context.assets, "model-spk", spkTargetDir)
                        } else {
                            Log.i(TAG, "Vosk speaker model not bundled - downloading it once.")
                            downloadAndUnzip(SPK_MODEL_URL, spkTargetDir)
                        }
                        Log.i(TAG, "Vosk Speaker model unpacked successfully.")
                    } else {
                        Log.i(TAG, "Vosk Speaker model already unpacked at: ${spkTargetDir.absolutePath}")
                    }

                    val loadedSpk = SpeakerModel(spkTargetDir.absolutePath)
                    speakerModel = loadedSpk
                    _isSpeakerModelReady.value = true
                    Log.i(TAG, "Vosk SpeakerModel ready in memory!")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to load Vosk SpeakerModel: ${e.message}", e)
                }

                isInitializing.set(false)
            } catch (e: Throwable) {
                isInitializing.set(false)
                Log.e(TAG, "FATAL: Failed to load Vosk model: ${e.message}", e)
            }
        }
    }

    private fun hasBundledFile(assetManager: AssetManager, path: String): Boolean =
        try {
            assetManager.open(path).use { true }
        } catch (_: Exception) {
            false
        }

    /** Downloads a Vosk model zip and extracts it into [targetDir], dropping the zip's top-level folder. */
    private fun downloadAndUnzip(url: String, targetDir: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("Model download failed: HTTP ${connection.responseCode}")
            }
            val rootCanonical = targetDir.canonicalPath
            ZipInputStream(connection.inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfter('/', "")
                    if (name.isNotEmpty()) {
                        val outFile = File(targetDir, name)
                        // zip-slip protection
                        if (!outFile.canonicalPath.startsWith(rootCanonical + File.separator)) {
                            throw java.io.IOException("Blocked unsafe zip entry: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toDir: File) {
        val list = assetManager.list(fromAssetPath)
        if (list.isNullOrEmpty()) {
            try {
                assetManager.open(fromAssetPath).use { input ->
                    toDir.parentFile?.mkdirs()
                    FileOutputStream(toDir).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy asset $fromAssetPath: ${e.message}")
            }
        } else {
            toDir.mkdirs()
            for (item in list) {
                val childAsset = if (fromAssetPath.isEmpty()) item else "$fromAssetPath/$item"
                val childTarget = File(toDir, item)
                copyAssetFolder(assetManager, childAsset, childTarget)
            }
        }
    }
}
