package com.example.flow.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flow.FLOW_HOME
import com.example.flow.FX_HOME
import com.example.flow.FlowAutopilot
import com.example.flow.FlowBridgeBus
import com.example.flow.FlowDownloader
import com.example.flow.FlowCaptured
import com.example.flow.FlowLibrary
import com.example.flow.FlowPhase
import com.example.flow.FlowPlan
import com.example.flow.FlowPlanStore
import com.example.flow.FlowReview
import com.example.flow.FlowRoles
import com.example.flow.FlowRunState
import com.example.flow.FlowSettings
import com.example.flow.FlowSlot
import com.example.flow.FlowSlotStatus
import com.example.flow.GEMINI_HOME
import com.example.flow.IMAGE_FX_HOME
import com.example.flow.WHISK_HOME
import com.example.llm.LlmRole
import com.example.studio.ai.AiClient
import com.example.studio.ai.AiProfile
import com.example.termux.TermuxBridge
import com.example.ui.theme.SaifAiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Round 14 — FULL-SCREEN SAIF FLOW BROWSER.
 *
 * This is the screen the owner gets when tapping the browser icon: a real, full-window browser (so
 * scrolling and pinch-zoom behave like a normal browser), Flow / FX / Whisk / Gemini shortcuts, a
 * login button that works any time, the prompt plan with live progress, and the Termux command panel.
 */
class FlowBrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: ""
        val autostart = intent.getBooleanExtra(EXTRA_AUTOSTART, false)
        val autofinish = intent.getBooleanExtra(EXTRA_AUTOFINISH, false)
        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
        setContent {
            SaifAiTheme(isLightMode = false) {
                FlowBrowserScreen(projectId = projectId, autostart = autostart, autofinish = autofinish, seedTopic = topic,
                    onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PROJECT_ID = "flow_project_id"
        const val EXTRA_AUTOSTART = "flow_autostart"
        /** Round 16 — hand the finished plan back to the director AND close the browser afterwards. */
        const val EXTRA_AUTOFINISH = "flow_autofinish"
        const val EXTRA_TOPIC = "flow_topic"

        /** Opens the browser full screen. Login is never required up front — it can happen any time. */
        fun start(context: Context, projectId: String = "", autostart: Boolean = false, topic: String = "",
                  autofinish: Boolean = false) {
            val i = Intent(context, FlowBrowserActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_AUTOSTART, autostart)
                putExtra(EXTRA_TOPIC, topic)
                putExtra(EXTRA_AUTOFINISH, autofinish)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }
}

@Composable
private fun FlowBrowserScreen(projectId: String, autostart: Boolean, autofinish: Boolean, seedTopic: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { FlowWebSession(ctx.applicationContext) }
    val webView = remember { session.create() }
    val info by session.pageInfo.collectAsStateWithLifecycle()
    val log by session.log.collectAsStateWithLifecycle()
    val progress by session.progress.collectAsStateWithLifecycle()
    val libCount by session.libraryCount.collectAsStateWithLifecycle()
    val zoom by session.zoomPercent.collectAsStateWithLifecycle()

    var plan by remember { mutableStateOf(FlowPlanStore.load(ctx, projectId) ?: FlowPlanStore.latest(ctx)) }
    var run by remember { mutableStateOf(FlowRunState()) }
    var autopilot by remember { mutableStateOf<FlowAutopilot?>(null) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var address by remember { mutableStateOf(info.url.ifBlank { FlowSettings.lastUrl(ctx) }) }
    var panelOpen by remember { mutableStateOf(true) }
    var quickTopic by remember { mutableStateOf(seedTopic) }
    var quickBusy by remember { mutableStateOf(false) }
    var showTermux by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(FlowSettings.desktopMode(ctx)) }
    var aiVerify by remember { mutableStateOf(FlowSettings.aiVerify(ctx)) }
    var delayMs by remember { mutableStateOf(FlowSettings.delayMs(ctx)) }

    // Round 15 SAFETY NET: if Google refuses WebView sign-in entirely, the owner can generate the pictures
    // in Chrome and import them here — the rest of the pipeline (verify → plan → timeline) is identical.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val rec = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    FlowDownloader.saveBytes(
                        ctx, bytes, -1, "", FlowRoles.BACKGROUND,
                        "imported_" + System.currentTimeMillis(), uri.toString(), "image", strict = false
                    )
                }.getOrNull()
            }
            com.example.power.SaifToolkit.Counters.bump(ctx, "flow_images")
            Toast.makeText(ctx, if (rec != null) "Image library mein add ho gayi" else "Import fail (file padhi nahi ja saki)", Toast.LENGTH_SHORT).show()
        }
    }

    fun persist(p: FlowPlan) {
        plan = p
        FlowPlanStore.save(ctx, p)
    }

    fun startAutopilot() {
        val p = plan ?: run {
            Toast.makeText(ctx, "Pehle prompts banao (neeche panel) — phir Start dabao.", Toast.LENGTH_LONG).show()
            return
        }
        if (run.running) return
        val ap = FlowAutopilot(
            context = ctx.applicationContext,
            driver = session,
            captures = session.captures,
            plan = p,
            onState = { run = it },
            onPlan = { persist(it) },
            aiVerify = { slot, review, path -> aiVerdict(ctx, slot, review, path) },
            delayMs = FlowSettings.delayMs(ctx)
        )
        autopilot = ap
        runJob = scope.launch {
            val finished = ap.run()
            persist(finished)
            // Round 16: when the agent started this run, the director continues by itself (place + QC + timeline).
            FlowBridgeBus.publishPlan(finished, autofinish)
            FlowBridgeBus.publishLibrary(FlowLibrary.count(ctx))
            if (autofinish && finished.phase == FlowPhase.DONE) {
                Toast.makeText(ctx, "Flow done — SAIF layers lagakar timeline bana raha hai", Toast.LENGTH_LONG).show()
                delay(2600L)
                onClose()
            }
        }
    }

    fun stopAutopilot() {
        autopilot?.stop()
        runJob?.cancel()
        runJob = null
        run = run.copy(phase = FlowPhase.STOPPED, message = "Aapne rok diya — jo pictures ban chuki hain wo safe hain.")
    }

    DisposableEffect(Unit) {
        onDispose {
            autopilot?.stop()
            runJob?.cancel()
            session.destroy()
        }
    }

    LaunchedEffect(Unit) {
        if (!autostart) return@LaunchedEffect
        // The plan is normally already saved, but a slow disk may need a moment — wait instead of doing nothing.
        var waited = 0
        while (plan == null && waited < 12) { delay(500); waited++; plan = FlowPlanStore.load(ctx, projectId) ?: FlowPlanStore.latest(ctx) }
        if (plan != null) {
            delay(1500)
            startAutopilot()
        } else {
            Toast.makeText(ctx, "Flow plan nahi mila — prompts dobara banao (panel neeche)", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = true) {
        if (showTermux) showTermux = false
        else if (webView.canGoBack()) webView.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF07060B)).statusBarsPadding().navigationBarsPadding().imePadding()) {

        // ---------------- top bar ----------------
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("SAIF FLOW BROWSER", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                val account = when {
                    info.hasComposer -> "Logged in · composer ready"
                    info.needsLogin -> "Login chahiye — kabhi bhi kar sakte ho"
                    else -> info.url.take(48)
                }
                Text(account, color = if (info.hasComposer) Color(0xFF34D399) else Color(0xFFFFB86B), fontSize = 9.sp, maxLines = 1)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF141021), modifier = Modifier.clickable { session.openLogin() }) {
                Text("Login", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { showMenu = !showMenu }) { Text("⋮", color = Color.White, fontSize = 16.sp) }
        }

        if (progress in 1..99) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF1B1630))) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(Color(0xFF00E5FF)))
            }
        }

        if (showMenu) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF141021)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            desktopMode = !desktopMode; session.setDesktopMode(desktopMode)
                        }) { Text(if (desktopMode) "Desktop mode: ON" else "Desktop mode: OFF", fontSize = 10.sp) }
                        OutlinedButton(onClick = { session.logout(); showMenu = false }) { Text("Logout", fontSize = 10.sp) }
                    }
                    Text("Login blocked? Desktop mode ON karo — Google WebView ko mobile-UA pe block karta hai. Ya 'Login' se account chooser kholo.",
                        color = Color(0xFF9E95AC), fontSize = 9.sp, lineHeight = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            aiVerify = !aiVerify; FlowSettings.setAiVerify(ctx, aiVerify)
                        }) { Text(if (aiVerify) "AI verify: ON" else "AI verify: OFF", fontSize = 10.sp) }
                        OutlinedButton(onClick = {
                            delayMs = if (delayMs < 12000L) 12000L else if (delayMs < 20000L) 20000L else FlowSettings.MIN_DELAY_MS
                            FlowSettings.setDelayMs(ctx, delayMs)
                        }) { Text("Gap: " + (delayMs / 1000) + "s", fontSize = 10.sp) }
                        OutlinedButton(onClick = { session.importPageNow { n -> Toast.makeText(ctx, "$n media mile — library check karo", Toast.LENGTH_SHORT).show() } }) {
                            Text("Import page", fontSize = 10.sp)
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val ok = runCatching { session.openProject() }.getOrDefault(false)
                                Toast.makeText(ctx, if (ok) "Project khol diya" else "Project button nahi mila — page pe khud khol lo", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Project kholo", fontSize = 10.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FLOW_HOME)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            showMenu = false
                        }) { Text("Chrome mein kholo", fontSize = 10.sp) }
                        OutlinedButton(onClick = { showMenu = false; importLauncher.launch("image/*") }) {
                            Text("Gallery se import", fontSize = 10.sp)
                        }
                    }
                    Text("Flow WebView mein login na ho to Chrome se images banao aur yahan import karo — verify/plan/timeline sab same chalega.",
                        color = Color(0xFF9E95AC), fontSize = 9.sp, lineHeight = 12.sp)
                    TextButton(onClick = { showMenu = false; showTermux = true }) { Text("🖥 Termux / commands", fontSize = 11.sp) }
                    TextButton(onClick = { showMenu = false; session.reload() }) { Text("Reload page", fontSize = 11.sp) }
                }
            }
        }

        // ---------------- address + shortcuts ----------------
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address, onValueChange = { address = it },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 56.dp),
                singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                placeholder = { Text("flow.google.com", fontSize = 11.sp) },
                trailingIcon = { TextButton(onClick = { session.go(address) }) { Text("Go", fontSize = 11.sp, color = Color(0xFF00E5FF)) } }
            )
            IconButton(onClick = { session.reload() }) { Icon(Icons.Default.Refresh, "Reload", tint = Color(0xFF9E95AC)) }
            IconButton(onClick = { session.openLogin() }) { Icon(Icons.Default.KeyboardArrowUp, "Login", tint = Color(0xFF34D399)) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Flow" to FLOW_HOME, "FX" to FX_HOME, "Whisk" to WHISK_HOME, "ImageFX" to IMAGE_FX_HOME, "Gemini" to GEMINI_HOME).forEach { pair ->
                Surface(shape = RoundedCornerShape(7.dp), color = Color(0xFF141021), modifier = Modifier.clickable {
                    address = pair.second; session.go(pair.second)
                }) { Text(pair.first, color = Color(0xFFD9C6FF), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { session.scrollByPage(false) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Scroll up", tint = Color(0xFF9E95AC))
            }
            IconButton(onClick = { session.scrollByPage(true) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Scroll down", tint = Color(0xFF9E95AC))
            }
            IconButton(onClick = { session.zoom(1f / 1.2f) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Remove, "Zoom out", tint = Color(0xFF9E95AC))
            }
            Text("$zoom%", color = Color(0xFF9E95AC), fontSize = 9.sp)
            IconButton(onClick = { session.zoom(1.2f) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Add, "Zoom in", tint = Color(0xFF9E95AC))
            }
            TextButton(onClick = { session.resetZoom() }) { Text("1x", fontSize = 9.sp) }
        }

        // ---------------- web view ----------------
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }

        // ---------------- status + panel ----------------
        Text(log, color = Color(0xFF9EE6B8), fontSize = 9.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        if (run.message.isNotBlank()) {
            Text(run.message, color = if (run.phase == FlowPhase.FAILED) Color(0xFFFF6B6B) else Color(0xFF00E5FF),
                fontSize = 9.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF141021), modifier = Modifier.clickable { panelOpen = !panelOpen }) {
                Text((if (panelOpen) "▾ " else "▸ ") + "Prompt plan" + (plan?.let { " ${it.finishedCount}/${it.slots.size}" } ?: " (khali)") + " · library $libCount",
                    color = Color(0xFFD9C6FF), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            if (run.running) OutlinedButton(onClick = { stopAutopilot() }) { Text("Stop", fontSize = 10.sp) }
            else OutlinedButton(onClick = { startAutopilot() }, enabled = plan != null) { Text("▶ Start", fontSize = 10.sp) }
        }

        val p = plan
        if (panelOpen) {
            if (p == null) {
                Surface(Modifier.fillMaxWidth().padding(6.dp).heightIn(max = 200.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF141021)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Koi prompt plan nahi hai", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("AI Director mein script approve karke '🖼 Flow images (auto)' dabao — scene-by-scene prompts ban jayenge. " +
                            "Ya yahan topic se quick prompts banao:", color = Color(0xFF9E95AC), fontSize = 9.sp, lineHeight = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(quickTopic, { quickTopic = it }, Modifier.weight(1f), singleLine = true,
                                placeholder = { Text("topic", fontSize = 11.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White))
                            TextButton(enabled = !quickBusy && quickTopic.isNotBlank(), onClick = {
                                quickBusy = true
                                scope.launch {
                                    val services = com.example.studio.director.DirectorServices(ctx.applicationContext)
                                    val r = services.flowImagePrompts(quickTopic.trim(), 10, "9:16", "cinematic explainer")
                                    val prompts = r.getOrNull()
                                    if (prompts.isNullOrEmpty()) {
                                        Toast.makeText(ctx, "Prompt error: " + (r.exceptionOrNull()?.message ?: "unknown"), Toast.LENGTH_LONG).show()
                                    } else {
                                        val slots = prompts.mapIndexed { i, text ->
                                            FlowSlot(index = i, sceneId = "quick_$i",
                                                role = if (i == 0) FlowRoles.COVER else FlowRoles.BACKGROUND,
                                                prompt = text, aspect = "9:16", sceneMs = 4000L)
                                        }
                                        persist(FlowPlan.fromSlotList(projectId, quickTopic.trim(), "9:16", "cinematic explainer", slots,
                                            listOf("${slots.size} quick prompts from the topic (no scene plan yet).")))
                                    }
                                    quickBusy = false
                                }
                            }) { Text(if (quickBusy) "…" else "Banao", fontSize = 11.sp) }
                        }
                    }
                }
            } else {
                FlowPromptPanel(
                    plan = p,
                    run = run,
                    onStart = { startAutopilot() },
                    onStop = { stopAutopilot() },
                    onRegenerate = { slot ->
                        val updated = p.withSlot(slot.copy(status = FlowSlotStatus.PENDING, error = "", tries = 0))
                        persist(updated)
                        startAutopilot()
                    },
                    onAttach = {
                        FlowBridgeBus.publishPlan(p, true)
                        Toast.makeText(ctx, "Plan timeline pe bhej diya (AI Director screen wapas kholo).", Toast.LENGTH_LONG).show()
                    },
                    onClear = { FlowPlanStore.clear(ctx, projectId); plan = null }
                )
            }
        }
    }

    if (showTermux) TermuxPanel(onDismiss = { showTermux = false })
}

@Composable
private fun FlowPromptPanel(
    plan: FlowPlan,
    run: FlowRunState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRegenerate: (FlowSlot) -> Unit,
    onAttach: () -> Unit,
    onClear: () -> Unit
) {
    val total = plan.slots.size
    val done = plan.finishedCount
    Surface(Modifier.fillMaxWidth().padding(6.dp).fillMaxHeight(0.42f), shape = RoundedCornerShape(12.dp), color = Color(0xFF100C1A)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${done}/${total} pictures ready", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("8s+ gap · " + (run.phase.name.lowercase()), color = Color(0xFF9E95AC), fontSize = 9.sp)
            }
            if (total > 0) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF1E1830))) {
                    Box(Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f)).height(5.dp).background(Color(0xFF00E5FF)))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (run.running) OutlinedButton(onClick = onStop) { Text("Stop", fontSize = 10.sp) }
                else OutlinedButton(onClick = onStart) { Text("▶ Generate all", fontSize = 10.sp) }
                OutlinedButton(onClick = onAttach, enabled = done > 0) { Text("Add to timeline", fontSize = 10.sp) }
                TextButton(onClick = onClear) { Text("Clear", fontSize = 10.sp, color = Color(0xFF9E95AC)) }
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(plan.slots, key = { it.index }) { slot ->
                    val statusColor = when (slot.status) {
                        FlowSlotStatus.VERIFIED -> Color(0xFF34D399)
                        FlowSlotStatus.DOWNLOADED -> Color(0xFFFBBF24)
                        FlowSlotStatus.RUNNING -> Color(0xFF00E5FF)
                        FlowSlotStatus.FAILED -> Color(0xFFFF6B6B)
                        FlowSlotStatus.SKIPPED -> Color(0xFF9E95AC)
                        else -> Color(0xFF6B6480)
                    }
                    Column(Modifier.fillMaxWidth().background(Color(0xFF171226), RoundedCornerShape(8.dp)).padding(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (slot.status == FlowSlotStatus.VERIFIED) "✓" else "${slot.index + 1}", color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            val layerTag = when (slot.layer) {
                                "bg" -> "BG"
                                "overlay" -> "OVERLAY"
                                "asset" -> "ASSET"
                                "cover" -> "COVER"
                                else -> slot.role
                            }
                            val cutTag = if (slot.cutPath.isNotBlank()) " · cut-out ✓" else ""
                            Text(layerTag + " z" + slot.z + " · " + slot.sceneId + cutTag, color = Color(0xFFB9A8E8), fontSize = 9.sp, maxLines = 1)
                            Spacer(Modifier.weight(1f))
                            if (slot.status == FlowSlotStatus.FAILED) TextButton(onClick = { onRegenerate(slot) }) { Text("retry", fontSize = 9.sp) }
                        }
                        Text(slot.prompt, color = Color(0xFFCCC3D9), fontSize = 9.sp, maxLines = 2, lineHeight = 12.sp)
                        slot.review?.verdict?.takeIf { it.isNotBlank() }?.let { verdict ->
                            Text(verdict, color = if (slot.review?.ok == true) Color(0xFF86EFAC) else Color(0xFFFFB86B), fontSize = 8.sp, maxLines = 2)
                        }
                        slot.review?.aiVerdict?.takeIf { it.isNotBlank() }?.let { verdict ->
                            Text("AI: " + verdict, color = Color(0xFF93C5FD), fontSize = 8.sp, maxLines = 2)
                        }
                        slot.error.takeIf { it.isNotBlank() }?.let { err ->
                            Text(err, color = Color(0xFFFF8F8F), fontSize = 8.sp, maxLines = 2)
                        }
                    }
                }
                if (plan.transitions.isNotEmpty()) {
                    item {
                        Text("Transitions: " + plan.transitions.joinToString(" · ") { it.type },
                            color = Color(0xFF00E5FF), fontSize = 8.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun TermuxPanel(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf(TermuxBridge.PRESETS.first().command) }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val installed = remember { TermuxBridge.isInstalled(ctx) }

    Surface(Modifier.fillMaxWidth().padding(8.dp).heightIn(max = 460.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFF141021)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🖥 TERMUX / COMMANDS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            Text(if (installed) "Termux mila — commands Termux mein chalenge (allow-external-apps=true chahiye)."
                else "Termux install nahi hai. App ke apne shell mein commands chal sakte hain, ya F-Droid se Termux install karo.",
                color = if (installed) Color(0xFF34D399) else Color(0xFFFFB86B), fontSize = 9.sp, lineHeight = 12.sp)
            LazyColumn(Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(TermuxBridge.PRESETS) { preset ->
                    Text("▸ " + preset.label + " — " + preset.note, color = Color(0xFFB9A8E8), fontSize = 9.sp,
                        modifier = Modifier.clickable { command = preset.command })
                }
            }
            OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), maxLines = 3,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = Color.White))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        output = if (installed) {
                            TermuxBridge.runInTermux(ctx, command).fold(
                                { "Termux ko command bhej di. Output Termux app mein dekho." },
                                { it.message ?: "Termux error" })
                        } else {
                            withContext(Dispatchers.IO) {
                                TermuxBridge.runLocal(ctx, command).fold({ it }, { "Shell error: " + (it.message ?: "") })
                            }
                        }
                        busy = false
                    }
                }) { Text(if (busy) "Running…" else if (installed) "Run in Termux" else "Run in app shell", fontSize = 10.sp) }
                OutlinedButton(onClick = {
                    busy = true
                    scope.launch {
                        output = withContext(Dispatchers.IO) {
                            TermuxBridge.runLocal(ctx, command).fold({ it }, { "Shell error: " + (it.message ?: "") })
                        }
                        busy = false
                    }
                }) { Text("App shell", fontSize = 10.sp) }
                TextButton(onClick = { TermuxBridge.copy(ctx, command); Toast.makeText(ctx, "Command copy ho gaya", Toast.LENGTH_SHORT).show() }) {
                    Text("Copy", fontSize = 10.sp)
                }
                TextButton(onClick = { TermuxBridge.openTermux(ctx) }) { Text("Open Termux", fontSize = 10.sp) }
            }
            if (output.isNotBlank()) {
                Text(output.take(1800), color = Color(0xFF9EE6B8), fontSize = 9.sp, lineHeight = 12.sp, maxLines = 14)
            }
            Text("SAIF AI koi command khud nahi chalata — jo aap Run dabao wahi chalta hai.", color = Color(0xFF6B6480), fontSize = 8.sp)
        }
    }
}

/** Optional AI FIT / NOT FIT check, using the owner's own provider key. Failures are silent (local checks stand). */
private suspend fun aiVerdict(context: Context, slot: FlowSlot, review: FlowReview, path: String): String {
    return try {
        val profile = AiProfile.fromSettings(context)
        if (profile.apiKey.isBlank()) return ""
        val b64 = withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@withContext ""
            val src = android.graphics.BitmapFactory.decodeFile(path)
            if (src == null) {
                runCatching {
                    val bos = ByteArrayOutputStream()
                    val bytes = file.readBytes()
                    bos.write(bytes)
                    android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
                }.getOrDefault("")
            } else {
                val maxSide = maxOf(src.width, src.height).coerceAtLeast(1)
                val scale = 640f / maxSide
                val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                    src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
                val bos = ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
                android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
            }
        }
        if (b64.isBlank()) return ""
        val prompt = "This image is planned for a video scene. Scene: \"${slot.sceneId}\". Prompt used: \"${slot.prompt.take(200)}\". " +
            "Measured: ${review.verdict}. Reply in ONE short line: FIT or NOT FIT — max 12 words."
        AiClient(profile).callWithImages(LlmRole.REVIEWER, prompt, b64).getOrNull()?.trim()?.take(120) ?: ""
    } catch (e: Exception) {
        ""
    }
}
