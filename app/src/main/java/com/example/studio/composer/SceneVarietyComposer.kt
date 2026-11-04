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
        // ROUND 16.0 — topic archetypes first (orbit/radar for space, bars/line for business, steps for
        // an explainer…), then the rest. Ordering only; every family stays available as a fallback.
        val preferred = dna?.topic?.archetypes.orEmpty()
        val ordered = usable.sortedBy { a -> preferred.indexOf(a.type).let { if (it < 0) 99 else it } }
        val offset = ((dna?.seed ?: 0).toLong() + beat.index.toLong() * 17L).mod(ordered.size.toLong()).toInt()
        for (a in ordered.indices) for (layout in 0 until LAYOUTS) {
            val archetype = ordered[(offset + a) % ordered.size]
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
        val base = SceneGraph(palette.bgTop, palette.bgBottom, camera, listOf(atmosphere, main, title, detail))
        val (aw, ah) = LayoutEngine.parseAspect(aspect)
        // ROUND 15.7 — the composition grammar decides the STRUCTURE of this beat (layout family +
        // the illustration that really shows its data + the animated boundary card that owns it), so two
        // different topics never come out looking like the same template.
        // ROUND 16.0 — the topic's own art direction decides which composition grammars are allowed,
        // so a space video does not borrow the layout family of a business explainer.
        val topic = dna?.topic ?: TopicStyle.general()
        val baseVariant = SceneGrammar.variantFor(beat, dna) + v
        val grammarVariant = if (topic.grammars.isEmpty()) baseVariant
            else topic.grammars[((baseVariant % topic.grammars.size) + topic.grammars.size) % topic.grammars.size]
        val decorated = SceneGrammar.decorate(beat, base, palette, accent, dur, grammarVariant)
        return LayoutEngine.repair(decorated, aw, ah, dur).graph
    }

    // ---------------------------------------------------------------------------------------------
    // ROUND 16.1 — BUILD FROM A RESERVED SLOT.
    // The design plan gives every scene its own element family, layout and hero position, so the
    // builder never has to reject a whole plan because one scene repeated another: it rebuilds THAT
    // scene from its own slot. The returned graph is checked against everything already accepted;
    // only if no checked candidate exists does it return the last candidate (a delivered video always
    // beats a failed run), and the caller reports that honestly.
    // ---------------------------------------------------------------------------------------------
    private val BACKDROP = setOf("glow", "particles", "vignette", "grain", "grid", "sweep", "burst", "flash", "bg", "noise")

    /** Nine hero positions; each scene gets its own so two scenes cannot share the same focal point. */
    private val HERO_SLOTS = listOf(
        .50f to .30f, .30f to .28f, .70f to .28f, .50f to .20f, .26f to .46f,
        .74f to .46f, .50f to .50f, .34f to .44f, .66f to .44f
    )

    private fun relocate(g: SceneGraph, heroSlot: Int): SceneGraph {
        val (tx, ty) = HERO_SLOTS[heroSlot.mod(HERO_SLOTS.size)]
        val hero = g.elements.firstOrNull { it.id == "hero" } ?: return g
        val dx = tx - hero.x
        val dy = ty - hero.y
        return g.copy(elements = g.elements.map { e ->
            when {
                e.type in BACKDROP -> e
                e.id == "hero" -> e.copy(x = (e.x + dx).coerceIn(.16f, .84f), y = (e.y + dy).coerceIn(.18f, .60f))
                else -> e.copy(x = (e.x + dx * .55f).coerceIn(.10f, .90f), y = (e.y + dy * .55f).coerceIn(.10f, .62f))
            }
        })
    }

    private fun passes(g: SceneGraph, aspect: String, accepted: List<SceneGraph>, dur: Long): Boolean {
        val (aw, ah) = LayoutEngine.parseAspect(aspect)
        return LayoutEngine.issues(g, aw, ah, dur).isEmpty() &&
            SceneLint.problems(g, aw, ah).isEmpty() &&
            SceneUniqueness.repeatsOf(g, accepted).isEmpty()
    }

    /**
     * Compose [beat] from its reserved [slot]. Returns a graph that differs from [accepted] whenever one
     * exists, and the closest candidate otherwise. [usedFamilies] lets the caller forbid the families that
     * earlier scenes already took.
     */
    fun fromSlot(
        beat: Beat, aspect: String, dna: StyleDNA?, accentOverride: String?,
        accepted: List<SceneGraph>, slot: DesignPlan.Slot, usedFamilies: Set<String> = emptySet()
    ): SceneGraph {
        val dur = beat.durationMs.coerceAtLeast(1200L)
        val reserved = ARCHETYPES.firstOrNull { it.type == slot.archetype } ?: ARCHETYPES[slot.index.mod(ARCHETYPES.size)]
        val rest = ARCHETYPES.filter { it.type != reserved.type }
            .sortedBy { a -> if (a.type in usedFamilies) 1 else 0 }
        var last: SceneGraph? = null
        for ((round, a) in (listOf(reserved) + rest).withIndex()) {
            for (k in 0 until LAYOUTS) {
                val layout = (slot.layout + k + round) % LAYOUTS
                val heroSlot = if (round == 0) slot.heroSlot else slot.heroSlot + round * 3 + k
                val g = relocate(compose(beat, aspect, a, layout, dna, accentOverride), heroSlot)
                val (aw, ah) = LayoutEngine.parseAspect(aspect)
                val fixed = LayoutEngine.repair(g, aw, ah, dur).graph
                last = fixed
                if (passes(fixed, aspect, accepted, dur)) return fixed
            }
        }
        // Nothing passed the cosmetic gates: return the reserved family anyway, on its own slot,
        // instead of failing the video.
        val fallback = relocate(compose(beat, aspect, reserved, slot.layout, dna, accentOverride), slot.heroSlot)
        return last ?: fallback
    }
}
