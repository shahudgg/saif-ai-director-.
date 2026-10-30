package com.example.studio.director

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ADVANCED MOTION-GRAPHICS PRIMITIVES (Round 9).
 *
 * The owner's complaint was that every video looked like the same template: the model only had ~12 shapes to
 * choose from, so scenes converged on "box + bars + label". This file adds 20 more ways of SHOWING information —
 * radar sweeps, gauges with needles, data tables, tickers, heat maps, spirals, orbits, ribbons, spotlights,
 * halftone screens, before/after splits — plus real per-element look controls (neon glow, drop shadow, blend
 * mode, gradient angle, dash, skew, letter spacing, rounded radius, motion path).
 *
 * Every primitive is a deterministic function of time, drawn on an Android Canvas. No generated code is ever
 * executed; the model can only pick from this whitelist, and [SceneValidator] clamps every number it sends.
 * The same functions draw the live preview and the exported MP4.
 */
object SceneFx {

    /** Element types this file draws. Anything else is handled by [SceneRenderer] itself. */
    val TYPES = setOf(
        "wave", "radar", "steps", "table", "quote", "ticker", "arcdial", "spotlight", "halftone", "scanline",
        "confetti", "beam", "ribbon", "compare", "heat", "spiral", "orbit", "stack", "badge", "barcode"
    )

    fun handles(type: String): Boolean = type in TYPES

    /** Everything a primitive needs, already resolved (animated props applied) by the caller. */
    data class Geo(
        val ew: Float, val eh: Float, val alpha: Float, val progress: Float, val tMs: Long,
        val w: Float, val h: Float, val color: Int, val color2: Int, val minSide: Float
    )

    /** Deterministic pseudo-random in 0..1 from a seed and two indices (never uses Random, so frames never jitter). */
    fun hash(seed: Int, i: Int, k: Int): Float {
        var x = (seed * 374761393 + i * 668265263 + k * 2147483647L).toInt()
        x = (x xor (x shr 13)) * 1274126177
        x = x xor (x shr 16)
        return (x and 0x7FFFFF).toFloat() / 0x7FFFFF.toFloat()
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt().coerceIn(0, 255),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt().coerceIn(0, 255)
        )
    }

    /** Greedy word wrap, same behaviour as the scene renderer's own wrap. */
    fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        if (maxW <= 1f) return listOf(text)
        val words = text.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var line = words[0]
        for (i in 1 until words.size) {
            val cand = "$line ${words[i]}"
            if (p.measureText(cand) <= maxW) line = cand else { out += line; line = words[i] }
        }
        out += line
        return out
    }

    fun typefaceFor(weight: String): Typeface = when (weight.lowercase()) {
        "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "normal" -> Typeface.create("sans-serif", Typeface.NORMAL)
        "black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun xferFor(blend: String): PorterDuffXfermode? = when (blend.lowercase()) {
        "screen" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        "multiply" -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        "overlay" -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        "add" -> PorterDuffXfermode(PorterDuff.Mode.ADD)
        "lighten" -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        "darken" -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        else -> null
    }

    /**
     * Draws one advanced element WITH its look controls: drop shadow, neon bloom, blend mode. The shape itself is
     * drawn by [drawBody], which only needs a paint — so the same shape can be stamped several times for glow.
     */
    fun draw(canvas: Canvas, e: Element, g: Geo, base: Paint, strokeBase: Paint) {
        val main = Paint(base).apply { xfermode = xferFor(e.blend) }
        val stroke = Paint(strokeBase).apply { xfermode = xferFor(e.blend) }
        if (e.dash > 0.01f) {
            val d = max(2f, e.dash * g.minSide * 0.03f)
            stroke.pathEffect = DashPathEffect(floatArrayOf(d, d * 0.8f), 0f)
        }
        // 1. drop shadow: the same shape, offset, in black.
        if (e.shadow > 0.01f) {
            val off = 2f + 14f * e.shadow * (g.minSide / 900f)
            canvas.save(); canvas.translate(off, off * 1.25f)
            drawBody(canvas, e, g, Paint(main).apply { color = withAlpha(Color.BLACK, (g.alpha * 150 * e.shadow).toInt()); shader = null })
            canvas.restore()
        }
        // 2. neon bloom: three increasingly large, increasingly faint stamps of the same shape.
        if (e.glow > 0.01f) {
            for (k in 1..3) {
                val s = 1f + 0.035f * k * e.glow
                val a = (g.alpha * 255 * e.glow * (0.34f - 0.09f * k)).toInt().coerceIn(0, 255)
                canvas.save(); canvas.scale(s, s)
                drawBody(canvas, e, g, Paint(main).apply { alpha = a; shader = null })
                canvas.restore()
            }
        }
        // 3. the shape itself.
        drawBody(canvas, e, g, main, stroke)
    }

    /** Paint with the element's optional two-colour gradient applied. */
    private fun shaded(p: Paint, e: Element, g: Geo): Paint {
        if (e.color2.isBlank()) return p
        val rad = Math.toRadians(e.gradient.toDouble())
        val len = max(g.ew, g.eh) / 2f
        val dx = (cos(rad) * len).toFloat(); val dy = (sin(rad) * len).toFloat()
        p.shader = LinearGradient(-dx, -dy, dx, dy, g.color, g.color2, Shader.TileMode.CLAMP)
        return p
    }

    private fun textPaint(e: Element, g: Geo, sizePx: Float, color: Int, alpha: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; this.alpha = (alpha * 255).toInt()
            textSize = max(6f, sizePx)
            typeface = typefaceFor(e.weight)
            letterSpacing = e.letterSpacing
            if (e.skew != 0f) textSkewX = -e.skew * 0.35f
            textAlign = when (e.align) { "left" -> Paint.Align.LEFT; "right" -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
        }

    private fun drawBody(canvas: Canvas, e: Element, g: Geo, main: Paint, stroke: Paint = main) {
        val ew = g.ew; val eh = g.eh; val p = g.progress; val t = g.tMs
        when (e.type) {

            "wave" -> {
                val amp = (e.values.firstOrNull() ?: 0.5f).coerceIn(0.05f, 1f)
                val harm = (e.values.getOrNull(1) ?: 1f).coerceIn(0.2f, 3f)
                val path = Path()
                val n = 64
                for (i in 0..n) {
                    val fx = i.toFloat() / n
                    if (fx > p) break
                    val px = -ew / 2 + ew * fx
                    val py = sin((fx * 6.283f * harm) + t / 420f) * eh * 0.42f * amp
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                shaded(Paint(main).apply { style = Paint.Style.STROKE; strokeWidth = max(2f, e.strokeW * g.minSide * 2.4f); strokeCap = Paint.Cap.ROUND }, e, g).let { canvas.drawPath(path, it) }
                // filled area under the wave
                val fillPath = Path(path).apply { lineTo(-ew / 2 + ew * p, eh / 2); lineTo(-ew / 2, eh / 2); close() }
                canvas.drawPath(fillPath, Paint(main).apply { alpha = (g.alpha * 60).toInt() })
            }

            "radar" -> {
                val r = min(ew, eh) / 2f
                val rp = Paint(stroke).apply { alpha = (g.alpha * 90).toInt(); strokeWidth = max(1f, g.minSide * 0.002f) }
                for (k in 1..3) canvas.drawCircle(0f, 0f, r * k / 3f, rp)
                canvas.drawLine(-r, 0f, r, 0f, rp); canvas.drawLine(0f, -r, 0f, r, rp)
                val ang = (t / 1400f * 6.283f) % 6.283f
                val sweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.SweepGradient(0f, 0f, intArrayOf(withAlpha(g.color, 0), withAlpha(g.color, (g.alpha * 130).toInt())), floatArrayOf(0f, 1f))
                }
                canvas.save(); canvas.rotate(Math.toDegrees(ang.toDouble()).toFloat())
                canvas.drawArc(RectF(-r, -r, r, r), -28f, 28f, true, sweep)
                canvas.restore()
                // blips light up as the sweep passes them
                val blips = e.values.ifEmpty { listOf(0.35f, 0.62f, 0.8f) }
                blips.forEachIndexed { i, v ->
                    val ba = hash(e.seed, i, 11) * 6.283f
                    val br = r * v.coerceIn(0.05f, 1f)
                    var d = abs(((ang - ba) % 6.283f + 6.283f) % 6.283f)
                    if (d > 3.1416f) d = 6.283f - d
                    val lit = (1f - d / 1.2f).coerceIn(0f, 1f)
                    if (lit > 0.02f) canvas.drawCircle(cos(ba) * br, sin(ba) * br, max(2f, g.minSide * 0.008f) * (0.6f + lit), Paint(main).apply { alpha = (g.alpha * 255 * lit).toInt() })
                }
            }

            "steps" -> {
                val n = max(2, min(6, if (e.count > 1) e.count else (e.labels.size.coerceAtLeast(3))))
                val stepW = ew / n
                for (i in 0 until n) {
                    val appear = ((p * n) - i).coerceIn(0f, 1f)
                    if (appear <= 0.01f) continue
                    val hgt = eh * (0.28f + 0.72f * (i + 1) / n) * appear
                    val cx = -ew / 2 + stepW * (i + 0.5f)
                    val top = eh / 2 - hgt
                    val r = RectF(cx - stepW * 0.40f, top, cx + stepW * 0.40f, eh / 2)
                    val c = mix(g.color, if (e.color2.isBlank()) g.color else g.color2, i.toFloat() / (n - 1).coerceAtLeast(1))
                    canvas.drawRoundRect(r, min(r.width(), r.height()) * e.radius, min(r.width(), r.height()) * e.radius, Paint(main).apply { color = c; alpha = (g.alpha * 255).toInt() })
                    val num = textPaint(e, g, stepW * 0.42f, Color.WHITE, g.alpha * appear)
                    canvas.drawText("${i + 1}", cx, top + stepW * 0.46f, num)
                    val lbl = e.labels.getOrNull(i).orEmpty()
                    if (lbl.isNotBlank()) {
                        val tp = textPaint(e, g, min(stepW * 0.20f, g.minSide * 0.028f), Color.WHITE, g.alpha * appear)
                        wrap(lbl.take(22), tp, stepW * 0.86f).take(2).forEachIndexed { k, line ->
                            canvas.drawText(line, cx, top - g.minSide * 0.018f - k * tp.textSize * 1.15f, tp)
                        }
                    }
                }
            }

            "table" -> {
                val cols = max(2, min(5, if (e.count > 1) e.count else 3))
                val cells = e.labels
                val rows = max(1, min(8, if (cells.size <= cols) 1 else (cells.size + cols - 1) / cols))
                val cw = ew / cols; val ch = eh / rows
                for (r in 0 until rows) {
                    val appear = ((p * rows) - r).coerceIn(0f, 1f)
                    if (appear <= 0.01f) continue
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        val box = RectF(-ew / 2 + c * cw + 1.5f, -eh / 2 + r * ch + 1.5f, -ew / 2 + (c + 1) * cw - 1.5f, -eh / 2 + (r + 1) * ch - 1.5f)
                        val isHead = r == 0
                        val body = Paint(main).apply {
                            color = if (isHead) g.color else withAlpha(g.color, (g.alpha * 46).toInt())
                            alpha = (g.alpha * 255 * appear).toInt()
                        }
                        canvas.drawRoundRect(box, min(box.width(), box.height()) * e.radius * 0.5f, min(box.width(), box.height()) * e.radius * 0.5f, body)
                        val txt = cells.getOrNull(idx).orEmpty()
                        if (txt.isNotBlank()) {
                            val tp = textPaint(e, g, min(cw * 0.24f, ch * 0.42f), if (isHead) Color.WHITE else mix(Color.WHITE, g.color, 0.25f), g.alpha * appear)
                            val lines = wrap(txt.take(20), tp, box.width() * 0.9f)
                            val startY = box.centerY() - (lines.size - 1) * tp.textSize * 0.6f + tp.textSize * 0.35f
                            lines.take(2).forEachIndexed { k, line -> canvas.drawText(line, box.centerX(), startY + k * tp.textSize * 1.2f, tp) }
                        }
                    }
                }
            }

            "quote" -> {
                val qm = textPaint(e, g, min(ew, eh) * 0.9f, g.color, g.alpha * 0.55f)
                qm.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                canvas.drawText("\u201C", -ew / 2 + qm.textSize * 0.35f, -eh / 2 + qm.textSize * 0.95f, qm)
                val tp = textPaint(e, g, min(ew * 0.13f, g.minSide * 0.045f), Color.WHITE, g.alpha * p)
                val lines = wrap(e.text.ifBlank { "quote" }, tp, ew * 0.86f).take(4)
                lines.forEachIndexed { i, line -> canvas.drawText(line, 0f, -eh * 0.06f + i * tp.textSize * 1.28f, tp) }
                val who = e.labels.firstOrNull().orEmpty()
                if (who.isNotBlank()) {
                    val ap = textPaint(e, g, tp.textSize * 0.62f, mix(Color.WHITE, g.color, 0.5f), g.alpha * p)
                    canvas.drawText("— $who", 0f, -eh * 0.06f + lines.size * tp.textSize * 1.28f + ap.textSize, ap)
                }
            }

            "ticker" -> {
                val items = e.labels.ifEmpty { listOf("SAIF", "AI") }
                val tp = textPaint(e, g, min(eh * 0.5f, g.minSide * 0.036f), Color.WHITE, g.alpha)
                val joined = items.joinToString("   •   ")
                val full = tp.measureText(joined)
                val barY = 0f
                canvas.drawRoundRect(RectF(-ew / 2, -eh / 2, ew / 2, eh / 2), eh * 0.3f, eh * 0.3f, Paint(main).apply { alpha = (g.alpha * 70).toInt() })
                val span = max(1f, full + ew)
                val off = ((t / 42f) % span) * p.coerceAtLeast(0.001f)
                canvas.save()
                canvas.clipRect(RectF(-ew / 2, -eh / 2, ew / 2, eh / 2))
                var drawX = ew / 2 - off
                while (drawX > -ew / 2 - full) {
                    canvas.drawText(joined, drawX, barY + tp.textSize * 0.35f, Paint(tp).apply { textAlign = Paint.Align.LEFT })
                    drawX -= span
                }
                canvas.restore()
            }

            "arcdial" -> {
                val pct = ((e.values.firstOrNull() ?: 50f).coerceIn(0f, 100f)) / 100f
                val r = min(ew, eh * 1.7f) / 2f
                val box = RectF(-r, -r * 0.15f, r, r * 1.85f)
                val track = Paint(stroke).apply { alpha = (g.alpha * 55).toInt(); strokeWidth = max(4f, g.minSide * 0.022f); strokeCap = Paint.Cap.ROUND }
                canvas.drawArc(box, 180f, 180f, false, track)
                val arc = Paint(stroke).apply { strokeWidth = track.strokeWidth; strokeCap = Paint.Cap.ROUND }
                shaded(arc, e, g)
                canvas.drawArc(box, 180f, 180f * pct * p, false, arc)
                // needle
                val na = Math.toRadians((180.0 + 180.0 * pct * p))
                val nx = (cos(na) * r * 0.82).toFloat(); val ny = (sin(na) * r * 0.82).toFloat()
                canvas.drawLine(0f, r * 0.85f, nx, r * 0.85f + ny, Paint(stroke).apply { color = Color.WHITE; alpha = (g.alpha * 230).toInt(); strokeWidth = max(2f, g.minSide * 0.006f) })
                val vp = textPaint(e, g, r * 0.46f, Color.WHITE, g.alpha * p)
                val shown = if (e.fx == "counter") (pct * 100f * p).toInt().toString() else "${(pct * 100f).toInt()}"
                canvas.drawText(shown + (e.labels.firstOrNull().orEmpty().ifBlank { "%" }), 0f, r * 0.72f, vp)
            }

            "spotlight" -> {
                val cx = ((sin(t / 1700f) * 0.5f) * ew) * p
                val rad = max(ew, eh) * 0.42f
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, 0f, rad, intArrayOf(withAlpha(g.color, (g.alpha * 190).toInt()), withAlpha(g.color, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    xfermode = xferFor(if (e.blend == "Normal") "Add" else e.blend)
                }
                canvas.drawRect(RectF(-ew, -eh, ew, eh), sp)
            }

            "halftone" -> {
                val cols = max(4, min(28, if (e.count > 3) e.count else 14))
                val rows = max(3, min(28, (cols * eh / max(1f, ew)).toInt().coerceAtLeast(3)))
                val cw = ew / cols; val ch = eh / rows
                for (r in 0 until rows) for (c in 0 until cols) {
                    val v = e.values.getOrNull((r * cols + c) % max(1, e.values.size)) ?: hash(e.seed, r, c)
                    val appear = ((p * (rows + cols)) - (r + c) * 0.5f).coerceIn(0f, 1f)
                    if (appear <= 0.02f) continue
                    val rad = min(cw, ch) * 0.46f * v.coerceIn(0f, 1f) * appear
                    if (rad <= 0.4f) continue
                    canvas.drawCircle(-ew / 2 + cw * (c + 0.5f), -eh / 2 + ch * (r + 0.5f), rad, Paint(main).apply { alpha = (g.alpha * 235 * appear).toInt() })
                }
            }

            "scanline" -> {
                canvas.drawRoundRect(RectF(-ew / 2, -eh / 2, ew / 2, eh / 2), min(ew, eh) * e.radius, min(ew, eh) * e.radius, Paint(main).apply { alpha = (g.alpha * 40).toInt() })
                val rows = max(4, min(40, (eh / max(1f, g.minSide * 0.012f)).toInt()))
                val lp = Paint(main).apply { alpha = (g.alpha * 34).toInt() }
                for (i in 0 until rows) {
                    val yy = -eh / 2 + eh * i / rows
                    canvas.drawLine(-ew / 2, yy, ew / 2, yy, lp)
                }
                val sy = -eh / 2 + eh * ((t / 1600f) % 1f)
                val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, sy - eh * 0.09f, 0f, sy + eh * 0.09f,
                        intArrayOf(withAlpha(g.color, 0), withAlpha(g.color, (g.alpha * 190).toInt()), withAlpha(g.color, 0)),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.drawRect(RectF(-ew / 2, sy - eh * 0.09f, ew / 2, sy + eh * 0.09f), bar)
            }

            "confetti" -> {
                val n = e.count.coerceIn(8, 120)
                for (i in 0 until n) {
                    val r1 = hash(e.seed, i, 1); val r2 = hash(e.seed, i, 2); val r3 = hash(e.seed, i, 3)
                    val fall = ((r2 + t / 2600f * (0.5f + r3)) % 1f)
                    val px = -ew / 2 + ew * r1 + sin((fall * 6.283f) + r3 * 6f) * ew * 0.05f
                    val py = -eh / 2 + eh * fall * p
                    val cw = max(2f, g.minSide * 0.011f * (0.5f + r3))
                    canvas.save(); canvas.translate(px, py); canvas.rotate((fall * 720f * (if (r1 > 0.5f) 1f else -1f)))
                    canvas.drawRect(-cw / 2, -cw / 4, cw / 2, cw / 4, Paint(main).apply {
                        color = mix(g.color, if (e.color2.isBlank()) Color.WHITE else g.color2, r3); alpha = (g.alpha * 235).toInt()
                    })
                    canvas.restore()
                }
            }

            "beam" -> {
                val spread = (e.values.firstOrNull() ?: 0.35f).coerceIn(0.08f, 1.2f)
                val sway = sin(t / 1900f) * spread * 0.35f
                val path = Path().apply {
                    moveTo(0f, -eh / 2)
                    lineTo(-ew * (spread + sway), eh / 2)
                    lineTo(ew * (spread - sway), eh / 2)
                    close()
                }
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, -eh / 2, 0f, eh / 2, withAlpha(g.color, (g.alpha * 150 * p).toInt()), withAlpha(g.color, 0), Shader.TileMode.CLAMP)
                    xfermode = xferFor(if (e.blend == "Normal") "Add" else e.blend)
                }
                canvas.drawPath(path, bp)
            }

            "ribbon" -> {
                val bands = max(2, min(6, if (e.count > 1) e.count else 3))
                for (b in 0 until bands) {
                    val path = Path()
                    val n = 48
                    val phase = t / 900f + b * 1.1f
                    val yOff = (b - (bands - 1) / 2f) * eh * 0.22f
                    for (i in 0..n) {
                        val fx = i.toFloat() / n
                        if (fx > p) break
                        val px = -ew / 2 + ew * fx
                        val py = yOff + sin(fx * 5.2f + phase) * eh * 0.20f
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    val rp = Paint(stroke).apply {
                        strokeWidth = max(2f, g.minSide * (0.016f - b * 0.002f))
                        color = mix(g.color, if (e.color2.isBlank()) Color.WHITE else g.color2, b.toFloat() / bands)
                        alpha = (g.alpha * (235 - b * 30)).toInt()
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawPath(path, rp)
                }
            }

            "compare" -> {
                val split = (-ew / 2 + ew * ((e.values.firstOrNull() ?: 50f).coerceIn(2f, 98f) / 100f))
                val left = RectF(-ew / 2, -eh / 2, split, eh / 2)
                val right = RectF(split, -eh / 2, ew / 2, eh / 2)
                canvas.drawRect(left, Paint(main).apply { alpha = (g.alpha * 120).toInt() })
                canvas.drawRect(right, Paint(main).apply { color = if (e.color2.isBlank()) Color.WHITE else g.color2; alpha = (g.alpha * 120).toInt() })
                canvas.drawLine(split, -eh / 2, split, eh / 2, Paint(stroke).apply { color = Color.WHITE; alpha = (g.alpha * 235).toInt(); strokeWidth = max(2f, g.minSide * 0.005f) })
                val knob = g.minSide * 0.022f
                canvas.drawCircle(split, 0f, knob, Paint(main).apply { color = Color.WHITE; alpha = (g.alpha * 255).toInt() })
                val l = e.labels.getOrNull(0).orEmpty(); val r = e.labels.getOrNull(1).orEmpty()
                val tp = textPaint(e, g, min(eh * 0.28f, g.minSide * 0.042f), Color.WHITE, g.alpha * p)
                if (l.isNotBlank()) canvas.drawText(l.take(14), left.centerX(), tp.textSize * 0.35f, tp)
                if (r.isNotBlank()) canvas.drawText(r.take(14), right.centerX(), tp.textSize * 0.35f, tp)
            }

            "heat" -> {
                val cols = max(3, min(16, if (e.count > 2) e.count else 8))
                val rows = max(2, min(12, max(2, e.values.size / cols)))
                val cw = ew / cols; val ch = eh / rows
                for (r in 0 until rows) for (c in 0 until cols) {
                    val idx = r * cols + c
                    val v = (e.values.getOrNull(idx) ?: hash(e.seed, (r * 17 + c) * 3, 1)).coerceIn(0f, 1f)
                    val appear = ((p * (cols + rows)) - (c + r) * 0.35f).coerceIn(0f, 1f)
                    if (appear <= 0.02f) continue
                    val box = RectF(-ew / 2 + c * cw + 1f, -eh / 2 + r * ch + 1f, -ew / 2 + (c + 1) * cw - 1f, -eh / 2 + (r + 1) * ch - 1f)
                    val cc = mix(if (e.color2.isBlank()) Color.parseColor("#123") else g.color2, g.color, v)
                    canvas.drawRoundRect(box, min(box.width(), box.height()) * e.radius * 0.4f, min(box.width(), box.height()) * e.radius * 0.4f,
                        Paint(main).apply { color = cc; alpha = (g.alpha * 245 * appear).toInt() })
                }
            }

            "spiral" -> {
                val turns = (e.values.firstOrNull() ?: 3f).coerceIn(1f, 8f)
                val rMax = min(ew, eh) / 2f
                val path = Path()
                val n = 220
                for (i in 0..n) {
                    val f = i.toFloat() / n
                    if (f > p) break
                    val a = f * turns * 6.283f
                    val rr = rMax * f
                    val px = cos(a) * rr; val py = sin(a) * rr
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                val sp = Paint(stroke).apply { strokeWidth = max(2f, g.minSide * 0.006f); strokeCap = Paint.Cap.ROUND }
                shaded(sp, e, g)
                canvas.drawPath(path, sp)
            }

            "orbit" -> {
                val n = max(1, min(6, if (e.count > 0) e.count else 3))
                val rx = ew / 2f; val ry = eh / 2f
                val op = Paint(stroke).apply { alpha = (g.alpha * 60).toInt(); strokeWidth = max(1f, g.minSide * 0.002f) }
                for (k in 1..n) canvas.drawOval(RectF(-rx * k / n, -ry * k / n, rx * k / n, ry * k / n), op)
                canvas.drawCircle(0f, 0f, max(3f, g.minSide * 0.022f), Paint(main).apply { alpha = (g.alpha * 255).toInt() })
                for (k in 1..n) {
                    val speed = 1f + (n - k) * 0.45f
                    val a = (t / 1500f * speed) + k * 1.7f
                    val px = cos(a) * rx * k / n; val py = sin(a) * ry * k / n
                    canvas.drawCircle(px, py, max(2.5f, g.minSide * 0.012f), Paint(main).apply {
                        color = mix(g.color, if (e.color2.isBlank()) Color.WHITE else g.color2, k.toFloat() / (n + 1)); alpha = (g.alpha * 255).toInt()
                    })
                    val lbl = e.labels.getOrNull(k - 1).orEmpty()
                    if (lbl.isNotBlank()) {
                        val tp = textPaint(e, g, min(g.minSide * 0.024f, ry * 0.24f), Color.WHITE, g.alpha)
                        canvas.drawText(lbl.take(12), px, py - g.minSide * 0.02f, tp)
                    }
                }
            }

            "stack" -> {
                val bands = max(2, min(6, if (e.count > 1) e.count else e.values.size.coerceAtLeast(3)))
                var acc = 0f
                for (b in 0 until bands) {
                    val v = (e.values.getOrNull(b) ?: (1f / bands)).coerceIn(0.01f, 1f)
                    val appear = ((p * bands) - b).coerceIn(0f, 1f)
                    if (appear <= 0.01f) continue
                    val top = -eh / 2 + eh * acc
                    val hgt = eh * v * appear
                    val box = RectF(-ew / 2, top, ew / 2, top + hgt)
                    canvas.drawRoundRect(box, min(ew, hgt) * e.radius * 0.35f, min(ew, hgt) * e.radius * 0.35f,
                        Paint(main).apply { color = mix(g.color, if (e.color2.isBlank()) Color.WHITE else g.color2, b.toFloat() / bands); alpha = (g.alpha * 230).toInt() })
                    val lbl = e.labels.getOrNull(b).orEmpty()
                    if (lbl.isNotBlank() && hgt > g.minSide * 0.03f) {
                        val tp = textPaint(e, g, min(hgt * 0.5f, g.minSide * 0.028f), Color.WHITE, g.alpha * appear)
                        canvas.drawText(lbl.take(16), 0f, box.centerY() + tp.textSize * 0.35f, tp)
                    }
                    acc += v
                }
            }

            "badge" -> {
                val r = min(ew, eh) / 2f
                val points = max(5, min(16, if (e.count > 4) e.count else 8))
                val path = Path()
                for (i in 0 until points * 2) {
                    val a = Math.PI * i / points - Math.PI / 2
                    val rr = if (i % 2 == 0) r else r * 0.82f
                    val px = cos(a) * rr; val py = sin(a) * rr
                    if (i == 0) path.moveTo(px.toFloat(), py.toFloat()) else path.lineTo(px.toFloat(), py.toFloat())
                }
                path.close()
                val bp = Paint(main)
                shaded(bp, e, g)
                bp.alpha = (g.alpha * 255).toInt()
                canvas.save(); canvas.scale(0.6f + 0.4f * p, 0.6f + 0.4f * p); canvas.drawPath(path, bp); canvas.restore()
                val lbl = e.text.ifBlank { e.labels.firstOrNull().orEmpty() }
                if (lbl.isNotBlank()) {
                    val tp = textPaint(e, g, min(r * 0.42f, g.minSide * 0.05f), Color.WHITE, g.alpha * p)
                    wrap(lbl.take(16), tp, r * 1.4f).take(2).forEachIndexed { i, line ->
                        canvas.drawText(line, 0f, tp.textSize * 0.35f + (i - 0.5f) * tp.textSize * 1.1f, tp)
                    }
                }
            }

            "barcode" -> {
                val n = max(6, min(48, if (e.count > 5) e.count else 22))
                var x = -ew / 2
                val total = (0 until n).sumOf { (0.4 + hash(e.seed, it, 7) * 1.0).toDouble() }.toFloat()
                val unit = ew / max(0.001f, total)
                for (i in 0 until n) {
                    val bw = unit * (0.4f + hash(e.seed, i, 7))
                    val appear = ((p * n * 1.4f) - i).coerceIn(0f, 1f)
                    if (appear > 0.02f) {
                        canvas.drawRect(x, -eh / 2, x + bw * 0.82f, -eh / 2 + eh * appear,
                            Paint(main).apply { alpha = (g.alpha * (if (i % 3 == 0) 255 else 190)).toInt() })
                    }
                    x += bw
                }
                val code = e.text.ifBlank { e.labels.firstOrNull().orEmpty() }
                if (code.isNotBlank()) {
                    val tp = textPaint(e, g, min(eh * 0.3f, g.minSide * 0.026f), Color.WHITE, g.alpha * p)
                    canvas.drawText(code.take(24), 0f, eh / 2 + tp.textSize * 1.1f, tp)
                }
            }
        }
    }

    /**
     * Extra motion path applied on top of the element's own x/y animations, so a shape can float, orbit or drift
     * without the model having to write a dozen keyframes. Returns dx, dy in pixels.
     */
    fun pathOffset(e: Element, tMs: Long, minSide: Float): Pair<Float, Float> {
        if (e.path.isBlank()) return 0f to 0f
        val t = tMs / 1000f
        val amp = minSide * 0.02f
        return when (e.path) {
            "sine" -> (sin(t * 1.6f + e.seed) * amp * 2f) to (cos(t * 1.1f + e.seed) * amp * 0.6f)
            "circle" -> (cos(t * 1.2f + e.seed) * amp * 1.6f) to (sin(t * 1.2f + e.seed) * amp * 1.6f)
            "figure8" -> (sin(t * 1.3f + e.seed) * amp * 2.2f) to (sin(t * 2.6f + e.seed) * amp * 1.1f)
            "drift" -> (t * amp * 0.35f * (if (e.seed % 2 == 0) 1f else -1f)) to (-t * amp * 0.2f)
            "linear" -> 0f to (-((t * amp) % (minSide * 0.2f)))
            else -> 0f to 0f
        }
    }

    /** Angle in degrees from the element centre to a point, used by radar/beam primitives. */
    fun angleTo(dx: Float, dy: Float): Float = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

    /** Distance helper kept here so primitives do not each re-import kotlin.math.hypot. */
    fun dist(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)
}
