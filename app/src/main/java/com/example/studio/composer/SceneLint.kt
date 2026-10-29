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

    fun sameLook(a: SceneGraph, b: SceneGraph): Boolean {
        val fa = fingerprint(a); val fb = fingerprint(b)
        if (fa.isEmpty() || fb.isEmpty()) return false
        val inter = fa.intersect(fb).size.toDouble()
        val union = fa.union(fb).size.toDouble()
        return inter / union >= 0.8 && abs(fa.size - fb.size) <= 1
    }
}
