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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(DirectorWorkflow.values().toList()) { wf ->
            val (icon, accent) = when (wf) {
                DirectorWorkflow.ZERO_TO_VIDEO -> Icons.Default.AutoAwesome to Color(0xFFA78BFA)
                DirectorWorkflow.VIDEO_TO_EDITING -> Icons.Default.ContentCut to Color(0xFF00E5FF)
                DirectorWorkflow.VIDEO_EDIT_WITH_VOICE -> Icons.Default.RecordVoiceOver to Color(0xFFF59E0B)
                DirectorWorkflow.VOICE_OVER_TO_VIDEO -> Icons.Default.GraphicEq to Color(0xFF10B981)
            }
            WorkflowCard(wf.title, wf.subtitle, icon, accent) { onOpenWorkflow(wf) }
        }
    }
}

@Composable
private fun WorkflowCard(title: String, desc: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF15111F), border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = Color(0xFF9E95AC), fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
        }
    }
}


/** One-shot handoff from the "+" sheet to the studio: which AI workflow to open as soon as the editor appears. */
object StudioLaunch {
    @Volatile var pending: com.example.studio.director.DirectorWorkflow? = null
    fun take(): com.example.studio.director.DirectorWorkflow? { val p = pending; pending = null; return p }
}
