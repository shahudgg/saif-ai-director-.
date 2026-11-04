package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ProjectDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class NewProjectChoice { ADVANCE_EDITING, AI_VIDEO, EDIT_WITH_AI, AI_VOICE_ON_VIDEO, VOICE_OVER_TO_VIDEO, BLANK, MORE }

/** When each video project was last opened or created in this app (used for "last edited" in the recent list). */
object RecentProjects {
    private const val PREFS = "saif_recent_projects"
    fun touch(context: Context, name: String) {
        if (name.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(name.trim(), System.currentTimeMillis()).apply()
    }
    fun lastEdited(context: Context, name: String): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(name.trim(), 0L)

    /** "just now", "5 min ago", "3 h ago", "2 d ago"; empty when unknown. */
    fun relative(nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0L) return ""
        val s = ((nowMs - thenMs) / 1000L).coerceAtLeast(0L)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }

    fun duration(seconds: Float): String { val t = seconds.toInt().coerceAtLeast(0); return "${t / 60}:${(t % 60).toString().padStart(2, '0')}" }
}

private val Panel = Color(0xFF14101D)
private val Row1 = Color(0xFF1E1929)
private val Violet = Color(0xFF7B2BFF)
private val Aqua = Color(0xFF00E5FF)

/**
 * The sheet behind the purple "+" button: every way to start a project, plus recent projects with a thumbnail, duration
 * and last-edited time. Every row is a real action handled by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectSheet(onDismiss: () -> Unit, onChoice: (NewProjectChoice) -> Unit, onOpenRecent: (String) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recents by produceState(initialValue = emptyList<VideoProjectData>(), ctx) {
        value = withContext(Dispatchers.IO) {
            runCatching { ProjectDataManager.getAllVideoProjects(ctx).sortedByDescending { RecentProjects.lastEdited(ctx, it.projectName) }.take(8) }.getOrDefault(emptyList())
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New project", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Choose how you want to start", color = Color(0xFF9E95AC), fontSize = 12.sp)
            AdvanceCard { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChoice(NewProjectChoice.ADVANCE_EDITING) }
            ChoiceRow(Icons.Filled.AutoAwesome, "AI video (Zero to Video)", "Topic or script → researched, animated, narrated video") { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChoice(NewProjectChoice.AI_VIDEO) }
            ChoiceRow(Icons.Filled.ContentCut, "Edit my video with AI", "Analyze your footage and build a reversible edit plan") { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChoice(NewProjectChoice.EDIT_WITH_AI) }
            ChoiceRow(Icons.Filled.RecordVoiceOver, "AI voice on my video", "Edit your footage and add AI narration that matches it") { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChoice(NewProjectChoice.AI_VOICE_ON_VIDEO) }
            ChoiceRow(Icons.Filled.Mic, "Voice-over to video", "Your voice-over stays untouched; visuals are built around it") { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChoice(NewProjectChoice.VOICE_OVER_TO_VIDEO) }
            ChoiceRow(Icons.Filled.Add, "Blank project", "Start with an empty timeline") { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChoice(NewProjectChoice.BLANK) }
            TextButton(onClick = { onChoice(NewProjectChoice.MORE) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("More options: import media, templates, prompt", color = Aqua, fontSize = 12.sp) }

            Text("Recent projects", color = Aqua, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (recents.isEmpty()) Text("No projects yet. Your next one will appear here.", color = Color(0xFF9E95AC), fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
            else LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recents, key = { it.projectId }) { p ->
                    val edited = RecentProjects.relative(System.currentTimeMillis(), RecentProjects.lastEdited(ctx, p.projectName))
                    Surface(shape = RoundedCornerShape(12.dp), color = Row1, modifier = Modifier.fillMaxWidth().clickable { onOpenRecent(p.projectName) }) {
                        Row(Modifier.padding(8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProjectThumb(ctx, p)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.projectName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(RecentProjects.duration(p.totalDurationSeconds) + " • " + p.aspectRatio + if (edited.isNotEmpty()) " • edited $edited" else "", color = Color(0xFF9E95AC), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Row1, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Violet.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Aqua) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF9E95AC), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProjectThumb(ctx: Context, p: VideoProjectData) {
    val bmp by produceState<Bitmap?>(initialValue = null, p.projectId) { value = withContext(Dispatchers.IO) { loadThumb(ctx, p) } }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Violet.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), "Project thumbnail", Modifier.size(56.dp), contentScale = ContentScale.Crop)
        else Text(p.projectName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun loadThumb(ctx: Context, p: VideoProjectData): Bitmap? {
    val clip = p.clips.firstOrNull { it.uri != null } ?: return null
    val uri = clip.uri ?: return null
    return try {
        if (clip.isPhoto) {
            val o = BitmapFactory.Options().apply { inSampleSize = 8 }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        } else {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(ctx, uri); r.getFrameAtTime(0L) } finally { r.release() }
        }
    } catch (_: Exception) { null }
}

/**
 * SAIF AI ADVANCE EDITING — the highlighted entry at the top of the purple "+" sheet. It opens the full
 * production engine that ships inside the app (finance? no — film): trend scan, voiceover alignment,
 * element cut-outs, shot planning, broadcast mix and master render, plus the whole skills/fonts library.
 */
@Composable
private fun AdvanceCard(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1B1130),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B5CF6)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF06B6D4)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bolt, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SAIF AI ADVANCE EDITING", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Aqua.copy(alpha = 0.22f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("NEW", color = Aqua, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Advance Studio engine — trends, cut-outs, shots, mix, master", color = Color(0xFFB9AECB), fontSize = 11.sp)
            }
        }
    }
}
