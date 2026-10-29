package com.example.studio.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.studio.export.ExportConfig
import com.example.studio.export.StudioExportEngine
import com.example.studio.model.StudioProject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun StudioExportDialog(
    project: StudioProject,
    currentPlayheadUs: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedResolution by remember { mutableStateOf("1080p") }
    var selectedFps by remember { mutableIntStateOf(30) }
    var selectedBitrateTier by remember { mutableStateOf("Standard") }

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatusText by remember { mutableStateOf("Ready to export") }
    var exportEtaMs by remember { mutableLongStateOf(0L) }
    var exportResultUri by remember { mutableStateOf<Uri?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var exportWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    // Real pre-export checks on the actual timeline (missing files, static stretches, overlapping text, silence).
    val quality = remember(project) {
        com.example.studio.export.QualityChecker.check(project) { uri ->
            runCatching {
                val u = Uri.parse(uri)
                if (u.scheme == "file" || u.scheme == null) java.io.File(u.path ?: uri).exists()
                else context.contentResolver.openAssetFileDescriptor(u, "r")?.use { true } ?: false
            }.getOrDefault(false)
        }
    }
    val blockingIssues = quality.filter { it.severity == com.example.studio.export.Severity.ERROR }

    Dialog(onDismissRequest = {
        if (!isExporting) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF13101C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2A3E)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (exportResultUri != null) "Export Successful! 🎉" else "Export Video",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            exportJob?.cancel()
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCC3D9))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (exportResultUri != null) {
                    // Success View
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Saved to Gallery (Movies/SAIF AI)",
                        color = Color(0xFFE5E2E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Verified: readable MP4, length matches the timeline",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp
                    )
                    exportWarnings.forEach { Text("⚠ $it", color = Color(0xFFFFB86B), fontSize = 10.sp) }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(exportResultUri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open in Gallery"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open Video")
                        }

                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, exportResultUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }

                } else if (isExporting) {
                    // Active Export Progress
                    Text(exportStatusText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Color(0xFF8B5CF6),
                        trackColor = Color(0xFF262335)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${(exportProgress * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val etaFormatted = StudioExportEngine.formatEta((exportEtaMs / 1000L).toInt())
                        Text("ETA: $etaFormatted", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            exportJob?.cancel()
                            isExporting = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text("Cancel Export")
                    }

                } else {
                    // Settings View
                    if (exportError != null) {
                        Text(
                            text = "❌ $exportError",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // One-tap export presets (they only set the real options below)
                    Text("Presets", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Shorts / Reels", "1080p" to 30, "Standard"),
                            Triple("Draft (fast)", "720p" to 30, "Standard"),
                            Triple("Best quality", "1080p" to 30, "High")
                        ).forEach { (label, rf, tier) ->
                            val on = selectedResolution == rf.first && selectedFps == rf.second && selectedBitrateTier == tier
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (on) Color(0xFF00B8D4) else Color(0xFF231F33))
                                    .clickable { selectedResolution = rf.first; selectedFps = rf.second; selectedBitrateTier = tier }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (quality.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1C1829)).padding(8.dp)) {
                            Text("Quality check (${blockingIssues.size} blocking, ${quality.size - blockingIssues.size} to review)", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            quality.take(6).forEach {
                                Text((if (it.severity == com.example.studio.export.Severity.ERROR) "✖ " else "⚠ ") + it.message,
                                    color = if (it.severity == com.example.studio.export.Severity.ERROR) Color(0xFFFF6B6B) else Color(0xFFFFB86B), fontSize = 10.sp)
                            }
                            if (quality.size > 6) Text("…and ${quality.size - 6} more", color = Color(0xFF8E8A99), fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Resolution Switcher
                    Text("Resolution", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1080p", "720p").forEach { res ->
                            val isSelected = selectedResolution == res
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF231F33))
                                    .clickable { selectedResolution = res }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(res, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Frame Rate Switcher
                    Text("Frame Rate", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(24, 30, 60).forEach { fps ->
                            val isSelected = selectedFps == fps
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF231F33))
                                    .clickable { selectedFps = fps }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${fps} fps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bitrate Tier Switcher (Standard, High, Cinema Master)
                    Text("Bitrate Tier", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Standard", "High", "Cinema Master").forEach { tier ->
                            val isSelected = selectedBitrateTier == tier
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF231F33))
                                    .clickable { selectedBitrateTier = tier }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(tier, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Extras: Export Current Frame & Export SRT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = StudioExportEngine.exportCurrentFrame(context, project, currentPlayheadUs)
                                if (uri != null) {
                                    Toast.makeText(context, "Frame saved to Gallery (Pictures/SAIF AI)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save PNG", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val srtText = StudioExportEngine.exportSubtitlesSrt(project)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, srtText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Subtitles (.srt)"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export SRT", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Start Export Button
                    Button(
                        onClick = {
                            isExporting = true
                            exportError = null
                            val (w, h) = com.example.studio.export.ExportPresets.dimensions(project.aspect, selectedResolution)
                            val finalBitrate = StudioExportEngine.calculateBitrate(w, h, selectedBitrateTier)
                            val config = ExportConfig(
                                width = w,
                                height = h,
                                fps = selectedFps,
                                bitrate = finalBitrate,
                                bitrateTier = selectedBitrateTier
                            )

                            exportJob = scope.launch {
                                val result = StudioExportEngine.exportProject(
                                    context = context,
                                    project = project,
                                    config = config,
                                    onProgress = { p, msg, eta ->
                                        exportProgress = p
                                        exportStatusText = msg
                                        exportEtaMs = eta
                                    },
                                    isCancelled = { exportJob?.isCancelled == true }
                                )

                                isExporting = false
                                if (result.success) {
                                    exportWarnings = result.warnings
                                    exportResultUri = result.outputUri
                                } else {
                                    exportError = result.errorMessage ?: "Unknown error"
                                }
                            }
                        },
                        enabled = blockingIssues.isEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (blockingIssues.isEmpty()) "Export MP4 to Gallery" else "Fix ${blockingIssues.size} blocking issue(s) first", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
