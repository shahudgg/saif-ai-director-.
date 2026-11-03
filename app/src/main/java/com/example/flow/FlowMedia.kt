package com.example.flow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Round 14 — what may become a video asset.
 *
 * The previous build imported EVERY <img> on the page, which included the signed-in Google account
 * avatar (lh3.googleusercontent.com/a/...). These rules reject avatars, logos, sprites, icons,
 * favicons, header/nav chrome, tracking pixels, SVGs, data-URIs and anything smaller than a real
 * video frame. Kotlin re-checks every candidate after download (real pixel size + hash), so a JS
 * mis-report can never sneak the profile photo into a video.
 */
object FlowMediaRules {
    const val MIN_SIDE = 320
    const val MIN_BYTES = 12_000L
    const val MAX_BYTES = 120L * 1024 * 1024

    /** True when the URL itself is account/profile/UI chrome — never a generated picture. */
    fun isUiChrome(url: String): Boolean {
        if (url.isBlank()) return true
        val u = url.lowercase()
        if (u.startsWith("data:")) return true
        if (u.startsWith("blob:")) return false
        if (!u.startsWith("http")) return true
        // Google account / profile photos: lh3.googleusercontent.com/a/... or /a-/...
        if (u.contains("googleusercontent.com/a/") || u.contains("googleusercontent.com/a-/")) return true
        if (u.contains("googleusercontent.com/ogw")) return true
        if (u.contains("/accounts/") || u.contains("serviceLogin".lowercase())) return true
        // Fixed-size avatar requests (?sz=32, =s64-c, =w48-h48)
        if (Regex("([?&=]s|=w|=h)\\d{1,3}(-c)?(&|$)").containsMatchIn(u)) return true
        if (Regex("googleusercontent\\.com/[^/?]+=s\\d{1,3}").containsMatchIn(u)) return true
        // UI art
        val tail = u.substringAfterLast('/').substringBefore('?')
        if (tail.endsWith(".svg")) return true
        if (listOf("favicon", "sprite", "logo", "avatar", "profile", "icon", "emoji", "badge", "placeholder").any { tail.contains(it) }) return true
        if (u.contains("gstatic.com/images") || u.contains("gstatic.com/ui") || u.contains("www.gstatic.com")) return true
        return false
    }

    /** Element-level rules mirrored from JS (Kotlin side gets width/height/alt/context flags). */
    fun isGeneratedCandidate(url: String, width: Int, height: Int, altOrLabel: String = "", insideChrome: Boolean = false): Boolean {
        if (isUiChrome(url)) return false
        if (insideChrome) return false
        val label = altOrLabel.lowercase()
        if (listOf("avatar", "profile", "account", "logo", "icon", "menu").any { label.contains(it) }) return false
        if (width < MIN_SIDE || height < MIN_SIDE) return false
        return true
    }

    fun kindFromMime(mime: String, url: String): String {
        val m = mime.lowercase()
        if (m.startsWith("video/")) return "video"
        if (m.startsWith("audio/")) return "audio"
        val u = url.lowercase()
        if (u.endsWith(".mp4") || u.contains(".mp4?") || u.endsWith(".webm") || u.endsWith(".mov")) return "video"
        return "image"
    }

    fun extensionFor(bytes: ByteArray, url: String): String {
        if (bytes.size >= 12) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "jpg"
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "png"
            if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) return "gif"
            if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
                bytes.size > 11 && bytes[8].toInt() == 'W'.code && bytes[9].toInt() == 'E'.code) return "webp"
            if (bytes.size > 11 && bytes[4].toInt() == 'f'.code && bytes[5].toInt() == 't'.code &&
                bytes[6].toInt() == 'y'.code && bytes[7].toInt() == 'p'.code) return "mp4"
        }
        val ext = url.substringAfterLast('/', "").substringAfterLast('.', "").substringBefore('?').lowercase()
        return if (ext in setOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "webm", "mov")) {
            if (ext == "jpeg") "jpg" else ext.take(4)
        } else "png"
    }
}

/**
 * Downloads Flow media WITH THE WEBVIEW SESSION COOKIES. This is the fix for "download nahi hota":
 * Flow's result URLs need the signed-in session; the old code used a bare OkHttp call with no cookie
 * and no Referer, so every download failed and the library stayed empty.
 */
object FlowDownloader {
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val pending = ConcurrentHashMap<String, ByteArrayOutputStreamHolder>()

    private class ByteArrayOutputStreamHolder { val out = java.io.ByteArrayOutputStream() }

    fun dir(ctx: Context): File = File(ctx.filesDir, "flow_downloads").apply { mkdirs() }

    fun startChunks(id: String) { pending[id] = ByteArrayOutputStreamHolder() }

    /** Appends one base64 chunk sent from JS (blob:/canvas results can only be read inside the page). */
    fun appendChunk(id: String, base64: String): Int {
        val holder = pending.getOrPut(id) { ByteArrayOutputStreamHolder() }
        return runCatching {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            synchronized(holder) { holder.out.write(bytes) }
            holder.out.size()
        }.getOrElse { -1 }
    }

    fun finishChunks(id: String): ByteArray? {
        val holder = pending.remove(id) ?: return null
        return synchronized(holder) { holder.out.toByteArray() }
    }

    fun cancelChunks(id: String) { pending.remove(id) }

    /** Saves raw bytes (from JS base64 or a direct download) as a real library file. */
    fun saveBytes(
        ctx: Context, bytes: ByteArray, promptIndex: Int, sceneId: String, role: String,
        title: String, url: String, kindHint: String = "image", strict: Boolean = true
    ): FlowRecord? {
        val minBytes = if (strict) FlowMediaRules.MIN_BYTES else 900L
        if (bytes.size < minBytes || bytes.size > FlowMediaRules.MAX_BYTES) return null
        val kind = if (kindHint == "video") "video" else "image"
        val ext = FlowMediaRules.extensionFor(bytes, url)
        val hash = FlowLibrary.hashOf(bytes)
        if (hash.isNotBlank()) FlowLibrary.byHash(ctx, hash)?.let { existing ->
            // Same picture already in the library: reuse it (and re-point it at this prompt).
            return FlowLibrary.add(ctx, existing.copy(promptIndex = promptIndex, sceneId = sceneId, role = role))
        }
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 _-]"), "_").take(48).ifBlank { "flow" }
        val f = File(dir(ctx), "${System.currentTimeMillis()}_${safeTitle}.$ext")
        return runCatching {
            f.writeBytes(bytes)
            var w = 0; var h = 0
            if (kind == "image") {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                w = opts.outWidth; h = opts.outHeight
                if (strict && (w < FlowMediaRules.MIN_SIDE || h < FlowMediaRules.MIN_SIDE)) {
                    f.delete()
                    return null
                }
            }
            runCatching { com.example.power.SaifToolkit.Counters.bump(ctx, "flow_images") }
            FlowLibrary.add(ctx, FlowRecord(
                id = "FLOW_" + hash.take(12).ifBlank { System.currentTimeMillis().toString() },
                title = safeTitle.ifBlank { "Flow image ${promptIndex + 1}" },
                path = f.absolutePath, url = url, kind = kind, ext = ext, bytes = bytes.size.toLong(),
                width = w, height = h, hash = hash, promptIndex = promptIndex, sceneId = sceneId,
                source = "flow-ai", role = role
            ))
        }.getOrNull()
    }

    /**
     * Downloads one URL using the real WebView cookies. Returns the stored record, or null with the
     * reason in [lastError] so the UI can be honest instead of silently losing an image.
     */
    @Volatile var lastError: String = ""
        private set

    /** Lets the autopilot explain a failure it detected itself (e.g. an unreadable blob). */
    fun noteError(msg: String) { lastError = msg }

    fun download(
        ctx: Context, url: String, cookie: String, promptIndex: Int, sceneId: String,
        role: String, title: String, strict: Boolean = true
    ): FlowRecord? {
        lastError = ""
        if (!url.startsWith("http")) { lastError = "Not a downloadable URL"; return null }
        return try {
            val builder = Request.Builder().url(url)
                .addHeader("User-Agent", FlowUserAgent.mobile())
                .addHeader("Referer", "https://flow.google.com/")
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,video/*,*/*;q=0.8")
            if (cookie.isNotBlank()) builder.addHeader("Cookie", cookie)
            val resp = http.newCall(builder.get().build()).execute()
            if (!resp.isSuccessful) { lastError = "HTTP ${resp.code}"; return null }
            val body = resp.body ?: run { lastError = "Empty response"; return null }
            val len = body.contentLength()
            if (len > FlowMediaRules.MAX_BYTES) { lastError = "File too large"; return null }
            val bytes = body.bytes()
            val kind = FlowMediaRules.kindFromMime(resp.header("Content-Type") ?: "", url)
            saveBytes(ctx, bytes, promptIndex, sceneId, role, title, url, kind, strict)
                ?: run { lastError = "Rejected by the quality gate (too small or unreadable)"; null }
        } catch (e: Throwable) {
            lastError = e.message ?: "download failed"
            null
        }
    }
}

/** User agents. The `wv` token is what makes Google refuse a WebView sign-in, so it is removed. */
object FlowUserAgent {
    fun mobile(): String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    fun desktop(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"
}

/**
 * Local, deterministic picture verification — the step the owner asked for ("verify kare ki timeline pe
 * kaise lagega, crop hoga ki nahi, glow lagega ki nahi, background remove hoga ki nahi").
 *
 * Everything is measured from the decoded bitmap, not guessed:
 *  - cropLoss    : how much of the picture is thrown away by cover-cropping to the video aspect
 *  - focal       : where the detail is, so the crop can be nudged to keep the subject in frame
 *  - brightness  : mean luminance (dark frame → glow + brighter grade)
 *  - contrast    : luminance spread (flat frame → grade lift)
 *  - sharpness   : neighbour-difference energy (soft frame at 1080p → flagged)
 *  - cornerEqual : four corners share a colour → the background can be removed cleanly
 */
object FlowImageProbe {

    fun aspectValue(aspect: String): Float {
        val clean = aspect.replace(" ", "")
        val parts = clean.split(":", "x", "/")
        if (parts.size != 2) return 9f / 16f
        val a = parts[0].toFloatOrNull() ?: 9f
        val b = parts[1].toFloatOrNull() ?: 16f
        if (a <= 0f || b <= 0f) return 9f / 16f
        return a / b
    }

    fun analyse(file: File, aspect: String, role: String): FlowReview? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        val fullW = opts.outWidth
        val fullH = opts.outHeight
        var sample = 1
        while (fullW / sample > 640 || fullH / sample > 640) sample *= 2
        val bmp = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull() ?: return null
        return try {
            measure(bmp, fullW, fullH, aspect, role)
        } finally {
            runCatching { bmp.recycle() }
        }
    }

    /** Pure measurement core (separated so it can be unit-tested with a tiny bitmap). */
    fun measure(bmp: Bitmap, fullW: Int, fullH: Int, aspect: String, role: String): FlowReview {
        val w = bmp.width.coerceAtLeast(1)
        val h = bmp.height.coerceAtLeast(1)
        val step = max(1, min(w, h) / 96)
        var count = 0
        var sum = 0.0
        var sumSq = 0.0
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0
        var energy = 0.0
        var fx = 0.0; var fy = 0.0
        var prevLum = -1.0
        var x = 0
        while (x < w) {
            var y = 0
            while (y < h) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16 and 0xFF) / 255.0
                val g = (c shr 8 and 0xFF) / 255.0
                val b = (c and 0xFF) / 255.0
                val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                sum += lum; sumSq += lum * lum; count++
                rSum += r; gSum += g; bSum += b
                if (prevLum >= 0) {
                    val d = abs(lum - prevLum)
                    energy += d
                    fx += (x.toDouble() / w) * d
                    fy += (y.toDouble() / h) * d
                }
                prevLum = lum
                y += step
            }
            x += step
            prevLum = -1.0
        }
        if (count == 0) return FlowReview(width = fullW, height = fullH, verdict = "unreadable", ok = false)
        val mean = sum / count
        val variance = (sumSq / count) - mean * mean
        val contrast = Math.sqrt(max(variance, 0.0)).coerceIn(0.0, 0.5) / 0.5
        val sharpness = (energy / count * 4.0).coerceIn(0.0, 1.0)
        val totalEnergy = if (energy <= 0.0) 1.0 else energy
        val focalX = (fx / totalEnergy).coerceIn(0.15, 0.85).toFloat()
        val focalY = (fy / totalEnergy).coerceIn(0.15, 0.85).toFloat()
        val palette = String.format(java.util.Locale.US, "#%02X%02X%02X",
            ((rSum / count) * 255).toInt().coerceIn(0, 255),
            ((gSum / count) * 255).toInt().coerceIn(0, 255),
            ((bSum / count) * 255).toInt().coerceIn(0, 255))
        val cornerEqual = cornersEqual(bmp)
        val target = aspectValue(aspect)
        val imgAspect = fullW.toFloat() / fullH.toFloat().coerceAtLeast(1f)
        val cropLoss = if (imgAspect > target) {
            1f - (target / imgAspect)
        } else if (imgAspect < target) {
            1f - (imgAspect / target)
        } else 0f
        val brightness = mean.toFloat()
        val needsGlow = brightness < 0.24f || contrast < 0.12f
        val glowStrength: Float = if (!needsGlow) 0f else
            (0.30f - brightness).coerceIn(0.06f, 0.30f) + ((0.12 - contrast).coerceAtLeast(0.0) * 0.5).toFloat()
        val wantsCutout = role == FlowRoles.STICKER || role == FlowRoles.KEYFRAME
        val needsBgRemove = wantsCutout && cornerEqual
        val fit = when {
            role == FlowRoles.BACKGROUND || role == FlowRoles.COVER -> "cover"
            cornerEqual || cropLoss > 0.42f -> "contain"
            else -> "cover"
        }
        val animIn = when {
            brightness < 0.22f -> "zoomIn"
            sharpness > 0.55f -> "zoom"
            else -> "fadeIn"
        }
        val drift = if (cropLoss > 0.2f) 0.35f else 0.22f
        val enoughPixels = fullW >= 480 && fullH >= 480
        val tooSoft = sharpness < 0.06f
        val ok = enoughPixels && !tooSoft && cropLoss <= 0.62f
        val verdict = buildString {
            append(if (ok) "OK" else "REVIEW")
            append(" · ").append(fullW).append("x").append(fullH)
            append(" · crop ").append((cropLoss * 100).toInt()).append("%")
            append(" · bright ").append((brightness * 100).toInt())
            append(" · detail ").append((sharpness * 100).toInt())
            if (needsGlow) append(" · glow +").append((glowStrength * 100).toInt())
            if (cornerEqual) append(" · flat background")
            if (needsBgRemove) append(" · bg-remove useful")
            if (tooSoft) append(" · soft at 1080p")
        }
        return FlowReview(
            width = fullW, height = fullH, cropLoss = cropLoss,
            focalX = focalX, focalY = focalY, brightness = brightness, contrast = contrast.toFloat(),
            sharpness = sharpness.toFloat(), cornerUniform = cornerEqual, paletteHex = palette,
            fit = fit,
            cropX = ((focalX - 0.5f) * cropLoss * 2f).coerceIn(-0.22f, 0.22f),
            cropY = ((focalY - 0.5f) * cropLoss * 2f).coerceIn(-0.22f, 0.22f),
            needsGlow = needsGlow, glowStrength = glowStrength.coerceIn(0f, 0.45f),
            needsBgRemove = needsBgRemove, animIn = animIn, animOut = "fadeOut", drift = drift,
            verdict = verdict, ok = ok
        )
    }

    /** Four 6x6 corner patches share (almost) one colour → a clean, removable background. */
    private fun cornersEqual(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (w < 24 || h < 24) return false
        val patch = 6
        val means = ArrayList<DoubleArray>(4)
        val origins = arrayOf(intArrayOf(0, 0), intArrayOf(w - patch, 0), intArrayOf(0, h - patch), intArrayOf(w - patch, h - patch))
        for (o in origins) {
            var r = 0.0; var g = 0.0; var b = 0.0
            var n = 0
            var dx = 0
            while (dx < patch) {
                var dy = 0
                while (dy < patch) {
                    val c = bmp.getPixel((o[0] + dx).coerceIn(0, w - 1), (o[1] + dy).coerceIn(0, h - 1))
                    r += (c shr 16 and 0xFF) / 255.0
                    g += (c shr 8 and 0xFF) / 255.0
                    b += (c and 0xFF) / 255.0
                    n++
                    dy++
                }
                dx++
            }
            means.add(doubleArrayOf(r / n, g / n, b / n))
        }
        var maxDist = 0.0
        for (i in 0 until 4) {
            for (j in i + 1 until 4) {
                val a = means[i]; val c = means[j]
                maxDist = max(maxDist, abs(a[0] - c[0]) + abs(a[1] - c[1]) + abs(a[2] - c[2]))
            }
        }
        return maxDist < 0.12
    }
}
