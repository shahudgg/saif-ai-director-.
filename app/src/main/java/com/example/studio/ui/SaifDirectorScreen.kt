package com.example.studio.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.director.ChatTurn
import com.example.studio.director.DirectorController
import com.example.studio.director.DirectorWorkflow
import com.example.studio.director.QuestionCard
import com.example.studio.director.WebResearch

private val Bg = Color(0xFF09080E); private val Glass = Color(0xFF17131F); private val Purple = Color(0xFF7B2BFF); private val Cyan = Color(0xFF00E5FF)

@Composable
fun SaifDirectorScreen(controller: DirectorController, onClose: () -> Unit, autoAgentGoal: String? = null) {
    val ui by controller.ui.collectAsStateWithLifecycle()
    val activity by controller.activity.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val wf = controller.workflow
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showAgent by remember { mutableStateOf(false) }
    var showFlowSetup by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var checkingMusic by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler(onBack = onClose)
    // Round 13: floating-AI / StudioLaunch can hand a goal straight to the SAIF Agent crew.
    LaunchedEffect(autoAgentGoal) {
        val g = autoAgentGoal
        if (!g.isNullOrBlank() && !ui.working) controller.agentRun(g)
    }
    LaunchedEffect(ui.turns.size) {
        if (ui.turns.size > 1) listState.animateScrollToItem(ui.turns.size - 1 + if (wf == DirectorWorkflow.ZERO_TO_VIDEO) 1 else 0)
    }
    LaunchedEffect(ui.liveScenes.isNotEmpty()) {
        if (ui.liveScenes.isNotEmpty()) listState.animateScrollToItem(ui.turns.size + if (wf == DirectorWorkflow.ZERO_TO_VIDEO) 1 else 0)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null } ?: "selected file"
            controller.onSourcePicked(uri, name)
        }
    }
    fun pick() = picker.launch(if (wf.needsSourceAudio) arrayOf("audio/*") else arrayOf("video/*"))

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Header — Round 10: premium gradient banner with the SAIF AI • AI STUDIO badge
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF2A1650), Color(0xFF12081F), Bg)))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to timeline", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Purple.copy(alpha = 0.25f)) {
                            Text("SAIF AI • AI STUDIO", color = Color(0xFFD9C6FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(wf.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.6.sp)
                    }
                    Text(wf.subtitle, color = Color(0xFF9E95AC), fontSize = 10.sp, maxLines = 1)
                }
                if (wf == DirectorWorkflow.ZERO_TO_VIDEO) TextButton(onClick = { scrollScope.launch { listState.animateScrollToItem(0) } }) { Text("Brief", color = Cyan, fontSize = 11.sp) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Research, asset sources & Flow AI", tint = Color(0xFFCCC3D9)) }
            }
        }

        // Round 12 — one-time Flow setup banner (the video's "browser button" at the top of zero-to-video)
        if (wf == DirectorWorkflow.ZERO_TO_VIDEO && com.example.ui.components.FlowAssetStore.count(ctx) == 0) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF1D1430), border = BorderStroke(1.dp, Color(0x557B2BFF))) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1️⃣", fontSize = 16.sp)
                    Column(Modifier.weight(1f)) {
                        Text("One-time setup: Google Flow mein login karo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Browser kholo, flow.google.com pe Gmail se login karo — baad mein SAIF Agent wahin se topic ke liye saari images 8-8 second ke gap mein bana lega aur yahin download karega.", color = Color(0xFF9E95AC), fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    OutlinedButton(onClick = { showFlowSetup = true }) { Text("Open browser", fontSize = 10.sp) }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (wf == DirectorWorkflow.ZERO_TO_VIDEO) item(key = "guided-brief") {
                GuidedBriefPanel(ui.brief, editingBrief = !ui.working && ui.manifest == null, editingMusic = !ui.working && !checkingMusic,
                    onOption = controller::selectBriefOption, onVoice = controller::selectNarrationVoice, onMusic = controller::selectBackgroundMusic,
                    musicScope = scrollScope, onMusicBusy = { checkingMusic = it })
            }
            items(ui.turns, key = { it.id }) { t -> Bubble(t,
                canAnswer = !ui.working && !checkingMusic && (wf != DirectorWorkflow.ZERO_TO_VIDEO || ui.manifest == null) && (t.cardAnswered != null || t.id == ui.turns.lastOrNull()?.id),
                onAnswer = controller::answer, onSkip = controller::skip, onEdit = controller::editAnswer) }
            item {
                val st = ui.status
                if (st != null) StatusPanel(st.label, st.detail, st.fraction, st.failed, ui.working, ui.paused,
                    onPause = { controller.setPaused(!ui.paused) }, onCancel = controller::cancel)
                if (ui.liveScenes.isNotEmpty()) LiveStoryboard(ui.liveScenes, ui.brief.aspect ?: "9:16")
                if (ui.problems.isNotEmpty()) Surface(shape = RoundedCornerShape(12.dp), color = Glass, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PRODUCTION NOTES", color = Color(0xFFFFB86B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val notes = ui.problems.sortedBy { if (it.startsWith("Music ")) 0 else 1 }
                        notes.take(12).forEach { Text("• $it", color = Color(0xFFCCC3D9), fontSize = 10.sp) }
                        if (notes.size > 12) Text("${notes.size - 12} more notes", color = Color(0xFF9E95AC), fontSize = 10.sp)
                    }
                }
                AgentActivityPanel(activity)
                if (ui.sources.isNotEmpty()) Surface(shape = RoundedCornerShape(12.dp), color = Glass, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Sources (${ui.sources.size})", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        ui.sources.take(8).forEachIndexed { i, s -> Text("[${i + 1}] ${s.title.take(60)} — ${s.url.take(60)}", color = Color(0xFF9E95AC), fontSize = 10.sp) }
                    }
                }
                ui.manifest?.let { m -> Surface(shape = RoundedCornerShape(12.dp), color = Glass, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Scene plan • ${m.scenes.size} scenes • ${m.totalMs / 1000}s", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val canPick = !ui.working && !checkingMusic && (ui.readyToApply || ui.applied)
                        if (canPick) Text("Use a card to change only that scene", color = Color(0xFF9E95AC), fontSize = 10.sp)
                        SceneCards(m, canPick, ctx0 = ctx, aspect = ui.brief.aspect ?: "9:16",
                            onVariant = controller::regenerateVariant, onLock = controller::toggleLock,
                            onStyle = controller::changeStyle, onText = controller::editSceneText, onAsset = controller::replaceAsset, onBulk = controller::reviseScenes)
                    }
                } }
            }
        }

        // Action bar — only real, state-driven actions
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if ((wf.needsSourceVideo || wf.needsSourceAudio) && !ui.working)
                OutlinedButton(onClick = { pick() }) { Icon(Icons.Default.AttachFile, null); Text(if (ui.sourceName == null) "Attach ${if (wf.needsSourceAudio) "audio" else "video"}" else "Change file") }
            if (wf == DirectorWorkflow.ZERO_TO_VIDEO && !ui.working && ui.manifest == null) OutlinedButton(onClick = { showAgent = true }) { Text("🤖 SAIF Agent") }
            if (wf == DirectorWorkflow.ZERO_TO_VIDEO && !ui.working) OutlinedButton(onClick = { showBatch = true }) { Text("🖼 Flow images") }
            if (wf == DirectorWorkflow.ZERO_TO_VIDEO && !ui.working) OutlinedButton(onClick = { showFlowSetup = true }) { Text("🌐 Flow browser") }
            if (ui.awaitingBriefConfirm && !ui.working) Button(enabled = !checkingMusic, onClick = controller::confirmAndStart, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Confirm & start") }
            if (ui.script.isNotBlank() && !ui.scriptApproved && ui.manifest == null && !ui.working) Button(enabled = !checkingMusic, onClick = controller::approveScript, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Approve script") }
            if (ui.readyToApply && !ui.working) Button(enabled = !checkingMusic, onClick = { controller.applyToTimeline() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) { Text("Add to timeline") }
            if (ui.status?.failed == true && !ui.working) Button(enabled = !checkingMusic, onClick = controller::retry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))) { Text("Retry") }
            if (ui.script.isNotBlank() && !ui.working) OutlinedButton(onClick = {
                val uri = com.example.studio.director.VoiceoverExport.save(ctx, "voiceover", controller.voiceoverText())
                if (uri == null) Toast.makeText(ctx, "Could not save the voiceover script", Toast.LENGTH_SHORT).show()
                else {
                    Toast.makeText(ctx, "Saved: Downloads/SAIF AI", Toast.LENGTH_SHORT).show()
                    runCatching {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share voiceover script"))
                    }
                }
            }) { Text("Voiceover script") }
            if (ui.manifest != null && (ui.readyToApply || ui.applied) && !ui.working) OutlinedButton(enabled = !checkingMusic, onClick = controller::strengthenHook) { Text("Stronger hook") }
            if (ui.manifest != null && (ui.readyToApply || ui.applied) && !ui.working) OutlinedButton(enabled = !checkingMusic, onClick = controller::attachFlowImages) { Text("🖼 Flow images lagao") }
            if (ui.applied) OutlinedButton(onClick = onClose) { Text("Back to timeline") }
            if (!ui.working) TextButton(enabled = !checkingMusic, onClick = controller::resetConversation) { Text("New chat", color = Color(0xFF9E95AC)) }
        }

        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), enabled = !ui.working && !checkingMusic,
                placeholder = { Text(if (ui.script.isNotBlank() && !ui.scriptApproved) "Describe script changes…" else if (ui.readyToApply || ui.applied) "e.g. “Scene 2: make it a bar chart”" else "Message the AI Director…", fontSize = 13.sp) },
                maxLines = 5, shape = RoundedCornerShape(16.dp))
            IconButton(onClick = { if (input.isNotBlank()) { controller.send(input); input = "" } }, enabled = !ui.working && !checkingMusic) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Cyan) }
        }
    }
    if (showSettings) KeysDialog(onDismiss = { showSettings = false })
    if (showAgent) AgentDialog(onDismiss = { showAgent = false }, onRun = { goal -> showAgent = false; controller.agentRun(goal) })
    if (showFlowSetup) com.example.ui.components.FlowAiBrowserDialog(onDismiss = { showFlowSetup = false })
    if (showBatch) com.example.ui.components.FlowBatchDialog(
        topic = ui.brief.prompt.ifBlank { input }.ifBlank { "short explainer video" },
        aspect = ui.brief.aspect ?: "9:16", style = ui.brief.style ?: "",
        onDismiss = { showBatch = false }, onDone = { controller.attachFlowImages() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(t: ChatTurn, canAnswer: Boolean, onAnswer: (String, String) -> Unit, onSkip: (String) -> Unit, onEdit: (String) -> Unit) {
    val mine = t.fromUser
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(shape = RoundedCornerShape(16.dp), color = if (t.isError) Color(0xFF3A1620) else if (mine) Purple else Glass, modifier = Modifier.widthIn(max = 330.dp)) {
            Text(t.text, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp), lineHeight = 18.sp)
        }
        t.card?.let { card -> QuestionCardView(card, t.cardAnswered, canAnswer, onAnswer, onSkip, onEdit) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCardView(card: QuestionCard, answered: String?, enabled: Boolean, onAnswer: (String, String) -> Unit, onSkip: (String) -> Unit, onEdit: (String) -> Unit) {
    var custom by remember { mutableStateOf(false) }; var text by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF110E1A), border = BorderStroke(1.dp, Purple.copy(alpha = 0.4f)), modifier = Modifier.padding(top = 6.dp).fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            if (answered != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ $answered", color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(enabled = enabled, onClick = { onEdit(card.key) }) { Text("Edit", fontSize = 11.sp) }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.options.forEach { o -> Chip(o, enabled = enabled) { onAnswer(card.key, o) } }
                    if (card.allowCustom) Chip(QuestionCard.CUSTOM, highlight = custom, enabled = enabled) { custom = !custom }
                    if (card.optional) Chip("Skip", enabled = enabled) { onSkip(card.key) }
                }
                if (custom) {
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = enabled, placeholder = { Text(card.customHint, fontSize = 12.sp) }, maxLines = 4)
                    Button(enabled = enabled, onClick = { if (text.isNotBlank()) onAnswer(card.key, text) }, modifier = Modifier.padding(top = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Use this") }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, highlight: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (highlight) Purple else Color(0xFF211B2E), modifier = Modifier.clickable(enabled = enabled, onClick = onClick)) {
        Text(label, color = if (enabled) Color.White else Color(0xFF9E95AC), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun StatusPanel(label: String, detail: String, fraction: Float?, failed: Boolean, working: Boolean, paused: Boolean, onPause: () -> Unit, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Glass, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Round 10: live stage dot — red on failure, amber while working, green when idle/done
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (failed) Color(0xFFFF6B6B) else if (working && !paused) Color(0xFFFFB86B) else Color(0xFF10B981)))
                Spacer(Modifier.width(8.dp))
                Text((if (paused) "⏸ Paused — " else if (working && !failed) "▶ " else "") + label + if (detail.isNotBlank()) "  ($detail)" else "",
                    color = if (failed) Color(0xFFFF6B6B) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            if (working && !paused) { // real fraction only when known; otherwise honest indeterminate bar
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().padding(top = 8.dp), color = if (failed) Color(0xFFFF6B6B) else Cyan)
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = if (failed) Color(0xFFFF6B6B) else Cyan)
            }
            if (working) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPause) { Text(if (paused) "Resume" else "Pause") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

/** Round 11 — SAIF Agent autopilot entry: one goal, the agent does everything (research → video → timeline). */
@Composable
private fun AgentDialog(onDismiss: () -> Unit, onRun: (String) -> Unit) {
    var goal by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("🤖 SAIF Agent — autopilot") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("One goal likho — agent khud research, script, plan, AI images, narration, assets aur timeline bana kar apply kar dega. Har scene unique banegi, live progress chat mein dikhega.", fontSize = 11.sp)
            OutlinedTextField(goal, { goal = it }, label = { Text("e.g. 60-second Hinglish video on how UPI works") }, maxLines = 4)
        } },
        confirmButton = { TextButton(enabled = goal.isNotBlank(), onClick = { onRun(goal.trim()) }) { Text("Run agent") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun KeysDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var provider by remember { mutableStateOf(WebResearch.provider(ctx)) }
    var searchKey by remember { mutableStateOf("") }; var tg by remember { mutableStateOf("") }
    var showFlow by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    Box {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Research, asset sources & Flow AI") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keys are encrypted with the Android Keystore and never shown or logged. Leave blank to keep the current value.", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Tavily", "Brave").forEach { p -> Chip(p, highlight = provider == p) { provider = p } } }
                OutlinedTextField(searchKey, { searchKey = it }, label = { Text("$provider search API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(tg, { tg = it }, label = { Text("Telegram bot token (asset library)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("Status: search ${if (WebResearch.isConfigured(ctx)) "configured" else "not set"}, Telegram ${if (WebResearch.telegramToken(ctx).isNotBlank()) "configured" else "not set"}, AI images ${if (com.example.llm.ImageGen.canGenerate(com.example.studio.ai.AiProfile.fromSettings(ctx).provider, com.example.studio.ai.AiProfile.fromSettings(ctx).apiKey)) "available with your ${com.example.studio.ai.AiProfile.fromSettings(ctx).provider} key" else "need a Gemini/OpenAI/OpenRouter key in Settings"}", fontSize = 11.sp)
                // Round 10: in-app Flow AI browser (login once) + AI asset tools over imported images.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showFlow = true }) { Text("🌐 Flow AI browser", fontSize = 11.sp) }
                    OutlinedButton(onClick = { showTools = true }) { Text("🖼 AI asset tools (${com.example.ui.components.FlowAssetStore.count(ctx)})", fontSize = 11.sp) }
                }
                ProductionOptions()
                TelegramDiagnostics()
            } },
            confirmButton = { TextButton(onClick = {
                val ok1 = searchKey.isBlank() || WebResearch.saveKey(ctx, provider, searchKey)
                val ok2 = tg.isBlank() || WebResearch.saveTelegramToken(ctx, tg)
                Toast.makeText(ctx, if (ok1 && ok2) "Saved securely" else "Secure storage unavailable on this device — nothing was saved", Toast.LENGTH_LONG).show(); onDismiss() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
        if (showFlow) com.example.ui.components.FlowAiBrowserDialog(onDismiss = { showFlow = false })
        if (showTools) com.example.ui.components.FlowAssetToolsDialog(onDismiss = { showTools = false })
    }
}


/** Honest status of the Telegram asset library: what was indexed, what was skipped and why, and one-tap fixes. */
@Composable
private fun TelegramDiagnostics() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var report by remember { mutableStateOf(com.example.ui.components.TelegramCatalogStore(ctx).lastReport()) }
    var count by remember { mutableStateOf(runCatching { com.example.ui.components.TelegramCatalogStore(ctx).items().size }.getOrDefault(0)) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    // Bot/channel health: token validity, channel reachability, bot admin status and webhook state.
    var diag by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(10.dp), color = Glass, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Telegram library • $count saved assets", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(report?.summary() ?: "Not synced yet.", color = Color(0xFFCCC3D9), fontSize = 10.sp)
            com.example.ui.components.TelegramAssetManager.lastDownloadError.takeIf { it.isNotBlank() }?.let { Text("Last download problem: $it", color = Color(0xFFFFB86B), fontSize = 10.sp) }
            if (note.isNotBlank()) Text(note, color = Color(0xFF9EE6B8), fontSize = 10.sp)
            if (diag.isNotBlank()) Text(diag.trim(), color = Color(0xFFB9C7FF), fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val rep = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.ui.components.TelegramCatalogStore(ctx).sync(WebResearch.telegramToken(ctx), com.example.ui.components.TelegramAssetManager.DEFAULT_CHANNEL_ID)
                        }
                        report = rep; count = com.example.ui.components.TelegramCatalogStore(ctx).items().size; busy = false
                    }
                }) { Text(if (busy) "Syncing…" else "Sync now", fontSize = 11.sp) }
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        diag = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.ui.components.TelegramCatalogStore(ctx)
                                .diagnose(WebResearch.telegramToken(ctx), com.example.ui.components.TelegramAssetManager.DEFAULT_CHANNEL_ID)
                        }
                        busy = false
                    }
                }) { Text("Check connection", fontSize = 11.sp) }
                if (report?.webhookUrl?.isNotBlank() == true) OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.ui.components.TelegramCatalogStore(ctx).removeWebhook(WebResearch.telegramToken(ctx)) }
                        note = if (ok) "Webhook removed. Tap Sync now." else "Could not remove the webhook."; busy = false
                    }
                }) { Text("Remove webhook", fontSize = 11.sp) }
            }
        }
    }
}


/** Brand kit: caption look, accent colour and automatic sound effects. Applied to every video the director makes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductionOptions() {
    val ctx = LocalContext.current
    var caption by remember { mutableStateOf(com.example.studio.director.BrandKit.captionStyle(ctx)) }
    var accent by remember { mutableStateOf(com.example.studio.director.BrandKit.accent(ctx) ?: "") }
    var sfx by remember { mutableStateOf(com.example.studio.director.BrandKit.autoSfx(ctx)) }
    Surface(shape = RoundedCornerShape(10.dp), color = Glass, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Style & sound (used for every new video)", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Caption style", fontSize = 10.sp, color = Color(0xFFCCC3D9))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.example.studio.render.CaptionStyles.ALL.forEach { st ->
                    Chip(st, highlight = caption == st) { caption = st; com.example.studio.director.BrandKit.setCaptionStyle(ctx, st) }
                }
            }
            Text("Narration voice", fontSize = 10.sp, color = Color(0xFFCCC3D9))
            var voice by remember { mutableStateOf(com.example.studio.director.BrandKit.narrationVoice(ctx)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.example.ui.components.VOICE_PROFILES.forEach { p -> Chip(p.name, highlight = voice == p.id) { voice = p.id; com.example.studio.director.BrandKit.setNarrationVoice(ctx, p.id) } }
            }
            Text("Accent colour", fontSize = 10.sp, color = Color(0xFFCCC3D9))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.example.studio.director.BrandKit.ACCENTS.forEach { (name, hex) ->
                    Chip(name, highlight = accent == hex) { accent = hex; com.example.studio.director.BrandKit.setAccent(ctx, hex) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Switch(checked = sfx, onCheckedChange = { sfx = it; com.example.studio.director.BrandKit.setAutoSfx(ctx, it) })
                Text("Automatic sound effects", fontSize = 11.sp)
            }
        }
    }
}


/**
 * Horizontally scrollable scene cards with a real rendered frame. Every card has Regenerate, Edit text, Replace asset,
 * Change style and Lock. Tick one or several cards to apply a written change to only those scenes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneCards(
    m: com.example.studio.director.SceneManifest, canAct: Boolean, ctx0: android.content.Context, aspect: String,
    onVariant: (Int) -> Unit, onLock: (Int) -> Unit,
    onStyle: (List<Int>) -> Unit, onText: (Int, String) -> Unit, onAsset: (Int) -> Unit, onBulk: (List<Int>, String) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val selected = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    var textEdit by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }
    val tap = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    val minH = Modifier.androidx_minTouch()

    if (selected.isNotEmpty() && canAct) Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF211B2E), border = BorderStroke(1.dp, Purple.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${selected.size} scene(s) selected: ${selected.sorted().joinToString { (it + 1).toString() }}", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(bulk, { bulk = it }, Modifier.fillMaxWidth(), placeholder = { Text("What should change in these scenes?", fontSize = 12.sp) }, maxLines = 3)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(enabled = bulk.isNotBlank(), modifier = minH, onClick = { tap(); onBulk(selected.toList(), bulk); bulk = ""; selected.clear() }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Apply to selected", fontSize = 12.sp) }
                OutlinedButton(modifier = minH, onClick = { tap(); onStyle(selected.toList()); selected.clear() }) { Text("Change style", fontSize = 12.sp) }
                TextButton(modifier = minH, onClick = { selected.clear() }) { Text("Clear", fontSize = 12.sp) }
            }
        }
    }

    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(count = m.scenes.size, key = { idx -> m.scenes[idx].id }) { i ->
            val s: com.example.studio.director.Scene = m.scenes[i]
            val isSel = i in selected
            var asking by remember(s.id) { mutableStateOf(false) }
            var instruction by remember(s.id) { mutableStateOf("") }
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1929), border = if (isSel) BorderStroke(2.dp, Cyan) else null, modifier = Modifier.widthIn(min = 170.dp, max = 190.dp)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box {
                        SceneThumb(s, aspect)
                        androidx.compose.material3.Checkbox(checked = isSel, enabled = canAct, onCheckedChange = { c -> if (c) selected.add(i) else selected.remove(i) }, modifier = Modifier.align(Alignment.TopStart))
                    }
                    Text("${i + 1}. ${s.visualObjective.substringBefore(':').lowercase().replace('_', ' ')}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${"%.1f".format(s.durationMs / 1000.0)} s" + (if (s.locked) "  🔒 locked" else "") + (if (s.assets.isNotEmpty()) "  🖼" else ""), color = Color(0xFF9E95AC), fontSize = 10.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        TextButton(modifier = minH, enabled = canAct && !s.locked, contentPadding = pad, onClick = { tap(); onVariant(i) }) { Text("Regenerate", fontSize = 11.sp) }
                        TextButton(modifier = minH, enabled = canAct && !s.locked, contentPadding = pad, onClick = { draft = s.narration; textEdit = i }) { Text("Edit text", fontSize = 11.sp) }
                        TextButton(modifier = minH, enabled = canAct && !s.locked, contentPadding = pad, onClick = { tap(); onAsset(i) }) { Text("Replace asset", fontSize = 11.sp) }
                        TextButton(modifier = minH, enabled = canAct && !s.locked, contentPadding = pad, onClick = { tap(); onStyle(listOf(i)) }) { Text("Change style", fontSize = 11.sp) }
                        TextButton(modifier = minH, enabled = canAct, contentPadding = pad, onClick = { tap(); onLock(i) }) { Text(if (s.locked) "Unlock" else "Lock", fontSize = 11.sp) }
                        TextButton(modifier = minH, enabled = canAct && !s.locked, contentPadding = pad, onClick = { tap(); asking = !asking }) { Text(if (asking) "Close edit" else "Ask AI…", fontSize = 11.sp) }
                    }
                    if (asking && canAct && !s.locked) {
                        OutlinedTextField(instruction, { instruction = it }, Modifier.fillMaxWidth(), maxLines = 3,
                            placeholder = { Text("What should change in this scene?", fontSize = 11.sp) })
                        TextButton(enabled = instruction.isNotBlank(), onClick = { tap(); onBulk(listOf(i), instruction.trim()); instruction = ""; asking = false }) {
                            Text("Revise scene ${i + 1}", color = Cyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    textEdit?.let { idx ->
        AlertDialog(onDismissRequest = { textEdit = null }, title = { Text("Scene ${idx + 1} narration") },
            text = { OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), maxLines = 6, label = { Text("Only this scene is re-voiced") }) },
            confirmButton = { TextButton(enabled = draft.trim().length >= 6, onClick = { tap(); onText(idx, draft); textEdit = null }) { Text("Re-voice scene") } },
            dismissButton = { TextButton(onClick = { textEdit = null }) { Text("Cancel") } })
    }
}

/** Only validated drafts emitted by the real planning pipeline appear here. */
@Composable
private fun LiveStoryboard(scenes: List<com.example.studio.director.Scene>, aspect: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Glass, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LIVE STORYBOARD · ${scenes.size} validated draft${if (scenes.size == 1) "" else "s"}", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Planning is in progress. Drafts may change before the final scene plan; editing unlocks when production is ready.", color = Color(0xFF9E95AC), fontSize = 10.sp)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(count = scenes.size, key = { scenes[it].id }) { index ->
                    val scene = scenes[index]
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF211B2E), modifier = Modifier.widthIn(min = 140.dp, max = 150.dp)) {
                        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (scene.graph != null) SceneThumb(scene, aspect)
                            else Text("No renderable graph yet", color = Color(0xFF9E95AC), fontSize = 10.sp)
                            Text("${index + 1}. ${scene.visualObjective.take(45)}", color = Color.White, fontSize = 10.sp, maxLines = 2)
                            Text("${scene.startMs / 1000}s–${scene.endMs / 1000}s", color = Color(0xFF9E95AC), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

/** 48 dp minimum touch target for the small text buttons on scene cards. */
private fun Modifier.androidx_minTouch(): Modifier = this.then(Modifier.heightIn(min = 48.dp))

@Composable
private fun SceneThumb(s: com.example.studio.director.Scene, aspect: String) {
    val bmp = remember(s.id, s.graph, s.durationMs, aspect) {
        val (aw, ah) = com.example.studio.composer.LayoutEngine.parseAspect(aspect)
        val w = 270; val h = (270f * ah / aw).toInt().coerceIn(150, 480)
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b); c.drawColor(android.graphics.Color.BLACK)
        s.graph?.let { g -> runCatching { com.example.studio.director.SceneRenderer.draw(c, w, h, g, minOf(s.durationMs * 6 / 10, 1800L), s.durationMs, 1f) } }
        b.asImageBitmap()
    }
    androidx.compose.foundation.Image(bitmap = bmp, contentDescription = "Scene preview", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
}
