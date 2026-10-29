package com.example.studio.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.VideoItem
import com.example.studio.render.FrameMediaProvider
import com.example.studio.render.RenderPlan
import com.example.studio.render.StudioFrameRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExportConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val bitrate: Int = 10_000_000,
    val audioBitrate: Int = 128_000,
    val bitrateTier: String = "Standard",
    val containerFormat: String = "MP4"
)

data class ExportResult(
    val success: Boolean,
    val outputUri: Uri? = null,
    val filePath: String? = null,
    val verifiedDurationMs: Long = 0L,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList()
)

/** Export sizes derived from the project's aspect ratio (a 16:9 project must not be exported as 9:16). */
object ExportPresets {
    /** [resolution] is "720p" or "1080p": the length of the SHORT side. Both sides are even. */
    fun dimensions(aspect: String, resolution: String): Pair<Int, Int> {
        val shortSide = if (resolution.trim().startsWith("720")) 720 else 1080
        val parts = aspect.split(":").mapNotNull { it.trim().toIntOrNull() }
        val aw = parts.getOrNull(0)?.takeIf { it > 0 } ?: 9
        val ah = parts.getOrNull(1)?.takeIf { it > 0 } ?: 16
        fun even(v: Int) = if (v % 2 == 0) v else v + 1
        return if (aw <= ah) Pair(shortSide, even(Math.round(shortSide.toFloat() * ah / aw)))
        else Pair(even(Math.round(shortSide.toFloat() * aw / ah)), shortSide)
    }

    /** Bytes needed in the worst case: raw video file + muxed copy + gallery copy. */
    fun estimateBytes(bitrate: Int, audioBitrate: Int, durationUs: Long): Long =
        ((bitrate + audioBitrate) / 8.0 * (durationUs / 1_000_000.0) * 3.0).toLong() + 32L * 1024 * 1024
}

object StudioExportEngine {

    fun getBaseBitrate(width: Int, height: Int): Int {
        val maxDim = maxOf(width, height)
        return when {
            maxDim >= 3840 -> 25_000_000
            maxDim >= 1920 -> 10_000_000
            else -> 5_000_000
        }
    }

    fun calculateBitrate(width: Int, height: Int, tier: String): Int {
        val base = getBaseBitrate(width, height)
        return when (tier.trim().lowercase()) {
            "high" -> (base * 1.8).toInt()
            "cinema master", "cinema" -> (base * 3.5).toInt()
            else -> base
        }
    }

    fun calculateEtaSeconds(totalFrames: Int, framesDone: Int, elapsedMs: Long): Int {
        if (framesDone <= 0) return 0
        val framesRemaining = (totalFrames - framesDone).coerceAtLeast(0)
        val msPerFrame = elapsedMs.toFloat() / framesDone
        return ((framesRemaining * msPerFrame) / 1000f).toInt()
    }

    fun formatEta(etaSec: Int): String {
        val safeSec = etaSec.coerceAtLeast(0)
        val mins = safeSec / 60
        val secs = safeSec % 60
        return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }

    /** Frame time in microseconds without drift: frame * 1e6 / fps, rounded. */
    fun frameTimeUs(frameIndex: Int, fps: Int): Long = Math.round(frameIndex * 1_000_000.0 / fps)

    // ------------------------------------------------------------------------------------------------------------
    // Media access for the shared renderer
    // ------------------------------------------------------------------------------------------------------------

    private class ExportMedia(private val context: Context, project: StudioProject) : FrameMediaProvider {
        private val retrievers = HashMap<String, MediaMetadataRetriever>()
        private val images = object : LinkedHashMap<String, Bitmap?>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?) = size > 6
        }

        init {
            project.items.filterIsInstance<VideoItem>().forEach { v ->
                if (v.mediaUri.isNotBlank()) {
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(context, Uri.parse(v.mediaUri))
                        retrievers[v.id] = r
                    } catch (_: Exception) { /* a missing clip is reported by preflight; here it just draws nothing */ }
                }
            }
        }

        override fun videoFrame(item: VideoItem, localTimeUs: Long): Bitmap? {
            val r = retrievers[item.id] ?: return null
            val srcUs = item.sourceInUs + (localTimeUs * item.playbackSpeed).toLong()
            return r.getFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }

        override fun image(item: ImageItem, width: Int, height: Int): Bitmap? {
            val bmp = images.getOrPut(item.mediaUri) { decode(item.mediaUri, width, height) }
            return bmp ?: throw IllegalStateException("Image '${item.label}' could not be decoded for export")
        }

        fun canDecode(uri: String): Boolean = decode(uri, 64, 64) != null

        private fun decode(uri: String, w: Int, h: Int): Bitmap? = try {
            val u = Uri.parse(uri)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
            context.contentResolver.openInputStream(u)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: Exception) { null }

        fun release() {
            retrievers.values.forEach { runCatching { it.release() } }
            retrievers.clear(); images.clear()
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Encoder helpers
    // ------------------------------------------------------------------------------------------------------------

    private fun encoderCandidates(width: Int, height: Int, fps: Int): List<String> {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
        }
        val usable = infos.filter { info ->
            try {
                val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                val flexible = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                val sizeOk = caps.videoCapabilities?.isSizeSupported(width, height) ?: true
                flexible && sizeOk
            } catch (_: Exception) { false }
        }
        fun isSoftware(n: String) = n.startsWith("OMX.google.") || n.startsWith("c2.android.")
        // Hardware first (fast), software as a safe fallback.
        return (usable.filterNot { isSoftware(it.name) } + usable.filter { isSoftware(it.name) }).map { it.name }
    }

    private fun buildFormat(config: ExportConfig, withProfile: Boolean): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            // Tell the player how to read the pixels: BT.709, limited range, SDR. Matches YuvConverter exactly.
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            if (withProfile) setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
        }

    /** Creates and starts the first encoder that accepts the format; reports why none did. */
    private fun startEncoder(config: ExportConfig): Pair<MediaCodec, String> {
        val candidates = encoderCandidates(config.width, config.height, config.fps)
        require(candidates.isNotEmpty()) { "This device has no H.264 encoder that supports ${config.width}x${config.height}. Try 720p." }
        var lastError: Exception? = null
        for (name in candidates) {
            for (withProfile in listOf(true, false)) {
                var codec: MediaCodec? = null
                try {
                    codec = MediaCodec.createByCodecName(name)
                    codec.configure(buildFormat(config, withProfile), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    codec.start()
                    return codec to name
                } catch (e: Exception) {
                    lastError = e
                    runCatching { codec?.release() }
                }
            }
        }
        throw IllegalStateException("No H.264 encoder could be started (${lastError?.message ?: "unknown error"}). Try a lower resolution.")
    }

    // ------------------------------------------------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Real multi-track export to Movies/SAIF AI. The same [StudioFrameRenderer] that draws the preview draws every frame here;
     * pixels reach the encoder through its real input Image planes (stride-aware), tagged BT.709/limited.
     */
    suspend fun exportProject(
        context: Context,
        project: StudioProject,
        config: ExportConfig = ExportConfig(),
        onProgress: (progress: Float, status: String, etaMs: Long) -> Unit,
        isCancelled: () -> Boolean
    ): ExportResult = withContext(Dispatchers.Default) {
        val totalDurationUs = project.totalDurationUs.coerceAtLeast(1_000_000L)
        val totalFrames = Math.round(totalDurationUs / 1_000_000.0 * config.fps).toInt().coerceAtLeast(config.fps)

        if (config.width % 2 != 0 || config.height % 2 != 0)
            return@withContext ExportResult(success = false, errorMessage = "Export size must be even (${config.width}x${config.height}).")

        // Storage check before doing any work.
        val needed = ExportPresets.estimateBytes(config.bitrate, config.audioBitrate, totalDurationUs)
        val free = context.cacheDir.usableSpace
        if (free < needed) {
            return@withContext ExportResult(success = false,
                errorMessage = "Not enough free storage: need about ${needed / (1024 * 1024)} MB, only ${free / (1024 * 1024)} MB available.")
        }

        val media = ExportMedia(context, project)
        val tempVideoFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.mp4")
        val finalFile = File(context.cacheDir, "export_final_${System.currentTimeMillis()}.mp4")
        var muxer: MediaMuxer? = null
        var codec: MediaCodec? = null

        fun cleanup() {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null
            runCatching { muxer?.release() }; muxer = null
            media.release()
            tempVideoFile.delete(); finalFile.delete()
        }

        // Pre-flight validation: fail early and clearly instead of mid-render.
        val preflight = project.items.mapNotNull { item ->
            when (item) {
                is CodeSceneItem ->
                    if (com.example.studio.director.SceneJson.decodeFromTimeline(item.htmlCode) == null) "Scene '${item.label}' is a legacy HTML scene the exporter cannot render" else null
                is ImageItem -> if (!media.canDecode(item.mediaUri)) "Image '${item.label}' is missing or unreadable" else null
                is VideoItem -> if (item.mediaUri.isBlank()) "Video '${item.label}' has no source" else null
                else -> null
            }
        }
        if (preflight.isNotEmpty()) {
            media.release()
            return@withContext ExportResult(success = false, errorMessage = "Export blocked: " + preflight.take(3).joinToString("; "))
        }

        try {
            onProgress(0.02f, "Starting H.264 encoder (${config.width}x${config.height} @ ${config.fps} fps)...", 0L)
            val started = startEncoder(config)
            val enc = started.first
            codec = enc

            val mux = MediaMuxer(tempVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            var videoTrack = -1
            var muxerStarted = false
            val info = MediaCodec.BufferInfo()
            var writtenSamples = 0

            /** Pulls every available encoded frame into the muxer. Returns true once end-of-stream was seen. */
            fun drain(timeoutUs: Long): Boolean {
                while (true) {
                    val oi = enc.dequeueOutputBuffer(info, timeoutUs)
                    when {
                        oi == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                        oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Encoder output format changed twice" }
                            videoTrack = mux.addTrack(enc.outputFormat); mux.start(); muxerStarted = true
                        }
                        oi >= 0 -> {
                            val out = enc.getOutputBuffer(oi)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && out != null && muxerStarted) {
                                out.position(info.offset); out.limit(info.offset + info.size)
                                mux.writeSampleData(videoTrack, out, info); writtenSamples++
                            }
                            enc.releaseOutputBuffer(oi, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                        }
                    }
                }
            }

            /** Waits for a free input buffer while keeping the output drained (the old code silently dropped frames here). */
            fun nextInputIndex(): Int {
                val deadline = System.nanoTime() + 10_000_000_000L
                while (true) {
                    if (isCancelled()) return -1
                    drain(0L)
                    val idx = enc.dequeueInputBuffer(5_000L)
                    if (idx >= 0) return idx
                    check(System.nanoTime() < deadline) { "The video encoder stopped responding." }
                }
            }

            val frameBitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap)
            val argb = IntArray(config.width * config.height)
            val startNs = System.nanoTime()

            for (frameIndex in 0 until totalFrames) {
                if (isCancelled()) { cleanup(); return@withContext ExportResult(success = false, errorMessage = "Export cancelled by user") }
                val tUs = frameTimeUs(frameIndex, config.fps)

                // One shared render path. The canvas is cleared every frame and each layer is drawn exactly once.
                frameCanvas.drawColor(AndroidColor.BLACK)
                StudioFrameRenderer.render(frameCanvas, config.width, config.height, RenderPlan.at(project, tUs), media)
                frameBitmap.getPixels(argb, 0, config.width, 0, 0, config.width, config.height)

                val idx = nextInputIndex()
                if (idx < 0) { cleanup(); return@withContext ExportResult(success = false, errorMessage = "Export cancelled by user") }
                val image = enc.getInputImage(idx)
                    ?: throw IllegalStateException("The encoder did not expose an input image (device not supported).")
                val planes = image.planes
                YuvConverter.argbToYuv420(
                    argb, config.width, config.height,
                    YuvConverter.Plane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride),
                    YuvConverter.Plane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride),
                    YuvConverter.Plane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride)
                )
                enc.queueInputBuffer(idx, 0, config.width * config.height * 3 / 2, tUs, 0)

                val progress = (frameIndex + 1).toFloat() / totalFrames
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                val remainingMs = if (progress > 0f) (elapsedMs / progress - elapsedMs).toLong().coerceAtLeast(0L) else 0L
                onProgress(0.04f + progress * 0.76f, "Rendering frame ${frameIndex + 1}/$totalFrames", remainingMs)
            }

            // End of stream as its own empty buffer, then drain until the encoder confirms it.
            val eosIdx = nextInputIndex()
            if (eosIdx < 0) { cleanup(); return@withContext ExportResult(success = false, errorMessage = "Export cancelled by user") }
            enc.queueInputBuffer(eosIdx, 0, 0, frameTimeUs(totalFrames, config.fps), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            val drainDeadline = System.nanoTime() + 20_000_000_000L
            while (!drain(10_000L)) {
                if (isCancelled()) { cleanup(); return@withContext ExportResult(success = false, errorMessage = "Export cancelled by user") }
                check(System.nanoTime() < drainDeadline) { "The encoder did not finish." }
            }
            frameBitmap.recycle()
            media.release()
            runCatching { enc.stop() }; enc.release(); codec = null
            check(muxerStarted && writtenSamples > 0) { "The encoder produced no video frames." }
            mux.stop(); mux.release(); muxer = null

            onProgress(0.84f, "Mixing audio tracks...", 0L)
            val audioRes = ExportAudio.muxWithAudio(context, project, tempVideoFile, finalFile, totalDurationUs, config.audioBitrate, isCancelled)
            if (audioRes.isFailure) {
                val msg = audioRes.exceptionOrNull()?.message ?: "Audio mixing failed"
                cleanup()
                return@withContext ExportResult(success = false, errorMessage = msg)
            }
            val fileToSave = audioRes.getOrNull() ?: tempVideoFile   // null = project has no audible audio
            val hadAudio = audioRes.getOrNull() != null

            onProgress(0.93f, "Saving to Gallery (Movies/SAIF AI)...", 0L)
            val savedUri = saveToGallery(context, fileToSave, project.name)
            tempVideoFile.delete(); finalFile.delete()
            if (savedUri == null) return@withContext ExportResult(success = false, errorMessage = "Failed to register video in MediaStore")

            onProgress(0.97f, "Verifying exported video...", 0L)
            val warnings = mutableListOf<String>()
            val expectedMs = totalFrames * 1000L / config.fps
            val v = verify(context, savedUri, config, expectedMs, hadAudio, warnings)
            if (v != null) return@withContext ExportResult(success = false, outputUri = savedUri, errorMessage = v)

            onProgress(1.0f, "Export complete", 0L)
            val dur = MediaMetadataRetriever().let { r ->
                try { r.setDataSource(context, savedUri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
                finally { r.release() }
            }
            ExportResult(success = true, outputUri = savedUri, verifiedDurationMs = dur, warnings = warnings)

        } catch (e: kotlinx.coroutines.CancellationException) {
            cleanup(); throw e
        } catch (e: Exception) {
            cleanup()
            ExportResult(success = false, errorMessage = "Export failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Returns an error message when the file is unusable, or null. Soft problems are added to [warnings]. */
    private fun verify(context: Context, uri: Uri, config: ExportConfig, expectedMs: Long, expectAudio: Boolean, warnings: MutableList<String>): String? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            if (!hasVideo || durMs <= 0L) return "Verification failed: the exported MP4 is unreadable or empty."
            val tol = maxOf(500L, expectedMs / 25)
            if (kotlin.math.abs(durMs - expectedMs) > tol)
                return "Verification failed: exported length ${durMs} ms differs from the timeline (${expectedMs} ms)."
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w != null && h != null && rot == 0 && (w != config.width || h != config.height))
                warnings += "Exported size is ${w}x$h, expected ${config.width}x${config.height}."
            if (expectAudio && r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == null)
                warnings += "The project has audio but the exported file reports no audio track."
        } catch (e: Exception) {
            return "Verification failed: ${e.message ?: e.javaClass.simpleName}"
        } finally { r.release() }

        // Colour tags: confirm the stream is tagged BT.709 (not the old "gbr"/unspecified output).
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    if (f.containsKey(MediaFormat.KEY_COLOR_STANDARD) && f.getInteger(MediaFormat.KEY_COLOR_STANDARD) != MediaFormat.COLOR_STANDARD_BT709)
                        warnings += "Video colour standard is not BT.709."
                }
            }
        } catch (_: Exception) { /* informational only */ } finally { ex.release() }
        return null
    }

    private fun saveToGallery(context: Context, sourceFile: File, projectName: String): Uri? {
        val safe = projectName.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "Video" }
        val fileName = "SAIF_${safe}_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, fileName)
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SAIF AI")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Could not open the gallery output stream")
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        return uri
    }

    /** Exports the frame at [timeUs] as a PNG, at the project's own aspect ratio, through the same shared renderer. */
    fun exportCurrentFrame(context: Context, project: StudioProject, timeUs: Long): Uri? {
        val (w, h) = ExportPresets.dimensions(project.aspect, "1080p")
        val media = ExportMedia(context, project)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.BLACK)
            StudioFrameRenderer.render(canvas, w, h, RenderPlan.at(project, timeUs), media)
            val fileName = "SAIF_Frame_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SAIF AI")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) context.contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            return uri
        } finally {
            bitmap.recycle(); media.release()
        }
    }

    /** Exports project captions as standard .srt subtitle text file. */
    fun exportSubtitlesSrt(project: StudioProject): String {
        val sb = StringBuilder()
        val textItems = project.items.filterIsInstance<TextItem>().sortedBy { it.startUs }
        textItems.forEachIndexed { index, item ->
            val startMs = item.startUs / 1000L
            val endMs = (item.startUs + item.durationUs) / 1000L
            sb.appendLine("${index + 1}")
            sb.appendLine("${formatSrtTime(startMs)} --> ${formatSrtTime(endMs)}")
            sb.appendLine(item.text)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun formatSrtTime(totalMs: Long): String {
        val hours = totalMs / 3600000L
        val minutes = (totalMs % 3600000L) / 60000L
        val seconds = (totalMs % 60000L) / 1000L
        val millis = totalMs % 1000L
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}
