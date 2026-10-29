package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Project card model matching user's Home Studio design.
 */
data class StudioProjectCard(
    val id: String,
    val title: String,
    val editedTimeAgo: String,
    val resolutionBadge: String,
    val durationText: String,
    val tagText: String,
    val tagColor: Color,
    val tagIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val imageUrl: String,
    val aspectRatioLabel: String,
    val isAudio: Boolean = false
)

/**
 * Modern Fullscreen / Modal Home Studio Screen based on user's exact provided interface.
 * Features:
 * 1. Top bar: Auto-videocam icon, "Home Studio", notifications, profile avatar, close button.
 * 2. Greeting header: "Namaste 👋", "Aapke projects", filter/tune button.
 * 3. Hero Card: Neural Engine v2.4, Cloud Active status.
 *    - "+ New Project" button: Opens workflow choice dialog (With Video / Photos vs Without Video / Blank).
 *    - "Ek Line Se Video banao" (Prompt-to-video launcher).
 * 4. Filter chips: All (4), Recent, 9:16, 16:9, 1:1, AI edited.
 * 5. Section heading: Active Sequences & Grid/List view toggle.
 * 6. 2-column project cards grid:
 *    - Cyberpunk Tokyo Vlog (AI Edit, 1080p, 00:42)
 *    - Nike Commercial Concept (16:9, 4K, 00:28)
 *    - AI SaaS Teaser Reel (AI Edit, 1080p, 00:15)
 *    - Lo-Fi Beats Soundscape (1:1, Audio, 01:20)
 * 7. Cloud render storage indicator: 2.4 GB of 15 GB used.
 */
@Composable
fun VideoProjectCreateDialog(
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onSelectWithMedia: (projectName: String) -> Unit,
    onSelectWithoutMedia: (projectName: String) -> Unit,
    onOpenProject: (projectName: String) -> Unit = onSelectWithoutMedia,
    onCreateFromPrompt: (prompt: String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showWorkflowChoiceModal by remember { mutableStateOf(false) }
    var showPromptDirectorModal by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var projectNameInput by remember { mutableStateOf(com.example.util.ProjectDataManager.nextUntitledVideoName(context)) }
    var savedProjectsList by remember {
        mutableStateOf(com.example.util.ProjectDataManager.getAllVideoProjects(context))
    }

    // Color tokens matching the user's HTML design
    val bgDark = Color(0xFF131313)
    val surfaceContainerLow = Color(0xFF1C1B1B)
    val surfaceContainer = Color(0xFF201F1F)
    val surfaceContainerHigh = Color(0xFF2A2A2A)
    val surfaceContainerHighest = Color(0xFF353534)
    val surfaceContainerLowest = Color(0xFF0E0E0E)
    val primaryContainer = Color(0xFF7B2BFF)
    val secondaryContainer = Color(0xFF006CEB)
    val tertiaryCyan = Color(0xFF00DAF3)
    val textPrimary = Color(0xFFE5E2E1)
    val textSecondary = Color(0xFFCCC3D9)
    val textMuted = Color(0xFF958DA2)

    val sampleProjects = remember { emptyList<StudioProjectCard>() }

    val dynamicSavedCards = remember(savedProjectsList) {
        savedProjectsList.map { draft ->
            StudioProjectCard(
                id = "saved_" + draft.projectName,
                title = draft.projectName,
                editedTimeAgo = "Draft Auto-Saved",
                resolutionBadge = draft.resolution,
                durationText = "${draft.totalDurationSeconds.toInt()}s",
                tagText = if (draft.clips.any { it.isAiEnhanced }) "AI Edit" else "${draft.clips.size} Clips",
                tagColor = if (draft.clips.any { it.isAiEnhanced }) Color(0xFF00DAF3) else Color(0xFFA855F7),
                tagIcon = if (draft.clips.any { it.isAiEnhanced }) Icons.Default.AutoFixHigh else Icons.Default.Videocam,
                imageUrl = draft.clips.firstOrNull { it.uri != null }?.uri?.toString() ?: "",
                aspectRatioLabel = "16:9"
            )
        }
    }

    val combinedProjects = dynamicSavedCards

    val filteredProjects = remember(selectedFilter, combinedProjects) {
        when (selectedFilter) {
            "9:16" -> combinedProjects.filter { it.aspectRatioLabel == "9:16" }
            "16:9" -> combinedProjects.filter { it.aspectRatioLabel == "16:9" }
            "1:1" -> combinedProjects.filter { it.aspectRatioLabel == "1:1" }
            "AI edited" -> combinedProjects.filter { it.tagText == "AI Edit" }
            else -> combinedProjects
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgDark)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Ambient Atmospheric Glow Background Blur Spheres
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(x = (-40).dp, y = (-50).dp)
                    .blur(70.dp)
                    .background(primaryContainer.copy(alpha = 0.20f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = 140.dp)
                    .blur(70.dp)
                    .background(tertiaryCyan.copy(alpha = 0.15f), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // --- 1. Fixed Header Bar (Home Studio Shell) ---
                Surface(
                    color = bgDark.copy(alpha = 0.92f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surfaceContainerHigh)
                                    .border(1.dp, tertiaryCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoCameraBack,
                                    contentDescription = null,
                                    tint = tertiaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Home Studio",
                                color = textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { /* Notifications */ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1BCFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color(0xFF3D008F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(surfaceContainerHigh)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Sub-Header: Greetings & Studio Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Welcome back 👋",
                                    color = textSecondary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(tertiaryCyan)
                                        .animateContentSize()
                                )
                            }
                            Text(
                                text = "Your Projects",
                                color = textPrimary,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        IconButton(
                            onClick = { /* Editor Preferences */ },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceContainerHigh.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Editor Settings",
                                tint = tertiaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // --- 2. Hero Card: Creation Launcher ---
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = surfaceContainerLow,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, Color(0x227B2BFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            primaryContainer.copy(alpha = 0.35f),
                                            secondaryContainer.copy(alpha = 0.18f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                // Neural Engine tag
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = surfaceContainerHighest.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = tertiaryCyan,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "NEURAL ENGINE V2.4",
                                                color = Color(0xFFEBDFFF),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Cloud Active",
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Two Action Launchers
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Primary: + New Project Button (Opens With Video / Without Video Workflow Choice)
                                    Button(
                                        onClick = { showWorkflowChoiceModal = true },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(7f)
                                            .height(78.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(primaryContainer, secondaryContainer)
                                                )
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddCircle,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.NorthEast,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "+ New Project",
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 16.sp
                                                )
                                                Text(
                                                    text = "Timeline canvas",
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Secondary: Ek Line Se Video Banao (Prompt Video generator)
                                    Surface(
                                        onClick = {
                                            showPromptDirectorModal = true
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = surfaceContainerHigh.copy(alpha = 0.9f),
                                        border = BorderStroke(1.dp, Color(0x25FFFFFF)),
                                        modifier = Modifier
                                            .weight(5f)
                                            .height(78.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(tertiaryCyan.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Bolt,
                                                        contentDescription = null,
                                                        tint = tertiaryCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = tertiaryCyan.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "AI",
                                                        color = tertiaryCyan,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = "Prompt to Video",
                                                    color = textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    lineHeight = 14.sp
                                                )
                                                Text(
                                                    text = "AI Generator",
                                                    color = textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Dedicated Editing Draft Files Section (Right below Create New Project) ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Editing Drafts",
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = primaryContainer.copy(alpha = 0.25f),
                                border = BorderStroke(0.5.dp, primaryContainer.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${savedProjectsList.size} SAVED",
                                    color = Color(0xFFD1BCFF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auto-Saved",
                                color = textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (savedProjectsList.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            savedProjectsList.forEach { draft ->
                                Surface(
                                    onClick = { onOpenProject(draft.projectName) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = surfaceContainerLow,
                                    border = BorderStroke(1.dp, Color(0x337B2BFF)),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(primaryContainer, secondaryContainer)
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Videocam,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = draft.projectName,
                                                    color = textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${draft.clips.size} clips • ${draft.resolution} ${draft.fps}",
                                                        color = textSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "• ${draft.totalDurationSeconds.toInt()}s",
                                                        color = tertiaryCyan,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = { onOpenProject(draft.projectName) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = primaryContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text(
                                                    text = "Open Draft",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = {
                                                    com.example.util.ProjectDataManager.duplicateVideoProject(context, draft.projectName)
                                                    savedProjectsList = com.example.util.ProjectDataManager.getAllVideoProjects(context)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = "Duplicate project",
                                                    tint = Color(0xFF00E5FF).copy(alpha = 0.85f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    com.example.util.ProjectDataManager.deleteVideoProject(context, draft.projectName)
                                                    savedProjectsList = com.example.util.ProjectDataManager.getAllVideoProjects(context)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = "Delete Draft",
                                                    tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = surfaceContainerLowest.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Videocam,
                                    contentDescription = null,
                                    tint = textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "No drafts yet. Projects created with '+ New Project' will be saved here automatically.",
                                    color = textMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // --- 3. Filter Chips Horizontal Scroll ---
                    val filterChips = listOf("All", "Recent", "9:16", "16:9", "1:1", "AI edited")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filterChips.forEach { chip ->
                            val isSelected = selectedFilter == chip
                            Surface(
                                onClick = { selectedFilter = chip },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color.Transparent else surfaceContainerHigh.copy(alpha = 0.8f),
                                border = if (isSelected) null else BorderStroke(1.dp, Color(0x22FFFFFF)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                Brush.horizontalGradient(
                                                    listOf(primaryContainer, secondaryContainer)
                                                )
                                            )
                                        } else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (chip == "9:16") {
                                        Icon(Icons.Default.StayCurrentPortrait, contentDescription = null, tint = tertiaryCyan, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else if (chip == "16:9") {
                                        Icon(Icons.Default.StayCurrentLandscape, contentDescription = null, tint = textSecondary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else if (chip == "1:1") {
                                        Icon(Icons.Default.CropSquare, contentDescription = null, tint = textSecondary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else if (chip == "AI edited") {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = tertiaryCyan, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = chip,
                                        color = if (isSelected) Color.White else textSecondary,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (chip == "All") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "${sampleProjects.size}",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 4. Section Heading & View Toggle ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Active Sequences",
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1BCFF))
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.GridView, contentDescription = "Grid", tint = tertiaryCyan, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ViewAgenda, contentDescription = "List", tint = textMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // --- 5. Project Grid: 2 Columns ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            filteredProjects.filterIndexed { idx, _ -> idx % 2 == 0 }.forEach { card ->
                                StudioProjectCardItem(
                                    card = card,
                                    onClick = {
                                        // Open this project or draft into timeline
                                        onOpenProject(card.title)
                                    }
                                )
                            }
                        }

                        // Right Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            filteredProjects.filterIndexed { idx, _ -> idx % 2 == 1 }.forEach { card ->
                                StudioProjectCardItem(
                                    card = card,
                                    onClick = {
                                        onOpenProject(card.title)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 6. Quick Storage & Render Quota Indicator ---
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = surfaceContainerLow.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(primaryContainer.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = tertiaryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "SAIF Cloud Render",
                                            color = textPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(tertiaryCyan)
                                        )
                                    }
                                    Text(
                                        text = "2.4 GB of 15 GB used · Fast GPU queue",
                                        color = textSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = surfaceContainerHigh
                            ) {
                                Text(
                                    text = "Manage",
                                    color = textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // --- 7. WORKFLOW MODAL: With Video / Without Video Choices ---
            if (showWorkflowChoiceModal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showWorkflowChoiceModal = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = surfaceContainer,
                        border = BorderStroke(1.dp, Color(0x357B2BFF)),
                        shadowElevation = 20.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // Modal Top
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
                                                Brush.linearGradient(
                                                    listOf(primaryContainer, tertiaryCyan)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Create New Project",
                                            color = textPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Choose timeline initialization mode",
                                            color = tertiaryCyan,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showWorkflowChoiceModal = false },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Project Name Input
                            OutlinedTextField(
                                value = projectNameInput,
                                onValueChange = { projectNameInput = it },
                                label = { Text("Project Title") },
                                placeholder = { Text("e.g. Cyberpunk Tokyo Vlog") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryContainer,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedLabelColor = tertiaryCyan
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Select sequence setup:",
                                color = textSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // OPTION 1: With Video / Media (Gallery Picker for 50+ items)
                            Surface(
                                onClick = {
                                    showWorkflowChoiceModal = false
                                    val name = projectNameInput.ifBlank { "Untitled ${savedProjectsList.size + 1}" }
                                    onSelectWithMedia(name)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1E1E28),
                                border = BorderStroke(1.5.dp, primaryContainer),
                                shadowElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    primaryContainer.copy(alpha = 0.35f),
                                                    tertiaryCyan.copy(alpha = 0.15f)
                                                )
                                            )
                                        )
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideoLibrary,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "With Video / Photos",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = tertiaryCyan
                                            ) {
                                                Text(
                                                    text = "50+ ITEMS",
                                                    color = Color.Black,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Select multi-media clips from gallery into timeline tracks",
                                            color = textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = tertiaryCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // OPTION 2: Without Video (Direct Blank Timeline Canvas)
                            Surface(
                                onClick = {
                                    showWorkflowChoiceModal = false
                                    val name = projectNameInput.ifBlank { "Untitled ${savedProjectsList.size + 1}" }
                                    onSelectWithoutMedia(name)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1C1B1B),
                                border = BorderStroke(1.dp, Color(0x35FFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewTimeline,
                                            contentDescription = null,
                                            tint = tertiaryCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Without Video (Blank Canvas)",
                                            color = textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Open empty timeline directly for text overlays, audio, or prompt generation",
                                            color = textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 8. PROMPT TO VIDEO MODAL: Ek Line Se Video Banao ---
            if (showPromptDirectorModal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showPromptDirectorModal = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = surfaceContainer,
                        border = BorderStroke(1.5.dp, Color(0xFF00DAF3)),
                        shadowElevation = 24.dp
                    ) {
                        var promptToGenerate by remember { mutableStateOf("") }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
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
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(primaryContainer, tertiaryCyan)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Ek Line Se Video Banao",
                                            color = textPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Autonomous AI Topic Research & Director",
                                            color = tertiaryCyan,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showPromptDirectorModal = false },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Enter any topic or prompt in Hindi or English.\nAI will research the topic, write a 5-scene viral script, code animations, and render your video.",
                                color = textSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = promptToGenerate,
                                onValueChange = { promptToGenerate = it },
                                placeholder = {
                                    Text(
                                        "e.g. 5 AI tools jo 2026 me sab badal denge / YouTube growth secrets",
                                        color = textMuted,
                                        fontSize = 12.sp
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = tertiaryCyan,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = surfaceContainerLowest,
                                    unfocusedContainerColor = surfaceContainerLowest
                                ),
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Suggestions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val chips = listOf(
                                    "🔥 5 AI Tools in 2026 (Hindi)",
                                    "📈 YouTube Channel Growth Secret",
                                    "🧠 Quantum Computing Explained",
                                    "💰 Crypto vs Stocks for Beginners"
                                )
                                chips.forEach { chip ->
                                    Surface(
                                        onClick = { promptToGenerate = chip },
                                        shape = RoundedCornerShape(14.dp),
                                        color = surfaceContainerHigh,
                                        border = BorderStroke(0.5.dp, tertiaryCyan.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = chip,
                                            color = textPrimary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    val finalPrompt = promptToGenerate.trim().ifBlank { "Viral AI Video Project" }
                                    showPromptDirectorModal = false
                                    onCreateFromPrompt(finalPrompt)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(primaryContainer, secondaryContainer)
                                        )
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Research & Generate Video",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Project Card item matching 9:16 / 16:9 / 1:1 container layout.
 */
@Composable
fun StudioProjectCardItem(
    card: StudioProjectCard,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1C1B1B),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 13f)
                    .background(Color(0xFF0E0E0E))
            ) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Tag & Menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(0.5.dp, card.tagColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = card.tagIcon,
                                contentDescription = null,
                                tint = card.tagColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = card.tagText,
                                color = card.tagColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Bottom Specs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = card.resolutionBadge,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Text(
                        text = card.durationText,
                        color = Color(0xFF00DAF3),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Card Title & Meta
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = card.title,
                    color = Color(0xFFE5E2E1),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.editedTimeAgo,
                        color = Color(0xFFCCC3D9),
                        fontSize = 10.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF00DAF3),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
