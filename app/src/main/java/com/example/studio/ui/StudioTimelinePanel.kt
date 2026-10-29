package com.example.studio.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.StudioLane
import com.example.studio.model.TextItem
import com.example.studio.model.TimelineItem
import com.example.studio.model.TrackType
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import com.example.studio.render.LayerMath
import com.example.studio.state.MasterClock
import com.example.studio.state.StudioUiState
import kotlin.math.roundToInt

@Composable
fun StudioTimelinePanel(
    state: StudioUiState,
    clock: MasterClock,
    onSelectItem: (String?) -> Unit,
    onTrimItem: (String, Long, Long) -> Unit,
    onMoveItem: (String, Long, String?) -> Unit,
    onAddMediaToLane: (TrackType) -> Unit,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentTimeUs by clock.currentTimeUs.collectAsState()
    val isPlaying by clock.isPlaying.collectAsState()

    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Drag-and-drop state for long-press moving across layers and time
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragTotalDelta by remember { mutableStateOf(Offset.Zero) }
    var dragInitialStartUs by remember { mutableStateOf(0L) }
    var dragInitialLaneIndex by remember { mutableStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0D15))
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoomChange(state.zoomDpPerSec * zoom)
                    }
                }
            }
    ) {
        // 1. Sticky Left Lane Headers
        Column(
            modifier = Modifier
                .width(108.dp)
                .fillMaxHeight()
                .background(Color(0xFF13101C))
                .verticalScroll(vScrollState)
                .zIndex(5f)
        ) {
            // Ruler Placeholder Spacer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Color(0xFF1A1724)),
                contentAlignment = Alignment.Center
            ) {
                Text("TRACKS", color = Color(0xFF888496), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            state.project.lanes.forEach { lane ->
                LaneHeaderItem(
                    lane = lane,
                    onAddClick = { onAddMediaToLane(lane.trackType) }
                )
            }
        }

        // 2. CapCut Style Timeline Viewport with Center Vertical Playhead Line
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val viewportWidthPx = constraints.maxWidth.toFloat()
            val centerXPx = viewportWidthPx / 2f
            val halfViewportDp = (centerXPx / density).dp

            // Project content width
            val contentWidthPx = remember(state.project.totalDurationUs, state.zoomDpPerSec, density) {
                val durUs = (state.project.totalDurationUs + 4_000_000L).coerceAtLeast(10_000_000L)
                LayerMath.timeUsToPixel(durUs, state.zoomDpPerSec, density)
            }
            // Canvas has leading and trailing padding equal to centerXPx
            val totalCanvasWidthPx = centerXPx + contentWidthPx + centerXPx

            // Sync scroll with playhead during playback
            LaunchedEffect(currentTimeUs, isPlaying) {
                if (isPlaying) {
                    val targetScroll = LayerMath.timeUsToPixel(currentTimeUs, state.zoomDpPerSec, density).roundToInt()
                    if (kotlin.math.abs(hScrollState.value - targetScroll) > 4) {
                        hScrollState.scrollTo(targetScroll)
                    }
                }
            }

            // Scrubbing when user scrolls the timeline
            LaunchedEffect(hScrollState.value, hScrollState.isScrollInProgress) {
                if (hScrollState.isScrollInProgress && !isPlaying) {
                    val scrubbedTimeUs = LayerMath.pixelToTimeUs(hScrollState.value.toFloat(), state.zoomDpPerSec, density)
                    clock.seekTo(scrubbedTimeUs)
                }
            }

            // Scrollable Timeline Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScrollState)
            ) {
                Column(
                    modifier = Modifier
                        .width((totalCanvasWidthPx / density).dp)
                        .fillMaxHeight()
                        .verticalScroll(vScrollState)
                ) {
                    // Adaptive Ruler (00:00 begins at centerXPx)
                    AdaptiveRuler(
                        startPaddingPx = centerXPx,
                        contentWidthPx = contentWidthPx,
                        zoomDpPerSec = state.zoomDpPerSec,
                        density = density,
                        onScrub = { scrubTimeUs ->
                            clock.seekTo(scrubTimeUs)
                            val targetScroll = LayerMath.timeUsToPixel(scrubTimeUs, state.zoomDpPerSec, density).roundToInt()
                            coroutineScope.launch {
                                hScrollState.scrollTo(targetScroll)
                            }
                        }
                    )

                    // Lanes Content Rows
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    onSelectItem(null)
                                    val timeOffsetPx = offset.x - centerXPx
                                    if (timeOffsetPx >= 0) {
                                        val scrubTimeUs = LayerMath.pixelToTimeUs(timeOffsetPx, state.zoomDpPerSec, density)
                                        clock.seekTo(scrubTimeUs)
                                    }
                                }
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            state.project.lanes.forEachIndexed { laneIndex, lane ->
                                val laneItems = state.project.items.filter { it.laneId == lane.id }
                                LaneCanvasRow(
                                    lane = lane,
                                    laneIndex = laneIndex,
                                    items = laneItems,
                                    selectedItemId = state.selectedItemId,
                                    draggingItemId = draggingItemId,
                                    startPaddingPx = centerXPx,
                                    zoomDpPerSec = state.zoomDpPerSec,
                                    density = density,
                                    onSelectItem = onSelectItem,
                                    onStartDrag = { itemId, startUs, laneId ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingItemId = itemId
                                        dragInitialStartUs = startUs
                                        dragInitialLaneIndex = state.project.lanes.indexOfFirst { it.id == laneId }.coerceAtLeast(0)
                                        dragTotalDelta = Offset.Zero
                                    },
                                    onDragDelta = { delta ->
                                        dragTotalDelta += delta
                                    },
                                    onEndDrag = {
                                        val currentDragged = state.project.items.firstOrNull { it.id == draggingItemId }
                                        if (currentDragged != null) {
                                            val deltaUs = LayerMath.pixelToTimeUs(dragTotalDelta.x, state.zoomDpPerSec, density)
                                            val finalStartUs = (dragInitialStartUs + deltaUs).coerceAtLeast(0L)
                                            val laneHeightPx = 48f * density
                                            val laneShift = (dragTotalDelta.y / laneHeightPx).roundToInt()
                                            val finalLaneIdx = (dragInitialLaneIndex + laneShift).coerceIn(0, state.project.lanes.lastIndex)
                                            val targetLane = state.project.lanes[finalLaneIdx]

                                            onMoveItem(currentDragged.id, finalStartUs, targetLane.id)
                                            onSelectItem(currentDragged.id)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Toast.makeText(context, "${currentDragged.label} moved to ${targetLane.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        draggingItemId = null
                                        dragTotalDelta = Offset.Zero
                                    },
                                    onCancelDrag = {
                                        draggingItemId = null
                                        dragTotalDelta = Offset.Zero
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // --- Floating Card Layer when Dragging (Moves freely across lanes and time) ---
            if (draggingItemId != null) {
                val draggedItem = state.project.items.firstOrNull { it.id == draggingItemId }
                if (draggedItem != null) {
                    val deltaUs = LayerMath.pixelToTimeUs(dragTotalDelta.x, state.zoomDpPerSec, density)
                    val liveStartUs = (dragInitialStartUs + deltaUs).coerceAtLeast(0L)
                    val laneHeightPx = 48f * density
                    val laneShift = (dragTotalDelta.y / laneHeightPx).roundToInt()
                    val liveLaneIdx = (dragInitialLaneIndex + laneShift).coerceIn(0, state.project.lanes.lastIndex)
                    val liveTargetLane = state.project.lanes[liveLaneIdx]

                    val floatingXPx = centerXPx + LayerMath.timeUsToPixel(liveStartUs, state.zoomDpPerSec, density) - hScrollState.value
                    val floatingYPx = 30f * density + liveLaneIdx * (48f * density)
                    val itemWidthPx = LayerMath.timeUsToPixel(draggedItem.durationUs, state.zoomDpPerSec, density).coerceAtLeast(16f * density)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(floatingXPx.roundToInt(), floatingYPx.roundToInt()) }
                            .size((itemWidthPx / density).dp, 44.dp)
                            .zIndex(25f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(liveTargetLane.trackType.colorHex).copy(alpha = 0.95f),
                            border = BorderStroke(2.dp, Color(0xFF00E5FF)),
                            shadowElevation = 14.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getItemIcon(draggedItem),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = draggedItem.label,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${liveTargetLane.name} • ${formatUs(liveStartUs)}",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. FIXED VERTICAL CENTER LINE (CapCut Style Khadi Line) ---
            // Pure white vertical indicator line running straight down the middle of the timeline
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.5.dp)
                    .fillMaxHeight()
                    .background(Color.White)
                    .zIndex(15f)
            )

            // Top Playhead Needle Head (Over the Ruler)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 16.dp, height = 24.dp)
                    .zIndex(16f),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(width = 14.dp, height = 18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F0D15))
                        )
                    }
                }
            }

            // Bottom subtle white foot anchor
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 10.dp, height = 5.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Color.White)
                    .zIndex(16f)
            )
        }
    }
}

@Composable
private fun LaneHeaderItem(
    lane: StudioLane,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(lane.heightDp.dp)
            .background(Color(0xFF13101C))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(lane.trackType.colorHex))
        )
        Text(
            text = lane.name,
            color = Color(0xFFCCC3D9),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF262330))
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to lane",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun AdaptiveRuler(
    startPaddingPx: Float,
    contentWidthPx: Float,
    zoomDpPerSec: Float,
    density: Float,
    onScrub: (Long) -> Unit
) {
    val intervals = remember(zoomDpPerSec) { LayerMath.calculateRulerIntervals(zoomDpPerSec) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Color(0xFF1A1724))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val scrubX = offset.x - startPaddingPx
                    if (scrubX >= 0) {
                        val scrubTimeUs = LayerMath.pixelToTimeUs(scrubX, zoomDpPerSec, density)
                        onScrub(scrubTimeUs)
                    }
                }
            }
    ) {
        val majorStepPx = LayerMath.timeUsToPixel(intervals.majorStepUs, zoomDpPerSec, density)
        val minorStepPx = LayerMath.timeUsToPixel(intervals.minorStepUs, zoomDpPerSec, density)

        // Draw 00:00:00 anchor tick
        drawLine(
            color = Color.White,
            start = Offset(startPaddingPx, 0f),
            end = Offset(startPaddingPx, size.height),
            strokeWidth = 2f
        )

        var x = startPaddingPx
        var timeUs = 0L
        while (x < startPaddingPx + contentWidthPx && x < size.width) {
            // Draw major tick
            drawLine(
                color = Color(0xFF888496),
                start = Offset(x, size.height * 0.4f),
                end = Offset(x, size.height),
                strokeWidth = 1.5f
            )

            // Draw minor ticks inside
            var mx = x + minorStepPx
            while (mx < x + majorStepPx && mx < startPaddingPx + contentWidthPx && mx < size.width) {
                drawLine(
                    color = Color(0xFF4A4458),
                    start = Offset(mx, size.height * 0.7f),
                    end = Offset(mx, size.height),
                    strokeWidth = 1f
                )
                mx += minorStepPx
            }

            x += majorStepPx
            timeUs += intervals.majorStepUs
        }
    }
}

@Composable
private fun LaneCanvasRow(
    lane: StudioLane,
    laneIndex: Int,
    items: List<TimelineItem>,
    selectedItemId: String?,
    draggingItemId: String?,
    startPaddingPx: Float,
    zoomDpPerSec: Float,
    density: Float,
    onSelectItem: (String) -> Unit,
    onStartDrag: (String, Long, String) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onEndDrag: () -> Unit,
    onCancelDrag: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(lane.heightDp.dp)
            .background(Color(0xFF0F0D15))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Lane horizontal divider
            drawLine(
                color = Color(0xFF1E1B28),
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1f
            )

            // Draw items on lane
            items.forEach { item ->
                val startX = startPaddingPx + LayerMath.timeUsToPixel(item.startUs, zoomDpPerSec, density)
                val widthPx = LayerMath.timeUsToPixel(item.durationUs, zoomDpPerSec, density).coerceAtLeast(12f * density)
                val isSelected = item.id == selectedItemId
                val isBeingDragged = item.id == draggingItemId

                // Background Card
                drawRoundRect(
                    color = Color(lane.trackType.colorHex).copy(alpha = if (isBeingDragged) 0.3f else if (isSelected) 0.95f else 0.8f),
                    topLeft = Offset(startX, 4f * density),
                    size = Size(widthPx, size.height - (8f * density)),
                    cornerRadius = CornerRadius(6f * density, 6f * density)
                )

                // Selection Border
                if (isSelected && !isBeingDragged) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(startX, 4f * density),
                        size = Size(widthPx, size.height - (8f * density)),
                        cornerRadius = CornerRadius(6f * density, 6f * density),
                        style = Stroke(width = 2f * density)
                    )
                }
            }
        }

        // Interactive touch interceptors for cards (Tap to Select + Long-Press to Drag & Move)
        items.forEach { item ->
            val startX = startPaddingPx + LayerMath.timeUsToPixel(item.startUs, zoomDpPerSec, density)
            val widthPx = LayerMath.timeUsToPixel(item.durationUs, zoomDpPerSec, density).coerceAtLeast(12f * density)
            val isBeingDragged = item.id == draggingItemId

            if (!isBeingDragged) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(startX.roundToInt(), 0) }
                        .size((widthPx / density).dp, lane.heightDp.dp)
                        .pointerInput(item.id) {
                            detectTapAndDragAfterLongPress(
                                onTap = { onSelectItem(item.id) },
                                onDragStart = { offset ->
                                    onStartDrag(item.id, item.startUs, item.laneId)
                                },
                                onDrag = { delta ->
                                    onDragDelta(delta)
                                },
                                onDragEnd = onEndDrag,
                                onDragCancel = onCancelDrag
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = getItemIcon(item),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Robust gesture detector that cleanly differentiates between a quick Tap and Long-Press Drag.
 */
private suspend fun PointerInputScope.detectTapAndDragAfterLongPress(
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var isLongPress = false
        try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        return@withTimeout
                    }
                }
            }
        } catch (e: PointerEventTimeoutCancellationException) {
            isLongPress = true
        }

        if (isLongPress) {
            onDragStart(down.position)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    onDragEnd()
                    break
                }
                val dragAmount = change.positionChange()
                change.consume()
                onDrag(dragAmount)
            }
        } else {
            onTap()
        }
    }
}

private fun getItemIcon(item: TimelineItem): androidx.compose.ui.graphics.vector.ImageVector = when (item) {
    is VideoItem -> Icons.Default.Videocam
    is ImageItem -> Icons.Default.Image
    is TextItem -> Icons.Default.TextFields
    is VoiceItem -> Icons.Default.Mic
    is AudioItem -> Icons.Default.Audiotrack
    is CodeSceneItem -> Icons.Default.Code
}

private fun formatUs(us: Long): String {
    val sec = us / 1_000_000f
    val minutes = (sec / 60).toInt()
    val seconds = sec % 60
    return String.format(java.util.Locale.US, "%02d:%04.1f", minutes, seconds)
}
