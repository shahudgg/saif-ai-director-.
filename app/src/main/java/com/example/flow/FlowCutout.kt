package com.example.flow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Round 16 — REAL BACKGROUND REMOVAL (no model, no internet, no licence).
 *
 * The owner's complaint was simple: "image ka background remove nahi ho raha hai". Until now the app only
 * *detected* that a picture looked like a cut-out (`FlowReview.needsBgRemove`) and then handed it to a
 * chroma-key shader with a guessed colour — which does nothing when Flow paints a flat grey/white studio
 * background instead of pure green.
 *
 * This object actually cuts the subject out of the picture:
 *
 *  1. Read the border ring (what surrounds the subject) and find the DOMINANT colour there.
 *  2. Flood-fill from every border pixel through all pixels that are close to that colour
 *     (4-neighbour, tolerance auto-raised for green/blue screens). Everything reached is background.
 *  3. Feather the edge by one pixel so the cut-out does not look like scissors.
 *  4. Refuse to lie: if the flood removed less than 6 % (nothing flat there) or more than 94 % (it ate the
 *     subject), the picture is returned UNCHANGED and the caller keeps the normal photo pipeline.
 *
 * The result is a transparent PNG saved next to the original, so the timeline can place it as a real
 * overlay above the background layer instead of a rectangle with a fake key colour.
 */
object FlowCutout {

    /** Hard cap on the working resolution: cut-outs are overlays, they never need to exceed this. */
    private const val MAX_SIDE = 1600
    private const val MAX_BYTES = 40L * 1024 * 1024

    data class Result(
        val ok: Boolean,
        val path: String = "",
        /** Fraction of pixels that became transparent (0..1). */
        val removed: Float = 0f,
        /** Dominant border colour, hex — useful to keep as a chroma fallback. */
        val borderHex: String = "#000000",
        val width: Int = 0,
        val height: Int = 0,
        val note: String = ""
    )

    /**
     * Cuts [src] out and writes a transparent PNG to [dst]. Returns [Result.ok] = false (with a reason) when
     * the picture has no removable flat background — in that case nothing is written.
     */
    fun process(src: File, dst: File, tolerance: Int = -1): Result {
        if (!src.exists() || src.length() < 256L || src.length() > MAX_BYTES) {
            return Result(false, note = "file missing or too small")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(src.absolutePath, bounds) }
        if (bounds.outWidth <= 8 || bounds.outHeight <= 8) return Result(false, note = "image decode nahi hui")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = runCatching { BitmapFactory.decodeFile(src.absolutePath, decode) }.getOrNull()
            ?: return Result(false, note = "image decode nahi hui")
        val w = bmp.width
        val h = bmp.height
        if (w < 16 || h < 16) { runCatching { bmp.recycle() }; return Result(false, note = "image bahut choti hai") }

        val pixels = IntArray(w * h)
        runCatching { bmp.getPixels(pixels, 0, w, 0, 0, w, h) }
            .onFailure { runCatching { bmp.recycle() }; return Result(false, note = "pixels read nahi hue") }

        // ---- 1. dominant border colour ----
        val hist = HashMap<Int, Int>(64)
        fun add(px: Int) {
            val key = (Color.red(px) / 16 shl 8) or (Color.green(px) / 16 shl 4) or (Color.blue(px) / 16)
            hist[key] = (hist[key] ?: 0) + 1
        }
        for (x in 0 until w step 2) { add(pixels[x]); add(pixels[(h - 1) * w + x]) }
        for (y in 0 until h step 2) { add(pixels[y * w]); add(pixels[y * w + w - 1]) }
        val dominant = hist.maxByOrNull { it.value }?.key ?: 0
        val borderCount = hist.values.sum().coerceAtLeast(1)
        val share = (hist[dominant] ?: 0).toFloat() / borderCount.toFloat()

        val dr = ((dominant shr 8) and 0xF) * 16 + 8
        val dg = ((dominant shr 4) and 0xF) * 16 + 8
        val db = (dominant and 0xF) * 16 + 8
        val borderHex = String.format(java.util.Locale.US, "#%02X%02X%02X", dr, dg, db)

        // Green / blue screen? Then be far more aggressive — the renderer used to guess at this.
        val chroma = (dg > 120 && dg > dr + 38 && dg > db + 38) || (db > 120 && db > dr + 38 && db > dg + 38)
        val tol = if (tolerance in 8..160) tolerance else if (chroma) 96 else 58

        // ---- 2. flood fill from the border ----
        val bg = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun seed(i: Int) {
            if (bg[i]) return
            val px = pixels[i]
            if (dist(px, dr, dg, db) > tol) return
            bg[i] = true
            stack[sp] = i
            sp++
        }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        var visited = 0
        while (sp > 0) {
            sp--
            val i = stack[sp]
            visited++
            if (visited > w * h * 3) break
            val x = i % w
            val y = i / w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w)
            if (y < h - 1) seed(i + w)
        }
        var count = 0
        for (i in bg.indices) if (bg[i]) count++
        val removed = count.toFloat() / (w * h).toFloat()

        // ---- 3. honest verdicts ----
        if (removed < 0.06f) {
            runCatching { bmp.recycle() }
            return Result(false, removed = removed, borderHex = borderHex, width = w, height = h,
                note = "background flat nahi hai (" + pct(removed) + " hataya) — normal photo ki tarah rakha")
        }
        if (removed > 0.94f) {
            runCatching { bmp.recycle() }
            return Result(false, removed = removed, borderHex = borderHex, width = w, height = h,
                note = "poora frame ek hi colour hai — subject nahi mila")
        }
        if (share < 0.28f && !chroma) {
            runCatching { bmp.recycle() }
            return Result(false, removed = removed, borderHex = borderHex, width = w, height = h,
                note = "border ek colour ka nahi hai (" + pct(share) + " match) — cut-out skip")
        }

        // ---- 4. cut + feather ----
        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val px = pixels[i]
            out[i] = if (bg[i]) px and 0x00FFFFFF else px
        }
        // One-pixel feather: a foreground pixel touching the background gets a soft edge instead of a hard one.
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (bg[i]) continue
                if (bg[i - 1] || bg[i + 1] || bg[i - w] || bg[i + w]) {
                    val px = out[i]
                    val a = ((px ushr 24) * 0.62f).toInt().coerceIn(0, 255)
                    out[i] = (px and 0x00FFFFFF) or (a shl 24)
                }
            }
        }
        val cut = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: run { runCatching { bmp.recycle() }; return Result(false, note = "bitmap memory nahi mili") }
        runCatching { cut.setPixels(out, 0, w, 0, 0, w, h) }

        val saved = runCatching {
            dst.parentFile?.mkdirs()
            FileOutputStream(dst).use { fos -> cut.compress(Bitmap.CompressFormat.PNG, 100, fos) }
            dst.exists() && dst.length() > 256L
        }.getOrDefault(false)
        runCatching { cut.recycle() }
        runCatching { bmp.recycle() }
        if (!saved) return Result(false, removed = removed, borderHex = borderHex, note = "cut-out save nahi hui")

        return Result(true, path = dst.absolutePath, removed = removed, borderHex = borderHex,
            width = w, height = h, note = "background hataya (" + pct(removed) + " transparent)")
    }

    /** True when the picture looks like a studio cut-out (used to decide whether to even try). */
    fun likelyFlatBackground(file: File): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }
        if (opts.outWidth < 16 || opts.outHeight < 16) return false
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > 220) sample *= 2
        val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull() ?: return false
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) { runCatching { bmp.recycle() }; return false }
        val px = IntArray(w * h)
        runCatching { bmp.getPixels(px, 0, w, 0, 0, w, h) }
        runCatching { bmp.recycle() }
        val first = px[0]
        var same = 0
        var total = 0
        for (x in 0 until w) {
            val a = px[x]; val b = px[(h - 1) * w + x]
            if (dist(a, Color.red(first), Color.green(first), Color.blue(first)) < 70) same++
            if (dist(b, Color.red(first), Color.green(first), Color.blue(first)) < 70) same++
            total += 2
        }
        return total > 0 && same.toFloat() / total.toFloat() > 0.7f
    }

    private fun dist(px: Int, r: Int, g: Int, b: Int): Int =
        abs(Color.red(px) - r) + abs(Color.green(px) - g) + abs(Color.blue(px) - b)

    private fun pct(f: Float): String = ((f * 100f).toInt()).toString() + "%"
}
