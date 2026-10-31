package com.example.studio.render

import com.example.studio.model.ItemTransform
import com.example.studio.model.StudioProject
import com.example.studio.model.TimelineItem
import com.example.studio.model.TrackType

data class RenderLayer(
    val item: TimelineItem,
    val localTimeUs: Long,
    val progress: Float, // 0f to 1f within the item's duration
    val opacity: Float,
    val resolvedTransform: ItemTransform,
    val zIndex: Int
)

data class RenderPlan(
    val timeUs: Long,
    val visibleLayers: List<RenderLayer>,
    val activeVideoItem: TimelineItem?,
    val activeAudioItems: List<TimelineItem>
) {
    companion object {
        /** True when a media layer carries a placement tag that puts it above the generated graphics (a sticker/PNG). */
        private fun isSticker(aiWhy: String?): Boolean {
            val p = com.example.studio.director.AssetPlacements.of(aiWhy) ?: return false
            return !p.isBackdrop
        }

        /**
         * Pure deterministic layer plan resolver at timestamp [tUs].
         * Single source of truth consumed by BOTH preview and exporter.
         */
        fun at(project: StudioProject, tUs: Long): RenderPlan {
            val visible = project.items.filter { item ->
                tUs in item.startUs until (item.startUs + item.durationUs)
            }

            // The director lays scenes end-to-end. During a geometric entrance, retain the
            // immediately preceding code scene's last frame underneath the new scene instead of revealing black.
            val frozenTimes = mutableMapOf<String, Long>()
            val frozenZ = mutableMapOf<String, Int>()
            val frozen = visible.mapNotNull { item ->
                val incoming = item as? com.example.studio.model.CodeSceneItem ?: return@mapNotNull null
                val animation = incoming.animIn ?: return@mapNotNull null
                if (!TransitionFx.isGeometric(animation.type) || animation.durationUs <= 0 ||
                    tUs - incoming.startUs !in 0 until animation.durationUs) return@mapNotNull null
                val prev = project.items.asSequence().filterIsInstance<com.example.studio.model.CodeSceneItem>()
                    .filter { it.laneId == incoming.laneId && it.startUs + it.durationUs == incoming.startUs && it.durationUs > 0L }
                    .maxByOrNull { it.startUs } ?: return@mapNotNull null
                if (visible.any { it.id == prev.id }) return@mapNotNull null
                frozenTimes[prev.id] = prev.startUs + prev.durationUs - 1L
                frozenZ[prev.id] = if (incoming.animationType == com.example.studio.director.SCENE_OVERLAY_MARKER) 34 else 9
                prev
            }.distinctBy { it.id }
            val layers = (frozen + visible).map { item ->
                val frameTime = frozenTimes[item.id] ?: tUs
                val localUs = frameTime - item.startUs
                val progress = if (item.durationUs > 0) (localUs.toFloat() / item.durationUs).coerceIn(0f, 1f) else 0f

                // Resolve keyframe interpolated transform
                val keyframeTransform = LayerMath.interpolateTransform(item.transform, item.keyframes, frameTime)

                // Resolve animation in / out opacity
                var opacity = keyframeTransform.opacity
                item.animIn?.let { anim ->
                    // Geometric scene transitions (slide/whip/zoom/iris...) keep the scene opaque; the renderer moves it instead.
                    val geometric = item is com.example.studio.model.CodeSceneItem && TransitionFx.isGeometric(anim.type)
                    if (!geometric && localUs < anim.durationUs && anim.durationUs > 0) {
                        val animProgress = (localUs.toFloat() / anim.durationUs).coerceIn(0f, 1f)
                        val eased = LayerMath.evaluateEasing(animProgress, anim.easing)
                        opacity *= eased
                    }
                }
                item.animOut?.let { anim ->
                    val remainingUs = item.durationUs - localUs
                    if (remainingUs < anim.durationUs && anim.durationUs > 0) {
                        val animProgress = (remainingUs.toFloat() / anim.durationUs).coerceIn(0f, 1f)
                        val eased = LayerMath.evaluateEasing(animProgress, anim.easing)
                        opacity *= eased
                    }
                }

                val zIndex = when (item.trackType) {
                    // Scenes built over a library picture/clip are drawn above the media so both are visible together.
                    TrackType.CODE -> if (item is com.example.studio.model.CodeSceneItem && item.animationType == com.example.studio.director.SCENE_OVERLAY_MARKER) 35 else 10
                    // A sticker/PNG the director placed as a small element sits ABOVE the graphics; a backdrop stays below.
                    TrackType.VISUAL -> if (isSticker(item.aiWhy)) 38 else 30
                    TrackType.VIDEO -> if (isSticker(item.aiWhy)) 37 else 20
                    TrackType.TEXT -> 40
                    TrackType.VOICE -> 50
                    TrackType.AUDIO -> 50
                }

                RenderLayer(
                    item = item,
                    localTimeUs = localUs,
                    progress = progress,
                    opacity = opacity.coerceIn(0f, 1f),
                    resolvedTransform = keyframeTransform.copy(opacity = opacity.coerceIn(0f, 1f)),
                    zIndex = frozenZ[item.id] ?: zIndex
                )
            }.sortedBy { it.zIndex }

            val activeVideo = visible.firstOrNull { it.trackType == TrackType.VIDEO }
            val activeAudio = visible.filter { it.trackType == TrackType.AUDIO || it.trackType == TrackType.VOICE }

            return RenderPlan(
                timeUs = tUs,
                visibleLayers = layers,
                activeVideoItem = activeVideo,
                activeAudioItems = activeAudio
            )
        }
    }
}
