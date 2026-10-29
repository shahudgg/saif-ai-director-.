package com.example.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class VideoStudioSubView {
    NONE,
    AI_DIRECTOR_CHAT,
    EXPORT_STUDIO,
    AI_VIDEO_GENERATOR
}

/**
 * Full Multi-Track Video Timeline Studio Screen.
 * Fully interactive with:
 * 1. Top Workspace Header (Back, Title, Resolution & FPS dialog, Undo, Redo, Export)
 * 2. Preview Viewport Area (Aspect ratio, filters, overlays, timecode, fullscreen toggle)
 * 3. Transport Controls Row (Replay -5s, Play/Pause live ticker, Forward +5s, Split clip)
 * 4. Signature SAIF AI EDITING Pill (56px) with Chat Director button
 * 5. Interactive Multi-Track Timeline (Video with add +, Audio, Text, Effects tracks)
 * 6. Bottom Workspace Toolbar with rich sheets for all 9 toolkits:
 *    - Edit, Audio, Text, Overlay, Effects (25+), Filters (10), Adjust, Ratio, Pro Tools (22)
 * 7. Navigation into AI Chat Director and Export Studio
 */
@Composable
fun VideoTimelineStudioScreen(
    initialProject: VideoProjectData,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentProject by remember { mutableStateOf(initialProject) }
    var activeSubView by remember { mutableStateOf(VideoStudioSubView.NONE) }
    var activeToolTab by remember { mutableStateOf(VideoStudioToolTab.NONE) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSaifAiEditingModal by remember { mutableStateOf(false) }

    // Undo & Redo History Stacks
    var undoStack by remember { mutableStateOf(listOf<VideoProjectData>()) }
    var redoStack by remember { mutableStateOf(listOf<VideoProjectData>()) }

    // Timecode helper
    fun formatSeconds(sec: Float): String {
        val totalSecs = sec.toInt()
        val minutes = totalSecs / 60
        val remainingSecs = totalSecs % 60
        val millis = ((sec - totalSecs) * 100).toInt()
        return "%02d:%02d:%02d".format(minutes, remainingSecs, millis)
    }

    fun formatExactTimestamp(sec: Float): String {
        val totalSecs = sec.toInt()
        val minutes = totalSecs / 60
        val remainingSecs = totalSecs % 60
        val tenths = ((sec - totalSecs) * 10).toInt().coerceIn(0, 9)
        return "%02d:%02d.%01d".format(minutes, remainingSecs, tenths)
    }

    var isSnapEnabled by remember { mutableStateOf(true) }
    var timelineZoom by remember { mutableStateOf(1.0f) }
    var selectedTrackNum by remember { mutableStateOf(1) }
    var selectedCardId by remember { mutableStateOf("") }

    fun calculateSnappedPosition(pos: Float, snapThreshold: Float = 0.25f): Float {
        if (!isSnapEnabled) return pos
        val points = mutableListOf(0f, currentProject.totalDurationSeconds)
        var accum = 0f
        currentProject.clips.forEach { clip ->
            points.add(accum)
            accum += clip.durationSeconds
            points.add(accum)
        }
        currentProject.getResolvedCodeTracks().forEach {
            points.add(it.startSeconds)
            points.add(it.startSeconds + it.durationSeconds)
        }
        currentProject.getResolvedVisualAssets().forEach {
            points.add(it.startSeconds)
            points.add(it.startSeconds + it.durationSeconds)
        }
        for (pt in points) {
            if (kotlin.math.abs(pos - pt) <= snapThreshold) {
                return pt
            }
        }
        return pos
    }

    fun pushState(newProject: VideoProjectData) {
        undoStack = undoStack + currentProject
        redoStack = emptyList()
        currentProject = newProject
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + currentProject
            currentProject = prev
            Toast.makeText(context, "Undo", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + currentProject
            currentProject = next
            Toast.makeText(context, "Redo", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }

    fun applySmartSplit() {
        val playhead = currentProject.currentPositionSeconds
        when (selectedTrackNum) {
            1 -> {
                val list = currentProject.getResolvedCodeTracks().toMutableList()
                val idx = list.indexOfFirst { playhead in (it.startSeconds + 0.15f)..(it.startSeconds + it.durationSeconds - 0.15f) }
                if (idx != -1) {
                    val item = list[idx]
                    val dur1 = playhead - item.startSeconds
                    val dur2 = item.durationSeconds - dur1
                    val item1 = item.copy(id = UUID.randomUUID().toString(), durationSeconds = dur1)
                    val item2 = item.copy(id = UUID.randomUUID().toString(), title = "${item.title} (Part 2)", startSeconds = playhead, durationSeconds = dur2)
                    list[idx] = item1
                    list.add(idx + 1, item2)
                    pushState(currentProject.copy(codeTracks = list))
                    Toast.makeText(context, "✂️ Track 1 Code card split at ${formatSeconds(playhead)}!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Position playhead inside a Code card to split", Toast.LENGTH_SHORT).show()
                }
            }
            3 -> {
                val list = currentProject.getResolvedVisualAssets().toMutableList()
                val idx = list.indexOfFirst { playhead in (it.startSeconds + 0.15f)..(it.startSeconds + it.durationSeconds - 0.15f) }
                if (idx != -1) {
                    val item = list[idx]
                    val dur1 = playhead - item.startSeconds
                    val dur2 = item.durationSeconds - dur1
                    val item1 = item.copy(id = UUID.randomUUID().toString(), durationSeconds = dur1)
                    val item2 = item.copy(id = UUID.randomUUID().toString(), name = "${item.name} (Part 2)", startSeconds = playhead, durationSeconds = dur2)
                    list[idx] = item1
                    list.add(idx + 1, item2)
                    pushState(currentProject.copy(visualAssetTracks = list))
                    Toast.makeText(context, "✂️ Track 3 Asset card split!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Position playhead inside an Asset card", Toast.LENGTH_SHORT).show()
                }
            }
            4 -> {
                val list = currentProject.getResolvedTextSubtitles().toMutableList()
                val idx = list.indexOfFirst { playhead in (it.startSeconds + 0.15f)..(it.startSeconds + it.durationSeconds - 0.15f) }
                if (idx != -1) {
                    val item = list[idx]
                    val dur1 = playhead - item.startSeconds
                    val dur2 = item.durationSeconds - dur1
                    val item1 = item.copy(id = UUID.randomUUID().toString(), durationSeconds = dur1)
                    val item2 = item.copy(id = UUID.randomUUID().toString(), startSeconds = playhead, durationSeconds = dur2)
                    list[idx] = item1
                    list.add(idx + 1, item2)
                    pushState(currentProject.copy(textSubtitleTracks = list))
                    Toast.makeText(context, "✂️ Track 4 Subtitle card split!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Position playhead inside a Subtitle card", Toast.LENGTH_SHORT).show()
                }
            }
            5 -> {
                val list = currentProject.getResolvedVoiceoverTracks().toMutableList()
                val idx = list.indexOfFirst { playhead in (it.startSeconds + 0.15f)..(it.startSeconds + it.durationSeconds - 0.15f) }
                if (idx != -1) {
                    val item = list[idx]
                    val dur1 = playhead - item.startSeconds
                    val dur2 = item.durationSeconds - dur1
                    val item1 = item.copy(id = UUID.randomUUID().toString(), durationSeconds = dur1)
                    val item2 = item.copy(id = UUID.randomUUID().toString(), startSeconds = playhead, durationSeconds = dur2)
                    list[idx] = item1
                    list.add(idx + 1, item2)
                    pushState(currentProject.copy(voiceoverTracks = list))
                    Toast.makeText(context, "✂️ Track 5 Voiceover split!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Position playhead inside a Voiceover card", Toast.LENGTH_SHORT).show()
                }
            }
            6 -> {
                val list = currentProject.getResolvedSoundAudioTracks().toMutableList()
                val idx = list.indexOfFirst { playhead in (it.startSeconds + 0.15f)..(it.startSeconds + it.durationSeconds - 0.15f) }
                if (idx != -1) {
                    val item = list[idx]
                    val dur1 = playhead - item.startSeconds
                    val dur2 = item.durationSeconds - dur1
                    val item1 = item.copy(id = UUID.randomUUID().toString(), durationSeconds = dur1)
                    val item2 = item.copy(id = UUID.randomUUID().toString(), title = "${item.title} (Part 2)", startSeconds = playhead, durationSeconds = dur2)
                    list[idx] = item1
                    list.add(idx + 1, item2)
                    pushState(currentProject.copy(soundAudioTracks = list))
                    Toast.makeText(context, "✂️ Track 6 Sound card split!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Position playhead inside a Sound card", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // TRACK 2: VIDEO CLIPS
                if (currentProject.clips.isEmpty()) {
                    Toast.makeText(context, "No clips to split", Toast.LENGTH_SHORT).show()
                    return
                }

                var foundIdx = -1
                var accum = 0f
                var offsetAtPlayhead = 0f
                for (i in currentProject.clips.indices) {
                    val clip = currentProject.clips[i]
                    val clipStart = accum
                    val clipEnd = accum + clip.durationSeconds
                    if (playhead >= clipStart && playhead <= clipEnd + 0.05f) {
                        foundIdx = i
                        offsetAtPlayhead = (playhead - clipStart).coerceIn(0f, clip.durationSeconds)
                        break
                    }
                    accum = clipEnd
                }

                val selectedIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
                val targetIdx = if (foundIdx != -1) foundIdx else selectedIdx
                val target = currentProject.clips[targetIdx]

                if (target.durationSeconds < 0.3f) {
                    Toast.makeText(context, "Clip too short to split (<0.3s)", Toast.LENGTH_SHORT).show()
                    return
                }

                val splitOffset = if (foundIdx == targetIdx && offsetAtPlayhead in 0.15f..(target.durationSeconds - 0.15f)) {
                    offsetAtPlayhead
                } else {
                    target.durationSeconds / 2f
                }

                val durA = splitOffset
                val durB = target.durationSeconds - splitOffset
                val baseName = target.name.replace(Regex("_part\\d+|_cut\\d+"), "")

                val partA = target.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${baseName}_part1",
                    durationSeconds = durA,
                    inSeconds = target.inSeconds,
                    outSeconds = target.inSeconds + durA,
                    origin = "manual",
                    lastModified = System.currentTimeMillis()
                )
                val partB = target.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${baseName}_part2",
                    durationSeconds = durB,
                    inSeconds = target.inSeconds + durA,
                    outSeconds = target.outSeconds,
                    origin = "manual",
                    lastModified = System.currentTimeMillis()
                )

                val newClips = currentProject.clips.toMutableList()
                newClips.removeAt(targetIdx)
                newClips.add(targetIdx, partA)
                newClips.add(targetIdx + 1, partB)

                val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()

                pushState(
                    currentProject.copy(
                        clips = newClips,
                        selectedClipIndex = targetIdx + 1,
                        totalDurationSeconds = newTotal
                    )
                )
                Toast.makeText(context, "✂️ Split into 2 parts at ${formatSeconds(playhead)}!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun applyCutDelete() {
        when (selectedTrackNum) {
            1 -> {
                val list = currentProject.getResolvedCodeTracks().toMutableList()
                if (list.size <= 1) {
                    Toast.makeText(context, "Cannot delete only remaining code block", Toast.LENGTH_SHORT).show()
                    return
                }
                val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                list.removeAt(idx)
                pushState(currentProject.copy(codeTracks = list))
                Toast.makeText(context, "🗑️ Deleted Code Card", Toast.LENGTH_SHORT).show()
            }
            3 -> {
                val list = currentProject.getResolvedVisualAssets().toMutableList()
                if (list.isNotEmpty()) {
                    val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                    list.removeAt(idx)
                    pushState(currentProject.copy(visualAssetTracks = list))
                    Toast.makeText(context, "🗑️ Deleted Asset Card", Toast.LENGTH_SHORT).show()
                }
            }
            4 -> {
                val list = currentProject.getResolvedTextSubtitles().toMutableList()
                if (list.isNotEmpty()) {
                    val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                    list.removeAt(idx)
                    pushState(currentProject.copy(textSubtitleTracks = list))
                    Toast.makeText(context, "🗑️ Deleted Subtitle Card", Toast.LENGTH_SHORT).show()
                }
            }
            5 -> {
                val list = currentProject.getResolvedVoiceoverTracks().toMutableList()
                if (list.isNotEmpty()) {
                    val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                    list.removeAt(idx)
                    pushState(currentProject.copy(voiceoverTracks = list))
                    Toast.makeText(context, "🗑️ Deleted Voiceover Card", Toast.LENGTH_SHORT).show()
                }
            }
            6 -> {
                val list = currentProject.getResolvedSoundAudioTracks().toMutableList()
                if (list.isNotEmpty()) {
                    val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                    list.removeAt(idx)
                    pushState(currentProject.copy(soundAudioTracks = list))
                    Toast.makeText(context, "🗑️ Deleted Sound Card", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // TRACK 2: VIDEO CLIPS
                if (currentProject.clips.isEmpty()) {
                    Toast.makeText(context, "No clips to cut", Toast.LENGTH_SHORT).show()
                    return
                }
                if (currentProject.clips.size <= 1) {
                    Toast.makeText(context, "Cannot delete only remaining clip", Toast.LENGTH_SHORT).show()
                    return
                }
                val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
                val target = currentProject.clips[targetIdx]
                val newClips = currentProject.clips.toMutableList()
                newClips.removeAt(targetIdx)
                val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                val newSelected = (targetIdx - 1).coerceAtLeast(0)
                pushState(
                    currentProject.copy(
                        clips = newClips,
                        selectedClipIndex = newSelected,
                        totalDurationSeconds = newTotal
                    )
                )
                Toast.makeText(context, "🗑️ Cut / Removed '${target.name}'!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun applyDuplicateClip() {
        when (selectedTrackNum) {
            1 -> {
                val list = currentProject.getResolvedCodeTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                if (idx in list.indices) {
                    val item = list[idx]
                    val dup = item.copy(id = UUID.randomUUID().toString(), title = "${item.title} (Copy)", startSeconds = item.startSeconds + item.durationSeconds)
                    list.add(idx + 1, dup)
                    pushState(currentProject.copy(codeTracks = list))
                    Toast.makeText(context, "📋 Code card duplicated!", Toast.LENGTH_SHORT).show()
                }
            }
            3 -> {
                val list = currentProject.getResolvedVisualAssets().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                if (idx in list.indices) {
                    val item = list[idx]
                    val dup = item.copy(id = UUID.randomUUID().toString(), name = "${item.name} (Copy)", startSeconds = item.startSeconds + 1.0f)
                    list.add(idx + 1, dup)
                    pushState(currentProject.copy(visualAssetTracks = list))
                    Toast.makeText(context, "📋 Asset card duplicated!", Toast.LENGTH_SHORT).show()
                }
            }
            4 -> {
                val list = currentProject.getResolvedTextSubtitles().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                if (idx in list.indices) {
                    val item = list[idx]
                    val dup = item.copy(id = UUID.randomUUID().toString(), startSeconds = item.startSeconds + item.durationSeconds)
                    list.add(idx + 1, dup)
                    pushState(currentProject.copy(textSubtitleTracks = list))
                    Toast.makeText(context, "📋 Subtitle duplicated!", Toast.LENGTH_SHORT).show()
                }
            }
            5 -> {
                val list = currentProject.getResolvedVoiceoverTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                if (idx in list.indices) {
                    val item = list[idx]
                    val dup = item.copy(id = UUID.randomUUID().toString(), startSeconds = item.startSeconds + item.durationSeconds)
                    list.add(idx + 1, dup)
                    pushState(currentProject.copy(voiceoverTracks = list))
                    Toast.makeText(context, "📋 Voiceover duplicated!", Toast.LENGTH_SHORT).show()
                }
            }
            6 -> {
                val list = currentProject.getResolvedSoundAudioTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId }.coerceAtLeast(0)
                if (idx in list.indices) {
                    val item = list[idx]
                    val dup = item.copy(id = UUID.randomUUID().toString(), title = "${item.title} (Copy)", startSeconds = item.startSeconds + item.durationSeconds)
                    list.add(idx + 1, dup)
                    pushState(currentProject.copy(soundAudioTracks = list))
                    Toast.makeText(context, "📋 Sound duplicated!", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                if (currentProject.clips.isEmpty()) return
                val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
                val target = currentProject.clips[targetIdx]
                val dup = target.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${target.name}_Copy",
                    origin = "manual",
                    lastModified = System.currentTimeMillis()
                )
                val newClips = currentProject.clips.toMutableList()
                newClips.add(targetIdx + 1, dup)
                val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                pushState(currentProject.copy(clips = newClips, selectedClipIndex = targetIdx + 1, totalDurationSeconds = newTotal))
                Toast.makeText(context, "📋 Clip duplicated!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Layer Order: Bring Forward or Send Backward
    fun applyLayerOrder(bringForward: Boolean) {
        if (selectedTrackNum == 2 && currentProject.clips.size > 1) {
            val list = currentProject.clips.toMutableList()
            val idx = currentProject.selectedClipIndex.coerceIn(0, list.lastIndex)
            val swapWith = if (bringForward) idx + 1 else idx - 1
            if (swapWith in list.indices) {
                val temp = list[idx]
                list[idx] = list[swapWith]
                list[swapWith] = temp
                pushState(currentProject.copy(clips = list, selectedClipIndex = swapWith))
                Toast.makeText(context, if (bringForward) "Moved Forward ▲" else "Moved Backward ▼", Toast.LENGTH_SHORT).show()
            }
        } else if (selectedTrackNum == 3) {
            val list = currentProject.getResolvedVisualAssets().toMutableList()
            val idx = list.indexOfFirst { it.id == selectedCardId }
            if (idx != -1) {
                val swapWith = if (bringForward) idx + 1 else idx - 1
                if (swapWith in list.indices) {
                    val temp = list[idx]
                    list[idx] = list[swapWith]
                    list[swapWith] = temp
                    pushState(currentProject.copy(visualAssetTracks = list))
                    Toast.makeText(context, if (bringForward) "Layer Up ▲" else "Layer Down ▼", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Select a clip or visual asset to adjust layer order", Toast.LENGTH_SHORT).show()
        }
    }

    // Cut Head: Ripple trim card start to playhead position
    fun applyCutHead() {
        val playhead = currentProject.currentPositionSeconds
        when (selectedTrackNum) {
            1 -> {
                val list = currentProject.getResolvedCodeTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = (it.startSeconds + it.durationSeconds) - playhead
                    if (newDur >= 0.2f && playhead >= it.startSeconds) {
                        list[idx] = it.copy(startSeconds = playhead, durationSeconds = newDur)
                        pushState(currentProject.copy(codeTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Head to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Playhead must be inside card to trim head", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            3 -> {
                val list = currentProject.getResolvedVisualAssets().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = (it.startSeconds + it.durationSeconds) - playhead
                    if (newDur >= 0.2f && playhead >= it.startSeconds) {
                        list[idx] = it.copy(startSeconds = playhead, durationSeconds = newDur)
                        pushState(currentProject.copy(visualAssetTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Head to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            4 -> {
                val list = currentProject.getResolvedTextSubtitles().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = (it.startSeconds + it.durationSeconds) - playhead
                    if (newDur >= 0.2f && playhead >= it.startSeconds) {
                        list[idx] = it.copy(startSeconds = playhead, durationSeconds = newDur)
                        pushState(currentProject.copy(textSubtitleTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Head to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            5 -> {
                val list = currentProject.getResolvedVoiceoverTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = (it.startSeconds + it.durationSeconds) - playhead
                    if (newDur >= 0.2f && playhead >= it.startSeconds) {
                        list[idx] = it.copy(startSeconds = playhead, durationSeconds = newDur)
                        pushState(currentProject.copy(voiceoverTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Head to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            6 -> {
                val list = currentProject.getResolvedSoundAudioTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = (it.startSeconds + it.durationSeconds) - playhead
                    if (newDur >= 0.2f && playhead >= it.startSeconds) {
                        list[idx] = it.copy(startSeconds = playhead, durationSeconds = newDur)
                        pushState(currentProject.copy(soundAudioTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Head to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                if (currentProject.clips.isNotEmpty()) {
                    val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.lastIndex)
                    val target = currentProject.clips[targetIdx]
                    val cutAmount = 0.5f.coerceAtMost(target.durationSeconds - 0.2f)
                    if (cutAmount > 0f) {
                        val newDur = target.durationSeconds - cutAmount
                        val newClips = currentProject.clips.toMutableList()
                        newClips[targetIdx] = target.copy(
                            durationSeconds = newDur,
                            inSeconds = target.inSeconds + cutAmount
                        )
                        val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                        pushState(currentProject.copy(clips = newClips, totalDurationSeconds = newTotal))
                        Toast.makeText(context, "✂️ Trimmed Head (0.5s)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Cut Tail: Ripple trim card end to playhead position
    fun applyCutTail() {
        val playhead = currentProject.currentPositionSeconds
        when (selectedTrackNum) {
            1 -> {
                val list = currentProject.getResolvedCodeTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = playhead - it.startSeconds
                    if (newDur >= 0.2f) {
                        list[idx] = it.copy(durationSeconds = newDur)
                        pushState(currentProject.copy(codeTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Tail to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Playhead must leave at least 0.2s duration", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            3 -> {
                val list = currentProject.getResolvedVisualAssets().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = playhead - it.startSeconds
                    if (newDur >= 0.2f) {
                        list[idx] = it.copy(durationSeconds = newDur)
                        pushState(currentProject.copy(visualAssetTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Tail to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            4 -> {
                val list = currentProject.getResolvedTextSubtitles().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = playhead - it.startSeconds
                    if (newDur >= 0.2f) {
                        list[idx] = it.copy(durationSeconds = newDur)
                        pushState(currentProject.copy(textSubtitleTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Tail to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            5 -> {
                val list = currentProject.getResolvedVoiceoverTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = playhead - it.startSeconds
                    if (newDur >= 0.2f) {
                        list[idx] = it.copy(durationSeconds = newDur)
                        pushState(currentProject.copy(voiceoverTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Tail to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            6 -> {
                val list = currentProject.getResolvedSoundAudioTracks().toMutableList()
                val idx = list.indexOfFirst { it.id == selectedCardId || playhead in it.startSeconds..(it.startSeconds + it.durationSeconds) }
                if (idx != -1) {
                    val it = list[idx]
                    val newDur = playhead - it.startSeconds
                    if (newDur >= 0.2f) {
                        list[idx] = it.copy(durationSeconds = newDur)
                        pushState(currentProject.copy(soundAudioTracks = list))
                        Toast.makeText(context, "✂️ Trimmed Tail to ${formatSeconds(playhead)}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                if (currentProject.clips.isNotEmpty()) {
                    val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.lastIndex)
                    val target = currentProject.clips[targetIdx]
                    val cutAmount = 0.5f.coerceAtMost(target.durationSeconds - 0.2f)
                    if (cutAmount > 0f) {
                        val newDur = target.durationSeconds - cutAmount
                        val newClips = currentProject.clips.toMutableList()
                        newClips[targetIdx] = target.copy(durationSeconds = newDur)
                        val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                        pushState(currentProject.copy(clips = newClips, totalDurationSeconds = newTotal))
                        Toast.makeText(context, "✂️ Trimmed Tail (0.5s)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Modal dialogs
    var showFullscreenPlayer by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var viewportMode by remember { mutableStateOf("VIDEO") }
    var isSimulatingRender by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf(0f) }
    var renderLogText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    var isGeneratingFromPrompt by remember { mutableStateOf(false) }
    var promptGenerationStatus by remember { mutableStateOf("") }
    var showPromptDirectorDialog by remember { mutableStateOf(false) }

    fun handleGenerateFromPrompt(userPrompt: String) {
        if (userPrompt.isBlank() || isGeneratingFromPrompt) return
        coroutineScope.launch {
            isGeneratingFromPrompt = true
            promptGenerationStatus = "Analyzing topic & researching..."
            val result = com.example.ui.components.SaifAiEditingEngine.generateAutonomousDirectorEdl(
                context = context,
                prompt = userPrompt,
                rawVideoUrl = null,
                onStepUpdate = { step, _ -> promptGenerationStatus = step }
            )
            result.onSuccess { (_, proj) ->
                pushState(proj)
                currentProject = proj
                isPlaying = true
                Toast.makeText(context, "✅ Assembled video from prompt!", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Generation error: ${err.message}", Toast.LENGTH_LONG).show()
            }
            isGeneratingFromPrompt = false
            promptGenerationStatus = ""
        }
    }

    // Persist changes back to local storage whenever project updates
    LaunchedEffect(currentProject) {
        com.example.util.ProjectDataManager.saveVideoProject(context, currentProject)
    }

    // Live playback ticker with smooth 20fps interpolation and auto-looping
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val safeTotal = if (currentProject.totalDurationSeconds > 0.5f) {
                currentProject.totalDurationSeconds
            } else {
                val clipSum = currentProject.clips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                if (clipSum > 0.5f) clipSum else 15.0f
            }
            if (currentProject.totalDurationSeconds != safeTotal) {
                currentProject = currentProject.copy(totalDurationSeconds = safeTotal)
            }
            if (currentProject.currentPositionSeconds >= safeTotal - 0.2f) {
                currentProject = currentProject.copy(currentPositionSeconds = 0f)
            }
            while (isPlaying) {
                delay(50) // smooth 20 fps tick
                val cur = currentProject.currentPositionSeconds
                val tot = if (currentProject.totalDurationSeconds > 0.5f) currentProject.totalDurationSeconds else 15.0f
                val next = cur + 0.05f
                if (next >= tot) {
                    currentProject = currentProject.copy(currentPositionSeconds = 0f)
                    isPlaying = false
                    com.example.utils.TTSManager.stop()
                } else {
                    currentProject = currentProject.copy(currentPositionSeconds = next)
                }
            }
        } else {
            com.example.utils.TTSManager.stop()
        }
    }

    // Launcher to add more clips from gallery with real duration/metadata decoding
    val addMoreMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newClips = uris.mapIndexed { idx, uri ->
                var duration = 4.0f
                var isPhoto = false
                val mime = context.contentResolver.getType(uri) ?: ""
                if (mime.startsWith("image/")) {
                    isPhoto = true
                    duration = 4.0f
                } else {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        retriever.release()
                        val durMs = durStr?.toLongOrNull() ?: 0L
                        if (durMs > 200) {
                            duration = (durMs / 1000f).coerceIn(1.0f, 600.0f)
                            isPhoto = false
                        } else {
                            isPhoto = true
                            duration = 4.0f
                        }
                    } catch (_: Exception) {
                        isPhoto = mime.startsWith("image/")
                        duration = 4.0f
                    }
                }
                val name = uri.lastPathSegment?.substringAfterLast('/')?.take(18) ?: "Clip_${currentProject.clips.size + idx + 1}"
                TimelineMediaClip(
                    name = name,
                    uri = uri,
                    durationSeconds = duration,
                    isAiEnhanced = false,
                    isPhoto = isPhoto
                )
            }
            val updatedClips = currentProject.clips + newClips
            val newTotal = updatedClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
            pushState(
                currentProject.copy(
                    clips = updatedClips,
                    totalDurationSeconds = newTotal
                )
            )
            Toast.makeText(context, "✅ Imported ${uris.size} items with decoded duration!", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher to import audio from phone storage into Track 6 (SFX / BGM)
    val addAudioMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var dur = 15.0f
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                val durMs = durStr?.toLongOrNull() ?: 0L
                if (durMs > 200) dur = (durMs / 1000f).coerceIn(1.0f, 600.0f)
            } catch (_: Exception) {}

            val audioItem = SoundAudioTrackItem(
                title = uri.lastPathSegment?.substringAfterLast('/')?.take(18) ?: "Custom_Track.mp3",
                startSeconds = currentProject.currentPositionSeconds,
                durationSeconds = dur,
                isSfx = false,
                volume = 1.0f,
                audioUri = uri.toString()
            )
            val updatedAudio = currentProject.getResolvedSoundAudioTracks() + audioItem
            pushState(currentProject.copy(soundAudioTracks = updatedAudio))
            Toast.makeText(context, "✅ Audio imported to Track 6 (${"%.1f".format(dur)}s)!", Toast.LENGTH_SHORT).show()
        }
    }



    // Back handling
    BackHandler {
        isPlaying = false
        com.example.utils.TTSManager.stop()
        if (activeSubView != VideoStudioSubView.NONE) {
            activeSubView = VideoStudioSubView.NONE
        } else if (activeToolTab != VideoStudioToolTab.NONE) {
            activeToolTab = VideoStudioToolTab.NONE
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131313))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131313))
        ) {
            // --- 1. Top Bar (Workspace Header) ---
            Surface(
                color = Color(0xFF0E0E0E).copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = {
                                isPlaying = false
                                com.example.utils.TTSManager.stop()
                                onClose()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1C1B1B))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = currentProject.projectName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showResolutionDialog = true }
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00DAF3)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${currentProject.resolution} · ${currentProject.fps} · ${currentProject.aspectRatio}",
                                    color = Color(0xFFCCC3D9),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { handleUndo() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF6B6675),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { handleRedo() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF6B6675),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // AI DIRECTOR BUTTON
                        IconButton(
                            onClick = { showPromptDirectorDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF201F2F))
                                .border(1.dp, Color(0xFF00DAF3).copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Prompt Director",
                                tint = Color(0xFF00DAF3),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // EXPORT BUTTON (Opens Export Studio Interface)
                        Button(
                            onClick = { activeSubView = VideoStudioSubView.EXPORT_STUDIO },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Export", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }

            // --- 2. Preview Viewport Area with Multi-Mode Selector ---
            VideoStudioViewportModeBar(
                currentMode = viewportMode,
                onSelectMode = { viewportMode = it }
            )

            // Pro NLE Aspect Ratio Switching Chips & Proxy Stream Mode Toggle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aspect Ratio Selector Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ratios = listOf(
                        Pair("9:16", "📱 9:16 Shorts"),
                        Pair("16:9", "📺 16:9 YT"),
                        Pair("1:1", "⬛ 1:1 Square"),
                        Pair("4:5", "📐 4:5 Feed")
                    )
                    ratios.forEach { (ratio, label) ->
                        val isSelected = currentProject.aspectRatio == ratio
                        Surface(
                            onClick = {
                                pushState(currentProject.copy(aspectRatio = ratio))
                                Toast.makeText(context, "Aspect Ratio: $ratio", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF00DAF3).copy(alpha = 0.25f) else Color(0xFF1E1D24),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF00DAF3) else Color(0x30FFFFFF))
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF00DAF3) else Color(0xFFCCC3D9),
                                fontSize = 8.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Proxy / Video Stream Preview Mode Toggle
                Surface(
                    onClick = {
                        val nextState = !currentProject.isProxyModeEnabled
                        pushState(currentProject.copy(isProxyModeEnabled = nextState))
                        if (nextState) {
                            Toast.makeText(context, "⚡ Proxy Stream Mode ON: Offscreen 60fps canvas stream (zero lag)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "💎 HD Direct Live Mode", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (currentProject.isProxyModeEnabled) Color(0xFF35E6A0).copy(alpha = 0.2f) else Color(0xFF1E1D24),
                    border = BorderStroke(1.dp, if (currentProject.isProxyModeEnabled) Color(0xFF35E6A0) else Color(0x30FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (currentProject.isProxyModeEnabled) Color(0xFF35E6A0) else Color(0xFF888888))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (currentProject.isProxyModeEnabled) "⚡ Proxy ON" else "⚡ Proxy OFF",
                            color = if (currentProject.isProxyModeEnabled) Color(0xFF35E6A0) else Color(0xFF888888),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Large Responsive Centered Preview Viewport
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 265.dp)
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                val maxH = maxHeight
                val maxW = maxWidth
                val previewModifier = when {
                    viewportMode != "VIDEO" -> Modifier.fillMaxSize()
                    currentProject.aspectRatio == "16:9" -> {
                        val w = maxW.coerceAtMost(360.dp)
                        val h = (w.value * 9f / 16f).dp.coerceAtMost(maxH)
                        Modifier.size(width = w, height = h)
                    }
                    currentProject.aspectRatio == "1:1" -> {
                        val s = minOf(maxH, maxW, 225.dp)
                        Modifier.size(s)
                    }
                    currentProject.aspectRatio == "4:5" -> {
                        val h = maxH.coerceAtMost(245.dp)
                        val w = (h.value * 4f / 5f).dp.coerceAtMost(maxW)
                        Modifier.size(width = w, height = h)
                    }
                    else -> { // "9:16" Shorts / Reels
                        val h = maxH.coerceAtMost(255.dp)
                        val w = (h.value * 9f / 16f).dp.coerceAtMost(maxW)
                        Modifier.size(width = w, height = h)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black,
                    border = BorderStroke(1.5.dp, if (currentProject.isProxyModeEnabled) Color(0xFF35E6A0) else Color(0x6000DAF3)),
                    shadowElevation = 16.dp,
                    modifier = previewModifier
                ) {
                    when (viewportMode) {
                        "CODE" -> {
                            LivePythonCodeRenderer(
                                project = currentProject,
                                isSimulatingRender = isSimulatingRender,
                                renderProgress = renderProgress,
                                renderLogText = renderLogText,
                                onStartRender = {
                                    if (!isSimulatingRender) {
                                        coroutineScope.launch {
                                            isSimulatingRender = true
                                            renderProgress = 0.15f
                                            renderLogText = "[1/5] 📦 Scanning assets_catalog.json for Telegram assets..."
                                            delay(350)
                                            renderProgress = 0.40f
                                            renderLogText += "\n[2/5] ⚡ Compiling Ken Burns Zoom (1.0x -> 1.15x) on Scene 1..."
                                            delay(350)
                                            renderProgress = 0.65f
                                            renderLogText += "\n[3/5] 🎯 Compositing 3D Checkmark pop-in bounce overlay on Scene 3..."
                                            delay(350)
                                            renderProgress = 0.85f
                                            renderLogText += "\n[4/5] 🎵 Mixing audio with dynamic ducking (BGM 0.35x during narration)..."
                                            delay(350)
                                            renderProgress = 1.0f
                                            renderLogText += "\n[5/5] ✅ Live Render Complete! Output: 1080x1920 MP4 ready on timeline."
                                            delay(300)
                                            isSimulatingRender = false
                                            viewportMode = "VIDEO"
                                            isPlaying = true
                                            Toast.makeText(context, "✅ Rendered video from Python code! Playing now.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        "SCRIPT" -> {
                            LiveScriptView(script = currentProject.script)
                        }
                        "CATALOG" -> {
                            LiveTelegramCatalogView(edlWhy = currentProject.activeEditPlan?.why, clips = currentProject.clips)
                        }
                        else -> {
                            val activeClip = currentProject.clips.getOrNull(currentProject.selectedClipIndex)
                                ?: currentProject.clips.firstOrNull()

                            Box(modifier = Modifier.fillMaxSize()) {
                                if (activeClip?.uri != null) {
                                    AsyncImage(
                                        model = activeClip.uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clickable { isPlaying = !isPlaying }
                                    )
                                } else {
                                    LiveAutonomousVideoViewport(
                                        project = currentProject,
                                        isPlaying = isPlaying,
                                        onTogglePlay = { isPlaying = !isPlaying },
                                        onGenerateFromPrompt = { handleGenerateFromPrompt(it) },
                                        isGenerating = isGeneratingFromPrompt,
                                        generatingStatus = promptGenerationStatus
                                    )
                                }

                                // Picture in Picture (PIP) overlay preview if active
                                if (currentProject.overlayMediaUri != null && currentProject.activePipPosition != "None") {
                                    val pipAlignment = when (currentProject.activePipPosition) {
                                        "Bottom-Right" -> Alignment.BottomEnd
                                        "Split-Screen" -> Alignment.CenterEnd
                                        else -> Alignment.TopEnd
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF00DAF3)),
                                        color = Color.Black.copy(alpha = currentProject.overlayOpacity),
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(width = 65.dp, height = 90.dp)
                                            .align(pipAlignment)
                                    ) {
                                        AsyncImage(
                                            model = Uri.parse(currentProject.overlayMediaUri),
                                            contentDescription = "PIP Overlay",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // Top right fullscreen toggle
                                IconButton(
                                    onClick = { showFullscreenPlayer = true },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                // Bottom left exact timestamp pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(formatExactTimestamp(currentProject.currentPositionSeconds), color = Color(0xFF00DAF3), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text(" / ${formatExactTimestamp(currentProject.totalDurationSeconds)}", color = Color.White.copy(alpha = 0.6f), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. Transport Controls Row (44px) ---
            Surface(
                color = Color(0xFF0E0E0E).copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Exact Timestamp display (00:00.0 / 00:00.0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatExactTimestamp(currentProject.currentPositionSeconds),
                            color = Color(0xFF00DAF3),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = " / ${formatExactTimestamp(currentProject.totalDurationSeconds)}",
                            color = Color(0xFFCCC3D9),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Transport Play/Pause Row with Jump to Start & Jump to End
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Jump to Start (|<<)
                        IconButton(
                            onClick = {
                                currentProject = currentProject.copy(currentPositionSeconds = 0f)
                                Toast.makeText(context, "|<< Start", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.FirstPage, contentDescription = "Jump to Start", tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
                        }

                        // Replay -5s
                        IconButton(
                            onClick = {
                                val next = (currentProject.currentPositionSeconds - 5f).coerceAtLeast(0f)
                                currentProject = currentProject.copy(currentPositionSeconds = next)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Replay5, contentDescription = "-5s", tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
                        }

                        // Main Play / Pause Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7B2BFF), Color(0xFF006CEB))
                                    )
                                )
                                .clickable {
                                    val safeTotal = if (currentProject.totalDurationSeconds > 0.5f) {
                                        currentProject.totalDurationSeconds
                                    } else {
                                        val clipSum = currentProject.clips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                                        if (clipSum > 0.5f) clipSum else 15.0f
                                    }
                                    if (!isPlaying) {
                                        val startPos = if (currentProject.currentPositionSeconds >= safeTotal - 0.25f) 0f else currentProject.currentPositionSeconds
                                        currentProject = currentProject.copy(currentPositionSeconds = startPos, totalDurationSeconds = safeTotal)
                                        isPlaying = true
                                    } else {
                                        isPlaying = false
                                        com.example.utils.TTSManager.stop()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Forward +5s
                        IconButton(
                            onClick = {
                                val next = (currentProject.currentPositionSeconds + 5f).coerceAtMost(currentProject.totalDurationSeconds)
                                currentProject = currentProject.copy(currentPositionSeconds = next)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Forward5, contentDescription = "+5s", tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
                        }

                        // Jump to End (>>|)
                        IconButton(
                            onClick = {
                                currentProject = currentProject.copy(currentPositionSeconds = currentProject.totalDurationSeconds)
                                Toast.makeText(context, ">>| End", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.LastPage, contentDescription = "Jump to End", tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
                        }
                    }

                    // Transport Actions: Fullscreen, Split & Cut
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { showFullscreenPlayer = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color(0xFF00DAF3), modifier = Modifier.size(18.dp))
                        }

                        Surface(
                            onClick = {
                                isPlaying = false
                                activeSubView = VideoStudioSubView.AI_DIRECTOR_CHAT
                                Toast.makeText(context, "📍 Co-Director at ${formatExactTimestamp(currentProject.currentPositionSeconds)}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1B1528),
                            border = BorderStroke(1.dp, Color(0x6000DAF3))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Pin", color = Color(0xFF00DAF3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Split Button
                        Surface(
                            onClick = { applySmartSplit() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF262530),
                            border = BorderStroke(1.dp, Color(0x8000DAF3))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Split", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Cut / Delete Button
                        Surface(
                            onClick = { applyCutDelete() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF38151E),
                            border = BorderStroke(1.dp, Color(0x60FF4D6D))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Cut", color = Color(0xFFFF4D6D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- 4. Signature SAIF AI EDITING Pill Row (56px) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main SAIF AI EDITING Button
                Surface(
                    onClick = { showSaifAiEditingModal = true },
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF7B2BFF),
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, Color(0x6000DAF3)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF7B2BFF),
                                        Color(0xFF006CEB),
                                        Color(0xFF7B2BFF)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SAIF AI EDITING",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "v2.4",
                                    color = Color(0xFF00DAF3),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Chat Bubble Director Shortcut
                IconButton(
                    onClick = { activeSubView = VideoStudioSubView.AI_DIRECTOR_CHAT },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = "AI Director Chat",
                        tint = Color(0xFFD1BCFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // --- 5. Pro NLE Multi-Track Timeline (Scroll & Zoom Fix + 6 Dedicated Color-Coded Tracks) ---
            val safeTotalSeconds = if (currentProject.totalDurationSeconds > 0.5f) {
                currentProject.totalDurationSeconds
            } else {
                val clipSum = currentProject.clips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                if (clipSum > 0.5f) clipSum else 15.0f
            }
            val pixelsPerSecond = (36f * timelineZoom).dp
            val timelineWidth = (safeTotalSeconds * pixelsPerSecond.value).dp.coerceAtLeast(420.dp)
            val timelineVerticalScrollState = rememberScrollState()
            val timelineHorizontalScrollState = rememberScrollState()

            Surface(
                color = Color(0xFF09090C),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Timeline Top Control Bar (Tracks count, Snap toggle, and Zoom slider controls)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0E0E12))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Track Count & Magnet Snap Toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ViewTimeline, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("6 TRACKS", color = Color(0xFF00DAF3), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                onClick = {
                                    isSnapEnabled = !isSnapEnabled
                                    Toast.makeText(context, if (isSnapEnabled) "🧲 Snap to Cuts ON" else "Snap OFF", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSnapEnabled) Color(0xFF00DAF3).copy(alpha = 0.2f) else Color(0xFF202028),
                                border = BorderStroke(0.5.dp, if (isSnapEnabled) Color(0xFF00DAF3) else Color(0x40FFFFFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isSnapEnabled) "🧲 SNAP ON" else "SNAP OFF",
                                        color = if (isSnapEnabled) Color(0xFF00DAF3) else Color(0xFF888888),
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Dedicated Zoom Controls (Zoom Out `-`, Slider, Zoom In `+`, and `[FIT]`)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { timelineZoom = (timelineZoom - 0.25f).coerceAtLeast(0.5f) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color(0xFFCCC3D9), modifier = Modifier.size(14.dp))
                            }

                            Slider(
                                value = timelineZoom,
                                onValueChange = { timelineZoom = it },
                                valueRange = 0.5f..3.5f,
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(20.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00DAF3),
                                    activeTrackColor = Color(0xFF00DAF3),
                                    inactiveTrackColor = Color(0xFF262535)
                                )
                            )

                            IconButton(
                                onClick = { timelineZoom = (timelineZoom + 0.25f).coerceAtMost(3.5f) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color(0xFFCCC3D9), modifier = Modifier.size(14.dp))
                            }

                            Text(
                                text = "${(timelineZoom * 100).toInt()}%",
                                color = Color(0xFF00DAF3),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                onClick = {
                                    timelineZoom = 1.0f
                                    Toast.makeText(context, "Fit to Screen", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF262535)
                            ) {
                                Text("FIT", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // Selected Item Quick Action Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF131218))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Track $selectedTrackNum · Active: ${
                                when (selectedTrackNum) {
                                    1 -> "Code Motion Graphics"
                                    2 -> currentProject.clips.getOrNull(currentProject.selectedClipIndex)?.name ?: "Video Clip"
                                    3 -> "Visual Asset Overlay"
                                    4 -> "Kinetic Subtitle"
                                    5 -> "AI Voice Narration"
                                    else -> "Audio FX & BGM"
                                }
                            }",
                            color = Color(0xFFD1BCFF),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { activeToolTab = VideoStudioToolTab.TUNE },
                                shape = RoundedCornerShape(4.dp),
                                color = if (activeToolTab == VideoStudioToolTab.TUNE) Color(0xFF00DAF3).copy(alpha = 0.3f) else Color(0xFF232230),
                                border = BorderStroke(0.5.dp, Color(0xFF00DAF3))
                            ) {
                                Text("Tune 🎛️", color = Color(0xFF00DAF3), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { applySmartSplit() },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF232230)
                            ) {
                                Text("Split ✂️", color = Color(0xFF00DAF3), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { activeSubView = VideoStudioSubView.AI_VIDEO_GENERATOR },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF2E1954),
                                border = BorderStroke(1.dp, Color(0xFF7B2BFF))
                            ) {
                                Text("AI Video 🎬", color = Color(0xFFD1BCFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { applyLayerOrder(bringForward = true) },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF232230)
                            ) {
                                Text("▲ Layer", color = Color(0xFFCCC3D9), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { applyLayerOrder(bringForward = false) },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF232230)
                            ) {
                                Text("Layer ▼", color = Color(0xFFCCC3D9), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { applyCutHead() },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF232230)
                            ) {
                                Text("✂️ Head", color = Color.White, fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { applyCutTail() },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF232230)
                            ) {
                                Text("Tail ✂️", color = Color.White, fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { applyDuplicateClip() },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF232230)
                            ) {
                                Text("+ Dup", color = Color.White, fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                            Surface(
                                onClick = { applyCutDelete() },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF45131C)
                            ) {
                                Text("Del 🗑️", color = Color(0xFFFF4D6D), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // Multi-Track Viewport with Fixed Left Track Headers & Synchronized Right Scrollable Lanes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Left Fixed Track Headers Column (Always visible, scrolls vertically in sync)
                        Column(
                            modifier = Modifier
                                .width(82.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF0D0C12))
                                .verticalScroll(timelineVerticalScrollState),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Top Ruler Spacer
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(Color(0xFF0A090E))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("TRACK", color = Color(0xFF888496), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }

                            // Track 1 Header: Code Animations
                            Surface(
                                onClick = { selectedTrackNum = 1; activeToolTab = VideoStudioToolTab.EDIT },
                                color = if (selectedTrackNum == 1) Color(0xFF00F5A0).copy(alpha = 0.25f) else Color(0xFF131D18),
                                border = if (selectedTrackNum == 1) BorderStroke(1.dp, Color(0xFF00F5A0)) else null,
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(start = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("< / >", color = Color(0xFF00F5A0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Code", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("HTML/GSAP", color = Color(0xFF00F5A0).copy(alpha = 0.8f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Track 2 Header: Video / B-Roll
                            Surface(
                                onClick = { selectedTrackNum = 2; activeToolTab = VideoStudioToolTab.EDIT },
                                color = if (selectedTrackNum == 2) Color(0xFF7B2BFF).copy(alpha = 0.25f) else Color(0xFF1A1426),
                                border = if (selectedTrackNum == 2) BorderStroke(1.dp, Color(0xFF00DAF3)) else null,
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .padding(start = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Video", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("B-Roll / 4K", color = Color(0xFFC084FC), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Track 3 Header: Visual Assets & Stickers
                            Surface(
                                onClick = { selectedTrackNum = 3; activeToolTab = VideoStudioToolTab.OVERLAY },
                                color = if (selectedTrackNum == 3) Color(0xFFFF9F1C).copy(alpha = 0.25f) else Color(0xFF221708),
                                border = if (selectedTrackNum == 3) BorderStroke(1.dp, Color(0xFFFFB703)) else null,
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .padding(start = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Assets", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Telegram", color = Color(0xFFFFB703), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Track 4 Header: Kinetic Subtitles & Text
                            Surface(
                                onClick = { selectedTrackNum = 4; activeToolTab = VideoStudioToolTab.TEXT },
                                color = if (selectedTrackNum == 4) Color(0xFFFFD166).copy(alpha = 0.25f) else Color(0xFF201B0B),
                                border = if (selectedTrackNum == 4) BorderStroke(1.dp, Color(0xFFFFD166)) else null,
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .padding(start = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Title, contentDescription = null, tint = Color(0xFFFFD166), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Text", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Hormozi", color = Color(0xFFFFD166), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Track 5 Header: AI Voiceover (TTS)
                            Surface(
                                onClick = { selectedTrackNum = 5 },
                                color = if (selectedTrackNum == 5) Color(0xFF00DAF3).copy(alpha = 0.25f) else Color(0xFF081B24),
                                border = if (selectedTrackNum == 5) BorderStroke(1.dp, Color(0xFF00DAF3)) else null,
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .padding(start = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Voice", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Neural TTS", color = Color(0xFF00DAF3), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Track 6 Header: SFX & Background Music
                            Surface(
                                onClick = { selectedTrackNum = 6; activeToolTab = VideoStudioToolTab.AUDIO },
                                color = if (selectedTrackNum == 6) Color(0xFFFF4D94).copy(alpha = 0.25f) else Color(0xFF220817),
                                border = if (selectedTrackNum == 6) BorderStroke(1.dp, Color(0xFFFF4D94)) else null,
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .padding(start = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFFF4D94), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Audio", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("SFX / BGM", color = Color(0xFFFF4D94), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // Right Scrollable Lane Area with Pinch-to-Zoom and Vertical Scroll Sync
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(timelineVerticalScrollState)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(timelineWidth)
                                    .horizontalScroll(timelineHorizontalScrollState)
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            timelineZoom = (timelineZoom * zoom).coerceIn(0.5f, 3.5f)
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 1. Dynamic Time Ruler (Seconds and tenths calculated with zoom)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .background(Color(0xFF0B0A10))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    val clickedSec = (offset.x / pixelsPerSecond.value).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(clickedSec))
                                                }
                                            }
                                    ) {
                                        // Draw dynamic tick marks
                                        val majorInterval = when {
                                            timelineZoom >= 2.0f -> 0.5f
                                            timelineZoom >= 1.0f -> 1.0f
                                            else -> 2.0f
                                        }
                                        val count = (safeTotalSeconds / majorInterval).toInt() + 1
                                        for (i in 0 until count) {
                                            val sec = i * majorInterval
                                            val xOffset = (sec * pixelsPerSecond.value).dp
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = xOffset)
                                                    .width(1.dp)
                                                    .height(8.dp)
                                                    .background(Color(0x60FFFFFF))
                                                    .align(Alignment.BottomStart)
                                            )
                                            Text(
                                                text = formatExactTimestamp(sec),
                                                color = Color(0xFF888496),
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier
                                                    .offset(x = xOffset + 3.dp, y = 2.dp)
                                                    .align(Alignment.TopStart)
                                            )
                                        }
                                    }

                                    // Track 1 Lane: Code Animations (HTML5/CSS3/GSAP Motion Graphics)
                                    val codeTracks = currentProject.getResolvedCodeTracks()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .background(Color(0x1500F5A0))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    val clickedSec = (offset.x / pixelsPerSecond.value).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(clickedSec))
                                                }
                                            }
                                    ) {
                                        codeTracks.forEach { item ->
                                            val isCardSelected = selectedTrackNum == 1 && selectedCardId == item.id
                                            val cardX = (item.startSeconds * pixelsPerSecond.value).dp
                                            val cardW = (item.durationSeconds * pixelsPerSecond.value).dp.coerceAtLeast(60.dp)

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = cardX)
                                                    .width(cardW)
                                                    .fillMaxHeight()
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        selectedTrackNum = 1
                                                        selectedCardId = item.id
                                                        currentProject = currentProject.copy(currentPositionSeconds = item.startSeconds)
                                                        activeToolTab = VideoStudioToolTab.TUNE
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF065F46),
                                                    border = if (isCardSelected) BorderStroke(2.dp, Color(0xFF00F5A0)) else BorderStroke(0.5.dp, Color(0x6000F5A0)),
                                                    shadowElevation = if (isCardSelected) 6.dp else 1.dp,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color(0xFF00F5A0)))
                                                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                            Text(item.title, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                            Text("${item.animationType} · ${"%.1f".format(item.durationSeconds)}s", color = Color(0xFF6EE7B7), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }
                                                // Trim Handles (Left & Right)
                                                if (isCardSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newStart = (item.startSeconds + deltaSec).coerceAtLeast(0f)
                                                                    val newDur = (item.durationSeconds - deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedCodeTracks().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(startSeconds = newStart, durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(codeTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newDur = (item.durationSeconds + deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedCodeTracks().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(codeTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Track 2 Lane: Video / B-Roll Clips
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .background(Color(0x157B2BFF))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    val clickedSec = (offset.x / pixelsPerSecond.value).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(clickedSec))
                                                }
                                            }
                                    ) {
                                        var clipAccumSec = 0f
                                        currentProject.clips.forEachIndexed { index, clip ->
                                            val isClipSelected = selectedTrackNum == 2 && currentProject.selectedClipIndex == index
                                            val clipX = (clipAccumSec * pixelsPerSecond.value).dp
                                            val clipW = (clip.durationSeconds * pixelsPerSecond.value).dp.coerceAtLeast(60.dp)
                                            val startOfClip = clipAccumSec

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = clipX)
                                                    .width(clipW)
                                                    .fillMaxHeight()
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        selectedTrackNum = 2
                                                        selectedCardId = clip.id
                                                        currentProject = currentProject.copy(selectedClipIndex = index, currentPositionSeconds = startOfClip)
                                                        activeToolTab = VideoStudioToolTab.TUNE
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF581C87),
                                                    border = if (isClipSelected) BorderStroke(2.dp, Color(0xFF00DAF3)) else BorderStroke(0.5.dp, Color(0x60C084FC)),
                                                    shadowElevation = if (isClipSelected) 6.dp else 1.dp,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color(0xFFC084FC)))
                                                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(clip.name, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                                                                if (clip.name.contains("_cut") || clip.name.contains("_part")) {
                                                                    Text("✂️", fontSize = 8.sp)
                                                                }
                                                            }
                                                            Text("Speed ${clip.speed}x · ${"%.1f".format(clip.durationSeconds)}s", color = Color(0xFFE9D5FF), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }
                                                // Trim Handles (Left & Right) for Video Clip
                                                if (isClipSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                            .pointerInput(clip.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newIn = (clip.inSeconds + deltaSec).coerceAtLeast(0f)
                                                                    val newDur = (clip.durationSeconds - deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.clips.toMutableList()
                                                                    updated[index] = clip.copy(inSeconds = newIn, durationSeconds = newDur)
                                                                    val newTotal = updated.sumOf { it.durationSeconds.toDouble() }.toFloat()
                                                                    currentProject = currentProject.copy(clips = updated, totalDurationSeconds = newTotal)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(14.dp).background(Color.Black))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                            .pointerInput(clip.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newDur = (clip.durationSeconds + deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.clips.toMutableList()
                                                                    updated[index] = clip.copy(durationSeconds = newDur)
                                                                    val newTotal = updated.sumOf { it.durationSeconds.toDouble() }.toFloat()
                                                                    currentProject = currentProject.copy(clips = updated, totalDurationSeconds = newTotal)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(14.dp).background(Color.Black))
                                                    }
                                                }
                                            }
                                            clipAccumSec += clip.durationSeconds
                                        }

                                        // + Add Media from phone gallery button
                                        Surface(
                                            onClick = {
                                                addMoreMediaLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                                )
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF261D3A),
                                            border = BorderStroke(1.dp, Color(0x507B2BFF)),
                                            modifier = Modifier
                                                .offset(x = (clipAccumSec * pixelsPerSecond.value).dp + 6.dp)
                                                .size(width = 38.dp, height = 48.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Media", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    // Track 3 Lane: Visual Assets / Overlays / Stickers (Telegram & Local Images)
                                    val assetTracks = currentProject.getResolvedVisualAssets()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .background(Color(0x15FF9F1C))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    val clickedSec = (offset.x / pixelsPerSecond.value).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(clickedSec))
                                                }
                                            }
                                    ) {
                                        assetTracks.forEach { item ->
                                            val isCardSelected = selectedTrackNum == 3 && selectedCardId == item.id
                                            val cardX = (item.startSeconds * pixelsPerSecond.value).dp
                                            val cardW = (item.durationSeconds * pixelsPerSecond.value).dp.coerceAtLeast(60.dp)

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = cardX)
                                                    .width(cardW)
                                                    .fillMaxHeight()
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        selectedTrackNum = 3
                                                        selectedCardId = item.id
                                                        currentProject = currentProject.copy(currentPositionSeconds = item.startSeconds)
                                                        activeToolTab = VideoStudioToolTab.TUNE
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF78350F),
                                                    border = if (isCardSelected) BorderStroke(2.dp, Color(0xFFFFB703)) else BorderStroke(0.5.dp, Color(0x60FFB703)),
                                                    shadowElevation = if (isCardSelected) 6.dp else 1.dp,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color(0xFFFBBF24)))
                                                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                            Text(item.name, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                            Text("Sticker · ${"%.1f".format(item.durationSeconds)}s", color = Color(0xFFFDE68A), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }
                                                // Trim Handles (Left & Right)
                                                if (isCardSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newStart = (item.startSeconds + deltaSec).coerceAtLeast(0f)
                                                                    val newDur = (item.durationSeconds - deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedVisualAssets().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(startSeconds = newStart, durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(visualAssetTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newDur = (item.durationSeconds + deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedVisualAssets().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(visualAssetTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Track 4 Lane: Text & Kinetic Subtitles
                                    val textTracks = currentProject.getResolvedTextSubtitles()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .background(Color(0x15FFD166))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    val clickedSec = (offset.x / pixelsPerSecond.value).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(clickedSec))
                                                }
                                            }
                                    ) {
                                        textTracks.forEach { item ->
                                            val isCardSelected = selectedTrackNum == 4 && selectedCardId == item.id
                                            val cardX = (item.startSeconds * pixelsPerSecond.value).dp
                                            val cardW = (item.durationSeconds * pixelsPerSecond.value).dp.coerceAtLeast(60.dp)

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = cardX)
                                                    .width(cardW)
                                                    .fillMaxHeight()
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        selectedTrackNum = 4
                                                        selectedCardId = item.id
                                                        currentProject = currentProject.copy(currentPositionSeconds = item.startSeconds)
                                                        activeToolTab = VideoStudioToolTab.TUNE
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF713F12),
                                                    border = if (isCardSelected) BorderStroke(2.dp, Color(0xFFFFD166)) else BorderStroke(0.5.dp, Color(0x60FFD166)),
                                                    shadowElevation = if (isCardSelected) 6.dp else 1.dp,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color(0xFFFFD166)))
                                                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                            Text(item.text, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                            Text("${item.style} · ${"%.1f".format(item.durationSeconds)}s", color = Color(0xFFFEF08A), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }
                                                // Trim Handles (Left & Right)
                                                if (isCardSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newStart = (item.startSeconds + deltaSec).coerceAtLeast(0f)
                                                                    val newDur = (item.durationSeconds - deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedTextSubtitles().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(startSeconds = newStart, durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(textSubtitleTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newDur = (item.durationSeconds + deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedTextSubtitles().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(textSubtitleTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Track 5 Lane: AI Voiceover (TTS)
                                    val voiceTracks = currentProject.getResolvedVoiceoverTracks()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .background(Color(0x1500DAF3))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    val clickedSec = (offset.x / pixelsPerSecond.value).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(clickedSec))
                                                }
                                            }
                                    ) {
                                        voiceTracks.forEach { item ->
                                            val isCardSelected = selectedTrackNum == 5 && selectedCardId == item.id
                                            val cardX = (item.startSeconds * pixelsPerSecond.value).dp
                                            val cardW = (item.durationSeconds * pixelsPerSecond.value).dp.coerceAtLeast(60.dp)

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = cardX)
                                                    .width(cardW)
                                                    .fillMaxHeight()
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        selectedTrackNum = 5
                                                        selectedCardId = item.id
                                                        currentProject = currentProject.copy(currentPositionSeconds = item.startSeconds)
                                                        activeToolTab = VideoStudioToolTab.TUNE
                                                        com.example.utils.TTSManager.init(context)
                                                        com.example.utils.TTSManager.speak(item.narration, context)
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF075985),
                                                    border = if (isCardSelected) BorderStroke(2.dp, Color(0xFF00DAF3)) else BorderStroke(0.5.dp, Color(0x6000DAF3)),
                                                    shadowElevation = if (isCardSelected) 6.dp else 1.dp,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color(0xFF00DAF3)))
                                                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                            Text(item.narration.take(24) + if (item.narration.length > 24) "..." else "", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                            Text("TTS · ${item.speakerTone} · ${"%.1f".format(item.durationSeconds)}s", color = Color(0xFFBAE6FD), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }
                                                // Trim Handles (Left & Right)
                                                if (isCardSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newStart = (item.startSeconds + deltaSec).coerceAtLeast(0f)
                                                                    val newDur = (item.durationSeconds - deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedVoiceoverTracks().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(startSeconds = newStart, durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(voiceoverTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newDur = (item.durationSeconds + deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedVoiceoverTracks().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(voiceoverTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Track 6 Lane: Sound Effects (SFX) & Background Music (BGM)
                                    val soundTracks = currentProject.getResolvedSoundAudioTracks()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .background(Color(0x15FF4D94))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    val clickedSec = (offset.x / pixelsPerSecond.value).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(clickedSec))
                                                }
                                            }
                                    ) {
                                        soundTracks.forEach { item ->
                                            val isCardSelected = selectedTrackNum == 6 && selectedCardId == item.id
                                            val cardX = (item.startSeconds * pixelsPerSecond.value).dp
                                            val cardW = (item.durationSeconds * pixelsPerSecond.value).dp.coerceAtLeast(60.dp)

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = cardX)
                                                    .width(cardW)
                                                    .fillMaxHeight()
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        selectedTrackNum = 6
                                                        selectedCardId = item.id
                                                        currentProject = currentProject.copy(currentPositionSeconds = item.startSeconds)
                                                        activeToolTab = VideoStudioToolTab.TUNE
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (item.isSfx) Color(0xFF831843) else Color(0xFF4C0519),
                                                    border = if (isCardSelected) BorderStroke(2.dp, Color(0xFFFF4D94)) else BorderStroke(0.5.dp, Color(0x60FF4D94)),
                                                    shadowElevation = if (isCardSelected) 6.dp else 1.dp,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color(0xFFFF4D94)))
                                                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                            Text(item.title, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                            Text("${if (item.isSfx) "SFX" else "BGM"} · Vol ${(item.volume * 100).toInt()}% · ${"%.1f".format(item.durationSeconds)}s", color = Color(0xFFFBCFE8), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }
                                                // Trim Handles (Left & Right)
                                                if (isCardSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newStart = (item.startSeconds + deltaSec).coerceAtLeast(0f)
                                                                    val newDur = (item.durationSeconds - deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedSoundAudioTracks().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(startSeconds = newStart, durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(soundAudioTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                            .pointerInput(item.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                                    val newDur = (item.durationSeconds + deltaSec).coerceAtLeast(0.3f)
                                                                    val updated = currentProject.getResolvedSoundAudioTracks().toMutableList()
                                                                    val idx = updated.indexOfFirst { it.id == item.id }
                                                                    if (idx != -1) {
                                                                        updated[idx] = item.copy(durationSeconds = newDur)
                                                                        currentProject = currentProject.copy(soundAudioTracks = updated)
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.Black))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Draggable Playhead Needle with Magnetic Snapping
                                val playheadX = (currentProject.currentPositionSeconds * pixelsPerSecond.value).dp
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(2.dp)
                                        .offset(x = playheadX)
                                        .background(Color(0xFF00DAF3))
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val deltaSec = dragAmount.x / pixelsPerSecond.value
                                                    val rawSec = (currentProject.currentPositionSeconds + deltaSec).coerceIn(0f, safeTotalSeconds)
                                                    currentProject = currentProject.copy(currentPositionSeconds = calculateSnappedPosition(rawSec))
                                                }
                                            )
                                        }
                                ) {
                                    // Scrubber Needle Handle Head
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                                        color = Color(0xFF00DAF3),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .size(width = 10.dp, height = 12.dp)
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-2).dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }

            // --- Tool Drawer / Sub-panel (When active) ---
            AnimatedVisibility(
                visible = activeToolTab != VideoStudioToolTab.NONE,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                VideoStudioToolContainer(
                    activeTab = activeToolTab,
                    project = currentProject,
                    selectedTrackNumber = selectedTrackNum,
                    selectedCardId = selectedCardId,
                    onUpdateProject = { updated -> pushState(updated) },
                    onCloseTab = { activeToolTab = VideoStudioToolTab.NONE }
                )
            }

            // --- 6. Bottom Workspace Toolbar (64px) ---
            Surface(
                color = Color(0xFF121212).copy(alpha = 0.98f),
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val toolbarItems = listOf(
                        Pair(VideoStudioToolTab.TUNE, Pair("Tune", Icons.Default.Tune)),
                        Pair(VideoStudioToolTab.EDIT, Pair("Edit", Icons.Default.ContentCut)),
                        Pair(VideoStudioToolTab.AUDIO, Pair("Audio", Icons.Default.MusicNote)),
                        Pair(VideoStudioToolTab.TEXT, Pair("Text", Icons.Default.Title)),
                        Pair(VideoStudioToolTab.OVERLAY, Pair("Overlay", Icons.Default.Layers)),
                        Pair(VideoStudioToolTab.EFFECTS, Pair("Effects", Icons.Default.AutoAwesome)),
                        Pair(VideoStudioToolTab.FILTERS, Pair("Filters", Icons.Default.Palette)),
                        Pair(VideoStudioToolTab.ADJUST, Pair("Adjust", Icons.Default.Tune)),
                        Pair(VideoStudioToolTab.RATIO, Pair("Ratio", Icons.Default.AspectRatio)),
                        Pair(VideoStudioToolTab.PRO_TOOLS, Pair("Pro AI", Icons.Default.ElectricBolt))
                    )

                    toolbarItems.forEach { (tab, item) ->
                        val (label, icon) = item
                        val isActive = activeToolTab == tab
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    activeToolTab = if (activeToolTab == tab) VideoStudioToolTab.NONE else tab
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isActive) Color(0xFF00DAF3) else Color(0xFFCCC3D9),
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = if (isActive) Color.White else Color(0xFFCCC3D9),
                                fontSize = 10.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00DAF3))
                                )
                            }
                        }
                    }
                }
            }
        }

        // SubScreen Overlays:
        // 1. AI Director Chatting interface
        AnimatedVisibility(
            visible = activeSubView == VideoStudioSubView.AI_DIRECTOR_CHAT,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            VideoAiDirectorChatScreen(
                project = currentProject,
                onBack = { activeSubView = VideoStudioSubView.NONE },
                onApplyChanges = { updated ->
                    pushState(updated)
                }
            )
        }

        // 2. Export Studio interface
        AnimatedVisibility(
            visible = activeSubView == VideoStudioSubView.EXPORT_STUDIO,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            VideoExportStudioScreen(
                project = currentProject,
                onBack = { activeSubView = VideoStudioSubView.NONE }
            )
        }

        // 3. AI Video Scene Generator interface (Level 3 Pro)
        AnimatedVisibility(
            visible = activeSubView == VideoStudioSubView.AI_VIDEO_GENERATOR,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            VideoAiVideoGeneratorDialog(
                project = currentProject,
                onInsertClip = { newClip ->
                    val updatedClips = currentProject.clips.toMutableList().apply { add(newClip) }
                    val updated = currentProject.copy(
                        clips = updatedClips,
                        totalDurationSeconds = updatedClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                    )
                    pushState(updated)
                    activeSubView = VideoStudioSubView.NONE
                },
                onInsertVisualAsset = { newAsset ->
                    val updatedAssets = currentProject.getResolvedVisualAssets().toMutableList().apply { add(newAsset) }
                    val updated = currentProject.copy(visualAssetTracks = updatedAssets)
                    pushState(updated)
                    activeSubView = VideoStudioSubView.NONE
                },
                onDismiss = { activeSubView = VideoStudioSubView.NONE }
            )
        }

        // 3. Fullscreen Player Dialog
        if (showFullscreenPlayer) {
            VideoFullscreenPlayerDialog(
                project = currentProject,
                isPlaying = isPlaying,
                onTogglePlay = { isPlaying = !isPlaying },
                onSeek = { currentProject = currentProject.copy(currentPositionSeconds = it) },
                onReplay5s = {
                    currentProject = currentProject.copy(currentPositionSeconds = (currentProject.currentPositionSeconds - 5f).coerceAtLeast(0f))
                },
                onForward5s = {
                    currentProject = currentProject.copy(currentPositionSeconds = (currentProject.currentPositionSeconds + 5f).coerceAtMost(currentProject.totalDurationSeconds))
                },
                onClose = { showFullscreenPlayer = false }
            )
        }

        // 4. Resolution Quality Dialog
        if (showResolutionDialog) {
            VideoResolutionQualityDialog(
                project = currentProject,
                onDismiss = { showResolutionDialog = false },
                onConfirm = { res, fps ->
                    pushState(currentProject.copy(resolution = res, fps = fps))
                }
            )
        }

        // 5. SAIF AI EDITING Master Modal (Levels 5, 8, 10, 15, 18, 6)
        if (showSaifAiEditingModal) {
            SaifAiEditingModal(
                project = currentProject,
                onDismiss = { showSaifAiEditingModal = false },
                onApplyEdit = { updated ->
                    pushState(updated)
                    showSaifAiEditingModal = false
                }
            )
        }

        // 6. Floating AI Prompt Video Director Dialog
        if (showPromptDirectorDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { if (!isGeneratingFromPrompt) showPromptDirectorDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { if (!isGeneratingFromPrompt) showPromptDirectorDialog = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF181528),
                        border = BorderStroke(1.5.dp, Color(0xFF00DAF3)),
                        shadowElevation = 24.dp
                    ) {
                        var dialogPromptInput by remember { mutableStateOf("") }
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF7B2BFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("AI Prompt Video Director", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("Research topic & synthesize video", color = Color(0xFF00DAF3), fontSize = 11.sp)
                                    }
                                }
                                IconButton(
                                    onClick = { if (!isGeneratingFromPrompt) showPromptDirectorDialog = false },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x25FFFFFF))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = dialogPromptInput,
                                onValueChange = { dialogPromptInput = it },
                                placeholder = { Text("e.g. 5 AI tools jo 2026 me sab badal denge / YouTube growth secrets", color = Color.Gray, fontSize = 12.sp) },
                                enabled = !isGeneratingFromPrompt,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00DAF3),
                                    unfocusedBorderColor = Color(0x40FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF100D1E),
                                    unfocusedContainerColor = Color(0xFF100D1E)
                                ),
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "🔥 5 AI Tools in 2026",
                                    "📈 YouTube Growth Secret",
                                    "🧠 Quantum Computing",
                                    "💰 Crypto vs Stocks"
                                ).forEach { suggestion ->
                                    Surface(
                                        onClick = { dialogPromptInput = suggestion },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF261F3B),
                                        border = BorderStroke(0.5.dp, Color(0x5000DAF3))
                                    ) {
                                        Text(suggestion, color = Color(0xFFE2E8F0), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isGeneratingFromPrompt) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF231C38), RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00DAF3), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(promptGenerationStatus.ifBlank { "Analyzing prompt & researching..." }, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val p = dialogPromptInput.trim()
                                        if (p.isNotBlank()) {
                                            showPromptDirectorDialog = false
                                            handleGenerateFromPrompt(p)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Research & Generate Video", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
