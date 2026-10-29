package com.example.studio.director

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves the clean narration as ONE plain .txt file (spoken words only) in Downloads/SAIF AI. */
object VoiceoverExport {
    fun fileName(projectName: String): String {
        val safe = projectName.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "Video" }
        return "${safe}_voiceover_script.txt"
    }

    /** Returns the saved file's Uri, or null on failure. Never writes anything but the narration text. */
    fun save(context: Context, projectName: String, narration: String): Uri? {
        val text = VoiceoverScript.clean(narration)
        if (text.isBlank()) return null
        val name = fileName(projectName)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SAIF AI")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
                } catch (e: Exception) { runCatching { context.contentResolver.delete(uri, null, null) }; return null }
                uri
            } else {
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "voiceover").also { it.mkdirs() }
                val f = File(dir, name); f.writeText(text, Charsets.UTF_8)
                Uri.fromFile(f)
            }
        }.getOrNull()
    }
}
