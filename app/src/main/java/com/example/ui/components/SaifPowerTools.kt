package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flow.FlowPlan
import com.example.power.CrashGuard
import com.example.power.SaifDiagnostics
import com.example.power.SaifToolkit
import com.example.utils.SaifVoice
import com.example.utils.TTSManager
import com.jarvis.assistant.SaifAutomation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Round 15 — SAIF POWER TOOLS.
 *
 * One screen that makes every subsystem *observable* and gives the owner 15 real tools, so a feature that
 * misbehaves can be diagnosed on the phone instead of guessed at:
 *
 *  1 Self test · 2 Crash report · 3 API key test · 4 Model browser · 5 Storage manager ·
 *  6 Caption/SRT exporter · 7 Shot list CSV · 8 Cover maker · 9 Flow library manager ·
 * 10 Prompt vault · 11 Voice studio · 12 Flow resume queue · 13 Batch picture fixer ·
 * 14 Provider health · 15 Usage analytics
 */
@Composable
fun SaifPowerToolsScreen(onClose: () -> Unit, onStartLiveTest: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    fun say(text: String) {
        toast = text
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF07060B)).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🧰 SAIF POWER TOOLS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Text("15 tools · har feature ka asli status", color = Color(0xFF9E95AC), fontSize = 9.sp)
                }
                if (busy) LinearProgressIndicator(Modifier.width(54.dp), color = Color(0xFF00E5FF), trackColor = Color(0xFF1B1630))
                TextButton(onClick = onClose) { Text("Close", color = Color(0xFF00E5FF), fontSize = 11.sp) }
            }
            if (toast.isNotBlank()) {
                Text(toast, color = Color(0xFF9EE6B8), fontSize = 9.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp))
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { DiagnosticsCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { LiveCard(onStartLiveTest = onStartLiveTest, onSay = ::say) }
                item { CrashCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { ApiTestCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { ModelBrowserCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { StorageCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { CaptionCard(onSay = ::say) }
                item { ShotListCard(onSay = ::say) }
                item { CoverCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { LibraryCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { PromptVaultCard(onSay = ::say) }
                item { VoiceStudioCard(onSay = ::say) }
                item { FlowResumeCard(onSay = ::say) }
                item { BatchCard(busy = busy, setBusy = { busy = it }, onSay = ::say) }
                item { HealthCard(onSay = ::say) }
                item { AnalyticsCard(onSay = ::say) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    accent: Color = Color(0xFF8B5CF6),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF120F1C)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp, 16.dp).background(accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Color(0xFF8B84A0), fontSize = 8.5.sp, lineHeight = 11.sp)
                }
            }
            content()
        }
    }
}

// 1 ─────────────────────────────────────────────────────────────────────────── self test
@Composable
private fun DiagnosticsCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf<List<SaifDiagnostics.Check>>(emptyList()) }
    var ran by remember { mutableStateOf(false) }

    ToolCard("1 · Self test", "Har subsystem ka asli PASS/FAIL + fix, crash trace ke saath", Color(0xFF22D3EE)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                setBusy(true)
                scope.launch {
                    val r = SaifDiagnostics.run(ctx)
                    checks = r; ran = true; setBusy(false)
                    onSay("Self test: ${r.count { it.ok }}/${r.size} pass")
                }
            }) { Text("Run self test", fontSize = 10.sp) }
            if (checks.isNotEmpty()) TextButton(onClick = {
                val text = SaifDiagnostics.report(ctx, checks)
                val uri = SaifToolkit.Exports.saveText(ctx, "SAIF_selftest.txt", "text/plain", text)
                if (uri != null) onSay("Report saved to Downloads/SAIF AI") else onSay("Report save fail")
            }) { Text("Share report", fontSize = 10.sp) }
        }
        if (ran && checks.isEmpty()) Text("Kuch bhi check nahi hua", color = Color(0xFFFF8F8F), fontSize = 9.sp)
        checks.forEach { c ->
            Text(
                (if (c.ok) "✅ " else "❌ ") + c.title + " — " + c.detail,
                color = if (c.ok) Color(0xFF86EFAC) else Color(0xFFFFB86B), fontSize = 9.sp, lineHeight = 12.sp
            )
            if (!c.ok && c.fix.isNotBlank()) Text("    → " + c.fix, color = Color(0xFF93C5FD), fontSize = 8.5.sp)
        }
    }
}

// 2 ─────────────────────────────────────────────────────────────────────────── live
@Composable
private fun LiveCard(onStartLiveTest: () -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val liveRunning by SaifAutomation.isLiveRunning.collectAsState()
    ToolCard("Live baatcheet", "Mic, voice engine aur session ka status — ek tap test", Color(0xFFA855F7)) {
        val mic = SaifToolkit.isGranted(ctx, android.Manifest.permission.RECORD_AUDIO)
        Text(
            "Mic: " + (if (mic) "allowed" else "DENIED") + " · engine: " + (if (SaifAutomation.isLinked) "linked" else "not linked") +
                " · session: " + (if (liveRunning) "RUNNING" else "idle"),
            color = if (mic) Color(0xFF86EFAC) else Color(0xFFFFB86B), fontSize = 9.sp
        )
        Text("API: " + SaifAutomation.currentApi().let { "${it.provider.ifBlank { "auto" }} · ${it.model.ifBlank { "default" }}" },
            color = Color(0xFF9E95AC), fontSize = 8.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (!mic) onSay("Mic permission nahi hai — Settings → Permissions se ON karo")
                else onStartLiveTest()
            }) { Text("Live test shuru", fontSize = 10.sp) }
            if (liveRunning) TextButton(onClick = { SaifAutomation.stopLive(ctx); onSay("Live band") }) { Text("Stop", fontSize = 10.sp) }
        }
    }
}

// 3 ─────────────────────────────────────────────────────────────────────────── crash
@Composable
private fun CrashCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val logs = remember { CrashGuard.logs(ctx) }
    ToolCard("2 · Crash report", "App band ho jaye to yahan asli stack trace milega", Color(0xFFEF4444)) {
        Text(if (logs.isEmpty()) "Koi crash record nahi" else "${logs.size} crash log · latest: ${logs.first().name}",
            color = if (logs.isEmpty()) Color(0xFF86EFAC) else Color(0xFFFFB86B), fontSize = 9.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = logs.isNotEmpty(), onClick = { text = CrashGuard.lastCrashText(ctx); show = true }) {
                Text("Dekho", fontSize = 10.sp)
            }
            OutlinedButton(enabled = logs.isNotEmpty(), onClick = {
                val t = CrashGuard.lastCrashText(ctx)
                val uri = SaifToolkit.Exports.saveText(ctx, "SAIF_crash_${System.currentTimeMillis()}.txt", "text/plain", t)
                onSay(if (uri != null) "Crash report saved (Downloads/SAIF AI)" else "Save fail")
            }) { Text("Share", fontSize = 10.sp) }
            TextButton(enabled = logs.isNotEmpty(), onClick = {
                val n = CrashGuard.clear(ctx); text = ""; onSay("$n crash log delete")
            }) { Text("Clear", fontSize = 10.sp) }
        }
        if (show && text.isNotBlank()) {
            Text(text.take(1200), color = Color(0xFFCBD5E1), fontSize = 8.sp, lineHeight = 11.sp,
                modifier = Modifier.heightIn(max = 160.dp).fillMaxWidth())
        }
    }
}

// 4 ─────────────────────────────────────────────────────────────────────────── api ping
@Composable
private fun ApiTestCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    ToolCard("3 · API key test", "Asli chhoti call — key/model/network ka proof + latency", Color(0xFF34D399)) {
        OutlinedButton(enabled = !busy, onClick = {
            setBusy(true); result = "Testing…"
            scope.launch {
                val p = SaifToolkit.Models.ping(ctx)
                result = (if (p.ok) "OK " else "FAIL ") + p.ms + "ms · " + p.provider + " / " + p.model + "\n" + p.reply
                setBusy(false)
                onSay(if (p.ok) "API OK (" + p.ms + "ms)" else "API fail — " + p.reply.take(60))
            }
        }) { Text("Run test", fontSize = 10.sp) }
        if (result.isNotBlank()) Text(result, color = Color(0xFFCBD5E1), fontSize = 9.sp, lineHeight = 12.sp)
    }
}

// 5 ─────────────────────────────────────────────────────────────────────────── models
@Composable
private fun ModelBrowserCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    ToolCard("4 · Model browser", "Provider se asli model list — tap karke copy", Color(0xFF60A5FA)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = !busy, onClick = {
                setBusy(true); error = ""
                scope.launch {
                    val r = SaifToolkit.Models.list(ctx)
                    models = r.getOrNull() ?: emptyList()
                    error = r.exceptionOrNull()?.message ?: ""
                    setBusy(false)
                    onSay(if (models.isEmpty()) "Model list nahi mili" else "${models.size} model mile")
                }
            }) { Text("Fetch models", fontSize = 10.sp) }
            OutlinedTextField(query, { query = it }, Modifier.weight(1f).heightIn(min = 46.dp), singleLine = true,
                placeholder = { Text("search", fontSize = 10.sp) })
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF8F8F), fontSize = 9.sp, maxLines = 3)
        val shown = models.filter { query.isBlank() || it.contains(query, true) }.take(40)
        shown.forEach { m ->
            Text(m, color = Color(0xFFCBD5E1), fontSize = 9.sp, modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    com.example.termux.TermuxBridge.copy(ctx, m)
                    onSay("Copied: $m")
                }
                .padding(vertical = 2.dp))
        }
        if (models.isNotEmpty()) Text("(tap = copy · Settings me paste karo)", color = Color(0xFF6B6480), fontSize = 8.sp)
    }
}

// 6 ─────────────────────────────────────────────────────────────────────────── storage
@Composable
private fun StorageCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    var buckets by remember { mutableStateOf(SaifToolkit.Storage.buckets(ctx)) }
    val free = remember { SaifToolkit.Storage.freeBytes(ctx) }
    ToolCard("5 · Storage", "Kahan jagah ja rahi hai — ek tap clean", Color(0xFFF59E0B)) {
        Text("Free: " + SaifToolkit.Storage.fmt(free), color = Color(0xFFCBD5E1), fontSize = 9.sp)
        buckets.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.name + " · " + SaifToolkit.Storage.fmt(b.bytes) + " (" + b.files + ")",
                    color = Color(0xFFCBD5E1), fontSize = 9.sp, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = {
                    setBusy(true)
                    val freed = SaifToolkit.Storage.clear(ctx, b.name)
                    buckets = SaifToolkit.Storage.buckets(ctx)
                    setBusy(false)
                    onSay("Freed " + SaifToolkit.Storage.fmt(freed))
                }) { Text("Clean", fontSize = 9.sp, color = Color(0xFFFFB86B)) }
            }
        }
    }
}

// 7 ─────────────────────────────────────────────────────────────────────────── captions
@Composable
private fun CaptionCard(onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    ToolCard("6 · Captions → SRT / VTT", "Narration paste karo, timed captions ban jayengi", Color(0xFFA78BFA)) {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 140.dp),
            placeholder = { Text("Narration / script paste karo…", fontSize = 10.sp) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = text.length > 10, onClick = {
                scope.launch {
                    val body = withContext(Dispatchers.Default) { SaifToolkit.Exports.srt(text) }
                    val uri = SaifToolkit.Exports.saveText(ctx, SaifToolkit.Exports.srtName(), "application/x-subrip", body)
                    onSay(if (uri != null) "SRT saved (Downloads/SAIF AI)" else "SRT save fail")
                }
            }) { Text("SRT", fontSize = 10.sp) }
            OutlinedButton(enabled = text.length > 10, onClick = {
                scope.launch {
                    val body = withContext(Dispatchers.Default) { SaifToolkit.Exports.vtt(text) }
                    val uri = SaifToolkit.Exports.saveText(ctx, SaifToolkit.Exports.vttName(), "text/vtt", body)
                    onSay(if (uri != null) "VTT saved (Downloads/SAIF AI)" else "VTT save fail")
                }
            }) { Text("VTT", fontSize = 10.sp) }
            OutlinedButton(enabled = text.isNotBlank(), onClick = {
                val n = SaifToolkit.Exports.cues(text).size
                onSay("$n caption cues banenge (~2.6s each)")
            }) { Text("Preview count", fontSize = 10.sp) }
        }
    }
}

// 8 ─────────────────────────────────────────────────────────────────────────── shot list
@Composable
private fun ShotListCard(onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val plan = remember { SaifToolkit.Resume.latestPlan(ctx) }
    ToolCard("7 · Shot list (CSV)", "Scene-by-scene plan: prompt, status, verdict — editor ke liye", Color(0xFF14B8A6)) {
        Text(SaifToolkit.Resume.planLine(plan), color = Color(0xFFCBD5E1), fontSize = 9.sp)
        OutlinedButton(enabled = plan != null, onClick = {
            val p = plan ?: return@OutlinedButton
            scope.launch {
                val csv = SaifToolkit.Exports.shotListCsv(p)
                val uri = SaifToolkit.Exports.saveText(ctx, SaifToolkit.Exports.csvName("shotlist"), "text/csv", csv)
                onSay(if (uri != null) "CSV saved (Downloads/SAIF AI)" else "CSV save fail")
            }
        }) { Text("Export CSV", fontSize = 10.sp) }
    }
}

// 9 ─────────────────────────────────────────────────────────────────────────── cover
@Composable
private fun CoverCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("SAIF AI") }
    val images = remember { com.example.flow.FlowLibrary.images(ctx) }
    ToolCard("8 · Cover maker", "Kisi bhi Flow image se 1080×1920 title cover", Color(0xFFEC4899)) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Cover title", fontSize = 10.sp) })
        OutlinedTextField(subtitle, { subtitle = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Brand line (default SAIF AI)", fontSize = 10.sp) })
        Text(if (images.isEmpty()) "Library khali hai — pehle Flow image banao" else "Latest image use hogi: " + File(images.maxByOrNull { it.createdAt }!!.path).name,
            color = Color(0xFF9E95AC), fontSize = 8.5.sp)
        OutlinedButton(enabled = !busy && images.isNotEmpty(), onClick = {
            setBusy(true)
            scope.launch {
                val src = images.maxByOrNull { it.createdAt }
                val made = withContext(Dispatchers.IO) {
                    src?.let { SaifToolkit.Cover.make(ctx, File(it.path), title.ifBlank { "SAIF AI" }, subtitle) }
                }
                setBusy(false)
                onSay(if (made != null) "Cover saved: " + made.name else "Cover banane mein fail")
            }
        }) { Text("Cover banao", fontSize = 10.sp) }
    }
}

// 10 ────────────────────────────────────────────────────────────────────────── library
@Composable
private fun LibraryCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var count by remember { mutableStateOf(com.example.flow.FlowLibrary.count(ctx)) }
    var confirmClear by remember { mutableStateOf(false) }
    ToolCard("9 · Flow library", "Dedupe, index export, saaf karo", Color(0xFF38BDF8)) {
        Text("$count record(s) · images: " + com.example.flow.FlowLibrary.images(ctx).size, color = Color(0xFFCBD5E1), fontSize = 9.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                setBusy(true)
                scope.launch {
                    val removed = withContext(Dispatchers.IO) {
                        val all = com.example.flow.FlowLibrary.records(ctx)
                        val seen = HashSet<String>()
                        var n = 0
                        all.forEach { r ->
                            val key = if (r.hash.isNotBlank()) r.hash else r.path
                            if (!seen.add(key)) { com.example.flow.FlowLibrary.remove(ctx, r.id); n++ }
                        }
                        n
                    }
                    count = com.example.flow.FlowLibrary.count(ctx)
                    setBusy(false)
                    onSay("$removed duplicate hata diye")
                }
            }) { Text("Dedupe", fontSize = 10.sp) }
            OutlinedButton(enabled = !busy, onClick = {
                setBusy(true)
                scope.launch {
                    val json = withContext(Dispatchers.IO) {
                        val arr = org.json.JSONArray()
                        com.example.flow.FlowLibrary.records(ctx).forEach { r ->
                            arr.put(org.json.JSONObject()
                                .put("id", r.id).put("title", r.title).put("path", r.path)
                                .put("w", r.width).put("h", r.height).put("bytes", r.bytes)
                                .put("role", r.role).put("scene", r.sceneId).put("hash", r.hash))
                        }
                        arr.toString(2)
                    }
                    val uri = SaifToolkit.Exports.saveText(ctx, "SAIF_flow_index.json", "application/json", json)
                    setBusy(false)
                    onSay(if (uri != null) "Index saved (Downloads/SAIF AI)" else "Index save fail")
                }
            }) { Text("Index JSON", fontSize = 10.sp) }
            TextButton(onClick = { confirmClear = true }) { Text("Clear all", fontSize = 10.sp, color = Color(0xFFFF8F8F)) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Flow library clear?") },
            text = { Text("Saare Flow records index se hat jayenge (files device pe rahengi). Timeline pe lagi pictures par asar nahi.") },
            confirmButton = { TextButton(onClick = {
                com.example.flow.FlowLibrary.clear(ctx)
                count = 0; confirmClear = false; onSay("Library index clear")
            }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

// 11 ────────────────────────────────────────────────────────────────────────── prompt vault
@Composable
private fun PromptVaultCard(onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(SaifToolkit.PromptVault.list(ctx)) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    ToolCard("10 · Prompt vault", "Best prompts save karo, dobara use karo", Color(0xFFFBBF24)) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Naam (e.g. neon city hook)", fontSize = 10.sp) })
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 120.dp),
            placeholder = { Text("Image prompt…", fontSize = 10.sp) })
        OutlinedButton(enabled = text.trim().length > 10, onClick = {
            val e = SaifToolkit.PromptVault.add(ctx, title, text)
            entries = SaifToolkit.PromptVault.list(ctx)
            title = ""; text = ""
            onSay(if (e != null) "Prompt saved" else "Prompt bahut chhota hai")
        }) { Text("Save prompt", fontSize = 10.sp) }
        entries.take(12).forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(e.text.take(90), color = Color(0xFF9E95AC), fontSize = 8.sp, maxLines = 2)
                }
                TextButton(onClick = { com.example.termux.TermuxBridge.copy(ctx, e.text); onSay("Copied") }) { Text("copy", fontSize = 9.sp) }
                TextButton(onClick = { SaifToolkit.PromptVault.remove(ctx, e.id); entries = SaifToolkit.PromptVault.list(ctx) }) {
                    Text("🗑", fontSize = 9.sp)
                }
            }
        }
    }
}

// 12 ────────────────────────────────────────────────────────────────────────── voice studio
@Composable
private fun VoiceStudioCard(onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(SaifVoice.selectedId(ctx)) }
    ToolCard("11 · Voice studio", "Har voice ka preview + select (poori app ke liye)", Color(0xFFC084FC)) {
        Text("Selected: " + selected + " · preview device TTS se", color = Color(0xFF9E95AC), fontSize = 8.5.sp)
        VOICE_PROFILES.forEach { v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v.name + (if (v.id == selected) "  ✓" else ""),
                        color = if (v.id == selected) Color(0xFF86EFAC) else Color.White, fontSize = 9.5.sp)
                    Text(v.description.take(70), color = Color(0xFF8B84A0), fontSize = 8.sp, maxLines = 1)
                }
                TextButton(onClick = { TTSManager.previewVoice(ctx, v.previewHinglish, v.id) }) { Text("hear", fontSize = 9.sp) }
                TextButton(onClick = {
                    SaifVoice.select(ctx, v.id); selected = v.id; onSay(v.name + " selected")
                }) { Text("use", fontSize = 9.sp) }
            }
        }
    }
}

// 13 ────────────────────────────────────────────────────────────────────────── flow resume
@Composable
private fun FlowResumeCard(onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    var plan by remember { mutableStateOf<FlowPlan?>(SaifToolkit.Resume.latestPlan(ctx)) }
    ToolCard("12 · Flow resume", "Adhoora plan wahin se continue karo", Color(0xFF4ADE80)) {
        Text(SaifToolkit.Resume.planLine(plan), color = Color(0xFFCBD5E1), fontSize = 9.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = plan != null, onClick = {
                val p = plan ?: return@OutlinedButton
                com.example.flow.web.FlowBrowserActivity.start(ctx, p.projectId, true, p.topic)
                onSay("Flow browser khul raha hai — pending prompts khud chalu honge")
            }) { Text("Resume in browser", fontSize = 10.sp) }
            OutlinedButton(enabled = plan != null, onClick = {
                val p = plan ?: return@OutlinedButton
                com.example.flow.FlowBridgeBus.publishPlan(p, true)
                onSay("Plan timeline pe bhej diya")
            }) { Text("Send to timeline", fontSize = 10.sp) }
            TextButton(onClick = {
                plan = SaifToolkit.Resume.latestPlan(ctx)
                onSay("Refreshed")
            }) { Text("refresh", fontSize = 9.sp) }
        }
    }
}

// 14 ────────────────────────────────────────────────────────────────────────── batch fixer
@Composable
private fun BatchCard(busy: Boolean, setBusy: (Boolean) -> Unit, onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    ToolCard("13 · Batch picture fixer", "Har Flow image verify + optional 9:16 crop copies", Color(0xFF818CF8)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                setBusy(true); progress = 0f
                scope.launch {
                    val r = SaifToolkit.Batch.verifyAndCrop(ctx, crop = false) { i, total ->
                        progress = if (total == 0) 1f else i.toFloat() / total
                    }
                    report = r.lines; setBusy(false)
                    onSay("Verified ${r.done}/${r.total} · failed ${r.failed}")
                }
            }) { Text("Verify all", fontSize = 10.sp) }
            OutlinedButton(enabled = !busy, onClick = {
                setBusy(true); progress = 0f
                scope.launch {
                    val r = SaifToolkit.Batch.verifyAndCrop(ctx, crop = true) { i, total ->
                        progress = if (total == 0) 1f else i.toFloat() / total
                    }
                    report = r.lines; setBusy(false)
                    onSay("9:16 copies: ${r.done} done · ${r.failed} fail")
                }
            }) { Text("Verify + crop", fontSize = 10.sp) }
        }
        if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF00E5FF))
        report.take(12).forEach { line ->
            Text(line, color = Color(0xFF9E95AC), fontSize = 8.sp, maxLines = 2)
        }
    }
}

// 15 ────────────────────────────────────────────────────────────────────────── health
@Composable
private fun HealthCard(onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<SaifToolkit.Models.Ping>>(emptyList()) }
    ToolCard("14 · Provider health", "3 asli calls — latency aur fail ka pattern", Color(0xFF2DD4BF)) {
        OutlinedButton(onClick = {
            scope.launch {
                val out = ArrayList<SaifToolkit.Models.Ping>()
                repeat(3) { out.add(SaifToolkit.Models.ping(ctx)) }
                rows = out
                onSay(out.count { it.ok }.toString() + "/3 OK")
            }
        }) { Text("Run 3 pings", fontSize = 10.sp) }
        rows.forEachIndexed { i, p ->
            Text(
                "#${i + 1} " + (if (p.ok) "OK " else "FAIL ") + p.ms + "ms · " + p.provider + " / " + p.model +
                    (if (!p.ok) " — " + p.reply.take(80) else " — " + p.reply.take(20)),
                color = if (p.ok) Color(0xFF86EFAC) else Color(0xFFFF8F8F), fontSize = 8.5.sp
            )
        }
    }
}

// 16 ────────────────────────────────────────────────────────────────────────── analytics
@Composable
private fun AnalyticsCard(onSay: (String) -> Unit) {
    val ctx = LocalContext.current
    var counts by remember { mutableStateOf(SaifToolkit.Counters.all(ctx)) }
    val keys = listOf("flow_images", "videos_applied", "live_sessions", "prompts_created", "exports")
    ToolCard("15 · Usage analytics", "Kitna kaam hua — local counters (koi cloud nahi)", Color(0xFFF472B6)) {
        if (counts.isEmpty()) Text("Abhi koi counter nahi — kaam karte hi bharna shuru hoga", color = Color(0xFF9E95AC), fontSize = 9.sp)
        val max = (keys.map { counts[it] ?: 0 }.maxOrNull() ?: 0).coerceAtLeast(1)
        keys.forEach { k ->
            val v = counts[k] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Color(0xFFCBD5E1), fontSize = 8.5.sp, modifier = Modifier.width(110.dp))
                Box(Modifier.weight(1f).height(6.dp).background(Color(0xFF1B1630), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth((v.toFloat() / max).coerceIn(0f, 1f)).height(6.dp)
                        .background(Color(0xFFF472B6), RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.width(6.dp))
                Text(v.toString(), color = Color.White, fontSize = 8.5.sp)
            }
        }
        TextButton(onClick = { SaifToolkit.Counters.reset(ctx); counts = emptyMap(); onSay("Counters reset") }) {
            Text("Reset", fontSize = 9.sp, color = Color(0xFF9E95AC))
        }
    }
}

