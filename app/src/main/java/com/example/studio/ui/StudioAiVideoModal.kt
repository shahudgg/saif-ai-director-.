package com.example.studio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.studio.ai.AiClient
import com.example.studio.state.StudioViewModel

@Composable
fun StudioAiVideoModal(
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {

    var promptText by remember { mutableStateOf("Cyberpunk rainy night in Neo-Tokyo with flying hovercars and glowing neon reflections") }
    var selectedStyle by remember { mutableStateOf("Cyberpunk Sci-Fi") }
    var selectedDurationSec by remember { mutableFloatStateOf(5.0f) }
    var selectedCameraMotion by remember { mutableStateOf("Slow Drone Push") }


    val styles = listOf(
        "Cyberpunk Sci-Fi",
        "Cinematic 35mm Film",
        "Anime / Ghibli",
        "Photorealistic 4K",
        "3D Octane Render",
        "Retro Synthwave 80s"
    )

    val cameraMotions = listOf(
        "Slow Drone Push",
        "Orbital Pan 360",
        "Dynamic Whip Zoom",
        "Low Angle Hero",
        "Static Cinematic"
    )

    val promptSuggestions = listOf(
        "Cyberpunk Neon Drift",
        "Cinematic Mountain Drone Lake",
        "Futuristic AI Hologram Studio",
        "Lo-Fi Anime Sunset Skyline"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ai_clip_preview")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientShift"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF13101D),
                border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
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
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF00E5FF)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("AI Video Scene Generator", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Style preview • no video generator connected",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCC3D9))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Prompt Input
                    Text("Prompt / Scene Description", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF2E2A3E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0D0B14),
                            unfocusedContainerColor = Color(0xFF0D0B14)
                        ),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Suggestion Chips
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        promptSuggestions.forEach { suggestion ->
                            Surface(
                                onClick = { promptText = suggestion },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E1A2C),
                                border = BorderStroke(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                            ) {
                                Text(suggestion, color = Color(0xFFCCC3D9), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Style Switcher
                    Text("Visual Style", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        styles.forEach { style ->
                            val isSelected = selectedStyle == style
                            Surface(
                                onClick = { selectedStyle = style },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF1C182A),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF2E2A3E))
                            ) {
                                Text(
                                    text = style,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Duration & Camera Motion in Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Duration", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(3.0f, 5.0f, 10.0f).forEach { dur ->
                                    val isSelected = selectedDurationSec == dur
                                    Surface(
                                        onClick = { selectedDurationSec = dur },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1C182A),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${dur.toInt()}s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Camera Motion", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1C182A),
                                border = BorderStroke(1.dp, Color(0xFF2E2A3E)),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(selectedCameraMotion, color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("This panel is a style preview only. It is not connected to a video generator and will not create a clip. Use SAIF Director to build a Canvas-based video.",
                        color = Color(0xFFCCC3D9), fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Video generation unavailable here")
                    }
                }
            }
        }
    }
}
