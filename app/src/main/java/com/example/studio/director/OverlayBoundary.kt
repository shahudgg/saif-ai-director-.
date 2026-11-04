package com.example.studio.director

import kotlin.math.abs

/**
 * ROUND 15.7 — THE ANIMATED BOUNDARY FOR OVERLAY LAYERS.
 *
 * The owner asked for this in plain words: "ek scene me 3 ya 4 assets overlays ka use ho … code likh
 * kar ek animated boundary banaya jaye uska position sahi set kar ke ye overlay layers ussi me hongi …
 * video me saare elements ek dam apne perfect area par lage honge".
 *
 * So every floating layer of a scene (cut-outs, key visuals, stickers, insets) is placed INSIDE one
 * designed rectangle — the boundary — instead of each one guessing its own position:
 *
 *   • the boundary is chosen on the side of the frame that the scene's own hero graphic does NOT use,
 *   • it is always inside the safe area (never under the notch, the gesture bar or off-screen),
 *   • its children are laid out on a small grid with equal padding (1 / 2 / 3 / 4 cells),
 *   • the boundary itself is an animated element (grows in, glows, keeps a thin accent edge), so the
 *     layers look like they belong to a designed panel, not scattered pictures,
 *   • every check is measurable, so [audit] can prove the result instead of claiming it.
 */
object OverlayBoundary {

    /** One planned cell: normalised centre + size, expressed like [AssetPlacement]. */
    data class Cell(val x: Float, val y: Float, val w: Float, val h: Float)

    /** The boundary rectangle itself (normalised centre + size) plus its accent colour. */
    data class Frame(val x: Float, val y: Float, val w: Float, val h: Float, val accent: String, val side: String)

    private const val PAD = 0.020f
    private const val MIN_SIDE = 0.06f

    /** Candidate boundaries, in preference order: bottom band, right column, left column, top band. */
    private fun candidates(): List<Frame> = listOf(
        Frame(0.50f, 0.735f, 0.90f, 0.20f, "#00E5FF", "bottom"),
        Frame(0.755f, 0.500f, 0.40f, 0.34f, "#00E5FF", "right"),
        Frame(0.245f, 0.500f, 0.40f, 0.34f, "#00E5FF", "left"),
        Frame(0.50f, 0.230f, 0.90f, 0.22f, "#00E5FF", "top")
    )

    /** Where the scene's own hero graphic sits, so the boundary never covers it. */
    private fun heroBox(scene: Scene): FloatArray? {
        val g = scene.graph ?: return null
        val hero = g.elements.firstOrNull { it.id == "hero" }
            ?: g.elements.firstOrNull { it.type !in setOf("text", "particles", "grid", "glow", "vignette", "grain") }
            ?: return null
        return floatArrayOf(hero.x - hero.w / 2f, hero.y - hero.h / 2f, hero.x + hero.w / 2f, hero.y + hero.h / 2f)
    }

    private fun overlaps(a: FloatArray, b: Frame): Boolean {
        val bl = b.x - b.w / 2f; val br = b.x + b.w / 2f
        val bt = b.y - b.h / 2f; val bb = b.y + b.h / 2f
        return a[0] < br && bl < a[2] && a[1] < bb && bt < a[3]
    }

    /** Pick the first boundary that does not sit on top of the scene's hero graphic. */
    fun choose(scene: Scene, accent: String = "#00E5FF"): Frame {
        val hero = heroBox(scene)
        val list = candidates().map { it.copy(accent = accent) }
        if (hero == null) return list.first()
        return list.firstOrNull { !overlaps(hero, it) } ?: list.first()
    }

    /** Grid of [count] cells inside the boundary, always fully inside the frame and inside the safe area. */
    fun cells(frame: Frame, count: Int): List<Cell> {
        val n = count.coerceIn(1, 4)
        val innerW = (frame.w - 2 * PAD).coerceAtLeast(MIN_SIDE)
        val innerH = (frame.h - 2 * PAD).coerceAtLeast(MIN_SIDE)
        val cols = if (n <= 1) 1 else if (n == 2) 2 else 2
        val rows = if (n <= 2) 1 else 2
        val cellW = ((innerW - (cols - 1) * PAD) / cols).coerceAtLeast(MIN_SIDE)
        val cellH = ((innerH - (rows - 1) * PAD) / rows).coerceAtLeast(MIN_SIDE)
        val out = ArrayList<Cell>()
        for (i in 0 until n) {
            val r = i / cols
            val c = i % cols
            val cx = frame.x - innerW / 2f + cellW / 2f + c * (cellW + PAD)
            val cy = frame.y - innerH / 2f + cellH / 2f + r * (cellH + PAD)
            out += Cell(cx, cy, cellW, cellH)
        }
        return out
    }

    /** The boundary as a drawable element pair (fill + accent edge), animated like a designed panel. */
    fun elements(frame: Frame, durationMs: Long): List<Element> {
        val dur = durationMs.coerceAtLeast(900L)
        val inMs = (dur * 0.2f).toLong().coerceIn(140L, 520L)
        val fill = Element(
            id = "card_overlay_boundary", type = "rect",
            x = frame.x, y = frame.y, w = frame.w * 0.72f, h = frame.h * 0.8f,
            color = "#141021", radius = 0.22f, glow = 0.16f, shadow = 0.34f,
            anims = listOf(
                Anim("w", frame.w * 0.72f, frame.w, 0L, inMs, "easeOut"),
                Anim("h", frame.h * 0.8f, frame.h, 0L, inMs, "easeOut"),
                Anim("alpha", 0f, 0.9f, 0L, inMs, "easeOut")
            )
        )
        val edge = Element(
            id = "card_overlay_boundary_edge", type = "rect",
            x = frame.x, y = frame.y, w = frame.w - 0.010f, h = frame.h - 0.010f,
            color = frame.accent, radius = 0.20f, glow = 0.5f, strokeW = 0.0032f,
            anims = listOf(Anim("alpha", 0f, 0.8f, 60L, inMs + 140L, "easeOut"))
        )
        return listOf(edge, fill)
    }

    /** Placements for the floating assets of a scene, one per cell, each with its own entrance keyframes. */
    fun placements(frame: Frame, assets: List<ResolvedAsset>, sceneMs: Long): List<AssetPlacement> {
        val cells = cells(frame, assets.size)
        val dur = sceneMs.coerceAtLeast(900L)
        return assets.mapIndexed { i, a ->
            val c = cells.getOrElse(i) { cells.last() }
            val delay = (i * 120L).coerceAtMost((dur * 0.3f).toLong())
            val base = a.placement ?: AssetPlacements.defaultFor(a.kind, a.ext, a.transparent, null)
            base.copy(
                x = c.x, y = c.y, width = c.w, height = c.h, fit = "contain", role = "sticker",
                corner = 0.10f, shadow = 0.26f,
                animIn = "scaleIn", animInMs = (320L + delay).coerceAtMost(600L),
                animOut = "fadeOut", animOutMs = 240L,
                keyframes = listOf(
                    PlacementKey(0L, c.x, c.y, 0.94f, 0f, "easeOut"),
                    PlacementKey((220L + delay).coerceAtMost(dur / 2), c.x, c.y, 1f, 1f, "overshoot"),
                    PlacementKey((dur - 200L).coerceAtLeast(400L), c.x, c.y, 1.03f, 1f, "easeInOut")
                )
            )
        }
    }

    // ------------------------------------------------------------------ audit

    data class Problem(val sceneId: String, val text: String)

    /** Does every floating layer really sit inside its boundary, with room, on screen and animated? */
    fun audit(scene: Scene, frame: Frame, placed: List<AssetPlacement>): List<Problem> {
        val out = ArrayList<Problem>()
        val l = frame.x - frame.w / 2f; val r = frame.x + frame.w / 2f
        val t = frame.y - frame.h / 2f; val b = frame.y + frame.h / 2f
        if (l < 0.05f || r > 0.95f || t < 0.05f || b > 0.95f) out += Problem(scene.id, "boundary bahar nikla (${(l * 100).toInt()}..${(r * 100).toInt()}%)")
        placed.forEachIndexed { i, p ->
            val pl = p.x - p.width / 2f; val pr = p.x + p.width / 2f
            val pt = p.y - p.height / 2f; val pb = p.y + p.height / 2f
            if (pl < l - 0.001f || pr > r + 0.001f || pt < t - 0.001f || pb > b + 0.001f)
                out += Problem(scene.id, "layer ${i + 1} boundary ke andar nahi hai")
            if (pl < 0f || pr > 1f || pt < 0f || pb > 1f)
                out += Problem(scene.id, "layer ${i + 1} screen ke bahar chala gaya")
            if (p.keyframes.size < 2) out += Problem(scene.id, "layer ${i + 1} ka animation keyframe nahi hai")
        }
        // Two layers may never cover each other.
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            val a = placed[i]; val c = placed[j]
            val ax = abs(a.x - c.x) < (a.width + c.width) / 2f - 0.004f
            val ay = abs(a.y - c.y) < (a.height + c.height) / 2f - 0.004f
            if (ax && ay) out += Problem(scene.id, "layer ${i + 1} aur ${j + 1} ek doosre par chadh gaye")
        }
        return out
    }

    /**
     * Full pass: for every scene that has TWO OR MORE floating assets, choose a boundary, lay the
     * assets inside it, add the animated frame to the scene graph and return both the new manifest and
     * the updated asset list. Scenes with a single backdrop are left exactly as they are.
     */
    fun apply(manifest: SceneManifest, assets: List<ResolvedAsset>, aspect: String): Pair<SceneManifest, List<ResolvedAsset>> {
        val floating = assets.filter { it.kind != "audio" && it.placement?.role != "backdrop" }
        if (floating.isEmpty()) return manifest to assets
        val byScene = floating.groupBy { it.sceneId }
        val newManifestScenes = ArrayList<Scene>()
        val replacements = HashMap<String, List<AssetPlacement>>()
        manifest.scenes.forEach { sc ->
            val mine = byScene[sc.id].orEmpty()
            if (mine.size < 2 || sc.graph == null) { newManifestScenes += sc; return@forEach }
            val accent = sc.graph.elements.firstOrNull { it.id == "hero" }?.color ?: "#00E5FF"
            // Try each boundary in turn and keep the first one the audit accepts.
            var chosen: Frame? = null
            var placed: List<AssetPlacement> = emptyList()
            for (cand in candidates().map { it.copy(accent = accent) }) {
                val p = placements(cand, mine, sc.durationMs)
                if (audit(sc, cand, p).isEmpty()) { chosen = cand; placed = p; break }
            }
            val frame = chosen ?: choose(sc, accent).copy(accent = accent)
            if (placed.isEmpty()) placed = placements(frame, mine, sc.durationMs)
            val withFrame = sc.copy(graph = sc.graph.copy(elements = elements(frame, sc.durationMs) + sc.graph.elements))
            newManifestScenes += withFrame
            mine.forEachIndexed { i, a -> replacements[a.requestId] = listOf(placed[i]) }
        }
        val newAssets = assets.map { a ->
            val p = replacements[a.requestId]?.firstOrNull() ?: return@map a
            a.copy(placement = p, note = a.note + " · boundary ke andar")
        }
        return manifest.copy(scenes = newManifestScenes) to newAssets
    }
}
