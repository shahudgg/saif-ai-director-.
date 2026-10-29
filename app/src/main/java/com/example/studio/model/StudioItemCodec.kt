package com.example.studio.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Lossless JSON codec for every [TimelineItem] field, so a saved project reopens exactly as it was. */
object StudioItemCodec {

    private fun transformToJson(t: ItemTransform) = JSONObject().apply {
        put("x", t.x.toDouble()); put("y", t.y.toDouble()); put("scale", t.scale.toDouble())
        put("rotation", t.rotation.toDouble()); put("opacity", t.opacity.toDouble()); put("blendMode", t.blendMode)
    }

    private fun transformFrom(o: JSONObject?): ItemTransform {
        if (o == null) return ItemTransform()
        return ItemTransform(
            x = o.optDouble("x", 0.0).toFloat(), y = o.optDouble("y", 0.0).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(), rotation = o.optDouble("rotation", 0.0).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(), blendMode = o.optString("blendMode", "Normal")
        )
    }

    private fun effectsToJson(e: ItemEffects) = JSONObject().apply {
        put("neonGlow", e.neonGlow.toDouble()); put("neonColorHex", e.neonColorHex)
        put("dropShadow", e.dropShadow.toDouble()); put("blur", e.blur.toDouble())
        put("brightness", e.brightness.toDouble()); put("contrast", e.contrast.toDouble())
        put("saturation", e.saturation.toDouble()); put("hue", e.hue.toDouble())
        put("vignette", e.vignette.toDouble()); put("chromaKey", e.chromaKey)
    }

    private fun effectsFrom(o: JSONObject?): ItemEffects {
        if (o == null) return ItemEffects()
        return ItemEffects(
            neonGlow = o.optDouble("neonGlow", 0.0).toFloat(), neonColorHex = o.optLong("neonColorHex", 0xFF00E5FF),
            dropShadow = o.optDouble("dropShadow", 0.0).toFloat(), blur = o.optDouble("blur", 0.0).toFloat(),
            brightness = o.optDouble("brightness", 1.0).toFloat(), contrast = o.optDouble("contrast", 1.0).toFloat(),
            saturation = o.optDouble("saturation", 1.0).toFloat(), hue = o.optDouble("hue", 0.0).toFloat(),
            vignette = o.optDouble("vignette", 0.0).toFloat(), chromaKey = o.optBoolean("chromaKey", false)
        )
    }

    private fun animToJson(a: ItemAnimation?): JSONObject? = a?.let {
        JSONObject().apply { put("type", it.type); put("durationUs", it.durationUs); put("easing", it.easing) }
    }

    private fun animFrom(o: JSONObject?): ItemAnimation? = o?.let {
        ItemAnimation(it.optString("type", "fade"), it.optLong("durationUs", 300_000L), it.optString("easing", "easeOutBack"))
    }

    private fun keyframesToJson(k: List<TransformKeyframe>) = JSONArray().apply {
        k.forEach { put(JSONObject().apply {
            put("timeUs", it.timeUs); put("x", it.x.toDouble()); put("y", it.y.toDouble())
            put("scale", it.scale.toDouble()); put("rotation", it.rotation.toDouble()); put("opacity", it.opacity.toDouble())
        }) }
    }

    private fun keyframesFrom(a: JSONArray?): List<TransformKeyframe> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            TransformKeyframe(
                o.optLong("timeUs", 0L), o.optDouble("x", 0.0).toFloat(), o.optDouble("y", 0.0).toFloat(),
                o.optDouble("scale", 1.0).toFloat(), o.optDouble("rotation", 0.0).toFloat(), o.optDouble("opacity", 1.0).toFloat()
            )
        }
    }

    fun toJson(item: TimelineItem): JSONObject = JSONObject().apply {
        put("id", item.id); put("laneId", item.laneId); put("trackType", item.trackType.name)
        put("startUs", item.startUs); put("durationUs", item.durationUs)
        put("label", item.label); put("origin", item.origin); put("locked", item.locked); put("aiWhy", item.aiWhy ?: "")
        put("transform", transformToJson(item.transform)); put("effects", effectsToJson(item.effects))
        animToJson(item.animIn)?.let { put("animIn", it) }
        animToJson(item.animOut)?.let { put("animOut", it) }
        put("keyframes", keyframesToJson(item.keyframes))
        when (item) {
            is VideoItem -> {
                put("mediaUri", item.mediaUri); put("sourceInUs", item.sourceInUs); put("sourceOutUs", item.sourceOutUs)
                put("naturalWidth", item.naturalWidth); put("naturalHeight", item.naturalHeight)
                put("naturalFps", item.naturalFps.toDouble()); put("playbackSpeed", item.playbackSpeed.toDouble())
                put("speedRampCurve", item.speedRampCurve); put("volume", item.volume.toDouble())
                put("transitionIn", item.transitionIn); put("transitionDurationUs", item.transitionDurationUs)
            }
            is ImageItem -> {
                put("mediaUri", item.mediaUri); put("transitionIn", item.transitionIn)
                put("transitionDurationUs", item.transitionDurationUs)
            }
            is TextItem -> {
                put("text", item.text); put("fontStyle", item.fontStyle); put("fontSizeSp", item.fontSizeSp.toDouble())
                put("textColorHex", item.textColorHex); put("outlineColorHex", item.outlineColorHex)
                put("outlineWidth", item.outlineWidth.toDouble()); put("isBold", item.isBold)
                put("hasBackgroundBox", item.hasBackgroundBox); put("boxColorHex", item.boxColorHex)
                put("isKaraoke", item.isKaraoke); put("stylePreset", item.stylePreset)
            }
            is VoiceItem -> {
                put("audioUri", item.audioUri ?: ""); put("text", item.text); put("speakerTone", item.speakerTone)
                put("volume", item.volume.toDouble()); put("speed", item.speed.toDouble()); put("audioDucking", item.audioDucking)
            }
            is AudioItem -> {
                put("audioUri", item.audioUri ?: ""); put("isSfx", item.isSfx); put("volume", item.volume.toDouble())
                put("speed", item.speed.toDouble()); put("fadeInUs", item.fadeInUs); put("fadeOutUs", item.fadeOutUs)
                put("audioDucking", item.audioDucking); put("duckingAmount", item.duckingAmount.toDouble())
            }
            is CodeSceneItem -> {
                put("title", item.title); put("htmlCode", item.htmlCode); put("animationType", item.animationType)
                put("isOverlay", item.isOverlay); put("proxyVideoPath", item.proxyVideoPath ?: "")
            }
        }
    }

    private fun trackTypeOf(name: String): TrackType =
        TrackType.values().firstOrNull { it.name == name } ?: TrackType.VIDEO

    fun fromJson(o: JSONObject): TimelineItem {
        val type = trackTypeOf(o.optString("trackType", TrackType.VIDEO.name))
        val id = o.optString("id", UUID.randomUUID().toString())
        val laneId = o.optString("laneId", "")
        val startUs = o.optLong("startUs", 0L)
        val durationUs = o.optLong("durationUs", 1_000_000L)
        val label = o.optString("label", "Item")
        val origin = o.optString("origin", "manual")
        val locked = o.optBoolean("locked", false)
        val aiWhy = o.optString("aiWhy", "").ifBlank { null }
        val transform = transformFrom(o.optJSONObject("transform"))
        val effects = effectsFrom(o.optJSONObject("effects"))
        val animIn = animFrom(o.optJSONObject("animIn"))
        val animOut = animFrom(o.optJSONObject("animOut"))
        val keyframes = keyframesFrom(o.optJSONArray("keyframes"))
        return when (type) {
            TrackType.VIDEO -> VideoItem(
                id = id, laneId = laneId, startUs = startUs, durationUs = durationUs,
                mediaUri = o.optString("mediaUri", ""), sourceInUs = o.optLong("sourceInUs", 0L),
                sourceOutUs = o.optLong("sourceOutUs", durationUs),
                naturalWidth = o.optInt("naturalWidth", 1080), naturalHeight = o.optInt("naturalHeight", 1920),
                naturalFps = o.optDouble("naturalFps", 30.0).toFloat(),
                playbackSpeed = o.optDouble("playbackSpeed", 1.0).toFloat(),
                speedRampCurve = o.optString("speedRampCurve", "Linear"),
                volume = o.optDouble("volume", 1.0).toFloat(),
                transitionIn = o.optString("transitionIn", "None"),
                transitionDurationUs = o.optLong("transitionDurationUs", 300_000L),
                transform = transform, effects = effects, animIn = animIn, animOut = animOut, keyframes = keyframes,
                locked = locked, label = label, origin = origin, aiWhy = aiWhy
            )
            TrackType.VISUAL -> ImageItem(
                id = id, laneId = laneId, startUs = startUs, durationUs = durationUs,
                mediaUri = o.optString("mediaUri", ""),
                transitionIn = o.optString("transitionIn", "None"),
                transitionDurationUs = o.optLong("transitionDurationUs", 300_000L),
                transform = transform, effects = effects, animIn = animIn, animOut = animOut, keyframes = keyframes,
                locked = locked, label = label, origin = origin, aiWhy = aiWhy
            )
            TrackType.TEXT -> TextItem(
                id = id, laneId = laneId, startUs = startUs, durationUs = durationUs,
                text = o.optString("text", ""), fontStyle = o.optString("fontStyle", "sans-serif"),
                fontSizeSp = o.optDouble("fontSizeSp", 24.0).toFloat(),
                textColorHex = o.optLong("textColorHex", 0xFFFFFFFF),
                outlineColorHex = o.optLong("outlineColorHex", 0xFF000000),
                outlineWidth = o.optDouble("outlineWidth", 2.0).toFloat(),
                isBold = o.optBoolean("isBold", true), hasBackgroundBox = o.optBoolean("hasBackgroundBox", true),
                boxColorHex = o.optLong("boxColorHex", 0x88000000), isKaraoke = o.optBoolean("isKaraoke", false),
                stylePreset = o.optString("stylePreset", "Hormozi Neon"),
                transform = transform, effects = effects, animIn = animIn, animOut = animOut, keyframes = keyframes,
                locked = locked, label = label, origin = origin, aiWhy = aiWhy
            )
            TrackType.VOICE -> VoiceItem(
                id = id, laneId = laneId, startUs = startUs, durationUs = durationUs,
                audioUri = o.optString("audioUri", "").ifBlank { null },
                text = o.optString("text", ""), speakerTone = o.optString("speakerTone", "Energetic"),
                volume = o.optDouble("volume", 1.0).toFloat(), speed = o.optDouble("speed", 1.0).toFloat(),
                audioDucking = o.optBoolean("audioDucking", false),
                transform = transform, effects = effects, animIn = animIn, animOut = animOut, keyframes = keyframes,
                locked = locked, label = label, origin = o.optString("origin", "ai"), aiWhy = aiWhy
            )
            TrackType.AUDIO -> AudioItem(
                id = id, laneId = laneId, startUs = startUs, durationUs = durationUs,
                audioUri = o.optString("audioUri", "").ifBlank { null },
                isSfx = o.optBoolean("isSfx", true), volume = o.optDouble("volume", 0.8).toFloat(),
                speed = o.optDouble("speed", 1.0).toFloat(),
                fadeInUs = o.optLong("fadeInUs", 0L), fadeOutUs = o.optLong("fadeOutUs", 0L),
                audioDucking = o.optBoolean("audioDucking", false),
                duckingAmount = o.optDouble("duckingAmount", 0.35).toFloat(),
                transform = transform, effects = effects, animIn = animIn, animOut = animOut, keyframes = keyframes,
                locked = locked, label = label, origin = origin, aiWhy = aiWhy
            )
            TrackType.CODE -> CodeSceneItem(
                id = id, laneId = laneId, startUs = startUs, durationUs = durationUs,
                title = o.optString("title", "Code Scene"), htmlCode = o.optString("htmlCode", ""),
                animationType = o.optString("animationType", "GSAP Motion Graphics"),
                isOverlay = o.optBoolean("isOverlay", false),
                proxyVideoPath = o.optString("proxyVideoPath", "").ifBlank { null },
                transform = transform, effects = effects, animIn = animIn, animOut = animOut, keyframes = keyframes,
                locked = locked, label = label, origin = o.optString("origin", "ai"), aiWhy = aiWhy
            )
        }
    }
}
