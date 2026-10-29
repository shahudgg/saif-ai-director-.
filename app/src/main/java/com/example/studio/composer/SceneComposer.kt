package com.example.studio.composer

import com.example.studio.director.Anim
import com.example.studio.director.CameraMove
import com.example.studio.director.Element
import com.example.studio.director.SceneGraph
import kotlin.math.min

/**
 * Designs a [SceneGraph] for a [Beat] entirely on-device: no network, no model. Every intent has three layout variants
 * (picked by beat index so neighbours never look alike), each with entrance animation, continuous idle motion, an
 * animated backdrop and a camera move. The result always passes through [LayoutEngine.repair].
 */
object SceneComposer {

    fun variantCount(intent: Intent) = 3

    /** [accentOverride] ("#RRGGBB") makes every scene use the brand accent while backgrounds still vary. */
    fun compose(beat: Beat, aspect: String = "9:16", variant: Int = beat.index, accentOverride: String? = null, dna: StyleDNA? = null): SceneGraph {
        val (aw, ah) = LayoutEngine.parseAspect(aspect)
        val dur = beat.durationMs.coerceAtLeast(1200L)
        val base = dna?.palette(beat.index, beat.emotion) ?: Palettes.pick(beat.index, beat.emotion)
        val pal = if (accentOverride != null) base.copy(accent = accentOverride) else base
        val b = Builder(beat, pal, dur)
        val v = (variant + (dna?.variantShift ?: 0)).mod(3)
        b.backdrop(v)
        if (dna != null) b.dnaBackdrop(dna)
        b.iconWatermark()
        when (beat.intent) {
            Intent.HOOK -> b.hook(v)
            Intent.BIG_STAT -> b.bigStat(v)
            Intent.LIST -> if (beat.items.size >= 2) b.list(v) else b.statement(v)
            Intent.COMPARISON -> b.comparison(v)
            Intent.PROCESS_STEPS -> if (beat.items.size >= 2) b.steps(v) else b.statement(v)
            Intent.TIMELINE -> b.timeline(v)
            Intent.QUOTE -> b.quote(v)
            Intent.CHART_BAR -> b.chartBar(v)
            Intent.CHART_LINE -> b.chartLine(v)
            Intent.NETWORK_DIAGRAM -> b.network(v)
            Intent.MAP_LIKE_ZOOM -> b.map(v)
            Intent.MYTH_VS_FACT -> b.myth(v)
            Intent.QUESTION -> b.question(v)
            Intent.CTA -> b.cta(v)
            Intent.STATEMENT -> b.statement(v)
        }
        if (dna != null) b.ornaments(dna)
        b.vignette()
        val g = SceneGraph(pal.bgTop, pal.bgBottom, b.camera(), b.els.toList())
        return LayoutEngine.repair(g, aw, ah, dur).graph
    }

    private class Builder(val beat: Beat, val pal: Palette, val dur: Long) {
        val els = ArrayList<Element>()
        private var n = 0
        private fun id(p: String) = "$p${n++}"
        private fun t(v: Long) = min(v, dur)
        private fun add(e: Element): Element { els += e; return e }

        // ---- animation helpers (all times clamped into the scene)
        private fun rise(delay: Long, y: Float) = listOf(
            Anim("alpha", 0f, 1f, t(delay), t(delay + 350), "easeOut"),
            Anim("y", y + 0.035f, y, t(delay), t(delay + 520), "expoOut"))
        private fun pop(delay: Long) = listOf(
            Anim("alpha", 0f, 1f, t(delay), t(delay + 200), "easeOut"),
            Anim("scale", 0.55f, 1f, t(delay), t(delay + 480), "backOut"))
        private fun fade(delay: Long) = listOf(Anim("alpha", 0f, 1f, t(delay), t(delay + 400), "easeOut"))
        private fun grow(delay: Long, ms: Long = 900) = listOf(
            Anim("alpha", 0f, 1f, t(delay), t(delay + 150), "easeOut"),
            Anim("progress", 0f, 1f, t(delay), t(delay + ms), "expoOut"))
        private fun constAlpha(a: Float) = listOf(Anim("alpha", a, a, 0, 1, "linear"))
        /** Continuous idle motion: gentle scale breathing for the second half of the scene. */
        private fun breathe() = listOf(
            Anim("scale", 1f, 1.05f, t(dur * 45 / 100), t(dur * 72 / 100), "easeInOut"),
            Anim("scale", 1.05f, 1f, t(dur * 72 / 100), t(dur), "easeInOut"))
        private fun stagger(i: Int, step: Long = 220L) = 120L + i * step

        // ---- element helpers
        fun text(txt: String, x: Float, y: Float, w: Float, size: Float, color: String = pal.text, delay: Long = 120, style: String = "rise", fx: String = "", idle: Boolean = false) =
            add(Element(id("txt"), "text", x, y, w, 0.1f, color = color, text = txt, size = size, fx = fx,
                anims = (when (style) { "pop" -> pop(delay); "fade" -> fade(delay); else -> rise(delay, y) }) + (if (idle) breathe() else emptyList())))
        fun card(x: Float, y: Float, w: Float, h: Float, color: String = pal.card, delay: Long = 0) =
            add(Element(id("card"), "rect", x, y, w, h, color = color, anims = listOf(
                Anim("alpha", 0f, 1f, t(delay), t(delay + 300), "easeOut"), Anim("scale", 0.92f, 1f, t(delay), t(delay + 450), "backOut"))))
        fun shape(type: String, x: Float, y: Float, w: Float, h: Float, color: String, anims: List<Anim>, strokeW: Float = 0.004f, name: String = type) =
            add(Element(id(name), type, x, y, w, h, color = color, strokeW = strokeW, anims = anims))
        fun line(type: String, x: Float, y: Float, x2: Float, y2: Float, color: String, delay: Long, ms: Long = 800) =
            add(Element(id(type), type, x, y, color = color, x2 = x2, y2 = y2, strokeW = 0.006f, anims = grow(delay, ms)))
        fun glow(x: Float, y: Float, size: Float, color: String, a: Float = 0.35f) =
            shape("glow", x, y, size, size, color, constAlpha(a))

        // ---- shared backdrop / finishing
        fun backdrop(v: Int) {
            add(Element(id("fx"), "particles", 0.5f, 0.5f, 1f, 1f, color = pal.accent, count = 24 + v * 6, seed = 7 + beat.index * 13, anims = constAlpha(0.4f)))
            if (v == 1) glow(0.85f, 0.15f, 0.8f, pal.accent2, 0.22f) else if (v == 2) glow(0.12f, 0.6f, 0.8f, pal.accent, 0.2f)
        }
        fun vignette() { add(Element(id("fx"), "vignette", 0.5f, 0.5f, 1f, 1f, anims = constAlpha(1f))) }

        /** A large faint topic glyph behind the headline (chart for trading, shield for security...). Only where one big text sits in the centre. */
        fun iconWatermark() {
            if (beat.intent !in setOf(Intent.HOOK, Intent.STATEMENT, Intent.QUESTION, Intent.BIG_STAT, Intent.CTA)) return
            val name = IconPicker.forBeat(beat) ?: return
            add(Element(id("icon"), "icon", 0.5f, 0.31f, 0.62f, 0.62f, color = pal.accent, text = name, strokeW = 0.004f, anims = listOf(
                Anim("alpha", 0f, 0.2f, 0, t(700), "easeOut"),
                Anim("scale", 0.8f, 1f, 0, t(900), "backOut"),
                Anim("rotation", -8f, 0f, 0, t(1100), "expoOut"))))
        }

        /** Extra backdrop chosen by the video's [StyleDNA], so different videos get different atmospheres. */
        fun dnaBackdrop(dna: StyleDNA) {
            when (dna.backdrop) {
                1 -> add(Element(id("fx"), "grid", 0.5f, 0.38f, 0.96f, 0.70f, color = pal.accent, count = 9, strokeW = 0.002f, anims = constAlpha(0.13f)))
                2 -> {
                    shape("ring", 0.86f, 0.14f, 0.55f, 0.55f, pal.accent2, grow(0, 1400), 0.003f, name = "fx")
                    shape("ring", 0.14f, 0.62f, 0.42f, 0.42f, pal.accent, grow(250, 1400), 0.003f, name = "fx")
                }
                3 -> { glow(0.18f, 0.14f, 0.95f, pal.accent, 0.24f); glow(0.86f, 0.58f, 0.85f, pal.accent2, 0.20f) }
                4 -> shape("sweep", 0.5f, 0.5f, 1f, 1f, "#FFFFFF", grow(200, min(dur, 1600L)), name = "fx")
                else -> {}
            }
        }

        /** Finishing touches: scene progress bar and corner brackets. */
        fun ornaments(dna: StyleDNA) {
            val bar = dna.ornament == 0 || dna.ornament == 2 || dna.ornament == 3
            val brackets = dna.ornament == 1 || dna.ornament == 3
                        if (bar) {
                val f = ((beat.index + 1).toFloat() / beat.total.coerceAtLeast(1)).coerceIn(0.05f, 1f)
                add(Element(id("bar"), "rect", 0.5f, 0.012f, 0.9f, 0.006f, color = pal.muted, anims = constAlpha(0.3f)))
                add(Element(id("bar"), "rect", 0.5f, 0.012f, 0.9f, 0.006f, color = pal.accent,
                    anims = listOf(Anim("alpha", 0f, 1f, 0, t(200), "easeOut"), Anim("progress", 0f, f, 0, t(900), "expoOut"))))
            }
            if (brackets) {
                val c = pal.accent2
                line("line", 0.04f, 0.035f, 0.11f, 0.035f, c, 100, 400); line("line", 0.04f, 0.035f, 0.04f, 0.05f, c, 100, 400)
                line("line", 0.96f, 0.035f, 0.89f, 0.035f, c, 140, 400); line("line", 0.96f, 0.035f, 0.96f, 0.05f, c, 140, 400)
            }
        }
        fun camera(): CameraMove = when (beat.index.mod(4)) {
            0 -> CameraMove(1.0f, 1.07f)
            1 -> CameraMove(1.07f, 1.0f)
            2 -> CameraMove(1.04f, 1.04f, 0.015f, -0.015f)
            else -> CameraMove(1.04f, 1.06f, -0.015f, 0.015f, 0f, -0.01f)
        }

        private fun splitHalf(s: String): Pair<String, String> {
            val ws = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (ws.size < 2) return "" to s
            val k = (ws.size + 1) / 2
            return ws.take(k - if (ws.size > 3) 0 else 1).joinToString(" ") to ws.drop(k - if (ws.size > 3) 0 else 1).joinToString(" ")
        }
        private fun label(max: Int = 4) = LocalBeatAnalyzer.headline(beat.support.ifBlank { beat.headline }, max)
        private fun pct(): Float = beat.numbers.firstOrNull()?.takeIf { beat.numberTokens.firstOrNull()?.contains('%') == true || it <= 100f }?.coerceIn(0f, 100f) ?: 100f

        // ============================================================ intents

        fun hook(v: Int) {
            val head = beat.headline.uppercase()
            when (v) {
                0 -> {
                    glow(0.5f, 0.31f, 0.95f, pal.accent, 0.35f)
                    shape("ring", 0.5f, 0.31f, 0.72f, 0.72f, pal.accent2, grow(0, 1000), 0.006f)
                    text(head, 0.5f, 0.33f, 0.84f, 0.085f, delay = 100, style = "pop", fx = "words", idle = true)
                    add(Element(id("bar"), "rect", 0.5f, 0.50f, 0.5f, 0.008f, color = pal.accent2, anims = grow(650, 600)))
                    if (beat.support.isNotBlank() && beat.support != beat.headline) text(label(8), 0.5f, 0.58f, 0.8f, 0.034f, pal.muted, 900, "fade")
                }
                1 -> {
                    shape("flash", 0.5f, 0.5f, 1f, 1f, pal.accent2, listOf(Anim("alpha", 0.7f, 0f, 0, t(380), "easeOut")))
                    val (a, b) = splitHalf(head)
                    if (a.isNotBlank()) text(a, 0.5f, 0.22f, 0.84f, 0.05f, pal.accent2, 120)
                    text(b, 0.5f, 0.35f, 0.84f, 0.09f, delay = 280, style = "pop", idle = true)
                    line("arrow", 0.5f, 0.47f, 0.5f, 0.58f, pal.accent, 800, 500)
                }
                else -> {
                    add(Element(id("fx"), "grid", 0.5f, 0.34f, 0.92f, 0.5f, color = pal.accent, count = 10, strokeW = 0.002f, anims = constAlpha(0.22f)))
                    text(head, 0.5f, 0.30f, 0.84f, 0.08f, delay = 100, style = "pop", fx = "glow", idle = true)
                    listOf(0.35f, 0.5f, 0.65f).forEachIndexed { i, x -> shape("circle", x, 0.50f, 0.045f, 0.045f, pal.accent2, pop(500 + i * 160L), 0.03f) }
                    shape("sweep", 0.5f, 0.5f, 1f, 1f, "#FFFFFF", grow(300, min(dur, 1400L)), name = "fx")
                }
            }
        }

        fun bigStat(v: Int) {
            val num = beat.numberTokens.firstOrNull() ?: beat.headline
            val lab = label(5)
            when (v) {
                0 -> {
                    shape("donut", 0.5f, 0.33f, 0.64f, 0.64f, pal.accent, grow(0, 1200), 0.006f)
                    text(num, 0.5f, 0.375f, 0.5f, 0.12f, pal.text, 100, "pop", "counter", true).also { els[els.lastIndex] = it.copy(values = listOf(0f)) }
                    text(lab, 0.5f, 0.58f, 0.82f, 0.042f, delay = 550)
                }
                1 -> {
                    text(num, 0.5f, 0.27f, 0.84f, 0.15f, pal.accent, 100, "pop", "counter", true)
                    add(Element(id("track"), "rect", 0.5f, 0.42f, 0.72f, 0.034f, color = pal.card, anims = fade(150)))
                    add(Element(id("fill"), "rect", 0.5f, 0.42f, 0.72f, 0.034f, color = pal.accent2,
                        anims = listOf(Anim("alpha", 0f, 1f, 200, 350, "easeOut"), Anim("progress", 0f, pct() / 100f, 200, t(1300), "expoOut"))))
                    text(lab, 0.5f, 0.55f, 0.82f, 0.042f, delay = 600)
                }
                else -> {
                    glow(0.5f, 0.33f, 1.0f, pal.accent, 0.4f)
                    shape("burst", 0.5f, 0.34f, 0.85f, 0.85f, pal.accent2, grow(0, 800), 0.004f)
                    text(num, 0.5f, 0.39f, 0.84f, 0.15f, pal.text, 150, "pop", "counter", true)
                    text(lab, 0.5f, 0.56f, 0.82f, 0.042f, delay = 600)
                }
            }
        }

        fun list(v: Int) {
            val items = beat.items.take(4)
            when (v) {
                0 -> items.forEachIndexed { i, s ->
                    val y = 0.14f + i * 0.125f + 0.05f
                    card(0.5f, y, 0.84f, 0.10f, delay = stagger(i))
                    shape("circle", 0.14f, y, 0.06f, 0.06f, pal.accent, pop(stagger(i) + 120), 0.03f)
                    text("${i + 1}", 0.14f, y + 0.012f, 0.06f, 0.034f, "#FFFFFF", stagger(i) + 160, "pop")
                    text(s, 0.56f, y + 0.012f, 0.62f, 0.040f, delay = stagger(i) + 180)
                }
                1 -> {
                    text(beat.headline, 0.5f, 0.14f, 0.84f, 0.048f, pal.accent2, 100)
                    items.forEachIndexed { i, s ->
                        val y = 0.27f + i * 0.10f
                        shape("ring", 0.11f, y - 0.012f, 0.05f, 0.05f, pal.good, grow(stagger(i), 500), 0.004f)
                        text(s, 0.55f, y, 0.74f, 0.045f, delay = stagger(i) + 100)
                    }
                }
                else -> {
                    val pos = listOf(0.28f to 0.26f, 0.72f to 0.26f, 0.28f to 0.44f, 0.72f to 0.44f)
                    items.forEachIndexed { i, s ->
                        val (x, y) = if (items.size == 3 && i == 2) 0.5f to 0.44f else pos[i]
                        card(x, y, if (items.size == 3 && i == 2) 0.5f else 0.42f, 0.15f, delay = stagger(i))
                        text(s, x, y + 0.01f, 0.36f, 0.036f, delay = stagger(i) + 150, style = "pop")
                    }
                }
            }
        }

        private fun sides(): Pair<String, String> {
            val s = beat.support
            val parts = s.split(Regex("\\s+(?:vs\\.?|versus|jabki|but|lekin|ya|while|whereas)\\s+", RegexOption.IGNORE_CASE), limit = 2)
            if (parts.size == 2) return LocalBeatAnalyzer.headline(parts[0], 3) to LocalBeatAnalyzer.headline(parts[1], 3)
            if (beat.items.size >= 2) return beat.items[0] to beat.items[1]
            val k = beat.keywords
            return (k.getOrNull(0) ?: "Before") to (k.getOrNull(1) ?: "After")
        }

        fun comparison(v: Int) {
            val (a, b) = sides()
            val useBars = v == 2 && beat.numbers.size >= 2
            if (v == 2 && !useBars) return comparison(0)
            when (v) {
                0 -> {
                    text(beat.headline, 0.5f, 0.12f, 0.84f, 0.04f, pal.muted, 80)
                    card(0.27f, 0.34f, 0.42f, 0.34f, delay = 100); card(0.73f, 0.34f, 0.42f, 0.34f, delay = 220)
                    text(a, 0.27f, 0.345f, 0.36f, 0.05f, pal.accent, 300, "pop"); text(b, 0.73f, 0.345f, 0.36f, 0.05f, pal.accent2, 420, "pop")
                    shape("circle", 0.5f, 0.34f, 0.10f, 0.10f, pal.accent, pop(560), 0.03f)
                    text("VS", 0.5f, 0.352f, 0.10f, 0.032f, "#FFFFFF", 600, "pop")
                }
                1 -> {
                    card(0.5f, 0.22f, 0.84f, 0.17f, delay = 100); card(0.5f, 0.47f, 0.84f, 0.17f, delay = 260)
                    text(a, 0.5f, 0.235f, 0.76f, 0.05f, pal.accent, 300, "pop"); text(b, 0.5f, 0.485f, 0.76f, 0.05f, pal.accent2, 460, "pop")
                    shape("circle", 0.5f, 0.345f, 0.10f, 0.10f, pal.accent, pop(600), 0.03f)
                    text("VS", 0.5f, 0.357f, 0.10f, 0.032f, "#FFFFFF", 640, "pop")
                }
                else -> {
                    text(beat.headline, 0.5f, 0.13f, 0.84f, 0.045f, pal.muted, 80)
                    add(Element(id("bars"), "bars", 0.5f, 0.36f, 0.62f, 0.30f, color = pal.accent, values = beat.numbers.take(2), labels = listOf(a, b),
                        anims = grow(150, 1300)))
                }
            }
        }

        fun steps(v: Int) {
            val items = beat.items.take(4)
            val horizontal = v == 1 && items.size <= 3 && items.all { it.length <= 14 }
            when {
                horizontal -> {
                    val n = items.size
                    items.forEachIndexed { i, s ->
                        val x = (i + 0.5f) / n
                        shape("circle", x, 0.30f, 0.11f, 0.11f, pal.accent, pop(stagger(i, 300)), 0.03f)
                        text("${i + 1}", x, 0.312f, 0.11f, 0.04f, "#FFFFFF", stagger(i, 300) + 80, "pop")
                        text(s, x, 0.44f, 0.28f, 0.034f, delay = stagger(i, 300) + 160)
                        if (i < n - 1) line("arrow", x + 0.085f, 0.30f, (i + 1.5f) / n - 0.085f, 0.30f, pal.accent2, stagger(i, 300) + 250, 400)
                    }
                }
                v == 2 -> items.forEachIndexed { i, s ->
                    val y = 0.14f + i * 0.145f + 0.045f
                    card(0.5f, y, 0.80f, 0.09f, delay = stagger(i, 260))
                    text(s, 0.5f, y + 0.012f, 0.72f, 0.04f, delay = stagger(i, 260) + 150)
                    if (i < items.size - 1) line("arrow", 0.5f, y + 0.05f, 0.5f, y + 0.092f, pal.accent2, stagger(i, 260) + 250, 300)
                }
                else -> {
                    line("line", 0.16f, 0.14f, 0.16f, 0.16f + (items.size - 1) * 0.13f + 0.02f, pal.muted, 0, 1100)
                    items.forEachIndexed { i, s ->
                        val y = 0.16f + i * 0.13f
                        shape("circle", 0.16f, y, 0.06f, 0.06f, pal.accent, pop(stagger(i)), 0.03f)
                        text("${i + 1}", 0.16f, y + 0.012f, 0.06f, 0.034f, "#FFFFFF", stagger(i) + 60, "pop")
                        text(s, 0.58f, y + 0.012f, 0.62f, 0.042f, delay = stagger(i) + 140)
                    }
                }
            }
        }

        fun timeline(v: Int) {
            val years = beat.numberTokens.filter { Regex("^(19|20)\\d{2}$").matches(it) }.take(4)
            val labels = (beat.items.ifEmpty { beat.keywords }).take(years.size)
            val n = years.size.coerceAtLeast(1)
            fun lab(i: Int) = labels.getOrNull(i) ?: ""
            if (years.size < 2) return statement(v)
            when (v) {
                0 -> {
                    line("line", 0.08f, 0.38f, 0.92f, 0.38f, pal.muted, 0, 1200)
                    years.forEachIndexed { i, yv ->
                        val x = 0.14f + i * (0.72f / (n - 1))
                        shape("circle", x, 0.38f, 0.045f, 0.045f, pal.accent, pop(300 + i * 280L), 0.03f)
                        text(yv, x, 0.31f, 0.22f, 0.046f, pal.accent2, 320 + i * 280L, "pop")
                        if (lab(i).isNotBlank()) text(lab(i), x, 0.47f, 0.22f, 0.03f, delay = 420 + i * 280L)
                    }
                }
                1 -> {
                    line("line", 0.2f, 0.12f, 0.2f, 0.60f, pal.muted, 0, 1200)
                    years.forEachIndexed { i, yv ->
                        val y = 0.16f + i * (0.40f / (n - 1).coerceAtLeast(1))
                        shape("circle", 0.2f, y, 0.045f, 0.045f, pal.accent, pop(300 + i * 280L), 0.03f)
                        text(yv, 0.40f, y + 0.012f, 0.26f, 0.05f, pal.accent2, 320 + i * 280L, "pop")
                        if (lab(i).isNotBlank()) text(lab(i), 0.72f, y + 0.01f, 0.36f, 0.032f, delay = 420 + i * 280L)
                    }
                }
                else -> years.forEachIndexed { i, yv ->
                    val x = 0.14f + i * (0.72f / (n - 1))
                    val h = 0.10f + 0.07f * i
                    shape("rect", x, 0.56f - h / 2, 0.16f, h, pal.accent, grow(200 + i * 260L, 700), name = "step")
                    text(yv, x, 0.56f - h - 0.018f, 0.2f, 0.04f, pal.accent2, 350 + i * 260L, "pop")
                    if (lab(i).isNotBlank()) text(lab(i), x, 0.61f, 0.2f, 0.028f, pal.muted, 450 + i * 260L)
                }
            }
        }

        fun quote(v: Int) {
            val q = beat.support.trim().trim('"', '\u201C', '\u201D').ifBlank { beat.headline }
            when (v) {
                0 -> {
                    text("\u201C", 0.5f, 0.24f, 0.3f, 0.2f, pal.accent, 60, "pop", "glow")
                    text(q, 0.5f, 0.34f, 0.82f, 0.046f, delay = 300, style = "fade", idle = true)
                    add(Element(id("bar"), "rect", 0.5f, 0.56f, 0.3f, 0.007f, color = pal.accent2, anims = grow(900, 600)))
                }
                1 -> {
                    card(0.5f, 0.34f, 0.86f, 0.42f, delay = 60)
                    add(Element(id("bar"), "rect", 0.1f, 0.34f, 0.012f, 0.34f, color = pal.accent, anims = grow(200, 700)))
                    text(q, 0.5f, 0.28f, 0.74f, 0.046f, delay = 350, style = "fade")
                }
                else -> {
                    shape("ring", 0.5f, 0.34f, 0.84f, 0.84f, pal.accent, grow(0, 1200), 0.005f)
                    text(q, 0.5f, 0.31f, 0.58f, 0.042f, delay = 400, style = "fade", idle = true)
                }
            }
        }

        private fun chartLabels(): List<String> = (beat.items.ifEmpty { beat.keywords }).take(beat.numbers.size)

        fun chartBar(v: Int) {
            val vals = beat.numbers.take(6)
            if (vals.size < 2) return statement(v)
            val labels = chartLabels().ifEmpty { vals.indices.map { "${it + 1}" } }
            when {
                v == 2 && vals.size <= 3 && vals.all { it <= 100f } -> vals.forEachIndexed { i, x ->
                    val cx = (i + 0.5f) / vals.size
                    shape("donut", cx, 0.34f, 0.26f, 0.26f, if (i % 2 == 0) pal.accent else pal.accent2, grow(stagger(i, 280), 1000), 0.005f)
                    text(beat.numberTokens.getOrNull(i) ?: x.toInt().toString(), cx, 0.35f, 0.22f, 0.04f, delay = stagger(i, 280) + 200, style = "pop")
                    text(labels.getOrNull(i) ?: "", cx, 0.52f, 0.28f, 0.03f, pal.muted, stagger(i, 280) + 300)
                }
                else -> {
                    text(beat.headline, 0.5f, 0.12f, 0.84f, 0.048f, pal.muted, 80)
                    if (v == 1) add(Element(id("fx"), "grid", 0.5f, 0.37f, 0.84f, 0.34f, color = pal.accent, count = 6, strokeW = 0.002f, anims = constAlpha(0.2f)))
                    add(Element(id("bars"), "bars", 0.5f, 0.37f, 0.8f, 0.34f, color = pal.accent, values = vals, labels = labels, anims = grow(150, 1500)))
                }
            }
        }

        fun chartLine(v: Int) {
            val vals = beat.numbers.take(10)
            if (vals.size < 2) return statement(v)
            text(beat.headline, 0.5f, 0.12f, 0.84f, 0.048f, pal.muted, 80)
            if (v >= 1) add(Element(id("fx"), "grid", 0.5f, 0.38f, 0.84f, 0.32f, color = pal.accent, count = 6, strokeW = 0.002f, anims = constAlpha(0.2f)))
            add(Element(id("chart"), "linechart", 0.5f, 0.38f, 0.82f, 0.30f, color = pal.accent2, strokeW = 0.008f, values = vals, anims = grow(150, 1700)))
            if (v == 1) line("arrow", 0.2f, 0.58f, 0.8f, 0.2f, pal.good, 1700, 500)
            if (v == 2) glow(0.9f, 0.23f, 0.35f, pal.accent2, 0.4f)
        }

        fun network(v: Int) {
            val layers = when (v) { 0 -> listOf(3f, 4f, 4f, 2f); 1 -> listOf(2f, 5f, 3f, 1f); else -> listOf(4f, 3f, 3f, 3f, 2f) }
            text(beat.headline, 0.5f, 0.13f, 0.84f, 0.055f, delay = 80, style = "pop")
            add(Element(id("nodes"), "nodes", 0.5f, 0.37f, 0.78f, 0.32f, color = pal.accent, color2 = pal.accent2, values = layers, seed = beat.index + 3,
                anims = listOf(Anim("alpha", 0f, 1f, 100, 400, "easeOut"), Anim("progress", 0f, 1f, 100, t(1500), "easeInOut"))))
            text("INPUT", 0.13f, 0.58f, 0.2f, 0.03f, pal.muted, 900, "fade"); text("OUTPUT", 0.87f, 0.58f, 0.2f, 0.03f, pal.muted, 1100, "fade")
            if (v == 2) shape("ring", 0.5f, 0.37f, 0.9f, 0.9f, pal.accent2, grow(400, 1400), 0.003f)
        }

        fun map(v: Int) {
            text(beat.headline, 0.5f, 0.12f, 0.84f, 0.055f, delay = 80, style = "pop")
            when (v) {
                0 -> {
                    add(Element(id("fx"), "grid", 0.5f, 0.38f, 0.9f, 0.40f, color = pal.accent, count = 12, strokeW = 0.002f, anims = constAlpha(0.28f)))
                    val pts = listOf(0.25f to 0.30f, 0.55f to 0.42f, 0.78f to 0.30f)
                    pts.forEachIndexed { i, (x, y) -> glow(x, y, 0.22f, pal.accent2, 0.5f); shape("circle", x, y, 0.035f, 0.035f, pal.accent2, pop(400 + i * 300L), 0.03f) }
                    line("line", pts[0].first, pts[0].second, pts[1].first, pts[1].second, pal.accent, 700, 600)
                    line("line", pts[1].first, pts[1].second, pts[2].first, pts[2].second, pal.accent, 1100, 600)
                }
                1 -> listOf(0, 1, 2).forEach { i ->
                    shape("ring", 0.5f, 0.36f, 0.35f + i * 0.22f, 0.35f + i * 0.22f, if (i % 2 == 0) pal.accent else pal.accent2, grow(i * 280L, 900), 0.004f)
                }.also { glow(0.5f, 0.36f, 0.5f, pal.accent, 0.5f) }
                else -> {
                    val pts = listOf(0.2f to 0.28f, 0.5f to 0.46f, 0.8f to 0.3f)
                    pts.forEachIndexed { i, (x, y) -> shape("circle", x, y, 0.05f, 0.05f, pal.accent, pop(300 + i * 280L), 0.03f) }
                    line("arrow", pts[0].first + 0.04f, pts[0].second + 0.02f, pts[1].first - 0.04f, pts[1].second - 0.02f, pal.accent2, 700, 500)
                    line("arrow", pts[1].first + 0.04f, pts[1].second - 0.02f, pts[2].first - 0.04f, pts[2].second + 0.02f, pal.accent2, 1100, 500)
                }
            }
        }

        fun myth(v: Int) {
            val s = beat.support
            val split = Regex("(?i)\\b(?:fact|sach|truth|haqiqat)\\b\\s*[:\\-–]?").find(s)
            val mythTxt = if (split != null) LocalBeatAnalyzer.headline(s.substring(0, split.range.first).replace(Regex("(?i)\\b(?:myth|mith)\\b\\s*[:\\-–]?"), ""), 6).ifBlank { beat.headline } else beat.headline
            val factTxt = if (split != null) LocalBeatAnalyzer.headline(s.substring(split.range.last + 1), 6).ifBlank { label(6) } else label(6)
            val badC = "#3A1A28"; val goodC = "#12382B"
            when (v) {
                0, 2 -> {
                    val factDelay = if (v == 2) dur * 45 / 100 else 700L
                    card(0.5f, 0.22f, 0.84f, 0.2f, badC, 80)
                    text("MYTH", 0.5f, 0.155f, 0.5f, 0.036f, pal.bad, 200, "pop"); text(mythTxt, 0.5f, 0.235f, 0.76f, 0.042f, delay = 260)
                    add(Element(id("strike"), "line", 0.14f, 0.225f, color = pal.bad, x2 = 0.86f, y2 = 0.225f, strokeW = 0.008f, anims = grow(min(factDelay - 150L, 900L).coerceAtLeast(300L), 400)))
                    card(0.5f, 0.47f, 0.84f, 0.2f, goodC, factDelay)
                    text("FACT", 0.5f, 0.405f, 0.5f, 0.036f, pal.good, factDelay + 120, "pop"); text(factTxt, 0.5f, 0.485f, 0.76f, 0.042f, delay = factDelay + 200)
                }
                else -> {
                    card(0.27f, 0.34f, 0.42f, 0.4f, badC, 80); card(0.73f, 0.34f, 0.42f, 0.4f, goodC, 380)
                    text("MYTH", 0.27f, 0.20f, 0.36f, 0.036f, pal.bad, 200, "pop"); text(mythTxt, 0.27f, 0.32f, 0.36f, 0.04f, delay = 280)
                    text("FACT", 0.73f, 0.20f, 0.36f, 0.036f, pal.good, 500, "pop"); text(factTxt, 0.73f, 0.32f, 0.36f, 0.04f, delay = 580)
                }
            }
        }

        fun question(v: Int) {
            val q = beat.support.ifBlank { beat.headline }
            when (v) {
                0 -> {
                    text("?", 0.5f, 0.34f, 0.4f, 0.20f, pal.accent, 60, "pop", "glow", true)
                    text(q, 0.5f, 0.50f, 0.84f, 0.05f, delay = 380)
                }
                1 -> {
                    shape("ring", 0.5f, 0.27f, 0.50f, 0.50f, pal.accent2, grow(0, 1000), 0.005f)
                    text("?", 0.5f, 0.31f, 0.3f, 0.16f, pal.text, 200, "pop", "", true)
                    text(q, 0.5f, 0.52f, 0.84f, 0.05f, delay = 420)
                }
                else -> {
                    text(q, 0.5f, 0.16f, 0.84f, 0.052f, delay = 100, style = "pop")
                    listOf(0.4f, 0.5f, 0.6f).forEachIndexed { i, x -> shape("circle", x, 0.40f, 0.04f, 0.04f, pal.accent, pop(600 + i * 200L), 0.03f) }
                    text("?", 0.5f, 0.56f, 0.3f, 0.12f, pal.accent2, 900, "pop", "glow")
                }
            }
        }

        fun cta(v: Int) {
            val msg = beat.support.ifBlank { "Comment karke batao" }
            when (v) {
                0 -> {
                    add(Element(id("btn"), "rect", 0.5f, 0.28f, 0.7f, 0.10f, color = pal.accent, anims = pop(80) + breathe()))
                    text("FOLLOW", 0.5f, 0.295f, 0.62f, 0.06f, "#FFFFFF", 200, "pop")
                    text(msg, 0.5f, 0.46f, 0.84f, 0.044f, delay = 500)
                    line("arrow", 0.5f, 0.53f, 0.5f, 0.62f, pal.accent2, 900, 500)
                }
                1 -> {
                    add(Element(id("btn"), "rect", 0.29f, 0.28f, 0.4f, 0.09f, color = pal.accent, anims = pop(80)))
                    add(Element(id("btn"), "rect", 0.71f, 0.28f, 0.4f, 0.09f, color = pal.accent2, anims = pop(240)))
                    text("FOLLOW", 0.29f, 0.29f, 0.36f, 0.04f, "#FFFFFF", 200, "pop"); text("COMMENT", 0.71f, 0.29f, 0.36f, 0.04f, "#0B0A14", 360, "pop")
                    text(msg, 0.5f, 0.46f, 0.84f, 0.044f, delay = 600)
                }
                else -> {
                    line("arrow", 0.5f, 0.12f, 0.5f, 0.30f, pal.accent2, 0, 500)
                    text("FOLLOW", 0.5f, 0.38f, 0.8f, 0.08f, pal.accent, 300, "pop", "glow", true)
                    text(msg, 0.5f, 0.52f, 0.84f, 0.044f, delay = 650)
                }
            }
        }

        fun statement(v: Int) {
            val head = beat.headline.uppercase()
            when (v) {
                0 -> {
                    add(Element(id("bar"), "rect", 0.07f, 0.30f, 0.012f, 0.3f, color = pal.accent, anims = grow(0, 700)))
                    text(head, 0.52f, 0.30f, 0.78f, 0.07f, delay = 120, style = "pop", fx = "words", idle = true)
                    add(Element(id("bar"), "rect", 0.5f, 0.47f, 0.45f, 0.007f, color = pal.accent2, anims = grow(700, 600)))
                    if (beat.support.isNotBlank() && beat.support != beat.headline) text(label(8), 0.5f, 0.55f, 0.8f, 0.034f, pal.muted, 900, "fade")
                }
                1 -> {
                    glow(0.5f, 0.32f, 0.9f, pal.accent, 0.35f)
                    shape("ring", 0.5f, 0.32f, 0.78f, 0.78f, pal.accent2, grow(0, 1100), 0.005f)
                    text(head, 0.5f, 0.33f, 0.6f, 0.07f, delay = 160, style = "pop", idle = true)
                }
                else -> {
                    val c = pal.accent2
                    line("line", 0.1f, 0.12f, 0.22f, 0.12f, c, 0, 400); line("line", 0.1f, 0.12f, 0.1f, 0.2f, c, 0, 400)
                    line("line", 0.9f, 0.56f, 0.78f, 0.56f, c, 200, 400); line("line", 0.9f, 0.56f, 0.9f, 0.48f, c, 200, 400)
                    text(head, 0.5f, 0.32f, 0.74f, 0.07f, delay = 140, style = "pop", fx = "glow", idle = true)
                }
            }
        }
    }
}
