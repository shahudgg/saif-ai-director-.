package com.example.flow

import com.example.studio.director.Scene
import com.example.studio.director.SceneManifest
import org.json.JSONObject

/**
 * Round 16 — LAYER PLANNING (background + overlays, per scene, aspect-locked).
 *
 * The Round-14 plan produced ONE picture per scene (plus an occasional key visual), so the video was a
 * slideshow: every scene had a backdrop and nothing else. The owner asked for real layers:
 *
 *   scene → BACKGROUND plate + 1-2 OVERLAY cut-outs (subject/object) → each with its own target box,
 *   draw order (z) and motion, all in the user's chosen aspect ratio.
 *
 * This planner is the single source of truth for that decision. It is deterministic (same manifest in, same
 * plan out), it never invents a scene, it always keeps the aspect ratio the owner picked, and it caps the
 * number of pictures so a 12-scene video cannot turn into a 60-image generation job.
 *
 * [LayerPlan.toSlots] turns the decision into the same [FlowSlot] records the autopilot already executes,
 * which is why nothing about login / 8 s gap / download / verify had to change.
 */

/** One planned picture with its layer role, target box and motion. */
data class LayerSpec(
    val sceneId: String,
    val layer: String,
    val role: String,
    val prompt: String,
    val negative: String,
    val z: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val motion: String,
    val removeBg: Boolean,
    val title: String
)

data class LayerPlan(
    val aspect: String,
    val specs: List<LayerSpec>,
    val notes: List<String>
) {
    val totalImages: Int get() = specs.size
    fun forScene(sceneId: String): List<LayerSpec> = specs.filter { it.sceneId == sceneId }.sortedBy { it.z }
    fun backgrounds(): Int = specs.count { it.layer == FlowLayerKind.BG || it.layer == FlowLayerKind.COVER }
    fun overlays(): Int = specs.count { it.layer == FlowLayerKind.OVERLAY || it.layer == FlowLayerKind.ASSET }

    /** Converts the decision into the executable Flow slot list (indices are sequential, scene order kept). */
    fun toSlots(manifest: SceneManifest): List<FlowSlot> {
        val ms = manifest.scenes.sortedBy { it.startMs }.associateBy { it.id }
        val out = ArrayList<FlowSlot>(specs.size)
        var index = 0
        manifest.scenes.sortedBy { it.startMs }.forEach { scene ->
            forScene(scene.id).forEach { spec ->
                out.add(
                    FlowSlot(
                        index = index,
                        sceneId = spec.sceneId,
                        role = spec.role,
                        prompt = spec.prompt,
                        negative = spec.negative,
                        aspect = aspect,
                        sceneMs = ms[spec.sceneId]?.durationMs ?: scene.durationMs,
                        layer = spec.layer,
                        z = spec.z,
                        targetX = spec.x,
                        targetY = spec.y,
                        targetW = spec.w,
                        targetH = spec.h,
                        motion = spec.motion,
                        removeBg = spec.removeBg
                    )
                )
                index++
            }
        }
        return out
    }
}

object LayerPlanner {

    val BG_MOTIONS = listOf("pushIn", "panLeft", "panRight", "tiltUp", "pullOut", "parallaxDrift")
    val OVERLAY_MOTIONS = listOf("floatUp", "orbitSmall", "parallaxLeft", "parallaxRight", "popDrift", "swaySlow")

    /** Hard cap: pictures per scene (background + overlays). Keeps a long script from becoming 60 images. */
    const val MAX_PER_SCENE = 3

    /**
     * Local, always-available plan. [polishedBg] may carry model-written background prompts (sceneId → prompt);
     * overlay prompts are deterministic because they must describe a clean cut-out.
     */
    fun plan(
        manifest: SceneManifest,
        aspect: String,
        style: String,
        topic: String,
        polishedBg: Map<String, String> = emptyMap(),
        maxLayersPerScene: Int = MAX_PER_SCENE
    ): LayerPlan {
        val scenes = manifest.scenes.sortedBy { it.startMs }
        val specs = ArrayList<LayerSpec>(scenes.size * 2)
        val notes = ArrayList<String>()
        val cap = maxLayersPerScene.coerceIn(1, 4)
        var lastOverlayMotion = ""
        var lastBgMotion = ""

        scenes.forEachIndexed { i, scene ->
            val narration = scene.narration.trim()
            val goal = scene.visualObjective.substringAfter(':').trim().ifBlank { narration.take(90) }
            val subject = subjectOf(scene, goal, narration)

            // ---- 1. BACKGROUND: always exactly one, full frame ----
            val bgMotion = pick(BG_MOTIONS, i * 3 + 1, lastBgMotion)
            lastBgMotion = bgMotion
            val bgPrompt = polishedBg[scene.id]?.takeIf { it.length > 20 }
                ?: backgroundPrompt(scene, goal, topic, aspect, style, i)
            specs.add(
                LayerSpec(
                    sceneId = scene.id,
                    layer = FlowLayerKind.BG,
                    role = FlowRoles.BACKGROUND,
                    prompt = bgPrompt,
                    negative = FlowPromptPlanner.DEFAULT_NEGATIVE + ", people's faces in the foreground, text overlay",
                    z = 0,
                    x = 0.5f,
                    y = 0.5f,
                    w = 1f,
                    h = 1f,
                    motion = bgMotion,
                    removeBg = false,
                    title = "BG " + (i + 1) + " · " + scene.visualObjective.take(22)
                )
            )

            // ---- 2. OVERLAY: a cut-out that floats above the plate ----
            val wantOverlay = cap >= 2 && (narration.length >= 60 || scene.durationMs >= 3200L) && subject.isNotBlank()
            if (wantOverlay) {
                val motion = pick(OVERLAY_MOTIONS, i * 5 + 2, lastOverlayMotion)
                lastOverlayMotion = motion
                val right = i % 2 == 0
                specs.add(
                    LayerSpec(
                        sceneId = scene.id,
                        layer = FlowLayerKind.OVERLAY,
                        role = FlowRoles.OVERLAY,
                        prompt = cutoutPrompt(subject, aspect, style, scene),
                        negative = FlowPromptPlanner.DEFAULT_NEGATIVE + ", busy background, scene, room, gradient, shadow on floor",
                        z = 10,
                        x = if (right) 0.68f else 0.32f,
                        y = 0.44f,
                        w = 0.52f,
                        h = 0.42f,
                        motion = motion,
                        removeBg = true,
                        title = "OV " + (i + 1) + " · " + subject.take(22)
                    )
                )

                // ---- 3. ASSET: only for longer scenes, a small accent so the frame never feels empty ----
                val wantAsset = cap >= 3 && scene.durationMs >= 5200L && i % 2 == 1
                if (wantAsset) {
                    specs.add(
                        LayerSpec(
                            sceneId = scene.id,
                            layer = FlowLayerKind.ASSET,
                            role = FlowRoles.OVERLAY,
                            prompt = accentPrompt(subject, aspect, style),
                            negative = FlowPromptPlanner.DEFAULT_NEGATIVE + ", scene, room, person, text",
                            z = 20,
                            x = if (right) 0.28f else 0.72f,
                            y = 0.62f,
                            w = 0.34f,
                            h = 0.26f,
                            motion = if (motion == "parallaxLeft") "parallaxRight" else "floatUp",
                            removeBg = true,
                            title = "AC " + (i + 1) + " · " + subject.take(18)
                        )
                    )
                }
            }
        }

        val bgs = specs.count { it.layer == FlowLayerKind.BG }
        val ovs = specs.count { it.layer == FlowLayerKind.OVERLAY || it.layer == FlowLayerKind.ASSET }
        notes.add("Layer plan: " + bgs + " background + " + ovs + " overlay = " + specs.size +
            " images for " + scenes.size + " scenes · aspect " + aspect)
        notes.add("Har scene: 1 background plate (full frame) + overlay cut-outs (alag box, apna motion). " +
            "Aspect ratio " + aspect + " lock hai — Flow usi ratio mein banayega.")
        notes.add("Overlay ka background locally remove hoga (flood-fill cut-out), phir PNG transparent layer ki tarah lagegi.")
        return LayerPlan(aspect = aspect, specs = specs, notes = notes)
    }

    /**
     * Plan + optional model polish of the background prompts in ONE call (strict JSON, scene ids kept).
     * Overlay prompts stay local so a cut-out is always described the same, reliable way.
     */
    suspend fun build(
        projectId: String,
        topic: String,
        aspect: String,
        style: String,
        manifest: SceneManifest,
        script: String = "",
        ask: suspend (String) -> Result<String> = { Result.failure(IllegalStateException("no model")) }
    ): Result<FlowPlan> {
        if (manifest.scenes.isEmpty()) {
            return Result.failure(IllegalStateException("Scene plan is empty — script approve karo pehle."))
        }
        val notes = ArrayList<String>()
        val polished = runCatching { modelBackgrounds(manifest, topic, aspect, style, script, ask) }
            .getOrElse { emptyMap() }
        if (polished.isEmpty()) notes.add("Model polish skip — background prompts scene ki narration se locally bane.")
        val layerPlan = plan(manifest, aspect, style, topic, polished)
        notes.addAll(layerPlan.notes)
        return Result.success(
            FlowPlan.fromSlotList(
                projectId = projectId,
                topic = topic,
                aspect = aspect,
                style = style,
                slots = layerPlan.toSlots(manifest),
                notes = notes
            )
        )
    }

    // ------------------------------------------------------------------ prompt building

    private suspend fun modelBackgrounds(
        manifest: SceneManifest,
        topic: String,
        aspect: String,
        style: String,
        script: String,
        ask: suspend (String) -> Result<String>
    ): Map<String, String> {
        val sceneLines = manifest.scenes.sortedBy { it.startMs }.mapIndexed { i, s ->
            "{\"sceneId\":\"" + s.id + "\",\"order\":" + (i + 1) +
                ",\"narration\":" + JSONObject.quote(s.narration.take(220)) +
                ",\"objective\":" + JSONObject.quote(s.visualObjective.take(120)) + "}"
        }
        val prompt = buildString {
            appendLine("You are SAIF, the background artist for a short vertical video.")
            appendLine("Topic: " + topic.take(160))
            appendLine("Style: " + style + " · Aspect: " + aspect)
            if (script.isNotBlank()) appendLine("Full narration (context only): " + script.take(1000))
            appendLine("Scenes:")
            sceneLines.forEach { appendLine(it) }
            appendLine("Write ONE BACKGROUND PLATE prompt per scene (the environment only — the hero subject is a separate cut-out).")
            appendLine("Rules: subject-free environment, no faces, no text, camera angle + lens + lighting + colour mood, max 45 words, every scene clearly different.")
            appendLine("Reply STRICT JSON only: {\"backgrounds\":[{\"sceneId\":\"s1\",\"prompt\":\"...\"}]}")
        }
        val raw = ask(prompt).getOrElse { return emptyMap() }
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = JSONObject(clean).optJSONArray("backgrounds") ?: return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("sceneId")
            val p = o.optString("prompt").trim()
            if (id.isNotBlank() && p.length > 20) map[id] = p
        }
        return map
    }

    private fun backgroundPrompt(scene: Scene, goal: String, topic: String, aspect: String, style: String, index: Int): String {
        val lens = when (index % 4) {
            0 -> "24mm wide establishing shot"
            1 -> "35mm environment shot, deep focus"
            2 -> "50mm low angle, dramatic side light"
            else -> "85mm compressed background, atmospheric haze"
        }
        val where = scene.visualObjective.substringBefore(':').trim().ifBlank { "environment" }
        return ("Background plate: " + where + " — " + goal.take(120) + ". Empty stage for the subject, " +
            lens + ", " + style + " lighting and colour grade, cinematic depth, realistic detail, " +
            "topic mood: " + topic.take(50) + ". " + aspectWord(aspect) + ", no text, no logos, no watermark.")
    }

    private fun cutoutPrompt(subject: String, aspect: String, style: String, scene: Scene): String =
        ("Isolated cut-out of " + subject.take(90) + " — " + scene.visualObjective.take(60) +
         ". Single object/subject filling most of the frame, clean flat chroma-green studio background, " +
         "even lighting, soft rim light, crisp edges, no floor shadow, no props, " + style + " render, high detail. " +
         aspectWord(aspect) + ", no text, no logos, no watermark.")

    private fun accentPrompt(subject: String, aspect: String, style: String): String =
        ("Small isolated accent object that represents \"" + subject.take(60) + "\", single object centred, " +
         "clean flat chroma-green background, studio product lighting, crisp edges, " + style + " icon-like render. " +
         aspectWord(aspect) + ", no text, no logos, no watermark.")

    private fun subjectOf(scene: Scene, goal: String, narration: String): String {
        val raw = goal.ifBlank { narration }.replace("\n", " ")
        val cut = raw.split('.', ',', ';', ':').firstOrNull { it.trim().length > 6 }?.trim().orEmpty()
        return cut.take(70).ifBlank { scene.visualObjective.take(60) }
    }

    private fun aspectWord(aspect: String): String = when (aspect.replace(" ", "")) {
        "9:16" -> "vertical 9:16 composition"
        "16:9" -> "wide 16:9 cinematic composition"
        "1:1" -> "square 1:1 composition"
        "4:5" -> "vertical 4:5 composition"
        "3:4" -> "vertical 3:4 composition"
        else -> "composition matching " + aspect
    }

    /** Picks a motion that is never the same as the previous scene's (that is what made cuts feel random). */
    private fun pick(pool: List<String>, seed: Int, previous: String): String {
        if (pool.isEmpty()) return ""
        val start = ((seed % pool.size) + pool.size) % pool.size
        for (k in pool.indices) {
            val candidate = pool[(start + k) % pool.size]
            if (candidate != previous) return candidate
        }
        return pool[start]
    }
}
