package com.example.studio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import com.example.studio.director.AssetPlacement
import com.example.studio.model.ItemEffects
import com.example.studio.model.ItemTransform
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * REAL effect engine for timeline layers.
 *
 * `ItemEffects` (neon glow, drop shadow, blur, brightness, contrast, saturation, hue, vignette, chroma key) and
 * `ItemTransform.blendMode` existed in the project model and in the Tune inspector, but nothing ever drew them:
 * the renderer only applied opacity and a translate/rotate/scale. This object implements them for pictures and
 * clips, and `StudioFrameRenderer` calls it, so the preview and the exported MP4 get exactly the same look.
 *
 * Everything is deterministic and works on API 26 (no RenderEffect, no hardware-only blend modes): glow, shadow
 * and blur are built by down-sampling and re-drawing with bilinear filtering, which is cheap and stable.
 */
object LayerEffects {

    /** Small cache of derived bitmaps (bloom copy, chroma-keyed copy) so a 30 fps export does not rebuild them. */
    private val derived = object : LruCache<String, Bitmap>(8) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) runCatching { oldValue.recycle() }
        }
    }

    private fun key(tag: String, b: Bitmap) = "$tag|${System.identityHashCode(b)}|${b.width}x${b.height}"

    // ---------------------------------------------------------------- geometry

    /**
     * Destination rectangle of a picture inside the frame, from its normalized placement box.
     * `cover` crops, `contain` letterboxes inside the box, `stretch` fills the box, `fill` covers the whole frame.
     */
    fun destRect(p: AssetPlacement, fw: Int, fh: Int, bw: Int, bh: Int): RectF {
        if (p.fit == "fill" || p.isBackdrop) return RectF(0f, 0f, fw.toFloat(), fh.toFloat())
        val boxW = max(1f, p.width * fw)
        val boxH = max(1f, p.height * fh)
        val cx = p.x * fw
        val cy = p.y * fh
        val arBox = boxW / boxH
        val arBmp = if (bh > 0) bw.toFloat() / bh else 1f
        val (dw, dh) = when (p.fit) {
            "stretch" -> boxW to boxH
            "contain" -> if (arBmp > arBox) boxW to boxW / arBmp else boxH * arBmp to boxH
            else -> if (arBmp > arBox) boxH * arBmp to boxH else boxW to boxW / arBmp   // cover inside the box
        }
        return RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f)
    }

    /**
     * Source rectangle of the bitmap that maps onto [dst] without distorting it (centre crop of the source).
     * For `stretch` the whole source is used.
     */
    fun srcRect(p: AssetPlacement, bw: Int, bh: Int, dst: RectF): Rect {
        if (p.fit == "stretch" || p.fit == "fill" || p.isBackdrop) return Rect(0, 0, bw, bh)
        val dstAr = if (dst.height() > 0f) dst.width() / dst.height() else 1f
        val srcAr = if (bh > 0) bw.toFloat() / bh else 1f
        return if (abs(srcAr - dstAr) < 0.01f) Rect(0, 0, bw, bh)
        else if (srcAr > dstAr) {
            val sw = (bh * dstAr).toInt().coerceIn(1, bw)
            Rect((bw - sw) / 2, 0, (bw - sw) / 2 + sw, bh)
        } else {
            val sh = (bw / dstAr).toInt().coerceIn(1, bh)
            Rect(0, (bh - sh) / 2, bw, (bh - sh) / 2 + sh)
        }
    }

    /** Centre-crop source rect that fills [w]x[h] (kept for the legacy backdrop path). */
    fun coverSource(bw: Int, bh: Int, w: Int, h: Int): Rect {
        val scale = max(w.toFloat() / bw, h.toFloat() / bh)
        val sw = w / scale; val sh = h / scale
        val l = ((bw - sw) / 2f).toInt().coerceAtLeast(0); val t = ((bh - sh) / 2f).toInt().coerceAtLeast(0)
        return Rect(l, t, (l + sw).toInt().coerceAtMost(bw), (t + sh).toInt().coerceAtMost(bh))
    }

    /** Fit-inside source/dest pair that keeps the aspect of the picture (legacy non-backdrop path). */
    fun fitDest(bw: Int, bh: Int, w: Int, h: Int): RectF {
        val s = min(w.toFloat() / bw, h.toFloat() / bh)
        val dw = bw * s; val dh = bh * s
        return RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
    }

    /**
     * Slow "Ken Burns" drift for a backdrop when the placement has no keyframes: a gentle zoom + pan driven only
     * by the clip's own progress, so it is frame-exact the same in the preview and in the export.
     */
    fun driftTransform(p: AssetPlacement, progress: Float, fw: Int, fh: Int): ItemTransform {
        if (p.drift <= 0.001f) return ItemTransform()
        val t = progress.coerceIn(0f, 1f)
        val zoom = 1f + 0.10f * p.drift * t
        val pan = 0.02f * p.drift * fw
        return ItemTransform(x = pan * (t - 0.5f) * 2f, y = pan * 0.4f * (t - 0.5f) * 2f, scale = zoom)
    }

    /** Extra pose of an entrance/exit animation: multiplied scale, offset as a fraction of the box, and spin.
     *  Opacity is NOT part of the pose — `RenderPlan` already fades every layer that has an `animIn`/`animOut`. */
    data class AnimPose(val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f, val rotation: Float = 0f) {
        val isIdentity: Boolean get() = scale == 1f && dx == 0f && dy == 0f && rotation == 0f
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f; val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

    /**
     * Pose of an entrance animation at [p01] (0 = first frame, 1 = settled). [RenderPlan] already fades opacity for
     * every `animIn`; this adds the geometric part (zoom, slide, spin, flip) that a plain fade cannot express.
     */
    fun entrance(name: String, p01: Float): AnimPose {
        val t = p01.coerceIn(0f, 1f)
        val e = easeOut(t)
        val eb = easeOutBack(t).coerceIn(0f, 1.4f)
        return when (name.lowercase()) {
            "none", "", "fadein", "fadeout" -> AnimPose()
            "zoomin" -> AnimPose(scale = 0.55f + 0.45f * e)
            "popin" -> AnimPose(scale = 0.2f + 0.8f * eb)
            "slideleft" -> AnimPose(dx = -0.45f * (1f - e))
            "slideright" -> AnimPose(dx = 0.45f * (1f - e))
            "slideup" -> AnimPose(dy = 0.40f * (1f - e))
            "slidedown" -> AnimPose(dy = -0.40f * (1f - e))
            "spinin" -> AnimPose(scale = 0.4f + 0.6f * e, rotation = -160f * (1f - e))
            "blurin" -> AnimPose(scale = 1.18f - 0.18f * e)
            "flipin" -> AnimPose(scale = 0.35f + 0.65f * e, rotation = 12f * (1f - e))
            else -> AnimPose()
        }
    }

    /** Pose of an exit animation at [p01] (0 = still fully visible, 1 = gone). */
    fun exit(name: String, p01: Float): AnimPose {
        val t = p01.coerceIn(0f, 1f)
        return when (name.lowercase()) {
            "none", "", "fadein", "fadeout" -> AnimPose()
            "zoomout" -> AnimPose(scale = 1f + 0.45f * t)
            "popout" -> AnimPose(scale = (1f - 0.8f * t).coerceAtLeast(0.02f))
            "spinout" -> AnimPose(scale = 1f - 0.5f * t, rotation = 160f * t)
            else -> AnimPose()
        }
    }

    // ---------------------------------------------------------------- colour

    /** Brightness / contrast / saturation / hue in one matrix, or null when nothing is graded. */
    fun colorFilter(e: ItemEffects): ColorMatrixColorFilter? {
        val b = e.brightness; val c = e.contrast; val s = e.saturation; val hDeg = e.hue
        val neutral = abs(b - 1f) < 0.005f && abs(c - 1f) < 0.005f && abs(s - 1f) < 0.005f && abs(hDeg) < 0.5f
        if (neutral) return null
        val m = ColorMatrix()
        // brightness (0..2, 1 = unchanged)
        m.postTranslate((b - 1f) * 90f, (b - 1f) * 90f, (b - 1f) * 90f, 0f)
        // contrast around the mid grey
        if (abs(c - 1f) >= 0.005f) {
            val t = (1f - c) * 127.5f
            val cm = ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(cm)
        }
        if (abs(s - 1f) >= 0.005f) { val sm = ColorMatrix(); sm.setSaturation(s); m.postConcat(sm) }
        if (abs(hDeg) >= 0.5f) { val hm = ColorMatrix(); hm.postRotate(hDeg); m.postConcat(hm) }
        return ColorMatrixColorFilter(m)
    }

    /** Blend mode name -> PorterDuff mode. Unknown / Normal returns null (plain SRC_OVER). */
    fun xfermode(name: String?): PorterDuffXfermode? = when (name?.lowercase()?.replace(" ", "")) {
        null, "", "normal", "src_over" -> null
        "screen" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        "multiply" -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        "overlay" -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        "add", "plus", "lighter" -> PorterDuffXfermode(PorterDuff.Mode.ADD)
        "lighten" -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        "darken" -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        else -> null
    }

    // ---------------------------------------------------------------- derived bitmaps

    /** Down-sampled copy used for bloom, shadow and blur. Deterministic and cheap. */
    private fun softCopy(b: Bitmap, tag: String, edge: Int, tint: Int?): Bitmap? {
        val ck = key(tag, b) + "|$edge|${tint ?: 0}"
        derived.get(ck)?.let { if (!it.isRecycled) return it }
        return try {
            val s = max(1, max(b.width, b.height) / max(8, edge))
            val w = max(1, b.width / s); val h = max(1, b.height / s)
            val small = Bitmap.createScaledBitmap(b, w, h, true)
            val out = if (tint == null) small else {
                val copy = small.copy(Bitmap.Config.ARGB_8888, true)
                if (small !== copy) runCatching { small.recycle() }
                Canvas(copy).apply {
                    drawBitmap(copy, 0f, 0f, Paint().apply { colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) })
                }
                copy
            }
            derived.put(ck, out)
            out
        } catch (_: Throwable) { null }
    }

    /** Removes a solid background colour (green screen / blue screen) from a sticker. */
    fun chromaKeyed(b: Bitmap, tag: String, hex: String, tolerance: Float): Bitmap {
        val ck = key("chroma", b) + "|$hex|$tolerance"
        derived.get(ck)?.let { if (!it.isRecycled) return it }
        val key = try { Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") } catch (_: Throwable) { Color.GREEN }
        val kr = Color.red(key); val kg = Color.green(key); val kb = Color.blue(key)
        val tol = (tolerance.coerceIn(0.02f, 0.8f) * 441f).toInt().coerceAtLeast(8)
        val out = try {
            val copy = b.copy(Bitmap.Config.ARGB_8888, true) ?: return b
            val w = copy.width; val h = copy.height
            val pixels = IntArray(w * h)
            copy.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val px = pixels[i]
                val d = abs(Color.red(px) - kr) + abs(Color.green(px) - kg) + abs(Color.blue(px) - kb)
                if (d < tol) pixels[i] = px and 0x00FFFFFF
                else if (d < tol * 2) {
                    val a = ((d - tol).toFloat() / tol).coerceIn(0f, 1f)
                    pixels[i] = (px and 0x00FFFFFF) or (((px ushr 24) * a).toInt().coerceIn(0, 255) shl 24)
                }
            }
            copy.setPixels(pixels, 0, w, 0, 0, w, h)
            derived.put(ck, copy)
            copy
        } catch (_: Throwable) { b }
        return out
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Draws one picture/clip layer with every effect the editor asked for, in this order:
     * shadow -> chroma key -> blur -> the picture itself (graded, blended, rounded) -> bloom -> vignette.
     */
    fun drawLayer(
        canvas: Canvas, bmp: Bitmap, src: Rect, dst: RectF,
        paint: Paint, effects: ItemEffects, placement: AssetPlacement?, tag: String,
        frameW: Int, frameH: Int
    ) {
        val alpha = paint.alpha
        val glow = placement?.glow ?: 0f
        val shadow = placement?.shadow ?: (if (effects.dropShadow > 0f) effects.dropShadow else 0f)
        val blur = placement?.blur ?: (effects.blur.coerceIn(0f, 1f))
        val vig = placement?.vignette ?: effects.vignette
        val corner = placement?.corner ?: 0f
        val chroma = placement?.chromaKey ?: effects.chromaKey
        val chromaColor = placement?.chromaColor ?: "#00FF00"
        val chromaTol = placement?.chromaTolerance ?: 0.22f

        val savedXfer = paint.xfermode
        paint.xfermode = xfermode(placement?.blend)

        // 1. drop shadow: a dark, blurred, slightly offset copy UNDER the picture.
        if (shadow > 0.01f) {
            softCopy(bmp, "shd$tag", 26, Color.BLACK)?.let { s ->
                val sp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                sp.alpha = (alpha * 0.55f * shadow).toInt().coerceIn(0, 255)
                val off = 6f + 22f * shadow
                canvas.drawBitmap(s, src, RectF(dst.left + off, dst.top + off * 1.3f, dst.right + off, dst.bottom + off * 1.3f), sp)
            }
        }

        // 2. chroma key (only worth it for stickers, never for a full-frame backdrop).
        var draw = bmp
        if (chroma && placement != null && !placement.isBackdrop) draw = chromaKeyed(bmp, tag, chromaColor, chromaTol)

        // 3. blur: replace the picture with a soft copy when the blur is strong.
        if (blur > 0.06f) softCopy(draw, "blr$tag", max(6, (60 - blur * 55).toInt()), null)?.let { draw = it }

        // 4. the picture itself, graded and clipped to its rounded box.
        val grade = colorFilter(effects)
        if (grade != null) paint.colorFilter = grade
        if (corner > 0.001f) {
            val r = min(dst.width(), dst.height()) * corner
            val path = android.graphics.Path().apply { addRoundRect(dst, r, r, android.graphics.Path.Direction.CW) }
            canvas.save(); canvas.clipPath(path)
            canvas.drawBitmap(draw, src, dst, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(draw, src, dst, paint)
        }
        paint.colorFilter = null

        // 5. neon bloom: the picture's own light, tinted, added on top.
        if (glow > 0.02f) {
            val tint = try { Color.parseColor(placement?.glowColor ?: "#00E5FF") } catch (_: Throwable) { 0xFF00E5FF.toInt() }
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            glowPaint.alpha = (alpha * 0.85f * glow).toInt().coerceIn(0, 255)
            val savedXf = glowPaint.xfermode
            glowPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            val grow = 8f + 46f * glow
            val gd = RectF(dst.left - grow, dst.top - grow, dst.right + grow, dst.bottom + grow)
            softCopy(draw, "glw$tag", max(10, (48 - glow * 30).toInt()), tint)?.let { s ->
                runCatching { canvas.drawBitmap(s, src, gd, glowPaint) }
            }
            glowPaint.xfermode = savedXf
        }

        paint.xfermode = savedXfer

        // 6. vignette over the layer's own box (full frame for a backdrop).
        if (vig > 0.02f) drawVignette(canvas, dst, vig, alpha)
    }

    /** Radial darkening inside [r]. Strength 0..1. */
    fun drawVignette(canvas: Canvas, r: RectF, strength: Float, alpha: Int = 255) {
        val cx = r.centerX(); val cy = r.centerY()
        val radius = max(1f, hypot(r.width() / 2f, r.height() / 2f))
        val s = strength.coerceIn(0f, 1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((90 * s).toInt().coerceIn(0, 255), 0, 0, 0), Color.argb((235 * s).toInt().coerceIn(0, 255) * alpha / 255, 0, 0, 0)),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(r, p)
    }

    /** Releases the derived-bitmap cache (called when a project is closed or a new export starts). */
    fun clearCache() { derived.evictAll() }
}
