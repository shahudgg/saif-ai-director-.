package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun VideoFullscreenPlayerDialog(
    project: VideoProjectData,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onReplay5s: () -> Unit,
    onForward5s: () -> Unit,
    onClose: () -> Unit
) {
    val activeClip = project.clips.getOrNull(project.selectedClipIndex) ?: project.clips.firstOrNull()

    fun formatSeconds(sec: Float): String {
        val totalSecs = sec.toInt()
        val minutes = totalSecs / 60
        val remainingSecs = totalSecs % 60
        val millis = ((sec - totalSecs) * 100).toInt()
        return "%02d:%02d:%02d".format(minutes, remainingSecs, millis)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Main video viewport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                if (activeClip?.uri != null) {
                    AsyncImage(
                        model = activeClip.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0F0C20), Color(0xFF1E1035), Color(0xFF050B14))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF00DAF3),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = project.textOverlay,
                                color = try { Color(android.graphics.Color.parseColor(project.textColorHex)) } catch (e: Exception) { Color.Cyan },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Effect: ${project.effectOverlay}",
                                color = Color(0xFF00DAF3),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Text overlay badge on screen
                if (project.textOverlay.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 50.dp)
                    ) {
                        Text(
                            text = project.textOverlay,
                            color = try { Color(android.graphics.Color.parseColor(project.textColorHex)) } catch (e: Exception) { Color.Cyan },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Top Bar with Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${project.resolution} · ${project.fps} · ${project.aspectRatio}",
                        color = Color(0xFF00DAF3),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Bottom Transport Controls Overlay
            Surface(
                color = Color(0xFF100F14).copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Time and Scrubber
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatSeconds(project.currentPositionSeconds), color = Color(0xFF00DAF3), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(formatSeconds(project.totalDurationSeconds), color = Color(0xFFCCC3D9), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Slider(
                        value = project.currentPositionSeconds.coerceIn(0f, project.totalDurationSeconds),
                        onValueChange = onSeek,
                        valueRange = 0f..project.totalDurationSeconds.coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00DAF3),
                            activeTrackColor = Color(0xFF00DAF3),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Transport Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onReplay5s, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Replay5, contentDescription = "-5s", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF7B2BFF), Color(0xFF00DAF3))))
                                .clickable { onTogglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(onClick = onForward5s, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Forward5, contentDescription = "+5s", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
