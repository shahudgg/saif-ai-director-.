package com.example.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.render.LayerMath
import com.example.studio.state.MasterClock
import com.example.studio.state.StudioUiState

@Composable
fun StudioTransportBar(
    state: StudioUiState,
    clock: MasterClock,
    onToggleProxyLive: () -> Unit
) {
    val currentTimeUs by clock.currentTimeUs.collectAsState()
    val isPlaying by clock.isPlaying.collectAsState()
    val speed by clock.speed.collectAsState()
    val isLooping by clock.isLooping.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF13101C))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Exact Timestamp Display
        Text(
            text = "${LayerMath.formatTimeDisplay(currentTimeUs)} / ${LayerMath.formatTimeDisplay(state.project.totalDurationUs)}",
            color = Color(0xFFCCC3D9),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )

        // Transport Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Jump Start
            IconButton(onClick = { clock.jumpStart() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FirstPage, contentDescription = "Jump Start", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Step -1s
            IconButton(onClick = { clock.stepSeconds(-1f) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FastRewind, contentDescription = "-1s", tint = Color(0xFFAEC6FF), modifier = Modifier.size(16.dp))
            }

            // Play / Pause Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6))
                    .clickable { clock.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Step +1s
            IconButton(onClick = { clock.stepSeconds(1f) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FastForward, contentDescription = "+1s", tint = Color(0xFFAEC6FF), modifier = Modifier.size(16.dp))
            }

            // Jump End
            IconButton(onClick = { clock.jumpEnd() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.LastPage, contentDescription = "Jump End", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Secondary Toggles: Loop, Speed, Proxy/Live Chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Loop Toggle
            IconButton(onClick = { clock.toggleLoop() }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (isLooping) Color(0xFF00E5FF) else Color(0xFF6B6579),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Speed Selector
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF262330))
                    .clickable {
                        val nextSpeed = when (speed) {
                            0.5f -> 1.0f
                            1.0f -> 2.0f
                            else -> 0.5f
                        }
                        clock.setSpeed(nextSpeed)
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${speed}x",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Proxy / Live Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (state.previewMode == "LIVE") Color(0xFF2E1A47) else Color(0xFF1F2937))
                    .clickable { onToggleProxyLive() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = state.previewMode,
                    color = if (state.previewMode == "LIVE") Color(0xFFD8B4FE) else Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
