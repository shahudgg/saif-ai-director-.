package com.example.studio.composer

import com.example.studio.director.Element
import com.example.studio.director.SceneGraph
import kotlin.math.abs

/**
 * Design critic for scenes written by an AI model. The model is free to be creative, but a scene is only accepted when it
 * has real content, no empty blocks, no text spilling out of its shape and does not look like the previous scene.
 * Rejected scenes are redone by the model (with the reason) or by the local composer.
 */
object SceneLint {
    private val DECOR = setOf("particles", "glow", "vignette", "grain", "sweep", "flash", "glitch", "grid", "burst")
    private val SMALL_RECT_IDS = listOf("card", "bar", "track", "fill", "btn", "step", "strike", "chip")

    private fun isShape(e: Element) = e.type in setOf("rect", "circle", "ring", "donut")
    private fun area(e: Element) = e.w * e.h

    /**
     * Rectangles that hold text are "cards": the layout engine then keeps their text inside them. Models name every
     * rectangle "rect1", so without this the text of a box could hang out of it.
     */
    fun adoptCards(g: SceneGraph, aw: Int, ah: Int): SceneGraph {
        val texts = g.elements.filter { it.type == "text" && it.text.isNotBlank() }
        if (texts.isEmpty()) return g
        val centres = texts.map { t -> LayoutEngine.textBox(t, aw, ah).first }
        val used = g.elements.map { it.id }.toMutableSet()
        val els = g.elements.map { e ->
            if (e.type != "rect" || e.id.startsWith("card") || area(e) < 0.012f) return@map e
            val holds = centres.any { b ->
                b.cx >= e.x - e.w / 2f && b.cx <= e.x + e.w / 2f && b.cy >= e.y - e.h / 2f && b.cy <= e.y + e.h / 2f
            }
            if (!holds) return@map e
            var nid = "card_" + e.id
            while (!used.add(nid)) nid += "x"
            e.copy(id = nid)
        }
        return g.copy(elements = els)
    }

    /** Human-readable reasons a scene is not good enough; empty means accepted. */
    fun problems(g: SceneGraph, aw: Int, ah: Int): List<String> {
        val out = ArrayList<String>()
        val content = g.elements.filter { it.type !in DECOR }
        val texts = g.elements.filter { it.type == "text" && it.text.isNotBlank() }
        if (content.size < 3) out += "the scene has almost no content (${content.size} visible elements)"
        if (texts.isEmpty()) out += "the scene shows no text at all"
        val boxes = texts.map { LayoutEngine.textBox(it, aw, ah).first }
        g.elements.filter { it.type == "rect" }.forEach { r ->
            val a = area(r)
            if (a >= 0.34f) out += "${r.id} is a full-screen flat block"
            else if (a >= 0.05f && SMALL_RECT_IDS.none { r.id.startsWith(it) || r.id.startsWith("card_") }) {
                val holds = boxes.any { b -> b.cx >= r.x - r.w / 2f && b.cx <= r.x + r.w / 2f && b.cy >= r.y - r.h / 2f && b.cy <= r.y + r.h / 2f }
                if (!holds) out += "${r.id} is a large empty block with nothing in it"
            }
        }
        val shapes = g.elements.count { it.type == "rect" && area(it) >= 0.012f }
        if (shapes >= 3 && texts.size <= 1) out += "plain rectangles with no labels explain nothing"
        val dup = texts.groupBy { it.text.trim().lowercase() }.filter { it.value.size > 1 }.keys.firstOrNull()
        if (dup != null) out += "the text \"${dup.take(20)}\" is repeated"
        return out
    }

    /** Layout fingerprint: element types and coarse positions. Two scenes with the same fingerprint look the same. */
    fun fingerprint(g: SceneGraph): Set<String> = g.elements.filter { it.type !in DECOR }
        .map { "${it.type}@${(it.x * 8).toInt()},${(it.y * 8).toInt()}" }.toSet()

    fun signature(g: SceneGraph): String = g.elements.filter { it.type !in DECOR }.map {
        "${it.type}@${(it.x * 8).toInt()},${(it.y * 8).toInt()}:${(it.w * 8).toInt()},${(it.h * 8).toInt()}"
    }.sorted().joinToString("|")

    fun sameLook(a: SceneGraph, b: SceneGraph): Boolean {
        val fa = fingerprint(a); val fb = fingerprint(b)
        if (fa.isEmpty() || fb.isEmpty()) return false
        val inter = fa.intersect(fb).size.toDouble()
        val union = fa.union(fb).size.toDouble()
        return inter / union >= 0.8 && abs(fa.size - fb.size) <= 1
    }

    /**
     * STRUCTURE signature: which primitive family the scene is built from, how many of each, its palette and its
     * camera move. Two scenes with the same signature feel like the same template even when their boxes are in
     * slightly different places — which is exactly the "every video looks the same" complaint.
     */
    fun structure(g: SceneGraph): String {
        val counts = g.elements.filter { it.type !in DECOR }
            .groupBy { it.type }.map { "${it.key}${it.value.size}" }.sorted().joinToString("+")
        val palette = (listOf(g.bgTop, g.bgBottom) + g.elements.map { it.color }.take(4))
            .map { it.lowercase() }.distinct().sorted().joinToString(",")
        val cam = "${"%.1f".format(g.camera.zoomTo)}|${"%.2f".format(g.camera.panXTo)}"
        return "$counts#$palette#$cam"
    }

    /** Primitive-family signature only (no colours), for a softer comparison. */
    fun family(g: SceneGraph): String = g.elements.filter { it.type !in DECOR }
        .groupBy { it.type }.map { "${it.key}${it.value.size}" }.sorted().joinToString("+")

    /**
     * True when two scenes are so alike that a viewer would call one a copy of the other. Stricter than [sameLook]:
     * it also catches "same shapes, shifted a little" and "same shapes, recoloured".
     */
    fun tooSimilar(a: SceneGraph, b: SceneGraph): Boolean {
        if (sameLook(a, b)) return true
        val fa = fingerprint(a); val fb = fingerprint(b)
        if (fa.isEmpty() || fb.isEmpty()) return false
        val jaccard = fa.intersect(fb).size.toDouble() / fa.union(fb).size
        val sameFamily = family(a) == family(b) && family(a).isNotBlank()
        // Same primitive recipe AND (nearly the same layout OR the same palette) = a template repeat.
        if (sameFamily && (jaccard >= 0.55 || structure(a) == structure(b))) return true
        // Same recipe, same colours, only the camera differs.
        return sameFamily && jaccard >= 0.40 && structure(a) == structure(b)
    }
}

/**
 * Whole-video uniqueness critic. `planManifest` asks it after every accepted scene, so a video can never end up
 * with three scenes that are the same diagram in different colours.
 */
object SceneUniqueness {

    /** Indices of the already-accepted scenes that [candidate] repeats, strongest repeat first. */
    fun repeatsOf(candidate: SceneGraph, accepted: List<SceneGraph>): List<Int> {
        val out = ArrayList<Pair<Int, Double>>()
        accepted.forEachIndexed { i, a ->
            if ((SceneLint.signature(a).isNotBlank() && SceneLint.signature(a) == SceneLint.signature(candidate)) || SceneLint.tooSimilar(a, candidate)) {
                val fa = SceneLint.fingerprint(a); val fb = SceneLint.fingerprint(candidate)
                val j = if (fa.isEmpty() || fb.isEmpty()) 1.0 else fa.intersect(fb).size.toDouble() / fa.union(fb).size
                out += i to j
            }
        }
        return out.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * A readable reason to send back to the model, naming the scene it copied and what must change.
     * [indexInVideo] is the 0-based position of the new scene.
     */
    fun reason(candidate: SceneGraph, accepted: List<SceneGraph>, indexInVideo: Int): String? {
        val hits = repeatsOf(candidate, accepted)
        if (hits.isEmpty()) return null
        val worst = hits.first()
        val a = accepted[worst]
        val shared = SceneLint.fingerprint(a).intersect(SceneLint.fingerprint(candidate))
        val shapes = shared.map { it.substringBefore('@') }.distinct().take(4)
        return "scene ${indexInVideo + 1} repeats the design of scene ${worst + 1}" +
            (if (shapes.isNotEmpty()) " (same ${shapes.joinToString("/")} in nearly the same places)" else "") +
            ". Redesign it with a DIFFERENT primitive family (radar, arcdial, table, steps, orbit, ribbon, heat, stack, compare, spiral, halftone, ticker, wave, badge, spotlight...), a different layout and a different colour balance."
    }

    /** Pairs of scenes in a finished manifest that still look alike, reported honestly to the owner. */
    fun collisions(scenes: List<SceneGraph?>): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (i in scenes.indices) for (j in i + 1 until scenes.size) {
            val a = scenes[i] ?: continue
            val b = scenes[j] ?: continue
            if (repeatsOf(b, listOf(a)).isNotEmpty()) out += i to j
        }
        return out
    }
}
