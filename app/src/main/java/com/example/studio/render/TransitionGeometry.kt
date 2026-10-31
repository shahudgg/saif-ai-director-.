package com.example.studio.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pure geometry for seekable transitions; coordinates are normalised to the frame. */
object TransitionGeometry {
    fun ease(progress: Float): Float {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        return p * p * (3f - 2f * p)
    }
    /** Four corners in top-left, top-right, bottom-right, bottom-left order. */
    fun pageFlip(progress: Float): FloatArray {
        val p = ease(progress)
        val theta = (1f - p) * (PI / 2).toFloat()
        val width = cos(theta).coerceIn(0f, 1f)
        val shrink = 1f / (1f + .55f * sin(theta))
        val top = (1f - shrink) / 2f
        return floatArrayOf(0f, 0f, width, top, width, 1f - top, 0f, 1f)
    }
    fun cubeSpin(progress: Float): FloatArray {
        val p = ease(progress)
        val theta = (1f - p) * (PI / 2).toFloat()
        val left = 1f - cos(theta).coerceIn(0f, 1f)
        val shrink = 1f / (1f + .7f * sin(theta))
        val top = (1f - shrink) / 2f
        return floatArrayOf(left, top, 1f, 0f, 1f, 1f, left, 1f - top)
    }
    fun depthWipe(progress: Float): FloatArray {
        val p = ease(progress)
        // A diagonally sloped leading edge crosses the entire frame; endpoints cover exactly zero / all.
        val edgeTop = (p * 1.25f).coerceIn(0f, 1f)
        val edgeBottom = (p * 1.25f - .25f).coerceIn(0f, 1f)
        return floatArrayOf(0f, 0f, edgeTop, 0f, edgeBottom, 1f, 0f, 1f)
    }
    /** Deterministic progress of an individual glitch strip. Monotonic, with exact zero and one endpoints. */
    fun glitchStrip(progress: Float, seed: Int, strip: Int): Float {
        var n = seed xor (strip * -1640531527)
        n = (n xor (n ushr 16)) * -2048144789
        n = (n xor (n ushr 13)) * -1028477387
        val threshold = ((n xor (n ushr 16)) ushr 8).toFloat() / 16777215f * .48f
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        return ((p - threshold) / (1f - threshold)).coerceIn(0f, 1f)
    }
}
