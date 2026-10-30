package com.example.studio.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Scene-entry transitions. The incoming scene stays fully opaque and moves/zooms/clips into place over the previous
 * one, so there is never a half-transparent "ghost" of two scenes. [p] is raw 0..1 progress of the transition.
 */
object TransitionFx {
    val GEOMETRIC = setOf("slideLeft", "slideRight", "slideUp", "push", "whip", "glitch", "flash", "iris", "spin", "zoomBlur", "zoom", "wipe", "pageflip", "cubeSpin", "depthWipe", "glitchDissolve", "page-flip", "cube-spin", "depth-wipe", "glitch-dissolve")

    private fun easeOut(p: Float) = 1f - Math.pow((1f - p).toDouble(), 3.0).toFloat()
    private fun expoOut(p: Float) = if (p >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * p).toFloat()
    private fun hash(i: Int, k: Int): Float { var v = i * 374761393 + k * 668265263; v = (v xor (v ushr 13)) * 1274126177; return ((v xor (v ushr 16)) and 0xFFFF) / 65535f }

    fun isGeometric(type: String?) = type != null && type in GEOMETRIC

    /** Applies the entry transform/clip to [canvas]. Call inside save()/restore(). Returns the number of extra ghost copies to draw (zoomBlur). */
    fun begin(canvas: Canvas, w: Float, h: Float, type: String, p: Float, seed: Int = 0x51A7) {
        val e = easeOut(p.coerceIn(0f, 1f))
        when (type) {
            "pageflip", "page-flip", "cubeSpin", "cube-spin" -> {
                if (p <= 0.0001f) canvas.clipRect(0f, 0f, 0f, 0f)
                else {
                    val unit = if (type == "pageflip" || type == "page-flip") TransitionGeometry.pageFlip(p) else TransitionGeometry.cubeSpin(p)
                    val dst = FloatArray(8) { i -> unit[i] * if (i % 2 == 0) w else h }
                    val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
                    val matrix = android.graphics.Matrix()
                    if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) canvas.concat(matrix) else canvas.clipRect(0f, 0f, 0f, 0f)
                }
            }
            "depthWipe", "depth-wipe" -> {
                val points = TransitionGeometry.depthWipe(p)
                val path = Path().apply {
                    moveTo(points[0] * w, points[1] * h)
                    for (i in 1..3) lineTo(points[i * 2] * w, points[i * 2 + 1] * h)
                    close()
                }
                canvas.clipPath(path)
                val scale = .88f + .12f * e
                canvas.scale(scale, scale, w * .5f, h * .5f)
            }
            "glitchDissolve", "glitch-dissolve" -> {
                val strips = 28
                val path = Path()
                for (i in 0 until strips) {
                    val progress = TransitionGeometry.glitchStrip(p, seed, i)
                    val y0 = h * i / strips; val y1 = h * (i + 1) / strips + 1f
                    if (progress > 0f) {
                        if (i % 2 == 0) path.addRect(0f, y0, w * progress, y1, Path.Direction.CW)
                        else path.addRect(w * (1f - progress), y0, w, y1, Path.Direction.CW)
                    }
                }
                canvas.clipPath(path)
            }
            "slideLeft" -> canvas.translate((1f - e) * w, 0f)
            "slideRight" -> canvas.translate(-(1f - e) * w, 0f)
            "slideUp" -> canvas.translate(0f, (1f - e) * h)
            "push" -> { val x = expoOut(p); canvas.translate((1f - x) * w, 0f); val s = 1.05f - 0.05f * x; canvas.scale(s, s, w / 2f, h / 2f) }
            "whip" -> canvas.translate((1f - expoOut(p)) * w * 1.1f, 0f)
            "zoom" -> { val s = 1.3f - 0.3f * e; canvas.scale(s, s, w / 2f, h / 2f) }
            "zoomBlur" -> { val s = 1.45f - 0.45f * expoOut(p); canvas.scale(s, s, w / 2f, h / 2f) }
            "spin" -> { val x = expoOut(p); canvas.rotate(-22f * (1f - x), w / 2f, h / 2f); val s = 0.78f + 0.22f * x; canvas.scale(s * 1.25f - 0.25f * x, s * 1.25f - 0.25f * x, w / 2f, h / 2f) }
            "iris" -> {
                val r = e * hypot(w, h) / 2f
                canvas.clipPath(Path().apply { addCircle(w / 2f, h / 2f, max(1f, r), Path.Direction.CW) })
            }
            "wipe" -> canvas.clipRect(0f, 0f, w * e, h)
            "glitch" -> { val frame = (p * 30).toInt(); canvas.translate((hash(frame, 1) - 0.5f) * w * 0.06f * (1f - p), 0f) }
            else -> {}
        }
    }

    /** Light overlays drawn on top of the whole frame during the transition (flash, speed streaks, glitch slices). */
    fun overlay(canvas: Canvas, w: Float, h: Float, type: String, p: Float) {
        val q = p.coerceIn(0f, 1f); val left = 1f - q
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (type) {
            "flash" -> { paint.color = 0xFFFFFFFF.toInt(); paint.alpha = (left * left * 235).toInt(); canvas.drawRect(0f, 0f, w, h, paint) }
            "zoom" -> { paint.color = 0xFFFFFFFF.toInt(); paint.alpha = (left * 60).toInt(); canvas.drawRect(0f, 0f, w, h, paint) }
            "whip", "zoomBlur" -> {
                paint.color = 0xFFFFFFFF.toInt(); paint.alpha = (left * 70).toInt(); paint.strokeWidth = max(2f, h * 0.004f)
                val n = 14
                for (i in 0 until n) {
                    val y = hash(i, 7) * h; val len = w * (0.3f + 0.5f * hash(i, 8)) * left
                    if (type == "whip") canvas.drawLine(w - len, y, w, y, paint)
                    else { val a = hash(i, 9) * 6.2831f; val cx = w / 2f; val cy = h / 2f
                        canvas.drawLine(cx + kotlin.math.cos(a) * w * 0.25f, cy + kotlin.math.sin(a) * w * 0.25f, cx + kotlin.math.cos(a) * w * (0.25f + 0.35f * left), cy + kotlin.math.sin(a) * w * (0.25f + 0.35f * left), paint) }
                }
            }
            "glitch" -> {
                val frame = (q * 30).toInt()
                for (i in 0 until 7) {
                    paint.color = if (i % 2 == 0) 0xFF00E5FF.toInt() else 0xFFFF2D95.toInt(); paint.alpha = (left * 120).toInt()
                    val y = hash(frame, i + 20) * h; val sh = h * (0.008f + 0.03f * hash(frame, i + 30))
                    val off = (hash(frame, i + 40) - 0.5f) * w * 0.15f
                    canvas.drawRect(max(0f, off), y, min(w, w + off), y + sh, paint)
                }
            }
            else -> {}
        }
    }
}
