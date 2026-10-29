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

            val layers = visible.map { item ->
                val localUs = tUs - item.startUs
                val progress = if (item.durationUs > 0) (localUs.toFloat() / item.durationUs).coerceIn(0f, 1f) else 0f

                // Resolve keyframe interpolated transform
                val keyframeTransform = LayerMath.interpolateTransform(item.transform, item.keyframes, tUs)

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
                    zIndex = zIndex
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
