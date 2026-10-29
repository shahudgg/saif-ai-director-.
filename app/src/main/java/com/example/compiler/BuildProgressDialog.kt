package com.example.compiler

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.ui.components.ProjectData
import kotlinx.coroutines.launch
import java.io.File

/**
 * Data passed to SAIF AI when the user taps "Ask SAIF AI to Optimize" or "Ask AI to Fix".
 */
data class CompilerErrorInfo(
    val fileName: String,
    val lineNumber: Int,
    val errorMessage: String,
    val rawLog: String,
    val codeSnippet: String = "",
    val fullCode: String = ""
)

/**
 * Modern In-Device Build Progress Dialog with Live Terminal & APK Installer.
 */
@Composable
fun BuildProgressDialog(
    project: ProjectData,
    isDarkTheme: Boolean = true,
    onDismiss: () -> Unit,
    onAskSaifAi: (CompilerErrorInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val logListState = rememberLazyListState()

    var currentStep by remember { mutableStateOf(BuildStep.RESOURCE_COMPILATION) }
    var progressFraction by remember { mutableStateOf(0.1f) }
    val logs = remember { mutableStateListOf<CompileLog>() }

    var isBuilding by remember { mutableStateOf(true) }
    var buildResult by remember { mutableStateOf<BuildResult?>(null) }
    var generatedApk by remember { mutableStateOf<File?>(null) }

    // Colors matching SAIF AI Dark Glassmorphism
    val dialogBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val terminalBg = if (isDarkTheme) Color(0xFF070B14) else Color(0xFFF1F5F9)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val borderStroke = if (isDarkTheme) Color(0x3338BDF8) else Color(0xFFE2E8F0)
    val runGreen = Color(0xFF00A86B)
    val errorRed = Color(0xFFEF4444)
    val purpleAi = Color(0xFFA855F7)

    // Run the compiler on launch via LocalBuildEngine
    LaunchedEffect(Unit) {
        isBuilding = true
        val result = LocalBuildEngine.executeBuildPipeline(
            context = context,
            project = project,
            onStepChanged = { step, frac ->
                currentStep = step
                progressFraction = frac
            },
            onLogAdded = { log ->
                logs.add(log)
            }
        )
        buildResult = result
        isBuilding = false
        if (result is BuildResult.Success) {
            generatedApk = result.apkFile
            progressFraction = 1f
        }
    }

    // Auto-scroll logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isBuilding) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .shadow(24.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = dialogBg,
            border = BorderStroke(1.dp, borderStroke)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // ==========================================
                // 1. DIALOG HEADER
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = runGreen.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isBuilding) {
                                    CircularProgressIndicator(
                                        color = runGreen,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else if (buildResult is BuildResult.Success) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = runGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = errorRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isBuilding) "Compiling APK..." else if (buildResult is BuildResult.Success) "Build Succeeded" else "Build Failed with Errors",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = textPrimary
                            )
                            Text(
                                text = "${project.appName} • ${project.packageName}",
                                fontSize = 11.5.sp,
                                color = textMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBuilding,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 2. PROGRESS BAR & 5 STEPS OVERVIEW
                // ==========================================
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction,
                    animationSpec = tween(300),
                    label = "BuildProgress"
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (buildResult is BuildResult.Error) errorRed else runGreen,
                    trackColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Steps Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BuildStep.values().forEach { step ->
                        val isDone = step.stepNumber < currentStep.stepNumber || (buildResult is BuildResult.Success)
                        val isCurrent = step == currentStep && isBuilding
                        val isFailed = step == (buildResult as? BuildResult.Error)?.errorStep

                        val pillBg = when {
                            isFailed -> errorRed.copy(alpha = 0.15f)
                            isDone -> runGreen.copy(alpha = 0.15f)
                            isCurrent -> Color(0x3338BDF8)
                            else -> if (isDarkTheme) Color(0x22334155) else Color(0x11000000)
                        }
                        val pillColor = when {
                            isFailed -> errorRed
                            isDone -> runGreen
                            isCurrent -> Color(0xFF38BDF8)
                            else -> textMuted
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pillBg,
                            border = BorderStroke(1.dp, pillColor.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isDone) {
                                    Icon(Icons.Default.Check, null, tint = runGreen, modifier = Modifier.size(12.dp))
                                } else if (isCurrent) {
                                    CircularProgressIndicator(color = pillColor, strokeWidth = 1.5.dp, modifier = Modifier.size(10.dp))
                                } else if (isFailed) {
                                    Icon(Icons.Default.Close, null, tint = errorRed, modifier = Modifier.size(12.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${step.stepNumber}. ${step.title.substringBefore(" (")}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Normal,
                                    color = pillColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ==========================================
                // 3. LIVE TERMINAL LOGS
                // ==========================================
                Text(
                    text = "CONSOLE OUTPUT",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = textMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = terminalBg,
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFCBD5E1))
                ) {
                    LazyColumn(
                        state = logListState,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs) { log ->
                            when {
                                log.isError -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(errorRed.copy(alpha = 0.12f))
                                            .padding(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = errorRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (log.fileName != null) "[ERROR in ${log.fileName}:${log.lineNumber ?: ""}]" else "[ERROR]",
                                                color = errorRed,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = log.message,
                                            color = if (isDarkTheme) Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                log.isWarning -> {
                                    Text(
                                        text = "[WARN] ${log.message}",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                                log.message.startsWith("===") || log.message.startsWith("[") -> {
                                    Text(
                                        text = log.message,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "  ${log.message}",
                                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF334155),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 4. ACTION FOOTER (Error Optimization vs Install Button)
                // ==========================================
                when (val result = buildResult) {
                    is BuildResult.Error -> {
                        val errorLog = logs.firstOrNull { it.isError }
                        val file = result.fileName ?: errorLog?.fileName ?: "MainActivity.java"
                        val line = result.lineNumber ?: errorLog?.lineNumber ?: 1
                        val msg = result.message

                        // Extract snippet and full code from the project file
                        val sanitizedName = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
                        val projectDir = File(context.filesDir, "projects/$sanitizedName")
                        val pkgPath = project.packageName.replace('.', '/')
                        val candidateFiles = listOf(
                            File(projectDir, "app/src/main/java/$pkgPath/$file"),
                            File(projectDir, "app/src/main/res/layout/$file"),
                            File(projectDir, "app/src/main/$file"),
                            File(projectDir, file)
                        )
                        val targetFile = candidateFiles.firstOrNull { it.exists() }
                            ?: projectDir.walkTopDown().firstOrNull { it.name.equals(file, ignoreCase = true) }

                        val fullCode = targetFile?.readText().orEmpty()
                        val allLines = fullCode.lines()
                        val snippet = if (allLines.isNotEmpty() && line > 0) {
                            val startIdx = maxOf(0, line - 4)
                            val endIdx = minOf(allLines.size, line + 4)
                            allLines.subList(startIdx, endIdx).mapIndexed { idx, codeText ->
                                val curLineNum = startIdx + idx + 1
                                val prefix = if (curLineNum == line) "▶ " else "  "
                                "$prefix${curLineNum.toString().padStart(3, ' ')} | $codeText"
                            }.joinToString("\n")
                        } else ""

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E1B4B))
                                .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF43F5E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Glitch at $file (Line $line)",
                                    color = Color(0xFFF43F5E),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            if (snippet.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = snippet,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Dismiss", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        onDismiss()
                                        onAskSaifAi(
                                            CompilerErrorInfo(
                                                fileName = file,
                                                lineNumber = line,
                                                errorMessage = msg,
                                                rawLog = logs.filter { it.isError }.joinToString("\n") { it.message },
                                                codeSnippet = snippet,
                                                fullCode = fullCode
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF7C3AED)
                                    ),
                                    modifier = Modifier.weight(1.8f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ask AI to Fix (Auto-Repair)",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    is BuildResult.Success -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Google Play Protect & Install Helper Banner
                            Surface(
                                color = Color(0x2638BDF8),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0x4D38BDF8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Play Protect Tip (Agar 'Blocked by Play Protect' dikhaye):",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Installation ke waqt 'More details' (अतिरिक्त विवरण) par tap karke 'Install anyway' (फिर भी इंस्टॉल करें) select karein. Ye aapka apna banaya hua standalone APK hai.",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    LocalBuildEngine.launchInstaller(context, result.apkFile)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = runGreen),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Install APK Now",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        AppCompilerEngine.saveApkToDownloads(context, result.apkFile, project.appName)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save to Downloads", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        AppCompilerEngine.shareApk(context, result.apkFile, project.appName)
                                    },
                                    modifier = Modifier.weight(0.9f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share APK", fontSize = 11.5.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val zipFile = AppCompilerEngine.exportProjectAsZip(context, project)
                                        if (zipFile != null && zipFile.exists()) {
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    putExtra(Intent.EXTRA_SUBJECT, "${project.appName} Android Project")
                                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Export Android Project (.zip)"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Export saved: ${zipFile.name}", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Export error", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export ZIP", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Done", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    else -> {
                        // While building
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel Build")
                            }
                        }
                    }
                }
            }
        }
    }
}
