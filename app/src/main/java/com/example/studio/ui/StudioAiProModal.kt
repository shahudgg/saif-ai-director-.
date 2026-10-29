package com.example.studio.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.state.StudioAction
import com.example.studio.state.StudioViewModel
import com.example.ui.components.SaifAiEditingEngine

/**
 * Level 6: Studio AI Pro Intelligence Modal.
 * Offers AI Silence Auto-Cut, Auto-Captions, Smart Audio Ducking, and Auto-Reframe
 * for multi-lane studio timelines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioAiProModal(
    viewModel: StudioViewModel,
    onDismiss: () -> Unit,
    onOpenWorkflow: (com.example.studio.director.DirectorWorkflow) -> Unit = {}
) {
    var aiMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.project

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0C1B),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(44.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = Color(0xFFFFC107).copy(alpha = 0.5f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7B2BFF).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("SAIF AI INTELLIGENCE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0x3500E5FF)) {
                                Text("PRO", color = Color(0xFF00E5FF), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Text("Autonomous director, silence cuts & smart audio", color = Color(0xFF9E95AC), fontSize = 11.sp)
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCC3D9))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SaifModeSwitch(aiMode = aiMode, onChange = { aiMode = it })
            Spacer(modifier = Modifier.height(12.dp))
            if (aiMode) {
                SaifAiWorkflowList(onOpenWorkflow = { wf -> onDismiss(); onOpenWorkflow(wf) })
            } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    StudioProToolCard(
                        title = "AI Auto-Cut (Silence Remover)",
                        desc = "Scans waveform, detects silent gaps (>1.2s), and ripple-deletes dead space across video & audio tracks.",
                        icon = Icons.Default.ContentCut,
                        badge = "RIPPLE CUT",
                        accent = Color(0xFF00E5FF),
                        onClick = {
                            val updated = SaifAiEditingEngine.applySilenceAutoCutToStudioProject(project)
                            viewModel.commit(StudioAction.ReplaceProject(updated))
                            Toast.makeText(context, "✂️ Removed silent pauses from timeline!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }

                item {
                    StudioProToolCard(
                        title = "AI Auto-Captions (Speech-to-Text)",
                        desc = "Generates synchronized subtitle TextItems on lane_text_1 with Hormozi Neon styling and karaoke timing.",
                        icon = Icons.Default.Subtitles,
                        badge = "AUTO SUBTITLES",
                        accent = Color(0xFFA78BFA),
                        onClick = {
                            val updated = SaifAiEditingEngine.generateStudioAutoCaptions(project)
                            viewModel.commit(StudioAction.ReplaceProject(updated))
                            Toast.makeText(context, "💬 Subtitle track generated on Text lane!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }

                item {
                    StudioProToolCard(
                        title = "Smart Audio Ducking (Auto-Duck)",
                        desc = "Automatically dips background music volume by 65% during voiceover and dialog segments.",
                        icon = Icons.Default.VolumeDown,
                        badge = "DSP DUCKING",
                        accent = Color(0xFF10B981),
                        onClick = {
                            val updated = SaifAiEditingEngine.applySmartAudioDuckingToStudioProject(project)
                            viewModel.commit(StudioAction.ReplaceProject(updated))
                            Toast.makeText(context, "🎵 Smart audio-ducking applied to BGM tracks!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }

                item {
                    StudioProToolCard(
                        title = "AI Auto-Reframe (9:16 Shorts)",
                        desc = "Instantly transforms aspect ratio to 9:16 portrait format with center focal alignment.",
                        icon = Icons.Default.CropRotate,
                        badge = "9:16 REELS",
                        accent = Color(0xFFFF5252),
                        onClick = {
                            val updated = SaifAiEditingEngine.applyAutoReframeToStudioProject(project, "9:16")
                            viewModel.commit(StudioAction.ReplaceProject(updated))
                            Toast.makeText(context, "📱 Reframed timeline to 9:16 Shorts aspect!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun StudioProToolCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1B1528),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = accent.copy(alpha = 0.25f)) {
                        Text(badge, color = accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Text(desc, color = Color(0xFF9E95AC), fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
        }
    }
}
