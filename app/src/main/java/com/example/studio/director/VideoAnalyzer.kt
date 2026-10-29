package com.example.studio.director

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

data class TimeRange(val startMs: Long, val endMs: Long) { val length get() = endMs - startMs }

data class VideoAnalysis(
    val uri: String, val durationMs: Long, val width: Int, val height: Int, val rotation: Int, val fps: Float,
    val hasAudio: Boolean, val silences: List<TimeRange>, val sceneChangesMs: List<Long>, val speechRatio: Float?
) {
    val aspectLabel: String get() { val w = if (rotation % 180 == 0) width else height; val h = if (rotation % 180 == 0) height else width
        return if (h > w) "9:16" else if (w > h) "16:9" else "1:1" }
    fun toJson(): JSONObject = JSONObject().apply {
        put("durationMs", durationMs); put("width", width); put("height", height); put("rotation", rotation); put("fps", fps.toDouble())
        put("hasAudio", hasAudio); put("orientation", aspectLabel); speechRatio?.let { put("speechRatio", it.toDouble()) }
        put("silences", JSONArray().apply { silences.forEach { put(JSONArray().put(it.startMs).put(it.endMs)) } })
        put("sceneChangesMs", JSONArray().apply { sceneChangesMs.forEach { put(it) } })
    }
}

/** Pure signal-processing helpers (unit-testable without Android). */
object SignalAnalysis {
    /** Finds runs of RMS below [thresholdDb] lasting at least [minMs]. */
    fun silences(pcm16k: ShortArray, thresholdDb: Double = -42.0, minMs: Long = 700L, windowMs: Int = 50): List<TimeRange> {
        val win = 16 * windowMs
        val out = mutableListOf<TimeRange>(); var runStart = -1L
        var i = 0
        while (i + win <= pcm16k.size) {
            var sum = 0.0
            for (k in i until i + win) { val v = pcm16k[k] / 32768.0; sum += v * v }
            val db = 20 * Math.log10(maxOf(sqrt(sum / win), 1e-9))
            val t = i / 16L
            if (db < thresholdDb) { if (runStart < 0) runStart = t }
            else if (runStart >= 0) { if (t - runStart >= minMs) out += TimeRange(runStart, t); runStart = -1 }
            i += win
        }
        val endT = pcm16k.size / 16L
        if (runStart >= 0 && endT - runStart >= minMs) out += TimeRange(runStart, endT)
        return out
    }

    /** Mean absolute difference between two tiny luminance grids, 0..1. */
    fun frameDiff(a: IntArray, b: IntArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var s = 0.0; for (i in a.indices) s += abs(a[i] - b[i]); return s / a.size / 255.0
    }

    fun detectChanges(times: List<Long>, grids: List<IntArray>, threshold: Double = 0.18): List<Long> =
        (1 until grids.size).filter { frameDiff(grids[it - 1], grids[it]) > threshold }.map { times[it] }

    /** Keep-ranges after cutting [cuts] out of [0, total], dropping fragments shorter than [minKeepMs]. */
    fun keepRanges(totalMs: Long, cuts: List<TimeRange>, minKeepMs: Long = 600L): List<TimeRange> {
        val sorted = cuts.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        val keeps = mutableListOf<TimeRange>(); var cursor = 0L
        for (c in sorted) { if (c.startMs > cursor) keeps += TimeRange(cursor, minOf(c.startMs, totalMs)); cursor = maxOf(cursor, c.endMs) }
        if (cursor < totalMs) keeps += TimeRange(cursor, totalMs)
        return keeps.filter { it.length >= minKeepMs }
    }
}

object VideoAnalyzer {
    private const val GRID = 32
    private const val MAX_COPY_BYTES = 2L * 1024 * 1024 * 1024

    /**
     * Returns a frame that is ALWAYS exactly GRID x GRID, or null when no frame could be decoded.
     * Some OEM decoders ignore the requested size, and reading pixels outside the bitmap throws
     * IllegalArgumentException, so the size is enforced here instead of trusted.
     */
    private fun smallFrame(r: MediaMetadataRetriever, tMs: Long): Bitmap? {
        val us = tMs.coerceAtLeast(0L) * 1000L
        var raw: Bitmap? = null
        try {
            raw = if (android.os.Build.VERSION.SDK_INT >= 27)
                r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, GRID, GRID)
            else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: RuntimeException) { raw = null }
        if (raw == null) {
            try { raw = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } catch (e: RuntimeException) { raw = null }
        }
        val frame = raw ?: return null
        if (frame.width == GRID && frame.height == GRID) return frame
        val scaled = Bitmap.createScaledBitmap(frame, GRID, GRID, true)
        if (scaled !== frame) frame.recycle()
        return scaled
    }

    private fun lumaGrid(bmp: Bitmap): IntArray {
        val px = IntArray(GRID * GRID)
        bmp.getPixels(px, 0, GRID, 0, 0, GRID, GRID)
        return IntArray(px.size) { i ->
            val c = px[i]
            (android.graphics.Color.red(c) * 3 + android.graphics.Color.green(c) * 6 + android.graphics.Color.blue(c)) / 10
        }
    }

    private fun normalize(uri: Uri): Uri =
        if (uri.scheme.isNullOrBlank()) Uri.fromFile(File(uri.toString())) else uri

    /** Returns null on success, otherwise a short human-readable reason. */
    private fun openSource(r: MediaMetadataRetriever, context: Context, uri: Uri): String? = try {
        if (uri.scheme == "file") r.setDataSource(uri.path ?: "") else r.setDataSource(context, uri)
        null
    } catch (e: SecurityException) { "no permission to read this file" }
    catch (e: IllegalArgumentException) { "the file location is not valid (${e.message ?: "bad argument"})" }
    catch (e: RuntimeException) { "the media engine cannot open it (${e.message ?: e.javaClass.simpleName})" }

    private fun copyToCache(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, "director_probe").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000L) it.delete() }
        val out = File(dir, "probe_${System.nanoTime()}.bin")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("the file could not be opened")
            var total = 0L
            input.use { i ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_COPY_BYTES) throw IllegalStateException("the video is larger than 2 GB")
                        o.write(buf, 0, n)
                    }
                }
            }
            return out
        } catch (e: Exception) { out.delete(); throw e }
    }

    suspend fun analyze(context: Context, uri: Uri, onStep: (String) -> Unit = {}): Result<VideoAnalysis> = withContext(Dispatchers.IO) {
        var temp: File? = null
        var r = MediaMetadataRetriever()
        var stage = "opening the video"
        try {
            val original = normalize(uri)
            var active = original
            var problem = openSource(r, context, active)
            var dur = if (problem == null) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L else 0L
            if (problem != null || dur <= 0) {
                // Content URIs are not always seekable for the media engine: work from a bounded local copy.
                onStep("Making a safe local copy of the video")
                stage = "copying the video"
                r.release(); r = MediaMetadataRetriever()
                val t = copyToCache(context, original); temp = t
                active = Uri.fromFile(t)
                stage = "opening the copied video"
                problem = openSource(r, context, active)
                dur = if (problem == null) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L else 0L
            }
            if (problem != null) return@withContext Result.failure(IllegalStateException("Could not open this video: $problem. Try another file or an MP4 (H.264)."))
            if (dur <= 0) return@withContext Result.failure(IllegalStateException("This video has no readable duration (it may be corrupted or unsupported). Try another file or an MP4 (H.264)."))

            stage = "reading video details"
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0).let { ((it % 360) + 360) % 360 }
            val fps = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f).takeIf { it in 1f..240f } ?: 30f
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            if (w <= 0 || h <= 0) {
                val f = try { r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } catch (e: RuntimeException) { null }
                if (f != null) { w = f.width; h = f.height; f.recycle() }
            }
            if (w <= 0 || h <= 0) return@withContext Result.failure(IllegalStateException("No video track was found in this file."))

            stage = "sampling frames"
            onStep("Sampling frames for scene changes")
            val step = maxOf(1000L, dur / 120L)            // bounded: at most ~120 frames regardless of length
            val times = mutableListOf<Long>(); val grids = mutableListOf<IntArray>()
            var t = 0L
            while (t < dur) {
                ensureActive()
                smallFrame(r, minOf(t, dur - 1))?.let { bmp ->
                    times += t; grids += lumaGrid(bmp); bmp.recycle()
                }
                t += step
            }
            val changes = SignalAnalysis.detectChanges(times, grids)

            var silences = emptyList<TimeRange>(); var speech: Float? = null
            if (hasAudio) {
                stage = "analyzing audio"
                onStep("Analyzing audio for speech and silence")
                runCatching { AudioDecoder.decodeTo16kMono(context, active, maxMs = 30 * 60 * 1000L) }.getOrNull()?.let { pcm ->
                    silences = SignalAnalysis.silences(pcm)
                    speech = (1f - silences.sumOf { it.length }.toFloat() / dur).coerceIn(0f, 1f)
                }
            }
            Result.success(VideoAnalysis(original.toString(), dur, w, h, rot, fps, hasAudio, silences, changes, speech))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val why = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            android.util.Log.w("VideoAnalyzer", "analyze failed while $stage: ${e.javaClass.simpleName}")
            Result.failure(IllegalStateException("Could not analyze video while $stage: $why"))
        } finally {
            try { r.release() } catch (_: Exception) {}
            temp?.delete()
        }
    }

    /** Deterministic fallback edit when no LLM is available: trims long silences only. Fully reversible (source untouched). */
    fun silenceTrimPlan(a: VideoAnalysis): List<TimeRange> =
        SignalAnalysis.keepRanges(a.durationMs, a.silences.map { TimeRange(it.startMs + 250, it.endMs - 150) }.filter { it.length > 400 })
}
