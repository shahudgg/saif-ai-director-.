package com.example.studio.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.studio.model.TextItem
import kotlin.math.min

/** Caption look presets, selectable per project through [TextItem.stylePreset]. */
object CaptionStyles {
    const val HORMOZI = "Hormozi Neon"   // bold, heavy outline, active word highlighted, pop-in per chunk
    const val KARAOKE = "Karaoke"        // word-by-word highlight, no pop
    const val BOLD_POP = "Bold Pop"      // bold outline, pop-in, no highlight
    const val BOXED = "Boxed"            // rounded dark box behind the text
    const val CLEAN = "Clean"            // thin outline, no animation
    val ALL = listOf(HORMOZI, KARAOKE, BOLD_POP, BOXED, CLEAN)
}

/**
 * Draws captions for BOTH preview and export (same function, same output). Long text is chunked into 2-4 word
 * groups timed to the item's duration, wrapped to the safe area and auto-fitted; the canvas is never given a
 * single long line.
 */
object CaptionRenderer {

    private const val HIGHLIGHT = 0xFFFFE600.toInt()

    fun draw(canvas: Canvas, width: Int, height: Int, item: TextItem, localTimeUs: Long, unitScale: Float = 1f) {
        if (item.text.isBlank()) return
        val w = width.toFloat(); val h = height.toFloat()
        val chunks = CaptionChunker.chunk(item.text, 0L, item.durationUs.coerceAtLeast(1L))
        val chunk = CaptionChunker.active(chunks, localTimeUs) ?: return
        val preset = item.stylePreset
        val highlight = item.isKaraoke || preset == CaptionStyles.HORMOZI || preset == CaptionStyles.KARAOKE
        val pop = preset == CaptionStyles.HORMOZI || preset == CaptionStyles.BOLD_POP
        val boxed = preset == CaptionStyles.BOXED || (item.hasBackgroundBox && preset !in CaptionStyles.ALL)

        // Safe area: 8% side margins; text block sits in the lower third, clear of Shorts/Reels UI.
        val maxWidth = w * 0.84f
        val base = min(w, h) * 0.078f * (item.fontSizeSp / 24f).coerceIn(0.5f, 2.5f)
        val minSize = min(w, h) * 0.04f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.textColorHex.toInt(); textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, if (item.isBold) Typeface.BOLD else Typeface.NORMAL)
        }
        val measure: (String, Float) -> Float = { s, size -> fill.textSize = size; fill.measureText(s) }
        val fit = CaptionLayout.fit(chunk.text, maxWidth, maxLines = 2, startSize = base, minSize = minSize, measure = measure)
        fill.textSize = fit.textSize
        val lineH = fit.textSize * 1.18f
        val blockH = lineH * fit.lines.size

        val stroke = Paint(fill).apply {
            style = Paint.Style.STROKE; color = item.outlineColorHex.toInt()
            strokeWidth = fit.textSize * (if (preset == CaptionStyles.CLEAN) 0.07f else 0.16f); strokeJoin = Paint.Join.ROUND
        }

        val popScale = if (pop) {
            val t = ((localTimeUs - chunk.startUs) / 140_000f).coerceIn(0f, 1f)
            0.82f + 0.18f * (SceneEasing.backOut(t))
        } else 1f

        val cx = w / 2f + item.transform.x * unitScale
        val cy = h * 0.74f + item.transform.y * unitScale

        canvas.save()
        canvas.translate(cx, cy); canvas.scale(popScale, popScale); canvas.translate(-cx, -cy)

        if (boxed) {
            val widest = fit.lines.maxOf { fill.measureText(it) }
            val pad = fit.textSize * 0.35f
            val box = RectF(cx - widest / 2 - pad, cy - blockH / 2 - pad * 0.6f, cx + widest / 2 + pad, cy + blockH / 2 + pad * 0.6f)
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.boxColorHex.toInt().let { if ((it ushr 24) < 0x66) 0xCC000000.toInt() else it } }
            canvas.drawRoundRect(box, pad, pad, bp)
        }

        val activeWord = if (highlight) CaptionChunker.activeWordIndex(chunk, localTimeUs) else -1
        var wordCounter = 0
        val space = fill.measureText(" ")
        fit.lines.forEachIndexed { li, line ->
            val words = line.split(' ').filter { it.isNotEmpty() }
            val lineW = fill.measureText(line)
            var x = cx - lineW / 2f
            val y = cy - blockH / 2f + lineH * li + fit.textSize * 0.92f
            words.forEach { word ->
                if (item.outlineWidth > 0f && !boxed) canvas.drawText(word, x, y, stroke)
                fill.color = if (wordCounter == activeWord) HIGHLIGHT else item.textColorHex.toInt()
                canvas.drawText(word, x, y, fill)
                x += fill.measureText(word) + space
                wordCounter++
            }
        }
        canvas.restore()
    }
}

/** Easing helpers shared by captions and transitions (pure functions of time). */
object SceneEasing {
    fun backOut(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val x = t - 1f; return 1f + c3 * x * x * x + c1 * x * x }
    fun expoOut(t: Float): Float = if (t >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * t).toFloat()
    fun cubicInOut(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - Math.pow((-2.0 * t + 2.0), 3.0).toFloat() / 2f
    fun bounceOut(t: Float): Float {
        val n1 = 7.5625f; val d1 = 2.75f
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { val x = t - 1.5f / d1; n1 * x * x + 0.75f }
            t < 2.5f / d1 -> { val x = t - 2.25f / d1; n1 * x * x + 0.9375f }
            else -> { val x = t - 2.625f / d1; n1 * x * x + 0.984375f }
        }
    }
    /** Damped spring from 0 to 1 (slight overshoot, settles). */
    fun spring(t: Float): Float {
        if (t <= 0f) return 0f; if (t >= 1f) return 1f
        return (1f - Math.exp(-6.0 * t) * Math.cos(10.0 * t)).toFloat()
    }
}
