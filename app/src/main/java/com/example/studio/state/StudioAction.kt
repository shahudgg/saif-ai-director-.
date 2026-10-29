package com.example.studio.state

import com.example.studio.model.AudioItem
import com.example.studio.model.ItemEffects
import com.example.studio.model.ItemTransform
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.TimelineItem
import com.example.studio.model.TrackType
import com.example.studio.model.TransformKeyframe
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import java.util.UUID

/**
 * Pure, reversible action for 100-step undo/redo timeline manipulation.
 */
sealed interface StudioAction {

    fun apply(project: StudioProject): StudioProject

    data class AddItem(val item: TimelineItem) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            project.copy(items = project.items + item, updatedAt = System.currentTimeMillis())
    }

    data class RemoveItem(val itemId: String) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            project.copy(items = project.items.filterNot { it.id == itemId }, updatedAt = System.currentTimeMillis())
    }

    data class RippleDelete(val itemId: String) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            val target = project.items.firstOrNull { it.id == itemId } ?: return project
            val targetLaneId = target.laneId
            val freedDurationUs = target.durationUs
            val targetEndUs = target.startUs + target.durationUs

            val updatedItems = project.items.filterNot { it.id == itemId }.map { item ->
                if (item.laneId == targetLaneId && item.startUs >= targetEndUs) {
                    item.copyWithTiming((item.startUs - freedDurationUs).coerceAtLeast(0L), item.durationUs)
                } else item
            }
            return project.copy(items = updatedItems, updatedAt = System.currentTimeMillis())
        }
    }

    data class DuplicateItem(val itemId: String) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            val target = project.items.firstOrNull { it.id == itemId } ?: return project
            val newId = UUID.randomUUID().toString()
            val newStartUs = target.startUs + target.durationUs
            val duplicated = when (target) {
                is VideoItem -> target.copy(id = newId, startUs = newStartUs, label = "${target.label} (Copy)")
                is com.example.studio.model.ImageItem -> target.copy(id = newId, startUs = newStartUs, label = "${target.label} (Copy)")
                is TextItem -> target.copy(id = newId, startUs = newStartUs, label = "${target.label} (Copy)")
                is VoiceItem -> target.copy(id = newId, startUs = newStartUs, label = "${target.label} (Copy)")
                is AudioItem -> target.copy(id = newId, startUs = newStartUs, label = "${target.label} (Copy)")
                is com.example.studio.model.CodeSceneItem -> target.copy(id = newId, startUs = newStartUs, label = "${target.label} (Copy)")
            }
            return project.copy(items = project.items + duplicated, updatedAt = System.currentTimeMillis())
        }
    }

    data class MoveItem(
        val itemId: String,
        val newStartUs: Long,
        val newLaneId: String? = null
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item.id == itemId) {
                        val timed = item.copyWithTiming(newStartUs.coerceAtLeast(0L), item.durationUs)
                        if (newLaneId != null && newLaneId.isNotBlank()) {
                            timed.copyWithLane(newLaneId)
                        } else timed
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class TrimItem(
        val itemId: String,
        val newStartUs: Long,
        val newDurationUs: Long
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item.id == itemId) {
                        item.copyWithTiming(newStartUs.coerceAtLeast(0L), newDurationUs.coerceAtLeast(100_000L))
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class TrimLeftToPlayhead(val itemId: String, val playheadUs: Long) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            val target = project.items.firstOrNull { it.id == itemId } ?: return project
            val targetEndUs = target.startUs + target.durationUs
            if (playheadUs <= target.startUs || playheadUs >= targetEndUs) return project
            val newDurationUs = targetEndUs - playheadUs
            return project.copy(
                items = project.items.map {
                    if (it.id == itemId) it.copyWithTiming(playheadUs, newDurationUs) else it
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class TrimRightToPlayhead(val itemId: String, val playheadUs: Long) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            val target = project.items.firstOrNull { it.id == itemId } ?: return project
            val targetEndUs = target.startUs + target.durationUs
            if (playheadUs <= target.startUs || playheadUs >= targetEndUs) return project
            val newDurationUs = playheadUs - target.startUs
            return project.copy(
                items = project.items.map {
                    if (it.id == itemId) it.copyWithTiming(target.startUs, newDurationUs) else it
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class SplitItem(
        val itemId: String,
        val splitPointUs: Long
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            val target = project.items.firstOrNull { it.id == itemId } ?: return project
            if (splitPointUs <= target.startUs || splitPointUs >= target.startUs + target.durationUs) return project

            val firstPartDuration = splitPointUs - target.startUs
            val secondPartDuration = target.durationUs - firstPartDuration

            val firstItem = target.copyWithTiming(target.startUs, firstPartDuration)
            val secondItem = target.copyWithTiming(splitPointUs, secondPartDuration)
            val secondItemWithNewId = when (secondItem) {
                is VideoItem -> secondItem.copy(id = UUID.randomUUID().toString())
                is com.example.studio.model.ImageItem -> secondItem.copy(id = UUID.randomUUID().toString())
                is TextItem -> secondItem.copy(id = UUID.randomUUID().toString())
                is VoiceItem -> secondItem.copy(id = UUID.randomUUID().toString())
                is AudioItem -> secondItem.copy(id = UUID.randomUUID().toString())
                is com.example.studio.model.CodeSceneItem -> secondItem.copy(id = UUID.randomUUID().toString())
            }

            return project.copy(
                items = project.items.filterNot { it.id == itemId } + listOf(firstItem, secondItemWithNewId),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class BringForward(val itemId: String) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            val idx = project.items.indexOfFirst { it.id == itemId }
            if (idx < 0 || idx == project.items.lastIndex) return project
            val mutable = project.items.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(idx + 1, item)
            return project.copy(items = mutable, updatedAt = System.currentTimeMillis())
        }
    }

    data class SendBackward(val itemId: String) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            val idx = project.items.indexOfFirst { it.id == itemId }
            if (idx <= 0) return project
            val mutable = project.items.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(idx - 1, item)
            return project.copy(items = mutable, updatedAt = System.currentTimeMillis())
        }
    }

    data class UpdateItemTransform(val itemId: String, val transform: ItemTransform) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            project.copy(
                items = project.items.map { if (it.id == itemId) it.copyWithTransform(transform) else it },
                updatedAt = System.currentTimeMillis()
            )
    }

    data class UpdateItemEffects(val itemId: String, val effects: ItemEffects) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            project.copy(
                items = project.items.map { if (it.id == itemId) it.copyWithEffects(effects) else it },
                updatedAt = System.currentTimeMillis()
            )
    }

    data class ToggleKeyframeAtTime(
        val itemId: String,
        val timeUs: Long,
        val currentTransform: ItemTransform
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item.id == itemId) {
                        val existingIndex = item.keyframes.indexOfFirst { kotlin.math.abs(it.timeUs - timeUs) < 100_000L }
                        val updatedKeyframes = if (existingIndex >= 0) {
                            item.keyframes.filterIndexed { index, _ -> index != existingIndex }
                        } else {
                            (item.keyframes + TransformKeyframe(
                                timeUs = timeUs,
                                x = currentTransform.x,
                                y = currentTransform.y,
                                scale = currentTransform.scale,
                                rotation = currentTransform.rotation,
                                opacity = currentTransform.opacity
                            )).sortedBy { it.timeUs }
                        }
                        item.copyWithKeyframes(updatedKeyframes)
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class UpdateSpeedRamp(
        val itemId: String,
        val speedRampCurve: String,
        val playbackSpeed: Float = 1.0f
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item.id == itemId && item is VideoItem) {
                        item.copy(speedRampCurve = speedRampCurve, playbackSpeed = playbackSpeed)
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class UpdateTransition(
        val itemId: String,
        val transitionIn: String,
        val transitionDurationUs: Long = 300_000L
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item.id == itemId) {
                        when (item) {
                            is VideoItem -> item.copy(transitionIn = transitionIn, transitionDurationUs = transitionDurationUs)
                            is com.example.studio.model.ImageItem -> item.copy(transitionIn = transitionIn, transitionDurationUs = transitionDurationUs)
                            else -> item
                        }
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class UpdateTextItem(
        val itemId: String,
        val text: String,
        val fontSizeSp: Float,
        val textColorHex: Long,
        val outlineColorHex: Long,
        val outlineWidth: Float,
        val stylePreset: String
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item.id == itemId && item is TextItem) {
                        item.copy(
                            text = text,
                            fontSizeSp = fontSizeSp,
                            textColorHex = textColorHex,
                            outlineColorHex = outlineColorHex,
                            outlineWidth = outlineWidth,
                            stylePreset = stylePreset,
                            label = text.take(16)
                        )
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class ApplyStyleToAllCaptions(
        val stylePreset: String,
        val fontSizeSp: Float,
        val textColorHex: Long,
        val outlineColorHex: Long,
        val outlineWidth: Float
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item is TextItem) {
                        item.copy(
                            stylePreset = stylePreset,
                            fontSizeSp = fontSizeSp,
                            textColorHex = textColorHex,
                            outlineColorHex = outlineColorHex,
                            outlineWidth = outlineWidth
                        )
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class UpdateAudioItem(
        val itemId: String,
        val volume: Float,
        val speed: Float,
        val fadeInUs: Long,
        val fadeOutUs: Long,
        val audioDucking: Boolean
    ) : StudioAction {
        override fun apply(project: StudioProject): StudioProject {
            return project.copy(
                items = project.items.map { item ->
                    if (item.id == itemId) {
                        when (item) {
                            is AudioItem -> item.copy(volume = volume, speed = speed, fadeInUs = fadeInUs, fadeOutUs = fadeOutUs, audioDucking = audioDucking)
                            is VoiceItem -> item.copy(volume = volume, speed = speed, audioDucking = audioDucking)
                            is VideoItem -> item.copy(volume = volume, playbackSpeed = speed)
                            else -> item
                        }
                    } else item
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    data class SetAspect(val newAspect: String) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            project.copy(aspect = newAspect, updatedAt = System.currentTimeMillis())
    }

    data class RenameProject(val newName: String) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            project.copy(name = newName, updatedAt = System.currentTimeMillis())
    }

    data class AddMarker(val markerUs: Long) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            project.copy(markersUs = (project.markersUs + markerUs).sorted(), updatedAt = System.currentTimeMillis())
    }

    data class ReplaceProject(val newProject: StudioProject) : StudioAction {
        override fun apply(project: StudioProject): StudioProject =
            newProject.copy(updatedAt = System.currentTimeMillis())
    }
}
