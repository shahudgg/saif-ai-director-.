package com.example.studio.director

import org.json.JSONArray
import org.json.JSONObject

const val SCENE_SCHEMA_VERSION = 1
const val SCENE_GRAPH_MARKER = "SAIF_SCENE_V1"
/** Scene drawn ABOVE b-roll/pictures (transparent background + scrim), so library assets and graphics show together. */
const val SCENE_OVERLAY_MARKER = "SAIF_SCENE_V1_OVERLAY"

/** Primitive types the native renderer understands. Anything else is rejected at validation time. */
val SUPPORTED_ELEMENT_TYPES = setOf(
    "text", "rect", "circle", "line", "arrow", "bars", "linechart", "nodes", "ring", "grid", "particles",
    // visual-effect primitives (all procedural, deterministic functions of time)
    "glow", "vignette", "grain", "sweep", "flash", "glitch", "burst", "donut",
    // topic glyph: Element.text names it (chart, coin, shield, lock, bulb, clock, heart, rocket, check, warning, globe, target, brain, phone, star)
    "icon"
)
/** Text effects for type "text" through Element.fx. */
val SUPPORTED_TEXT_FX = setOf("", "rise", "pop", "fade", "typewriter", "words", "counter", "glow", "outline")
val SUPPORTED_ANIM_PROPS = setOf("x", "y", "w", "h", "alpha", "scale", "rotation", "progress", "value")
val SUPPORTED_EASINGS = setOf("linear", "easeIn", "easeOut", "easeInOut", "overshoot", "expoOut", "backOut", "bounce", "spring", "cubicInOut")
val SUPPORTED_TRANSITIONS = setOf(
    "none", "fade", "slideLeft", "slideUp", "zoom", "wipe", "dissolve",
    "slideRight", "push", "whip", "glitch", "flash", "iris", "spin", "zoomBlur"
)

data class Anim(
    val prop: String, val from: Float, val to: Float,
    val startMs: Long, val endMs: Long, val easing: String = "easeOut"
)

/** Coordinates are normalized 0..1 of the frame so one scene works for every aspect ratio. */
data class Element(
    val id: String, val type: String,
    val x: Float = 0.5f, val y: Float = 0.5f, val w: Float = 0.2f, val h: Float = 0.1f,
    val color: String = "#FFFFFF", val color2: String = "",
    val text: String = "", val size: Float = 0.05f, val strokeW: Float = 0.004f,
    val values: List<Float> = emptyList(), val labels: List<String> = emptyList(),
    val x2: Float = 0f, val y2: Float = 0f, val count: Int = 0, val seed: Int = 1,
    val anims: List<Anim> = emptyList(),
    /** Text effect (see SUPPORTED_TEXT_FX): typewriter, words, counter, glow, outline. */
    val fx: String = ""
)

data class CameraMove(
    val zoomFrom: Float = 1f, val zoomTo: Float = 1f,
    val panXFrom: Float = 0f, val panXTo: Float = 0f, val panYFrom: Float = 0f, val panYTo: Float = 0f
)

data class SceneGraph(
    val bgTop: String = "#0B0A14", val bgBottom: String = "#1B1330",
    val camera: CameraMove = CameraMove(),
    val elements: List<Element> = emptyList()
)

data class AssetRequest(
    val id: String, val kind: String,          // image | video | audio | overlay
    val description: String, val fallback: String = "procedural"
)

data class AudioCue(val kind: String, val description: String, val atMs: Long, val durationMs: Long = 800L)

data class Scene(
    val id: String, val startMs: Long, val endMs: Long,
    val narration: String = "", val visualObjective: String = "",
    val graph: SceneGraph? = null,
    val assets: List<AssetRequest> = emptyList(),
    val transitionIn: String = "fade", val transitionMs: Long = 400L,
    val cues: List<AudioCue> = emptyList(),
    val caption: String = "",
    val sourceInMs: Long = 0L,                 // for footage-based scenes
    val locked: Boolean = false
) {
    val durationMs: Long get() = endMs - startMs
}

data class SceneManifest(
    val version: Int = SCENE_SCHEMA_VERSION, val fps: Int = 30, val title: String = "",
    val scenes: List<Scene> = emptyList(), val sources: List<String> = emptyList(),
    val researchNote: String = ""
) {
    val totalMs: Long get() = scenes.maxOfOrNull { it.endMs } ?: 0L
}

data class ValidationIssue(val sceneId: String?, val message: String, val fatal: Boolean = true)

object SceneValidator {
    private val hex = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun validate(m: SceneManifest, targetMs: Long? = null, toleranceFraction: Double = 0.12): List<ValidationIssue> {
        val out = mutableListOf<ValidationIssue>()
        if (m.scenes.isEmpty()) out += ValidationIssue(null, "Manifest has no scenes")
        if (m.fps !in 12..120) out += ValidationIssue(null, "Unsupported frame rate ${m.fps}")
        val ids = HashSet<String>()
        var prevEnd = 0L
        m.scenes.sortedBy { it.startMs }.forEach { s ->
            if (!ids.add(s.id)) out += ValidationIssue(s.id, "Duplicate scene id")
            if (s.endMs <= s.startMs) out += ValidationIssue(s.id, "Scene has non-positive duration")
            if (s.startMs < prevEnd) out += ValidationIssue(s.id, "Scene overlaps previous scene")
            if (s.startMs > prevEnd + 50) out += ValidationIssue(s.id, "Gap of ${s.startMs - prevEnd} ms before scene", fatal = false)
            prevEnd = maxOf(prevEnd, s.endMs)
            if (s.transitionIn !in SUPPORTED_TRANSITIONS) out += ValidationIssue(s.id, "Unknown transition '${s.transitionIn}'", fatal = false)
            s.graph?.let { g -> validateGraph(s, g, out) }
            s.assets.forEach { if (it.kind !in setOf("image", "video", "audio", "overlay")) out += ValidationIssue(s.id, "Unknown asset kind '${it.kind}'") }
        }
        if (targetMs != null && targetMs > 0 && m.scenes.isNotEmpty()) {
            val diff = kotlin.math.abs(m.totalMs - targetMs).toDouble() / targetMs
            if (diff > toleranceFraction) out += ValidationIssue(null,
                "Total ${m.totalMs / 1000}s differs from target ${targetMs / 1000}s by ${(diff * 100).toInt()}%", fatal = false)
        }
        return out
    }

    private fun validateGraph(s: Scene, g: SceneGraph, out: MutableList<ValidationIssue>) {
        if (!hex.matches(g.bgTop) || !hex.matches(g.bgBottom)) out += ValidationIssue(s.id, "Invalid background color")
        if (g.elements.size > 80) out += ValidationIssue(s.id, "Too many elements (${g.elements.size} > 80)")
        val eIds = HashSet<String>()
        g.elements.forEach { e ->
            if (e.type !in SUPPORTED_ELEMENT_TYPES) out += ValidationIssue(s.id, "Unsupported element type '${e.type}'")
            if (!eIds.add(e.id)) out += ValidationIssue(s.id, "Duplicate element id '${e.id}'", fatal = false)
            if (e.color.isNotEmpty() && !hex.matches(e.color)) out += ValidationIssue(s.id, "Invalid color '${e.color}' on ${e.id}")
            if (e.text.length > 280) out += ValidationIssue(s.id, "Text too long on ${e.id}", fatal = false)
            if (e.values.size > 40) out += ValidationIssue(s.id, "Too many data points on ${e.id}")
            e.anims.forEach { a ->
                if (a.prop !in SUPPORTED_ANIM_PROPS) out += ValidationIssue(s.id, "Unsupported anim prop '${a.prop}'")
                if (a.endMs < a.startMs) out += ValidationIssue(s.id, "Animation ends before it starts on ${e.id}")
                if (a.endMs > s.durationMs + 50) out += ValidationIssue(s.id, "Animation on ${e.id} outlasts the scene", fatal = false)
                if (a.easing !in SUPPORTED_EASINGS) out += ValidationIssue(s.id, "Unknown easing '${a.easing}'", fatal = false)
            }
        }
    }

    fun fatal(issues: List<ValidationIssue>) = issues.filter { it.fatal }
}

/**
 * Scenes that belong to ONE spoken sentence (a long sentence is shown over several beats) are voiced together.
 * Voicing half a sentence on its own gave it a falling "full stop" tone and an audible cut between the halves.
 */
object VoiceGroups {
    /** True when this narration ends a sentence (so the voice may stop there). */
    fun endsSentence(t: String): Boolean = t.trimEnd().trimEnd('"', '\'', '\u201D', '\u2019', ')').lastOrNull()?.let { it in ".!?\u0964\u2026" } ?: true

    fun build(scenes: List<Scene>, maxScenes: Int = 4): List<List<Scene>> {
        val groups = ArrayList<List<Scene>>()
        var cur = ArrayList<Scene>()
        scenes.forEach { s ->
            cur.add(s)
            if (endsSentence(s.narration) || cur.size >= maxScenes) { groups.add(cur); cur = ArrayList() }
        }
        if (cur.isNotEmpty()) groups.add(cur)
        return groups
    }
}

/** Timing helpers: durations come from real narration length, never equal splits. */
object SceneTiming {
    /**
     * Re-times scenes back-to-back. [narrationMs] holds measured audio lengths per scene id;
     * a scene is never shorter than its narration + [padMs] or [minMs].
     */
    fun retime(m: SceneManifest, narrationMs: Map<String, Long>, padMs: Long = 350L, minMs: Long = 1800L): SceneManifest {
        var cursor = 0L
        val scenes = m.scenes.sortedBy { it.startMs }.map { s ->
            val need = narrationMs[s.id]?.plus(padMs) ?: s.durationMs
            val dur = maxOf(need, minMs, if (s.locked) s.durationMs else 0L)
            val out = s.copy(startMs = cursor, endMs = cursor + dur)
            cursor += dur
            out
        }
        return m.copy(scenes = scenes)
    }

    /**
     * Like [retime], but understands narration clips that span several consecutive scenes (one spoken sentence shown over
     * more than one beat). The clip's real length (+[padMs]) is shared between its scenes by word count, so the voice is
     * never cut in the middle of a sentence and every later scene still lines up with the audio.
     */
    fun retimeGrouped(m: SceneManifest, clips: List<NarrationClip>, padMs: Long = 170L, minMs: Long = 1800L, minMemberMs: Long = 900L): SceneManifest {
        val sorted = m.scenes.sortedBy { it.startMs }
        val byFirst = clips.filter { it.groupIds.size > 1 }.associateBy { it.groupIds.first() }
        val single = clips.filter { it.groupIds.size <= 1 }.associate { it.sceneId to it.durationMs }
        val done = HashSet<String>()
        val out = ArrayList<Scene>()
        var cursor = 0L
        for (s in sorted) {
            if (s.id in done) continue
            val clip = byFirst[s.id]
            if (clip != null) {
                val members = clip.groupIds.mapNotNull { id -> sorted.firstOrNull { it.id == id } }
                if (members.size > 1) {
                    val total = clip.durationMs + padMs
                    val ws = members.map { SpeechTiming.countWords(it.narration).coerceAtLeast(1) }
                    val sumW = ws.sum()
                    var used = 0L
                    members.forEachIndexed { k, ms ->
                        val share = if (k == members.lastIndex) maxOf(total - used, minMemberMs) else maxOf(minMemberMs, total * ws[k] / sumW)
                        out += ms.copy(startMs = cursor, endMs = cursor + share)
                        cursor += share; used += share; done += ms.id
                    }
                    continue
                }
            }
            val need = single[s.id]?.plus(padMs) ?: s.durationMs
            val dur = maxOf(need, minMs, if (s.locked) s.durationMs else 0L)
            out += s.copy(startMs = cursor, endMs = cursor + dur)
            cursor += dur; done += s.id
        }
        return m.copy(scenes = out)
    }

    /** Scene durations derived from transcript segments (voice-over workflow): boundaries are preserved. */
    fun fromSegments(segments: List<Pair<Long, Long>>, totalMs: Long): List<Pair<Long, Long>> {
        if (segments.isEmpty()) return emptyList()
        val sorted = segments.sortedBy { it.first }
        return sorted.mapIndexed { i, seg ->
            val start = if (i == 0) 0L else seg.first
            val end = if (i == sorted.lastIndex) maxOf(totalMs, seg.second) else sorted[i + 1].first
            start to end
        }
    }

    fun msToFrame(ms: Long, fps: Int): Long = Math.round(ms * fps / 1000.0)
    fun frameToMs(frame: Long, fps: Int): Long = Math.round(frame * 1000.0 / fps)
}

object SceneJson {
    fun parseManifest(raw: String): Result<SceneManifest> = runCatching {
        val obj = JSONObject(extractJson(raw))
        val arr = obj.optJSONArray("scenes") ?: error("JSON has no 'scenes' array")
        val scenes = (0 until arr.length()).map { parseScene(arr.getJSONObject(it), it) }
        SceneManifest(
            version = obj.optInt("version", SCENE_SCHEMA_VERSION), fps = obj.optInt("fps", 30),
            title = obj.optString("title", ""), scenes = scenes,
            sources = obj.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            researchNote = obj.optString("researchNote", "")
        )
    }

    fun extractJson(raw: String): String {
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1) ?: raw
        val s = fence.indexOf('{'); val e = fence.lastIndexOf('}')
        require(s >= 0 && e > s) { "Response did not contain a JSON object" }
        return fence.substring(s, e + 1)
    }

    private fun parseScene(o: JSONObject, idx: Int): Scene {
        val id = o.optString("id", "s${idx + 1}")
        val start = o.optLong("startMs", 0L)
        val end = if (o.has("endMs")) o.optLong("endMs") else start + o.optLong("durationMs", 4000L)
        return Scene(
            id = id, startMs = start, endMs = end,
            narration = o.optString("narration", ""), visualObjective = o.optString("visualObjective", ""),
            graph = o.optJSONObject("graph")?.let { parseGraph(it) },
            assets = o.optJSONArray("assets")?.let { a -> (0 until a.length()).map { i ->
                val x = a.getJSONObject(i)
                AssetRequest(x.optString("id", "$id-a$i"), x.optString("kind", "image"), x.optString("description", ""), x.optString("fallback", "procedural"))
            } } ?: emptyList(),
            transitionIn = o.optString("transition", "fade"), transitionMs = o.optLong("transitionMs", 400L),
            cues = o.optJSONArray("cues")?.let { a -> (0 until a.length()).map { i ->
                val x = a.getJSONObject(i)
                AudioCue(x.optString("kind", "sfx"), x.optString("description", ""), x.optLong("atMs", 0L), x.optLong("durationMs", 800L))
            } } ?: emptyList(),
            caption = o.optString("caption", ""), sourceInMs = o.optLong("sourceInMs", 0L), locked = o.optBoolean("locked", false)
        )
    }

    fun parseGraph(o: JSONObject): SceneGraph {
        val cam = o.optJSONObject("camera")
        val els = o.optJSONArray("elements") ?: JSONArray()
        return SceneGraph(
            bgTop = o.optString("bgTop", "#0B0A14"), bgBottom = o.optString("bgBottom", "#1B1330"),
            camera = if (cam == null) CameraMove() else CameraMove(
                cam.optDouble("zoomFrom", 1.0).toFloat(), cam.optDouble("zoomTo", 1.0).toFloat(),
                cam.optDouble("panXFrom", 0.0).toFloat(), cam.optDouble("panXTo", 0.0).toFloat(),
                cam.optDouble("panYFrom", 0.0).toFloat(), cam.optDouble("panYTo", 0.0).toFloat()),
            elements = (0 until els.length()).map { parseElement(els.getJSONObject(it), it) }
        )
    }

    private fun floats(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.optDouble(i, 0.0).toFloat() } } ?: emptyList()

    private fun parseElement(o: JSONObject, i: Int) = Element(
        id = o.optString("id", "e$i"), type = o.optString("type", "rect"),
        x = o.optDouble("x", 0.5).toFloat(), y = o.optDouble("y", 0.5).toFloat(),
        w = o.optDouble("w", 0.2).toFloat(), h = o.optDouble("h", 0.1).toFloat(),
        color = o.optString("color", "#FFFFFF"), color2 = o.optString("color2", ""),
        text = o.optString("text", ""), size = o.optDouble("size", 0.05).toFloat(),
        strokeW = o.optDouble("strokeW", 0.004).toFloat(),
        values = floats(o.optJSONArray("values")),
        labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        x2 = o.optDouble("x2", 0.0).toFloat(), y2 = o.optDouble("y2", 0.0).toFloat(),
        count = o.optInt("count", 0), seed = o.optInt("seed", 1), fx = o.optString("fx", ""),
        anims = o.optJSONArray("anims")?.let { a -> (0 until a.length()).map { k ->
            val x = a.getJSONObject(k)
            Anim(x.optString("prop", "alpha"), x.optDouble("from", 0.0).toFloat(), x.optDouble("to", 1.0).toFloat(),
                x.optLong("startMs", 0L), x.optLong("endMs", 500L), x.optString("easing", "easeOut"))
        } } ?: emptyList()
    )

    // ---- serialization (round-trips through CodeSceneItem.htmlCode so old projects keep loading) ----

    fun graphToJson(g: SceneGraph): JSONObject = JSONObject().apply {
        put("bgTop", g.bgTop); put("bgBottom", g.bgBottom)
        put("camera", JSONObject().apply {
            put("zoomFrom", g.camera.zoomFrom.toDouble()); put("zoomTo", g.camera.zoomTo.toDouble())
            put("panXFrom", g.camera.panXFrom.toDouble()); put("panXTo", g.camera.panXTo.toDouble())
            put("panYFrom", g.camera.panYFrom.toDouble()); put("panYTo", g.camera.panYTo.toDouble())
        })
        put("elements", JSONArray().apply { g.elements.forEach { put(elementToJson(it)) } })
    }

    private fun elementToJson(e: Element) = JSONObject().apply {
        put("id", e.id); put("type", e.type); put("x", e.x.toDouble()); put("y", e.y.toDouble())
        put("w", e.w.toDouble()); put("h", e.h.toDouble()); put("color", e.color); put("color2", e.color2)
        put("text", e.text); put("size", e.size.toDouble()); put("strokeW", e.strokeW.toDouble())
        put("values", JSONArray().apply { e.values.forEach { put(it.toDouble()) } })
        put("labels", JSONArray().apply { e.labels.forEach { put(it) } })
        put("x2", e.x2.toDouble()); put("y2", e.y2.toDouble()); put("count", e.count); put("seed", e.seed); put("fx", e.fx)
        put("anims", JSONArray().apply { e.anims.forEach { a -> put(JSONObject().apply {
            put("prop", a.prop); put("from", a.from.toDouble()); put("to", a.to.toDouble())
            put("startMs", a.startMs); put("endMs", a.endMs); put("easing", a.easing) }) } })
    }

    /** Stored payload: marker line + JSON so it can live in CodeSceneItem.htmlCode. */
    fun encodeForTimeline(g: SceneGraph): String = "<!-- $SCENE_GRAPH_MARKER -->\n" + graphToJson(g).toString()
    fun decodeFromTimeline(code: String): SceneGraph? =
        if (!code.contains(SCENE_GRAPH_MARKER)) null else runCatching { parseGraph(JSONObject(extractJson(code.substringAfter("-->")))) }.getOrNull()

    fun manifestToJson(m: SceneManifest): JSONObject = JSONObject().apply {
        put("version", m.version); put("fps", m.fps); put("title", m.title); put("researchNote", m.researchNote)
        put("sources", JSONArray().apply { m.sources.forEach { put(it) } })
        put("scenes", JSONArray().apply { m.scenes.forEach { s -> put(JSONObject().apply {
            put("id", s.id); put("startMs", s.startMs); put("endMs", s.endMs)
            put("narration", s.narration); put("visualObjective", s.visualObjective)
            put("transition", s.transitionIn); put("transitionMs", s.transitionMs); put("caption", s.caption)
            put("sourceInMs", s.sourceInMs); put("locked", s.locked)
            s.graph?.let { put("graph", graphToJson(it)) }
            put("assets", JSONArray().apply { s.assets.forEach { a -> put(JSONObject().apply {
                put("id", a.id); put("kind", a.kind); put("description", a.description); put("fallback", a.fallback) }) } })
            put("cues", JSONArray().apply { s.cues.forEach { c -> put(JSONObject().apply {
                put("kind", c.kind); put("description", c.description); put("atMs", c.atMs); put("durationMs", c.durationMs) }) } })
        }) } })
    }
}
