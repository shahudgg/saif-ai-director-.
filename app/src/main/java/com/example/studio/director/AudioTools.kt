package com.example.studio.director

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.studio.audio.NarrationPolish
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.local.ProviderSettingsManager
import com.example.llm.KeyStoreCrypto
import com.example.llm.ProviderProfile
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)
data class Transcript(val segments: List<TranscriptSegment>, val language: String?, val provider: String) {
    val fullText: String get() = segments.joinToString(" ") { it.text.trim() }
}

object AudioDurations {
    fun durationMs(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(context, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
        catch (_: Exception) { 0L } finally { r.release() }
    }
    fun durationMs(file: File): Long {
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(file.absolutePath); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
        catch (_: Exception) { 0L } finally { r.release() }
    }
}

/** Decodes any Android-supported audio (or the audio track of a video) to 16 kHz mono PCM16 for STT. */
object AudioDecoder {
    fun decodeTo16kMono(context: Context, uri: Uri, maxMs: Long = Long.MAX_VALUE): ShortArray {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        var track = -1
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        require(track >= 0) { "No audio track found" }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        var inRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val out = ArrayList<Short>(inRate * 30)
        val info = MediaCodec.BufferInfo()
        var inputDone = false; var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime / 1000 > maxMs) { codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(idx, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    val ob = codec.getOutputBuffer(o)!!
                    ob.order(ByteOrder.LITTLE_ENDIAN)
                    val sb = ob.asShortBuffer()
                    val frames = sb.remaining() / channels
                    for (f in 0 until frames) {
                        var acc = 0
                        for (c in 0 until channels) acc += sb.get()
                        out.add((acc / channels).toShort())
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    inRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        } finally { codec.stop(); codec.release(); ex.release() }
        return resample(out.toShortArray(), inRate, 16_000)
    }

    fun resample(src: ShortArray, inRate: Int, outRate: Int): ShortArray {
        if (inRate == outRate || src.isEmpty()) return src
        val n = (src.size.toLong() * outRate / inRate).toInt()
        val dst = ShortArray(n)
        for (i in 0 until n) {
            val pos = i.toDouble() * inRate / outRate; val i0 = pos.toInt().coerceAtMost(src.size - 1); val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            val fr = pos - i0; dst[i] = (src[i0] * (1 - fr) + src[i1] * fr).toInt().toShort()
        }
        return dst
    }

    fun wavBytes(pcm: ShortArray, rate: Int = 16_000): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> pcm.forEach { b.putShort(it) } }.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + data.size); h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
        h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(rate); h.putInt(rate * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(data.size)
        return h.array() + data
    }
}

/** Real transcription with timestamps via Whisper-compatible endpoints. Never fabricates a transcript. */
object Transcriber {
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).build()
    private const val CHUNK_MS = 8 * 60 * 1000L

    suspend fun transcribe(context: Context, uri: Uri, languageHint: String? = null): Result<Transcript> = withContext(Dispatchers.IO) {
        ProviderSettingsManager.init(context)
        val st = ProviderSettingsManager.loadState()
        val key = st.apiKeys.firstOrNull { it.isNotBlank() }?.let { KeyStoreCrypto.decrypt(it).trim() }
            ?: return@withContext Result.failure(IllegalStateException(ProviderRegistry.missingMessage(Capability.AUDIO_TRANSCRIPTION)))
        val profile = ProviderProfile.detectProviderFromKey(key, st.providerName)
        val sttPath = profile.sttPath.takeIf { profile.id.lowercase() in ProviderRegistry.WHISPER_PROVIDERS }
        if (sttPath == null) return@withContext Result.failure(IllegalStateException(
            "${profile.displayName} has no Whisper-style transcription endpoint. ${ProviderRegistry.missingMessage(Capability.AUDIO_TRANSCRIPTION)}"))
        val model = when { profile.id.equals("Groq", true) -> "whisper-large-v3"; else -> "whisper-1" }
        try {
            val pcm = AudioDecoder.decodeTo16kMono(context, uri)
            val chunkSamples = (CHUNK_MS * 16).toInt()
            val segs = mutableListOf<TranscriptSegment>(); var lang: String? = null
            var offset = 0
            while (offset < pcm.size) {
                val part = pcm.copyOfRange(offset, minOf(pcm.size, offset + chunkSamples))
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", "chunk.wav", AudioDecoder.wavBytes(part).toRequestBody("audio/wav".toMediaType()))
                    .addFormDataPart("model", model).addFormDataPart("response_format", "verbose_json")
                    .apply { languageHint?.let { l -> isoCode(l)?.let { addFormDataPart("language", it) } } }.build()
                val req = Request.Builder().url(profile.baseUrl.trimEnd('/') + sttPath).header("Authorization", "Bearer $key").post(body).build()
                http.newCall(req).execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    if (!r.isSuccessful) return@withContext Result.failure(IllegalStateException("Transcription failed (HTTP ${r.code})"))
                    val o = JSONObject(txt); lang = lang ?: o.optString("language").ifBlank { null }
                    val arr = o.optJSONArray("segments")
                    val baseMs = offset / 16L
                    if (arr != null) for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        segs += TranscriptSegment(baseMs + (s.optDouble("start") * 1000).toLong(), baseMs + (s.optDouble("end") * 1000).toLong(), s.optString("text").trim())
                    } else if (o.optString("text").isNotBlank())
                        segs += TranscriptSegment(baseMs, baseMs + part.size / 16L, o.optString("text").trim())
                }
                offset += chunkSamples
            }
            val clean = segs.filter { it.text.isNotBlank() }
            if (clean.isEmpty()) Result.failure(IllegalStateException("No speech detected in the audio"))
            else Result.success(Transcript(clean, lang, profile.displayName))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            Result.failure(IllegalStateException("Transcription failed: ${e.javaClass.simpleName}"))
        }
    }

    private fun isoCode(l: String) = when (l.trim().lowercase()) {
        "english" -> "en"; "hindi", "hinglish" -> "hi"; "spanish" -> "es"; "french" -> "fr"; "german" -> "de"; "arabic" -> "ar"; else -> null }
}

/**
 * Real narration files from the device TTS engine, voiced with the app's selected voice (see utils/SaifVoice -
 * the SAME voice as the chat and the live conversation, SAIF ADVANCE VOICE by default),
 * with silence trimmed and loudness matched so scenes flow as one continuous voice.
 */
object DeviceSpeech {
    data class Clip(val file: File, val durationMs: Long)

    private val lock = Mutex()
    private var shared: TextToSpeech? = null

    private fun hasDevanagari(t: String) = t.any { it in '\u0900'..'\u097F' }

    /** Locale for [language] and the actual [text]: Romanised Hinglish is read with Indian English, which sounds smoothest. */
    fun localeFor(language: String?, text: String): Locale {
        val l = language?.trim()?.lowercase()
        return when {
            l == "hindi" || l == "hinglish" -> if (hasDevanagari(text)) Locale("hi", "IN") else Locale("en", "IN")
            l == "spanish" -> Locale("es"); l == "french" -> Locale.FRENCH; l == "german" -> Locale.GERMAN
            l == null || l == "english" -> if (hasDevanagari(text)) Locale("hi", "IN") else Locale("en", "IN")
            else -> Locale.forLanguageTag(language)
        }
    }

    /** Makes the text speakable: no citations, markdown, emojis or URLs, and a closing full stop so the voice lands naturally. */
    fun prepare(raw: String, hinglish: Boolean): String {
        var t = raw
        t = t.replace(Regex("\\[\\d+\\]"), " ")
        t = t.replace(Regex("https?://\\S+"), " ")
        t = t.replace(Regex("[*#`~_>|{}\\[\\]\\\\<>]"), " ")
        t = t.replace(Regex("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]"), " ")
        t = t.replace(Regex("[\\u2600-\\u27BF\\u2B00-\\u2BFF\\uFE0F]"), " ")
        t = t.replace("&", if (hinglish) " aur " else " and ")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.isNotEmpty() && t.last() !in ".!?\u0964") t += "."
        return t
    }

    /** Ordered voices to try for [profile]: exact profile names first, then partial matches, then any matching Indian voice of the right gender. */
    private fun candidateVoices(engine: TextToSpeech, profile: com.example.ui.components.VoiceProfile): List<android.speech.tts.Voice> {
        val voices: List<android.speech.tts.Voice> = try { engine.voices?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        val out = LinkedHashSet<android.speech.tts.Voice>()
        for (t in profile.targetEngineNameMatches) voices.filter { it.name.equals(t, true) }.forEach { out.add(it) }
        for (t in profile.targetEngineNameMatches) voices.filter { it.name.contains(t, true) }.forEach { out.add(it) }
        val male = profile.gender.equals("Male", true)
        voices.filter { v ->
            val n = v.name.lowercase()
            val indian = n.contains("en-in") || n.contains("en_in") || n.contains("hi-in") || n.contains("hi_in")
            indian && (if (male) (n.contains("male") || n.contains("-end") || n.contains("-ene") || n.contains("-hia"))
                       else (n.contains("female") || n.contains("-cxx") || n.contains("-hic")))
        }.forEach { out.add(it) }
        // offline-capable voices first among equals so export never depends on the network when avoidable
        return out.toList().sortedBy { if (it.isNetworkConnectionRequired) 1 else 0 }.take(4)
    }

    private suspend fun engine(context: Context): TextToSpeech? {
        shared?.let { return it }
        val e = withTimeoutOrNull(8_000) { initEngine(context) } ?: return null
        shared = e
        return e
    }

    /** Frees the shared engine. Called when a narration batch is finished. */
    fun release() { try { shared?.shutdown() } catch (_: Exception) {}; shared = null }

    suspend fun synthesize(context: Context, text: String, language: String?, pace: String?, outDir: File, tag: String, voiceId: String? = null): Result<Clip> = lock.withLock {
        outDir.mkdirs()
        val file = File(outDir, "${tag}_${UUID.randomUUID().toString().take(8)}.wav")
        val tts = engine(context) ?: return@withLock Result.failure(IllegalStateException("Device speech engine did not start"))
        val loc = localeFor(language, text)
        val hing = language?.trim()?.lowercase().let { it == "hindi" || it == "hinglish" }
        val spoken = prepare(text, hing)
        if (spoken.isBlank()) return@withLock Result.failure(IllegalStateException("Nothing to speak in this scene"))
        val avail = tts.setLanguage(loc)
        if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = tts.setLanguage(Locale.US)
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED)
                return@withLock Result.failure(IllegalStateException("This device has no voice for ${language ?: "that language"}. Install it in Android TTS settings."))
        }
        // A per-video choice takes precedence; otherwise use the app-wide narration voice.
        val profile = com.example.ui.components.VOICE_PROFILES.firstOrNull { it.id == voiceId }
            ?: com.example.utils.SaifVoice.narrationProfile(context)
        val paceMul = when (pace?.lowercase()) { "slow" -> 0.9f; "fast" -> 1.12f; else -> 1.0f }
        tts.setPitch(profile.pitch)
        tts.setSpeechRate((profile.speechRate.coerceIn(0.9f, 1.08f) * paceMul).coerceIn(0.7f, 1.4f))

        val tries: List<android.speech.tts.Voice?> = candidateVoices(tts, profile).ifEmpty { listOf(null) }
        var ok = false
        for (v in tries) {
            if (v != null) try { tts.voice = v } catch (_: Exception) { continue }
            file.delete()
            ok = withTimeoutOrNull(120_000) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val id = UUID.randomUUID().toString()
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(true) }
                        @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(false) }
                    })
                    val rc = tts.synthesizeToFile(spoken, Bundle(), file, id)
                    cont.invokeOnCancellation { runCatching { tts.stop(); file.delete() } }
                    if (rc != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
                }
            } ?: false
            if (ok && file.exists() && file.length() > 100) break
            ok = false
        }
        if (ok) NarrationPolish.polishFile(file)
        val dur = if (ok) AudioDurations.durationMs(file) else 0L
        if (!ok || dur <= 0L) { file.delete(); Result.failure(IllegalStateException("Speech synthesis produced no audio")) }
        else Result.success(Clip(file, dur))
    }

    private suspend fun initEngine(context: Context): TextToSpeech = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (cont.isActive) { if (status == TextToSpeech.SUCCESS) cont.resume(tts!!) else cont.cancel(IllegalStateException("TTS init failed")) }
        }
    }
}
