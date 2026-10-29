package com.example.studio.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.studio.ai.AiProfile
import com.example.studio.model.TrackType
import com.example.studio.model.VoiceItem
import com.example.studio.state.StudioAction
import com.example.studio.state.StudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

data class VoiceProfile(
    val id: String,
    val name: String,
    val tone: String,
    val description: String
)

@Composable
fun StudioVoiceoverModal(
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiProfile = remember { AiProfile.fromSettings(context) }
    val voiceLaneId = remember {
        viewModel.uiState.value.project.lanes.firstOrNull { it.trackType == TrackType.VOICE }?.id ?: "lane_voice_1"
    }

    var scriptText by remember {
        mutableStateOf("Every frame tells a story. Step into the future of creative neural video production.")
    }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var audioDucking by remember { mutableStateOf(true) }

    val voices = listOf(
        VoiceProfile("fenrir", "Fenrir", "Cinematic Deep", "Deep resonant voice for movie trailers and teasers"),
        VoiceProfile("nova", "Nova", "Energetic & Bold", "Punchy modern voice for reels and shorts"),
        VoiceProfile("shimmer", "Shimmer", "Warm Storyteller", "Gentle, emotional tone for documentary & tutorials"),
        VoiceProfile("alloy", "Alloy", "Crisp Tech", "Balanced and neutral tech narrator"),
        VoiceProfile("aarav", "Aarav", "Hindi / Hinglish", "Natural Hindi flow for viral Indian creators")
    )

    var selectedVoice by remember { mutableStateOf(voices[0]) }
    var isSynthesizing by remember { mutableStateOf(false) }

    // Dynamic word count & Level 3 duration formula
    val wordCount = remember(scriptText) {
        val trimmed = scriptText.trim()
        if (trimmed.isEmpty()) 0 else trimmed.split("\\s+".toRegex()).size
    }
    val calculatedDurationSec = remember(wordCount, speechRate) {
        if (wordCount == 0) 2.0f
        else ((wordCount * 0.38f) / speechRate).coerceIn(1.5f, 30.0f)
    }

    Dialog(
        onDismissRequest = { if (!isSynthesizing) onDismiss() },
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
                border = BorderStroke(1.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f))
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
                                        Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("AI Voiceover Studio", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Neural TTS • ${aiProfile.provider}",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { if (!isSynthesizing) onDismiss() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCC3D9))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice Character Selection
                    Text("Select Voice Character", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voices.forEach { voice ->
                            val isSelected = selectedVoice.id == voice.id
                            Surface(
                                onClick = { selectedVoice = voice },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.25f) else Color(0xFF1C182A),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF8B5CF6) else Color(0xFF2E2A3E))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = "🎙️ ${voice.name}",
                                        color = if (isSelected) Color(0xFFA78BFA) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = voice.tone,
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Script Text Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voiceover Script", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "$wordCount words • est. ${"%.1f".format(calculatedDurationSec)}s",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = scriptText,
                        onValueChange = { scriptText = it },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF2E2A3E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0D0B14),
                            unfocusedContainerColor = Color(0xFF0D0B14)
                        ),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speech Rate", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("${"%.2f".format(speechRate)}x", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = { speechRate = it },
                        valueRange = 0.75f..1.5f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8B5CF6),
                            activeTrackColor = Color(0xFF8B5CF6),
                            inactiveTrackColor = Color(0xFF2E2A3E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto Audio Ducking Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C182A))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto Audio Ducking", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Ducks BGM volume during speech", color = Color(0xFF9CA3AF), fontSize = 10.sp)
                        }
                        Switch(
                            checked = audioDucking,
                            onCheckedChange = { audioDucking = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF8B5CF6)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Generate & Insert Button
                    Button(
                        onClick = {
                            isSynthesizing = true
                            scope.launch {
                                delay(600)
                                val currentPlayheadUs = viewModel.clock.currentTimeUs.value
                                val durationUs = (calculatedDurationSec * 1_000_000L).toLong()

                                val voiceItem = VoiceItem(
                                    id = "voice_${UUID.randomUUID().toString().take(8)}",
                                    laneId = voiceLaneId,
                                    startUs = currentPlayheadUs,
                                    durationUs = durationUs,
                                    text = scriptText.trim(),
                                    speakerTone = selectedVoice.name,
                                    speed = speechRate,
                                    audioDucking = audioDucking,
                                    label = "🎙️ AI_${selectedVoice.name}",
                                    origin = "ai_tts",
                                    aiWhy = "Neural TTS narration with ${selectedVoice.name}"
                                )

                                viewModel.commit(StudioAction.AddItem(voiceItem))
                                viewModel.selectItem(voiceItem.id)
                                isSynthesizing = false
                                onDismiss()
                            }
                        },
                        enabled = !isSynthesizing && scriptText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isSynthesizing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesizing Neural Speech...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Insert Voiceover to Timeline", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
