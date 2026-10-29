package com.example.studio.director

import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.ItemAnimation
import com.example.studio.model.StudioLane
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.TimelineItem
import com.example.studio.model.TrackType
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
                         val atMs: Long = 0L, val durMs: Long = 0L)
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
        captionStyle: String = com.example.studio.render.CaptionStyles.HORMOZI
    ): Pair<StudioProject, List<String>> {
        val problems = mutableListOf<String>()
        var p = ensureLanes(if (replaceExisting) clearAi(base) else base)
        if (aspect != null) p = p.copy(aspect = aspect)
        p = p.copy(fps = manifest.fps)
        val items = mutableListOf<TimelineItem>()
        val codeLane = laneId(p, TrackType.CODE, "lane_code_1"); val videoLane = laneId(p, TrackType.VIDEO, "lane_video_1")
        val visLane = laneId(p, TrackType.VISUAL, "lane_visual_1"); val textLane = laneId(p, TrackType.TEXT, "lane_text_1")
        val voiceLane = laneId(p, TrackType.VOICE, "lane_voice_1"); val audLane = laneId(p, TrackType.AUDIO, "lane_audio_1")
        val tOffset = if (replaceExisting) 0L else p.totalDurationUs

        manifest.scenes.sortedBy { it.startMs }.forEach { s ->
            val start = us(s.startMs) + tOffset; val dur = us(s.durationMs)
            if (dur <= 0) { problems += "Scene ${s.id}: invalid duration"; return@forEach }
            val tr = if (s.transitionIn == "none") null else ItemAnimation(s.transitionIn, us(s.transitionMs.coerceAtMost(s.durationMs / 2)))

            // A usable picture/clip behind the scene: the scene becomes an overlay (transparent backdrop) drawn above the media.
            val hasBackdrop = assets.any { it.sceneId == s.id && (it.kind == "image" || it.kind == "video" || it.kind == "overlay") && it.localPath.isNotBlank() && (it.verified || it.note.isNotBlank()) }
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
                    "image", "overlay" -> items += ImageItem(UUID.randomUUID().toString(), visLane, start, dur, uri, label = "Asset ${a.requestId}",
                        animIn = tr, origin = "ai", aiWhy = why(s.id, "asset", "${a.source}; ${a.note}".take(100)))
                    "video" -> items += VideoItem(UUID.randomUUID().toString(), videoLane, start, dur, uri, sourceOutUs = dur, label = "B-roll ${a.requestId}",
                        volume = 0f, animIn = tr, origin = "ai", aiWhy = why(s.id, "asset", "${a.source}; ${a.note}".take(100)))
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
