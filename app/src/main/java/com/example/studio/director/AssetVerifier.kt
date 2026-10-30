package com.example.studio.director

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Base64
import com.example.llm.LlmRole
import com.example.studio.ai.AiClient
import com.example.studio.ai.AiProfile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Technical facts read from the actual file bytes, never from the file name. */
data class MediaProbe(
    val valid: Boolean, val kind: String,            // image | video | audio | unknown
    val width: Int = 0, val height: Int = 0, val durationMs: Long = 0L,
    val hasAudio: Boolean = false, val sizeBytes: Long = 0L, val error: String? = null
)

data class AssetVerdict(
    val accepted: Boolean, val reason: String, val visuallyVerified: Boolean = false, val score: Int? = null,
    val probe: MediaProbe? = null
)

object MediaInspector {

    /** Sniffs magic bytes so a renamed text/HTML error page can never pass as media. */
    fun sniffKind(head: ByteArray): String {
        fun starts(vararg b: Int) = head.size >= b.size && b.indices.all { head[it] == b[it].toByte() }
        return when {
            starts(0x89, 0x50, 0x4E, 0x47) || starts(0xFF, 0xD8, 0xFF) || starts(0x47, 0x49, 0x46) -> "image"
            head.size >= 12 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "image"
            head.size >= 12 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> "video"       // mp4/m4a/mov (disambiguated by probe)
            starts(0x1A, 0x45, 0xDF, 0xA3) -> "video"                                          // webm/mkv
            starts(0x49, 0x44, 0x33) || starts(0xFF, 0xFB) || starts(0xFF, 0xF3) -> "audio"    // mp3
            head.size >= 12 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WAVE" -> "audio"
            starts(0x4F, 0x67, 0x67, 0x53) -> "audio"                                          // ogg
            else -> "unknown"
        }
    }

    fun probe(context: Context, file: File): MediaProbe {
        if (!file.exists() || file.length() == 0L) return MediaProbe(false, "unknown", error = "File missing or empty")
        val head = ByteArray(16).also { b -> file.inputStream().use { it.read(b) } }
        val kind = sniffKind(head)
        if (kind == "unknown") return MediaProbe(false, "unknown", sizeBytes = file.length(), error = "Content is not a recognised media format")
        return try {
            if (kind == "image") {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, o)
                if (o.outWidth <= 0 || o.outHeight <= 0) MediaProbe(false, "image", sizeBytes = file.length(), error = "Image cannot be decoded")
                else MediaProbe(true, "image", o.outWidth, o.outHeight, sizeBytes = file.length())
            } else {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val hasA = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                    val hasV = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" || w > 0
                    val k = if (hasV) "video" else "audio"
                    if (dur <= 0L) MediaProbe(false, k, sizeBytes = file.length(), error = "Zero duration / undecodable")
                    else MediaProbe(true, k, w, h, dur, hasA, file.length())
                } finally { r.release() }
            }
        } catch (e: Exception) {
            MediaProbe(false, kind, sizeBytes = file.length(), error = "Decode failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * True when the picture really has transparent pixels. This decides whether the asset is placed as a small
     * STICKER above the graphics (a cut-out PNG) or as a full-frame BACKDROP behind them — guessing from the file
     * extension alone was wrong often enough to hide an asset behind an opaque rectangle.
     */
    fun hasAlpha(file: File, probe: MediaProbe): Boolean {
        if (probe.kind != "image") return false
        return try {
            val edge = maxOf(probe.width, probe.height)
            val o = BitmapFactory.Options().apply { inSampleSize = maxOf(1, edge / 256) }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, o) ?: return false
            try {
                // `try` is not a lambda, so `return@try` is invalid Kotlin - the block below
                // simply evaluates to the Boolean instead, which is the try-expression's value.
                if (!bmp.hasAlpha()) {
                    false
                } else {
                    val w = bmp.width; val h = bmp.height
                    if (w < 4 || h < 4) {
                        false
                    } else {
                        // A cut-out sticker has transparent corners; a photograph never does.
                        val corners = intArrayOf(bmp.getPixel(0, 0), bmp.getPixel(w - 1, 0), bmp.getPixel(0, h - 1), bmp.getPixel(w - 1, h - 1))
                        if (corners.count { ((it ushr 24) and 0xFF) < 24 } >= 2) {
                            true
                        } else {
                            val px = IntArray(w * h)
                            bmp.getPixels(px, 0, w, 0, 0, w, h)
                            px.count { ((it ushr 24) and 0xFF) < 24 }.toFloat() / px.size > 0.06f
                        }
                    }
                }
            } finally { runCatching { bmp.recycle() } }
        } catch (_: Exception) { false }
    }

    /** Down-sampled JPEG (base64) for multimodal verification. Frames for videos come from the middle of the clip. */
    fun previewJpegBase64(file: File, probe: MediaProbe, maxEdge: Int = 640): String? = try {
        val bmp: Bitmap? = if (probe.kind == "image") {
            val o = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(probe.width, probe.height) / maxEdge) }
            BitmapFactory.decodeFile(file.absolutePath, o)
        } else {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(file.absolutePath); r.getFrameAtTime(probe.durationMs * 500, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } finally { r.release() }
        }
        bmp?.let {
            val out = ByteArrayOutputStream(); it.compress(Bitmap.CompressFormat.JPEG, 80, out); it.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    } catch (_: Exception) { null }
}

/** Pure decision logic (unit-testable): technical gate first, then visual score when available. */
object AssetDecision {
    fun decide(probe: MediaProbe, req: AssetRequest, targetAspect: String?, visual: VisualCheck?): AssetVerdict {
        if (!probe.valid) return AssetVerdict(false, "Rejected: ${probe.error ?: "invalid file"}", probe = probe)
        val wantKinds = when (req.kind) { "image", "overlay" -> setOf("image", "video"); "video" -> setOf("video"); "audio" -> setOf("audio"); else -> setOf(req.kind) }
        if (probe.kind !in wantKinds) return AssetVerdict(false, "Rejected: expected ${req.kind}, file is ${probe.kind}", probe = probe)
        if (probe.kind != "audio" && (probe.width < 240 || probe.height < 240) && req.kind != "overlay")
            return AssetVerdict(false, "Rejected: resolution too low (${probe.width}x${probe.height})", probe = probe)
        if (probe.kind == "video" && probe.durationMs < 800) return AssetVerdict(false, "Rejected: clip shorter than 0.8 s", probe = probe)
        if (visual != null) {
            if (visual.unsafe) return AssetVerdict(false, "Rejected: flagged unsuitable content", true, visual.score, probe)
            if (!visual.match || visual.score < 6) return AssetVerdict(false, "Rejected: does not match '${req.description.take(60)}' (score ${visual.score}/10${visual.issues.joinToString(prefix = "; ")})", true, visual.score, probe)
            return AssetVerdict(true, "Accepted: visually verified (score ${visual.score}/10)", true, visual.score, probe)
        }
        return AssetVerdict(true, "Accepted on technical checks only — visual relevance NOT verified (no vision-capable provider)", false, null, probe)
    }
}

data class VisualCheck(
    val match: Boolean, val score: Int, val issues: List<String> = emptyList(), val unsafe: Boolean = false,
    /** Keyword suggestion from the verifier, used by the hunter's retry loop. */
    val betterKeywords: List<String> = emptyList()
) {
    companion object {
        fun parse(raw: String): VisualCheck? = runCatching {
            val o = JSONObject(SceneJson.extractJson(raw))
            val issues = o.optJSONArray("issues")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val kw = o.optJSONArray("betterKeywords")?.let { a ->
                (0 until a.length()).map { a.optString(it).trim().lowercase() }.filter { it.length > 1 }.take(3)
            } ?: emptyList()
            VisualCheck(o.optBoolean("match", false), o.optInt("score", 0).coerceIn(0, 10), issues,
                unsafe = issues.any { it.contains("unsafe", true) }, betterKeywords = kw)
        }.getOrNull()
    }
}

/** Everything the sequential hunter needs after looking at one downloaded file. */
data class HuntVerdict(
    val verdict: AssetVerdict,
    val transparent: Boolean = false,
    val ext: String = "",
    val betterKeywords: List<String> = emptyList(),
    val visual: VisualCheck? = null
)

object AssetVerifier {
    /** Full verification of a downloaded file. [visionAvailable] must come from the provider registry. */
    suspend fun verify(context: Context, file: File, req: AssetRequest, targetAspect: String?, sceneObjective: String, visionAvailable: Boolean): AssetVerdict {
        val probe = MediaInspector.probe(context, file)
        var visual: VisualCheck? = null
        if (probe.valid && probe.kind != "audio" && visionAvailable) {
            MediaInspector.previewJpegBase64(file, probe)?.let { b64 ->
                val prof = AiProfile.fromSettings(context)
                val r = AiClient(prof).callWithImages(LlmRole.REVIEWER, DirectorPrompts.assetMatch(req.description, sceneObjective), b64)
                visual = r.getOrNull()?.let { VisualCheck.parse(it) }
            }
        }
        return AssetDecision.decide(probe, req, targetAspect, visual)
    }

    /**
     * Verification for the SEQUENTIAL hunter: it looks at the file in the context of the exact moment it would be
     * cut into, and it returns what the editor needs to know to place it (real extension, transparency) plus better
     * keywords for the next try when the file is wrong.
     */
    suspend fun verifyForHunt(
        context: Context, file: File, what: String, kind: String, situation: String,
        targetAspect: String?, visionAvailable: Boolean, alreadyTried: List<String> = emptyList()
    ): HuntVerdict {
        val probe = MediaInspector.probe(context, file)
        val ext = file.extension.lowercase().ifBlank { kind }
        val transparent = probe.valid && MediaInspector.hasAlpha(file, probe)
        var visual: VisualCheck? = null
        if (probe.valid && probe.kind != "audio" && visionAvailable) {
            MediaInspector.previewJpegBase64(file, probe)?.let { b64 ->
                val prof = AiProfile.fromSettings(context)
                val r = AiClient(prof).callWithImages(
                    LlmRole.REVIEWER, DirectorPrompts.assetMatchAt(what, situation, kind, alreadyTried), b64
                )
                visual = r.getOrNull()?.let { VisualCheck.parse(it) }
            }
        }
        val req = AssetRequest("hunt", kind, what)
        val verdict = AssetDecision.decide(probe, req, targetAspect, visual)
        return HuntVerdict(verdict, transparent, ext, visual?.betterKeywords ?: emptyList(), visual)
    }
}
