package com.example.power

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.ProviderSettingsManager
import com.example.flow.FlowImageProbe
import com.example.flow.FlowLibrary
import com.example.flow.FlowMediaRules
import com.example.flow.FlowPlan
import com.example.flow.FlowPlanStore
import com.example.flow.FlowRecord
import com.example.flow.FlowRoles
import com.example.llm.KeyStoreCrypto
import com.example.llm.LlmRequest
import com.example.llm.LlmRole
import com.example.llm.LlmGateway
import com.example.llm.ProviderProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Round 15 — SAIF POWER TOOLS backend.
 *
 * Everything here is deliberately dependency-free (prefs + files + one HTTP call) so it cannot break the
 * app the way a half-wired feature does. Each function does ONE thing and reports honestly, and every
 * result is shown to the owner in Power Tools.
 */
object SaifToolkit {

    // ------------------------------------------------------------------ counters / analytics

    object Counters {
        private const val PREFS = "saif_power_counters"
        private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun bump(c: Context, key: String, by: Int = 1) { p(c).edit().putInt(key, get(c, key) + by).apply() }
        fun get(c: Context, key: String): Int = p(c).getInt(key, 0)
        fun all(c: Context): Map<String, Int> {
            val raw = p(c).all
            val out = LinkedHashMap<String, Int>()
            raw.forEach { (k, v) -> (v as? Int)?.let { out[k] = it } }
            return out
        }
        fun reset(c: Context) { p(c).edit().clear().apply() }
    }

    // ------------------------------------------------------------------ prompt vault

    data class PromptEntry(val id: String, val title: String, val text: String, val createdAt: Long)

    object PromptVault {
        private const val PREFS = "saif_prompt_vault"
        private const val KEY = "entries_v1"
        private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun list(c: Context): List<PromptEntry> = runCatching {
            val raw = p(c).getString(KEY, null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PromptEntry(o.optString("id"), o.optString("title"), o.optString("text"), o.optLong("createdAt"))
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())

        private fun save(c: Context, items: List<PromptEntry>) {
            val arr = JSONArray()
            items.forEach { e ->
                arr.put(JSONObject().put("id", e.id).put("title", e.title).put("text", e.text).put("createdAt", e.createdAt))
            }
            p(c).edit().putString(KEY, arr.toString()).apply()
        }

        fun add(c: Context, title: String, text: String): PromptEntry? {
            val t = text.trim()
            if (t.length < 5) return null
            val entry = PromptEntry("p" + System.currentTimeMillis(), title.trim().ifBlank { t.take(32) }, t, System.currentTimeMillis())
            save(c, listOf(entry) + list(c).take(99))
            return entry
        }

        fun remove(c: Context, id: String) = save(c, list(c).filterNot { it.id == id })
    }

    // ------------------------------------------------------------------ storage

    data class Bucket(val name: String, val dir: File, val bytes: Long, val files: Int)

    object Storage {
        fun buckets(c: Context): List<Bucket> {
            val list = mutableListOf<Bucket>()
            fun add(name: String, dir: File) {
                if (!dir.exists()) { list.add(Bucket(name, dir, 0L, 0)); return }
                var total = 0L
                var count = 0
                dir.walkTopDown().forEach { f -> if (f.isFile) { total += f.length(); count++ } }
                list.add(Bucket(name, dir, total, count))
            }
            add("Flow downloads", File(c.filesDir, "flow_downloads"))
            add("Director assets", File(c.filesDir, "director_assets"))
            add("Exports", File(c.filesDir, "exports"))
            add("Logs", File(c.filesDir, "logs"))
            add("Cache", c.cacheDir)
            return list
        }

        /** Deletes everything inside a bucket. Returns freed bytes. */
        fun clear(c: Context, name: String): Long {
            val bucket = buckets(c).firstOrNull { it.name == name } ?: return 0L
            var freed = 0L
            bucket.dir.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val len = f.length()
                    if (runCatching { f.delete() }.getOrDefault(false)) freed += len
                }
            }
            return freed
        }

        fun freeBytes(c: Context): Long = runCatching {
            val st = android.os.StatFs(c.filesDir.absolutePath)
            st.availableBlocksLong * st.blockSizeLong
        }.getOrDefault(0L)

        fun fmt(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
            bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
            bytes >= 1024 -> String.format(Locale.US, "%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }

    // ------------------------------------------------------------------ exports

    object Exports {

        /** Saves text into Downloads/SAIF AI (Q+) or app external files; returns the Uri. */
        fun saveText(context: Context, fileName: String, mime: String, text: String): Uri? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SAIF AI")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
                    Counters.bump(context, "exports")
                    uri
                } catch (e: Exception) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    null
                }
            } else {
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports").apply { mkdirs() }
                val f = File(dir, fileName)
                f.writeText(text, Charsets.UTF_8)
                Uri.fromFile(f)
            }
        }.getOrNull()

        private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

        /** Splits narration into caption cues of ~[charsPerCue] characters at sentence boundaries. */
        fun cues(text: String, charsPerCue: Int = 72): List<String> {
            val clean = text.replace("\r", "").replace(Regex("[ \\t]+"), " ").trim()
            if (clean.isBlank()) return emptyList()
            val sentences = clean.split(Regex("(?<=[.!?\\u0964])\\s+")).filter { it.isNotBlank() }
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            for (s in sentences) {
                if (sb.isEmpty()) sb.append(s)
                else if (sb.length + 1 + s.length <= charsPerCue) sb.append(' ').append(s)
                else { out.add(sb.toString()); sb.setLength(0); sb.append(s) }
            }
            if (sb.isNotEmpty()) out.add(sb.toString())
            return out.flatMap { block ->
                if (block.length <= charsPerCue * 2) listOf(block)
                else block.chunked(charsPerCue).map { it.trim() }.filter { it.isNotEmpty() }
            }
        }

        private fun tsSrt(ms: Long): String {
            val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000
            val s = (ms % 60_000) / 1000; val f = ms % 1000
            return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, f)
        }

        private fun tsVtt(ms: Long): String = tsSrt(ms).replace(',', '.')

        /** SRT from narration, ~2.6 s per cue (a comfortable reading speed at 9:16). */
        fun srt(text: String, cueMs: Long = 2600L): String {
            val list = cues(text)
            val sb = StringBuilder()
            list.forEachIndexed { i, cue ->
                sb.append(i + 1).append('\n')
                sb.append(tsSrt(i * cueMs)).append(" --> ").append(tsSrt((i + 1) * cueMs)).append('\n')
                sb.append(cue).append("\n\n")
            }
            return sb.toString()
        }

        fun vtt(text: String, cueMs: Long = 2600L): String {
            val list = cues(text)
            val sb = StringBuilder("WEBVTT\n\n")
            list.forEachIndexed { i, cue ->
                sb.append(tsVtt(i * cueMs)).append(" --> ").append(tsVtt((i + 1) * cueMs)).append('\n')
                sb.append(cue).append("\n\n")
            }
            return sb.toString()
        }

        /** One row per planned Flow picture: index, scene, role, status, measurement verdict. */
        fun shotListCsv(plan: FlowPlan): String {
            val sb = StringBuilder("index,scene,role,status,duration_ms,image,verdict,notes,prompt\n")
            plan.slots.sortedBy { it.index }.forEach { s ->
                fun q(v: String) = "\"" + v.replace("\"", "'").replace("\n", " ").take(300) + "\""
                sb.append(s.index + 1).append(',')
                sb.append(q(s.sceneId)).append(',')
                sb.append(q(s.role)).append(',')
                sb.append(q(s.status.name)).append(',')
                sb.append(s.sceneMs).append(',')
                sb.append(q(File(s.imagePath).name)).append(',')
                sb.append(q(s.review?.verdict ?: "")).append(',')
                sb.append(q(s.error)).append(',')
                sb.append(q(s.prompt)).append('\n')
            }
            return sb.toString()
        }

        fun srtName(prefix: String = "captions"): String = "SAIF_${prefix}_${stamp()}.srt"
        fun vttName(prefix: String = "captions"): String = "SAIF_${prefix}_${stamp()}.vtt"
        fun csvName(prefix: String = "shotlist"): String = "SAIF_${prefix}_${stamp()}.csv"
    }

    // ------------------------------------------------------------------ model tools

    object Models {

        data class Ping(val ok: Boolean, val ms: Long, val reply: String, val provider: String, val model: String)

        /** One tiny real call: proves the key + model + network actually work, with a latency number. */
        suspend fun ping(context: Context): Ping = withContext(Dispatchers.IO) {
            ProviderSettingsManager.init(context)
            val state = ProviderSettingsManager.loadState()
            val provider = state.providerName.ifBlank { "Gemini" }
            val key = state.apiKeys.firstOrNull { it.isNotBlank() }.orEmpty()
            val model = state.activeModel.ifBlank { "default" }
            if (key.isBlank()) return@withContext Ping(false, 0, "Settings me koi API key nahi hai", provider, model)
            val start = System.currentTimeMillis()
            val r = runCatching {
                LlmGateway.call(
                    role = LlmRole.SUMMARIZER, provider = provider, apiKey = key, model = model,
                    request = LlmRequest(prompt = "Reply with exactly: OK", systemPrompt = "You reply with one word.", maxTokens = 16)
                )
            }.getOrElse { Result.failure(it) }
            val ms = System.currentTimeMillis() - start
            val text = r.getOrNull()?.text?.trim().orEmpty()
            if (r.isSuccess) Ping(true, ms, text.ifBlank { "(empty reply, but the API answered)" }, provider, model)
            else Ping(false, ms, r.exceptionOrNull()?.message ?: "failed", provider, model)
        }

        /** Lists the models the provider really offers for this key. */
        suspend fun list(context: Context): Result<List<String>> = withContext(Dispatchers.IO) {
            ProviderSettingsManager.init(context)
            val state = ProviderSettingsManager.loadState()
            val profile = ProviderProfile.getById(state.providerName)
            val rawKey = state.apiKeys.firstOrNull { it.isNotBlank() } ?: return@withContext Result.failure(IllegalStateException("No API key in Settings"))
            val key = runCatching { KeyStoreCrypto.decrypt(rawKey) }.getOrDefault(rawKey).trim()
            if (key.isBlank()) return@withContext Result.failure(IllegalStateException("No API key in Settings"))
            runCatching {
                val isGemini = profile.id.equals("Gemini", ignoreCase = true)
                val base = profile.baseUrl.trimEnd('/')
                val url = if (isGemini) "$base/models?key=$key" else base + profile.modelsPath
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    if (!isGemini) setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (code !in 200..299) error("HTTP $code: " + body.take(160))
                val root = JSONObject(body)
                val out = ArrayList<String>()
                root.optJSONArray("data")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                }
                root.optJSONArray("models")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val name = o.optString("name").removePrefix("models/")
                        if (name.isNotBlank()) out.add(name)
                    }
                }
                out.distinct().sorted()
            }
        }
    }

    // ------------------------------------------------------------------ cover / thumbnail

    object Cover {
        /**
         * Builds a 1080x1920 cover from any picture: cover-crop, gradient scrim at the bottom, wrapped
         * title + a small SAIF AI line. Saved into the Flow library so the director can use it.
         */
        fun make(context: Context, source: File, title: String, subtitle: String = "SAIF AI"): File? = runCatching {
            val src = BitmapFactory.decodeFile(source.absolutePath) ?: return null
            val w = 1080
            val h = 1920
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // cover crop
            val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
            val dw = (src.width * scale).toInt()
            val dh = (src.height * scale).toInt()
            val dst = Rect((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh)
            canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            // bottom scrim
            val scrim = Paint().apply {
                shader = LinearGradient(0f, h * 0.35f, 0f, h.toFloat(),
                    intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Color.argb(235, 0, 0, 0)), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, h * 0.35f, w.toFloat(), h.toFloat(), scrim)
            // title
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 96f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val lines = wrap(title.ifBlank { "SAIF AI" }, titlePaint, (w - 160).toFloat())
            var y = h - 260f - (lines.size - 1) * 112f
            lines.forEach { line ->
                canvas.drawText(line, 80f, y, titlePaint)
                y += 112f
            }
            // brand line
            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E5FF"); textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
            }
            canvas.drawText(subtitle.uppercase(Locale.US), 80f, h - 140f, small)
            val dir = com.example.flow.FlowDownloader.dir(context)
            val file = File(dir, "cover_" + System.currentTimeMillis() + ".png")
            java.io.FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            runCatching { if (src !== out) src.recycle() }
            runCatching { out.recycle() }
            FlowLibrary.add(context, FlowRecord(
                id = "FLOW_" + FlowLibrary.hashOf(file).take(12),
                title = "cover_" + title.take(24),
                path = file.absolutePath, url = "", kind = "image", ext = "png", bytes = file.length(),
                width = w, height = h, hash = FlowLibrary.hashOf(file),
                sceneId = "", source = "saif-cover", role = FlowRoles.COVER
            ))
            file
        }.getOrNull()

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            val words = text.replace("\n", " ").split(' ').filter { it.isNotBlank() }
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            for (word in words) {
                val candidate = if (sb.isEmpty()) word else "$sb $word"
                if (paint.measureText(candidate) <= maxWidth) { sb.setLength(0); sb.append(candidate) }
                else {
                    if (sb.isNotEmpty()) out.add(sb.toString())
                    sb.setLength(0); sb.append(word)
                    if (out.size == 3) break
                }
            }
            if (sb.isNotEmpty() && out.size < 4) out.add(sb.toString())
            return out.ifEmpty { listOf(text.take(28)) }
        }
    }

    // ------------------------------------------------------------------ batch image fixes

    data class BatchReport(val total: Int, val done: Int, val failed: Int, val lines: List<String>)

    object Batch {
        /**
         * Re-checks every picture in the Flow library with the real measurement probe and, when asked,
         * writes a 9:16 center-cropped JPEG copy (useful for external editors / WhatsApp previews).
         */
        suspend fun verifyAndCrop(context: Context, crop: Boolean, onStep: (Int, Int) -> Unit = { _, _ -> }): BatchReport =
            withContext(Dispatchers.IO) {
                val items = FlowLibrary.images(context).filter { File(it.path).exists() }
                var done = 0
                var failed = 0
                val lines = mutableListOf<String>()
                items.forEachIndexed { i, rec ->
                    onStep(i + 1, items.size)
                    val f = File(rec.path)
                    val review = FlowImageProbe.analyse(f, "9:16", rec.role)
                    if (review == null) {
                        failed++
                        lines.add("${f.name}: decode fail (corrupt?)")
                        return@forEachIndexed
                    }
                    var extra = ""
                    if (crop && review.cropLoss > 0.05f) {
                        val made = cropTo916(context, f, rec)
                        if (made != null) { extra = " · 9:16 copy saved"; done++ } else { failed++; extra = " · crop fail" }
                    } else done++
                    lines.add(f.name + " → " + review.verdict + extra)
                }
                BatchReport(items.size, done, failed, lines.take(60))
            }

        private fun cropTo916(context: Context, source: File, rec: FlowRecord): File? = runCatching {
            val bmp = BitmapFactory.decodeFile(source.absolutePath) ?: return null
            val targetRatio = 9f / 16f
            val cur = bmp.width.toFloat() / bmp.height
            val w: Int; val h: Int; val x: Int; val y: Int
            if (cur > targetRatio) {
                h = bmp.height; w = (h * targetRatio).toInt(); x = (bmp.width - w) / 2; y = 0
            } else {
                w = bmp.width; h = (w / targetRatio).toInt(); x = 0; y = (bmp.height - h) / 2
            }
            val cropped = Bitmap.createBitmap(bmp, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1))
            val dir = com.example.flow.FlowDownloader.dir(context)
            val out = File(dir, "crop916_" + source.nameWithoutExtension + "_" + System.currentTimeMillis() + ".jpg")
            val cw = cropped.width
            val ch = cropped.height
            java.io.FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            runCatching { if (cropped !== bmp) cropped.recycle(); bmp.recycle() }
            FlowLibrary.add(context, rec.copy(
                id = "FLOW_" + FlowLibrary.hashOf(out).take(12),
                title = rec.title + "_916", path = out.absolutePath, ext = "jpg",
                bytes = out.length(), width = cw, height = ch,
                hash = FlowLibrary.hashOf(out), source = "saif-crop"
            ))
            out
        }.getOrNull()
    }

    // ------------------------------------------------------------------ flow resume

    object Resume {
        fun latestPlan(context: Context): FlowPlan? = runCatching { FlowPlanStore.latest(context) }.getOrNull()

        fun planLine(plan: FlowPlan?): String {
            if (plan == null) return "Koi saved Flow plan nahi hai."
            val ready = plan.finishedCount
            val total = plan.slots.size
            val when0 = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(plan.createdAt))
            return "$ready/$total pictures ready · ${plan.topic.take(40).ifBlank { "untitled" }} · $when0"
        }
    }

    // ------------------------------------------------------------------ misc

    fun isGranted(context: Context, permission: String): Boolean =
        runCatching { context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "saif" }.take(60)

    /** Rule used by the batch tools: does this file look like a real generated picture? */
    fun looksGenerated(file: File): Boolean =
        file.exists() && file.length() >= FlowMediaRules.MIN_BYTES
}
