package com.example.studio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.director.DirectorWorkflow

private val Purple = Color(0xFF7B2BFF)

/** Classic ⇄ AI Studio switch shown at the top of the SAIF AI sheet. */
@Composable
fun SaifModeSwitch(aiMode: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1A1626), border = BorderStroke(1.dp, Color(0x337B2BFF)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp)) {
            listOf("Classic tools" to false, "AI Studio" to true).forEach { (label, mode) ->
                val sel = aiMode == mode
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) Purple else Color.Transparent)
                        .clickable { onChange(mode) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (sel) Color.White else Color(0xFF9E95AC), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SaifAiWorkflowList(onOpenWorkflow: (DirectorWorkflow) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(listOf("__header__")) {
            Text("SAIF AI DIRECTOR", color = Color(0xFFD9C6FF), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Text("Script se lekar AI images, animation, narration aur timeline tak — sab yahin hota hai, live progress ke saath.",
                color = Color(0xFF9E95AC), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 4.dp))
        }
        items(DirectorWorkflow.values().toList()) { wf ->
            val (icon, accent, accent2) = when (wf) {
                DirectorWorkflow.ZERO_TO_VIDEO -> Triple(Icons.Default.AutoAwesome, Color(0xFFA78BFA), Color(0xFF7B2BFF))
                DirectorWorkflow.VIDEO_TO_EDITING -> Triple(Icons.Default.ContentCut, Color(0xFF00E5FF), Color(0xFF2563EB))
                DirectorWorkflow.VIDEO_EDIT_WITH_VOICE -> Triple(Icons.Default.RecordVoiceOver, Color(0xFFF59E0B), Color(0xFFEF4444))
                DirectorWorkflow.VOICE_OVER_TO_VIDEO -> Triple(Icons.Default.GraphicEq, Color(0xFF10B981), Color(0xFF0D9488))
            }
            val tag = when (wf) {
                DirectorWorkflow.ZERO_TO_VIDEO -> "✨ har scene ka AI image + unique animation"
                else -> null
            }
            WorkflowCard(wf.title, wf.subtitle, tag, icon, accent, accent2) { onOpenWorkflow(wf) }
        }
    }
}

@Composable
private fun WorkflowCard(title: String, desc: String, tag: String?, icon: ImageVector, accent: Color, accent2: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF15111F), border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent2, accent))), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp)
                Spacer(Modifier.size(3.dp))
                Text(desc, color = Color(0xFF9E95AC), fontSize = 11.sp, lineHeight = 15.sp)
                if (tag != null) {
                    Spacer(Modifier.size(5.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.18f)) {
                        Text(tag, color = accent, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(20.dp))
        }
    }
}


/** One-shot handoff from the "+" sheet to the studio: which AI workflow to open as soon as the editor appears. */
object StudioLaunch {
    @Volatile var pending: com.example.studio.director.DirectorWorkflow? = null
    fun take(): com.example.studio.director.DirectorWorkflow? { val p = pending; pending = null; return p }
}
