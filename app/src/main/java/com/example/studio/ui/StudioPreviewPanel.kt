package com.example.studio.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.studio.render.StudioFrameRenderer
import androidx.media3.ui.PlayerView
import com.example.studio.model.ItemTransform
import com.example.studio.model.TextItem
import com.example.studio.model.VideoItem
import com.example.studio.render.RenderPlan
import com.example.studio.state.MasterClock
import com.example.studio.state.StudioUiState
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun StudioPreviewPanel(
    state: StudioUiState,
    clock: MasterClock,
    onHeightPercentChange: (Float) -> Unit,
    onToggleFocus: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleSafeZone: () -> Unit,
    onToggleThirds: () -> Unit,
    onTransformChange: ((ItemTransform) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTimeUs by clock.currentTimeUs.collectAsState()
    val isPlaying by clock.isPlaying.collectAsState()

    val renderPlan = remember(state.project, currentTimeUs) {
        RenderPlan.at(state.project, currentTimeUs)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val activeVideo = renderPlan.activeVideoItem as? VideoItem
    LaunchedEffect(activeVideo?.mediaUri) {
        if (activeVideo != null && activeVideo.mediaUri.isNotBlank()) {
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(activeVideo.mediaUri))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            } catch (e: Exception) {
                // Ignore uri parse errors
            }
        } else {
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(isPlaying, currentTimeUs) {
        if (activeVideo != null) {
            val offsetMs = (renderPlan.visibleLayers.firstOrNull { it.item.id == activeVideo.id }?.localTimeUs ?: 0L) / 1000L
            if (kotlin.math.abs(exoPlayer.currentPosition - offsetMs) > 100) {
                exoPlayer.seekTo(offsetMs)
            }
            if (isPlaying && !exoPlayer.isPlaying) {
                exoPlayer.play()
            } else if (!isPlaying && exoPlayer.isPlaying) {
                exoPlayer.pause()
            }
        } else {
            if (exoPlayer.isPlaying) exoPlayer.pause()
        }
    }

    val aspectMultiplier = when (state.project.aspect) {
        "16:9" -> 16f / 9f
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        else -> 9f / 16f // 9:16 default
    }

    val selectedItem = state.project.items.firstOrNull { it.id == state.selectedItemId }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF09080E))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .aspectRatio(aspectMultiplier)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // 1. Video Layer (ExoPlayer)
                if (activeVideo != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { pv ->
                            // Library b-roll fills the frame like in the export; user footage keeps its proportions.
                            pv.resizeMode = if (StudioFrameRenderer.isBackdrop(activeVideo.aiWhy)) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF14121E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.project.items.isEmpty()) "No media on timeline\nTap + to import" else "Preview",
                            color = Color(0xFF6B6579),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // 1b. Director scenes + image layers + synchronized audio from the real timeline
                StudioSceneLayers(renderPlan)
                StudioAudioLayers(renderPlan, isPlaying)

                // 2. Overlays (Safe Zone, Grid)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    if (state.isSafeZoneEnabled) {
                        // Shorts / Reels / TikTok: the app UI covers the top bar, the caption + buttons at the bottom and the right rail.
                        val shade = Color(0x55FF4D6D)
                        drawRect(shade, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, h * 0.12f))
                        drawRect(shade, Offset(0f, h * 0.78f), androidx.compose.ui.geometry.Size(w, h * 0.22f))
                        drawRect(shade, Offset(w * 0.86f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.66f))
                        drawRect(
                            color = Color(0xAA00E5FF),
                            topLeft = Offset(w * 0.04f, h * 0.12f),
                            size = androidx.compose.ui.geometry.Size(w * 0.82f, h * 0.66f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    if (state.isThirdsGridEnabled) {
                        val stroke = Stroke(width = 1.dp.toPx())
                        val gridColor = Color(0x33FFFFFF)
                        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke.width)
                        drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), stroke.width)
                        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), stroke.width)
                        drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), stroke.width)
                    }
                }

                // Captions are drawn by the shared renderer in StudioSceneLayers (identical to the exported video).

                // 3. On-Canvas Interactive Gizmo (Drag to Move, Corners to Scale, Top to Rotate)
                if (selectedItem != null && onTransformChange != null) {
                    val transform = selectedItem.transform
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(transform.x.roundToInt(), transform.y.roundToInt()) }
                            .size(140.dp * transform.scale)
                            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                            .pointerInput(selectedItem.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val updated = transform.copy(
                                        x = transform.x + dragAmount.x,
                                        y = transform.y + dragAmount.y
                                    )
                                    onTransformChange(updated)
                                }
                            }
                    ) {
                        // Top Rotation Handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-18).dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6))
                                .pointerInput(selectedItem.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val newRot = (transform.rotation + dragAmount.x * 0.8f).coerceIn(-180f, 180f)
                                        onTransformChange(transform.copy(rotation = newRot))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(14.dp))
                        }

                        // Bottom-Right Scale Handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                                .pointerInput(selectedItem.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaScale = (dragAmount.x + dragAmount.y) / 100f
                                        val newScale = (transform.scale + deltaScale).coerceIn(0.2f, 3.5f)
                                        onTransformChange(transform.copy(scale = newScale))
                                    }
                                }
                        )
                    }
                }

                // Preview Controls Overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0x88000000), CircleShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(onClick = onToggleSafeZone, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Safe Zone",
                            tint = if (state.isSafeZoneEnabled) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = onToggleThirds, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Grid",
                            tint = if (state.isThirdsGridEnabled) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = onToggleFocus, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Focus Mode",
                            tint = if (state.isFocusMode) Color(0xFF8B5CF6) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Preview Resize Drag Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Color(0xFF13101C))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val deltaPercent = dragAmount / 1200f
                        onHeightPercentChange(state.previewHeightPercent + deltaPercent)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A4458))
            )
        }
    }
}
