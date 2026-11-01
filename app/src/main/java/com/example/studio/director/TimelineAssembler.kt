package com.example.studio.director

import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.ItemAnimation
import com.example.studio.model.ItemEffects
import com.example.studio.model.ItemTransform
import com.example.studio.model.StudioLane
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.TimelineItem
import com.example.studio.model.TrackType
import com.example.studio.model.TransformKeyframe
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import java.util.UUID

/** A narration/voice-over file with its measured duration and the scene it belongs to. */
data class NarrationClip(val sceneId: String, val uri: String, val durationMs: Long, val text: String,
                         /** Scenes this one clip is spoken over, in order (first = [sceneId]). Empty/one entry = a normal single-scene clip. */
                         val groupIds: List<String> = emptyList())
/** A verified asset bound to a scene. Source/licence info is kept for project metadata. */
data class ResolvedAsset(val sceneId: String, val requestId: String, val kind: String, val localPath: String,
                         val source: String, val verified: Boolean, val note: String = "",
                         /** Where inside the scene the asset starts and how long it plays (0 = whole scene). Used for sound effects. */
                         val atMs: Long = 0L, val durMs: Long = 0L,
                         /** File extension of the downloaded asset (png, jpg, mp4, mp3...), used to pick the right default placement. */
                         val ext: String = "",
                         /** True when the picture has a transparent background (PNG/WebP), so it is placed as a sticker, not a backdrop. */
                         val transparent: Boolean = false,
                         /**
                          * FULL placement control: position, size, fit, keyframes, glow, blend, grade, corner and
                          * entrance/exit animation. Null = the assembler picks a sensible default for the file type.
                          */
                         val placement: AssetPlacement? = null)
data class FootageRange(val sourceUri: String, val sourceInMs: Long, val sourceOutMs: Long, val width: Int, val height: Int, val why: String)

/** Turns a normal scene into an overlay that sits ABOVE b-roll/pictures: transparent backdrop plus a soft dark scrim. */
object SceneOverlay {
    fun apply(g: SceneGraph): SceneGraph {
        if (g.elements.any { it.id == "asset_scrim" }) return g
        val scrim = Element(id = "asset_scrim", type = "rect", x = 0.5f, y = 0.5f, w = 1.3f, h = 1.3f, color = "#000000",
            anims = listOf(Anim("alpha", 0.52f, 0.52f, 0L, 1L, "linear")))
        return g.copy(bgTop = "#00000000", bgBottom = "#00000000", elements = listOf(scrim) + g.elements)
    }

    /** Sound effect volume by kind: effects support the voice, they never compete with it. */
    fun sfxVolume(kind: String): Float = when (kind) {
        "hit" -> 0.42f; "whoosh" -> 0.36f; "riser" -> 0.28f; "ding" -> 0.30f; "glitch" -> 0.28f
        "pop" -> 0.34f; "tick" -> 0.24f; "click" -> 0.28f; "typing" -> 0.26f; else -> 0.32f
    }
}

/**
 * Converts an [AssetPlacement] (normalized, aspect-independent) into the project's own timeline values, so the
 * asset lands on the timeline with FULL control that the editor's Tune inspector can still change afterwards:
 * position, size, fit, rotation, opacity, blend mode, neon glow, drop shadow, blur, brightness, contrast,
 * saturation, hue, vignette, chroma key, rounded corner, motion keyframes and entrance/exit animation.
 *
 * The geometry (x, y, w, h, fit, role) travels in the `@pl=` tag inside `aiWhy`; the look travels in
 * `ItemEffects`, `ItemTransform` and `TransformKeyframe`, all of which the project codec already persists.
 */
object PlacementApplier {

    /** Final placement for one resolved asset: the model's explicit one, or a sensible default for the file type. */
    fun of(a: ResolvedAsset, aspect: String?, sceneDurationMs: Long): AssetPlacement {
        val fallback = AssetPlacements.defaultFor(a.kind, a.ext, a.transparent, aspect)
        val p = AssetPlacements.clamp(a.placement ?: fallback)
        // A clip is never a small sticker: the preview plays video full-frame, so keep clips as backdrops.
        val safe = if (a.kind == "video") p.copy(role = "backdrop", width = 1f, height = 1f, fit = "cover") else p
        return AssetPlacements.clamp(safe.copy(keyframes = AssetPlacements.sanitizeKeys(safe.keyframes, sceneDurationMs)))
    }

    fun effects(p: AssetPlacement): ItemEffects = ItemEffects(
        neonGlow = p.glow,
        neonColorHex = hexToLong(p.glowColor, 0xFF00E5FF),
        dropShadow = p.shadow,
        blur = p.blur,
        brightness = p.brightness,
        contrast = p.contrast,
        saturation = p.saturation,
        hue = p.hue,
        vignette = p.vignette,
        chromaKey = p.chromaKey
    )

    /** Base transform. Position and size live in the placement box, so the base offset is zero. */
    fun transform(p: AssetPlacement): ItemTransform =
        ItemTransform(x = 0f, y = 0f, scale = 1f, rotation = p.rotation, opacity = p.opacity, blendMode = p.blend)

    /** Motion keyframes in absolute timeline microseconds; x/y are pixel offsets from the placement centre. */
    fun keyframes(p: AssetPlacement, startUs: Long, aspect: String?): List<TransformKeyframe> {
        if (p.keyframes.size < 2) return emptyList()
        val ref = AssetPlacements.referenceSize(aspect)
        return p.keyframes.map { k ->
            TransformKeyframe(
                timeUs = startUs + k.tMs * 1000L,
                x = (k.x - p.x) * ref.first,
                y = (k.y - p.y) * ref.second,
                scale = k.scale,
                rotation = k.rotation,
                opacity = k.opacity
            )
        }
    }

    fun animIn(p: AssetPlacement): ItemAnimation? =
        if (p.animIn == "none" || p.animIn.isBlank() || p.animInMs <= 0L) null
        else ItemAnimation(p.animIn, p.animInMs * 1000L, "easeOut")

    fun animOut(p: AssetPlacement): ItemAnimation? =
        if (p.animOut == "none" || p.animOut.isBlank() || p.animOutMs <= 0L) null
        else ItemAnimation(p.animOut, p.animOutMs * 1000L, "easeIn")

    /** One-line description of the placement for the chat report and the item's tooltip. */
    fun describe(p: AssetPlacement): String = buildString {
        append(if (p.isBackdrop) "backdrop" else "sticker")
        append(" ${"%.0f".format(p.x * 100)}%,${"%.0f".format(p.y * 100)}%")
        if (!p.isBackdrop) append(" ${"%.0f".format(p.width * 100)}x${"%.0f".format(p.height * 100)}%")
        append(" ${p.fit}")
        if (p.glow > 0.01f) append(" glow${"%.0f".format(p.glow * 100)}%")
        if (p.keyframes.size >= 2) append(" ${p.keyframes.size} keyframes")
        if (p.animIn != "none") append(" in:${p.animIn}")
        if (p.animOut != "none") append(" out:${p.animOut}")
        if (p.blend != "Normal") append(" blend:${p.blend}")
    }

    private fun hexToLong(s: String, fallback: Long): Long = try {
        val clean = s.removePrefix("#")
        val v = java.lang.Long.parseLong(clean, 16)
        if (clean.length == 6) 0xFF000000L or v else v
    } catch (_: Exception) { fallback }
}

object TimelineAssembler {
    private fun us(ms: Long) = ms * 1000L

    private fun laneId(p: StudioProject, t: TrackType, fallback: String) = p.lanes.firstOrNull { it.trackType == t }?.id ?: fallback

    private fun ensureLanes(p: StudioProject): StudioProject {
        val have = p.lanes.map { it.trackType }.toSet()
        val add = StudioProject.defaultEmptyLanes().filter { it.trackType !in have }
        return if (add.isEmpty()) p else p.copy(lanes = p.lanes + add)
    }

    /** Removes items previously created by the director for these scenes (so regeneration doesn't duplicate). */
    fun clearAi(p: StudioProject, sceneIds: Set<String>? = null): StudioProject =
        p.copy(items = p.items.filterNot { it.origin == "ai" && it.aiWhy?.startsWith("director:") == true &&
            (sceneIds == null || sceneIds.any { id -> it.aiWhy!!.startsWith("director:$id:") }) })

    private fun why(sceneId: String, role: String, reason: String) = "director:$sceneId:$role:$reason"

    /**
     * Builds editable timeline items from the manifest. Nothing is added without a source or a renderable definition.
     * Returns the new project plus any problems (missing media, etc.) so the UI can report them honestly.
     */
    fun assemble(
        base: StudioProject, manifest: SceneManifest, narration: List<NarrationClip>, assets: List<ResolvedAsset>,
        footage: List<FootageRange> = emptyList(), captions: Boolean = true, replaceExisting: Boolean = true,
        voiceOverUri: String? = null, aspect: String? = null,
        sfx: Map<String, Pair<String, Long>> = emptyMap(),
        captionStyle: String = com.example.studio.render.CaptionStyles.HORMOZI,
        appendAfterExisting: Boolean = false
    ): Pair<StudioProject, List<String>> {
        val problems = mutableListOf<String>()
        var p = ensureLanes(if (replaceExisting) clearAi(base) else base)
        if (aspect != null) p = p.copy(aspect = aspect)
        p = p.copy(fps = manifest.fps)
        val items = mutableListOf<TimelineItem>()
        val codeLane = laneId(p, TrackType.CODE, "lane_code_1"); val videoLane = laneId(p, TrackType.VIDEO, "lane_video_1")
        val visLane = laneId(p, TrackType.VISUAL, "lane_visual_1"); val textLane = laneId(p, TrackType.TEXT, "lane_text_1")
        val voiceLane = laneId(p, TrackType.VOICE, "lane_voice_1"); val audLane = laneId(p, TrackType.AUDIO, "lane_audio_1")
        // Scene-level edits preserve the manifest's original timestamps. Explicitly opt in to appending a whole new sequence.
        val tOffset = if (appendAfterExisting) p.totalDurationUs else 0L

        manifest.scenes.sortedBy { it.startMs }.forEach { s ->
            val start = us(s.startMs) + tOffset; val dur = us(s.durationMs)
            if (dur <= 0) { problems += "Scene ${s.id}: invalid duration"; return@forEach }
            val tr = if (s.transitionIn == "none") null else ItemAnimation(s.transitionIn, us(s.transitionMs.coerceAtMost(s.durationMs / 2)))

            // Resolve every media asset of this scene to its FULL placement first, so the scene knows whether it has
            // to become a transparent overlay (a backdrop picture) or stay opaque (stickers only, drawn above it).
            val sceneAssets = assets.filter { it.sceneId == s.id && it.localPath.isNotBlank() && (it.verified || it.note.isNotBlank()) }
            val placed: List<Pair<ResolvedAsset, AssetPlacement>> = sceneAssets
                .filter { a -> a.kind == "image" || a.kind == "video" || a.kind == "overlay" }
                .map { a -> a to PlacementApplier.of(a, aspect ?: p.aspect, s.durationMs) }
            // A usable picture/clip BEHIND the scene: the scene becomes an overlay (transparent backdrop) drawn above the media.
            val hasBackdrop = placed.any { it.second.isBackdrop }
            s.graph?.let { g ->
                val gg = if (hasBackdrop) SceneOverlay.apply(g) else g
                items += CodeSceneItem(UUID.randomUUID().toString(), codeLane, start, dur, title = s.visualObjective.take(40).ifBlank { "Scene ${s.id}" },
                    htmlCode = SceneJson.encodeForTimeline(gg), animationType = if (hasBackdrop) SCENE_OVERLAY_MARKER else SCENE_GRAPH_MARKER, animIn = tr, origin = "ai",
                    aiWhy = why(s.id, "graphics", s.visualObjective.take(80)))
            }
            assets.filter { it.sceneId == s.id }.forEach { a ->
                if (a.localPath.isBlank() || !a.verified && a.note.isBlank()) { problems += "Scene ${s.id}: asset ${a.requestId} has no usable file"; return@forEach }
                val uri = if (a.localPath.contains("://")) a.localPath else "file://${a.localPath}"
                when (a.kind) {
                    "image", "overlay", "video" -> {
                        // Full placement: geometry, grade, glow, blend, keyframes and entrance/exit animation.
                        val pl = placed.firstOrNull { it.first === a }?.second ?: PlacementApplier.of(a, aspect ?: p.aspect, s.durationMs)
                        val fx = PlacementApplier.effects(pl)
                        val tf = PlacementApplier.transform(pl)
                        val kf = PlacementApplier.keyframes(pl, start, aspect ?: p.aspect)
                        val inAnim = PlacementApplier.animIn(pl) ?: tr
                        val outAnim = PlacementApplier.animOut(pl)
                        val tag = AssetPlacements.encodeTag(pl)
                        val note = "${a.source}; ${a.note}".take(90)
                        if (a.kind == "video") {
                            items += VideoItem(UUID.randomUUID().toString(), videoLane, start, dur, uri, sourceOutUs = dur, label = "B-roll ${a.requestId}",
                                volume = 0f, transform = tf, effects = fx, animIn = inAnim, animOut = outAnim, keyframes = kf, origin = "ai",
                                aiWhy = why(s.id, "asset", "$note $tag").take(150))
                        } else {
                            items += ImageItem(UUID.randomUUID().toString(), visLane, start, dur, uri, label = "Asset ${a.requestId}",
                                transform = tf, effects = fx, animIn = inAnim, animOut = outAnim, keyframes = kf, origin = "ai",
                                aiWhy = why(s.id, "asset", "$note $tag").take(150))
                        }
                    }
                    "audio" -> {
                        val off = a.atMs.coerceIn(0L, (s.durationMs - 100L).coerceAtLeast(0L))
                        val len = if (a.durMs > 0L) minOf(a.durMs, 6000L) else (s.durationMs - off)
                        items += AudioItem(UUID.randomUUID().toString(), audLane, start + us(off), us(len.coerceAtLeast(100L)), uri, isSfx = true,
                            volume = 0.5f, audioDucking = true, duckingAmount = 0.5f, fadeOutUs = us(minOf(250L, len / 3)),
                            label = "SFX ${a.requestId}", origin = "ai", aiWhy = why(s.id, "sfx", "@$off library: ${a.source}".take(90)))
                    }
                }
            }
            if (captions && s.caption.isNotBlank()) {
                // Caption lasts as long as the speech (plus a short tail), so its word groups follow the narration pace.
                val spoken = narration.firstOrNull { it.sceneId == s.id }?.durationMs
                val capDur = if (spoken != null) us((spoken + 200L).coerceAtMost(s.durationMs)) else dur
                items += TextItem(UUID.randomUUID().toString(), textLane, start, capDur, s.caption, label = s.caption.take(16), origin = "ai",
                    stylePreset = captionStyle, isKaraoke = captionStyle == com.example.studio.render.CaptionStyles.KARAOKE,
                    aiWhy = why(s.id, "caption", "caption"))
            }
            narration.firstOrNull { it.sceneId == s.id }?.let { n ->
                // One sentence can span several scenes: the clip then plays across all of them, uncut.
                val members = n.groupIds.mapNotNull { gid -> manifest.scenes.firstOrNull { it.id == gid } }
                val spanMs = if (n.groupIds.size > 1 && members.size == n.groupIds.size) members.sumOf { it.durationMs } else s.durationMs
                items += VoiceItem(UUID.randomUUID().toString(), voiceLane, start, us(n.durationMs.coerceAtMost(spanMs)), audioUri = n.uri, text = n.text,
                    origin = "ai", aiWhy = why(s.id, "narration", "measured ${n.durationMs} ms" + (if (n.groupIds.size > 1) " grp=${n.groupIds.joinToString("+")}" else "")))
                if (n.durationMs > spanMs + 50) problems += "Scene ${s.id}: narration (${n.durationMs} ms) is longer than the scene (${spanMs} ms)"
            }
            s.cues.forEachIndexed { ci, c ->
                val f = sfx["${s.id}:$ci"]
                if (f == null) {
                    problems += "Scene ${s.id}: ${c.kind} cue '${c.description.take(30)}' has no audio file yet and was not added"
                } else {
                    val cueDur = us(minOf(f.second, c.durationMs.coerceAtLeast(60L), (s.durationMs - c.atMs).coerceAtLeast(60L)))
                    items += AudioItem(UUID.randomUUID().toString(), audLane, start + us(c.atMs), cueDur, f.first, isSfx = true,
                        volume = if (f.first.startsWith("file://") && f.first.contains("/telegram_workspace/")) 0.5f else SceneOverlay.sfxVolume(c.kind), audioDucking = true, duckingAmount = 0.55f,
                        label = "SFX ${c.kind}", origin = "ai", aiWhy = why(s.id, "sfx", "@${c.atMs} ${c.kind}: ${c.description}".take(80)))
                }
            }
        }
        footage.forEachIndexed { i, f ->
            // Source footage is laid out back-to-back in the order of the (reversible) edit plan.
            val start = items.filterIsInstance<VideoItem>().filter { it.aiWhy?.startsWith("director:footage") == true }.sumOf { it.durationUs } + tOffset
            val dur = us(f.sourceOutMs - f.sourceInMs)
            if (dur > 0) items += VideoItem(UUID.randomUUID().toString(), videoLane, start, dur, f.sourceUri, us(f.sourceInMs), us(f.sourceOutMs),
                f.width, f.height, label = "Cut ${i + 1}", origin = "ai", aiWhy = "director:footage:$i:${f.why.take(80)}")
        }
        voiceOverUri?.let { u ->
            val total = items.maxOfOrNull { it.startUs + it.durationUs } ?: 0L
            items += VoiceItem(UUID.randomUUID().toString(), voiceLane, tOffset, manifest.totalMs * 1000L, audioUri = u, text = "Original voice-over",
                origin = "manual", aiWhy = null, label = "Original voice-over")
            if (total < manifest.totalMs * 1000L) problems += "Visuals are shorter than the voice-over"
        }
        // Chapter markers at every beat start (director scenes only; existing markers are kept).
        val beatMarkers = manifest.scenes.map { us(it.startMs) }
        val merged = p.copy(items = p.items + items, markersUs = (p.markersUs + beatMarkers).distinct().sorted(), updatedAt = System.currentTimeMillis())
        return merged to (problems + validate(merged))
    }

    /** Replace only the director's background-music item; unrelated user audio is never removed. */
    fun withBackgroundMusic(base: StudioProject, videoDurationMs: Long, music: BackgroundMusic?): Pair<StudioProject, List<String>> {
        var p = ensureLanes(base.copy(items = base.items.filterNot {
            it.origin == "ai" && it.aiWhy?.startsWith("director:music:") == true
        }))
        if (music == null) return p to emptyList()
        if (music.uri.isBlank() || music.durationMs <= 0L || videoDurationMs <= 0L)
            return p to listOf("Music was not added: missing audio source or duration.")
        // A source is never extended beyond its real duration; this path does not loop music.
        val lengthMs = minOf(music.durationMs, videoDurationMs, Long.MAX_VALUE / 1000L)
        val lenUs = us(lengthMs)
        val item = AudioItem(
            id = UUID.randomUUID().toString(), laneId = laneId(p, TrackType.AUDIO, "lane_audio_1"),
            startUs = 0L, durationUs = lenUs, audioUri = music.uri, isSfx = false,
            volume = GuidedBriefOptions.safeVolume(music.volume), audioDucking = true, duckingAmount = 0.55f,
            fadeInUs = us(minOf(300L, lengthMs / 3)), fadeOutUs = us(minOf(400L, lengthMs / 3)),
            label = "Music: ${music.name.take(32)}", origin = "ai", aiWhy = "director:music:background:user selected"
        )
        p = p.copy(items = p.items + item, updatedAt = System.currentTimeMillis())
        val notes = if (music.durationMs < videoDurationMs)
            listOf("Music ends at ${music.durationMs / 1000}s; the selected file is shorter than the video and is not looped.") else emptyList()
        return p to notes
    }

    /** Detects overlaps on single-track lanes, missing media and zero durations. */
    fun validate(p: StudioProject): List<String> {
        val out = mutableListOf<String>()
        p.items.forEach { i ->
            if (i.durationUs <= 0) out += "${i.label}: zero duration"
            when (i) {
                is VideoItem -> if (i.mediaUri.isBlank()) out += "${i.label}: missing video source"
                is ImageItem -> if (i.mediaUri.isBlank()) out += "${i.label}: missing image source"
                is VoiceItem -> if (i.audioUri.isNullOrBlank()) out += "${i.label}: missing voice audio"
                is AudioItem -> if (i.audioUri.isNullOrBlank()) out += "${i.label}: missing audio source"
                is CodeSceneItem -> if (i.htmlCode.isBlank()) out += "${i.label}: empty scene definition"
                else -> {}
            }
        }
        listOf(TrackType.VOICE, TrackType.CODE, TrackType.TEXT).forEach { t ->
            val sorted = p.items.filter { it.trackType == t }.sortedBy { it.startUs }
            sorted.zipWithNext().forEach { (a, b) -> if (b.startUs < a.startUs + a.durationUs - 1000) out += "Overlap on ${t.displayName}: '${a.label}' and '${b.label}'" }
        }
        return out
    }

    /** Recalculates scene timing after one scene changed and shifts every later director item. */
    fun retimeAfterChange(p: StudioProject, manifest: SceneManifest): StudioProject {
        val byScene = manifest.scenes.associateBy { it.id }
        return p.copy(items = p.items.map { it ->
            val id = it.aiWhy?.takeIf { w -> w.startsWith("director:") }?.split(":")?.getOrNull(1)
            val s = id?.let { byScene[it] }
            if (s == null || it.aiWhy?.startsWith("director:footage") == true) it
            else if (it.aiWhy?.split(":")?.getOrNull(2) == "sfx") {
                // sound cues keep their offset inside the scene
                val off = Regex("@(\\d+)").find(it.aiWhy ?: "")?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                it.copyWithTiming(us(s.startMs + off.coerceAtMost((s.durationMs - 60L).coerceAtLeast(0L))), it.durationUs)
            }
            else {
                val grp = Regex("grp=([A-Za-z0-9+]+)").find(it.aiWhy ?: "")?.groupValues?.get(1)?.split('+')
                val span = if (it.trackType == TrackType.VOICE && grp != null && grp.size > 1) grp.sumOf { gid -> byScene[gid]?.durationMs ?: 0L }.coerceAtLeast(s.durationMs) else s.durationMs
                it.copyWithTiming(us(s.startMs), if (it.trackType == TrackType.VOICE) minOf(it.durationUs, us(span)) else us(s.durationMs))
            }
        })
    }

    /** Maps a source-video time to timeline time through the kept ranges; null when that moment was cut out. */
    fun sourceToTimelineMs(keep: List<FootageRange>, sourceMs: Long): Long? {
        var acc = 0L
        for (k in keep) {
            if (sourceMs >= k.sourceInMs && sourceMs < k.sourceOutMs) return acc + (sourceMs - k.sourceInMs)
            acc += k.sourceOutMs - k.sourceInMs
        }
        return null
    }

    /** Adds timed narration/overlay items on top of an existing project (footage workflows). */
    fun addVoiceAt(p: StudioProject, startMs: Long, uri: String, durMs: Long, text: String): StudioProject {
        val lane = laneId(p, TrackType.VOICE, "lane_voice_1")
        val item = VoiceItem(UUID.randomUUID().toString(), lane, us(startMs), us(durMs), audioUri = uri, text = text, origin = "ai", aiWhy = "director:narration:${startMs}:footage narration")
        return p.copy(items = p.items + item)
    }

    fun addTextAt(p: StudioProject, startMs: Long, durMs: Long, text: String, role: String): StudioProject {
        val lane = laneId(p, TrackType.TEXT, "lane_text_1")
        val item = TextItem(UUID.randomUUID().toString(), lane, us(startMs), us(durMs), text, label = text.take(16), origin = "ai", aiWhy = "director:$role:$startMs:$role")
        return p.copy(items = p.items + item)
    }

    fun setFootageVolume(p: StudioProject, volume: Float): StudioProject =
        p.copy(items = p.items.map { if (it is VideoItem && it.aiWhy?.startsWith("director:footage") == true) it.copy(volume = volume) else it })
}
