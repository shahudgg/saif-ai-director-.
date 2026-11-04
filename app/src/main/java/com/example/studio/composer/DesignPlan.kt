package com.example.studio.composer

/**
 * ROUND 16.1 — DESIGN PLAN: repetition is prevented by construction, not by rejection.
 *
 * Before a single scene is designed, this object reserves a full design slot for every scene of the video:
 *
 *   • one **element family** that no other scene in this video is allowed to use (29 families, so a video
 *     up to 29 scenes can never hand the same family to two scenes),
 *   • a layout variant and a hero position slot that rotate independently,
 *   • a palette index out of the video's own four palettes,
 *   • the exact **time window** the scene covers (mm:ss–mm:ss), computed from the narration timing.
 *
 * The model is told its reserved slot, and the local composer builds from the same slot when the model's
 * design has to be replaced. That is why a repeated scene is now a *repair of one scene* and not a failed run.
 */
object DesignPlan {

    data class Slot(
        val index: Int,
        val archetype: String,
        val layout: Int,
        val paletteIndex: Int,
        val heroSlot: Int,
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1000L)
        fun clock(): String = "%02d:%02d–%02d:%02d".format(startMs / 60000, (startMs / 1000) % 60, endMs / 60000, (endMs / 1000) % 60)
        fun seconds(): String = "%.1f".format(java.util.Locale.ROOT, durationMs / 1000.0)
    }

    /** One slot per scene, in video order. [windows] are the narration windows (startMs, endMs) of each scene. */
    fun plan(count: Int, dna: StyleDNA?, windows: List<Pair<Long, Long>>): List<Slot> {
        if (count <= 0) return emptyList()
        val families = SceneVarietyComposer.ARCHETYPES.map { it.type }
        val r = java.util.Random(((dna?.seed ?: 0).toLong() * 2654435761L) or 1L)
        val order = families.toMutableList().also { java.util.Collections.shuffle(it, r) }
        val paletteCount = dna?.palettes?.size?.coerceAtLeast(1) ?: 1
        return (0 until count).map { i ->
            val win = windows.getOrNull(i) ?: (i * 4000L to (i + 1) * 4000L)
            Slot(
                index = i,
                // A family is reused only after every other family has been used once (count > 29).
                archetype = order[i % order.size],
                layout = (i + (dna?.variantShift ?: 0)) % SceneVarietyComposer.LAYOUTS,
                paletteIndex = (i + (dna?.seed ?: 0)) % paletteCount,
                heroSlot = (i * 5 + (dna?.seed ?: 0)) % 9,
                startMs = win.first,
                endMs = win.second
            )
        }
    }

    /** The line the model receives for one scene: its own time window and its reserved design. */
    fun hint(slot: Slot, total: Int): String =
        "TIME WINDOW ${slot.clock()} (${slot.seconds()} s, scene ${slot.index + 1} of $total) — show only what the narrator " +
            "says in this window. RESERVED DESIGN (unique to this scene in this video): ${slot.archetype} family, layout " +
            "${slot.layout + 1} of ${SceneVarietyComposer.LAYOUTS}, hero in position ${slot.heroSlot + 1} of 9. No other scene " +
            "may use this family — build the scene around it and change only what the narration needs."
}
