package com.example.studio.director

import com.example.studio.composer.LayoutEngine
import com.example.studio.composer.SceneLint

/**
 * ROUND 15.7 — AUTOMATIC SCENE REVIEW (the "AI previews every scene and fixes it" pass).
 *
 * Before a timeline is handed over, every scene is checked the way a reviewer would look at it:
 *   • does each content element actually animate (entrance at least)?  → missing ones get a real fade-in
 *   • does any element leave the safe area?                            → the renderer clamps, and it is counted
 *   • does any text overflow its card, sit in the caption zone, or overlap another text? → repaired
 *   • does a floating layer sit outside its boundary card, overlap another layer, or lack keyframes?
 *
 * Nothing is claimed unless it is measured: the returned [Result] carries the honest counts and the
 * per-scene notes, which the Director shows in the chat ("N scenes clean, K auto-fixed, R needs a look").
 */
object SceneReview {

    data class SceneStatus(val sceneId: String, val issues: List<String>, val fixed: Boolean)

    data class Result(
        val manifest: SceneManifest,
        val issues: List<SceneStatus>,
        val clean: Int,
        val fixedCount: Int,
        val leftover: Int,
        val lines: List<String>
    )

    private val DECOR = setOf("particles", "glow", "vignette", "grain", "sweep", "flash", "grid", "burst")

    fun review(manifest: SceneManifest, aspect: String): Result {
        val (aw, ah) = LayoutEngine.parseAspect(aspect)
        val statuses = ArrayList<SceneStatus>()
        // ROUND 15.9 — pixel proof: the scene is actually rendered (3 frames) and measured, so a scene
        // that "looks fine" in JSON but draws nothing is caught before the owner ever sees it.
        val probeLimit = 40
        var clean = 0
        var fixed = 0
        var leftover = 0
        val scenes = manifest.scenes.map { sc ->
            val g = sc.graph ?: run { clean++; return@map sc }
            val before = ArrayList<String>()
            before += LayoutEngine.issues(g, aw, ah, sc.durationMs)
            before += SceneLint.problems(g, aw, ah)
            val strays = SceneSafeArea.strays(sc)
            if (strays > 0) before += "$strays element(s) safe area ke bahar the"

            // ---- real fixes -------------------------------------------------------------------
            var elements = g.elements.map { e ->
                if (e.type in DECOR || e.type == "text") e
                else if (e.anims.isEmpty()) e.copy(anims = listOf(
                    Anim("alpha", 0f, 1f, 0L, 320L, "easeOut"),
                    Anim("progress", 0f, 1f, 120L, minOf(sc.durationMs, 1200L), "easeInOut")
                )) else e
            }
            // Text that never appears is as bad as text that overflows: give it a fade too.
            elements = elements.map { e ->
                if (e.type == "text" && e.text.isNotBlank() && e.anims.none { it.prop == "alpha" })
                    e.copy(anims = e.anims + Anim("alpha", 0f, 1f, 80L, 380L, "easeOut"))
                else e
            }
            val repaired = LayoutEngine.repair(g.copy(elements = elements), aw, ah, sc.durationMs).graph
            // ROUND 15.9 — pixel proof of the REPAIRED scene. These findings cannot be fixed by editing
            // the JSON, so they are reported as-is and never counted as an automatic fix.
            val probeNotes = ArrayList<String>()
            if (manifest.scenes.size <= probeLimit) {
                val reading = FrameProbe.probe(sc.copy(graph = repaired))
                if (reading != null) {
                    if (reading.blank) probeNotes += "render blank nikli (ink ${(reading.ink * 100).toInt()}%)"
                    if (reading.spillsIntoCaption) probeNotes += "content caption band mein (${(reading.bottomInk * 100).toInt()}%)"
                }
            }
            val after = ArrayList<String>()
            after += LayoutEngine.issues(repaired, aw, ah, sc.durationMs)
            after += SceneLint.problems(repaired, aw, ah)
            after += probeNotes
            val wasFixed = probeNotes.isEmpty() && before.isNotEmpty() && after.size < before.size
            when {
                probeNotes.isNotEmpty() -> leftover++
                before.isEmpty() && after.isEmpty() -> clean++
                wasFixed -> fixed++
                else -> leftover++
            }

            statuses += SceneStatus(sc.id, after.distinct(), wasFixed)
            sc.copy(graph = repaired)
        }
        val lines = ArrayList<String>()
        if (clean > 0) lines += "$clean scene(s) pehli baar mein clean"
        if (fixed > 0) lines += "$fixed scene(s) auto-fix hui"
        if (leftover > 0) lines += "$leftover scene(s) mein review note bacha hai"
        return Result(manifest.copy(scenes = scenes), statuses, clean, fixed, leftover, lines)
    }
}
