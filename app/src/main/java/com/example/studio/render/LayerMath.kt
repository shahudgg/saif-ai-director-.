package com.example.studio.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure mathematical functions for timeline layout, zooming, snapping, and animations.
 * Completely unit-tested, zero Android UI dependencies for maximum speed and determinism.
 */
object LayerMath {

    const val MIN_ZOOM_DP_PER_SEC: Float = 2.0f
    const val MAX_ZOOM_DP_PER_SEC: Float = 1200.0f
    const val DEFAULT_ZOOM_DP_PER_SEC: Float = 60.0f
    const val SNAP_THRESHOLD_DP: Float = 14.0f

    /**
     * Converts time in microseconds to horizontal pixel position on canvas.
     */
    fun timeUsToPixel(timeUs: Long, zoomDpPerSec: Float, density: Float): Float {
        val seconds = timeUs / 1_000_000.0
        val dp = seconds * zoomDpPerSec
        return (dp * density).toFloat()
    }

    /**
     * Converts pixel position on timeline canvas to time in microseconds.
     */
    fun pixelToTimeUs(pixel: Float, zoomDpPerSec: Float, density: Float): Long {
        if (zoomDpPerSec <= 0f || density <= 0f) return 0L
        val dp = pixel / density
        val seconds = dp / zoomDpPerSec
        return (seconds * 1_000_000L).toLong().coerceAtLeast(0L)
    }

    /**
     * Quantizes time to discrete video frame intervals (e.g. 1/30s or 1/60s).
     */
    fun quantizeToFrame(timeUs: Long, fps: Int = 30): Long {
        if (fps <= 0) return timeUs
        val frameDurationUs = 1_000_000L / fps
        val frameIndex = (timeUs + (frameDurationUs / 2)) / frameDurationUs
        return frameIndex * frameDurationUs
    }

    /**
     * Formats microseconds into accurate display time string: mm:ss.S or ss.SS
     */
    fun formatTimeDisplay(timeUs: Long): String {
        val totalMs = timeUs / 1000L
        val minutes = totalMs / 60000L
        val seconds = (totalMs % 60000L) / 1000L
        val fraction = (totalMs % 1000L) / 100L
        return "%02d:%02d.%d".format(minutes, seconds, fraction)
    }

    /**
     * Calculates the nearest snap point for a given target timestamp.
     * Snaps to item start/end boundaries, playhead, and markers if within threshold.
     */
    fun findSnapPoint(
        targetUs: Long,
        snapTargets: List<Long>,
        zoomDpPerSec: Float,
        density: Float,
        snapThresholdDp: Float = SNAP_THRESHOLD_DP
    ): SnapResult {
        val thresholdUs = ((snapThresholdDp / zoomDpPerSec) * 1_000_000L).toLong()
        var closestTarget: Long? = null
        var minDiff = Long.MAX_VALUE

        for (target in snapTargets) {
            val diff = kotlin.math.abs(target - targetUs)
            if (diff <= thresholdUs && diff < minDiff) {
                minDiff = diff
                closestTarget = target
            }
        }

        return if (closestTarget != null) {
            SnapResult(isSnapped = true, snappedTimeUs = closestTarget)
        } else {
            SnapResult(isSnapped = false, snappedTimeUs = targetUs)
        }
    }

    /**
     * Calculates adaptive major and minor tick intervals for timeline ruler based on zoom level.
     */
    fun calculateRulerIntervals(zoomDpPerSec: Float): RulerIntervals {
        return when {
            zoomDpPerSec >= 400f -> RulerIntervals(majorStepUs = 100_000L, minorStepUs = 33_333L, label = "Frames")
            zoomDpPerSec >= 150f -> RulerIntervals(majorStepUs = 500_000L, minorStepUs = 100_000L, label = "0.1s")
            zoomDpPerSec >= 60f -> RulerIntervals(majorStepUs = 1_000_000L, minorStepUs = 200_000L, label = "0.5s")
            zoomDpPerSec >= 20f -> RulerIntervals(majorStepUs = 5_000_000L, minorStepUs = 1_000_000L, label = "1s")
            zoomDpPerSec >= 8f -> RulerIntervals(majorStepUs = 10_000_000L, minorStepUs = 2_000_000L, label = "5s")
            else -> RulerIntervals(majorStepUs = 30_000_000L, minorStepUs = 5_000_000L, label = "10s")
        }
    }

    /**
     * Easing function calculation for animations and keyframes.
     * Progress t is in range [0f, 1f].
     */
    fun evaluateEasing(t: Float, type: String): Float {
        val clamped = t.coerceIn(0f, 1f)
        return when (type.lowercase().replace(" ", "").replace("_", "")) {
            "easeoutback" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * ((clamped - 1f) * (clamped - 1f) * (clamped - 1f)) + c1 * ((clamped - 1f) * (clamped - 1f))
            }
            "easeinoutquad", "easeinout" -> {
                if (clamped < 0.5f) 2f * clamped * clamped
                else 1f - ((-2f * clamped + 2f) * (-2f * clamped + 2f)) / 2f
            }
            "easeinquad", "easein" -> clamped * clamped
            "easeoutquad", "easeout" -> 1f - (1f - clamped) * (1f - clamped)
            "easeincubic" -> clamped * clamped * clamped
            "easeoutcubic" -> 1f - (1f - clamped) * (1f - clamped) * (1f - clamped)
            "easeinoutcubic", "cubiceaseinout" -> {
                if (clamped < 0.5f) 4f * clamped * clamped * clamped
                else 1f - ((-2f * clamped + 2f) * (-2f * clamped + 2f) * (-2f * clamped + 2f)) / 2f
            }
            "elastic" -> {
                if (clamped == 0f || clamped == 1f) clamped
                else {
                    val p = 0.3f
                    val s = p / 4f
                    val postFix = Math.pow(2.0, (10 * (clamped - 1)).toDouble()).toFloat()
                    -(postFix * sin((clamped - 1 - s) * (2 * PI.toFloat()) / p))
                }
            }
            "bounce" -> {
                var c = clamped
                val n1 = 7.5625f
                val d1 = 2.75f
                if (c < 1 / d1) {
                    n1 * c * c
                } else if (c < 2 / d1) {
                    c -= 1.5f / d1
                    n1 * c * c + 0.75f
                } else if (c < 2.5 / d1) {
                    c -= 2.25f / d1
                    n1 * c * c + 0.9375f
                } else {
                    c -= 2.625f / d1
                    n1 * c * c + 0.984375f
                }
            }
            "beziercubic" -> solveCubicBezier(0.25f, 0.1f, 0.25f, 1.0f, clamped)
            "linear" -> clamped
            else -> clamped
        }
    }

    /**
     * Solves a cubic bezier curve for a given x in [0, 1] using bisection method.
     */
    fun solveCubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, xTarget: Float): Float {
        val target = xTarget.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        var t = target

        for (i in 0 until 12) {
            val currentX = 3f * (1f - t) * (1f - t) * t * x1 + 3f * (1f - t) * t * t * x2 + t * t * t
            if (kotlin.math.abs(currentX - target) < 0.001f) break
            if (currentX < target) {
                low = t
            } else {
                high = t
            }
            t = (low + high) / 2f
        }

        // Return y at t
        return (3f * (1f - t) * (1f - t) * t * y1 + 3f * (1f - t) * t * t * y2 + t * t * t).coerceIn(0f, 1f)
    }

    /**
     * Interpolates instantaneous speed multiplier at normalized progress t in [0, 1].
     */
    fun evaluateSpeedRampVelocity(points: List<Pair<Float, Float>>, t: Float): Float {
        if (points.isEmpty()) return 1.0f
        if (points.size == 1) return points.first().second
        val clampedT = t.coerceIn(0f, 1f)

        if (clampedT <= points.first().first) return points.first().second
        if (clampedT >= points.last().first) return points.last().second

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if (clampedT in p1.first..p2.first) {
                val span = p2.first - p1.first
                val factor = if (span > 0f) (clampedT - p1.first) / span else 0f
                val eased = evaluateEasing(factor, "easeinoutquad")
                return p1.second + (p2.second - p1.second) * eased
            }
        }
        return 1.0f
    }

    /**
     * Calculates the cumulative source progress (0.0 to 1.0) for a given output progress t in [0, 1]
     * under a speed ramp velocity curve via numerical integration.
     */
    fun calculateSpeedRampProgress(points: List<Pair<Float, Float>>, t: Float, sampleCount: Int = 100): Float {
        if (points.isEmpty() || t <= 0f) return 0f
        if (t >= 1f) return 1f

        val dt = 1.0f / sampleCount
        var totalArea = 0f
        var targetArea = 0f

        for (i in 0 until sampleCount) {
            val currentT = i * dt
            val v = evaluateSpeedRampVelocity(points, currentT)
            val areaSlice = v * dt
            totalArea += areaSlice
            if (currentT < t) {
                targetArea += areaSlice
            }
        }

        return if (totalArea > 0f) (targetArea / totalArea).coerceIn(0f, 1f) else t
    }

    /**
     * Calculates the resulting duration when a speed ramp preset is applied to footage.
     */
    fun calculateSpeedRampDuration(originalDurationSeconds: Float, points: List<Pair<Float, Float>>): Float {
        if (points.isEmpty()) return originalDurationSeconds
        val sampleCount = 50
        val dt = 1.0f / sampleCount
        var avgSpeed = 0f
        for (i in 0 until sampleCount) {
            avgSpeed += evaluateSpeedRampVelocity(points, i * dt) * dt
        }
        return if (avgSpeed > 0f) (originalDurationSeconds / avgSpeed).coerceAtLeast(0.2f) else originalDurationSeconds
    }

    /**
     * Level 4 Cinematic Transition Math: calculates alpha, scale, and translation offset
     * for incoming or outgoing clip during transition phase (progress 0f to 1f).
     */
    fun calculateTransitionFrame(
        category: String,
        progress: Float,
        isOutgoing: Boolean
    ): TransitionFrameResult {
        val p = progress.coerceIn(0f, 1f)
        return when (category.lowercase().trim()) {
            "whip", "whippan" -> {
                val offset = if (isOutgoing) -p * 1000f else (1f - p) * 1000f
                val alpha = if (isOutgoing) 1f - p * 0.5f else p
                TransitionFrameResult(alpha = alpha, offsetX = offset, scale = 1.0f)
            }
            "glitch", "cyberpunk" -> {
                val glitchFactor = sin(p * 20f * PI.toFloat())
                val alpha = if (isOutgoing) 1f - p else p
                val offsetX = if (p in 0.3f..0.7f) glitchFactor * 30f else 0f
                TransitionFrameResult(alpha = alpha, offsetX = offsetX, scale = 1.0f + (p * 0.05f))
            }
            "warp", "spatialwarp" -> {
                val scale = if (isOutgoing) 1.0f + (p * 0.8f) else 0.5f + (p * 0.5f)
                val alpha = if (isOutgoing) 1f - evaluateEasing(p, "easeinquad") else evaluateEasing(p, "easeoutquad")
                TransitionFrameResult(alpha = alpha, scale = scale)
            }
            "light", "lightleak" -> {
                val flash = sin(p * PI.toFloat()) * 0.8f
                val alpha = if (isOutgoing) (1f - p).coerceAtLeast(0f) else p
                TransitionFrameResult(alpha = alpha, brightnessBoost = flash)
            }
            "3d", "pageflip" -> {
                val rotationY = if (isOutgoing) -p * 90f else (1f - p) * 90f
                val alpha = if (isOutgoing) 1f - p else p
                TransitionFrameResult(alpha = alpha, rotationY = rotationY)
            }
            else -> { // Cross Dissolve
                val alpha = if (isOutgoing) 1f - p else p
                TransitionFrameResult(alpha = alpha)
            }
        }
    }
    /**
     * Interpolates item transform at a given timestamp using defined keyframes.
     * If no keyframes are defined, returns the static baseTransform.
     */
    fun interpolateTransform(
        baseTransform: com.example.studio.model.ItemTransform,
        keyframes: List<com.example.studio.model.TransformKeyframe>,
        tUs: Long
    ): com.example.studio.model.ItemTransform {
        if (keyframes.isEmpty()) return baseTransform
        if (keyframes.size == 1) {
            val k = keyframes.first()
            return baseTransform.copy(x = k.x, y = k.y, scale = k.scale, rotation = k.rotation, opacity = k.opacity)
        }

        // Before first keyframe
        if (tUs <= keyframes.first().timeUs) {
            val k = keyframes.first()
            return baseTransform.copy(x = k.x, y = k.y, scale = k.scale, rotation = k.rotation, opacity = k.opacity)
        }
        // After last keyframe
        if (tUs >= keyframes.last().timeUs) {
            val k = keyframes.last()
            return baseTransform.copy(x = k.x, y = k.y, scale = k.scale, rotation = k.rotation, opacity = k.opacity)
        }

        // Find surrounding keyframe pair
        for (i in 0 until keyframes.size - 1) {
            val k1 = keyframes[i]
            val k2 = keyframes[i + 1]
            if (tUs in k1.timeUs..k2.timeUs) {
                val span = (k2.timeUs - k1.timeUs).toFloat()
                val ratio = if (span > 0) ((tUs - k1.timeUs) / span).coerceIn(0f, 1f) else 0f
                val eased = evaluateEasing(ratio, "easeinoutquad")

                return baseTransform.copy(
                    x = k1.x + (k2.x - k1.x) * eased,
                    y = k1.y + (k2.y - k1.y) * eased,
                    scale = k1.scale + (k2.scale - k1.scale) * eased,
                    rotation = k1.rotation + (k2.rotation - k1.rotation) * eased,
                    opacity = (k1.opacity + (k2.opacity - k1.opacity) * eased).coerceIn(0f, 1f)
                )
            }
        }
        return baseTransform
    }
}

data class SnapResult(
    val isSnapped: Boolean,
    val snappedTimeUs: Long
)

data class RulerIntervals(
    val majorStepUs: Long,
    val minorStepUs: Long,
    val label: String
)

data class TransitionFrameResult(
    val alpha: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1.0f,
    val rotationY: Float = 0f,
    val brightnessBoost: Float = 0f
)
