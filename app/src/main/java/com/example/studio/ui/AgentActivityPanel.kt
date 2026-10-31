package com.example.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.director.ActivityEvent
import com.example.studio.director.ActivityKind
import com.example.studio.director.ActivityState
import kotlinx.coroutines.delay

private val TraceCard = Color(0xFF17131F)
private val TraceText = Color(0xFFCCC3D9)
private val TraceAccent = Color(0xFF00E5FF)

/** Live, bounded trace of actual operations. Closing this composable does not own/cancel the director job. */
@Composable
internal fun AgentActivityPanel(events: List<ActivityEvent>) {
    if (events.isEmpty()) return
    var open by remember { mutableStateOf(true) }
    val running = events.any { it.state == ActivityState.RUNNING }
    var now by remember { mutableLongStateOf(System.nanoTime() / 1_000_000L) }
    LaunchedEffect(running) {
        while (running) { now = System.nanoTime() / 1_000_000L; delay(1000L) }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = TraceCard, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) "▾  Production activity" else "▸  Production activity", color = TraceAccent, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${events.size} events", color = TraceText, fontSize = 10.sp)
            }
            if (open) {
                Text("Live trace · recent events kept in memory only", color = TraceText, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                events.forEach { event -> key(event.id) { ActivityRow(event, now) } }
            }
        }
    }
}

@Composable
private fun ActivityRow(event: ActivityEvent, now: Long) {
    val isModelResponse = event.title.contains(" · request ")
    var expanded by remember(event.id) { mutableStateOf(isModelResponse && event.state == ActivityState.RUNNING) }
    var userChanged by remember(event.id) { mutableStateOf(false) }
    LaunchedEffect(event.state) { if (event.state != ActivityState.RUNNING && !userChanged) expanded = false }
    val color = when (event.state) {
        ActivityState.RUNNING -> TraceAccent
        ActivityState.FAILED, ActivityState.CANCELLED -> Color(0xFFFF9B92)
        ActivityState.RETRIED -> Color(0xFFFFC27D)
        else -> TraceText
    }
    val state = when (event.state) {
        ActivityState.RUNNING -> "running"
        ActivityState.SUCCEEDED -> "done"
        ActivityState.FAILED -> "failed"
        ActivityState.CANCELLED -> "cancelled"
        ActivityState.RETRIED -> "retried"
        ActivityState.INFO -> "event"
    }
    val elapsed = when {
        event.durationMs != null -> durationLabel(event.durationMs)
        event.state == ActivityState.RUNNING -> durationLabel((now - event.startedAtMs).coerceAtLeast(0))
        else -> null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded; userChanged = true }, verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (expanded) "▾" else "▸", color = color, fontSize = 10.sp)
            Text(event.title, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text(state + (elapsed?.let { " · $it" } ?: ""), color = color, fontSize = 9.sp)
        }
        // Search and verification outcomes stay visible without opening every individual event.
        if (event.state == ActivityState.INFO && event.detail.isNotBlank()) {
            Text(event.detail, color = TraceText, fontSize = 10.sp, modifier = Modifier.padding(start = 14.dp, top = 2.dp))
        }
        if (expanded) {
            if (event.state != ActivityState.INFO && event.detail.isNotBlank()) Text(event.detail, color = TraceText, fontSize = 10.sp, modifier = Modifier.padding(start = 14.dp, top = 2.dp))
            if (event.kind == ActivityKind.SCENE_JSON && isModelResponse) {
                Text("Declarative scene data, interpreted by the Canvas renderer; not executable code.", color = TraceText, fontSize = 9.sp, modifier = Modifier.padding(start = 14.dp, top = 3.dp))
            }
            if (event.output.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp).heightIn(max = 180.dp)
                    .background(Color(0xFF0D0B12), RoundedCornerShape(8.dp)).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    SelectionContainer { Text(event.output, color = Color(0xFFB8EFFF), fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace) }
                }
                if (event.truncated) Text("Output truncated in this trace", color = TraceText, fontSize = 9.sp, modifier = Modifier.padding(start = 14.dp))
            } else if (isModelResponse && event.state == ActivityState.RUNNING) {
                Text("Waiting for model output…", color = TraceText, fontSize = 10.sp, modifier = Modifier.padding(start = 14.dp, top = 2.dp))
            }
        }
    }
}

private fun durationLabel(ms: Long): String = if (ms < 1000L) "${ms} ms" else "${ms / 1000}.${(ms % 1000) / 100} s"
