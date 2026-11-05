package com.example.ui
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File


import com.example.ui.components.*
import com.example.data.local.ChatMessageEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SaifAiApp(
    viewModel: ChatViewModel,
    isLightMode: Boolean,
    onToggleTheme: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val bookmarkedMessages by viewModel.bookmarkedMessages.collectAsStateWithLifecycle()
    val isFilterBookmarked by viewModel.isFilterBookmarked.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentStreamingContent by viewModel.currentStreamingContent.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val customSystemPrompt by viewModel.customSystemPrompt.collectAsStateWithLifecycle()
    var showModeSelector by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val openSettingsRequest by com.example.HostNavigation.openSettingsRequest.collectAsState()
    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest) {
            showSettings = true
            com.example.HostNavigation.openSettingsRequest.value = false
        }
    }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showConfigureDialog by remember { mutableStateOf(false) }
    var activeProjectData by remember { mutableStateOf<com.example.ui.components.ProjectData?>(null) }
    var showProjectEditor by remember { mutableStateOf(false) }

    // Video Project Timeline Studio states
    var showVideoProjectDialog by remember { mutableStateOf(false) }
    var showNewProjectSheet by remember { mutableStateOf(false) }
    // Round 9: the purple mic opens SAIF AI's live conversation, powered by the SAIF AI AUTOMATION engine.
    var showLiveModeSheet by remember { mutableStateOf(false) }
    // Round 15: 🧰 POWER TOOLS — self test, crash log and 15 utilities, reachable from the header.
    var showPowerTools by remember { mutableStateOf(false) }

    /**
     * A MediaProjection can only be requested from a visible activity, so SAIF AI asks for it and then hands it to
     * the automation engine (`SaifAutomation.startScreenShare`). The engine never opens its own permission screen.
     */
    val screenCapturePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
            com.example.ui.components.SaifLiveSession.pendingScreenShare = false
            android.widget.Toast.makeText(context, "Screen share ke liye permission chahiye thi.", android.widget.Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        // Android 14+ requires a running media-projection foreground service BEFORE
        // getMediaProjection(). Starting the service is asynchronous, so await it.
        scope.launch {
            val automation = com.jarvis.assistant.SaifAutomation
            if (!automation.isLiveRunning.value && !automation.startLive(context, com.jarvis.assistant.SaifAutomation.LiveMode.SCREEN_SHARE, isLightMode)) {
                com.example.ui.components.SaifLiveSession.pendingScreenShare = false
                return@launch
            }
            val ready = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                automation.isLiveRunning.first { it }
                true
            } ?: false
            if (!ready || !com.jarvis.assistant.background.JarvisConversationService.elevateToMediaProjection()) {
                com.example.ui.components.SaifLiveSession.pendingScreenShare = false
                android.widget.Toast.makeText(context, "Screen capture service start nahi ho payi.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val mpm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val projection = try { mpm.getMediaProjection(result.resultCode, data) } catch (_: Exception) { null }
            if (projection == null) {
                com.jarvis.assistant.background.JarvisConversationService.demoteFromMediaProjection()
                com.example.ui.components.SaifLiveSession.pendingScreenShare = false
                android.widget.Toast.makeText(context, "MediaProjection nahi mila.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val metrics = android.util.DisplayMetrics().also { dm ->
                @Suppress("DEPRECATION")
                (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(dm)
            }
            val started = automation.startScreenShare(projection, metrics)
            if (started) automation.liveMode.value = com.jarvis.assistant.SaifAutomation.LiveMode.SCREEN_SHARE
            else {
                com.jarvis.assistant.background.JarvisConversationService.demoteFromMediaProjection()
                runCatching { projection.stop() }
            }
            com.example.ui.components.SaifLiveSession.pendingScreenShare = false
            android.widget.Toast.makeText(
                context,
                if (started) "Screen share shuru ho raha hai." else "Screen share start nahi ho paya.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            if (started) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val reason = automation.lastScreenShareError
                    if (reason.isNotBlank()) {
                        android.widget.Toast.makeText(context, "Screen share: $reason", android.widget.Toast.LENGTH_LONG).show()
                    }
                }, 9_000L)
            }
        }
    }
    var activeVideoProjectData by remember { mutableStateOf<com.example.ui.components.VideoProjectData?>(null) }
    var showVideoStudio by remember { mutableStateOf(false) }
    var pendingVideoProjectName by remember { mutableStateOf("Cyberpunk Tokyo Vlog") }

    val pendingOpenVideoProject by viewModel.pendingOpenVideoProject.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenVideoProject) {
        pendingOpenVideoProject?.let { proj ->
            activeVideoProjectData = proj
            showVideoStudio = true
            viewModel.consumePendingVideoProject()
        }
    }

    // Multi-media gallery picker for 50+ video & photos
    val videoProjectMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        val clips = if (uris.isNotEmpty()) {
            uris.mapIndexed { idx, uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/')?.take(18) ?: "Clip_${idx + 1}"
                com.example.ui.components.TimelineMediaClip(
                    name = name,
                    uri = uri,
                    durationSeconds = 8.0f,
                    isAiEnhanced = false,
                    isPhoto = false
                )
            }
        } else {
            // Clean empty timeline if user canceled gallery selection
            emptyList()
        }

        val videoProj = com.example.ui.components.VideoProjectData(
            projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(
                context, pendingVideoProjectName.ifBlank { "New Video Project" }
            ),
            clips = clips
        )
        activeVideoProjectData = videoProj
        showVideoStudio = true
        viewModel.createVideoProjectSession(videoProj, context)
        android.widget.Toast.makeText(
            context,
            "Loaded ${clips.size} clips into Timeline Studio!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    val displayedMessages = if (isFilterBookmarked) bookmarkedMessages else activeMessages
    // Auto-scroll on new messages or during active streaming
    LaunchedEffect(displayedMessages.size, currentStreamingContent.length) {
        val totalCount = displayedMessages.size + (if (isGenerating) 1 else 0)
        if (totalCount > 0) {
            listState.scrollToItem(totalCount - 1)
        }
    }
    val darkBg = Color(0xFF0A0D14)
    val lightBg = Color(0xFFFFFFFF)
    val revealProgress = remember { Animatable(1f) }
    var displayedBg by remember { mutableStateOf(if (isLightMode) lightBg else darkBg) }
    var upcomingBg by remember { mutableStateOf(if (isLightMode) lightBg else darkBg) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableStateOf(0f) }
    var stopVoiceListeningTrigger by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    val isAssistantOverlayActive by com.example.util.WakeWordManager.isAssistantOverlayActive.collectAsState()
    val cornerGlowTrigger by com.example.util.WakeWordManager.cornerGlowTrigger.collectAsState()
    val initialSpokenQuery by com.example.util.WakeWordManager.initialSpokenQuery.collectAsState()
    val isWakeWordEnabled by com.example.util.WakeWordManager.isWakeWordEnabledFlow.collectAsState()

    val isHomeScreen = !showProjectEditor && !showSettings && !showConfigureDialog && !showUpgradeDialog && !showModeSelector

    val appContext = LocalContext.current.applicationContext
    DisposableEffect(isHomeScreen, isWakeWordEnabled, isAssistantOverlayActive) {
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (isHomeScreen && isWakeWordEnabled && !isAssistantOverlayActive && hasMicPermission) {
            com.example.util.WakeWordAudioDetector.start(appContext)
        } else {
            com.example.util.WakeWordAudioDetector.stop()
        }

        onDispose {
            com.example.util.WakeWordAudioDetector.stop()
        }
    }

    LaunchedEffect(isLightMode) {
        val newBg = if (isLightMode) lightBg else darkBg
        if (newBg != upcomingBg) {
            displayedBg = upcomingBg
            upcomingBg = newBg
            revealProgress.snapTo(0f)
            revealProgress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Theme Reveal Background Canvas
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = displayedBg)
            val radius = size.height * 1.5f * revealProgress.value
            if (radius > 0f) {
                drawCircle(
                    color = upcomingBg,
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height)
                )
            }
        }

        // Subtle liquid glass atmospheric background effect
        com.example.ui.components.LiquidGlassBackground(
            isLightMode = isLightMode,
            modifier = Modifier.fillMaxSize()
        )

        // Gemini-style Ambient Atmospheric Voice Glow & Shifting Aurora Wave
        com.example.ui.components.GeminiAmbientVoiceGlow(
            isActive = isVoiceListening,
            rmsValue = voiceRms
        )

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                SessionDrawerContent(
                    sessions = allSessions,
                    activeSessionId = activeSessionId,
                    drawerState = drawerState,
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                        val target = allSessions.firstOrNull { it.id == id }
                        val isVideoProj = target != null && (target.mode == "video_project" || com.example.util.ProjectDataManager.hasVideoProject(context, target.title))
                        val isProj = target != null && (target.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, target.title))
                        if (isVideoProj) {
                            val savedVideo = com.example.util.ProjectDataManager.getVideoProject(context, target!!.title)
                                ?: com.example.ui.components.VideoProjectData(projectName = target.title)
                            activeVideoProjectData = savedVideo
                            showVideoStudio = true
                        } else if (isProj) {
                            val savedProj = com.example.util.ProjectDataManager.getProject(context, target!!.title)
                                ?: com.example.ui.components.ProjectData(
                                    appName = target.title,
                                    packageName = "com.example.${target.title.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                                    minSdk = 21,
                                    targetSdk = 34,
                                    buildStudio = "SAIF AI Studio",
                                    language = "Java",
                                    iconBitmap = com.example.util.ProjectDataManager.loadProjectIcon(context, target.title)
                                )
                            activeProjectData = savedProj
                            showProjectEditor = true
                        }
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onRenameSession = { id, title -> viewModel.renameSession(id, title, context) },
                    onDeleteSession = { id -> viewModel.deleteSession(id, context) },
                    onClearAll = {
                        viewModel.clearAllSessions()
                        scope.launch { drawerState.close() }
                    },
                    onOpenAdminDashboard = onOpenAdminDashboard
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    SaifHeader(
                        currentMode = currentMode,
                        isLightMode = isLightMode,
                        onToggleTheme = onToggleTheme,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenModeSelector = { showModeSelector = true },
                        onNewChat = { viewModel.createNewSession() },
                        onOpenSettings = { showSettings = true },
                        onPlusClick = { showConfigureDialog = true },
                        onNewVideoProjectClick = { showNewProjectSheet = true },
                        onAutomationClick = {
                            // The red "+" button is gone. SAIF AI AUTOMATION now lives behind the purple mic.
                            showLiveModeSheet = true
                        },
                        isFilterBookmarked = isFilterBookmarked,
                        onToggleBookmarkFilter = { viewModel.toggleBookmarkFilter() },
                        onUpgradeClick = { showUpgradeDialog = true },
                        onOpenAdminDashboard = onOpenAdminDashboard,
                        onOpenPowerTools = { showPowerTools = true }
                    )
                },
                bottomBar = {
                    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
                    val extraBottomPadding by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isImeVisible) 4.dp else 10.dp,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.6f, // Medium bouncy
                            stiffness = 300f // Nice and smooth
                        ),
                        label = "ImeExtraPadding"
                    )
                    // Explicit navigationBarsPadding keeps chatbox comfortably separated from system nav bar
                    // imePadding smoothly lifts above soft keyboard when typing
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = extraBottomPadding.coerceAtLeast(0.dp))
                            .onGloballyPositioned { coordinates ->
                                bottomBarHeightPx = coordinates.size.height
                            }
                    ) {
                    AnimatedVisibility(
                        visible = !showSettings && !showModeSelector,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val selectedAttachments by viewModel.selectedAttachments.collectAsState()
                        val galleryLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.PickMultipleVisualMedia(8)
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                viewModel.addAttachments(uris)
                            }
                        }
                        val fileLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenMultipleDocuments()
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                viewModel.addAttachments(uris)
                            }
                        }

                        val videoLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.PickVisualMedia()
                        ) { uri ->
                            uri?.let { viewModel.addAttachments(listOf(it)) }
                        }
                        
                        var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
                        val context = LocalContext.current
                        val cameraLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.TakePicture()
                        ) { success ->
                            if (success) {
                                tempCameraUri?.let { viewModel.addAttachments(listOf(it)) }
                            }
                        }

                        val cameraPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted) {
                                try {
                                    val photoFile = java.io.File(context.cacheDir, "camera").apply { mkdirs() }.let {
                                        java.io.File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", it)
                                    }
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    tempCameraUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Camera open karne me samasya: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Camera permission zaruri hai photo lene ke liye", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }

                        val micPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted) {
                                showLiveModeSheet = true
                            } else {
                                android.widget.Toast.makeText(context, "Microphone permission is required for Live Voice", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Attachments Display
                            if (selectedAttachments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedAttachments.forEach { uri ->
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(com.example.ui.theme.SaifTheme.colors.surfaceCard)
                                    ) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Attachment",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize()
                                        )
                                        IconButton(
                                            onClick = { viewModel.removeAttachment(uri) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp)
                                                .padding(2.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        SaifInputBar(
                            input = input,
                            onInputChange = { viewModel.onInputChange(it) },
                            isGenerating = isGenerating,
                            hasAttachments = selectedAttachments.isNotEmpty(),
                            onSend = { viewModel.sendMessage(context) },
                            onStop = { viewModel.stopGeneration() },
                            onVoiceRecorded = { file -> viewModel.processAudioToText(context, file) },
                            onVoiceListeningStateChange = { listening, rms ->
                                isVoiceListening = listening
                                voiceRms = rms
                            },
                            stopVoiceListeningTrigger = stopVoiceListeningTrigger,
                            onStartLiveVoice = {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    // Two options now: Normal voice or Screen share. The automation engine runs both.
                                    showLiveModeSheet = true
                                }
                            },
                            onGalleryClick = { 
                                galleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                ) 
                            },
                            onFileClick = { 
                                fileLauncher.launch(arrayOf("*/*")) 
                            },
                            onVideoClick = { 
                                videoLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                ) 
                            },
                            onCameraClick = {
                                val hasCamPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (hasCamPerm) {
                                    try {
                                        val photoFile = java.io.File(context.cacheDir, "camera").apply { mkdirs() }.let {
                                            java.io.File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", it)
                                        }
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            photoFile
                                        )
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Camera open karne me samasya: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Inner box for the main chat content with proper padding
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (displayedMessages.isEmpty() && !isGenerating) {
                        // Empty Welcome State with Hero & Quick Chips
                        QuickPromptChips(
                            currentMode = currentMode,
                            onSelectPrompt = { promptText ->
                                viewModel.sendMessage(context, promptText)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Message Stream
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = displayedMessages,
                                key = { _, msg -> msg.id }
                            ) { index, msg ->
                                val isLatestAi = (msg.role == "model") &&
                                        (index == displayedMessages.indexOfLast { it.role == "model" })
                                Box {
                                    MessageBubble(
                                        message = msg,
                                        isLatestAiMessage = isLatestAi && !isGenerating,
                                        onToggleBookmark = { viewModel.toggleBookmark(msg) },
                                        onRegenerate = { viewModel.regenerateLastResponse(context) }
                                    )
                                }
                            }
                            // Live streaming bubble if model is currently generating
                            if (isGenerating) {
                                item(key = "streaming_response") {
                                    if (currentStreamingContent.isNotEmpty()) {
                                        val tempMsg = ChatMessageEntity(
                                            sessionId = activeSessionId ?: "",
                                            role = "model",
                                            content = currentStreamingContent
                                        )
                                        MessageBubble(
                                            message = tempMsg,
                                            isLatestAiMessage = false,
                                            onToggleBookmark = {},
                                            onRegenerate = {}
                                        )
                                    } else {
                                        // Typing indicator with bouncing dots
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            TypingIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                
                // Content-area tap dismissal when mic is listening
                if (isVoiceListening) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = paddingValues.calculateBottomPadding())
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        stopVoiceListeningTrigger++
                                    }
                                )
                            }
                    )
                }

                // Full-screen LIVE conversation: the SAIF AI AUTOMATION 3D orb, inside SAIF AI's own UI.
                // (This replaced the old sparkle-star FloatingVoiceOverlay animation.)
                val isAutomationLive by com.jarvis.assistant.SaifAutomation.isLiveRunning.collectAsState()
                androidx.compose.animation.AnimatedVisibility(
                    visible = isAutomationLive,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    com.example.ui.components.SaifLiveConversationPanel(
                        isLightMode = isLightMode,
                        onClose = { com.jarvis.assistant.SaifAutomation.stopLive(context) },
                        onRequestScreenShare = {
                            com.example.ui.components.SaifLiveSession.pendingScreenShare = true
                            screenCapturePermissionLauncher.launch(
                                (context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                                    as android.media.projection.MediaProjectionManager).createScreenCaptureIntent()
                            )
                        }
                    )
                }

                // Round 15: POWER TOOLS — full screen, above everything.
                if (showPowerTools) {
                    com.example.ui.components.SaifPowerToolsScreen(
                        onClose = { showPowerTools = false },
                        onStartLiveTest = {
                            showPowerTools = false
                            showLiveModeSheet = true
                        }
                    )
                }

                // Normal / Screen-share chooser behind the purple mic.
                if (showLiveModeSheet) {
                    com.example.ui.components.SaifLiveModeSheet(
                        isLightMode = isLightMode,
                        onDismiss = { showLiveModeSheet = false },
                        onNeedScreenCapturePermission = {
                            showLiveModeSheet = false
                            com.example.ui.components.SaifLiveSession.pendingScreenShare = true
                            screenCapturePermissionLauncher.launch(
                                (context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                                    as android.media.projection.MediaProjectionManager).createScreenCaptureIntent()
                            )
                        }
                    )
                }
            }
        } // Close Scaffold

        // Global unwanted touch dismissal: When mic is actively listening,
        // tapping ANY unwanted area outside the input bar stops the mic!
        if (isVoiceListening) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bottomPadding = if (bottomBarHeightPx > 0) {
                with(density) { bottomBarHeightPx.toDp() }
            } else {
                96.dp
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                stopVoiceListeningTrigger++
                            }
                        )
                    }
            )
        }
    } // Close Box wrapping Scaffold
        // Mode Selector Sheet
        if (showModeSelector) {
            ModeSelectorSheet(
                currentMode = currentMode,
                onSelectMode = { newMode -> viewModel.setMode(newMode) },
                onDismiss = { showModeSelector = false }
            )
        }
        // Settings Bottom Sheet
        if (showSettings) {
            SettingsSheet(
                temperature = temperature,
                customSystemPrompt = customSystemPrompt,
                onSaveSettings = { temp, prompt -> viewModel.updateSettings(temp, prompt) },
                onClearCurrentChat = { viewModel.clearCurrentSessionMessages() },
                onDismiss = { showSettings = false },
                onOpenAdminDashboard = onOpenAdminDashboard
            )
        }
        // Upgrade Premium Dialog (Overlay)
        AnimatedVisibility(
            visible = showUpgradeDialog,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f)
        ) {
            com.example.ui.components.UpgradeDialog(
                onDismiss = { showUpgradeDialog = false }
            )
        }
        
        AnimatedVisibility(
            visible = showConfigureDialog,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f)
        ) {
            com.example.ui.components.ConfigureProjectDialog(
                isDarkTheme = !isLightMode,
                onDismiss = { showConfigureDialog = false },
                onCreateProject = { appName, packageName, minSdk, targetSdk, buildStudio, language, iconBitmap ->
                    showConfigureDialog = false
                    val projectData = com.example.ui.components.ProjectData(
                        appName = appName,
                        packageName = packageName,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        buildStudio = buildStudio,
                        language = language,
                        iconBitmap = iconBitmap
                    )
                    activeProjectData = projectData
                    showProjectEditor = true
                    android.widget.Toast.makeText(
                        context,
                        "Opening Code Studio for '$appName'...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    viewModel.createProjectSession(
                        appName = appName,
                        packageName = packageName,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        buildStudio = buildStudio,
                        language = language,
                        iconBitmap = iconBitmap,
                        context = context
                    )
                }
            )
        }
        } // Close ModalNavigationDrawer

        // Floating "Open Code Studio" Quick Action when active project exists
        AnimatedVisibility(
            visible = activeProjectData != null && !showProjectEditor && !showConfigureDialog,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -30 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -30 }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 58.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF00A86B),
                shadowElevation = 6.dp,
                modifier = Modifier.clickable { showProjectEditor = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Studio",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Fullscreen Animated Code Studio Editor matching user's design
        AnimatedVisibility(
            visible = showProjectEditor && activeProjectData != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(200))
        ) {
            activeProjectData?.let { project ->
                com.example.ui.components.ProjectEditorScreen(
                    project = project,
                    isDarkTheme = !isLightMode,
                    onClose = { showProjectEditor = false },
                    onUpdateProject = { updated ->
                        activeProjectData = updated
                        val currId = activeSessionId
                        if (currId != null && updated.appName != project.appName) {
                            viewModel.renameSession(currId, updated.appName)
                        }
                    }
                )
            }
        }

        // The purple "+" sheet: every way to start a project, plus recent projects
        if (showNewProjectSheet) {
            com.example.ui.components.NewProjectSheet(
                onDismiss = { showNewProjectSheet = false },
                onOpenRecent = { name ->
                    showNewProjectSheet = false
                    val existing = com.example.util.ProjectDataManager.getVideoProject(context, name)
                        ?: com.example.ui.components.VideoProjectData(projectName = name)
                    com.example.ui.components.RecentProjects.touch(context, existing.projectName)
                    activeVideoProjectData = existing
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(existing, context)
                },
                onChoice = { choice ->
                    showNewProjectSheet = false
                    if (choice == com.example.ui.components.NewProjectChoice.MORE) {
                        showVideoProjectDialog = true
                    } else if (claimCreateTap()) {
                        val (base, wf) = when (choice) {
                            com.example.ui.components.NewProjectChoice.AI_VIDEO -> "AI Video" to com.example.studio.director.DirectorWorkflow.ZERO_TO_VIDEO
                            com.example.ui.components.NewProjectChoice.EDIT_WITH_AI -> "AI Edit" to com.example.studio.director.DirectorWorkflow.VIDEO_TO_EDITING
                            com.example.ui.components.NewProjectChoice.AI_VOICE_ON_VIDEO -> "AI Voice Video" to com.example.studio.director.DirectorWorkflow.VIDEO_EDIT_WITH_VOICE
                            com.example.ui.components.NewProjectChoice.VOICE_OVER_TO_VIDEO -> "Voice-over Video" to com.example.studio.director.DirectorWorkflow.VOICE_OVER_TO_VIDEO
                            else -> "Untitled Video" to null
                        }
                        val proj = com.example.ui.components.VideoProjectData(
                            projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(context, base),
                            clips = emptyList()
                        )
                        com.example.studio.ui.StudioLaunch.pending = wf
                        com.example.ui.components.RecentProjects.touch(context, proj.projectName)
                        activeVideoProjectData = proj
                        showVideoStudio = true
                        viewModel.createVideoProjectSession(proj, context)
                    }
                }
            )
        }

        // Video Project Creation Choice Dialog (With Video / Without Video)
        if (showVideoProjectDialog) {
            com.example.ui.components.VideoProjectCreateDialog(
                isDarkTheme = !isLightMode,
                onDismiss = { showVideoProjectDialog = false },
                onSelectWithMedia = { projectName ->
                    if (!claimCreateTap()) return@VideoProjectCreateDialog
                    showVideoProjectDialog = false
                    pendingVideoProjectName = projectName
                    try {
                        videoProjectMediaPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    } catch (e: Exception) {
                        // Fallback to standard timeline
                        val fallbackProj = com.example.ui.components.VideoProjectData(
                            projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(context, projectName)
                        )
                        activeVideoProjectData = fallbackProj
                        showVideoStudio = true
                        viewModel.createVideoProjectSession(fallbackProj, context)
                    }
                },
                onSelectWithoutMedia = { projectName ->
                    if (!claimCreateTap()) return@VideoProjectCreateDialog
                    showVideoProjectDialog = false
                    val blankProj = com.example.ui.components.VideoProjectData(
                        projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(context, projectName),
                        clips = emptyList()
                    )
                    activeVideoProjectData = blankProj
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(blankProj, context)
                    android.widget.Toast.makeText(
                        context,
                        "Opened blank timeline canvas",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onOpenProject = { projectName ->
                    showVideoProjectDialog = false
                    val existing = com.example.util.ProjectDataManager.getVideoProject(context, projectName)
                        ?: com.example.ui.components.VideoProjectData(projectName = projectName)
                    activeVideoProjectData = existing
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(existing, context)
                },
                onCreateFromPrompt = { userPrompt ->
                    showVideoProjectDialog = false
                    val cleanTitle = com.example.util.ProjectDataManager.uniqueVideoProjectName(
                        context, userPrompt.take(28).trim().ifBlank { "AI Video Project" }
                    )
                    val initialProj = com.example.ui.components.VideoProjectData(
                        projectName = cleanTitle,
                        textOverlay = cleanTitle.uppercase(),
                        clips = emptyList()
                    )
                    activeVideoProjectData = initialProj
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(initialProj, context)

                    android.widget.Toast.makeText(
                        context,
                        "🎬 Autonomous AI Director: Researching & assembling '$cleanTitle'...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    scope.launch {
                        val result = com.example.ui.components.SaifAiEditingEngine.generateAutonomousDirectorEdl(
                            context = context,
                            prompt = userPrompt,
                            rawVideoUrl = null
                        )
                        result.onSuccess { (_, proj) ->
                            activeVideoProjectData = proj
                            com.example.util.ProjectDataManager.saveVideoProject(context, proj)
                            viewModel.createVideoProjectSession(proj, context)
                            android.widget.Toast.makeText(
                                context,
                                "✅ Video assembled from prompt with Telegram assets!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { err ->
                            // Algorithmic synthesis fallback: guarantees project is never blank or failing
                            val catalog = com.example.ui.components.TelegramAssetManager.loadMasterCatalog(context)
                            val fallbackEdl = com.example.ui.components.SaifAiEditingEngine.synthesizeAlgorithmicEdl(userPrompt, catalog)
                            val fallbackProj = com.example.ui.components.SaifAiEditingEngine.convertEdlToProject(context, fallbackEdl, userPrompt)
                            activeVideoProjectData = fallbackProj
                            com.example.util.ProjectDataManager.saveVideoProject(context, fallbackProj)
                            viewModel.createVideoProjectSession(fallbackProj, context)
                            android.widget.Toast.makeText(
                                context,
                                "🎬 Video assembled via Autonomous Director engine!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }

        // Fullscreen Video Timeline Studio Screen (with SAIF AI EDITING chat & Export studio)
        AnimatedVisibility(
            visible = showVideoStudio && activeVideoProjectData != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(200))
        ) {
            activeVideoProjectData?.let { videoProject ->
                // Keyed by the permanent project id (never the name). A saved studio project, including all AI work,
                // is loaded first; legacy migration happens only when nothing was saved yet.
                val studioProject = remember(videoProject.projectId) {
                    com.example.studio.model.StudioProjectResolver.resolve(context, videoProject)
                }
                val studioViewModel = remember(studioProject.id) {
                    com.example.studio.state.StudioViewModel(context, studioProject)
                }
                com.example.studio.ui.StudioScreen(
                    viewModel = studioViewModel,
                    onBack = { showVideoStudio = false },
                    onExportClick = {
                        android.widget.Toast.makeText(context, "Full Video Export unlocks in Level 2", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Screen 4-Corner & Perimeter Aura Glow (Activates on Wake Word)
        com.example.ui.components.GeminiCornerGlow(
            triggerTimestamp = cornerGlowTrigger
        )

        // Gemini Floating Assistant Bottom Sheet
        if (isAssistantOverlayActive) {
            com.example.ui.components.GeminiAssistantSheet(
                initialPrompt = initialSpokenQuery,
                onDismiss = {
                    com.example.util.WakeWordManager.dismissAssistant()
                }
            )
        }

        // On-demand Permission Request Dialog for voice features (Calls, Contacts, SMS, Accessibility, Notifications)
        com.example.ui.components.OnDemandPermissionDialog()
    } // Close Box
}


private var lastCreateTapMs = 0L
/** Ignores a second "create project" tap that arrives within ~1.2 s of the first, so repeated taps cannot make duplicate projects. */
private fun claimCreateTap(): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastCreateTapMs < 1200L) return false
    lastCreateTapMs = now
    return true
}
