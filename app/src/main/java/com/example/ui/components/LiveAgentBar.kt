package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveAgentBar(
    isGenerating: Boolean,
    currentAction: String,
    elapsedSeconds: Int,
    currentTaskIndex: Int,
    totalTasks: Int,
    liveCodeSnippet: String = "",
    onStop: () -> Unit,
    onRunInBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isGenerating,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val purpleAi = Color(0xFFA855F7)
        val cyanSky = Color(0xFF38BDF8)
        val runGreen = Color(0xFF10B981)

        val progressFrac = if (totalTasks > 0) {
            (currentTaskIndex.toFloat() / totalTasks.toFloat()).coerceIn(0.05f, 1f)
        } else 0.1f

        val infiniteTransition = rememberInfiniteTransition(label = "agent_bar_pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = purpleAi),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFA0F172A),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        purpleAi.copy(alpha = pulseAlpha),
                        cyanSky.copy(alpha = pulseAlpha),
                        purpleAi.copy(alpha = pulseAlpha)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Top row: Action spinner + Title + Timer + Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = cyanSky,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentAction.ifBlank { "Architecting and synthesizing application..." },
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val timeStr = if (elapsedSeconds < 60) "${elapsedSeconds}s" else "${elapsedSeconds / 60}m ${elapsedSeconds % 60}s"
                            Text(
                                text = "Elapsed: $timeStr",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            if (totalTasks > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• Task $currentTaskIndex of $totalTasks",
                                    color = cyanSky,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Background Build Button
                    OutlinedButton(
                        onClick = onRunInBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Background", color = Color(0xFFCBD5E1), fontSize = 10.5.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Stop Button
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Linear task progress bar
                LinearProgressIndicator(
                    progress = { progressFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = cyanSky,
                    trackColor = Color(0xFF1E293B)
                )

                // Optional live code ticker (last 2-3 lines being written)
                if (liveCodeSnippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF070B14),
                        border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = liveCodeSnippet.lines().takeLast(2).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8).copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
