package com.example.studio.composer

import com.example.studio.director.Anim
import com.example.studio.director.Element
import com.example.studio.director.SceneGraph
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic layout checker/repairer for scene graphs. Whatever produced the graph (the local composer or an AI model),
 * this guarantees: text fits the frame and its box, text never collides with other text or hides behind shapes,
 * nothing sits in the caption zone, colours are readable, and animations end inside the scene.
 *
 * Coordinates are the renderer's normalized 0..1. Text width is estimated from the glyph count (conservative bold-sans metric),
 * so no Android classes are needed and the whole engine is unit-testable on the JVM.
 */
object LayoutEngine {

    /** Captions are drawn around y = 0.74; graphics keep above this line. */
    const val CONTENT_BOTTOM = 0.66f
    const val CONTENT_TOP = 0.05f
    const val SIDE_MARGIN = 0.05f
    const val MIN_TEXT_SIZE = 0.028f
    const val MAX_ELEMENTS = 24
    private const val CHAR_W = 0.60f      // average glyph width as a fraction of font pixel size
    private const val GAP = 0.008f

    data class Result(val graph: SceneGraph, val fixes: List<String>)
    data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun intersects(o: Box, gap: Float = 0f) = l < o.r + gap && o.l < r + gap && t < o.b + gap && o.t < b + gap
        fun contains(px: Float, py: Float) = px in l..r && py in t..b
        val cx get() = (l + r) / 2f
        val cy get() = (t + b) / 2f
    }

    fun parseAspect(s: String): Pair<Int, Int> {
        val p = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        return Pair(p.getOrNull(0)?.takeIf { it > 0 } ?: 9, p.getOrNull(1)?.takeIf { it > 0 } ?: 16)
    }

    // ------------------------------------------------------------------ text metrics

    private fun px(e: Element, aw: Int, ah: Int) = e.size * 1.6f * min(aw, ah)

    fun wrapCount(text: String, maxChars: Int): List<String> {
        val lines = ArrayList<String>(); var cur = ""
        for (word in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            var w = word
            while (w.length > maxChars) { if (cur.isNotEmpty()) { lines += cur; cur = "" }; lines += w.take(maxChars); w = w.drop(maxChars) }
            val trial = if (cur.isEmpty()) w else "$cur $w"
            if (trial.length > maxChars && cur.isNotEmpty()) { lines += cur; cur = w } else cur = trial
        }
        if (cur.isNotEmpty()) lines += cur
        return lines
    }

    private fun maxWidthUnits(e: Element, aw: Int) = (if (e.w > 0f) e.w else 0.9f) * aw

    fun textBox(e: Element, aw: Int, ah: Int, yOverride: Float? = null): Pair<Box, Int> {
        val p = px(e, aw, ah)
        val maxChars = max(1, (maxWidthUnits(e, aw) / (CHAR_W * p)).toInt())
        val lines = wrapCount(e.text, maxChars)
        val widest = (lines.maxOfOrNull { it.length } ?: 1) * CHAR_W * p
        val y = yOverride ?: e.y
        val wN = widest / aw
        val top = y - 0.85f * p / ah
        val bottom = y + (((lines.size - 1) * 1.2f) + 0.28f) * p / ah
        return Box(e.x - wN / 2f, top, e.x + wN / 2f, bottom) to lines.size
    }

    private fun cardBox(e: Element) = Box(e.x - e.w / 2f, e.y - e.h / 2f, e.x + e.w / 2f, e.y + e.h / 2f)
    private fun isCard(e: Element) = e.type == "rect" && e.id.startsWith("card")

    // ------------------------------------------------------------------ colour

    fun contrast(a: String, b: String): Float {
        val la = rel(a); val lb = rel(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun rel(hex: String): Float {
        val c = hex.removePrefix("#").takeLast(6)
        if (c.length != 6) return 0f
        fun ch(i: Int): Float {
            val v = (c.substring(i, i + 2).toIntOrNull(16) ?: 0) / 255f
            return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * ch(0) + 0.7152f * ch(2) + 0.0722f * ch(4)
    }

    // ------------------------------------------------------------------ repair

    private fun layerRank(e: Element): Int = when (e.type) {
        "grid", "particles" -> 0
        "glow", "burst" -> 1
        "ring", "circle", "rect", "donut", "icon" -> 2
        "bars", "linechart", "nodes", "wave", "radar", "steps", "table", "quote", "ticker", "arcdial", "spotlight", "halftone", "scanline", "confetti", "beam", "ribbon", "compare", "heat", "spiral", "orbit", "stack", "badge", "barcode", "depthStack", "parallax", "extrusion", "perspectiveQuad", "perspectiveCard", "cube" -> 3
        "line", "arrow" -> 4
        "text" -> 6
        else -> 7                     // flash, sweep, glitch, grain, vignette overlays
    }

    private fun shiftY(e: Element, dy: Float): Element =
        e.copy(y = e.y + dy, anims = e.anims.map { if (it.prop == "y") it.copy(from = it.from + dy, to = it.to + dy) else it })

    fun repair(g: SceneGraph, aspectW: Int, aspectH: Int, durationMs: Long): Result {
        val fixes = ArrayList<String>()
        var els = g.elements

        // 1) cap the element count by dropping the most decorative first
        if (els.size > MAX_ELEMENTS) {
            val drop = els.sortedBy { layerRank(it) }.take(els.size - MAX_ELEMENTS).map { it.id }.toSet()
            els = els.filterNot { it.id in drop }; fixes += "dropped ${drop.size} decorative element(s)"
        }

        // 2) clamp animations into the scene
        els = els.map { e ->
            e.copy(anims = e.anims.map { a ->
                val end = min(a.endMs, durationMs); val start = min(a.startMs, end)
                if (end != a.endMs || start != a.startMs) a.copy(startMs = start, endMs = end) else a
            })
        }

        // 3) text: fit size / lines, then keep inside the content zone
        val cardBoxes0 = els.filter { isCard(it) }.map { cardBox(it) }
        els = els.map { e ->
            if (e.type != "text" || e.text.isBlank()) return@map e
            var cur = e
            var guard = 0
            while (guard++ < 24) {
                val (box, lines) = textBox(cur, aspectW, aspectH)
                val tooWide = box.r - box.l > 1f - 2 * SIDE_MARGIN
                val limit = if (cur.size >= 0.07f) 2 else 3
                if (lines <= limit && !tooWide) break
                if (cur.size <= MIN_TEXT_SIZE) {
                    val maxChars = max(1, (maxWidthUnits(cur, aspectW) / (CHAR_W * px(cur, aspectW, aspectH))).toInt())
                    val ls = wrapCount(cur.text, maxChars).take(limit).toMutableList()
                    ls[ls.lastIndex] = ls.last().dropLast(1).trimEnd() + "\u2026"
                    cur = cur.copy(text = ls.joinToString(" ")); fixes += "truncated text on ${cur.id}"; break
                }
                cur = cur.copy(size = max(MIN_TEXT_SIZE, cur.size * 0.93f))
            }
            // text that lives inside a card must fit inside that card
            run {
                var g2 = 0
                while (g2++ < 12 && cur.size > MIN_TEXT_SIZE) {
                    val tb = textBox(cur, aspectW, aspectH).first
                    val host = cardBoxes0.firstOrNull { it.contains(tb.cx, tb.cy) } ?: break
                    if (tb.t >= host.t + 0.004f && tb.b <= host.b - 0.004f && tb.l >= host.l && tb.r <= host.r) break
                    cur = cur.copy(size = max(MIN_TEXT_SIZE, cur.size * 0.93f))
                }
            }
            if (cur.size != e.size) fixes += "shrunk text ${e.id} ${"%.3f".format(e.size)}->${"%.3f".format(cur.size)}"
            // horizontal safe margin
            val (b, _) = textBox(cur, aspectW, aspectH)
            val half = (b.r - b.l) / 2f
            val nx = cur.x.coerceIn(SIDE_MARGIN + half, 1f - SIDE_MARGIN - half)
            if (nx != cur.x) { cur = cur.copy(x = nx); fixes += "moved ${cur.id} inside side margins" }
            cur
        }

        // 4) vertical bounds (captions zone) and collisions, a few relaxation passes
        repeat(6) {
            var moved = false
            val cards = els.filter { isCard(it) }.map { it to cardBox(it) }
            val texts = els.indices.filter { els[it].type == "text" && els[it].text.isNotBlank() }
            // order by top so the lower element yields
            val order = texts.sortedBy { textBox(els[it], aspectW, aspectH).first.t }
            for (i in order) {
                var e = els[i]
                var (box, _) = textBox(e, aspectW, aspectH)
                // push out of the caption zone / above the top
                if (box.b > CONTENT_BOTTOM) {
                    val dy = CONTENT_BOTTOM - box.b
                    if (box.t + dy >= CONTENT_TOP) { e = shiftY(e, dy); fixes += "moved ${e.id} out of caption zone"; moved = true }
                    else if (e.size > MIN_TEXT_SIZE) { e = e.copy(size = max(MIN_TEXT_SIZE, e.size * 0.9f)); moved = true }
                }
                box = textBox(e, aspectW, aspectH).first
                if (box.t < CONTENT_TOP) { e = shiftY(e, CONTENT_TOP - box.t); moved = true }
                els = els.toMutableList().also { it[i] = e }
            }
            val ord2 = texts.sortedBy { textBox(els[it], aspectW, aspectH).first.t }
            for (a in ord2.indices) for (bIdx in a + 1 until ord2.size) {
                val ia = ord2[a]; val ib = ord2[bIdx]
                val ba = textBox(els[ia], aspectW, aspectH).first; val bb = textBox(els[ib], aspectW, aspectH).first
                if (ba.intersects(bb, GAP)) {
                    val dy = ba.b + GAP - bb.t
                    val lower = els[ib]; val lb = textBox(lower, aspectW, aspectH).first
                    if (lb.b + dy <= CONTENT_BOTTOM) { els = els.toMutableList().also { it[ib] = shiftY(lower, dy) }; fixes += "separated ${lower.id} from ${els[ia].id}"; moved = true }
                    else if (ba.t - dy >= CONTENT_TOP) { els = els.toMutableList().also { it[ia] = shiftY(els[ia], -dy) }; moved = true }
                    else { els = els.toMutableList().also { it[ib] = lower.copy(size = max(MIN_TEXT_SIZE, lower.size * 0.88f)) }; moved = true }
                }
            }
            // text vs. card that does not contain it
            for (i in texts) {
                val e = els[i]; val tb = textBox(e, aspectW, aspectH).first
                for ((card, cb) in cards) {
                    if (cb.contains(tb.cx, tb.cy)) continue
                    if (tb.intersects(cb, 0f)) {
                        val dy = if (tb.cy < cb.cy) cb.t - GAP - tb.b else cb.b + GAP - tb.t
                        val nb = tb.copy(t = tb.t + dy, b = tb.b + dy)
                        if (nb.t >= CONTENT_TOP && nb.b <= CONTENT_BOTTOM) { els = els.toMutableList().also { it[i] = shiftY(e, dy) }; fixes += "moved ${e.id} off ${card.id}"; moved = true; break }
                    }
                }
            }
            if (!moved) return@repeat
        }

        // 5) contrast
        val bgL = listOf(g.bgTop, g.bgBottom)
        val cards = els.filter { isCard(it) }.map { it to cardBox(it) }
        els = els.map { e ->
            if (e.type != "text") return@map e
            val tb = textBox(e, aspectW, aspectH).first
            val container = cards.firstOrNull { it.second.contains(tb.cx, tb.cy) }?.first
            val backdrops = if (container != null) listOf(container.color) else bgL
            val worst = backdrops.minOf { contrast(e.color, it) }
            if (worst >= 4.5f) e else {
                val white = backdrops.minOf { contrast("#FFFFFF", it) }; val dark = backdrops.minOf { contrast("#0B0A14", it) }
                fixes += "fixed low contrast on ${e.id}"
                e.copy(color = if (white >= dark) "#FFFFFF" else "#0B0A14")
            }
        }

        // 6) z-order: shapes behind, text always on top
        els = els.withIndex().sortedWith(compareBy({ layerRank(it.value) }, { it.index })).map { it.value }

        return Result(g.copy(elements = els), fixes)
    }

    /** Problems left in [g] (empty when the layout is clean). Used by tests and as a gate for model-written graphs. */
    fun issues(g: SceneGraph, aspectW: Int, aspectH: Int, durationMs: Long): List<String> {
        val out = ArrayList<String>()
        val texts = g.elements.filter { it.type == "text" && it.text.isNotBlank() }
        val boxes = texts.map { it to textBox(it, aspectW, aspectH).first }
        val cards = g.elements.filter { isCard(it) }.map { cardBox(it) }
        boxes.forEach { (e, b) ->
            if (b.l < SIDE_MARGIN - 0.002f || b.r > 1f - SIDE_MARGIN + 0.002f) out += "${e.id}: text outside safe width"
            if (b.b > CONTENT_BOTTOM + 0.002f) out += "${e.id}: text in caption zone"
            if (b.t < CONTENT_TOP - 0.002f) out += "${e.id}: text above frame"
            if (e.size < MIN_TEXT_SIZE - 0.0005f) out += "${e.id}: font too small"
            val inCard = cards.firstOrNull { it.contains(b.cx, b.cy) }
            if (inCard != null && (b.t < inCard.t - 0.006f || b.b > inCard.b + 0.006f || b.l < inCard.l - 0.006f || b.r > inCard.r + 0.006f)) out += "${e.id}: text overflows its card"
            val backdrops = if (inCard != null) null else listOf(g.bgTop, g.bgBottom)
            if (backdrops != null && backdrops.minOf { contrast(e.color, it) } < 3f) out += "${e.id}: low contrast"
        }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            if (boxes[i].second.intersects(boxes[j].second)) out += "${boxes[i].first.id} overlaps ${boxes[j].first.id}"
        }
        if (g.elements.size > MAX_ELEMENTS) out += "too many elements (${g.elements.size})"
        val firstText = g.elements.indexOfFirst { it.type == "text" }
        val lastShape = g.elements.indexOfLast { it.type in setOf("rect", "circle", "ring", "bars", "linechart", "nodes", "donut") }
        if (firstText in 0 until lastShape) out += "text is drawn before a shape (may be hidden)"
        g.elements.forEach { e -> e.anims.forEach { a -> if (a.endMs > durationMs + 50) out += "${e.id}: animation outlasts scene" } }
        return out
    }
}
