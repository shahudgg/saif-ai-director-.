package com.example.studio.director

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/**
 * ROUND 15.9 — PIXEL PROOF FOR EVERY SCENE.
 *
 * Until now a scene was "verified" only by reading its own JSON. This object actually RENDERS it —
 * three frames, at the real aspect, with the same [SceneRenderer] the preview and the exporter use —
 * and measures what came out:
 *
 *   • **ink**      : how much of the frame differs from the plain background gradient. A scene with
 *                    almost no ink is a blank/broken scene, however good its JSON looks.
 *   • **bottom**   : how much ink sits in the last 8% of the frame (the gesture bar / subtitle band).
 *                    Content there gets cut on a real phone, so it is reported.
 *   • **edge**     : how much ink touches the outer 1% — only a backdrop is allowed to do that.
 *
 * Cheap on purpose: 168×298 pixels per frame, at most three frames per scene, no disk writes. The
 * verdict is a measurement, never a claim, and [SceneReview] adds it to the report it already prints.
 */
object FrameProbe {

    data class Reading(
        val sceneId: String,
        val ink: Float,
        val bottomInk: Float,
        val edgeInk: Float,
        val blank: Boolean,
        val spillsIntoCaption: Boolean
    )

    private const val W = 168
    private const val H = 298

    fun probe(scene: Scene, samples: Int = 3): Reading? {
        val g = scene.graph ?: return null
        val duration = scene.durationMs.coerceAtLeast(600L)
        val bmp = try { Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888) } catch (e: Throwable) { return null }
        var ink = 0f
        var bottom = 0f
        var edge = 0f
        var frames = 0
        val times = (0 until samples).map { duration * (it + 1) / (samples + 1) }
        var previous = Long.MIN_VALUE
        for (t in times) {
            if (t == previous) continue
            previous = t
            try {
                bmp.eraseColor(Color.BLACK)
                SceneRenderer.draw(Canvas(bmp), W, H, g, t, duration, 1f)
                val r = measure(bmp, g)
                ink = maxOf(ink, r.first)
                bottom = maxOf(bottom, r.second)
                edge = maxOf(edge, r.third)
                frames++
            } catch (e: Throwable) {
                // A frame that cannot be drawn is itself a finding.
                break
            }
        }
        if (frames == 0) return Reading(scene.id, 0f, 0f, 0f, blank = true, spillsIntoCaption = false)
        return Reading(
            sceneId = scene.id,
            ink = ink,
            bottomInk = bottom,
            edgeInk = edge,
            blank = ink < 0.035f,
            spillsIntoCaption = bottom > 0.06f
        )
    }

    /** (ink, bottomInk, edgeInk) as fractions of the frame. */
    private fun measure(bmp: Bitmap, g: SceneGraph): Triple<Float, Float, Float> {
        val w = bmp.width
        val h = bmp.height
        var ink = 0
        var bottom = 0
        var edge = 0
        val bottomFrom = (h * 0.92f).toInt()
        val edgePx = 2
        val step = 2
        var total = 0
        var bottomTotal = 0
        var edgeTotal = 0
        for (y in 0 until h step step) {
            // The plain background is a vertical gradient of the scene's two colours.
            val frac = y.toFloat() / (h - 1).coerceAtLeast(1)
            val base = blend(g.bgTop, g.bgBottom, frac)
            val br = Color.red(base); val bg = Color.green(base); val bb = Color.blue(base)
            for (x in 0 until w step step) {
                val p = bmp.getPixel(x, y)
                val d = kotlin.math.abs(Color.red(p) - br) + kotlin.math.abs(Color.green(p) - bg) + kotlin.math.abs(Color.blue(p) - bb)
                val isInk = d > 26
                total++
                if (y >= bottomFrom) bottomTotal++
                if (x < edgePx || x >= w - edgePx || y < edgePx || y >= h - edgePx) edgeTotal++
                if (isInk) {
                    ink++
                    if (y >= bottomFrom) bottom++
                    if (x < edgePx || x >= w - edgePx || y < edgePx || y >= h - edgePx) edge++
                }
            }
        }
        fun ratio(a: Int, b: Int) = if (b <= 0) 0f else a.toFloat() / b
        return Triple(ratio(ink, total), ratio(bottom, bottomTotal), ratio(edge, edgeTotal))
    }

    private fun blend(hexA: String, hexB: String, t: Float): Int {
        val a = parse(hexA); val b = parse(hexB)
        val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), bl.coerceIn(0, 255))
    }

    private fun parse(hex: String): Int = runCatching { Color.parseColor(if (hex.startsWith("#")) hex else "#000000") }.getOrDefault(Color.BLACK)
}
