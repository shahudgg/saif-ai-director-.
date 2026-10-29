package com.example.studio.director

import org.json.JSONArray
import org.json.JSONObject

/**
 * FULL creative control over one library asset on the timeline: where it sits, how big it is, how it moves
 * (keyframes), how it glows / blends / grades and when it enters and leaves.
 *
 * Every number is NORMALIZED (0..1 of the frame, x/y = centre) so the same placement works for any export
 * resolution and any aspect ratio. [TimelineAssembler] converts it into the project's `ItemTransform`,
 * `ItemEffects`, `TransformKeyframe` and `ItemAnimation` values, which the editor's own Tune inspector can
 * still change afterwards — nothing is locked.
 */

/** Fit modes: how the picture is placed inside its box. */
val SUPPORTED_FIT_MODES = setOf("cover", "contain", "stretch", "fill")

/** Where the asset lives in the stack. Backdrop = full-frame behind the graphics; sticker = a small element above them. */
val SUPPORTED_PLACEMENT_ROLES = setOf("backdrop", "sticker", "overlay")

/** Blend modes understood by the renderer (mirrors `ItemTransform.blendMode`). */
val SUPPORTED_BLEND_MODES = setOf("Normal", "Screen", "Multiply", "Overlay", "Add", "Lighten", "Darken")

/** Entrance / exit animations an asset may use (must exist in `ItemAnimation` handling of the renderer). */
val SUPPORTED_PLACEMENT_ANIMS = setOf(
    "none", "fadeIn", "zoomIn", "slideLeft", "slideRight", "slideUp", "slideDown",
    "popIn", "spinIn", "blurIn", "flipIn", "fadeOut", "zoomOut", "popOut", "spinOut"
)

/** One keyframe of the asset's motion, at [tMs] milliseconds INSIDE the scene/clip. */
data class PlacementKey(
    val tMs: Long,
    val x: Float = 0.5f, val y: Float = 0.5f,
    val scale: Float = 1f, val rotation: Float = 0f, val opacity: Float = 1f,
    val easing: String = "easeOut"
)

data class AssetPlacement(
    /** Centre of the asset box, normalized 0..1 of the frame. */
    val x: Float = 0.5f, val y: Float = 0.5f,
    /** Box size as a fraction of the frame. 1.0 = as wide as the frame. */
    val width: Float = 1f, val height: Float = 1f,
    val fit: String = "cover",
    val role: String = "backdrop",
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val blend: String = "Normal",
    /** 0 = no glow, 1 = maximum. Drawn as an additive blurred copy of the picture. */
    val glow: Float = 0f,
    val glowColor: String = "#00E5FF",
    /** 0..1 drop shadow strength. */
    val shadow: Float = 0f,
    /** 0..1 blur strength. */
    val blur: Float = 0f,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    /** Degrees of hue rotation. */
    val hue: Float = 0f,
    val vignette: Float = 0f,
    /** Removes a solid green/blue background from a sticker. */
    val chromaKey: Boolean = false,
    val chromaColor: String = "#00FF00",
    val chromaTolerance: Float = 0.22f,
    /** Rounded-corner radius as a fraction of the shortest box side (0 = sharp). */
    val corner: Float = 0f,
    /** Motion keyframes, in order. Empty = the asset is static (only [animIn]/[animOut]). */
    val keyframes: List<PlacementKey> = emptyList(),
    val animIn: String = "none",
    val animInMs: Long = 350L,
    val animOut: String = "none",
    val animOutMs: Long = 300L,
    /** Slow automatic drift applied when there are no keyframes ("Ken Burns" style). 0 = off. */
    val drift: Float = 0f
) {
    companion object {
        /** Safe defaults for a full-frame b-roll backdrop. */
        val BACKDROP = AssetPlacement()

        /** Safe defaults for a sticker/PNG that sits above the graphics. */
        val STICKER = AssetPlacement(x = 0.5f, y = 0.38f, width = 0.52f, height = 0.34f, fit = "contain",
            role = "sticker", animIn = "popIn", animInMs = 380L, animOut = "fadeOut", animOutMs = 260L, drift = 0.25f)
    }

    val isBackdrop: Boolean get() = role == "backdrop" || (width >= 0.98f && height >= 0.98f)
}

/**
 * Clamping + parsing of placements. Everything is pure so it is unit-tested without a device, and so a
 * model that returns nonsense can never produce an invisible or frame-breaking asset.
 */
object AssetPlacements {

    private val hex = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun clamp(p: AssetPlacement): AssetPlacement = p.copy(
        x = p.x.coerceIn(-0.2f, 1.2f), y = p.y.coerceIn(-0.2f, 1.2f),
        width = p.width.coerceIn(0.04f, 2f), height = p.height.coerceIn(0.04f, 2f),
        fit = if (p.fit in SUPPORTED_FIT_MODES) p.fit else "cover",
        role = if (p.role in SUPPORTED_PLACEMENT_ROLES) p.role else "backdrop",
        rotation = p.rotation.coerceIn(-180f, 180f),
        opacity = p.opacity.coerceIn(0f, 1f),
        blend = if (p.blend in SUPPORTED_BLEND_MODES) p.blend else "Normal",
        glow = p.glow.coerceIn(0f, 1f),
        glowColor = if (hex.matches(p.glowColor)) p.glowColor else "#00E5FF",
        shadow = p.shadow.coerceIn(0f, 1f),
        blur = p.blur.coerceIn(0f, 1f),
        brightness = p.brightness.coerceIn(0.2f, 2.5f),
        contrast = p.contrast.coerceIn(0.2f, 2.5f),
        saturation = p.saturation.coerceIn(0f, 2.5f),
        hue = p.hue.coerceIn(-180f, 180f),
        vignette = p.vignette.coerceIn(0f, 1f),
        chromaColor = if (hex.matches(p.chromaColor)) p.chromaColor else "#00FF00",
        chromaTolerance = p.chromaTolerance.coerceIn(0.02f, 0.8f),
        corner = p.corner.coerceIn(0f, 0.5f),
        keyframes = sanitizeKeys(p.keyframes),
        animIn = if (p.animIn in SUPPORTED_PLACEMENT_ANIMS) p.animIn else "none",
        animInMs = p.animInMs.coerceIn(0L, 3000L),
        animOut = if (p.animOut in SUPPORTED_PLACEMENT_ANIMS) p.animOut else "none",
        animOutMs = p.animOutMs.coerceIn(0L, 3000L),
        drift = p.drift.coerceIn(0f, 1f)
    )

    /** Keyframes must be sorted, unique in time and inside the clip; at most 12 of them. */
    fun sanitizeKeys(keys: List<PlacementKey>, clipMs: Long = Long.MAX_VALUE): List<PlacementKey> {
        val out = keys
            .filter { it.tMs >= 0L && it.tMs <= clipMs }
            .sortedBy { it.tMs }
            .distinctBy { it.tMs }
            .take(12)
            .map { k ->
                k.copy(
                    x = k.x.coerceIn(-0.2f, 1.2f), y = k.y.coerceIn(-0.2f, 1.2f),
                    scale = k.scale.coerceIn(0.05f, 4f), rotation = k.rotation.coerceIn(-180f, 180f),
                    opacity = k.opacity.coerceIn(0f, 1f),
                    easing = if (k.easing in SUPPORTED_EASINGS) k.easing else "easeOut"
                )
            }
        // A single keyframe freezes the asset at that pose, which is never what a designer means: drop it.
        return if (out.size < 2) emptyList() else out
    }

    /**
     * A sensible placement when the model gave none, chosen from what the asset actually is:
     * a transparent PNG becomes a sticker with a pop-in, everything else becomes a backdrop.
     */
    fun defaultFor(kind: String, ext: String, transparent: Boolean = false, aspect: String? = null): AssetPlacement {
        val lower = ext.lowercase().removePrefix(".")
        val stickerLike = transparent || lower == "png" || lower == "webp"
        return if (kind == "image" && stickerLike) {
            val base = AssetPlacement.STICKER
            // Keep a wide sticker inside a narrow (9:16) frame.
            if (aspect == "9:16" || aspect == "4:5") base.copy(width = base.width.coerceAtMost(0.62f)) else base
        } else if (kind == "image") {
            AssetPlacement(role = "backdrop", width = 1f, height = 1f, fit = "cover", drift = 0.3f, animIn = "fadeIn", animInMs = 300L)
        } else {
            AssetPlacement(role = "backdrop", width = 1f, height = 1f, fit = "cover", drift = 0.35f, animIn = "none")
        }
    }

    // ---------------- JSON (model replies and project storage) ----------------

    fun parse(o: JSONObject?, fallback: AssetPlacement): AssetPlacement {
        if (o == null) return fallback
        val keys = o.optJSONArray("keyframes")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val k = a.optJSONObject(i) ?: return@mapNotNull null
                PlacementKey(
                    tMs = k.optLong("tMs", k.optLong("atMs", 0L)),
                    x = k.optDouble("x", 0.5).toFloat(), y = k.optDouble("y", 0.5).toFloat(),
                    scale = k.optDouble("scale", 1.0).toFloat(), rotation = k.optDouble("rotation", 0.0).toFloat(),
                    opacity = k.optDouble("opacity", 1.0).toFloat(), easing = k.optString("easing", "easeOut")
                )
            }
        } ?: emptyList()
        return clamp(
            fallback.copy(
                x = o.optDouble("x", fallback.x.toDouble()).toFloat(),
                y = o.optDouble("y", fallback.y.toDouble()).toFloat(),
                width = o.optDouble("w", o.optDouble("width", fallback.width.toDouble())).toFloat(),
                height = o.optDouble("h", o.optDouble("height", fallback.height.toDouble())).toFloat(),
                fit = o.optString("fit", fallback.fit).ifBlank { fallback.fit },
                role = o.optString("role", fallback.role).ifBlank { fallback.role },
                rotation = o.optDouble("rotation", fallback.rotation.toDouble()).toFloat(),
                opacity = o.optDouble("opacity", fallback.opacity.toDouble()).toFloat(),
                blend = o.optString("blend", o.optString("blendMode", fallback.blend)).ifBlank { fallback.blend },
                glow = o.optDouble("glow", fallback.glow.toDouble()).toFloat(),
                glowColor = o.optString("glowColor", fallback.glowColor).ifBlank { fallback.glowColor },
                shadow = o.optDouble("shadow", fallback.shadow.toDouble()).toFloat(),
                blur = o.optDouble("blur", fallback.blur.toDouble()).toFloat(),
                brightness = o.optDouble("brightness", fallback.brightness.toDouble()).toFloat(),
                contrast = o.optDouble("contrast", fallback.contrast.toDouble()).toFloat(),
                saturation = o.optDouble("saturation", fallback.saturation.toDouble()).toFloat(),
                hue = o.optDouble("hue", fallback.hue.toDouble()).toFloat(),
                vignette = o.optDouble("vignette", fallback.vignette.toDouble()).toFloat(),
                chromaKey = o.optBoolean("chromaKey", fallback.chromaKey),
                corner = o.optDouble("corner", fallback.corner.toDouble()).toFloat(),
                keyframes = keys,
                animIn = o.optString("animIn", fallback.animIn).ifBlank { fallback.animIn },
                animInMs = o.optLong("animInMs", fallback.animInMs),
                animOut = o.optString("animOut", fallback.animOut).ifBlank { fallback.animOut },
                animOutMs = o.optLong("animOutMs", fallback.animOutMs),
                drift = o.optDouble("drift", fallback.drift.toDouble()).toFloat()
            )
        )
    }

    fun toJson(p: AssetPlacement): JSONObject = JSONObject().apply {
        put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("w", p.width.toDouble()); put("h", p.height.toDouble())
        put("fit", p.fit); put("role", p.role); put("rotation", p.rotation.toDouble()); put("opacity", p.opacity.toDouble())
        put("blend", p.blend); put("glow", p.glow.toDouble()); put("glowColor", p.glowColor)
        put("shadow", p.shadow.toDouble()); put("blur", p.blur.toDouble())
        put("brightness", p.brightness.toDouble()); put("contrast", p.contrast.toDouble())
        put("saturation", p.saturation.toDouble()); put("hue", p.hue.toDouble()); put("vignette", p.vignette.toDouble())
        put("chromaKey", p.chromaKey); put("corner", p.corner.toDouble())
        put("animIn", p.animIn); put("animInMs", p.animInMs); put("animOut", p.animOut); put("animOutMs", p.animOutMs)
        put("drift", p.drift.toDouble())
        put("keyframes", JSONArray().apply {
            p.keyframes.forEach { k ->
                put(JSONObject().apply {
                    put("tMs", k.tMs); put("x", k.x.toDouble()); put("y", k.y.toDouble())
                    put("scale", k.scale.toDouble()); put("rotation", k.rotation.toDouble())
                    put("opacity", k.opacity.toDouble()); put("easing", k.easing)
                })
            }
        })
    }

    /**
     * Compact text form stored in `TimelineItem.aiWhy` so the geometry survives a project save/load even though the
     * timeline schema is unchanged (the grade, blend and keyframes live in `ItemEffects` / `ItemTransform` /
     * `TransformKeyframe`, which the project codec already persists).
     * Example: `@pl=0.50,0.38,0.52x0.34,contain,sticker,glow0.40,drift0.25`.
     */
    fun encodeTag(p: AssetPlacement): String {
        val sb = StringBuilder("@pl=")
        sb.append(f2(p.x)).append(',').append(f2(p.y)).append(',').append(f2(p.width)).append('x').append(f2(p.height))
        sb.append(',').append(p.fit).append(',').append(p.role)
        if (p.glow > 0.001f) sb.append(",glow").append(f2(p.glow))
        if (p.rotation != 0f) sb.append(",rot").append(p.rotation.toInt())
        if (p.opacity < 0.999f) sb.append(",op").append(f2(p.opacity))
        if (p.drift > 0.001f) sb.append(",drift").append(f2(p.drift))
        if (p.shadow > 0.001f) sb.append(",shd").append(f2(p.shadow))
        if (p.blur > 0.001f) sb.append(",blr").append(f2(p.blur))
        if (p.vignette > 0.001f) sb.append(",vig").append(f2(p.vignette))
        if (p.corner > 0.001f) sb.append(",crn").append(f2(p.corner))
        if (p.chromaKey) sb.append(",chroma").append(p.chromaColor.removePrefix("#")).append(':').append(f2(p.chromaTolerance))
        return sb.toString()
    }

    /** Inverse of [encodeTag]; null when the item carries no placement tag. */
    fun decodeTag(tag: String?): AssetPlacement? {
        if (tag == null) return null
        val raw = Regex("@pl=([0-9.,x:#\\-A-Za-z]+)").find(tag)?.groupValues?.get(1) ?: return null
        val parts = raw.split(',')
        // encodeTag always writes exactly five fields (x, y, WxH, fit, role) and then the optional extras, so a
        // tag with five parts is complete. Requiring six silently threw EVERY placement away on project reload.
        if (parts.size < 5) return null
        val px = parts[0].toFloatOrNull() ?: return null
        val py = parts[1].toFloatOrNull() ?: return null
        val wh = parts[2].split('x')
        val w = wh.getOrNull(0)?.toFloatOrNull() ?: 1f
        val h = wh.getOrNull(1)?.toFloatOrNull() ?: 1f
        var p = AssetPlacement(x = px, y = py, width = w, height = h, fit = parts[3], role = parts[4])
        for (extra in parts.drop(5)) {
            when {
                extra.startsWith("glow") -> p = p.copy(glow = extra.removePrefix("glow").toFloatOrNull() ?: 0f)
                extra.startsWith("rot") -> p = p.copy(rotation = extra.removePrefix("rot").toFloatOrNull() ?: 0f)
                extra.startsWith("op") -> p = p.copy(opacity = extra.removePrefix("op").toFloatOrNull() ?: 1f)
                extra.startsWith("drift") -> p = p.copy(drift = extra.removePrefix("drift").toFloatOrNull() ?: 0f)
                extra.startsWith("shd") -> p = p.copy(shadow = extra.removePrefix("shd").toFloatOrNull() ?: 0f)
                extra.startsWith("blr") -> p = p.copy(blur = extra.removePrefix("blr").toFloatOrNull() ?: 0f)
                extra.startsWith("vig") -> p = p.copy(vignette = extra.removePrefix("vig").toFloatOrNull() ?: 0f)
                extra.startsWith("crn") -> p = p.copy(corner = extra.removePrefix("crn").toFloatOrNull() ?: 0f)
                extra.startsWith("chroma") -> {
                    val body = extra.removePrefix("chroma")
                    val hexPart = body.substringBefore(':').ifBlank { "00FF00" }
                    val tol = body.substringAfter(':', "0.22").toFloatOrNull() ?: 0.22f
                    p = p.copy(chromaKey = true, chromaColor = "#${hexPart.take(6).uppercase()}", chromaTolerance = tol)
                }
            }
        }
        return clamp(p)
    }

    /** Placement of a timeline item, read from its `aiWhy` tag; null when the item has none (legacy/manual items). */
    fun of(aiWhy: String?): AssetPlacement? = decodeTag(aiWhy)

    /** True when this item is a full-frame backdrop (the old behaviour), so existing projects render exactly as before. */
    fun isBackdropTag(aiWhy: String?): Boolean {
        val p = decodeTag(aiWhy) ?: return aiWhy?.contains(":asset:") == true
        return p.isBackdrop
    }

    /**
     * Two-decimal formatting for the tag. Locale-fixed on purpose: `String.format` follows the device locale, and a
     * comma-decimal locale (de, fr, pt...) would write "0,50" - which `decodeTag` reads back as two fields and so
     * loses the placement. The tag is data, not display text.
     */
    private fun f2(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)

    /**
     * Reference pixel size of an aspect ratio. `ItemTransform.x/y` are pixels around the frame centre and the
     * editor's own sliders use a 1080-wide space, so normalized placements are converted through this.
     */
    fun referenceSize(aspect: String?): Pair<Int, Int> = when ((aspect ?: "9:16").trim()) {
        "16:9" -> 1920 to 1080
        "1:1" -> 1080 to 1080
        "4:5" -> 1080 to 1350
        "4:3" -> 1440 to 1080
        "2.35:1" -> 1920 to 817
        else -> 1080 to 1920
    }
}
