package com.example.studio.director

/**
 * ROUND 15.5 — GLOBAL SAFE AREA.
 *
 * The owner's words: "kuch bhi screen ke bahar na jaye". Elements are authored in normalised 0..1
 * coordinates, so an element that is slightly off-centre in a 9:16 frame can lose its edge on a phone,
 * and a lower-third can sit under the gesture bar.
 *
 * This object keeps the RESTING position of every element inside a professional safe area:
 *   • 5.5% side margins,
 *   • 7% top margin (status bar / notch band),
 *   • 10% bottom margin (gesture bar + caption band).
 *
 * Only the resting geometry is clamped, never an animation: an entrance that deliberately slides in from
 * outside the frame keeps working, while the pose the element settles on is always fully visible.
 * Full-bleed elements (background rects, scrims, images that cover the frame) are never touched.
 */
object SceneSafeArea {

    const val MARGIN_X = 0.055f
    const val MARGIN_TOP = 0.07f
    const val MARGIN_BOTTOM = 0.10f

    /** A clamped resting box in normalised coordinates (centre based, like [Element]). */
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

    /** True for elements that are SUPPOSED to cover the whole frame (never clamped). */
    fun isFullBleed(e: Element): Boolean {
        if (e.w >= 0.95f && e.h >= 0.95f) return true
        if (e.type == "rect" || e.type == "image" || e.type == "video") return e.w >= 0.9f && e.h >= 0.9f
        return false
    }

    /** The resting box of [e], moved/shrunk only as much as needed to sit inside the safe area. */
    fun restingBox(e: Element): Box {
        val w = e.w.coerceIn(0.01f, 2f)
        val h = e.h.coerceIn(0.01f, 2f)
        if (isFullBleed(e)) return Box(e.x, e.y, w, h)

        val maxW = 1f - 2f * MARGIN_X
        val maxH = 1f - MARGIN_TOP - MARGIN_BOTTOM
        val nw = w.coerceAtMost(maxW)
        val nh = h.coerceAtMost(maxH)
        val halfW = nw / 2f
        val halfH = nh / 2f
        val minX = MARGIN_X + halfW
        val maxX = 1f - MARGIN_X - halfW
        val minY = MARGIN_TOP + halfH
        val maxY = 1f - MARGIN_BOTTOM - halfH
        val nx = if (minX <= maxX) e.x.coerceIn(minX, maxX) else 0.5f
        val ny = if (minY <= maxY) e.y.coerceIn(minY, maxY) else (MARGIN_TOP + (1f - MARGIN_BOTTOM)) / 2f
        return Box(nx, ny, nw, nh)
    }

    /** True when the element already sits fully inside the safe area (used for honest QA reporting). */
    fun inside(e: Element): Boolean {
        if (isFullBleed(e)) return true
        val b = restingBox(e)
        return kotlin.math.abs(b.x - e.x) < 0.0005f && kotlin.math.abs(b.y - e.y) < 0.0005f &&
            kotlin.math.abs(b.w - e.w) < 0.0005f && kotlin.math.abs(b.h - e.h) < 0.0005f
    }

    /** How many elements of a scene had to be pulled back inside the frame (0 = all clean). */
    fun strays(s: Scene): Int = s.graph?.elements?.count { !inside(it) } ?: 0
}
