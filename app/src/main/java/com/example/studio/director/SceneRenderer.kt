package com.example.studio.director

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Deterministic native renderer for validated [SceneGraph]s. Same function is used by the live
 * preview and the exporter, so what you see is what gets encoded. It never executes generated code:
 * the AI can only choose from the whitelisted primitives in [SUPPORTED_ELEMENT_TYPES].
 */
object SceneRenderer {

    fun ease(t: Float, name: String): Float {
        val x = t.coerceIn(0f, 1f)
        return when (name) {
            "linear" -> x
            "easeIn" -> x * x * x
            "easeInOut" -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            "overshoot", "backOut" -> { val c1 = 1.70158f; val c3 = c1 + 1f; 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2) }
            "expoOut" -> if (x >= 1f) 1f else 1f - 2f.pow(-10f * x)
            "cubicInOut" -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            "bounce" -> com.example.studio.render.SceneEasing.bounceOut(x)
            "spring" -> com.example.studio.render.SceneEasing.spring(x)
            else -> 1f - (1f - x).pow(3)
        }
    }

    /** Resolves an animated property at [tMs]; later animations of the same prop win once started. */
    fun resolve(e: Element, prop: String, base: Float, tMs: Long): Float {
        var v = base
        val list = e.anims.filter { it.prop == prop }.sortedBy { it.startMs }
        for (a in list) {
            if (tMs < a.startMs) { if (v == base && list.first() === a) v = a.from; break }
            val span = max(1L, a.endMs - a.startMs)
            v = if (tMs >= a.endMs) a.to else a.from + (a.to - a.from) * ease((tMs - a.startMs).toFloat() / span, a.easing)
        }
        return v
    }

    fun parseColor(hex: String, fallback: Int = 0xFFFFFFFF.toInt()): Int =
        try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { fallback }

    /** Blend mode name -> PorterDuff xfermode, or null for plain drawing. Mirrors the timeline's own blend modes. */
    fun blendXfer(name: String): android.graphics.PorterDuffXfermode? = when (name.lowercase()) {
        "screen" -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
        "multiply" -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
        "overlay" -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.OVERLAY)
        "add" -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
        "lighten" -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.LIGHTEN)
        "darken" -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DARKEN)
        else -> null
    }

    fun draw(canvas: Canvas, width: Int, height: Int, g: SceneGraph, tMs: Long, durationMs: Long, opacity: Float = 1f) {
        val w = width.toFloat(); val h = height.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h, parseColor(g.bgTop), parseColor(g.bgBottom), Shader.TileMode.CLAMP)
        p.alpha = (opacity * 255).toInt()
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val prog = if (durationMs > 0) (tMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val c = g.camera
        val zoom = c.zoomFrom + (c.zoomTo - c.zoomFrom) * ease(prog, "easeInOut")
        val panX = (c.panXFrom + (c.panXTo - c.panXFrom) * prog) * w
        val panY = (c.panYFrom + (c.panYTo - c.panYFrom) * prog) * h
        fun bounded(value: Float, min: Float, max: Float) = if (value.isFinite()) value.coerceIn(min, max) else 0f
        val pose = SpatialPose(
            bounded(c.dollyFrom + (c.dollyTo - c.dollyFrom) * prog, -1f, 1f),
            bounded(c.orbitFrom + (c.orbitTo - c.orbitFrom) * prog, -75f, 75f),
            bounded(c.tiltFrom + (c.tiltTo - c.tiltFrom) * prog, -75f, 75f)
        )
        canvas.save()
        canvas.translate(w / 2f + panX, h / 2f + panY); canvas.scale(zoom, zoom); canvas.translate(-w / 2f, -h / 2f)
        // Sort in camera space (not just source depth): an orbit can move a side layer behind another.
        // The sort is stable, so old scenes with zero depth keep their original stacking.
        g.elements.sortedBy { cameraDepth(it, w, h, tMs, pose) }.forEach { drawElement(canvas, w, h, it, tMs, opacity, pose) }
        canvas.restore()
    }

    private data class SpatialPose(val dolly: Float, val orbit: Float, val tilt: Float)

    private fun cameraDepth(e: Element, w: Float, h: Float, tMs: Long, camera: SpatialPose): Float {
        val side = min(w, h)
        val rawDepth = resolve(e, "depth", e.depth, tMs)
        val depth = if (rawDepth.isFinite()) rawDepth.coerceIn(-1f, 1f) else 0f
        if (camera.orbit == 0f && camera.tilt == 0f) return depth
        val (dx, dy) = SceneFx.pathOffset(e, tMs, side)
        val x = resolve(e, "x", e.x, tMs) * w + dx - w / 2f
        val y = resolve(e, "y", e.y, tMs) * h + dy - h / 2f
        return SpatialMath.rotate(SpatialMath.Vec3(x, y, depth * side), camera.tilt, camera.orbit).z
    }

    private fun drawElement(canvas: Canvas, w: Float, h: Float, e: Element, tMs: Long, sceneOpacity: Float, camera: SpatialPose) {
        val alpha = resolve(e, "alpha", if (e.anims.any { it.prop == "alpha" }) 0f else 1f, tMs).coerceIn(0f, 1f) * sceneOpacity
        if (alpha <= 0.002f) return
        val x = resolve(e, "x", e.x, tMs) * w
        val y = resolve(e, "y", e.y, tMs) * h
        val ew = resolve(e, "w", e.w, tMs) * w
        val eh = resolve(e, "h", e.h, tMs) * h
        val scale = resolve(e, "scale", 1f, tMs)
        val rot = resolve(e, "rotation", 0f, tMs)
        val progress = resolve(e, "progress", 1f, tMs).coerceIn(0f, 1f)
        val color = parseColor(e.color)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = (alpha * 255).toInt() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = max(1f, e.strokeW * min(w, h))
            strokeCap = Paint.Cap.ROUND; this.alpha = (alpha * 255).toInt()
        }
        // Round 9: an element can carry its own blend mode, and can float/orbit on a motion path.
        blendXfer(e.blend)?.let { xf -> fill.xfermode = xf; stroke.xfermode = xf }
        val side = min(w, h)
        val (pdx, pdy) = SceneFx.pathOffset(e, tMs, side)
        val depth = resolve(e, "depth", e.depth, tMs).let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
        val centre = SpatialMath.rotate(SpatialMath.Vec3(x + pdx - w / 2f, y + pdy - h / 2f, depth * side), camera.tilt, camera.orbit)
        val projected = SpatialMath.project(centre.copy(z = centre.z + camera.dolly * side), side * 3.2f)
        canvas.save()
        canvas.translate(w / 2f + projected.x, h / 2f + projected.y); canvas.rotate(rot)
        canvas.scale(scale * projected.scale, scale * projected.scale)
        if (Scene3DFx.handles(e.type)) {
            val geo = SceneFx.Geo(ew, eh, alpha, progress, tMs, w, h, color,
                parseColor(e.color2.ifBlank { e.color }), side)
            val pitch = resolve(e, "rotationX", e.rotationX, tMs).let { if (it.isFinite()) it.coerceIn(-360f, 360f) else 0f } + camera.tilt
            val yaw = resolve(e, "rotationY", e.rotationY, tMs).let { if (it.isFinite()) it.coerceIn(-360f, 360f) else 0f } + camera.orbit
            Scene3DFx.draw(canvas, e, geo, fill, pitch, yaw)
            canvas.restore()
            return
        }
        // The advanced primitives are drawn by SceneFx, which also applies their glow / shadow / gradient.
        if (SceneFx.handles(e.type)) {
            val geo = SceneFx.Geo(ew, eh, alpha, progress, tMs, w, h, color,
                parseColor(e.color2.ifBlank { e.color }), min(w, h))
            SceneFx.draw(canvas, e, geo, fill, stroke)
            canvas.restore()
            return
        }
        when (e.type) {
            "text" -> {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; this.alpha = (alpha * 255).toInt(); textSize = e.size * min(w, h) * 1.6f
                    textAlign = when (e.align) { "left" -> Paint.Align.LEFT; "right" -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                    typeface = SceneFx.typefaceFor(e.weight)
                    letterSpacing = e.letterSpacing
                    if (e.skew != 0f) textSkewX = -e.skew * 0.35f
                }
                val maxW = if (ew > 0) ew else w * 0.9f
                val local = tMs - (e.anims.minOfOrNull { it.startMs } ?: 0L)
                val shown = textFx(e, local, tMs)
                // Round 9: the motion and focus text effects. Each one is a deterministic function of the
                // element's own local clock, so the preview and the exporter agree frame for frame, and none of
                // the whitelisted fx names is a silent no-op any more.
                val intro = (local / 620f).coerceIn(0f, 1f)
                val travel = max(eh, tp.textSize * 1.6f)
                when (e.fx) {
                    "rise" -> canvas.translate(0f, (1f - ease(intro, "easeOut")) * travel * 0.55f)
                    "slide" -> canvas.translate((1f - ease(intro, "easeOut")) * max(ew, tp.textSize * 4f) * 0.6f, 0f)
                    "pop" -> { val ps = 0.55f + 0.45f * ease(intro, "backOut"); canvas.scale(ps, ps) }
                    "scale" -> { val ss = 1.35f - 0.35f * ease(intro, "expoOut"); canvas.scale(ss, ss) }
                    "bounce" -> canvas.translate(0f, -(1f - ease(intro, "bounce")) * travel * 0.9f)
                    else -> { }
                }
                if (e.fx == "fade") {
                    tp.alpha = (alpha * 255f * ease(intro, "easeOut")).toInt().coerceIn(0, 255)
                }
                if (e.fx == "blur") {
                    // A real blur filter needs API 31 (this module supports 26), so the text is softened with
                    // offset low-alpha copies that tighten into focus as the line arrives.
                    val amt = (1f - ease(intro, "easeOut")) * tp.textSize * 0.20f
                    if (amt > 0.35f) {
                        val bp = Paint(tp).apply { this.alpha = (alpha * 55f).toInt().coerceIn(0, 255) }
                        val soft = wrap(shown, tp, maxW)
                        for (k in 1..5) {
                            val dx = amt * cos(k * 1.7f)
                            val dy = amt * sin(k * 2.3f)
                            soft.forEachIndexed { i, line ->
                                canvas.drawText(line, dx, i * tp.textSize * 1.2f + dy, bp)
                            }
                        }
                    }
                }
                if (e.fx == "glow") {
                    val gp = Paint(tp).apply { this.alpha = (alpha * 70).toInt(); style = Paint.Style.STROKE; strokeWidth = tp.textSize * 0.22f; strokeJoin = Paint.Join.ROUND }
                    wrap(shown, tp, maxW).forEachIndexed { i, line -> canvas.drawText(line, 0f, i * tp.textSize * 1.2f, gp) }
                }
                if (e.fx == "outline") {
                    val op = Paint(tp).apply { this.color = 0xFF000000.toInt(); this.alpha = (alpha * 255).toInt(); style = Paint.Style.STROKE; strokeWidth = tp.textSize * 0.14f; strokeJoin = Paint.Join.ROUND }
                    wrap(shown, tp, maxW).forEachIndexed { i, line -> canvas.drawText(line, 0f, i * tp.textSize * 1.2f, op) }
                }
                if (e.fx == "neon") {
                    // Three bloom passes behind the crisp text: a real neon tube, not a single blurred stroke.
                    for (k in 3 downTo 1) {
                        val np = Paint(tp).apply {
                            this.alpha = (alpha * (34 + k * 12)).toInt(); style = Paint.Style.STROKE
                            strokeWidth = tp.textSize * 0.10f * k; strokeJoin = Paint.Join.ROUND
                        }
                        wrap(shown, tp, maxW).forEachIndexed { i, line -> canvas.drawText(line, 0f, i * tp.textSize * 1.2f, np) }
                    }
                }
                if (e.fx == "shadow3d") {
                    val depth = (tp.textSize * 0.16f).toInt().coerceAtLeast(2)
                    for (k in depth downTo 1) {
                        val sp = Paint(tp).apply { this.color = 0xFF000000.toInt(); this.alpha = (alpha * 90).toInt() }
                        wrap(shown, tp, maxW).forEachIndexed { i, line -> canvas.drawText(line, k.toFloat(), i * tp.textSize * 1.2f + k, sp) }
                    }
                }
                if (e.fx == "words" || e.fx == "karaoke") {
                    drawWordsPop(canvas, shown, tp, maxW, local)
                } else {
                    wrap(shown, tp, maxW).forEachIndexed { i, line -> canvas.drawText(line, 0f, i * tp.textSize * 1.2f, tp) }
                }
            }
            "rect" -> {
                val r = RectF(-ew / 2, -eh / 2, -ew / 2 + ew * progress, eh / 2)
                val rad = min(ew, eh) * e.radius
                val rp = if (e.color2.isBlank()) fill else Paint(fill).apply {
                    val a = Math.toRadians(e.gradient.toDouble()); val len = max(ew, eh) / 2f
                    shader = LinearGradient(-(cos(a) * len).toFloat(), -(sin(a) * len).toFloat(),
                        (cos(a) * len).toFloat(), (sin(a) * len).toFloat(), color, parseColor(e.color2), Shader.TileMode.CLAMP)
                }
                if (e.outline > 0.5f) canvas.drawRoundRect(r, rad, rad, Paint(stroke).apply { strokeWidth = max(1.5f, e.strokeW * min(w, h) * 2f) })
                else canvas.drawRoundRect(r, rad, rad, rp)
            }
            "circle" -> canvas.drawCircle(0f, 0f, min(ew, eh) / 2f * progress, if (e.strokeW > 0.02f) fill else stroke)
            "ring" -> {
                val r = RectF(-ew / 2, -ew / 2, ew / 2, ew / 2)
                stroke.strokeWidth = max(2f, e.strokeW * min(w, h) * 3f)
                canvas.drawArc(r, -90f, 360f * progress, false, stroke)
            }
            "line", "arrow" -> {
                val dx = (e.x2 - e.x) * w * progress; val dy = (e.y2 - e.y) * h * progress
                canvas.drawLine(0f, 0f, dx, dy, stroke)
                if (e.type == "arrow" && progress > 0.9f) {
                    val ang = kotlin.math.atan2(dy, dx); val a = min(w, h) * 0.02f
                    val path = Path().apply {
                        moveTo(dx, dy)
                        lineTo(dx - a * cos(ang - 0.45f), dy - a * sin(ang - 0.45f))
                        lineTo(dx - a * cos(ang + 0.45f), dy - a * sin(ang + 0.45f)); close()
                    }
                    canvas.drawPath(path, fill)
                }
            }
            "bars" -> drawBars(canvas, e, ew, eh, progress, fill)
            "linechart" -> drawLineChart(canvas, e, ew, eh, progress, stroke)
            "nodes" -> drawNodes(canvas, e, ew, eh, progress, fill, stroke, tMs)
            "grid" -> {
                stroke.alpha = (alpha * 90).toInt()
                val n = if (e.count > 1) e.count else 8
                for (i in 0..n) {
                    val gx = -ew / 2 + ew * i / n; val gy = -eh / 2 + eh * i / n
                    canvas.drawLine(gx, -eh / 2, gx, eh / 2, stroke); canvas.drawLine(-ew / 2, gy, ew / 2, gy, stroke)
                }
            }
            "icon" -> drawIcon(canvas, e.text, min(ew, eh), fill, stroke)
            "glow" -> {
                val rad = max(1f, min(ew, eh) / 2f * (0.9f + 0.1f * sin(tMs / 500f)))
                val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(0f, 0f, rad, intArrayOf(withAlpha(color, (alpha * 150).toInt()), withAlpha(color, 0)), null, Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(0f, 0f, rad, gp)
            }
            "vignette" -> {
                canvas.restore(); canvas.save()
                val rad = max(w, h) * 0.75f
                val vp = Paint().apply {
                    shader = android.graphics.RadialGradient(w / 2f, h / 2f, rad, intArrayOf(0x00000000, withAlpha(0xFF000000.toInt(), (alpha * 190).toInt())), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w, h, vp)
            }
            "grain" -> {
                canvas.restore(); canvas.save()
                val gp = Paint().apply { this.color = 0xFFFFFFFF.toInt(); this.alpha = (alpha * 38).toInt() }
                val frame = (tMs / 50L).toInt()   // 20 fps film grain
                for (i in 0 until 160) {
                    canvas.drawRect(hash(frame, i, 1) * w, hash(frame, i, 2) * h, hash(frame, i, 1) * w + 2f, hash(frame, i, 2) * h + 2f, gp)
                }
            }
            "sweep" -> {
                canvas.restore(); canvas.save()
                val pos = (-0.3f + 1.6f * progress) * w
                val sp = Paint().apply {
                    shader = android.graphics.LinearGradient(pos - w * 0.2f, 0f, pos + w * 0.2f, 0f,
                        intArrayOf(0x00FFFFFF, withAlpha(0xFFFFFFFF.toInt(), (alpha * 90).toInt()), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w, h, sp)
            }
            "flash" -> {
                canvas.restore(); canvas.save()
                val fp = Paint().apply { this.color = color; this.alpha = (alpha * 255).toInt() }
                canvas.drawRect(0f, 0f, w, h, fp)
            }
            "glitch" -> {
                canvas.restore(); canvas.save()
                val slices = 6
                val gp = Paint().apply { this.color = color; this.alpha = (alpha * 120).toInt() }
                val frame = (tMs / 40L).toInt()
                for (i in 0 until slices) {
                    val sy = hash(frame, i, 3) * h; val sh = h * (0.01f + 0.03f * hash(frame, i, 4)); val off = (hash(frame, i, 5) - 0.5f) * w * 0.12f
                    canvas.drawRect(max(0f, off), sy, w + min(0f, off), sy + sh, gp)
                }
            }
            "burst" -> {
                val n = if (e.count > 3) e.count else 12
                val inner = min(ew, eh) * 0.25f * progress; val outer = min(ew, eh) / 2f * progress
                for (i in 0 until n) {
                    val a = (2.0 * Math.PI * i / n).toFloat()
                    canvas.drawLine(cos(a) * inner, sin(a) * inner, cos(a) * outer, sin(a) * outer, stroke)
                }
            }
            "donut" -> {
                // values[0] = percent 0..100 filled; draws a track ring and the filled arc
                val pct = (e.values.firstOrNull() ?: 100f).coerceIn(0f, 100f) / 100f
                val r = RectF(-ew / 2, -ew / 2, ew / 2, ew / 2)
                val tr = Paint(stroke).apply { this.alpha = (alpha * 55).toInt(); strokeWidth = max(4f, e.strokeW * min(w, h) * 4f) }
                stroke.strokeWidth = tr.strokeWidth
                canvas.drawArc(r, 0f, 360f, false, tr)
                canvas.drawArc(r, -90f, 360f * pct * progress, false, stroke)
            }
            "particles" -> {
                val n = e.count.coerceIn(1, 120)
                for (i in 0 until n) {
                    val r1 = hash(e.seed, i, 1); val r2 = hash(e.seed, i, 2); val r3 = hash(e.seed, i, 3)
                    val px = (r1 - 0.5f) * ew
                    val py = ((r2 + tMs / 6000f * (0.3f + r3)) % 1f - 0.5f) * eh
                    canvas.drawCircle(px, py, 2f + r3 * min(w, h) * 0.006f, fill)
                }
            }
        }
        canvas.restore()
    }

    /** Simple vector glyphs centred on (0,0) inside an s x s box. Outline style with a few filled accents. */
    private fun drawIcon(canvas: Canvas, name: String, s: Float, fill: Paint, stroke: Paint) {
        if (s <= 1f) return
        val r = s / 2f
        stroke.strokeWidth = max(3f, s * 0.045f); stroke.strokeJoin = Paint.Join.ROUND
        when (name.lowercase()) {
            "chart" -> {
                val bw = s * 0.16f
                canvas.drawRoundRect(RectF(-r * 0.62f, r * 0.05f, -r * 0.62f + bw, r * 0.7f), bw * 0.2f, bw * 0.2f, fill)
                canvas.drawRoundRect(RectF(-r * 0.2f, -r * 0.25f, -r * 0.2f + bw, r * 0.7f), bw * 0.2f, bw * 0.2f, fill)
                canvas.drawRoundRect(RectF(r * 0.22f, -r * 0.6f, r * 0.22f + bw, r * 0.7f), bw * 0.2f, bw * 0.2f, fill)
                val tr = Path().apply { moveTo(-r * 0.7f, -r * 0.1f); lineTo(-r * 0.25f, -r * 0.45f); lineTo(r * 0.05f, -r * 0.25f); lineTo(r * 0.7f, -r * 0.8f) }
                canvas.drawPath(tr, stroke)
            }
            "coin" -> {
                canvas.drawCircle(0f, 0f, r * 0.8f, stroke)
                canvas.drawCircle(0f, 0f, r * 0.55f, stroke)
                canvas.drawLine(0f, -r * 0.32f, 0f, r * 0.32f, stroke)
                canvas.drawLine(-r * 0.18f, -r * 0.12f, r * 0.18f, -r * 0.12f, stroke)
            }
            "shield" -> {
                val p = Path().apply {
                    moveTo(0f, -r * 0.9f); lineTo(r * 0.75f, -r * 0.55f); lineTo(r * 0.75f, 0f)
                    quadTo(r * 0.75f, r * 0.65f, 0f, r * 0.95f); quadTo(-r * 0.75f, r * 0.65f, -r * 0.75f, 0f)
                    lineTo(-r * 0.75f, -r * 0.55f); close()
                }
                canvas.drawPath(p, stroke)
                val ck = Path().apply { moveTo(-r * 0.3f, 0f); lineTo(-r * 0.05f, r * 0.28f); lineTo(r * 0.38f, -r * 0.25f) }
                canvas.drawPath(ck, stroke)
            }
            "lock" -> {
                canvas.drawRoundRect(RectF(-r * 0.6f, -r * 0.05f, r * 0.6f, r * 0.8f), r * 0.12f, r * 0.12f, stroke)
                canvas.drawArc(RectF(-r * 0.38f, -r * 0.8f, r * 0.38f, r * 0.0f), 180f, 180f, false, stroke)
                canvas.drawCircle(0f, r * 0.38f, r * 0.09f, fill)
            }
            "bulb" -> {
                canvas.drawCircle(0f, -r * 0.2f, r * 0.55f, stroke)
                canvas.drawLine(-r * 0.25f, r * 0.5f, r * 0.25f, r * 0.5f, stroke)
                canvas.drawLine(-r * 0.18f, r * 0.7f, r * 0.18f, r * 0.7f, stroke)
                canvas.drawLine(0f, -r * 0.2f, 0f, r * 0.3f, stroke)
            }
            "clock" -> {
                canvas.drawCircle(0f, 0f, r * 0.8f, stroke)
                canvas.drawLine(0f, 0f, 0f, -r * 0.5f, stroke)
                canvas.drawLine(0f, 0f, r * 0.35f, r * 0.2f, stroke)
                canvas.drawCircle(0f, 0f, r * 0.06f, fill)
            }
            "heart" -> {
                val p = Path().apply {
                    moveTo(0f, r * 0.75f)
                    cubicTo(-r * 1.1f, r * 0.05f, -r * 0.6f, -r * 0.85f, 0f, -r * 0.3f)
                    cubicTo(r * 0.6f, -r * 0.85f, r * 1.1f, r * 0.05f, 0f, r * 0.75f)
                    close()
                }
                canvas.drawPath(p, stroke)
            }
            "rocket" -> {
                canvas.drawLine(0f, r * 0.8f, 0f, -r * 0.7f, stroke)
                val head = Path().apply { moveTo(-r * 0.45f, -r * 0.25f); lineTo(0f, -r * 0.8f); lineTo(r * 0.45f, -r * 0.25f) }
                canvas.drawPath(head, stroke)
                canvas.drawLine(-r * 0.6f, r * 0.8f, r * 0.6f, r * 0.8f, stroke)
            }
            "check" -> {
                canvas.drawCircle(0f, 0f, r * 0.8f, stroke)
                val ck = Path().apply { moveTo(-r * 0.4f, 0f); lineTo(-r * 0.1f, r * 0.3f); lineTo(r * 0.45f, -r * 0.3f) }
                canvas.drawPath(ck, stroke)
            }
            "warning" -> {
                val tri = Path().apply { moveTo(0f, -r * 0.8f); lineTo(r * 0.85f, r * 0.65f); lineTo(-r * 0.85f, r * 0.65f); close() }
                canvas.drawPath(tri, stroke)
                canvas.drawLine(0f, -r * 0.25f, 0f, r * 0.2f, stroke)
                canvas.drawCircle(0f, r * 0.42f, r * 0.07f, fill)
            }
            "globe" -> {
                canvas.drawCircle(0f, 0f, r * 0.8f, stroke)
                canvas.drawOval(RectF(-r * 0.35f, -r * 0.8f, r * 0.35f, r * 0.8f), stroke)
                canvas.drawLine(-r * 0.8f, 0f, r * 0.8f, 0f, stroke)
            }
            "target" -> {
                canvas.drawCircle(0f, 0f, r * 0.85f, stroke)
                canvas.drawCircle(0f, 0f, r * 0.55f, stroke)
                canvas.drawCircle(0f, 0f, r * 0.2f, fill)
            }
            "brain" -> {
                val pts = listOf(-r * 0.6f to -r * 0.3f, -r * 0.6f to r * 0.4f, 0f to -r * 0.6f, 0f to r * 0.1f, r * 0.6f to -r * 0.2f, r * 0.6f to r * 0.5f)
                val edges = listOf(0 to 2, 0 to 3, 1 to 3, 2 to 4, 3 to 4, 3 to 5, 2 to 3)
                edges.forEach { (a, b) -> canvas.drawLine(pts[a].first, pts[a].second, pts[b].first, pts[b].second, stroke) }
                pts.forEach { (x, y) -> canvas.drawCircle(x, y, r * 0.11f, fill) }
            }
            "phone" -> {
                canvas.drawRoundRect(RectF(-r * 0.42f, -r * 0.85f, r * 0.42f, r * 0.85f), r * 0.14f, r * 0.14f, stroke)
                canvas.drawLine(-r * 0.12f, -r * 0.68f, r * 0.12f, -r * 0.68f, stroke)
                canvas.drawCircle(0f, r * 0.68f, r * 0.06f, fill)
            }
            else -> {
                val p = Path()
                for (i in 0 until 10) {
                    val ang = (-Math.PI / 2 + i * Math.PI / 5).toFloat()
                    val rad = if (i % 2 == 0) r * 0.85f else r * 0.38f
                    val px = cos(ang) * rad; val py = sin(ang) * rad
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                canvas.drawPath(p, stroke)
            }
        }
    }

    private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** Text content for the current time: typewriter reveals characters, counter rolls numbers up. */
    private fun textFx(e: Element, localMs: Long, tMs: Long): String {
        val t = localMs.coerceAtLeast(0L)
        return when (e.fx) {
            "typewriter" -> {
                val total = e.text.length
                val n = ((t / 45L).toInt()).coerceIn(0, total)
                e.text.take(n)
            }
            "counter" -> {
                // Rolls the first number in the text from 0 (or values[0]) to its value over ~900 ms, keeping prefix/suffix.
                val m = Regex("""\d[\d,]*(?:\.\d+)?""").find(e.text) ?: return e.text
                val target = m.value.replace(",", "").toFloatOrNull() ?: return e.text
                val from = e.values.firstOrNull() ?: 0f
                val p = ease((t / 900f).coerceIn(0f, 1f), "expoOut")
                val cur = from + (target - from) * p
                val isInt = !m.value.contains('.')
                val shown = if (isInt) Math.round(cur).toString() else String.format(java.util.Locale.US, "%.1f", cur)
                e.text.replaceRange(m.range, shown)
            }
            "scramble" -> {
                // Characters lock in from the left while the rest keeps scrambling: a decode/hacker reveal.
                val total = e.text.length
                val locked = ((t / 38L).toInt()).coerceIn(0, total)
                if (locked >= total) e.text
                else buildString {
                    append(e.text.take(locked))
                    for (i in locked until total) {
                        val c = e.text[i]
                        append(if (c == ' ') ' ' else GLYPHS[((t / 60L + i) % GLYPHS.size).toInt().coerceAtLeast(0)])
                    }
                }
            }
            "karaoke" -> e.text
            else -> e.text
        }
    }

    /** Glyph pool for the scramble reveal. */
    private val GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#@$%&*".toCharArray()

    /** Draws words one after another with a small pop, centred (used by fx = "words"). */
    private fun drawWordsPop(canvas: Canvas, text: String, tp: Paint, maxW: Float, localMs: Long) {
        val words = text.split(' ').filter { it.isNotEmpty() }
        var idx = 0
        val lines = wrap(text, tp, maxW)
        lines.forEachIndexed { li, line ->
            val lw = tp.measureText(line); val space = tp.measureText(" ")
            var x = -lw / 2f
            val a = tp.alpha
            tp.textAlign = Paint.Align.LEFT
            line.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val start = idx * 140L
                val k = ((localMs - start) / 220f).coerceIn(0f, 1f)
                if (k > 0f) {
                    val sc = 0.7f + 0.3f * ease(k, "backOut")
                    canvas.save(); canvas.translate(x + tp.measureText(word) / 2f, li * tp.textSize * 1.2f); canvas.scale(sc, sc)
                    tp.alpha = (a * k).toInt(); tp.textAlign = Paint.Align.CENTER
                    canvas.drawText(word, 0f, 0f, tp); canvas.restore()
                }
                x += tp.measureText(word) + space; idx++
            }
            tp.alpha = a; tp.textAlign = Paint.Align.CENTER
        }
        if (words.isEmpty()) return
    }

    private fun hash(seed: Int, i: Int, k: Int): Float {
        var v = seed * 374761393 + i * 668265263 + k * 2147483647
        v = (v xor (v ushr 13)) * 1274126177
        return ((v xor (v ushr 16)) and 0xFFFF) / 65535f
    }

    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val lines = mutableListOf<String>(); var cur = ""
        for (word in text.split(' ')) {
            val trial = if (cur.isEmpty()) word else "$cur $word"
            if (p.measureText(trial) > maxW && cur.isNotEmpty()) { lines += cur; cur = word } else cur = trial
        }
        if (cur.isNotEmpty()) lines += cur
        return lines
    }

    private fun drawBars(canvas: Canvas, e: Element, w: Float, h: Float, progress: Float, fill: Paint) {
        val vals = e.values.ifEmpty { return }
        val maxV = vals.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val slot = w / vals.size; val bw = slot * 0.62f
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.color; alpha = fill.alpha; textSize = h * 0.07f; textAlign = Paint.Align.CENTER }
        vals.forEachIndexed { i, v ->
            val stagger = ((progress * (vals.size + 1)) - i).coerceIn(0f, 1f)
            val bh = (v / maxV) * h * ease(stagger, "easeOut")
            val left = -w / 2 + slot * i + (slot - bw) / 2
            canvas.drawRoundRect(RectF(left, h / 2 - bh, left + bw, h / 2), bw * 0.12f, bw * 0.12f, fill)
            e.labels.getOrNull(i)?.let { canvas.drawText(it, left + bw / 2, h / 2 + label.textSize * 1.3f, label) }
        }
    }

    private fun drawLineChart(canvas: Canvas, e: Element, w: Float, h: Float, progress: Float, stroke: Paint) {
        val vals = e.values; if (vals.size < 2) return
        val lo = vals.min(); val hi = vals.max(); val span = if (hi - lo < 1e-6f) 1f else hi - lo
        val path = Path()
        val visible = (vals.size - 1) * progress
        for (i in vals.indices) {
            if (i > visible + 1) break
            val px = -w / 2 + w * i / (vals.size - 1)
            val py = h / 2 - (vals[i] - lo) / span * h
            if (i == 0) path.moveTo(px, py) else if (i <= visible) path.lineTo(px, py) else {
                val frac = visible - (i - 1)
                val prevX = -w / 2 + w * (i - 1) / (vals.size - 1); val prevY = h / 2 - (vals[i - 1] - lo) / span * h
                path.lineTo(prevX + (px - prevX) * frac, prevY + (py - prevY) * frac)
            }
        }
        canvas.drawPath(path, stroke)
    }

    private fun drawNodes(canvas: Canvas, e: Element, w: Float, h: Float, progress: Float, fill: Paint, stroke: Paint, tMs: Long) {
        // layers described by `values` = node count per layer; fully connected with signals flowing along edges.
        val layers = e.values.map { it.toInt().coerceIn(1, 8) }.ifEmpty { listOf(3, 4, 4, 2) }
        val pts = layers.mapIndexed { li, n -> (0 until n).map { ni ->
            val px = -w / 2 + w * li / max(1, layers.size - 1)
            val py = if (n == 1) 0f else -h / 2 + h * ni / (n - 1)
            px to py } }
        val line = Paint(stroke).apply { alpha = (stroke.alpha * 0.35f).toInt(); strokeWidth = max(1f, stroke.strokeWidth * 0.5f) }
        val sig = Paint(fill).apply { color = if (e.color2.isNotEmpty()) parseColor(e.color2) else fill.color; alpha = fill.alpha }
        for (li in 0 until pts.size - 1) {
            val layerReveal = ((progress * pts.size) - li).coerceIn(0f, 1f)
            pts[li].forEachIndexed { a, (ax, ay) -> pts[li + 1].forEachIndexed { b, (bx, by) ->
                canvas.drawLine(ax, ay, ax + (bx - ax) * layerReveal, ay + (by - ay) * layerReveal, line)
                if (layerReveal >= 1f) {
                    val phase = ((tMs / 1400f) + hash(e.seed, li * 31 + a, b)) % 1f
                    canvas.drawCircle(ax + (bx - ax) * phase, ay + (by - ay) * phase, min(w, h) * 0.012f, sig)
                }
            } }
        }
        val r = min(w, h) * 0.035f
        pts.forEachIndexed { li, layer -> layer.forEach { (px, py) ->
            val reveal = ((progress * pts.size) - li + 0.5f).coerceIn(0f, 1f)
            canvas.drawCircle(px, py, r * reveal, fill)
        } }
    }
}
