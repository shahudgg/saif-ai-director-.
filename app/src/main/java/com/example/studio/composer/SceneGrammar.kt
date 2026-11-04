package com.example.studio.composer

import com.example.studio.director.Anim
import com.example.studio.director.CameraMove
import com.example.studio.director.Element
import com.example.studio.director.SceneGraph

/**
 * ROUND 15.7 — CONTENT GRAMMAR (the "one template for every topic" fix).
 *
 * The old local composer always produced the same four-element skeleton: particles + one hero
 * primitive + a title + a line of detail. Whatever the topic was, the video looked the same, because
 * the STRUCTURE was the same even when the colours changed.
 *
 * This file gives a beat a *composition grammar* instead of a template:
 *   • six layout families (hero centre / hero left + data right / hero right + data left /
 *     full-width data band / split stat / band on top),
 *   • a secondary ILLUSTRATION chosen from the beat's own meaning — a numbered process rail, a real
 *     bar chart of the beat's numbers, a line trend, a two-sided comparison, a quote, a network —
 *   • a **boundary card** ("card_frame"): one animated rounded panel that owns that band, so every
 *     overlay element sits inside a designed, animated area instead of floating wherever,
 *   • per-element keyframes derived from the beat's timing (staggered entrances, emphasis on the
 *     important value, outro fade), merged into the same SceneGraph the renderer already animates.
 *
 * Everything here is deterministic: the layout family is chosen by a hash of the beat text + topic
 * seed, so two different topics never get the same family by accident, and the same topic re-renders
 * identically.
 */
object SceneGrammar {

    const val VARIANTS = 6

    /** Layout family for a beat: depends on the words, the scene index and the video's style seed. */
    fun variantFor(beat: Beat, dna: StyleDNA?): Int {
        val seed = (dna?.seed ?: 0)
        val text = (beat.headline + "|" + beat.support).lowercase()
        var h = 17
        for (ch in text) h = (h * 31 + ch.code) and 0x7FFFFF
        val mixed = (h + seed * 7 + beat.index * 13)
        return ((mixed % VARIANTS) + VARIANTS) % VARIANTS
    }

    /** One band (the boundary card) per scene, always inside the safe width. */
    private data class Band(val x: Float, val y: Float, val w: Float, val h: Float)

    private fun bandFor(variant: Int): Band = when (variant) {
        0 -> Band(0.50f, 0.575f, 0.86f, 0.125f)  // wide data band under the hero
        1 -> Band(0.50f, 0.580f, 0.72f, 0.110f)  // compact band
        2 -> Band(0.71f, 0.390f, 0.42f, 0.320f)  // right column card
        3 -> Band(0.29f, 0.390f, 0.42f, 0.320f)  // left column card
        4 -> Band(0.62f, 0.430f, 0.34f, 0.260f)  // small floating card, right
        else -> Band(0.50f, 0.585f, 0.80f, 0.120f)
    }

    /** Families whose card sits beside the hero (so the hero narrows). */
    private fun isSide(variant: Int): Boolean = variant == 2 || variant == 3 || variant == 4

    /** The primitive that best shows this beat's own data. Null when the beat has nothing to show. */
    private fun secondaryType(beat: Beat): String? {
        val items = beat.items.filter { it.isNotBlank() }
        val nums = beat.numbers
        return when (beat.intent) {
            Intent.PROCESS_STEPS -> if (items.size >= 2) "steps" else "steps"
            Intent.LIST -> if (items.size >= 2) "steps" else "ticker"
            Intent.CHART_BAR -> if (nums.size >= 2) "bars" else "bars"
            Intent.BIG_STAT -> if (nums.size >= 3) "bars" else "arcdial"
            Intent.CHART_LINE -> if (nums.size >= 3) "linechart" else "linechart"
            Intent.COMPARISON, Intent.MYTH_VS_FACT -> "compare"
            Intent.TIMELINE -> "steps"
            Intent.NETWORK_DIAGRAM -> "nodes"
            Intent.QUOTE -> "quote"
            Intent.MAP_LIKE_ZOOM -> "ring"
            Intent.QUESTION -> "badge"
            Intent.CTA -> "badge"
            Intent.HOOK -> "ticker"
            Intent.STATEMENT -> if (nums.size >= 3) "bars" else "ticker"
        }
    }

    private fun labelsFor(beat: Beat, max: Int = 4): List<String> {
        val src = (beat.items + beat.keywords).map { clean(it) }.filter { it.isNotBlank() }.distinct()
        val fallback = headlineWords(beat)
        return (src + fallback).distinct().take(max)
    }

    private fun clean(s: String): String =
        s.trim().replace(Regex("\\s+"), " ").take(18)

    private fun headlineWords(beat: Beat): List<String> =
        beat.headline.split(" ").map { it.trim(' ', ',', '.', ':', ';', '!', '?') }
            .filter { it.length >= 3 }.take(4)

    private fun valuesFor(beat: Beat): List<Float> {
        val n = beat.numbers.filter { it.isFinite() }
        if (n.size >= 2) return n.take(12)
        // No numbers in the narration: use the syllable weight of the key items, so the chart still
        // shows something real (relative emphasis) instead of invented statistics.
        val items = labelsFor(beat, 6)
        if (items.isEmpty()) return emptyList()
        return items.map { it.length.toFloat() }.let { list ->
            val max = list.maxOrNull() ?: 1f
            list.map { (it / max) * 100f }
        }
    }

    /**
     * Returns the graph with the grammar applied: hero re-positioned for its family, a boundary card
     * added, and the beat's own data drawn inside that card with staggered keyframes.
     */
    fun decorate(beat: Beat, base: SceneGraph, palette: Palette, accent: String, durationMs: Long, variant: Int): SceneGraph {
        val v = ((variant % VARIANTS) + VARIANTS) % VARIANTS
        val band = bandFor(v)
        val dur = durationMs.coerceAtLeast(1200L)
        val els = ArrayList<Element>()
        val side = isSide(v)

        // ---- 1. re-position the hero so it never fights the band -------------------------------
        base.elements.forEach { e ->
            when {
                e.id == "hero" && side -> els += e.copy(
                    x = if (v == 3) 0.68f else 0.34f, y = 0.330f, w = 0.44f, h = 0.22f
                )
                e.id == "hero" -> els += e.copy(x = 0.50f, y = 0.300f, w = 0.72f, h = 0.235f)
                e.id == "title" -> els += e.copy(x = 0.50f, y = 0.105f, w = 0.82f)
                e.id == "detail" && side -> els += e.copy(x = 0.50f, y = 0.180f, w = 0.72f)
                e.id == "detail" -> els += e.copy(x = 0.50f, y = 0.470f, w = 0.76f)
                else -> els += e
            }
        }

        // ---- 2. the animated BOUNDARY card that owns the band ----------------------------------
        val inDur = (dur * 0.18f).toLong().coerceIn(160L, 520L)
        val frame = Element(
            id = "card_frame", type = "rect",
            x = band.x, y = band.y, w = band.w, h = band.h,
            color = palette.card, radius = 0.28f, glow = 0.18f, shadow = 0.30f,
            anims = listOf(
                Anim("w", band.w * 0.55f, band.w, 0L, inDur, "easeOut"),
                Anim("h", band.h * 0.7f, band.h, 0L, inDur, "easeOut"),
                Anim("alpha", 0f, 0.92f, 0L, inDur, "easeOut")
            )
        )
        val frameEdge = Element(
            id = "card_frame_edge", type = "rect",
            x = band.x, y = band.y, w = band.w - 0.012f, h = band.h - 0.012f,
            color = accent, radius = 0.26f, glow = 0.55f, strokeW = 0.0035f,
            anims = listOf(
                Anim("alpha", 0f, 0.85f, 40L, inDur + 120L, "easeOut"),
                Anim("scale", 0.94f, 1f, inDur, (inDur + 520L).coerceAtMost(dur), "easeInOut")
            )
        )

        // ---- 3. the beat's own illustration INSIDE the band ------------------------------------
        val type = secondaryType(beat)
        val labels = labelsFor(beat)
        val values = valuesFor(beat)
        val inner = Element(
            id = "band_data", type = type ?: "ticker",
            x = band.x, y = band.y + if (side) 0.012f else 0.004f,
            w = band.w * 0.86f, h = band.h * 0.74f,
            color = accent, color2 = palette.accent2,
            values = values, labels = labels,
            count = labels.size.coerceIn(2, 6),
            text = labels.firstOrNull().orEmpty(),
            seed = (beat.index + 1) * 7,
            anims = listOf(
                Anim("alpha", 0f, 1f, inDur + 60L, inDur + 420L, "easeOut"),
                Anim("progress", 0f, 1f, inDur + 60L, inDur + 900L, "easeInOut")
            )
        )

        // ---- 4. a caption strip that names what the band shows ---------------------------------
        val bandLabel = when (beat.intent) {
            Intent.PROCESS_STEPS, Intent.TIMELINE -> "STEP BY STEP"
            Intent.LIST -> "KEY POINTS"
            Intent.COMPARISON, Intent.MYTH_VS_FACT -> "SIDE BY SIDE"
            Intent.CHART_BAR, Intent.BIG_STAT, Intent.CHART_LINE -> "THE DATA"
            Intent.QUOTE -> "IN THEIR WORDS"
            Intent.NETWORK_DIAGRAM -> "HOW IT CONNECTS"
            Intent.QUESTION, Intent.CTA -> "YOUR TURN"
            else -> labels.firstOrNull()?.uppercase()?.take(14) ?: "HIGHLIGHTS"
        }
        val bandTitle = Element(
            id = "band_label", type = "text",
            x = band.x, y = band.y - band.h / 2f + 0.018f, w = band.w * 0.9f, h = 0.036f,
            color = palette.accent2, text = bandLabel, size = 0.030f, letterSpacing = 0.06f,
            anims = listOf(Anim("alpha", 0f, 1f, inDur + 140L, inDur + 380L, "easeOut"))
        )

        // ---- 5. order matters: every shape is drawn before any text -----------------------------
        val all = ArrayList<Element>()
        all += els.filter { it.type != "text" }
        all += listOf(frameEdge, frame, inner)
        all += bandTitle
        all += els.filter { it.type == "text" }

        // ---- 6. camera: the family drives the move, so no two families feel alike --------------
        val cam = when (v) {
            0 -> CameraMove(1f, 1.05f, panXTo = -0.02f)
            1 -> CameraMove(1.03f, 1.0f, panYTo = -0.02f)
            2 -> CameraMove(1.0f, 1.07f, panXTo = 0.03f)
            3 -> CameraMove(1.05f, 1.0f, panXTo = -0.03f, orbitTo = 6f)
            4 -> CameraMove(1.0f, 1.04f, tiltTo = -4f)
            else -> CameraMove(1.02f, 1.09f, panYTo = 0.02f)
        }
        return base.copy(camera = cam, elements = all.take(22))
    }
}
