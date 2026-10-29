package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compiler.BuildProgressDialog
import com.example.compiler.CompilerErrorInfo
import com.example.data.local.ProviderSettingsManager
import com.example.data.remote.AIApiUtility
import com.example.builder.AutonomousAppBuilder
import com.example.builder.ProjectDiskBridge
import com.example.util.ProjectChatHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Step action representing a Google AI Studio style step progress.
 */
data class AiStudioProgressStep(
    val title: String,
    val isCompleted: Boolean = false,
    val isRunning: Boolean = false
)

/**
 * Chat message model for SAIF AI coding assistant.
 */
data class BuildAiMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "assistant"
    val content: String,
    val timestamp: String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
    val isCodeSnippet: Boolean = false,
    val steps: List<AiStudioProgressStep> = emptyList(),
    val changeSummary: List<String> = emptyList(),
    val autoAppliedFiles: List<String> = emptyList(),
    val isOptimizing: Boolean = false,
    val isGlitchFix: Boolean = false,
    val thoughts: List<String> = emptyList(),
    val elapsedSeconds: Int = 0,
    val durationText: String = "",
    val activePhase: String = ""
)

/**
 * SAIF AI Coding Assistant Workspace.
 * - Header: Back Arrow, Purple Sparkle, Title "SAIF AI", Active Coding Model Badge, Clear Chat
 * - Reuses the exact same SaifInputBar as the main screen (Speech-to-text, attachments, camera, gallery, live voice)
 * - Real-time Google AI Studio style step-by-step progress with animated green ticks [✓]
 * - Direct file access: automatically applies code modifications to project files on storage
 * - Automatically diagnoses and resolves compiler glitches from the Run action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildAIScreen(
    project: ProjectData,
    editorViewModel: CodeEditorViewModel,
    onBack: () -> Unit,
    initialError: CompilerErrorInfo? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val activeFile by editorViewModel.activeFile.collectAsStateWithLifecycle()
    val editorCode = editorViewModel.editorTextFieldValue.collectAsStateWithLifecycle().value.text

    // Provider state & Coding expert model auto-selection
    val providerState = remember { ProviderSettingsManager.loadState() }
    val activeCodingModel = remember(providerState.activeModel, providerState.providerName) {
        val configured = providerState.activeModel
        if (configured.isNotBlank() && configured != "big-pickle") {
            configured
        } else when {
            providerState.providerName.contains("Gemini", ignoreCase = true) -> "gemini-flash-latest"
            providerState.providerName.contains("OpenCode", ignoreCase = true) -> "glm-4.7"
            else -> "gemini-flash-latest"
        }
    }

    // Context inclusion toggles
    var includeFileContext by remember { mutableStateOf(true) }
    var includeProjectContext by remember { mutableStateOf(true) }

    // Persistent Chat State per Project
    val initialChatHistory = remember(project.appName) {
        ProjectChatHistoryManager.getMessages(context, project.appName)
    }

    var messages by remember(project.appName) {
        mutableStateOf(
            if (initialChatHistory.isNotEmpty()) {
                initialChatHistory
            } else {
                listOf(
                    BuildAiMessage(
                        sender = "assistant",
                        content = "Hello! I am SAIF AI, your coding specialist. Tell me what to build, modify, or fix, and I will write and auto-apply the changes directly to your files.",
                        timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    )
                )
            }
        )
    }

    // Capture system Back gesture to ensure conversation is saved to storage
    BackHandler {
        ProjectChatHistoryManager.saveMessages(context, project.appName, messages)
        onBack()
    }

    // Ensure state refreshes if persistent history has been updated
    LaunchedEffect(project.appName) {
        val loaded = ProjectChatHistoryManager.getMessages(context, project.appName)
        if (loaded.isNotEmpty() && loaded.size >= messages.size) {
            messages = loaded
        }
    }

    // Auto-save messages to persistent storage
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            ProjectChatHistoryManager.saveMessages(context, project.appName, messages)
        }
    }

    var userInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var showBuildProgress by remember { mutableStateOf(false) }
    var showAppRunnerDialog by remember { mutableStateOf(false) }

    // Attachment state for SaifInputBar
    val attachedUris = remember { mutableStateListOf<Uri>() }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var stopVoiceListeningTrigger by remember { mutableIntStateOf(0) }

    // Media Launchers for SaifInputBar
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachedUris.add(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) tempCameraUri?.let { attachedUris.add(it) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                val photoFile = File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", cameraDir)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Camera open karne me samasya: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission zaruri hai", Toast.LENGTH_SHORT).show()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) attachedUris.add(uri)
    }

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachedUris.add(uri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone enabled. Press mic to talk.", Toast.LENGTH_SHORT).show()
        }
    }

    // Colors matching SAIF AI Signature Theme
    val bgDark = Color(0xFF070B14)
    val cardBg = Color(0xFF0F172A)
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val purpleAccent = Color(0xFFA855F7)
    val borderStroke = Color(0x3338BDF8)
    val glassBorder = Color(0x33A855F7)

    // Helper to safely convert Uri to Base64 for multi-modal API calls
    fun safeUriToBase64(ctx: Context, uri: Uri, maxDim: Int = 1024): String? {
        return try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var sampleSize = 1
            var w = origWidth
            var h = origHeight
            while (w > maxDim * 2 || h > maxDim * 2) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val scale = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
            val finalBmp = if (scale < 1f) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                if (scaled != bmp) bmp.recycle()
                scaled
            } else {
                bmp
            }

            val out = java.io.ByteArrayOutputStream()
            finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            finalBmp.recycle()
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
    }

    fun isPromptHindiOrHinglish(prompt: String): Boolean {
        val hindiKeywords = setOf(
            "karo", "karna", "kare", "kariye", "banao", "bana", "chahiye", "kaise", "hai", "hain", "par",
            "aur", "me", "mai", "mera", "meri", "mere", "thik", "nahi", "dabane", "wala", "wali", "wale",
            "dikhe", "dikhta", "kya", "hoga", "hona", "dijiye", "batao", "bataiye", "ye", "yeh", "wo", "woh",
            "kaat", "aata", "aaye", "aane", "saare", "sab", "ham", "hum", "ke", "ki", "ko", "se", "pe",
            "badal", "badlo", "hatao", "jodo", "likho", "dalo", "daal", "sakte", "lagata", "lagta", "kisine"
        )
        val words = prompt.lowercase().split(Regex("""[\s\p{Punct}]+"""))
        val matchCount = words.count { hindiKeywords.contains(it) }
        val hasDevanagari = prompt.any { it in '\u0900'..'\u097F' }
        return hasDevanagari || matchCount >= 2 || (words.size <= 5 && matchCount >= 1)
    }

    // Execute Autonomous Android Coder Pipeline with Real-Time Multi-Stage Progress
    fun processPrompt(promptText: String, isGlitchFix: Boolean = false, errorInfo: CompilerErrorInfo? = null) {
        if (promptText.isBlank() && errorInfo == null) return

        val userDisplay = if (isGlitchFix && errorInfo != null) {
            "Fix compilation error in ${errorInfo.fileName}:${errorInfo.lineNumber} - ${errorInfo.errorMessage}"
        } else {
            promptText
        }

        val userMsg = BuildAiMessage(
            sender = "user",
            content = userDisplay,
            timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        )

        val lowerP = promptText.lowercase().trim()

        val isGlitchPrompt = isGlitchFix || errorInfo != null ||
            lowerP.contains("glitch") || lowerP.contains("error") || lowerP.contains("bug") ||
            lowerP.contains("crash") || lowerP.contains("fix karo") || lowerP.contains("theek karo") ||
            lowerP.contains("thik karo") || lowerP.contains("sahi karo") || lowerP.contains("sudharo") ||
            lowerP.contains("fix this") || lowerP.contains("fix it") || lowerP.contains("resolve") ||
            lowerP.contains("repair") || lowerP.contains("compilation error")

        val assistantMsgId = UUID.randomUUID().toString()
        val initialContent = "🧠 Analyzing prompt & designing UI layout..."

        val initialSteps = listOf(
            AiStudioProgressStep("🧠 Analyzing prompt & designing UI layout...", isRunning = true),
            AiStudioProgressStep("⚡ Synthesizing application code & interactions..."),
            AiStudioProgressStep("📁 Saving application & syncing assets..."),
            AiStudioProgressStep("✅ Project Ready!")
        )

        val assistantMsg = BuildAiMessage(
            id = assistantMsgId,
            sender = "assistant",
            content = initialContent,
            timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
            steps = initialSteps,
            isOptimizing = true,
            isGlitchFix = isGlitchPrompt,
            activePhase = "✦ Autonomous Coder Pipeline..."
        )

        val currentAttachments = attachedUris.toList()
        messages = messages + userMsg + assistantMsg
        ProjectChatHistoryManager.saveMessages(context, project.appName, messages)
        userInput = ""
        attachedUris.clear()
        isGenerating = true

        coroutineScope.launch {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)

            val startTime = System.currentTimeMillis()
            val timerJob = launch {
                while (true) {
                    val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    val durationStr = if (elapsed < 60) "${elapsed}s" else "${elapsed / 60}m ${elapsed % 60}s"
                    messages = messages.map { msg ->
                        if (msg.id == assistantMsgId && msg.isOptimizing) {
                            msg.copy(
                                elapsedSeconds = elapsed,
                                durationText = durationStr
                            )
                        } else msg
                    }
                    delay(1000)
                }
            }

            val base64Images = mutableListOf<String>()
            if (currentAttachments.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    for (uri in currentAttachments) {
                        val b64 = safeUriToBase64(context, uri)
                        if (!b64.isNullOrBlank()) {
                            base64Images.add(b64)
                        }
                    }
                }
            }

            val result = AutonomousAppBuilder.buildOrModifyApp(
                context = context,
                project = project,
                prompt = promptText,
                editorViewModel = editorViewModel,
                isGlitchFix = isGlitchPrompt,
                errorInfo = errorInfo,
                base64Images = base64Images,
                onProgressUpdate = { statusText, stage ->
                    withContext(Dispatchers.Main) {
                        messages = messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                val updatedSteps = msg.steps.mapIndexed { idx, s ->
                                    val stepIndex = idx + 1
                                    when {
                                        stepIndex < stage -> s.copy(isCompleted = true, isRunning = false)
                                        stepIndex == stage -> s.copy(isRunning = true, isCompleted = false)
                                        else -> s.copy(isRunning = false, isCompleted = false)
                                    }
                                }
                                msg.copy(
                                    content = statusText,
                                    activePhase = statusText,
                                    steps = updatedSteps
                                )
                            } else msg
                        }
                    }
                }
            )

            timerJob.cancel()
            val totalElapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)
            val finalDurationString = if (totalElapsedSeconds < 60) "${totalElapsedSeconds}s" else "${totalElapsedSeconds / 60}m ${totalElapsedSeconds % 60}s"

            withContext(Dispatchers.Main) {
                val hasError = !result.isSuccess
                val errorMessage = result.error ?: result.summary

                val finalSteps = if (hasError) {
                    listOf(
                        AiStudioProgressStep("🧠 Analyzing prompt & designing UI layout...", isCompleted = true),
                        AiStudioProgressStep("❌ $errorMessage", isCompleted = false, isRunning = false)
                    )
                } else {
                    listOf(
                        AiStudioProgressStep("🧠 Analyzing prompt & designing UI layout...", isCompleted = true),
                        AiStudioProgressStep("⚡ Synthesizing application code & interactions...", isCompleted = true),
                        AiStudioProgressStep("📁 Saving application & syncing assets...", isCompleted = true),
                        AiStudioProgressStep("✅ Project Ready! Updated files: [${result.updatedFiles.joinToString(", ")}]", isCompleted = true)
                    )
                }

                val parsedSummaryList = if (hasError) {
                    listOf("Code generation aborted: $errorMessage")
                } else {
                    val extracted = result.summary.lines()
                        .map { it.trim() }
                        .filter { it.startsWith("•") || it.startsWith("*") || it.startsWith("-") }
                        .map { it.removePrefix("•").removePrefix("*").removePrefix("-").trim().replace("**", "") }
                        .filter { it.isNotBlank() }
                    if (extracted.isNotEmpty()) extracted else listOf(
                        "Native UI components & responsive layout built",
                        "Functional button click listeners and reactive states bound",
                        "Files updated on disk: [${result.updatedFiles.joinToString(", ")}]"
                    )
                }

                messages = messages.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            content = if (hasError) "❌ $errorMessage" else result.summary,
                            autoAppliedFiles = if (hasError) emptyList() else result.updatedFiles,
                            steps = finalSteps,
                            changeSummary = parsedSummaryList,
                            isOptimizing = false,
                            isGlitchFix = isGlitchPrompt,
                            elapsedSeconds = totalElapsedSeconds,
                            durationText = finalDurationString,
                            activePhase = if (hasError) "Failed: $errorMessage" else "Completed in $finalDurationString"
                        )
                    } else msg
                }

                ProjectChatHistoryManager.saveMessages(context, project.appName, messages)
                isGenerating = false
                delay(100)
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // If navigated from compiler error "Ask SAIF AI to Optimize", auto-run repair immediately!
    LaunchedEffect(initialError) {
        if (initialError != null) {
            processPrompt(
                promptText = "Fix ${initialError.errorMessage} in ${initialError.fileName}:${initialError.lineNumber}",
                isGlitchFix = true,
                errorInfo = initialError
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = bgDark,
        topBar = {
            // Liquid Glass Top Bar matching SAIF AI
            Surface(
                color = cardBg.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                ProjectChatHistoryManager.saveMessages(context, project.appName, messages)
                                onBack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Purple Sparkle Icon
                        Surface(
                            shape = CircleShape,
                            color = purpleAccent.copy(alpha = 0.18f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = purpleAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Title & Coding Model Badge
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SAIF AI",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textLight
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Active Model Badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = purpleAccent.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, purpleAccent.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = activeCodingModel,
                                        color = Color(0xFFC084FC),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Coding Specialist • ${activeFile?.name ?: "Current Workspace"}",
                                fontSize = 11.5.sp,
                                color = textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Run Live App Preview Button
                        IconButton(
                            onClick = { showAppRunnerDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = "Run Live App Preview",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Build & Install APK Button
                        IconButton(
                            onClick = { showBuildProgress = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Build & Install APK",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Clear Chat Button
                        IconButton(
                            onClick = {
                                ProjectChatHistoryManager.clearMessages(context, project.appName)
                                messages = listOf(
                                    BuildAiMessage(
                                        sender = "assistant",
                                        content = "Chat cleared. Tell me what to build or fix next.",
                                        timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                                    )
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Clear Chat",
                                tint = textMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Active File Context Strip
                    Surface(
                        color = Color(0x1F38BDF8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Code,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Live Sync: ${activeFile?.name ?: "index.html"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showAppRunnerDialog = true }
                                    .padding(end = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Preview",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showBuildProgress = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Build APK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Embed the EXACT SaifInputBar as requested!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Previews of attached media
                if (attachedUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachedUris.forEachIndexed { index, uri ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderStroke)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        tint = purpleAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Attachment ${index + 1}",
                                        fontSize = 11.sp,
                                        color = textLight
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = textMuted,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { attachedUris.removeAt(index) }
                                    )
                                }
                            }
                        }
                    }
                }

                // SaifInputBar component with all features (speech, gallery, camera, file, live voice)
                SaifInputBar(
                    input = userInput,
                    onInputChange = { userInput = it },
                    onSend = {
                        if (userInput.isNotBlank() || attachedUris.isNotEmpty()) {
                            processPrompt(userInput)
                        }
                    },
                    isGenerating = isGenerating,
                    onStop = { isGenerating = false },
                    onVoiceRecorded = { voiceFile ->
                        coroutineScope.launch {
                            Toast.makeText(context, "Transcribing voice...", Toast.LENGTH_SHORT).show()
                            try {
                                val apiKey = com.example.util.AudioTranscriptionEngine.resolveActiveApiKey()
                                val bytes = withContext(Dispatchers.IO) { voiceFile.readBytes() }
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                val res = com.example.util.AudioTranscriptionEngine.transcribeWav(apiKey, base64, bytes)
                                if (res.isSuccess) {
                                    val spoken = res.getOrNull()?.trim() ?: ""
                                    if (spoken.isNotBlank()) {
                                        userInput = if (userInput.isBlank()) spoken else "$userInput $spoken"
                                    }
                                } else {
                                    Toast.makeText(context, "Voice note recorded", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Voice note recorded", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onStartLiveVoice = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (!Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                            com.example.LiveVoiceManager.toggleLiveVoice(context)
                        }
                    },
                    onGalleryClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onFileClick = {
                        fileLauncher.launch(arrayOf("*/*"))
                    },
                    onVideoClick = {
                        videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    onCameraClick = {
                        val hasCamPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasCamPerm) {
                            try {
                                val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val photoFile = File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", cameraDir)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Camera open karne me samasya: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    hasAttachments = attachedUris.isNotEmpty(),
                    stopVoiceListeningTrigger = stopVoiceListeningTrigger,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                if (message.sender == "assistant") {
                    SaifAiAssistantBubble(
                        message = message,
                        onRunApp = { showBuildProgress = true },
                        onLivePreview = { showAppRunnerDialog = true }
                    )
                } else {
                    SaifAiUserBubble(message = message)
                }
            }

            // Quick suggestion chips when only the initial greeting is present
            if (messages.size <= 1) {
                item {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = "Suggested actions:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textMuted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        val suggestions = listOf(
                            "Add Floating Action Button to activity_main.xml",
                            "Create SQLite Database Helper class",
                            "Add RecyclerView Adapter with click listener",
                            "Explain MainActivity code and optimize it",
                            "Fix all missing semicolons and imports"
                        )

                        suggestions.forEach { prompt ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderStroke),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { processPrompt(prompt) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = purpleAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = prompt,
                                        fontSize = 13.sp,
                                        color = textLight
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBuildProgress) {
        BuildProgressDialog(
            project = project,
            isDarkTheme = true,
            onDismiss = { showBuildProgress = false },
            onAskSaifAi = { errorInfo ->
                showBuildProgress = false
                processPrompt(
                    promptText = "Fix compilation error in ${errorInfo.fileName}:${errorInfo.lineNumber} - ${errorInfo.errorMessage}",
                    isGlitchFix = true,
                    errorInfo = errorInfo
                )
            }
        )
    }

    if (showAppRunnerDialog) {
        AppRunnerDialog(
            project = project,
            isDarkTheme = true,
            onBuildApk = {
                showAppRunnerDialog = false
                showBuildProgress = true
            },
            onDismiss = { showAppRunnerDialog = false }
        )
    }
}

/**
 * Assistant Message Bubble: Modern Google AI Studio / Gemini layout featuring
 * an illuminating live working task bar, expandable reasoning thoughts,
 * step checklist with animated green ticks, auto-applied badges, and clean conversational explanation.
 */
@Composable
private fun SaifAiAssistantBubble(
    message: BuildAiMessage,
    onRunApp: () -> Unit = {},
    onLivePreview: () -> Unit = {}
) {
    val runGreen = Color(0xFF10B981)
    val purpleAi = Color(0xFFA855F7)
    val skyCyan = Color(0xFF38BDF8)
    val amberAlert = Color(0xFFF59E0B)

    var isTaskBarExpanded by remember(message.id) { mutableStateOf(message.isOptimizing) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        // Header with Sparkle, Title & Live Elapsed Duration
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = purpleAi,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SAIF AI STUDIO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = purpleAi
            )
            if (message.durationText.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "⏱ ${message.durationText}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = message.timestamp,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================================
        // GEMINI-STYLE LIVE ILLUMINATING / WORKING TASK BAR (Clickable & Expandable)
        // =========================================================================
        val taskBorder = if (message.isOptimizing) {
            BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        purpleAi.copy(alpha = glowAlpha),
                        skyCyan.copy(alpha = glowAlpha),
                        amberAlert.copy(alpha = glowAlpha * 0.7f)
                    )
                )
            )
        } else {
            BorderStroke(1.dp, runGreen.copy(alpha = 0.45f))
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (message.isOptimizing) Color(0xFF0F172A).copy(alpha = 0.88f) else Color(0xFF0F172A).copy(alpha = 0.75f),
            border = taskBorder,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (message.isOptimizing) 6.dp else 1.dp, shape = RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.animateContentSize()) {
                // Clickable Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTaskBarExpanded = !isTaskBarExpanded }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isOptimizing) {
                        CircularProgressIndicator(
                            color = purpleAi,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.activePhase.ifBlank { "✦ Illuminating UI & Architecture..." },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (message.durationText.isNotBlank()) "Working (${message.durationText})" else "Working...",
                                    fontSize = 11.sp,
                                    color = purpleAi,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTaskBarExpanded) "• Tap to collapse" else "• Tap to inspect details",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = runGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✦ Architectural Pipeline Completed • ${message.steps.size} Tasks Verified",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                            val metaSummary = buildString {
                                if (message.durationText.isNotBlank()) append("Took ${message.durationText}")
                                if (message.autoAppliedFiles.isNotEmpty()) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${message.autoAppliedFiles.size} files auto-applied")
                                }
                                append(if (isTaskBarExpanded) " • Tap to collapse" else " • Tap to expand task inspector")
                            }
                            Text(
                                text = metaSummary,
                                fontSize = 10.5.sp,
                                color = if (message.autoAppliedFiles.isNotEmpty()) runGreen else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = if (isTaskBarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Task Bar",
                        tint = if (message.isOptimizing) purpleAi else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // =====================================================================
                // EXPANDABLE CONTENT: Real Thoughts, Checklist, Action Buttons, Summary
                // =====================================================================
                if (isTaskBarExpanded) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    Column(modifier = Modifier.padding(12.dp)) {
                        // 1. AI Real Chain of Thought
                        if (message.thoughts.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF070B14),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Psychology,
                                            contentDescription = null,
                                            tint = skyCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "AUTONOMOUS REASONING & ALGORITHM BLUEPRINT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.6.sp,
                                            color = skyCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    message.thoughts.forEach { thought ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "•",
                                                color = skyCyan,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Text(
                                                text = thought,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFCBD5E1),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // 2. Step-by-Step Task Checklist with Green Ticks
                        if (message.steps.isNotEmpty()) {
                            Text(
                                text = "TASK PROGRESS & VERIFICATION CHECKLIST",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            message.steps.forEach { step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (step.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Completed",
                                            tint = runGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    } else if (step.isRunning) {
                                        CircularProgressIndicator(
                                            color = purpleAi,
                                            strokeWidth = 1.5.dp,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF334155))
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = step.title,
                                        fontSize = 12.sp,
                                        color = if (step.isCompleted) Color(0xFFF1F5F9) else if (step.isRunning) Color(0xFFC084FC) else Color(0xFF64748B),
                                        fontWeight = if (step.isRunning || step.isCompleted) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // 3. Quick Action Buttons & Auto-applied file badges
                        if (message.autoAppliedFiles.isNotEmpty() || message.isGlitchFix) {
                            Text(
                                text = "UPDATED FILES & INSTANT TEST RUNNERS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Live Preview
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = skyCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, skyCyan.copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable { onLivePreview() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Live Preview",
                                            tint = skyCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Live Preview",
                                            color = skyCyan,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Build & Install APK
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = runGreen.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, runGreen),
                                    modifier = Modifier.clickable { onRunApp() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InstallMobile,
                                            contentDescription = "Install APK",
                                            tint = runGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Build & Install APK",
                                            color = runGreen,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                message.autoAppliedFiles.forEach { file ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = runGreen.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, runGreen.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Auto Applied",
                                                tint = runGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Updated: $file",
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // 4. Change Summary Checklist
                        if (message.changeSummary.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = runGreen.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, runGreen.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.TaskAlt,
                                            contentDescription = null,
                                            tint = runGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val isHindiSummary = message.changeSummary.any {
                                            it.contains(" me ") || it.contains(" gaye") || it.contains(" gayi") || it.contains(" ho gaya") || it.contains(" kiya") || it.contains("kiye")
                                        }
                                        Text(
                                            text = if (isHindiSummary) "AAPKE APP ME IMPLEMENT HUE CHANGES" else "SUMMARY OF IMPLEMENTED CHANGES",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = runGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    message.changeSummary.forEach { summary ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 1.5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = runGreen,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = summary,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFE2E8F0)
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

        // Clean conversational content without raw code cards
        if (message.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            val isError = message.content.startsWith("❌") || message.content.contains("API Error", ignoreCase = true) || message.content.contains("Failed", ignoreCase = true)
            SelectionContainer {
                Text(
                    text = message.content,
                    color = if (isError) Color(0xFFEF4444) else Color(0xFFF1F5F9),
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/**
 * User Message Bubble (Right-aligned dark purple card).
 */
@Composable
private fun SaifAiUserBubble(message: BuildAiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            color = Color(0xFF6D28D9),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.timestamp,
                    color = Color(0xCCFFFFFF),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/**
 * Terminal-styled Code Block Card with "Copy" and "Apply & Save to File" action buttons.
 */
@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    activeFileName: String,
    onApply: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF070B14),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header Bar with Language badge and Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Copy Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCopy() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Apply to File Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF7C3AED))
                        .clickable { onApply() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Apply",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Apply to $activeFileName",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Code Content
            SelectionContainer {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

/**
 * Extracts pure code from markdown backticks.
 */
private fun extractCodeFromMarkdown(raw: String): String {
    val codeBlockRegex = Regex("""```(?:[a-zA-Z0-9_-]+)?\s*([\s\S]*?)```""")
    val match = codeBlockRegex.find(raw)
    return if (match != null) {
        match.groups[1]?.value?.trim() ?: raw.trim()
    } else {
        raw.trim()
    }
}

internal data class ContentSection(
    val text: String,
    val isCode: Boolean,
    val language: String = ""
)

internal fun splitContentIntoSections(content: String): List<ContentSection> {
    val sections = mutableListOf<ContentSection>()
    val regex = Regex("""```([a-zA-Z0-9_-]*)\n?([\s\S]*?)```""")
    var lastIndex = 0

    regex.findAll(content).forEach { matchResult ->
        val startIndex = matchResult.range.first
        val endIndex = matchResult.range.last + 1

        if (startIndex > lastIndex) {
            val normalText = content.substring(lastIndex, startIndex).trim()
            if (normalText.isNotBlank()) {
                sections.add(ContentSection(normalText, isCode = false))
            }
        }

        val lang = matchResult.groupValues[1].trim()
        val code = matchResult.groupValues[2].trim()
        sections.add(ContentSection(code, isCode = true, language = lang))

        lastIndex = endIndex
    }

    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex).trim()
        if (remaining.isNotBlank()) {
            sections.add(ContentSection(remaining, isCode = false))
        }
    }

    return if (sections.isEmpty()) listOf(ContentSection(content, isCode = false)) else sections
}
