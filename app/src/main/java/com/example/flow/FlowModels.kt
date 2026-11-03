package com.example.flow

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Round 14 — SAIF FLOW PIPELINE MODELS.
 *
 * The old build only dumped "whatever <img> was on the page" into the library, which is why the owner's
 * Google profile photo kept showing up as a video asset. This round the Flow work is a real, resumable
 * pipeline with ONE record per planned picture:
 *
 *   script → per-scene prompt plan (BACKGROUND / KEYFRAME / STICKER) → generate on flow.google.com one by one
 *   (8 s gap) → download with the logged-in cookies → verify (crop / glow / background removal / sharpness)
 *   → plan transitions from the real pixels → build the timeline + animation code.
 *
 * Everything here is plain data + JSON so it survives an app restart and is unit-testable.
 */

/** What a planned picture is for. */
object FlowRoles {
    const val BACKGROUND = "background"
    const val KEYFRAME = "keyframe"
    const val STICKER = "sticker"
    const val COVER = "cover"
    /** Round 16 — a second picture for the SAME scene, drawn above the background as a floating layer. */
    const val OVERLAY = "overlay"
    val ALL = setOf(BACKGROUND, KEYFRAME, STICKER, COVER, OVERLAY)

    /** Everything that is NOT a full-frame background is treated as a floating layer. */
    fun isFloating(role: String): Boolean = role == OVERLAY || role == KEYFRAME || role == STICKER
}

/** Round 16 — which layer of the video a planned picture becomes. */
object FlowLayerKind {
    const val BG = "bg"
    const val OVERLAY = "overlay"
    const val ASSET = "asset"
    const val COVER = "cover"
    val ALL = setOf(BG, OVERLAY, ASSET, COVER)
}

enum class FlowSlotStatus { PENDING, RUNNING, GENERATED, DOWNLOADED, VERIFIED, FAILED, SKIPPED }

/** One picture the video needs, tied to the scene it belongs to. */
data class FlowSlot(
    val index: Int,
    val sceneId: String,
    val role: String = FlowRoles.BACKGROUND,
    val prompt: String = "",
    /** What must NOT appear (text, watermark, extra people...). Kept separate so the UI can show it. */
    val negative: String = "",
    val aspect: String = "9:16",
    /** Real length of the scene this picture belongs to; used for Ken-Burns + transition timing. */
    val sceneMs: Long = 4000L,
    val status: FlowSlotStatus = FlowSlotStatus.PENDING,
    val tries: Int = 0,
    /** Absolute path of the downloaded image (empty until downloaded). */
    val imagePath: String = "",
    val imageUrl: String = "",
    val imageHash: String = "",
    val review: FlowReview? = null,
    val error: String = "",
    // ---- Round 16: multi-layer planning. A scene now gets a background AND overlays, and the planner
    // says exactly where each one goes, so the timeline is not guessing any more. ----
    /** [FlowLayerKind] of this picture (bg / overlay / asset / cover). Blank = old single-layer plan. */
    val layer: String = "",
    /** Draw order inside the scene (0 = background, higher = above). */
    val z: Int = 0,
    /** Target centre + box, normalized 0..1 of the frame. Used verbatim by the timeline placement. */
    val targetX: Float = 0.5f,
    val targetY: Float = 0.5f,
    val targetW: Float = 1f,
    val targetH: Float = 1f,
    /** Named motion the animation planner asked for (pushIn, panLeft, floatUp, orbitSmall, parallaxRight...). */
    val motion: String = "",
    /** Transparent PNG produced locally by [FlowCutout] — used instead of [imagePath] when present. */
    val cutPath: String = "",
    val removeBg: Boolean = false,
    /** Dominant border colour measured while cutting out (kept as the chroma fallback). */
    val borderHex: String = ""
) {
    val done: Boolean get() = status == FlowSlotStatus.VERIFIED || status == FlowSlotStatus.DOWNLOADED
}

/** Everything the video needs from Flow, derived from the approved script + scene plan. */
data class FlowPlan(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val topic: String = "",
    val aspect: String = "9:16",
    val style: String = "cinematic explainer",
    val createdAt: Long = System.currentTimeMillis(),
    val slots: List<FlowSlot> = emptyList(),
    /** One line per decision made while planning (shown in the chat, never invented). */
    val notes: List<String> = emptyList(),
    /** Transitions planned from the downloaded pixels, filled after verification. */
    val transitions: List<FlowTransition> = emptyList()
) {
    val pending: List<FlowSlot> get() = slots.filter { it.status == FlowSlotStatus.PENDING || it.status == FlowSlotStatus.FAILED }
    val finishedCount: Int get() = slots.count { it.done }
    fun slot(index: Int): FlowSlot? = slots.firstOrNull { it.index == index }

    fun withSlot(s: FlowSlot): FlowPlan = copy(slots = slots.map { if (it.index == s.index) s else it })
    fun withTransitions(t: List<FlowTransition>): FlowPlan = copy(transitions = t)

    companion object {
        fun fromSlotList(projectId: String, topic: String, aspect: String, style: String,
                         slots: List<FlowSlot>, notes: List<String> = emptyList()): FlowPlan =
            FlowPlan(projectId = projectId, topic = topic, aspect = aspect, style = style, slots = slots, notes = notes)
    }
}

/**
 * Real measurements + decisions for ONE downloaded picture. All of this is computed locally from the
 * decoded bitmap (no model guessing), and the optional AI verdict is stored next to it instead of
 * replacing it.
 */
data class FlowReview(
    val width: Int = 0,
    val height: Int = 0,
    /** Pixels lost when the picture is cover-cropped to the video aspect (0..1). */
    val cropLoss: Float = 0f,
    /** Where the interesting part of the picture is (0..1, centre based). Used to nudge the crop. */
    val focalX: Float = 0.5f,
    val focalY: Float = 0.5f,
    /** 0..1 average luminance. */
    val brightness: Float = 0.5f,
    /** 0..1 rough contrast (std-dev of luminance). */
    val contrast: Float = 0.2f,
    /** 0..1 detail/edge energy; low = soft picture, will look mushy at 1080p. */
    val sharpness: Float = 0.3f,
    /** True when the four corners share one colour (background removal is meaningful). */
    val cornerUniform: Boolean = false,
    val paletteHex: String = "#101018",
    /** Decisions the assembler + timeline actually use. */
    val fit: String = "cover",
    val cropX: Float = 0f,
    val cropY: Float = 0f,
    val needsGlow: Boolean = false,
    val glowStrength: Float = 0f,
    val needsBgRemove: Boolean = false,
    val animIn: String = "fadeIn",
    val animOut: String = "fadeOut",
    val drift: Float = 0.2f,
    /** Honest one-line verdict for the chat report. */
    val verdict: String = "",
    /** Optional AI vision verdict (FIT / NOT FIT — reason). Blank when no vision provider is configured. */
    val aiVerdict: String = "",
    val ok: Boolean = true
)

/** A transition planned between two consecutive scene pictures, using their real measured pixels. */
data class FlowTransition(
    val fromIndex: Int,
    val toIndex: Int,
    val type: String,
    val ms: Long,
    val reason: String
)

enum class FlowPhase { IDLE, WAITING_LOGIN, GENERATING, DOWNLOADING, VERIFYING, TRANSITIONS, DONE, STOPPED, FAILED }

data class FlowRunState(
    val phase: FlowPhase = FlowPhase.IDLE,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val loggedIn: Boolean = false,
    val lastImagePath: String = ""
) {
    val running: Boolean
        get() = phase == FlowPhase.GENERATING || phase == FlowPhase.DOWNLOADING ||
            phase == FlowPhase.VERIFYING || phase == FlowPhase.TRANSITIONS || phase == FlowPhase.WAITING_LOGIN
}

/** Immutable JSON for both the plan and the library. Never uses Locale-dependent formatting. */
object FlowJson {

    private fun slotToJson(s: FlowSlot): JSONObject = JSONObject().apply {
        put("index", s.index); put("sceneId", s.sceneId); put("role", s.role)
        put("prompt", s.prompt); put("negative", s.negative); put("aspect", s.aspect); put("sceneMs", s.sceneMs)
        put("status", s.status.name); put("tries", s.tries)
        put("imagePath", s.imagePath); put("imageUrl", s.imageUrl); put("imageHash", s.imageHash)
        put("error", s.error)
        put("layer", s.layer); put("z", s.z)
        put("tx", s.targetX.toDouble()); put("ty", s.targetY.toDouble())
        put("tw", s.targetW.toDouble()); put("th", s.targetH.toDouble())
        put("motion", s.motion); put("cut", s.cutPath); put("removeBg", s.removeBg); put("border", s.borderHex)
        s.review?.let { put("review", reviewToJson(it)) }
    }

    private fun slotFromJson(o: JSONObject): FlowSlot = FlowSlot(
        index = o.optInt("index"),
        sceneId = o.optString("sceneId"),
        role = o.optString("role", FlowRoles.BACKGROUND).let { if (it in FlowRoles.ALL) it else FlowRoles.BACKGROUND },
        prompt = o.optString("prompt"),
        negative = o.optString("negative"),
        aspect = o.optString("aspect", "9:16"),
        sceneMs = o.optLong("sceneMs", 4000L),
        status = runCatching { FlowSlotStatus.valueOf(o.optString("status", "PENDING")) }.getOrDefault(FlowSlotStatus.PENDING),
        tries = o.optInt("tries"),
        imagePath = o.optString("imagePath"),
        imageUrl = o.optString("imageUrl"),
        imageHash = o.optString("imageHash"),
        review = o.optJSONObject("review")?.let { reviewFromJson(it) },
        error = o.optString("error"),
        layer = o.optString("layer", "").let { if (it in FlowLayerKind.ALL) it else "" },
        z = o.optInt("z"),
        targetX = o.optDouble("tx", 0.5).toFloat(), targetY = o.optDouble("ty", 0.5).toFloat(),
        targetW = o.optDouble("tw", 1.0).toFloat(), targetH = o.optDouble("th", 1.0).toFloat(),
        motion = o.optString("motion"),
        cutPath = o.optString("cut"),
        removeBg = o.optBoolean("removeBg"),
        borderHex = o.optString("border")
    )

    fun reviewToJson(r: FlowReview): JSONObject = JSONObject().apply {
        put("w", r.width); put("h", r.height); put("cropLoss", r.cropLoss.toDouble())
        put("fx", r.focalX.toDouble()); put("fy", r.focalY.toDouble())
        put("lum", r.brightness.toDouble()); put("con", r.contrast.toDouble()); put("sharp", r.sharpness.toDouble())
        put("corner", r.cornerUniform); put("palette", r.paletteHex)
        put("fit", r.fit); put("cx", r.cropX.toDouble()); put("cy", r.cropY.toDouble())
        put("glow", r.needsGlow); put("glowS", r.glowStrength.toDouble()); put("bg", r.needsBgRemove)
        put("in", r.animIn); put("out", r.animOut); put("drift", r.drift.toDouble())
        put("verdict", r.verdict); put("ai", r.aiVerdict); put("ok", r.ok)
    }

    fun reviewFromJson(o: JSONObject): FlowReview = FlowReview(
        width = o.optInt("w"), height = o.optInt("h"),
        cropLoss = o.optDouble("cropLoss", 0.0).toFloat(),
        focalX = o.optDouble("fx", 0.5).toFloat(), focalY = o.optDouble("fy", 0.5).toFloat(),
        brightness = o.optDouble("lum", 0.5).toFloat(), contrast = o.optDouble("con", 0.2).toFloat(),
        sharpness = o.optDouble("sharp", 0.3).toFloat(),
        cornerUniform = o.optBoolean("corner"), paletteHex = o.optString("palette", "#101018"),
        fit = o.optString("fit", "cover"), cropX = o.optDouble("cx", 0.0).toFloat(), cropY = o.optDouble("cy", 0.0).toFloat(),
        needsGlow = o.optBoolean("glow"), glowStrength = o.optDouble("glowS", 0.0).toFloat(),
        needsBgRemove = o.optBoolean("bg"), animIn = o.optString("in", "fadeIn"), animOut = o.optString("out", "fadeOut"),
        drift = o.optDouble("drift", 0.2).toFloat(), verdict = o.optString("verdict"),
        aiVerdict = o.optString("ai"), ok = o.optBoolean("ok", true)
    )

    fun transitionToJson(t: FlowTransition): JSONObject = JSONObject().apply {
        put("from", t.fromIndex); put("to", t.toIndex); put("type", t.type); put("ms", t.ms); put("why", t.reason)
    }

    fun transitionFromJson(o: JSONObject): FlowTransition = FlowTransition(
        o.optInt("from"), o.optInt("to"), o.optString("type", "fade"),
        o.optLong("ms", 400L), o.optString("why")
    )

    fun planToJson(p: FlowPlan): JSONObject = JSONObject().apply {
        put("id", p.id); put("projectId", p.projectId); put("topic", p.topic)
        put("aspect", p.aspect); put("style", p.style); put("createdAt", p.createdAt)
        put("slots", JSONArray().apply { p.slots.forEach { put(slotToJson(it)) } })
        put("notes", JSONArray().apply { p.notes.forEach { put(it) } })
        put("transitions", JSONArray().apply { p.transitions.forEach { put(transitionToJson(it)) } })
    }

    fun planFromJson(raw: String): FlowPlan? = runCatching {
        val o = JSONObject(raw)
        val sa = o.optJSONArray("slots") ?: JSONArray()
        val ta = o.optJSONArray("transitions") ?: JSONArray()
        val na = o.optJSONArray("notes") ?: JSONArray()
        FlowPlan(
            id = o.optString("id", UUID.randomUUID().toString()),
            projectId = o.optString("projectId"),
            topic = o.optString("topic"),
            aspect = o.optString("aspect", "9:16"),
            style = o.optString("style"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            slots = (0 until sa.length()).mapNotNull { i -> sa.optJSONObject(i)?.let { slotFromJson(it) } },
            notes = (0 until na.length()).map { na.optString(it) },
            transitions = (0 until ta.length()).mapNotNull { i -> ta.optJSONObject(i)?.let { transitionFromJson(it) } }
        )
    }.getOrNull()

    /** The library record: one downloaded/generated picture, with everything needed to place it. */
    fun recordToJson(r: FlowRecord): JSONObject = JSONObject().apply {
        put("id", r.id); put("title", r.title); put("path", r.path); put("url", r.url)
        put("kind", r.kind); put("ext", r.ext); put("bytes", r.bytes)
        put("w", r.width); put("h", r.height); put("hash", r.hash)
        put("promptIndex", r.promptIndex); put("sceneId", r.sceneId); put("createdAt", r.createdAt)
        put("source", r.source); put("role", r.role)
    }

    fun recordFromJson(o: JSONObject): FlowRecord = FlowRecord(
        id = o.optString("id"), title = o.optString("title"), path = o.optString("path"), url = o.optString("url"),
        kind = o.optString("kind", "image"), ext = o.optString("ext", "png"), bytes = o.optLong("bytes"),
        width = o.optInt("w"), height = o.optInt("h"), hash = o.optString("hash"),
        promptIndex = o.optInt("promptIndex", -1), sceneId = o.optString("sceneId"),
        createdAt = o.optLong("createdAt"), source = o.optString("source"), role = o.optString("role", FlowRoles.BACKGROUND)
    )
}

/** One real file in the Flow library. */
data class FlowRecord(
    val id: String,
    val title: String,
    val path: String,
    val url: String = "",
    val kind: String = "image",
    val ext: String = "png",
    val bytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val hash: String = "",
    val promptIndex: Int = -1,
    val sceneId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "flow-ai",
    val role: String = FlowRoles.BACKGROUND
)
