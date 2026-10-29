package com.example.studio.model

import com.example.ui.components.VideoProjectData
import java.util.UUID

/**
 * Clean migrator that converts legacy [VideoProjectData] to [StudioProject].
 * Guarantees zero demo or template content is injected.
 */
object StudioProjectMigrator {

    fun migrate(legacy: VideoProjectData): StudioProject {
        val lanes = StudioProject.defaultEmptyLanes()
        val videoLaneId = lanes.first { it.trackType == TrackType.VIDEO }.id
        val textLaneId = lanes.first { it.trackType == TrackType.TEXT }.id
        val visualLaneId = lanes.first { it.trackType == TrackType.VISUAL }.id
        val voiceLaneId = lanes.first { it.trackType == TrackType.VOICE }.id
        val audioLaneId = lanes.first { it.trackType == TrackType.AUDIO }.id
        val codeLaneId = lanes.first { it.trackType == TrackType.CODE }.id

        val items = mutableListOf<TimelineItem>()

        // 1. Migrate real video clips
        var cursorUs = 0L
        legacy.clips.forEachIndexed { idx, clip ->
            val durUs = (clip.durationSeconds * 1_000_000L).toLong().coerceAtLeast(100_000L)
            if (clip.isPhoto) {
                items.add(
                    ImageItem(
                        id = "migrated_img_${idx}_${UUID.randomUUID().toString().take(6)}",
                        laneId = visualLaneId,
                        startUs = cursorUs,
                        durationUs = durUs,
                        mediaUri = clip.uri?.toString() ?: "",
                        label = clip.name.ifBlank { "Photo ${idx + 1}" }
                    )
                )
            } else {
                items.add(
                    VideoItem(
                        id = "migrated_vid_${idx}_${UUID.randomUUID().toString().take(6)}",
                        laneId = videoLaneId,
                        startUs = cursorUs,
                        durationUs = durUs,
                        mediaUri = clip.uri?.toString() ?: "",
                        label = clip.name.ifBlank { "Clip ${idx + 1}" },
                        playbackSpeed = clip.speed,
                        volume = clip.volume
                    )
                )
            }
            cursorUs += durUs
        }

        // 2. Migrate real captions if present
        legacy.autoCaptions.forEachIndexed { idx, cap ->
            val startUs = (cap.startSeconds * 1_000_000L).toLong()
            val durUs = ((cap.endSeconds - cap.startSeconds) * 1_000_000L).toLong().coerceAtLeast(500_000L)
            items.add(
                TextItem(
                    id = "migrated_cap_${idx}_${UUID.randomUUID().toString().take(6)}",
                    laneId = textLaneId,
                    startUs = startUs,
                    durationUs = durUs,
                    text = cap.text,
                    label = cap.text.take(16)
                )
            )
        }

        // 3. Migrate real code tracks (only if non-empty in original legacy project)
        legacy.codeTracks.forEachIndexed { idx, ct ->
            items.add(
                CodeSceneItem(
                    id = "migrated_code_${idx}_${UUID.randomUUID().toString().take(6)}",
                    laneId = codeLaneId,
                    startUs = (ct.startSeconds * 1_000_000L).toLong(),
                    durationUs = (ct.durationSeconds * 1_000_000L).toLong(),
                    title = ct.title,
                    animationType = ct.animationType
                )
            )
        }

        // 4. Migrate real visual asset tracks
        legacy.visualAssetTracks.forEachIndexed { idx, va ->
            items.add(
                ImageItem(
                    id = "migrated_va_${idx}_${UUID.randomUUID().toString().take(6)}",
                    laneId = visualLaneId,
                    startUs = (va.startSeconds * 1_000_000L).toLong(),
                    durationUs = (va.durationSeconds * 1_000_000L).toLong(),
                    mediaUri = va.imageUri ?: "",
                    label = va.name
                )
            )
        }

        // 5. Migrate real voiceover tracks
        legacy.voiceoverTracks.forEachIndexed { idx, vo ->
            items.add(
                VoiceItem(
                    id = "migrated_vo_${idx}_${UUID.randomUUID().toString().take(6)}",
                    laneId = voiceLaneId,
                    startUs = (vo.startSeconds * 1_000_000L).toLong(),
                    durationUs = (vo.durationSeconds * 1_000_000L).toLong(),
                    audioUri = vo.audioUri,
                    text = vo.narration,
                    speakerTone = vo.speakerTone
                )
            )
        }

        // 6. Migrate real sound audio tracks
        legacy.soundAudioTracks.forEachIndexed { idx, sat ->
            items.add(
                AudioItem(
                    id = "migrated_sat_${idx}_${UUID.randomUUID().toString().take(6)}",
                    laneId = audioLaneId,
                    startUs = (sat.startSeconds * 1_000_000L).toLong(),
                    durationUs = (sat.durationSeconds * 1_000_000L).toLong(),
                    audioUri = sat.audioUri,
                    isSfx = sat.isSfx,
                    volume = sat.volume,
                    label = sat.title
                )
            )
        }

        return StudioProject(
            id = legacy.projectId.ifBlank { ProjectIdentity.newId() },
            name = legacy.projectName.ifBlank { "Untitled" },
            aspect = legacy.aspectRatio.ifBlank { "9:16" },
            lanes = lanes,
            items = items,
            markersUs = legacy.timelineMarkers.map { (it.timestampSeconds * 1_000_000L).toLong() }
        )
    }
}
