package com.example.studio.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.studio.audio.AudioMixer
import com.example.studio.model.AudioItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TimelineItem
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline audio render for export: decodes every audio-bearing timeline item to a temporary 48 kHz stereo
 * PCM file (bounded memory), mixes in 1024-frame blocks using [AudioMixer] gains (volume, fades, ducking),
 * encodes AAC and muxes it together with the already-rendered video track.
 */
object ExportAudio {
    private const val RATE = 48_000
    private const val BLOCK = 1024

    private class Src(val item: TimelineItem, val raw: File, val frames: Long)

    fun audioSources(p: StudioProject): List<TimelineItem> = p.items.filter {
        (it is VoiceItem && !it.audioUri.isNullOrBlank()) || (it is AudioItem && !it.audioUri.isNullOrBlank()) || (it is VideoItem && it.volume > 0f && it.mediaUri.isNotBlank())
    }

    private fun uriOf(i: TimelineItem): String? = when (i) { is VoiceItem -> i.audioUri; is AudioItem -> i.audioUri; is VideoItem -> i.mediaUri; else -> null }
    private fun srcInUs(i: TimelineItem): Long = if (i is VideoItem) i.sourceInUs else 0L

    /** Returns null when no item has audible audio (video-only export is then correct), else the muxed file or a failure. */
    fun muxWithAudio(context: Context, project: StudioProject, videoOnly: File, out: File, totalUs: Long, audioBitrate: Int, isCancelled: () -> Boolean): Result<File?> {
        val items = audioSources(project)
        val tmp = mutableListOf<File>()
        try {
            val srcs = items.mapNotNull { it ->
                val u = uriOf(it) ?: return@mapNotNull null
                val raw = File(context.cacheDir, "xa_${it.id}.pcm").also { f -> tmp += f }
                val frames = decodeToRaw(context, Uri.parse(u), raw, srcInUs(it) / 1000, it.durationUs / 1000)
                if (frames > 0) Src(it, raw, frames) else null   // video without an audio track simply contributes nothing
            }
            if (srcs.isEmpty()) return Result.success(null)

            val encoded = encodeAac(project, srcs, totalUs, audioBitrate, isCancelled)
                ?: return Result.failure(IllegalStateException("Audio export cancelled"))
            muxTogether(videoOnly, encoded.first, encoded.second, out)
            return Result.success(out)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Audio mixing failed: ${e.javaClass.simpleName}"))
        } finally { tmp.forEach { it.delete() } }
    }

    /** Decodes to raw s16le stereo 48 kHz. Returns frame count (0 when the source has no audio track). */
    private fun decodeToRaw(context: Context, uri: Uri, outFile: File, startMs: Long, maxMs: Long): Long {
        val ex = MediaExtractor()
        try { ex.setDataSource(context, uri, null) } catch (_: Exception) { ex.release(); return 0 }
        var track = -1
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        if (track < 0) { ex.release(); return 0 }
        ex.selectTrack(track); ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false; var frames = 0L
        var pos = 0.0                                    // fractional read position (input frames), carried across chunks
        val limitFrames = maxMs * RATE / 1000
        val skipUs = startMs * 1000
        java.io.BufferedOutputStream(java.io.FileOutputStream(outFile), 1 shl 16).use { os ->
            try {
                while (!outDone && frames < limitFrames) {
                    if (!inDone) {
                        val ii = codec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                            if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 10_000)
                    if (oi >= 0) {
                        val sb = codec.getOutputBuffer(oi)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val inFrames = sb.remaining() / ch
                        val chunk = ShortArray(inFrames * ch); sb.get(chunk)
                        val skipFrames = if (skipUs > info.presentationTimeUs) ((skipUs - info.presentationTimeUs) * rate / 1_000_000L).toInt() else 0
                        if (skipFrames >= inFrames) { pos = 0.0 }
                        else {
                            if (pos < skipFrames) pos = skipFrames.toDouble()
                            val step = rate.toDouble() / RATE
                            val bytes = ByteBuffer.allocate(((inFrames - pos) / step + 4).toInt() * 4).order(ByteOrder.LITTLE_ENDIAN)
                            while (pos < inFrames && frames < limitFrames) {
                                val i0 = pos.toInt(); val i1 = minOf(i0 + 1, inFrames - 1); val fr = (pos - i0).toFloat()
                                for (c in 0..1) {
                                    val cc = if (ch > 1) c else 0
                                    val a0 = chunk[i0 * ch + cc] / 32768f; val a1 = chunk[i1 * ch + cc] / 32768f
                                    bytes.putShort(((a0 + (a1 - a0) * fr) * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                                }
                                frames++; pos += step
                            }
                            pos -= inFrames
                            if (pos < 0) pos = 0.0
                            os.write(bytes.array(), 0, bytes.position())
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } finally { try { codec.stop() } catch (_: Exception) {}; codec.release(); ex.release() }
        }
        return frames
    }

    private fun encodeAac(project: StudioProject, srcs: List<Src>, totalUs: Long, bitrate: Int, isCancelled: () -> Boolean): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>>? {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE,android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BLOCK * 4 * 4)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        val files = srcs.map { RandomAccessFile(it.raw, "r") }
        val result = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>(); var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        val totalFrames = totalUs * RATE / 1_000_000L
        var frame = 0L; var inputDone = false; var outputDone = false
        val mix = FloatArray(BLOCK * 2); val tmpBytes = ByteArray(BLOCK * 4)
        val lastGain = FloatArray(srcs.size)        // per-source gain of the previous block: ramped, so ducking/fades never click
        try {
            while (!outputDone) {
                if (isCancelled()) return null
                if (!inputDone) {
                    val ii = enc.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = enc.getInputBuffer(ii)!!; buf.clear()
                        if (frame >= totalFrames) { enc.queueInputBuffer(ii, 0, 0, frame * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else {
                            val tUs = frame * 1_000_000L / RATE
                            val n = minOf(BLOCK.toLong(), totalFrames - frame).toInt()
                            java.util.Arrays.fill(mix, 0f)
                            val voice = AudioMixer.isVoiceActive(project, tUs)
                            srcs.forEachIndexed { si, s ->
                                val it = s.item
                                if (tUs < it.startUs || tUs >= it.startUs + it.durationUs) return@forEachIndexed
                                val g = AudioMixer.calculateEffectiveGain(it, tUs, voice)
                                if (g <= 0f) return@forEachIndexed
                                val startFrame = (tUs - it.startUs) * RATE / 1_000_000L
                                if (startFrame >= s.frames) return@forEachIndexed
                                val want = minOf(n.toLong(), s.frames - startFrame).toInt()
                                files[si].seek(startFrame * 4); files[si].readFully(tmpBytes, 0, want * 4)
                                val bb = ByteBuffer.wrap(tmpBytes, 0, want * 4).order(ByteOrder.LITTLE_ENDIAN)
                                val g0 = lastGain[si]
                                for (k in 0 until want * 2) {
                                    val gk = g0 + (g - g0) * ((k / 2) + 1) / want
                                    mix[k] += bb.getShort() / 32768f * gk
                                }
                                lastGain[si] = g
                            }
                            val ob = buf.order(ByteOrder.LITTLE_ENDIAN)
                            for (k in 0 until n * 2) ob.putShort((AudioMixer.softLimit(mix[k]).coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                            enc.queueInputBuffer(ii, 0, n * 4, tUs, 0)
                            frame += n
                        }
                    }
                }
                val oi = enc.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = enc.outputFormat
                    oi >= 0 -> {
                        val ob = enc.getOutputBuffer(oi)!!
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val bytes = ByteArray(info.size); ob.position(info.offset); ob.get(bytes)
                            val copy = MediaCodec.BufferInfo().also { b -> b.set(0, info.size, info.presentationTimeUs, info.flags) }
                            result += bytes to copy
                        }
                        enc.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally { try { enc.stop() } catch (_: Exception) {}; enc.release(); files.forEach { it.close() } }
        return (outFormat ?: return null) to result
    }

    private fun muxTogether(videoFile: File, audioFormat: MediaFormat, audio: List<Pair<ByteArray, MediaCodec.BufferInfo>>, out: File) {
        val ex = MediaExtractor(); ex.setDataSource(videoFile.absolutePath)
        var vt = -1
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { vt = i; break }
        require(vt >= 0) { "Rendered video has no video track" }
        ex.selectTrack(vt)
        val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val vIdx = mux.addTrack(ex.getTrackFormat(vt)); val aIdx = mux.addTrack(audioFormat)
            mux.start()
            val buf = ByteBuffer.allocate(2 shl 20); val bi = MediaCodec.BufferInfo()
            var ai = 0
            while (true) {                                  // interleave by timestamp
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val vt0 = ex.sampleTime
                while (ai < audio.size && audio[ai].second.presentationTimeUs <= vt0) {
                    val (b, info) = audio[ai]; mux.writeSampleData(aIdx, ByteBuffer.wrap(b), info); ai++
                }
                bi.set(0, n, vt0, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(vIdx, buf, bi); ex.advance()
            }
            while (ai < audio.size) { val (b, info) = audio[ai]; mux.writeSampleData(aIdx, ByteBuffer.wrap(b), info); ai++ }
            mux.stop()
        } finally { mux.release(); ex.release() }
    }
}
