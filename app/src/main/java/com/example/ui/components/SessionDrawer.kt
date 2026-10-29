package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import com.example.data.local.ChatSessionEntity
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.SubtextMuted
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.automirrored.outlined.Logout
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDrawerContent(
    sessions: List<ChatSessionEntity>,
    activeSessionId: String?,
    drawerState: androidx.compose.material3.DrawerState,
    onSelectSession: (String) -> Unit,
    onNewChat: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearAll: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sessionToRename by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var sessionToDelete by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showConfirmClearAll by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSessions = if (searchQuery.isBlank()) {
        sessions
    } else {
        sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Row container to hold both the drawer content and the outer glowing edge
    Row(
        modifier = modifier
            .fillMaxHeight()
            // Consumes tap gestures to prevent the transparent scrim behind it from immediately closing the drawer
            .pointerInput(Unit) { detectTapGestures { } } 
    ) {
        // Main Drawer Panel
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(320.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        0.85f to SaifTheme.colors.primaryBackground, // Very deep black/blue
                        1.0f to SaifTheme.colors.surfaceCard   // Subtle glowing edge
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                // New Chat Button
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val buttonScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "NewChatScale"
                )

                Box(modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth().scale(buttonScale)) {
                    // Slight glow when pressed
                    val glowAlpha by animateFloatAsState(if (isPressed) 0.3f else 0f, label = "NewChatGlow")
                    Box(modifier = Modifier.matchParentSize().background(Color(0xFFA855F7).copy(alpha = glowAlpha), RoundedCornerShape(24.dp)))
                    
                    Surface(
                        onClick = onNewChat,
                        shape = RoundedCornerShape(24.dp),
                        color = SaifTheme.colors.surfaceCard.copy(alpha = 0.6f), // Dark purple tint
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4C1D95)),
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = interactionSource
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = Color(0xFFA855F7), // Light Purple
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "New chat",
                                color = Color(0xFFA855F7),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Search Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SaifTheme.colors.surfaceCard.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = SaifTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = SaifTheme.colors.textPrimary,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFFA855F7)),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search chats...",
                                        color = SaifTheme.colors.textSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // RECENT Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textSecondary,
                        letterSpacing = 1.sp
                    )
                    
                    if (sessions.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showConfirmClearAll = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear All",
                                tint = SaifTheme.colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear All",
                                fontSize = 10.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                    }
                }

                // Session List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredSessions, key = { _, session -> session.id }) { index, session ->
                        var isVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(drawerState.isOpen) {
                            if (drawerState.isOpen) {
                                delay(index * 60L + 50L) // Wave-like stagger effect
                                isVisible = true
                            } else {
                                isVisible = false
                            }
                        }
                        
                        AnimatedVisibility(
                            visible = isVisible,
                            modifier = Modifier.animateItem(),
                            enter = slideInHorizontally(
                                initialOffsetX = { -150 },
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(400))
                        ) {
                            val isActive = session.id == activeSessionId
                            val isAppProject = session.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, session.title)
                            val isVideoProject = session.mode == "video_project" || com.example.util.ProjectDataManager.hasVideoProject(context, session.title)
                            val isProject = isAppProject || isVideoProject
                            val projectRed = Color(0xFFEF4444)
                            val videoPurple = Color(0xFFA855F7)
                            var showMenu by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isActive && isVideoProject -> videoPurple.copy(alpha = 0.22f)
                                            isActive && isAppProject -> projectRed.copy(alpha = 0.20f)
                                            isActive -> SaifTheme.colors.surfaceCard
                                            isVideoProject -> videoPurple.copy(alpha = 0.08f)
                                            isAppProject -> projectRed.copy(alpha = 0.08f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onSelectSession(session.id) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when {
                                        isVideoProject -> Icons.Outlined.Videocam
                                        isAppProject -> Icons.Outlined.Code
                                        else -> Icons.Outlined.ChatBubbleOutline
                                    },
                                    contentDescription = when {
                                        isVideoProject -> "Video Project"
                                        isAppProject -> "App Project"
                                        else -> "Chat"
                                    },
                                    tint = when {
                                        isVideoProject -> videoPurple
                                        isAppProject -> projectRed
                                        else -> SaifTheme.colors.textSecondary
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = session.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isAppProject || isVideoProject) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isVideoProject -> videoPurple
                                        isAppProject -> projectRed
                                        else -> SaifTheme.colors.textPrimary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isVideoProject) {
                                    Surface(
                                        color = videoPurple.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, videoPurple.copy(alpha = 0.4f)),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "VIDEO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = videoPurple,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                } else if (isAppProject) {
                                    Surface(
                                        color = projectRed.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, projectRed.copy(alpha = 0.4f)),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "APP",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = projectRed,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = if (isProject) projectRed.copy(alpha = 0.8f) else SaifTheme.colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier.background(SaifTheme.colors.surfaceCard)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename", color = SaifTheme.colors.textPrimary, fontSize = 13.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = SaifTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
                                            },
                                            onClick = {
                                                showMenu = false
                                                sessionToRename = session
                                                renameInput = session.title
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = StatusError, fontSize = 13.sp) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                            },
                                            onClick = {
                                                showMenu = false
                                                sessionToDelete = session
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // User Profile & Log Out Footer
                val authUser by com.example.data.local.AuthManager.currentUser.collectAsState()
                val drawerScope = rememberCoroutineScope()
                val user = authUser
                if (user != null) {
                    val initials = user.initialLetter
                    val avatarPath = user.profilePicturePath
                    val avatarFile = avatarPath?.let { java.io.File(it) }
                    val drawerAvatarBitmap = remember(avatarPath) {
                        if (avatarFile != null && avatarFile.exists()) {
                            try {
                                BitmapFactory.decodeFile(avatarFile.absolutePath)
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }

                    if (user.isAdmin) {
                        Surface(
                            onClick = {
                                drawerScope.launch { drawerState.close() }
                                onOpenAdminDashboard()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E1B4B),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "👑", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Admin Control Center",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = SaifTheme.colors.surfaceCard.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (user.isAdmin) Color(0xFFF59E0B).copy(alpha = 0.4f) else SaifTheme.colors.cardBorder.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (user.isAdmin) Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                                        else Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (drawerAvatarBitmap != null) {
                                    Image(
                                        bitmap = drawerAvatarBitmap.asImageBitmap(),
                                        contentDescription = "Profile Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.name,
                                        color = SaifTheme.colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (user.isAdmin) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0x33F59E0B)
                                        ) {
                                            Text(
                                                text = "ADMIN",
                                                color = Color(0xFFF59E0B),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = user.email,
                                    color = SubtextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } // End Main Drawer Panel

        // Subtle Light Glow on the Right Edge (tight to border, continuous from top to bottom)
        var glowVisible by remember { mutableStateOf(false) }
        LaunchedEffect(drawerState.isOpen) {
            if (drawerState.isOpen) {
                delay(80)
                glowVisible = true
            } else {
                glowVisible = false
            }
        }
        val infiniteTransition = rememberInfiniteTransition(label = "drawerGlow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.16f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "drawerGlowAlpha"
        )

        AnimatedVisibility(
            visible = glowVisible,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            0.0f to Color(0xFF8B5CF6).copy(alpha = glowAlpha),
                            1.0f to Color.Transparent
                        )
                    )
            )
        }
    } // End Outer Row

    // Rename Dialog
    if (sessionToRename != null) {
        BasicAlertDialog(onDismissRequest = { sessionToRename = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SaifTheme.colors.surfaceCard.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(SaifTheme.colors.textPrimary.copy(alpha = 0.35f), Color.Transparent, SaifTheme.colors.textPrimary.copy(alpha = 0.1f)))),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Rename Conversation",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryAccent,
                            unfocusedBorderColor = SaifTheme.colors.cardBorder,
                            focusedTextColor = SaifTheme.colors.textPrimary,
                            unfocusedTextColor = SaifTheme.colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { sessionToRename = null },
                            shape = RoundedCornerShape(8.dp),
                            color = SaifTheme.colors.surfaceCard
                        ) {
                            Text("Cancel", color = SaifTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                if (renameInput.isNotBlank()) {
                                    onRenameSession(sessionToRename!!.id, renameInput.trim())
                                }
                                sessionToRename = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryAccent
                        ) {
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Confirm Clear All Dialog
    if (showConfirmClearAll) {
        BasicAlertDialog(onDismissRequest = { showConfirmClearAll = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SaifTheme.colors.surfaceCard.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(SaifTheme.colors.textPrimary.copy(alpha = 0.35f), Color.Transparent, SaifTheme.colors.textPrimary.copy(alpha = 0.1f)))),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Clear All Conversations?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textPrimary
                    )

                    Text(
                        text = "This will permanently delete all chat history. This action cannot be undone.",
                        fontSize = 13.sp,
                        color = SubtextMuted,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { showConfirmClearAll = false },
                            shape = RoundedCornerShape(8.dp),
                            color = SaifTheme.colors.surfaceCard
                        ) {
                            Text("Cancel", color = SaifTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                onClearAll()
                                showConfirmClearAll = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = StatusError
                        ) {
                            Text("Delete All", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Confirm Single Session / Project Delete Dialog
    if (sessionToDelete != null) {
        val targetSession = sessionToDelete!!
        val isVideo = targetSession.mode.equals("video_project", ignoreCase = true) || com.example.util.ProjectDataManager.hasVideoProject(context, targetSession.title)
        val isProject = targetSession.mode.equals("project", ignoreCase = true) || com.example.util.ProjectDataManager.hasProject(context, targetSession.title) || isVideo

        BasicAlertDialog(onDismissRequest = { sessionToDelete = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SaifTheme.colors.surfaceCard.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isVideo) "Delete Video Project?" else if (isProject) "Delete Project?" else "Delete Chat?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaifTheme.colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isVideo) {
                            "Are you sure you want to delete '${targetSession.title}'? All timeline clips, drafts, and director edits will be permanently removed."
                        } else if (isProject) {
                            "Are you sure you want to delete '${targetSession.title}'? All project source code, layouts, and chat history will be permanently deleted."
                        } else {
                            "Are you sure you want to delete this chat session with '${targetSession.title}'?"
                        },
                        fontSize = 13.sp,
                        color = SubtextMuted,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { sessionToDelete = null },
                            shape = RoundedCornerShape(8.dp),
                            color = SaifTheme.colors.surfaceCard
                        ) {
                            Text(
                                "Cancel",
                                color = SaifTheme.colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                onDeleteSession(targetSession.id)
                                sessionToDelete = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = StatusError
                        ) {
                            Text(
                                "Delete",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
