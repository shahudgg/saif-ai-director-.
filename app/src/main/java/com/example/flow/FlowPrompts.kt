package com.example.flow

import com.example.studio.director.Scene
import com.example.studio.director.SceneManifest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Round 14 — IMAGE PROMPTS FROM THE REAL SCRIPT, NOT FROM THE TOPIC.
 *
 * The old batch writer invented 15 prompts from the topic, so the pictures rarely matched the words.
 * Here every prompt is built from the scene the script actually produced: its narration, its visual
 * objective, the video style and aspect. When a vision/chat model is available it is asked to polish the
 * whole list in one call (strict JSON, scene ids kept); if that call fails, the local builder still
 * produces a usable prompt for every scene, so the pipeline never stops.
 */
class FlowPromptPlanner(private val ask: suspend (String) -> Result<String> = { Result.failure(IllegalStateException("no model")) }) {

    /** Where the plan is written. Deterministic and readable in the UI. */
    suspend fun build(
        projectId: String,
        topic: String,
        aspect: String,
        style: String,
        manifest: SceneManifest,
        script: String = "",
        keyframes: Boolean = true
    ): Result<FlowPlan> {
        if (manifest.scenes.isEmpty()) return Result.failure(IllegalStateException("Scene plan is empty — approve the script first."))
        val notes = ArrayList<String>()
        var crafted: Map<String, Pair<String, String>> = emptyMap()
        if (manifest.scenes.any { it.imagePrompt.isBlank() }) {
            crafted = modelPrompts(manifest, topic, aspect, style, script).getOrElse {
                notes.add("Model prompt polish skip: " + (it.message ?: "unavailable") + " — prompts built locally from each scene.")
                emptyMap()
            }
        }
        val slots = ArrayList<FlowSlot>()
        var index = 0
        manifest.scenes.sortedBy { it.startMs }.forEachIndexed { i, scene ->
            val role = if (i == 0) FlowRoles.COVER else FlowRoles.BACKGROUND
            val craftedPair = crafted[scene.id]
            val base = scene.imagePrompt.ifBlank { craftedPair?.first ?: localPrompt(scene, topic, style, i) }
            slots.add(FlowSlot(
                index = index, sceneId = scene.id, role = role,
                prompt = polish(base, aspect, style, cutout = false),
                negative = craftedPair?.second ?: DEFAULT_NEGATIVE,
                aspect = aspect, sceneMs = scene.durationMs
            ))
            index++
            // A cut-out key visual for every third scene: gives the editor a real object to animate over the backdrop.
            if (keyframes && (i % 3 == 1) && scene.narration.isNotBlank()) {
                slots.add(FlowSlot(
                    index = index, sceneId = scene.id, role = FlowRoles.KEYFRAME,
                    prompt = polish(keyframePrompt(scene, style), aspect, style, cutout = true),
                    negative = DEFAULT_NEGATIVE + ", busy background, photograph of a screen",
                    aspect = aspect, sceneMs = scene.durationMs
                ))
                index++
            }
        }
        if (slots.isEmpty()) return Result.failure(IllegalStateException("No scene needed a picture."))
        notes.add("${slots.size} prompt(s) for ${manifest.scenes.size} scenes · " +
            "${slots.count { it.role == FlowRoles.KEYFRAME }} key visual(s) · aspect $aspect")
        notes.add("Prompts follow the approved narration (scene by scene), never a generic topic list.")
        return Result.success(FlowPlan.fromSlotList(projectId, topic, aspect, style, slots, notes))
    }

    /** One model call for the whole list; scene ids are preserved so pictures land on the right scene. */
    private suspend fun modelPrompts(
        manifest: SceneManifest, topic: String, aspect: String, style: String, script: String
    ): Result<Map<String, Pair<String, String>>> {
        val sceneLines = manifest.scenes.sortedBy { it.startMs }.mapIndexed { i, s ->
            "{\"sceneId\":\"${s.id}\",\"order\":${i + 1},\"narration\":${JSONObject.quote(s.narration.take(220))}," +
                "\"objective\":${JSONObject.quote(s.visualObjective.take(120))}}"
        }
        val prompt = buildString {
            appendLine("You are SAIF, the image director for a short vertical video.")
            appendLine("Topic: " + topic.take(160))
            appendLine("Style: $style · Aspect: $aspect")
            if (script.isNotBlank()) appendLine("Full narration (context only): " + script.take(1200))
            appendLine("Scenes:")
            sceneLines.forEach { appendLine(it) }
            appendLine("Write ONE image-generation prompt per scene. Rules:")
            appendLine("- The picture must show the exact idea of that scene's narration, not the general topic.")
            appendLine("- Describe subject, action, setting, lighting, camera angle/lens, colour mood. Max 55 words.")
            appendLine("- No text, no captions, no logos, no watermarks, no UI, no faces of real people.")
            appendLine("- Every scene must look clearly different from the others.")
            appendLine("Reply STRICT JSON only: {\"images\":[{\"sceneId\":\"s1\",\"prompt\":\"...\",\"negative\":\"...\"}]}")
        }
        val raw = ask(prompt).getOrElse { return Result.failure(it) }
        return runCatching {
            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONObject(clean).optJSONArray("images")
                ?: throw IllegalStateException("model returned no images array")
            val map = LinkedHashMap<String, Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("sceneId")
                val p = o.optString("prompt").trim()
                if (id.isNotBlank() && p.length > 20) map[id] = p to o.optString("negative", DEFAULT_NEGATIVE)
            }
            if (map.isEmpty()) throw IllegalStateException("model returned no usable prompt")
            map
        }
    }

    private fun localPrompt(scene: Scene, topic: String, style: String, index: Int): String {
        val subject = scene.visualObjective.substringAfter(':').trim().ifBlank { scene.narration.take(80) }
        val narration = scene.narration.take(140)
        val lens = when (index % 4) {
            0 -> "35mm wide establishing shot"
            1 -> "50mm medium shot, shallow depth of field"
            2 -> "85mm close-up, dramatic side light"
            else -> "24mm low-angle hero shot"
        }
        return "$subject — $narration. $lens, $style lighting, realistic detail, single clear subject, " +
            "cinematic colour grade, topic: ${topic.take(60)}"
    }

    private fun keyframePrompt(scene: Scene, style: String): String {
        val subject = scene.visualObjective.substringAfter(':').trim().ifBlank { scene.narration.take(70) }
        return "Isolated cut-out object/character of \"$subject\", centred, full object in frame, " +
            "clean flat chroma-green background, soft studio rim light, $style render, high detail"
    }

    private fun polish(prompt: String, aspect: String, style: String, cutout: Boolean): String {
        val trimmed = prompt.trim().trimEnd('.')
        val aspectWord = when (aspect.replace(" ", "")) {
            "9:16" -> "vertical 9:16 composition with headroom"
            "16:9" -> "wide 16:9 cinematic composition"
            "1:1" -> "square 1:1 composition"
            else -> "vertical composition"
        }
        val background = if (cutout) "flat chroma green background, no shadows on the floor"
        else "rich $style background, cinematic depth"
        return "$trimmed. $aspectWord, $background. No text, no letters, no logos, no watermark, no UI."
    }

    companion object {
        const val DEFAULT_NEGATIVE = "text, letters, captions, watermark, logo, signature, ui, frame, collage, split screen, blurry"
    }
}
