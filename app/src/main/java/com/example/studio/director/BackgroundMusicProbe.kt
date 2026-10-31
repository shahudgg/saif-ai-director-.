package com.example.studio.director

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/** Verifies that the picked document can be read, contains audio, and has a measured duration. */
object BackgroundMusicProbe {
    fun inspect(context: Context, uri: Uri): Result<Long> = try {
        val extractor = MediaExtractor()
        val formatDuration = try {
            extractor.setDataSource(context, uri, null)
            val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IllegalArgumentException("The selected file has no audio track.")
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000L else 0L
        } finally { extractor.release() }
        val duration = formatDuration.takeIf { it > 0 } ?: AudioDurations.durationMs(context, uri)
        if (duration <= 0) Result.failure(IllegalArgumentException("The selected audio has no readable duration."))
        else Result.success(duration)
    } catch (e: SecurityException) {
        Result.failure(IllegalStateException("Read permission for this audio is unavailable. Pick it again."))
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException(e.message?.take(150) ?: "Could not read this audio file."))
    }
}
