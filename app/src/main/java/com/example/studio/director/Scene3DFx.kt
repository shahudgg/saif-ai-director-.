package com.example.studio.director

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Canvas-only perspective meshes. The same projected polygons are used for preview and export;
 * all motion is derived solely from the element seed and the scene's local time.
 */
object Scene3DFx {
    val TYPES = setOf("depthStack", "parallax", "extrusion", "perspectiveQuad", "perspectiveCard", "cube")
    fun handles(type: String) = type in TYPES

    fun draw(canvas: Canvas, e: Element, geo: SceneFx.Geo, base: Paint, rotationX: Float, rotationY: Float) {
        if (!geo.ew.isFinite() || !geo.eh.isFinite() || !geo.progress.isFinite() || geo.ew <= 0f || geo.eh <= 0f || geo.progress <= 0f) return
        val g = geo.copy(alpha = geo.alpha * geo.progress.coerceIn(0f, 1f))
        val focal = max(g.minSide * 3.2f, max(g.ew, g.eh) * 1.5f)
        val pitch = (-18f + rotationX).coerceIn(-360f, 360f)
        val yaw = (24f + rotationY).coerceIn(-360f, 360f)
        if (e.shadow > 0f) softShadow(canvas, e, g)
        when (e.type) {
            "cube", "extrusion" -> {
                val thickness = if (e.type == "cube") min(g.ew, g.eh) * (0.55f + e.extrusion * 0.9f)
                    else min(g.ew, g.eh) * (0.08f + e.extrusion * 0.9f)
                val faces = SpatialMath.cube(g.ew, g.eh, thickness, pitch, yaw)
                faces.forEach { face ->
                    val points = face.vertices.map { SpatialMath.project(it, focal) }
                    val faceColor = if (face.name == "front" || e.color2.isBlank()) g.color else g.color2
                    drawFace(canvas, e, g, base, points, faceColor, SpatialMath.light(face.normal),
                        if (face.name == "front") e.text.ifBlank { e.labels.firstOrNull().orEmpty() } else "")
                }
            }
            "perspectiveQuad", "perspectiveCard" -> {
                val points = quad(g.ew, g.eh, 0f, 0f, 0f, pitch, yaw, focal)
                val normal = SpatialMath.rotate(SpatialMath.Vec3(0f, 0f, 1f), pitch, yaw)
                drawFace(canvas, e, g, base, points, g.color, SpatialMath.light(normal), e.text.ifBlank { e.labels.firstOrNull().orEmpty() })
            }
            "depthStack", "parallax" -> {
                val count = (if (e.count > 1) e.count else 4).coerceIn(2, 8)
                val gap = min(g.ew, g.eh) * (0.12f + e.extrusion * 0.48f)
                val wave = sin(g.tMs.coerceAtLeast(0L) / 1800f + e.seed * 0.07f)
                // Far cards first, so overlapping translucent surfaces compose consistently.
                for (index in (count - 1) downTo 0) {
                    val reveal = (g.progress * count - index).coerceIn(0f, 1f)
                    if (reveal <= 0f) continue
                    val offset = index.toFloat() / (count - 1)
                    val dx = if (e.type == "parallax") wave * g.ew * 0.14f * (offset - 0.5f) else offset * g.ew * 0.12f
                    val dy = -offset * g.eh * 0.12f
                    val points = quad(g.ew * 0.83f, g.eh * 0.75f, dx, dy, -index * gap, pitch, yaw, focal)
                    val faceColor = mix(g.color, if (e.color2.isBlank()) Color.WHITE else g.color2, offset * 0.7f)
                    drawFace(canvas, e, g.copy(alpha = g.alpha * reveal), base, points, faceColor, 0.96f - offset * 0.24f,
                        e.labels.getOrNull(index).orEmpty(), g.ew * 0.83f, g.eh * 0.75f)
                }
            }
        }
    }

    private fun quad(w: Float, h: Float, dx: Float, dy: Float, z: Float, pitch: Float, yaw: Float, focal: Float): List<SpatialMath.Projection> =
        listOf(
            SpatialMath.Vec3(-w / 2f + dx, -h / 2f + dy, z), SpatialMath.Vec3(w / 2f + dx, -h / 2f + dy, z),
            SpatialMath.Vec3(w / 2f + dx, h / 2f + dy, z), SpatialMath.Vec3(-w / 2f + dx, h / 2f + dy, z)
        ).map { SpatialMath.project(SpatialMath.rotate(it, pitch, yaw), focal) }

    private fun path(points: List<SpatialMath.Projection>): Path = Path().apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }

    private fun drawFace(
        canvas: Canvas, e: Element, g: SceneFx.Geo, base: Paint, points: List<SpatialMath.Projection>,
        faceColor: Int, light: Float, label: String, sourceW: Float = g.ew, sourceH: Float = g.eh
    ) {
        if (points.size != 4) return
        val shape = path(points)
        val fill = Paint(base).apply {
            color = shade(faceColor, light); alpha = (g.alpha * 255f).toInt().coerceIn(0, 255)
            shader = null; style = Paint.Style.FILL
        }
        canvas.drawPath(shape, fill)
        if (e.specular > 0.001f) highlight(canvas, shape, e, g)
        if (e.rimLight > 0.001f) {
            val edge = Paint(base).apply {
                color = Color.WHITE; alpha = (g.alpha * e.rimLight * 210f).toInt().coerceIn(0, 255)
                shader = null; style = Paint.Style.STROKE; strokeWidth = max(1f, g.minSide * 0.0025f)
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(shape, edge)
        }
        if (label.isNotBlank()) drawLabel(canvas, shape, points, sourceW, sourceH, label, e, g)
    }

    /** Draw text in the plane of the card using a true four-corner homography. */
    private fun drawLabel(canvas: Canvas, shape: Path, points: List<SpatialMath.Projection>, w: Float, h: Float, label: String, e: Element, g: SceneFx.Geo) {
        val source = floatArrayOf(-w / 2f, -h / 2f, w / 2f, -h / 2f, w / 2f, h / 2f, -w / 2f, h / 2f)
        val destination = FloatArray(8)
        points.forEachIndexed { i, point -> destination[2 * i] = point.x; destination[2 * i + 1] = point.y }
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(source, 0, destination, 0, 4)) return
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = (g.alpha * 245f).toInt().coerceIn(0, 255)
            textAlign = Paint.Align.CENTER; textSize = max(6f, min(h * 0.2f, g.minSide * 0.045f))
            typeface = SceneFx.typefaceFor(e.weight)
        }
        canvas.save()
        canvas.clipPath(shape)
        canvas.concat(matrix)
        val lines = SceneFx.wrap(label.take(70), textPaint, w * 0.8f).take(3)
        val startY = -(lines.size - 1) * textPaint.textSize * 0.55f + textPaint.textSize * 0.35f
        lines.forEachIndexed { index, line -> canvas.drawText(line, 0f, startY + index * textPaint.textSize * 1.1f, textPaint) }
        canvas.restore()
    }

    private fun highlight(canvas: Canvas, shape: Path, e: Element, g: SceneFx.Geo) {
        val bounds = RectF()
        shape.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val middle = bounds.centerX() + SpatialMath.specularPosition(e.seed, g.tMs) * bounds.width() * 0.7f
        val band = max(1f, bounds.width() * 0.18f)
        val white = (g.alpha * e.specular * 145f).toInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(middle - band, bounds.top, middle + band, bounds.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(white, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        canvas.save(); canvas.clipPath(shape); canvas.drawRect(bounds, paint); canvas.restore()
    }

    private fun softShadow(canvas: Canvas, e: Element, g: SceneFx.Geo) {
        val radius = max(g.ew, g.eh) * 0.72f
        val alpha = (g.alpha * e.shadow * 160f).toInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, radius, intArrayOf(Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        canvas.save(); canvas.translate(g.ew * 0.07f, g.eh * 0.57f); canvas.scale(1f, 0.27f)
        canvas.drawCircle(0f, 0f, radius, paint); canvas.restore()
    }

    private fun shade(c: Int, light: Float): Int = Color.argb(Color.alpha(c),
        (Color.red(c) * light).toInt().coerceIn(0, 255), (Color.green(c) * light).toInt().coerceIn(0, 255),
        (Color.blue(c) * light).toInt().coerceIn(0, 255))

    private fun mix(a: Int, b: Int, amount: Float): Int {
        val f = amount.coerceIn(0f, 1f)
        return Color.argb(255, (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())
    }
}
