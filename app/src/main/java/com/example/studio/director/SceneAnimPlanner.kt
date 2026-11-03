package com.example.studio.director

import com.example.flow.LayerPlanner

/**
 * Round 16 — PER-SCENE MOTION + MOTION-GRAPHICS PLANNING (layer-aware).
 *
 * Two problems this solves:
 *
 *  1. Every scene moved the same way. The camera was whatever the assembler defaulted to, so a 10-scene
 *     video felt like one long clip. Here each scene gets its own camera move and its own graphics, and the
 *     planner refuses to repeat the previous scene's move or primitive.
 *  2. The graphics collided with the pictures. A Flow overlay sits in the right third, and a bar chart used
 *     to land on top of it. Here the planner receives the real overlay boxes of every scene, scores the four
 *     free zones against them, and puts the motion graphics in the emptiest one — so the background layer,
 *     the overlay layer and the code animations never fight.
 *
 * Everything produced here is a plain [Element]/[Anim]/[CameraMove] — the exact types the native renderer and
 * the existing SceneValidator already understand, so nothing unsafe is executed and everything stays editable.
 */

/** Normalized box used for collision scoring (0..1 of the frame). */
data class AnimBox(val x: Float, val y: Float, val w: Float, val h: Float) {

    val left: Float get() = x - w / 2f
    val right: Float get() = x + w / 2f
    val top: Float get() = y - h / 2f
    val bottom: Float get() = y + h / 2f
    val area: Float get() = (w.coerceAtLeast(0f)) * (h.coerceAtLeast(0f))

    /** How much of THIS box is covered by [other] (0 = free, 1 = fully covered). */
    fun coverage(other: AnimBox): Float {
        val ox = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
        val oy = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        val overlap = ox * oy
        return if (area <= 0.0001f) 0f else (overlap / area).coerceIn(0f, 1f)
    }
}

/** The plan for one scene: camera move, background motion name and the graphics elements to add. */
data class SceneMotion(
    val sceneId: String,
    val bgMotion: String,
    val camera: CameraMove,
    val elements: List<Element>,
    val freeZone: String,
    val note: String
)

object SceneAnimPlanner {

    /** Accent primitives that sit BEHIND the information graphics. */
    private val ACCENT_TYPES = listOf("glow", "beam", "sweep", "grain", "spotlight", "scanline", "halftone", "vignette")

    /** Primitives that carry information / rhythm in the empty part of the frame. */
    private val GRAPHIC_TYPES = listOf(
        "ring", "bars", "orbit", "spiral", "radar", "wave", "arcdial", "steps", "badge",
        "stack", "compare", "heat", "ticker", "quote", "table", "nodes", "linechart", "donut"
    )

    private val ACCENTS = listOf("#00E5FF", "#7B2BFF", "#FF3D81", "#34D399", "#FFB86B", "#FFE066")

    /** Camera move for a background motion name. Values stay conservative so no frame is ever empty. */
    fun cameraFor(bgMotion: String): CameraMove = when (bgMotion) {
        "pushIn" -> CameraMove(zoomFrom = 1.0f, zoomTo = 1.10f, dollyFrom = 0f, dollyTo = 0.10f)
        "pullOut" -> CameraMove(zoomFrom = 1.12f, zoomTo = 1.0f, dollyFrom = 0.12f, dollyTo = 0f)
        "panLeft" -> CameraMove(zoomFrom = 1.05f, zoomTo = 1.05f, panXFrom = 0.03f, panXTo = -0.03f)
        "panRight" -> CameraMove(zoomFrom = 1.05f, zoomTo = 1.05f, panXFrom = -0.03f, panXTo = 0.03f)
        "tiltUp" -> CameraMove(zoomFrom = 1.04f, zoomTo = 1.04f, panYFrom = 0.03f, panYTo = -0.03f)
        "parallaxDrift" -> CameraMove(zoomFrom = 1.02f, zoomTo = 1.08f, tiltFrom = -2f, tiltTo = 2f)
        else -> CameraMove(zoomFrom = 1.0f, zoomTo = 1.06f)
    }

    /**
     * Builds one [SceneMotion] per scene.
     *
     * @param overlays overlay boxes per scene id (from the layer plan) — the graphics avoid them.
     * @param aspect used only for the note text; every box is normalized so it works at any ratio.
     */
    fun plan(manifest: SceneManifest, aspect: String, overlays: Map<String, List<AnimBox>>): List<SceneMotion> {
        val scenes = manifest.scenes.sortedBy { it.startMs }
        val out = ArrayList<SceneMotion>(scenes.size)
        var lastAccent = ""
        var lastGraphic = ""
        var lastCamera = ""
        scenes.forEachIndexed { i, scene ->
            val boxes = overlays[scene.id].orEmpty().filter { it.area > 0.01f }
            val zone = freeZone(boxes)
            val accentType = rotate(ACCENT_TYPES, i * 3 + 1, lastAccent)
            val graphicType = rotate(GRAPHIC_TYPES, i * 7 + 2, lastGraphic)
            lastAccent = accentType
            lastGraphic = graphicType

            val existing = scene.graph?.camera
            val motionName = if (existing != null && !isDefaultCamera(existing)) {
                // The owner (or an earlier scene edit) already set a camera — never stomp on it.
                lastCamera.ifBlank { "keep" }
            } else {
                var pick = LayerPlanner.BG_MOTIONS[(i * 3 + 1) % LayerPlanner.BG_MOTIONS.size]
                if (pick == lastCamera) pick = LayerPlanner.BG_MOTIONS[(i * 3 + 2) % LayerPlanner.BG_MOTIONS.size]
                pick
            }
            lastCamera = motionName
            val dur = scene.durationMs.coerceAtLeast(900L)
            val elements = ArrayList<Element>(3)

            // 1. accent glow / beam / sweep — subtle, behind everything, wide.
            elements.add(
                Element(
                    id = "saif_fx_" + scene.id + "_accent",
                    type = accentType,
                    x = zone.x,
                    y = zone.y,
                    w = 0.62f,
                    h = 0.34f,
                    color = ACCENTS[(i + 2) % ACCENTS.size],
                    color2 = ACCENTS[(i + 4) % ACCENTS.size],
                    glow = 0.45f,
                    blend = "Screen",
                    seed = 11 + i,
                    anims = listOf(
                        Anim("alpha", 0f, 0.42f, 0L, (dur * 22 / 100).coerceAtLeast(120L), "easeOut"),
                        Anim("alpha", 0.42f, 0.22f, (dur * 70 / 100), dur, "easeInOut"),
                        Anim("scale", 0.94f, 1.04f, 0L, dur, "cubicInOut")
                    )
                )
            )

            // 2. information graphic in the emptiest zone — this is the scene's "motion graphics beat".
            val gx = zone.x
            val gy = zone.y
            val gw = if (zone.wide) 0.52f else 0.40f
            val gh = if (zone.wide) 0.24f else 0.30f
            elements.add(
                Element(
                    id = "saif_fx_" + scene.id + "_graphic",
                    type = graphicType,
                    x = gx,
                    y = gy,
                    w = gw,
                    h = gh,
                    color = ACCENTS[i % ACCENTS.size],
                    color2 = ACCENTS[(i + 3) % ACCENTS.size],
                    glow = 0.28f,
                    radius = 0.10f,
                    seed = 21 + i * 5,
                    values = graphicValues(graphicType, i),
                    anims = listOf(
                        Anim("alpha", 0f, 1f, 0L, (dur * 16 / 100).coerceIn(120L, 420L), "easeOut"),
                        Anim("scale", 0.86f, 1f, 0L, (dur * 26 / 100).coerceIn(200L, 620L), "backOut"),
                        Anim("y", gy + 0.035f, gy, 0L, (dur * 30 / 100).coerceIn(220L, 680L), "easeOut"),
                        Anim("alpha", 1f, 0.9f, (dur * 78 / 100), dur, "linear")
                    )
                )
            )

            // 3. a short kicker from the narration, only when the frame still has room and the zone is a top one.
            val kicker = kickerOf(scene.narration)
            if (kicker.isNotBlank() && zone.top && boxes.none { it.coverage(AnimBox(0.5f, 0.13f, 0.86f, 0.10f)) > 0.35f }) {
                elements.add(
                    Element(
                        id = "saif_fx_" + scene.id + "_kicker",
                        type = "text",
                        x = 0.5f,
                        y = 0.13f,
                        w = 0.84f,
                        h = 0.10f,
                        color = "#FFFFFF",
                        text = kicker,
                        size = 0.038f,
                        weight = "bold",
                        align = "center",
                        fx = if (i % 2 == 0) "words" else "typewriter",
                        glow = 0.18f,
                        anims = listOf(
                            Anim("alpha", 0f, 1f, (dur * 8 / 100), (dur * 22 / 100).coerceAtLeast(160L), "easeOut"),
                            Anim("alpha", 1f, 0f, (dur * 62 / 100), (dur * 72 / 100).coerceAtLeast(200L), "easeIn")
                        )
                    )
                )
            }

            out.add(
                SceneMotion(
                    sceneId = scene.id,
                    bgMotion = motionName,
                    camera = cameraFor(motionName),
                    elements = elements,
                    freeZone = zone.name,
                    note = "cam " + motionName + " · " + accentType + " + " + graphicType + " · free zone " + zone.name +
                        " (" + aspect + ")"
                )
            )
        }
        return out
    }

    /**
     * Writes the motions into the manifest. An explicit camera already present in the scene is never
     * overwritten (the owner may have tuned it), and no scene is touched when it has no graph.
     */
    fun merge(manifest: SceneManifest, motions: List<SceneMotion>): SceneManifest {
        if (motions.isEmpty()) return manifest
        val byId = motions.associateBy { it.sceneId }
        return manifest.copy(scenes = manifest.scenes.map { scene ->
            val graph = scene.graph ?: return@map scene
            val motion = byId[scene.id] ?: return@map scene
            val camera = if (isDefaultCamera(graph.camera)) motion.camera else graph.camera
            val keepExisting = graph.elements
            val added = motion.elements.filterNot { e -> keepExisting.any { it.id == e.id } }
            scene.copy(graph = graph.copy(camera = camera, elements = keepExisting + added))
        })
    }

    /** True when the camera does nothing (the assembler default), so the planner may replace it. */
    fun isDefaultCamera(c: CameraMove): Boolean =
        c.zoomFrom == 1f && c.zoomTo == 1f && c.panXFrom == 0f && c.panXTo == 0f &&
            c.panYFrom == 0f && c.panYTo == 0f && c.dollyFrom == 0f && c.dollyTo == 0f &&
            c.orbitFrom == 0f && c.orbitTo == 0f && c.tiltFrom == 0f && c.tiltTo == 0f

    // ------------------------------------------------------------------ helpers

    private data class Zone(val name: String, val x: Float, val y: Float, val w: Float, val h: Float, val top: Boolean) {
        val wide: Boolean get() = w >= h
        fun box(): AnimBox = AnimBox(x, y, w, h)
        fun key(): String = name
    }

    private fun freeZone(overlays: List<AnimBox>): Zone {
        val candidates = listOf(
            Zone("top-left", 0.28f, 0.30f, 0.44f, 0.24f, true),
            Zone("top-right", 0.72f, 0.30f, 0.44f, 0.24f, true),
            Zone("mid-left", 0.26f, 0.55f, 0.40f, 0.26f, false),
            Zone("mid-right", 0.74f, 0.55f, 0.40f, 0.26f, false),
            Zone("bottom-band", 0.5f, 0.74f, 0.80f, 0.18f, false)
        )
        var best = candidates[0]
        var bestScore = Float.MAX_VALUE
        candidates.forEach { z ->
            val b = z.box()
            var score = 0f
            overlays.forEach { o -> score += b.coverage(o) * 3f + o.coverage(b) * 0.5f }
            // Captions live in the bottom fifth; keep graphics clear of them even without overlays.
            if (b.bottom > 0.80f) score += 1.2f
            if (score < bestScore) { bestScore = score; best = z }
        }
        return best
    }

    private fun rotate(pool: List<String>, seed: Int, previous: String): String {
        if (pool.isEmpty()) return ""
        val start = ((seed % pool.size) + pool.size) % pool.size
        for (k in pool.indices) {
            val candidate = pool[(start + k) % pool.size]
            if (candidate != previous) return candidate
        }
        return pool[start]
    }

    private fun graphicValues(type: String, i: Int): List<Float> {
        val n = 5
        return when (type) {
            "bars", "wave", "stack", "heat" -> List(n) { k -> 0.35f + (((i * 7 + k * 13) % 60) / 100f) }
            "ring", "arcdial", "donut", "radar" -> listOf(0.62f + ((i % 4) * 0.06f))
            "steps" -> listOf(0.2f, 0.45f, 0.7f, 0.9f)
            else -> List(3) { k -> 0.4f + (((i + k) % 5) * 0.1f) }
        }
    }

    /** A 2-4 word kicker from the narration (never a full sentence; captions already carry those). */
    private fun kickerOf(narration: String): String {
        val words = narration.replace("\n", " ").split(' ').filter { it.isNotBlank() }
        if (words.size < 3) return ""
        val cut = words.take(4).joinToString(" ").trim().trimEnd('.', ',', '!', '?', ':', ';')
        return cut.take(34)
    }
}
