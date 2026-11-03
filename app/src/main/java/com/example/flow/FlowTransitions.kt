package com.example.flow

import com.example.studio.director.AssetPlacement
import com.example.studio.director.AssetPlacements
import com.example.studio.director.PlacementKey
import kotlin.math.abs

/**
 * Round 14 — TRANSITION + MOTION PLANNING FROM THE REAL PIXELS.
 *
 * Once every picture is downloaded and measured, the agent plans how the video cuts between them:
 * a bright→dark jump becomes a flash, two similar palettes become a dissolve, a big colour jump becomes
 * a whip pan, a very detailed frame becomes a zoom-blur push, a long calm scene gets a slow push.
 * Nothing is random: the same pictures always produce the same edit, and every transition name exists in
 * `SUPPORTED_TRANSITIONS`, so the renderer really plays it.
 */
object FlowTransitions {

    private val SAFE = setOf(
        "fade", "dissolve", "whip", "flash", "zoomBlur", "slideLeft", "slideRight", "slideUp",
        "push", "wipe", "iris", "zoom", "glitchDissolve", "depthWipe", "cubeSpin", "spin"
    )

    /** Planned transition INTO scene [toIndex] (from the previous scene's picture). */
    fun between(from: FlowSlot?, to: FlowSlot, toIndex: Int, toDurationMs: Long): FlowTransition {
        val a = from?.review
        val b = to.review
        if (a == null || b == null) {
            val fallback = if (toIndex % 2 == 0) "fade" else "slideUp"
            return FlowTransition(toIndex - 1, toIndex, fallback, 350L, "no measurements — safe $fallback")
        }
        val paletteJump = colorDistance(a.paletteHex, b.paletteHex)
        val brightnessJump = abs(a.brightness - b.brightness)
        val type: String
        val reason: String
        when {
            brightnessJump > 0.42f -> { type = "flash"; reason = "big brightness jump (${(brightnessJump * 100).toInt()}%)" }
            paletteJump > 1.05f -> { type = "whip"; reason = "very different palette" }
            paletteJump > 0.55f -> { type = "glitchDissolve"; reason = "different palette + texture" }
            a.sharpness > 0.55f && b.sharpness > 0.5f -> { type = "zoomBlur"; reason = "both frames very detailed" }
            toDurationMs > 6000L -> { type = "slideUp"; reason = "long calm scene — slow push in" }
            paletteJump < 0.18f -> { type = "dissolve"; reason = "matching palettes" }
            toIndex % 4 == 0 -> { type = "iris"; reason = "rhythm accent every 4th cut" }
            toIndex % 3 == 0 -> { type = "wipe"; reason = "rhythm accent every 3rd cut" }
            else -> { type = "push"; reason = "clean cut for this pace" }
        }
        val safe = if (type in SAFE) type else "fade"
        val ms = (toDurationMs / 5L).coerceIn(220L, 600L)
        return FlowTransition(toIndex - 1, toIndex, safe, ms, reason)
    }

    /** Plans every cut after the pictures are verified. [sceneDurationsMs] is index-aligned with [ordered]. */
    fun plan(ordered: List<FlowSlot>, sceneDurationsMs: List<Long>): List<FlowTransition> {
        val out = ArrayList<FlowTransition>()
        for (i in ordered.indices) {
            if (i == 0) {
                out.add(FlowTransition(-1, ordered[i].index, "fade", 420L, "video opens on the hook picture"))
                continue
            }
            val dur = sceneDurationsMs.getOrElse(i) { 4000L }
            out.add(between(ordered[i - 1], ordered[i], i, dur))
        }
        return out
    }

    /** Entrance animation that matches the planned cut, so image motion and scene motion do not fight. */
    fun animFor(type: String, index: Int): String = when (type) {
        "flash" -> "fadeIn"
        "whip" -> "slideRight"
        "push", "slideLeft" -> "slideLeft"
        "slideUp" -> "slideUp"
        "zoomBlur", "zoom" -> "zoomIn"
        "iris", "wipe" -> "popIn"
        "glitchDissolve", "dissolve" -> "blurIn"
        "cubeSpin", "depthWipe" -> "flipIn"
        else -> if (index % 2 == 0) "fadeIn" else "zoomIn"
    }

    private fun colorDistance(a: String, b: String): Float {
        val ca = rgb(a) ?: return 0.5f
        val cb = rgb(b) ?: return 0.5f
        return (abs(ca[0] - cb[0]) + abs(ca[1] - cb[1]) + abs(ca[2] - cb[2])) / 255f
    }

    private fun rgb(hex: String): IntArray? = runCatching {
        val h = hex.removePrefix("#")
        if (h.length < 6) return null
        intArrayOf(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    }.getOrNull()
}

/**
 * Turns a verified slot into a real [AssetPlacement] for the timeline: full-frame backdrop for
 * backgrounds, a floating sticker for cut-outs, with the measured crop nudge, glow, grade, drift and
 * entrance/exit. All normalized, all still editable in the Tune inspector.
 */
object FlowPlacement {

    /**
     * Round 16 — the slot's OWN plan decides where it lands.
     *
     * A background plate is always full frame. An overlay/asset slot carries the target box, motion name and
     * (when the local cut-out worked) a transparent PNG, so the floating layer lands exactly where the layer
     * planner put it instead of in a guessed corner. Old single-layer plans still work: they fall back to the
     * measured crop/glow/drift logic of Round 14.
     */
    fun forSlot(slot: FlowSlot, sceneDurationMs: Long, transitionType: String): AssetPlacement {
        val r = slot.review ?: FlowReview()
        val planned = slot.layer.isNotBlank()
        val floating = if (planned) {
            slot.layer == FlowLayerKind.OVERLAY || slot.layer == FlowLayerKind.ASSET
        } else {
            slot.role == FlowRoles.STICKER || (slot.role == FlowRoles.KEYFRAME && (r.cornerUniform || slot.removeBg))
        }
        val cutout = slot.cutPath.isNotBlank()
        val base = if (floating) {
            AssetPlacement(
                x = slot.targetX.coerceIn(0.05f, 0.95f),
                y = slot.targetY.coerceIn(0.05f, 0.95f),
                width = slot.targetW.coerceIn(0.2f, 0.9f),
                height = slot.targetH.coerceIn(0.15f, 0.9f),
                fit = "contain",
                role = "overlay",
                shadow = if (cutout) 0.20f else 0.26f,
                corner = 0f
            )
        } else {
            AssetPlacement(
                x = 0.5f + r.cropX * 0.5f, y = 0.5f + r.cropY * 0.5f,
                width = 1f, height = 1f, fit = r.fit, role = "backdrop"
            )
        }
        val withLook = base.copy(
            glow = if (r.needsGlow) r.glowStrength else if (floating) 0.22f else 0.05f,
            glowColor = glowColorFor(r.paletteHex),
            brightness = if (r.brightness < 0.3f) 1.12f else 1f,
            contrast = if (r.contrast < 0.18f) 1.10f else 1f,
            saturation = if (r.brightness < 0.3f) 1.06f else 1f,
            vignette = if (floating) 0.04f else if (r.brightness > 0.72f) 0.20f else 0.08f,
            // A real transparent PNG never needs a key colour; only fakes do.
            chromaKey = !cutout && !floating && r.needsBgRemove,
            drift = if (floating) 0.10f else r.drift,
            animIn = animForMotion(slot.motion, transitionType, slot.index, floating),
            animInMs = if (floating) 460L else 420L,
            animOut = "fadeOut",
            animOutMs = 240L,
            keyframes = if (floating) overlayKeys(slot, sceneDurationMs) else kenBurns(slot, sceneDurationMs, r, false)
        )
        return AssetPlacements.clamp(withLook)
    }

    /** Entrance animation: the planned motion name first, then the transition-based fallback. */
    private fun animForMotion(motion: String, transitionType: String, index: Int, floating: Boolean): String {
        return when (motion) {
            "floatUp", "swaySlow" -> "slideUp"
            "orbitSmall", "popDrift" -> "popIn"
            "parallaxLeft" -> "slideRight"
            "parallaxRight" -> "slideLeft"
            else -> if (floating) "popIn" else FlowTransitions.animFor(transitionType, index)
        }
    }

    /**
     * Keyframe track for a floating layer. Times are LOCAL to the clip (the assembler adds the scene start),
     * and every value is inside the frame so the layer can never drift off-screen.
     */
    private fun overlayKeys(slot: FlowSlot, sceneDurationMs: Long): List<PlacementKey> {
        if (sceneDurationMs < 900L) return emptyList()
        val x = slot.targetX.coerceIn(0.08f, 0.92f)
        val y = slot.targetY.coerceIn(0.08f, 0.92f)
        val end = (sceneDurationMs - 160L).coerceAtLeast(320L)
        return when (slot.motion) {
            "floatUp" -> listOf(
                PlacementKey(0L, x, (y + 0.06f).coerceAtMost(0.94f), 0.96f, 0f, 0.85f, "easeOut"),
                PlacementKey(end / 2L, x, y, 1.0f, 0f, 1f, "easeInOut"),
                PlacementKey(end, x, (y - 0.03f).coerceAtLeast(0.06f), 1.03f, 0f, 1f, "linear")
            )
            "orbitSmall" -> listOf(
                PlacementKey(0L, (x - 0.015f).coerceAtLeast(0.06f), y, 1.0f, -1.5f, 1f, "easeInOut"),
                PlacementKey(end / 2L, (x + 0.015f).coerceAtMost(0.94f), (y - 0.012f).coerceAtLeast(0.06f), 1.02f, 1.5f, 1f, "easeInOut"),
                PlacementKey(end, (x - 0.01f).coerceAtLeast(0.06f), y, 1.0f, -1f, 1f, "easeInOut")
            )
            "parallaxLeft", "parallaxRight" -> {
                val dir = if (slot.motion == "parallaxLeft") -1f else 1f
                listOf(
                    PlacementKey(0L, (x + 0.03f * dir).coerceIn(0.06f, 0.94f), y, 1.04f, 0f, 1f, "easeOut"),
                    PlacementKey(end, (x - 0.03f * dir).coerceIn(0.06f, 0.94f), y, 1.0f, 0f, 1f, "linear")
                )
            }
            "popDrift" -> listOf(
                PlacementKey(0L, x, y, 0.94f, 0f, 0.9f, "backOut"),
                PlacementKey((end * 22L) / 100L, x, y, 1.05f, 0f, 1f, "easeOut"),
                PlacementKey(end, x, (y - 0.02f).coerceAtLeast(0.06f), 1.02f, 0f, 1f, "linear")
            )
            "swaySlow" -> listOf(
                PlacementKey(0L, x, y, 1.0f, -2.5f, 1f, "easeInOut"),
                PlacementKey(end / 2L, x, y, 1.02f, 2.5f, 1f, "easeInOut"),
                PlacementKey(end, x, y, 1.0f, -1.5f, 1f, "easeInOut")
            )
            else -> listOf(
                PlacementKey(0L, x, y, 0.98f, 0f, 0.9f, "backOut"),
                PlacementKey(end, x, y, 1.02f, 0f, 1f, "linear")
            )
        }
    }

    /**
     * Slow Ken-Burns motion measured from the frame: push towards the focal point, then settle.
     * Keyframe times are LOCAL to the clip (the assembler adds the scene start).
     */
    private fun kenBurns(slot: FlowSlot, sceneDurationMs: Long, r: FlowReview, sticker: Boolean): List<PlacementKey> {
        if (sceneDurationMs < 1200L) return emptyList()
        val end = (sceneDurationMs - 150L).coerceAtLeast(300L)
        val sx = 0.5f + r.cropX * 0.5f
        val sy = 0.5f + r.cropY * 0.5f
        val zoom = if (sticker) 1.02f else 1.06f
        return listOf(
            PlacementKey(tMs = 0L, x = sx, y = sy, scale = 1f, opacity = 1f, easing = "easeOut"),
            PlacementKey(tMs = end / 2L, x = 0.5f, y = 0.5f, scale = (1f + (zoom - 1f) / 2f), opacity = 1f, easing = "easeInOut"),
            PlacementKey(tMs = end, x = 0.5f + (sx - 0.5f) * 0.4f, y = 0.5f + (sy - 0.5f) * 0.4f, scale = zoom, opacity = 1f, easing = "linear")
        )
    }

    private fun glowColorFor(paletteHex: String): String {
        val h = paletteHex.removePrefix("#")
        if (h.length < 6) return "#00E5FF"
        val r = h.substring(0, 2).toIntOrNull(16) ?: return "#00E5FF"
        val g = h.substring(2, 4).toIntOrNull(16) ?: return "#00E5FF"
        val b = h.substring(4, 6).toIntOrNull(16) ?: return "#00E5FF"
        // Complementary neon rim keeps the picture readable against itself.
        return String.format(java.util.Locale.US, "#%02X%02X%02X", 255 - r, 255 - g, 255 - b)
    }
}
