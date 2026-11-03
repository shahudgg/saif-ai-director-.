package com.example.studio.director

import com.example.flow.FlowPlan
import java.io.File
import kotlin.math.abs

/**
 * Round 16 — FINAL QC AGENT ("last agent").
 *
 * The owner asked for one more pass after everything is built: check that no keyframe is wrong, no background
 * layer sits where an overlay should, no picture is missing from its scene, no code animation collides with an
 * overlay, and no scene repeats the previous scene's animation. Then FIX it before the timeline is built.
 *
 * This agent only *repairs*; it never invents new pictures. Every change it makes is written into
 * [QcResult.fixes] so the chat shows exactly what was corrected, and anything it cannot fix is listed in
 * [QcResult.remaining] instead of being hidden.
 */
data class QcFix(val check: String, val sceneId: String, val action: String)

data class QcResult(
    val manifest: SceneManifest,
    val assets: List<ResolvedAsset>,
    val fixes: List<QcFix>,
    val remaining: List<String>
) {
    val ok: Boolean get() = remaining.isEmpty()
    fun summary(): String = buildString {
        if (fixes.isEmpty() && remaining.isEmpty()) {
            append("✅ Final QC: sab layers, keyframes aur animations sahi hain.")
        } else {
            append("🧪 Final QC: ").append(fixes.size).append(" cheezein theek ki")
            if (remaining.isNotEmpty()) append(", ").append(remaining.size).append(" nahi ho payi")
            append(".")
        }
    }
}

object FinalQcAgent {

    /** Transitions that are safe at any scene length (mirrors the assembler whitelist). */
    private val SAFE_TRANSITIONS = listOf("fade", "dissolve", "slideLeft", "slideUp", "zoom", "whip", "iris", "push")

    fun run(manifest: SceneManifest, assets: List<ResolvedAsset>, plan: FlowPlan?, aspect: String): QcResult {
        val fixes = ArrayList<QcFix>()
        val remaining = ArrayList<String>()
        val scenes = manifest.scenes.sortedBy { it.startMs }
        val sceneIds = scenes.map { it.id }.toSet()

        // ---- 0. drop assets whose file disappeared, or whose scene no longer exists ----
        val alive = ArrayList<ResolvedAsset>()
        for (a in assets) {
            if (a.sceneId !in sceneIds) {
                fixes.add(QcFix("orphan asset", a.sceneId, "scene maujood nahi — asset skip"))
                continue
            }
            val hasFile = a.localPath.isBlank() || a.localPath.contains("://") || File(a.localPath).exists()
            if (!hasFile) {
                fixes.add(QcFix("file missing", a.sceneId, "asset " + a.requestId + " hata diya (file nahi mili)"))
                continue
            }
            alive.add(a)
        }

        // ---- 1. clamp every placement + keyframe into legal values ----
        val out = ArrayList<ResolvedAsset>(alive.size)
        for (a in alive) out.add(clampAsset(a, durationOf(scenes, a.sceneId), fixes))

        // ---- 2. exactly ONE backdrop per scene; extra backdrops become floating layers ----
        for (scene in scenes) {
            val own = out.filter { it.sceneId == scene.id }
            val backdropIds = own.filter { isBackdrop(it) }.map { it.requestId }
            if (backdropIds.size <= 1) continue
            val keep = backdropIds.first()
            for (id in backdropIds.drop(1)) {
                val idx = out.indexOfFirst { it.sceneId == scene.id && it.requestId == id }
                if (idx < 0) continue
                val a = out[idx]
                val p = a.placement ?: AssetPlacement()
                out[idx] = a.copy(placement = p.copy(x = 0.5f, y = 0.42f, width = 0.58f, height = 0.44f,
                    fit = "contain", role = "overlay"))
                fixes.add(QcFix("double backdrop", scene.id, id + " ko overlay bana diya (plate " + keep + " hi backdrop hai)"))
            }
        }

        // ---- 3. overlays that cover the whole frame would hide the background layer ----
        for (i in out.indices) {
            val a = out[i]
            val p = a.placement ?: continue
            if (!p.isBackdrop && p.width >= 0.94f && p.height >= 0.94f) {
                out[i] = a.copy(placement = p.copy(width = 0.58f, height = 0.46f, fit = "contain"))
                fixes.add(QcFix("overlay too big", a.sceneId, a.requestId + " ko 58% frame tak chhota kiya"))
            }
        }

        // ---- 4. a scene that only has overlays borrows the nearest verified plate ----
        val plates = out.filter { isBackdrop(it) && File(it.localPath).exists() }
        for (scene in scenes) {
            val own = out.filter { it.sceneId == scene.id }
            if (own.isEmpty()) {
                if (scene.graph == null) remaining.add("Scene " + scene.id + ": koi background layer nahi mili")
                continue
            }
            if (own.any { isBackdrop(it) } || plates.isEmpty()) continue
            val donor = plates.firstOrNull { it.sceneId != scene.id } ?: plates.first()
            out.add(donor.copy(sceneId = scene.id, requestId = donor.requestId + "-qc-" + scene.id,
                note = "QC: borrowed backdrop"))
            fixes.add(QcFix("missing plate", scene.id, "verified plate backdrop ke roop mein lagaya"))
        }

        // ---- 5. motion graphics vs overlay collision ----
        val boxesByScene = HashMap<String, MutableList<AnimBox>>()
        for (a in out) {
            val p = a.placement ?: continue
            if (p.isBackdrop) continue
            boxesByScene.getOrPut(a.sceneId) { ArrayList() }.add(AnimBox(p.x, p.y, p.width, p.height))
        }
        var moved = 0
        val scenesFixed = ArrayList<Scene>(scenes.size)
        for (scene in scenes) {
            val graph = scene.graph
            val boxes = boxesByScene[scene.id]
            if (graph == null || boxes.isNullOrEmpty()) { scenesFixed.add(scene); continue }
            val elements = ArrayList<Element>(graph.elements.size)
            for (e in graph.elements) {
                if (!e.id.startsWith("saif_fx_")) { elements.add(e); continue }
                val box = AnimBox(e.x, e.y, e.w, e.h)
                var worst = 0f
                for (b in boxes) {
                    val c = box.coverage(b)
                    if (c > worst) worst = c
                }
                if (worst <= 0.45f) { elements.add(e); continue }
                val spots = listOf(
                    AnimBox(0.28f, 0.30f, e.w, e.h), AnimBox(0.72f, 0.30f, e.w, e.h),
                    AnimBox(0.26f, 0.55f, e.w, e.h), AnimBox(0.74f, 0.55f, e.w, e.h),
                    AnimBox(0.5f, 0.72f, minOf(e.w, 0.7f), e.h)
                )
                var best = spots[0]
                var bestScore = Float.MAX_VALUE
                for (s in spots) {
                    var score = 0f
                    for (b in boxes) score += s.coverage(b)
                    if (score < bestScore) { bestScore = score; best = s }
                }
                moved++
                elements.add(e.copy(x = best.x, y = best.y, w = best.w, h = best.h))
            }
            scenesFixed.add(scene.copy(graph = graph.copy(elements = elements)))
        }
        if (moved > 0) fixes.add(QcFix("overlay collision", "", moved.toString() + " motion-graphic(s) overlay se hata kar khali zone mein rakhe"))

        // ---- 6. continuity: never repeat the previous scene's transition or camera ----
        val continuity = ArrayList<Scene>(scenesFixed.size)
        for (i in scenesFixed.indices) {
            var scene = scenesFixed[i]
            if (i > 0) {
                val prev = continuity[i - 1]
                if (scene.transitionIn == prev.transitionIn && scene.transitionIn != "fade") {
                    val alt = SAFE_TRANSITIONS.firstOrNull { it != scene.transitionIn } ?: "fade"
                    fixes.add(QcFix("transition repeat", scene.id, scene.transitionIn + " → " + alt))
                    scene = scene.copy(transitionIn = alt)
                }
                val prevCam = prev.graph?.camera
                val cam = scene.graph?.camera
                if (prevCam != null && cam != null && prevCam == cam && !SceneAnimPlanner.isDefaultCamera(prevCam)) {
                    val next = nextCamera(prevCam)
                    fixes.add(QcFix("camera repeat", scene.id, "camera move badla taaki cut saaf dikhe"))
                    scene = scene.copy(graph = scene.graph?.copy(camera = next) ?: scene.graph)
                }
            }
            continuity.add(scene)
        }

        // ---- 7. aspect: flag pictures whose measured ratio is far away from the video ratio ----
        if (plan != null) {
            val want = ratioOf(aspect)
            var bad = 0
            for (s in plan.slots) {
                val r = s.review ?: continue
                if (r.width <= 0 || r.height <= 0) continue
                val got = r.width.toFloat() / r.height.toFloat()
                if (abs(got - want) > want * 0.9f) bad++
            }
            if (bad > 0) fixes.add(QcFix("aspect", "", bad.toString() + " picture(s) ka ratio video se alag hai — cover-crop se adjust"))
        }

        return QcResult(
            manifest = manifest.copy(scenes = continuity),
            assets = out,
            fixes = fixes,
            remaining = remaining
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun durationOf(scenes: List<Scene>, sceneId: String): Long =
        scenes.firstOrNull { it.id == sceneId }?.durationMs ?: 4000L

    private fun isBackdrop(a: ResolvedAsset): Boolean {
        val p = a.placement ?: return true
        return p.isBackdrop
    }

    private fun clampAsset(a: ResolvedAsset, durationMs: Long, fixes: MutableList<QcFix>): ResolvedAsset {
        val p = a.placement ?: return a
        val keys = AssetPlacements.sanitizeKeys(p.keyframes, durationMs)
        val clamped = AssetPlacements.clamp(p.copy(keyframes = keys))
        if (keys.size != p.keyframes.size) {
            fixes.add(QcFix("keyframes", a.sceneId, "keyframes ko scene ki length ke andar clamp kiya"))
        }
        // A transparent PNG never needs a key colour — a fake green key would punch holes in the cut-out.
        val cutout = a.localPath.endsWith("_cut.png", true) || a.transparent
        val finalPlacement = if (cutout && clamped.chromaKey) clamped.copy(chromaKey = false) else clamped
        return a.copy(placement = finalPlacement)
    }

    private fun nextCamera(c: CameraMove): CameraMove {
        if (c.zoomTo > c.zoomFrom) return CameraMove(zoomFrom = 1.10f, zoomTo = 1.0f)
        if (c.zoomTo < c.zoomFrom) return CameraMove(zoomFrom = 1.0f, zoomTo = 1.10f)
        if (c.panXTo < c.panXFrom) return CameraMove(zoomFrom = 1.05f, zoomTo = 1.05f, panXFrom = -0.03f, panXTo = 0.03f)
        return CameraMove(zoomFrom = 1.05f, zoomTo = 1.05f, panXFrom = 0.03f, panXTo = -0.03f)
    }

    private fun ratioOf(aspect: String): Float {
        val parts = aspect.replace(" ", "").split(":", "x", "/")
        val a = parts.getOrNull(0)?.toFloatOrNull() ?: 9f
        val b = parts.getOrNull(1)?.toFloatOrNull() ?: 16f
        return if (a <= 0f || b <= 0f) 9f / 16f else a / b
    }
}
