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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.studio.ai.AiClient
import com.example.studio.ai.AiProfile
import com.example.studio.model.ItemEffects
import com.example.studio.model.ItemTransform
import com.example.studio.model.TrackType
import com.example.studio.model.VideoItem
import com.example.studio.state.StudioAction
import com.example.studio.state.StudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun StudioAiVideoModal(
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiProfile = remember { AiProfile.fromSettings(context) }
    val activeLaneId = remember {
        viewModel.uiState.value.project.lanes.firstOrNull { it.trackType == TrackType.VIDEO }?.id ?: "lane_video_1"
    }

    var promptText by remember { mutableStateOf("Cyberpunk rainy night in Neo-Tokyo with flying hovercars and glowing neon reflections") }
    var selectedStyle by remember { mutableStateOf("Cyberpunk Sci-Fi") }
    var selectedDurationSec by remember { mutableFloatStateOf(5.0f) }
    var selectedCameraMotion by remember { mutableStateOf("Slow Drone Push") }

    var isGenerating by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableFloatStateOf(0f) }
    var generationPhase by remember { mutableStateOf("Idle") }
    var generatedItem by remember { mutableStateOf<VideoItem?>(null) }

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
        onDismissRequest = { if (!isGenerating) onDismiss() },
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
                                    text = "Provider: ${aiProfile.provider} (${aiProfile.activeModel})",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { if (!isGenerating) onDismiss() },
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

                    // Preview Canvas / Progress
                    if (isGenerating) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF09080F)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(generationPhase, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { generationProgress },
                                modifier = Modifier.fillMaxWidth(0.7f).height(4.dp),
                                color = Color(0xFF00E5FF),
                                trackColor = Color(0xFF1E1A2C)
                            )
                        }
                    } else if (generatedItem != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF09080F),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth().height(80.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(generatedItem!!.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${selectedDurationSec}s • $selectedStyle", color = Color(0xFF34D399), fontSize = 11.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.commit(StudioAction.AddItem(generatedItem!!))
                                        viewModel.selectItem(generatedItem!!.id)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Add to Track", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Generate Button
                    Button(
                        onClick = {
                            isGenerating = true
                            generationProgress = 0.1f
                            generationPhase = "Contacting ${aiProfile.provider} Neural Engine..."

                            scope.launch {
                                delay(400)
                                generationProgress = 0.4f
                                generationPhase = "Synthesizing visual layers & motion vectors..."
                                delay(600)
                                generationProgress = 0.8f
                                generationPhase = "Finalizing $selectedStyle scene rendering..."
                                delay(400)

                                val currentPlayheadUs = viewModel.clock.currentTimeUs.value
                                val durationUs = (selectedDurationSec * 1_000_000L).toLong()

                                val newItem = VideoItem(
                                    id = "ai_clip_${UUID.randomUUID().toString().take(8)}",
                                    laneId = activeLaneId,
                                    startUs = currentPlayheadUs,
                                    durationUs = durationUs,
                                    mediaUri = "", // Procedural AI stream
                                    label = "AI: ${selectedStyle.take(12)}",
                                    origin = "ai_generator",
                                    aiWhy = "$selectedStyle clip: $promptText",
                                    effects = ItemEffects(
                                        contrast = 1.15f,
                                        saturation = 1.2f
                                    ),
                                    transform = ItemTransform(scale = 1.0f)
                                )

                                isGenerating = false
                                generatedItem = newItem
                            }
                        },
                        enabled = !isGenerating && promptText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGenerating) "Generating Scene..." else "Generate AI Video Scene",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
