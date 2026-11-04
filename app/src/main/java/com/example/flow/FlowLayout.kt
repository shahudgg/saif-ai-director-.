package com.example.flow

import org.json.JSONObject

/**
 * ROUND 15.4 — PER-SLOT LAYOUT PLAN.
 *
 * The owner's complaint: pictures were generated but landed wherever the generic placement put them, so
 * overlays could sit on the subject, a sticker could hang off the edge of the frame, and there was no
 * written plan saying "this asset goes here, at this size, with this motion, for this long".
 *
 * This file is that plan. For every slot (one generated picture) it decides, BEFORE the picture is used:
 *   - which role it plays (backdrop / floating keyframe / small sticker),
 *   - the exact rectangle (normalised 0..1, centre based) it occupies,
 *   - the fit mode (cover keeps the frame full, contain keeps the whole picture visible),
 *   - the depth (z-order) it sits at,
 *   - the motion (push-in, pan, parallax, float, pop),
 *   - the time window inside the scene.
 *
 * Two guarantees are enforced here and re-checked by [FlowLayoutPlanner.fitsInside]:
 *   1. a non-backdrop asset NEVER leaves the safe area (nothing is cut off at the screen edge),
 *   2. a backdrop ALWAYS covers the frame (no empty border, no letterbox).
 *
 * The plan is stored on the slot itself (see [FlowSlot.layout]) the moment the picture is verified, so the
 * assembly step uses the same numbers that were planned — not a second, unrelated guess.
 */
data class SlotLayout(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val w: Float = 1f,
    val h: Float = 1f,
    val fit: String = "cover",
    val z: Int = 0,
    val motion: String = "push-in",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val note: String = ""
) {
    val isBackdrop: Boolean get() = z <= 0 && w >= 0.98f && h >= 0.98f

    fun toJson(): String = JSONObject().apply {
        put("x", x.toDouble()); put("y", y.toDouble()); put("w", w.toDouble()); put("h", h.toDouble())
        put("fit", fit); put("z", z); put("motion", motion)
        put("start", startMs); put("end", endMs); put("note", note)
    }.toString()

    companion object {
        fun fromJson(raw: String): SlotLayout? {
            if (raw.isBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                SlotLayout(
                    x = o.optDouble("x", 0.5).toFloat(),
                    y = o.optDouble("y", 0.5).toFloat(),
                    w = o.optDouble("w", 1.0).toFloat(),
                    h = o.optDouble("h", 1.0).toFloat(),
                    fit = o.optString("fit", "cover"),
                    z = o.optInt("z", 0),
                    motion = o.optString("motion", "push-in"),
                    startMs = o.optLong("start", 0L),
                    endMs = o.optLong("end", 0L),
                    note = o.optString("note", "")
                )
            }.getOrNull()
        }
    }
}

object FlowLayoutPlanner {

    /** Safe margins: nothing important is allowed within these bands (phones crop the very edge). */
    const val SAFE_X = 0.06f
    const val SAFE_TOP = 0.08f
    const val SAFE_BOTTOM = 0.90f

    private val MOTIONS_BG = listOf("push-in", "pan-left", "parallax-up", "ken-burns")
    private val MOTIONS_FLOAT = listOf("float", "drift-up", "sway")

    /**
     * Decide the layout for one generated picture.
     *
     * @param slot the picture's plan (role decided by the prompt builder),
     * @param sceneMs how long the scene lasts (the layout time window),
     * @param aspect the video aspect (a wide frame gets wider stickers, a tall frame gets taller ones),
     * @param index slot index — used only for deterministic variety (never for randomness).
     */
    fun plan(slot: FlowSlot, sceneMs: Long, aspect: String, index: Int = slot.index): SlotLayout {
        val r = slot.review
        val cut = slot.cutPath.isNotBlank()
        val tall = !aspect.startsWith("16:9")
        val start = 0L
        val end = (sceneMs - 120L).coerceAtLeast(400L)
        val seed = (index * 37 + slot.sceneId.hashCode()) and 0x7FFF

        // A real transparent cut-out always floats; a flat-corner keyframe also floats; else backdrop.
        val floating = cut || slot.role == FlowRoles.STICKER ||
            (slot.role == FlowRoles.KEYFRAME && (r?.cornerUniform == true || r?.needsBgRemove == true))

        if (!floating) {
            // Backdrop: covers the whole frame, focal point from the measured pixels, slow confident motion.
            return SlotLayout(
                x = 0.5f, y = 0.5f, w = 1f, h = 1f,
                fit = if (r?.fit == "contain") "contain" else "cover",
                z = 0,
                motion = MOTIONS_BG[seed % MOTIONS_BG.size],
                startMs = start, endMs = end,
                note = "backdrop · poora frame cover"
            )
        }

        // Floating layer: size from the role, then pushed INSIDE the safe area (never off-screen).
        val small = slot.role == FlowRoles.STICKER
        val w = when {
            small -> if (tall) 0.34f else 0.24f
            else -> if (tall) 0.56f else 0.42f
        }
        val h = when {
            small -> if (tall) 0.26f else 0.22f
            else -> if (tall) 0.40f else 0.44f
        }
        // Anchor: small stickers alternate corners (top-right, bottom-left…), key visuals sit in the
        // lower-middle third so the headline area above stays free for text overlays.
        val (anchorX, anchorY) = when {
            small -> when (index % 4) {
                0 -> 0.74f to 0.28f
                1 -> 0.26f to 0.30f
                2 -> 0.72f to 0.66f
                else -> 0.28f to 0.64f
            }
            else -> 0.5f to if (tall) 0.60f else 0.58f
        }
        val rect = clampInside(SlotLayout(
            x = anchorX, y = anchorY, w = w, h = h, fit = "contain",
            z = if (small) 40 else 30,
            motion = MOTIONS_FLOAT[seed % MOTIONS_FLOAT.size],
            startMs = start, endMs = end,
            note = if (small) "sticker · safe area ke andar" else "key visual · headline area free"
        ))
        return rect
    }

    /** True when the layout keeps a non-backdrop asset fully inside the frame's safe area. */
    fun fitsInside(l: SlotLayout): Boolean {
        if (l.isBackdrop) return l.w >= 0.98f && l.h >= 0.98f
        val halfW = l.w / 2f
        val halfH = l.h / 2f
        return l.x - halfW >= -0.001f && l.x + halfW <= 1.001f &&
            l.y - halfH >= -0.001f && l.y + halfH <= 1.001f
    }

    /** Push a layout back inside the safe area. Backdrops are left alone (they must bleed). */
    fun clampInside(input: SlotLayout): SlotLayout {
        if (input.isBackdrop) return input
        val w = input.w.coerceIn(0.08f, 0.94f)
        val h = input.h.coerceIn(0.08f, 0.90f)
        val halfW = w / 2f
        val halfH = h / 2f
        val minX = SAFE_X + halfW
        val maxX = 1f - SAFE_X - halfW
        val minY = SAFE_TOP + halfH
        val maxY = SAFE_BOTTOM - halfH
        val x = if (minX <= maxX) input.x.coerceIn(minX, maxX) else 0.5f
        val y = if (minY <= maxY) input.y.coerceIn(minY, maxY) else (SAFE_TOP + (SAFE_BOTTOM - SAFE_TOP) / 2f)
        return input.copy(x = x, y = y, w = w, h = h, fit = if (input.fit == "cover") "cover" else "contain")
    }

    /** One-line, human readable description used in logs and in the flow report. */
    fun describe(l: SlotLayout): String =
        l.note.ifBlank { if (l.isBackdrop) "backdrop" else "layer" } +
            " · " + (l.x * 100).toInt() + "%," + (l.y * 100).toInt() + "% · " +
            (l.w * 100).toInt() + "×" + (l.h * 100).toInt() + "% · " + l.motion + " · z" + l.z
}
