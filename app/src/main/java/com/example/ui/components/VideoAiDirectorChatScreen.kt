package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

data class AiDirectorDiffItem(
    val prefix: String, // "+", "~"
    val prefixColor: Color,
    val title: String,
    val detail: String,
    val tag: String,
    val tagColor: Color
)

data class AiDirectorMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestampTag: String? = null,
    val diffCardItems: List<AiDirectorDiffItem>? = null,
    val diffPreviewLabel: String? = null
)

/**
 * AI Director Video Editing Chat Interface.
 * Opened when user clicks on "SAIF AI EDITING" pill on the timeline.
 * Allows user to send instructions or prompt about specific parts of the video
 * to edit via AI.
 */
@Composable
fun VideoAiDirectorChatScreen(
    project: VideoProjectData,
    onBack: () -> Unit,
    onApplyChanges: (VideoProjectData) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var latestPlan by remember { mutableStateOf<VideoEditPlan?>(null) }

    // Initial conversation history matching provided HTML design
    val messages = remember {
        mutableStateListOf(
            AiDirectorMessage(
                isUser = false,
                text = "Welcome to Saif AI Director. Specify any timestamp, clip range, or visual instruction to transform your timeline."
            ),
            AiDirectorMessage(
                isUser = true,
                text = "Add a chromatic glitch transition at 00:10:00 - 00:15:20 and boost neon bloom luminescense.",
                timestampTag = "Target: 00:10:00 – 00:15:20 • Clip #2"
            ),
            AiDirectorMessage(
                isUser = false,
                text = "Planned cinematic modifications for the targeted sequence:",
                diffCardItems = listOf(
                    AiDirectorDiffItem(
                        prefix = "+",
                        prefixColor = Color(0xFF00DAF3),
                        title = "Chromatic Glitch FX",
                        detail = "Duration: 0.6s at 00:12:00 • Track V2",
                        tag = "FX Insert",
                        tagColor = Color(0xFF00DAF3)
                    ),
                    AiDirectorDiffItem(
                        prefix = "~",
                        prefixColor = Color(0xFFAEC6FF),
                        title = "Neon Luminescence Grade",
                        detail = "65% -> 85% Glow Bloom Bloom at 00:12:08",
                        tag = "Grade",
                        tagColor = Color(0xFFAEC6FF)
                    ),
                    AiDirectorDiffItem(
                        prefix = "+",
                        prefixColor = Color(0xFFD1BCFF),
                        title = "Cyber Bass Drop & Glitch Whoosh",
                        detail = "Sub-bass + 24kHz rise at 00:12:08 • Track A1",
                        tag = "SFX",
                        tagColor = Color(0xFFD1BCFF)
                    )
                ),
                diffPreviewLabel = "Preview Window (00:11:50 - 00:12:30)"
            )
        )
    }

    val activeClip = project.clips.getOrNull(project.selectedClipIndex)
        ?: project.clips.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131313))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // --- 1. Header Bar ---
        Surface(
            color = Color(0xFF131313).copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF201F1F))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE5E2E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7B2BFF), Color(0xFF00DAF3))
                                    )
                                )
                                .padding(1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF0E0E0E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF00DAF3),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00DAF3))
                                .align(Alignment.BottomEnd)
                                .border(1.5.dp, Color(0xFF131313), CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ai Director",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x337B2BFF)
                            ) {
                                Text(
                                    text = "AI",
                                    color = Color(0xFFD1BCFF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00DAF3))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Timeline Synced • 00:12:08",
                                color = Color(0xFF00DAF3),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            android.widget.Toast.makeText(context, "AI Director latency: 12ms (Cloud Neural Engine)", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF201F1F))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFFCCC3D9),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // --- 2. Dynamic Pinned Timeline Context Strip ---
        Surface(
            color = Color(0xFF1C1B1B),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A2A2A))
                        ) {
                            if (activeClip?.uri != null) {
                                AsyncImage(
                                    model = activeClip.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF00DAF3),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.Center)
                                )
                            }
                            Text(
                                text = "00:12:08",
                                color = Color(0xFF00DAF3),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = project.projectName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00DAF3))
                                )
                            }
                            Text(
                                text = "⏱ 00:12:08 • ${activeClip?.name ?: "Clip #2: Shibuya_Rain.mp4"}",
                                color = Color(0xFFCCC3D9),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            android.widget.Toast.makeText(context, "Live Preview synchronized", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2A2A2A)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFF00DAF3),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Preview",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Range Scrubber Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF7B2BFF)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("00:08:00 – 00:15:20", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2A2A2A)
                    ) {
                        Text("Current (00:12:08)", color = Color(0xFFCCC3D9), fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2A2A2A)
                    ) {
                        Text("Full (00:42:00)", color = Color(0xFFCCC3D9), fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2A2A2A)
                    ) {
                        Text("Intro 0:00–0:08", color = Color(0xFFCCC3D9), fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }

        // --- 3. Chat Messages Stream ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            messages.forEach { msg ->
                if (!msg.isUser) {
                    // AI Message Bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(0.96f),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7B2BFF), Color(0xFF00DAF3))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                                color = Color(0xFF1C1B1B)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.text,
                                        color = Color(0xFFE5E2E1),
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp
                                    )

                                    if (msg.diffCardItems == null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Realtime multi-track latency: 12ms", color = Color(0xFFCCC3D9), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }

                            // Diff Card If Present
                            msg.diffCardItems?.let { items ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF201F1F),
                                    border = BorderStroke(1.dp, Color(0x3500DAF3)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.HistoryToggleOff, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Timeline Proposed Diff", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF2A2A2A)
                                            ) {
                                                Text("${items.size} Actions", color = Color(0xFF00DAF3), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        items.forEach { item ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF141414),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = item.prefix,
                                                        color = item.prefixColor,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.width(16.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                        Text(item.detail, color = Color(0xFFCCC3D9), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF2A2A2A)
                                                    ) {
                                                        Text(
                                                            text = item.tag,
                                                            color = item.tagColor,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Apply & Preview Actions
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    android.widget.Toast.makeText(context, "Playing preview with FX...", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                                modifier = Modifier.weight(1f).height(40.dp)
                                            ) {
                                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Preview ▶", color = Color.White, fontSize = 12.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    val updatedProject = if (latestPlan != null) {
                                                        SaifAiEditingEngine.executePlan(project, latestPlan!!)
                                                    } else {
                                                        val updatedClips = project.clips.mapIndexed { idx, clip ->
                                                            if (idx == project.selectedClipIndex) clip.copy(isAiEnhanced = true, origin = "ai") else clip
                                                        }
                                                        project.copy(
                                                            clips = updatedClips,
                                                            effectOverlay = "Chromatic Glitch & Bloom"
                                                        )
                                                    }
                                                    onApplyChanges(updatedProject)
                                                    android.widget.Toast.makeText(context, "✅ AI modifications applied to timeline!", android.widget.Toast.LENGTH_LONG).show()
                                                    onBack()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                                                modifier = Modifier.weight(1f).height(40.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Apply ✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick Director Suggestions (Levels 9 & 17)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "✂️ Split active clip in half",
                                    "🗑️ Cut & remove silence",
                                    "⚡ Boost cinematic contrast",
                                    "⏳ Smooth 50% slow-motion",
                                    "💬 Neon blue subtitle overlay",
                                    "🔥 Aur energetic karo",
                                    "🎯 0:15 se 0:22 theek karo",
                                    "✨ Dikhne me zyada minimal"
                                ).forEach { sug ->
                                    Surface(
                                        onClick = {
                                            inputText = sug.removePrefix("✂️ ").removePrefix("🗑️ ").removePrefix("⚡ ").removePrefix("⏳ ").removePrefix("💬 ").removePrefix("🔥 ").removePrefix("🎯 ").removePrefix("✨ ")
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF1E1E1E)
                                    ) {
                                        Text(
                                            text = sug,
                                            color = Color(0xFFCCC3D9),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // User Message Bubble
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = Color(0xFF7B2BFF),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(
                                text = msg.text,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        msg.timestampTag?.let { tag ->
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1C1B1B)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFAEC6FF)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(tag, color = Color(0xFFCCC3D9), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            if (isThinking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color(0xFF00DAF3),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Director is analyzing timeline sequence...",
                        color = Color(0xFF00DAF3),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- 4. Sticky Bottom Input Bar ---
        Surface(
            color = Color(0xFF131313).copy(alpha = 0.95f),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Pinned Timestamp Adjuster Strip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF201F1F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00DAF3))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📍 Pinned at 00:12:08 (${activeClip?.name ?: "Clip #2"})",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "Adjust Range",
                            color = Color(0xFFAEC6FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Capsule Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF201F1F))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            android.widget.Toast.makeText(context, "Attach reference clip", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = {
                            android.widget.Toast.makeText(context, "Voice input ready", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(20.dp))
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Direct edit at 00:12:08: type visual prompt...", fontSize = 12.5.sp, color = Color(0xFF948F99)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            val prompt = inputText.trim()
                            if (prompt.isNotBlank()) {
                                messages.add(
                                    AiDirectorMessage(
                                        isUser = true,
                                        text = prompt,
                                        timestampTag = "Target: 00:12:08 • ${activeClip?.name ?: "Active Clip"}"
                                    )
                                )
                                inputText = ""
                                isThinking = true

                                scope.launch {
                                    SaifAiEditingEngine.planAndStreamEdit(
                                        context = context,
                                        project = project,
                                        userPrompt = prompt,
                                        referenceNote = null,
                                        onProgressPhase = { _, _ -> },
                                        onStreamChunk = {}
                                    ).onSuccess { plan ->
                                        isThinking = false
                                        latestPlan = plan
                                        val diffItems = mutableListOf<AiDirectorDiffItem>()
                                        plan.operations.forEach { op ->
                                            diffItems.add(
                                                AiDirectorDiffItem(
                                                    prefix = "~",
                                                    prefixColor = Color(0xFF00DAF3),
                                                    title = "${op.op.uppercase()}: ${op.why.ifBlank { "Optimized parameter" }}",
                                                    detail = "Target: ${op.clipId} • ${op.params}",
                                                    tag = "OPERATION",
                                                    tagColor = Color(0xFF00DAF3)
                                                )
                                            )
                                        }
                                        plan.overlays.forEach { ov ->
                                            diffItems.add(
                                                AiDirectorDiffItem(
                                                    prefix = "+",
                                                    prefixColor = Color(0xFFD1BCFF),
                                                    title = "${ov.type.uppercase()}: ${ov.why.ifBlank { "Added overlay" }}",
                                                    detail = "At ${ov.startSeconds}s (${ov.durationSeconds}s) • ${ov.props}",
                                                    tag = "OVERLAY",
                                                    tagColor = Color(0xFFD1BCFF)
                                                )
                                            )
                                        }
                                        if (diffItems.isEmpty()) {
                                            diffItems.add(
                                                AiDirectorDiffItem(
                                                    prefix = "+",
                                                    prefixColor = Color(0xFF00DAF3),
                                                    title = "AI Prompt FX: $prompt",
                                                    detail = "Applied at 00:12:08 • Track V1",
                                                    tag = "AI GENERATED",
                                                    tagColor = Color(0xFF00DAF3)
                                                )
                                            )
                                        }
                                        messages.add(
                                            AiDirectorMessage(
                                                isUser = false,
                                                text = "Understood. Generated neural edit plan: ${plan.summary}",
                                                diffCardItems = diffItems
                                            )
                                        )
                                    }.onFailure { err ->
                                        isThinking = false
                                        messages.add(
                                            AiDirectorMessage(
                                                isUser = false,
                                                text = "Could not generate diff: ${err.message}"
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF7B2BFF), Color(0xFF006CEB))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
