package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Master SAIF AI EDITING Modal (v2 · Detailed)
 * Handles:
 * 1. Prompt Se Edit (Level 5)
 * 2. Video Dekar Edit (Level 5 Reference)
 * 3. Script Se Video Banao (Level 15 Script Writer)
 * 4. Ek Line Se Video Banao (Level 18 Zero-to-Video)
 * 5. AI Pro Tools (Level 6: Auto-Cut, Captions, B-Roll, Reframe)
 * 6. Streaming Monospace Code / JSON Plan View (Claude-style code generation)
 * 7. Level 8 "How SAIF AI Editing Works" Architecture Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaifAiEditingModal(
    project: VideoProjectData,
    onDismiss: () -> Unit,
    onApplyEdit: (VideoProjectData) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 0: Prompt, 1: Reference, 2: Script Writer, 3: Zero-to-Video, 4: AI Pro Tools, 5: Telegram Assets
    var selectedTab by remember { mutableIntStateOf(0) }

    // Telegram Asset Tab State
    var telegramSearchKeyword by remember { mutableStateOf("") }
    var telegramSelectedType by remember { mutableStateOf("all") }
    var telegramAssetsList by remember { mutableStateOf<List<TelegramAssetItem>>(emptyList()) }
    var isSyncingTelegram by remember { mutableStateOf(false) }

    // Prompt tab state
    var promptText by remember { mutableStateOf("") }
    var referenceNote by remember { mutableStateOf("") }
    var isMultiVariant by remember { mutableStateOf(false) }

    // Script tab state
    var scriptIdea by remember { mutableStateOf("5 Secrets to Edit Videos 10x Faster") }
    var scriptDuration by remember { mutableFloatStateOf(30.0f) }
    var scriptTone by remember { mutableStateOf("Energetic") }
    var generatedScript by remember { mutableStateOf<VideoScript?>(null) }
    var isGeneratingScript by remember { mutableStateOf(false) }

    // Zero-to-Video state
    var zeroPrompt by remember { mutableStateOf("Cyberpunk street food vlog with neon bloom and bass drops") }
    var isZeroProcessing by remember { mutableStateOf(false) }
    var zeroCurrentStep by remember { mutableStateOf("") }
    var zeroProgress by remember { mutableFloatStateOf(0f) }

    // Streaming Code View state
    var isStreamingCodeView by remember { mutableStateOf(false) }
    var streamedCodeText by remember { mutableStateOf("") }
    var streamPhaseText by remember { mutableStateOf("Initializing neural planner...") }
    var streamProgress by remember { mutableFloatStateOf(0.1f) }
    var readyPlan by remember { mutableStateOf<VideoEditPlan?>(null) }

    // How It Works dialog
    var showHowItWorksDialog by remember { mutableStateOf(false) }

    // Auto-Cut Silence dialog
    var showAutoCutDialog by remember { mutableStateOf(false) }
    var silenceSegments by remember { mutableStateOf<List<SilenceSegment>>(emptyList()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF100E17),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(44.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = Color(0xFF00DAF3).copy(alpha = 0.5f)
            ) {}
        },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                                    listOf(Color(0xFF7B2BFF), Color(0xFF00DAF3))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SAIF AI EDITING",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x3500DAF3)
                            ) {
                                Text(
                                    text = "v2.4 MASTER",
                                    color = Color(0xFF00DAF3),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Code Generation & Multi-Track Neural Execution",
                            color = Color(0xFF9C93B5),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showHowItWorksDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B1528))
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "How it works",
                            tint = Color(0xFF00DAF3),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B1528))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFCCC3D9),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // If in Streaming Code View, show the Monospace Code Generator
            if (isStreamingCodeView) {
                StreamingCodePlanView(
                    codeText = streamedCodeText,
                    phase = streamPhaseText,
                    progress = streamProgress,
                    isReady = readyPlan != null,
                    onCancel = {
                        isStreamingCodeView = false
                        streamedCodeText = ""
                        readyPlan = null
                        Toast.makeText(context, "AI Edit cancelled. Timeline untouched.", Toast.LENGTH_SHORT).show()
                    },
                    onApply = {
                        readyPlan?.let { plan ->
                            val updated = SaifAiEditingEngine.executePlan(project, plan)
                            onApplyEdit(updated)
                            Toast.makeText(context, "✅ AI Edit successfully rendered to timeline!", Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    }
                )
            } else if (isZeroProcessing) {
                // Zero-to-Video Progress Screen
                ZeroToVideoProgressView(
                    step = zeroCurrentStep,
                    progress = zeroProgress,
                    onCancel = {
                        isZeroProcessing = false
                        Toast.makeText(context, "Generation cancelled.", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // Master 5-Tab Selector
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF14101C),
                    contentColor = Color(0xFF00DAF3),
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedTab])
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(Color(0xFF00DAF3))
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    val tabTitles = listOf("⚡ Prompt Edit", "🎬 Video Reference", "📝 Script Writer", "✨ Zero-to-Video", "🛠️ AI Pro", "✈️ Telegram Assets")
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) Color(0xFF00DAF3) else Color(0xFF9C93B5)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content Switcher
                when (selectedTab) {
                    0 -> PromptSeEditTab(
                        prompt = promptText,
                        onPromptChange = { promptText = it },
                        refNote = referenceNote,
                        onRefNoteChange = { referenceNote = it },
                        isMultiVariant = isMultiVariant,
                        onToggleMultiVariant = { isMultiVariant = it },
                        onStartGeneration = {
                            if (promptText.isBlank()) {
                                Toast.makeText(context, "Please enter an editing prompt!", Toast.LENGTH_SHORT).show()
                                return@PromptSeEditTab
                            }
                            isStreamingCodeView = true
                            streamedCodeText = ""
                            readyPlan = null
                            scope.launch {
                                SaifAiEditingEngine.planAndStreamEdit(
                                    context = context,
                                    project = project,
                                    userPrompt = promptText,
                                    referenceNote = referenceNote.ifBlank { null },
                                    onProgressPhase = { phase, prog ->
                                        streamPhaseText = phase
                                        streamProgress = prog
                                    },
                                    onStreamChunk = { chunk ->
                                        streamedCodeText += chunk
                                    }
                                ).onSuccess { plan ->
                                    readyPlan = plan
                                    streamPhaseText = "Validation Complete: ${plan.summary}"
                                    streamProgress = 1.0f
                                }
                            }
                        }
                    )
                    1 -> VideoDekarEditTab(
                        onSelectReference = { note ->
                            promptText = "Match style from reference: $note"
                            selectedTab = 0
                        }
                    )
                    2 -> ScriptWriterTab(
                        idea = scriptIdea,
                        onIdeaChange = { scriptIdea = it },
                        duration = scriptDuration,
                        onDurationChange = { scriptDuration = it },
                        tone = scriptTone,
                        onToneChange = { scriptTone = it },
                        script = generatedScript,
                        isGenerating = isGeneratingScript,
                        onGenerateScript = {
                            isGeneratingScript = true
                            scope.launch {
                                val res = SaifAiEditingEngine.generateScript(scriptIdea, scriptDuration, scriptTone, "Hindi & English")
                                generatedScript = res.getOrNull()
                                isGeneratingScript = false
                            }
                        },
                        onApplyScriptToTimeline = { script ->
                            promptText = "Edit project to match script: ${script.title}"
                            selectedTab = 0
                            Toast.makeText(context, "Script attached to AI Editor!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    3 -> ZeroToVideoTab(
                        prompt = zeroPrompt,
                        onPromptChange = { zeroPrompt = it },
                        onStartZeroToVideo = {
                            if (zeroPrompt.isBlank()) {
                                Toast.makeText(context, "Enter an idea prompt!", Toast.LENGTH_SHORT).show()
                                return@ZeroToVideoTab
                            }
                            isZeroProcessing = true
                            zeroProgress = 0.1f
                            scope.launch {
                                val res = SaifAiEditingEngine.generateZeroToVideo(
                                    context = context,
                                    prompt = zeroPrompt,
                                    onStepUpdate = { step, prog ->
                                        zeroCurrentStep = step
                                        zeroProgress = prog
                                    }
                                )
                                res.onSuccess { newProject ->
                                    onApplyEdit(newProject)
                                    isZeroProcessing = false
                                    Toast.makeText(context, "🎉 Full video ready on timeline!", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }.onFailure {
                                    isZeroProcessing = false
                                    Toast.makeText(context, "Error generating video: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    4 -> AiProToolsTab(
                        project = project,
                        onAutoCut = {
                            silenceSegments = SaifAiEditingEngine.detectSilenceSegments(project.clips)
                            showAutoCutDialog = true
                        },
                        onAutoCaptions = {
                            val captions = SaifAiEditingEngine.generateWordLevelAutoCaptions(project)
                            val updated = project.copy(autoCaptions = captions)
                            onApplyEdit(updated)
                            Toast.makeText(context, "✅ AI Word-Level Captions generated (${captions.size} segments)!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        onAutoReframe = { ratio ->
                            val updated = SaifAiEditingEngine.applyAutoReframe(project, ratio)
                            onApplyEdit(updated)
                            Toast.makeText(context, "✅ Reframed video to $ratio", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        onAutoDuck = {
                            val updated = SaifAiEditingEngine.applySmartAudioDucking(project, 0.30f)
                            onApplyEdit(updated)
                            Toast.makeText(context, "✅ Smart audio-ducking enabled", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                    5 -> TelegramAssetsBrowserTab(
                        searchQuery = telegramSearchKeyword,
                        onQueryChange = { telegramSearchKeyword = it },
                        selectedType = telegramSelectedType,
                        onTypeChange = { telegramSelectedType = it },
                        assets = telegramAssetsList,
                        isSyncing = isSyncingTelegram,
                        onSync = {
                            isSyncingTelegram = true
                            scope.launch {
                                val list = TelegramAssetManager.syncChannelAssets(context = context)
                                telegramAssetsList = list
                                isSyncingTelegram = false
                                Toast.makeText(context, "Synced ${list.size} assets from Master Catalog & Channel!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUseAsset = { asset ->
                            scope.launch {
                                val dl = TelegramAssetManager.searchAndDownloadAsset(
                                    context = context,
                                    userId = "user_default",
                                    assetType = asset.type,
                                    keywords = listOf(asset.title)
                                )
                                val path = dl?.localCachedPath ?: ""
                                if (asset.type == "audio") {
                                    val updated = project.copy(audioTrackName = asset.title)
                                    onApplyEdit(updated)
                                    Toast.makeText(context, "🎵 Attached ${asset.title} as Background Track!", Toast.LENGTH_SHORT).show()
                                } else if (asset.type == "video" || asset.type == "image") {
                                    val updated = project.copy(
                                        overlayMediaUri = path,
                                        activePipPosition = "Bottom-Right"
                                    )
                                    onApplyEdit(updated)
                                    Toast.makeText(context, "🎬 Added ${asset.title} as PIP Overlay Layer!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val updated = project.copy(
                                        textOverlay = asset.title.uppercase(),
                                        textFontFamily = "Monospace"
                                    )
                                    onApplyEdit(updated)
                                    Toast.makeText(context, "Applied ${asset.title} to Project!", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }

    // --- How SAIF AI Editing Works Dialog (Level 8) ---
    if (showHowItWorksDialog) {
        HowItWorksDialog(onDismiss = { showHowItWorksDialog = false })
    }

    // --- Silence Auto-Cut Dialog (Level 6) ---
    if (showAutoCutDialog) {
        SilenceAutoCutDialog(
            segments = silenceSegments,
            onDismiss = { showAutoCutDialog = false },
            onApply = { approvedSegments ->
                val updated = SaifAiEditingEngine.applySilenceAutoCut(project, approvedSegments)
                onApplyEdit(updated)
                showAutoCutDialog = false
                Toast.makeText(context, "✂️ Removed ${approvedSegments.size} silent pauses!", Toast.LENGTH_LONG).show()
                onDismiss()
            }
        )
    }
}

// ---------------- SUB-COMPONENTS ----------------

@Composable
fun PromptSeEditTab(
    prompt: String,
    onPromptChange: (String) -> Unit,
    refNote: String,
    onRefNoteChange: (String) -> Unit,
    isMultiVariant: Boolean,
    onToggleMultiVariant: (Boolean) -> Unit,
    onStartGeneration: () -> Unit
) {
    val exampleChips = listOf(
        "Is video ko cinematic bana do, background blur karo, rain effect add karo",
        "Fast-cut Phonk montage with bass drops & chromatic split",
        "Cyberpunk neon bloom & glitch text hook",
        "Retro 90s VHS tape aesthetic with warm analog audio"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Prompt Se Edit Karo (Level 5)",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                placeholder = {
                    Text(
                        text = "Describe your edits in Hindi or English (e.g. 'cinematic teal orange grade karo, 0:05 par neon caption aur whoosh sound dalo')...",
                        fontSize = 13.sp,
                        color = Color(0xFF9C93B5)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00DAF3),
                    unfocusedBorderColor = Color(0x3500DAF3),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1B1528),
                    unfocusedContainerColor = Color(0xFF1B1528)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )
        }

        item {
            Text(
                text = "⚡ Quick Prompt Suggestions:",
                color = Color(0xFFCCC3D9),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(exampleChips) { chip ->
                    Surface(
                        onClick = { onPromptChange(chip) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E182D),
                        border = BorderStroke(1.dp, Color(0x307B2BFF))
                    ) {
                        Text(
                            text = chip,
                            color = Color(0xFFD1BCFF),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            // Multi-Variant Generator Toggle (Level 10)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B1528),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("3 Options Dikhao (Multi-Variant)", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Generate Fast-Cut, Cinematic & Minimal variants", color = Color(0xFF9C93B5), fontSize = 10.5.sp)
                    }
                    Switch(
                        checked = isMultiVariant,
                        onCheckedChange = onToggleMultiVariant,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00DAF3),
                            checkedTrackColor = Color(0xFF7B2BFF)
                        )
                    )
                }
            }
        }

        item {
            // Big Glowing CTA Button
            Button(
                onClick = onStartGeneration,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF7B2BFF), Color(0xFF00DAF3))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GENERATE AI EDIT NOW",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun VideoDekarEditTab(
    onSelectReference: (note: String) -> Unit
) {
    var refNote by remember { mutableStateOf("Mirror pacing, transitions, and cinematic tone from reference video") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Video Dekar Edit Karo (Reference Mode)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Upload or pick a reference clip. AI samples keyframes + audio tempo and replicates the aesthetic onto your clips.", color = Color(0xFF9C93B5), fontSize = 12.sp)

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1B1528),
            border = BorderStroke(1.dp, Color(0x4000DAF3)),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select Reference Video (≤ 60s)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Samples 8 frames + audio energy summary", color = Color(0xFF9C93B5), fontSize = 11.sp)
            }
        }

        OutlinedTextField(
            value = refNote,
            onValueChange = { refNote = it },
            label = { Text("What to copy from reference?", color = Color(0xFF00DAF3)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1B1528),
                unfocusedContainerColor = Color(0xFF1B1528)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onSelectReference(refNote) },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Extract Style & Apply Edit", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ScriptWriterTab(
    idea: String,
    onIdeaChange: (String) -> Unit,
    duration: Float,
    onDurationChange: (Float) -> Unit,
    tone: String,
    onToneChange: (String) -> Unit,
    script: VideoScript?,
    isGenerating: Boolean,
    onGenerateScript: () -> Unit,
    onApplyScriptToTimeline: (VideoScript) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("AI Script Writer (Level 15)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Turn a raw thought into a high-retention video script with Hook, Story, Key Points, and CTA.", color = Color(0xFF9C93B5), fontSize = 12.sp)
        }

        item {
            OutlinedTextField(
                value = idea,
                onValueChange = onIdeaChange,
                label = { Text("Script Topic / Idea", color = Color(0xFF00DAF3)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1B1528),
                    unfocusedContainerColor = Color(0xFF1B1528)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Duration Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1B1528),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Duration", color = Color(0xFF9C93B5), fontSize = 10.sp)
                        Text("${duration.toInt()}s Target", color = Color(0xFF00DAF3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Tone Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1B1528),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Tone", color = Color(0xFF9C93B5), fontSize = 10.sp)
                        Text(tone, color = Color(0xFF00DAF3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onGenerateScript,
                enabled = !isGenerating,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Writing Neural Script...", color = Color.White)
                } else {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Structured Script", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        script?.let { sc ->
            item {
                Text("Generated Script: ${sc.title}", color = Color(0xFF00DAF3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            itemsIndexed(sc.sections) { idx, sec ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B1528),
                    border = BorderStroke(1.dp, Color(0x307B2BFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF7B2BFF)
                            ) {
                                Text(
                                    text = sec.kind.uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text("${sec.estSeconds}s • ${sec.emotion}", color = Color(0xFF00DAF3), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(sec.narration, color = Color.White, fontSize = 12.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🎬 Visual: ${sec.visualHint}", color = Color(0xFF9C93B5), fontSize = 10.5.sp)
                    }
                }
            }
            item {
                Button(
                    onClick = { onApplyScriptToTimeline(sc) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00DAF3)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Build Video from this Script", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ZeroToVideoTab(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onStartZeroToVideo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Zero-to-Video Mode (Level 18)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Give a single line idea. SAIF AI automatically orchestrates: Script -> Voice -> Motion Visuals -> SFX -> Multi-Track Timeline.", color = Color(0xFF9C93B5), fontSize = 12.sp)

        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("E.g. 'How to grow on YouTube, 2 min video, motion graphics, sound effects aur hook'") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1B1528),
                unfocusedContainerColor = Color(0xFF1B1528)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1B1528),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Autonomous 5-Stage Orchestrator:", color = Color(0xFF00DAF3), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("1. Script & Hook Generation (Level 15)\n2. Voice Synthesis & Real Timing (Level 14)\n3. Motion Graphics & B-Roll Keyframing\n4. Audio SFX & Smart Ducking (Level 19)\n5. Assembled Timeline ready for manual edits!", color = Color(0xFFCCC3D9), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }

        Button(
            onClick = onStartZeroToVideo,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF7B2BFF), Color(0xFF00DAF3))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BUILD ZERO-TO-VIDEO NOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }
        }
    }
}

@Composable
fun AiProToolsTab(
    project: VideoProjectData,
    onAutoCut: () -> Unit,
    onAutoCaptions: () -> Unit,
    onAutoReframe: (String) -> Unit,
    onAutoDuck: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("AI Pro Intelligence Features (Level 6)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        item {
            ProToolCard(
                title = "AI Auto-Cut (Silence Remover)",
                desc = "Scans waveform, detects silent gaps, and ripple-deletes dead air in one clean undo step.",
                icon = Icons.Default.ContentCut,
                badge = "TIME SAVER",
                onClick = onAutoCut
            )
        }

        item {
            ProToolCard(
                title = "AI Auto-Captions (Speech-to-Text)",
                desc = "Word-level synchronization in Hindi, English, and Hinglish with dynamic karaoke pop highlights.",
                icon = Icons.Default.Subtitles,
                badge = "REALTIME STT",
                onClick = onAutoCaptions
            )
        }

        item {
            ProToolCard(
                title = "Smart Audio Ducking (Auto-Duck)",
                desc = "Automatically dips background soundtrack volume by 75% whenever narration or speech occurs.",
                icon = Icons.Default.VolumeDown,
                badge = "AUDIO DSP",
                onClick = onAutoDuck
            )
        }

        item {
            ProToolCard(
                title = "AI Auto-Reframe (9:16 Shorts / 16:9)",
                desc = "Smooth subject-tracking centroid camera ensuring faces and action stay centered in portrait mode.",
                icon = Icons.Default.CropRotate,
                badge = "MULTI-PLATFORM",
                onClick = { onAutoReframe("9:16") }
            )
        }
    }
}

@Composable
fun ProToolCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1B1528),
        border = BorderStroke(1.dp, Color(0x2500DAF3)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF261D3A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0x357B2BFF)) {
                        Text(badge, color = Color(0xFFD1BCFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Text(desc, color = Color(0xFF9C93B5), fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Streaming Monospace Code / JSON Plan View (Claude-style code generation)
 */
@Composable
fun StreamingCodePlanView(
    codeText: String,
    phase: String,
    progress: Float,
    isReady: Boolean,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        // Status Rail
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF14101C),
            border = BorderStroke(1.dp, Color(0x3500DAF3)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            progress = { progress },
                            color = Color(0xFF00DAF3),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = phase,
                            color = Color(0xFF00DAF3),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color(0xFF7B2BFF),
                    trackColor = Color(0xFF1E182D),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Monospace Code Container (JetBrains Mono)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0A0810),
            border = BorderStroke(1.dp, Color(0x2500DAF3)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00DAF3)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("neural_edit_plan.json", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text("LIVE STREAM", color = Color(0xFF35E6A0), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }

                HorizontalDivider(color = Color(0x20FFFFFF), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (codeText.isBlank()) "// Initializing multi-track neural plan stream..." else codeText,
                        color = Color(0xFF00DAF3),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x40FF4D6A)),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text("Cancel", color = Color(0xFFFF4D6A), fontSize = 12.sp)
            }

            Button(
                onClick = onApply,
                enabled = isReady,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B2BFF),
                    disabledContainerColor = Color(0xFF261D3A)
                ),
                modifier = Modifier
                    .weight(2f)
                    .height(44.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isReady) "Apply Plan to Timeline ✓" else "Writing Plan...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
fun ZeroToVideoProgressView(
    step: String,
    progress: Float,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            color = Color(0xFF00DAF3),
            strokeWidth = 4.dp,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Assembling Zero-to-Video...",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = step,
            color = Color(0xFF00DAF3),
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Text("Cancel", color = Color(0xFFFF4D6A))
        }
    }
}

/**
 * Level 8: Architecture Dialog
 */
@Composable
fun HowItWorksDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14101C),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("How SAIF AI Editing Works", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "AI Edit Pipeline • Prompt to Playable Timeline in 7 Steps:",
                    color = Color(0xFF00DAF3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                val steps = listOf(
                    "1. Your Prompt / Voice / Reference: Context sent with current timeline state.",
                    "2. Neural Planning: LLM writes structured Edit Plan JSON instead of unsafe raw code.",
                    "3. Plan Executor: Dispatches footage operations (speed, crop, color) and overlay graphics.",
                    "4. Live Compositor: Instant timeline preview without long render delays.",
                    "5. Manual Fine-Tuning: Every AI-added item is a normal timeline layer you can adjust.",
                    "6. Timeline Memory: Manual modifications are strictly preserved and never overwritten.",
                    "7. Private & Safe: Media stays on your device. Export renders full-res MP4 directly."
                )

                steps.forEach { stepText ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1B1528),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stepText,
                            color = Color(0xFFE1DEE6),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF))
            ) {
                Text("Got It", color = Color.White)
            }
        }
    )
}

/**
 * Level 6: Silence Auto-Cut Dialog
 */
@Composable
fun SilenceAutoCutDialog(
    segments: List<SilenceSegment>,
    onDismiss: () -> Unit,
    onApply: (List<SilenceSegment>) -> Unit
) {
    val items = remember { mutableStateListOf(*segments.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14101C),
        title = {
            Text("AI Auto-Cut Silence Detection", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                Text(
                    text = "Found ${items.size} silent pauses (~${items.size * 1.2f}s time saved). Uncheck any you wish to keep:",
                    color = Color(0xFF9C93B5),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(items) { index, seg ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1B1528),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = seg.isChecked,
                                        onCheckedChange = { checked ->
                                            items[index] = seg.copy(isChecked = checked)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00DAF3))
                                    )
                                    Column {
                                        Text(seg.clipName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("At ${seg.startSeconds.toInt()}s • Duration: ${seg.durationSeconds}s", color = Color(0xFF9C93B5), fontSize = 10.5.sp)
                                    }
                                }
                                Text("Save ${seg.savedTimeLabel}", color = Color(0xFF35E6A0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(items.filter { it.isChecked }) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00DAF3))
            ) {
                Text("Apply Silence Cuts", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFCCC3D9))
            }
        }
    )
}

/**
 * Telegram Assets Browser Tab
 * Connects directly to the user's Telegram Channel (Bot Token + Channel ID),
 * parses [ASSET_INDEX] captions, enables isolated search by keywords, and
 * downloads files straight into the timeline project.
 */
@Composable
fun TelegramAssetsBrowserTab(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedType: String,
    onTypeChange: (String) -> Unit,
    assets: List<TelegramAssetItem>,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onUseAsset: (TelegramAssetItem) -> Unit
) {
    LaunchedEffect(Unit) {
        if (assets.isEmpty()) {
            onSync()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(bottom = 16.dp)
    ) {
        // Channel Status Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1B1528),
            border = BorderStroke(1.dp, Color(0x6000DAF3)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color(0xFF00DAF3),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Telegram Channel Indexer",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: -1002181329582 · [ASSET_INDEX] Synced",
                            color = Color(0xFF9C93B5),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Button(
                    onClick = onSync,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Text("Sync", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search by title, keywords or tags...", color = Color(0xFF6B6675), fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(18.dp))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00DAF3),
                unfocusedBorderColor = Color(0xFF2E2A3B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Type Filter Chips: All, Audio, Video, Image, Font
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val types = listOf("all" to "All", "audio" to "🎵 Audio", "video" to "🎬 Video", "image" to "🖼️ Image", "font" to "🔤 Font")
            types.forEach { (typeKey, label) ->
                val isSelected = selectedType == typeKey
                Surface(
                    onClick = { onTypeChange(typeKey) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF00DAF3) else Color(0xFF1E1A29),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF00DAF3) else Color(0x30FFFFFF))
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color(0xFFCCC3D9),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filtered Asset List
        val filtered = assets.filter { item ->
            val matchesType = selectedType == "all" || item.type.equals(selectedType, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true) ||
                    item.subject.contains(searchQuery, ignoreCase = true) ||
                    item.searchKeywords.any { it.contains(searchQuery, ignoreCase = true) } ||
                    item.tags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesType && matchesQuery
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFF6B6675), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No matching Telegram assets found.", color = Color(0xFF9C93B5), fontSize = 12.sp)
                    Text("Tap 'Sync' to refresh from channel.", color = Color(0xFF6B6675), fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { asset ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF171322),
                        border = BorderStroke(1.dp, Color(0x3000DAF3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (asset.type) {
                                        "audio" -> Color(0xFF7B2BFF).copy(alpha = 0.3f)
                                        "video" -> Color(0xFF00DAF3).copy(alpha = 0.2f)
                                        "image" -> Color(0xFFFF4D6D).copy(alpha = 0.2f)
                                        else -> Color(0xFFFFC44D).copy(alpha = 0.2f)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val icon = when (asset.type) {
                                            "audio" -> Icons.Default.MusicNote
                                            "video" -> Icons.Default.Movie
                                            "image" -> Icons.Default.Image
                                            else -> Icons.Default.FontDownload
                                        }
                                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(asset.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        text = "${asset.category} · .${asset.extension.uppercase()} · ${asset.searchKeywords.take(3).joinToString(", ")}",
                                        color = Color(0xFF9C93B5),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { onUseAsset(asset) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00DAF3)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Use ⚡", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

