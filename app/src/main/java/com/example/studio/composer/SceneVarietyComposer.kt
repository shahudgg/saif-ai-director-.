package com.example.studio.composer

import com.example.studio.director.Anim
import com.example.studio.director.CameraMove
import com.example.studio.director.Element
import com.example.studio.director.SceneGraph

/** Twenty-nine native visual families, each with three materially different layouts. */
object SceneVarietyComposer {
    data class Archetype(val name: String, val type: String, val needsNumbers: Boolean = false, val needsItems: Boolean = false,
                         val needsPercentages: Boolean = false, val needsQuote: Boolean = false)
    val ARCHETYPES = listOf(
        Archetype("signal radar", "radar"), Archetype("flowing wave", "wave"), Archetype("ribbon", "ribbon"),
        Archetype("orbit", "orbit"), Archetype("spiral", "spiral"), Archetype("ticker", "ticker"),
        Archetype("editorial quote", "quote", needsQuote = true), Archetype("halftone", "halftone"), Archetype("scan panel", "scanline"),
        Archetype("spotlight", "spotlight"), Archetype("light beam", "beam"), Archetype("badge", "badge"),
        Archetype("barcode", "barcode"), Archetype("confetti", "confetti"), Archetype("network", "nodes"),
        Archetype("ring focus", "ring"), Archetype("circular focus", "circle"), Archetype("topic glyph", "icon"),
        Archetype("depth stack", "depthStack"), Archetype("parallax planes", "parallax"),
        Archetype("extruded block", "extrusion"), Archetype("perspective quad", "perspectiveQuad"),
        Archetype("perspective card", "perspectiveCard"), Archetype("cube", "cube"),
        Archetype("steps", "steps", needsItems = true), Archetype("table", "table", needsItems = true),
        Archetype("data bars", "bars", needsNumbers = true), Archetype("line chart", "linechart", needsNumbers = true),
        Archetype("gauge", "arcdial", needsPercentages = true), Archetype("comparison", "compare", needsItems = true),
        Archetype("composition", "stack", needsPercentages = true), Archetype("heat map", "heat", needsPercentages = true)
    )
    const val LAYOUTS = 3

    /** null rather than a repeated scene when no candidate passes the hard gate. */
    fun unique(beat: Beat, aspect: String, dna: StyleDNA?, accentOverride: String?, accepted: List<SceneGraph>): SceneGraph? {
        // First try the semantic composer for this beat. Then replan across concrete archetype/layout pairs.
        repeat(3) { v ->
            val g = SceneComposer.compose(beat, aspect, variant = beat.index + v, accentOverride = accentOverride, dna = dna)
            val (aw, ah) = LayoutEngine.parseAspect(aspect)
            if (LayoutEngine.issues(g, aw, ah, beat.durationMs).isEmpty() && SceneLint.problems(g, aw, ah).isEmpty() && SceneUniqueness.repeatsOf(g, accepted).isEmpty()) return g
        }
        val hasPercentages = beat.numbers.isNotEmpty() && beat.numbers.all { it in 0f..100f } &&
            beat.numberTokens.size >= beat.numbers.size && beat.numberTokens.all { it.contains("%") || it.contains("percent", ignoreCase = true) }
        val usable = ARCHETYPES.filter { (!it.needsNumbers || beat.numbers.size >= 2) &&
            (!it.needsItems || beat.items.size >= 2) && (!it.needsPercentages || hasPercentages) &&
            (!it.needsQuote || beat.intent == Intent.QUOTE) &&
            (it.type != "stack" || (beat.numbers.size >= 2 && beat.numbers.take(6).sum() <= 100f)) &&
            (it.type != "heat" || beat.numbers.size >= 6) }
        val offset = ((dna?.seed ?: 0).toLong() + beat.index.toLong() * 17L).mod(usable.size.toLong()).toInt()
        for (a in usable.indices) for (layout in 0 until LAYOUTS) {
            val archetype = usable[(offset + a) % usable.size]
            val variant = (layout + beat.index + (dna?.variantShift ?: 0)).mod(LAYOUTS)
            val g = compose(beat, aspect, archetype, variant, dna, accentOverride)
            val (aw, ah) = LayoutEngine.parseAspect(aspect)
            if (LayoutEngine.issues(g, aw, ah, beat.durationMs).isEmpty() && SceneLint.problems(g, aw, ah).isEmpty() && SceneUniqueness.repeatsOf(g, accepted).isEmpty()) return g
        }
        return null
    }

    fun compose(beat: Beat, aspect: String, archetype: Archetype, layout: Int, dna: StyleDNA?, accentOverride: String?): SceneGraph {
        val v = layout.mod(LAYOUTS)
        val palette = dna?.palettes?.get((beat.index + v).mod(dna.palettes.size)) ?: Palettes.ALL[(beat.index + v).mod(Palettes.ALL.size)]
        val accent = accentOverride ?: palette.accent
        val dur = beat.durationMs.coerceAtLeast(1200L)
        fun anim(delay: Long) = listOf(
            Anim("alpha", 0f, 1f, delay.coerceAtMost(dur), (delay + 300).coerceAtMost(dur)),
            Anim("progress", 0f, 1f, delay.coerceAtMost(dur), (delay + 1000).coerceAtMost(dur)))
        data class Layout(val x: Float, val y: Float, val w: Float, val h: Float, val tx: Float, val ty: Float, val tw: Float, val sy: Float)
        val pos = when (v) {
            0 -> Layout(.50f, .36f, .68f, .30f, .50f, .12f, .82f, .59f)
            1 -> Layout(.25f, .35f, .34f, .30f, .70f, .24f, .43f, .47f)
            else -> Layout(.75f, .35f, .34f, .30f, .30f, .24f, .43f, .47f)
        }
        val keywords = (beat.items + beat.keywords).filter { it.isNotBlank() }.distinct().take(4)
        val main = Element("hero", archetype.type, x = pos.x, y = pos.y, w = pos.w, h = pos.h,
            color = accent, color2 = palette.accent2, text = if (archetype.type == "icon") (IconPicker.forBeat(beat) ?: "star") else beat.headline.take(60),
            values = when (archetype.type) {
                "nodes" -> listOf(3f, 4f, 3f); "wave" -> listOf(.5f, 1f)
                "stack", "heat" -> beat.numbers.take(12).map { it / 100f }
                else -> beat.numbers.take(12)
            },
            labels = keywords, count = when (archetype.type) {
                "halftone" -> 12
                "steps", "table" -> beat.items.size.coerceIn(2, 6)
                "stack" -> beat.numbers.size.coerceIn(2, 6)
                "heat" -> 3
                else -> 3
            }, seed = (dna?.seed ?: 1) xor beat.index,
            anims = anim(120), shadow = if (archetype.type in setOf("cube", "perspectiveCard", "extrusion")) .35f else 0f)
        val title = Element("title", "text", x = pos.tx, y = pos.ty, w = pos.tw, h = .1f, color = palette.text,
            text = beat.headline.ifBlank { beat.support.take(35) }.take(60), size = if (v == 0) .052f else .044f, fx = "rise", anims = anim(0))
        val supporting = beat.support.take(90).takeIf { it.isNotBlank() && !it.equals(title.text, true) } ?: keywords.firstOrNull() ?: ""
        // A second independent visual is present even if the support would repeat the headline.
        val detail = if (supporting.isNotBlank() && !supporting.equals(title.text, true)) Element("detail", "text", x = pos.tx, y = pos.sy, w = pos.tw, h = .1f,
            color = palette.muted, text = supporting, size = .032f, anims = anim(400)) else Element("detail", "line", x = pos.tx - pos.tw * .3f,
            y = pos.sy, x2 = pos.tx + pos.tw * .3f, y2 = pos.sy, color = palette.accent2, anims = anim(400))
        val atmosphere = Element("fx", "particles", x = .5f, y = .35f, w = 1f, h = .7f, color = accent, count = 18, seed = (dna?.seed ?: 1) + beat.index,
            anims = listOf(Anim("alpha", .3f, .3f, 0L, 1L, "linear")))
        val camera = when (v) {
            0 -> CameraMove(1f, 1.06f, panXTo = -.02f)
            1 -> CameraMove(1.04f, 1.0f, panXTo = .04f, orbitTo = 8f)
            else -> CameraMove(1f, 1.12f, panXTo = -.04f, tiltTo = -6f)
        }
        val g = SceneGraph(palette.bgTop, palette.bgBottom, camera, listOf(atmosphere, main, title, detail))
        val (aw, ah) = LayoutEngine.parseAspect(aspect)
        return LayoutEngine.repair(g, aw, ah, dur).graph
    }
}
