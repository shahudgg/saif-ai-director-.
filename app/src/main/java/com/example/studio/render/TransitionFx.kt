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
    val GEOMETRIC = setOf("slideLeft", "slideRight", "slideUp", "push", "whip", "glitch", "flash", "iris", "spin", "zoomBlur", "zoom", "wipe")

    private fun easeOut(p: Float) = 1f - Math.pow((1f - p).toDouble(), 3.0).toFloat()
    private fun expoOut(p: Float) = if (p >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * p).toFloat()
    private fun hash(i: Int, k: Int): Float { var v = i * 374761393 + k * 668265263; v = (v xor (v ushr 13)) * 1274126177; return ((v xor (v ushr 16)) and 0xFFFF) / 65535f }

    fun isGeometric(type: String?) = type != null && type in GEOMETRIC

    /** Applies the entry transform/clip to [canvas]. Call inside save()/restore(). Returns the number of extra ghost copies to draw (zoomBlur). */
    fun begin(canvas: Canvas, w: Float, h: Float, type: String, p: Float) {
        val e = easeOut(p.coerceIn(0f, 1f))
        when (type) {
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
