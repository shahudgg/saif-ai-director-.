package com.example.advance

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.termux.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0912)
private val Card = Color(0xFF171223)
private val Violet = Color(0xFF7C3AED)
private val Aqua = Color(0xFF22D3EE)
private val Muted = Color(0xFF9E95AC)

/**
 * SAIF AI ADVANCE EDITING — a real home for the complete production engine that ships inside the APK.
 *
 * It does not pretend the phone is a Linux box: it packs the bundled engine, publishes it where Termux can
 * read it, and gives one-tap access to the engine's own 9-step protocol, with the exact command next to every
 * step so it can also be copied by hand. Every result shown here is the real result of a real action.
 */
@Composable
fun SaifAdvanceEditingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { AdvanceEngine.assetFiles(context) }
    val sizeMb = remember {
        var b = 0L
        for (f in files) b += runCatching { context.assets.open(f).use { it.available().toLong() } }.getOrDefault(0L)
        "%.1f".format(b / 1048576.0)
    }
    var published by remember { mutableStateOf(AdvanceEngine.installedPath(context)) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    val termuxOk = remember { TermuxBridge.isInstalled(context) }

    fun note(text: String) { log = text }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("SAIF AI ADVANCE EDITING", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Advance Studio engine · ${files.size} files · $sizeMb MB (app ke andar)", color = Muted, fontSize = 11.sp)
                }
                Box(
                    Modifier.size(34.dp).background(Brush.linearGradient(listOf(Violet, Aqua)), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Bolt, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Card, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1 · Engine phone pe likho", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "App ke andar jo engine code hai use ek zip mein pack karke Download/" +
                                    AdvanceEngine.FOLDER + "/ mein rakha jayega. Termux usi ko unzip karega.",
                                color = Muted, fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (busy) return@Button
                                        busy = true
                                        note("Engine pack ho raha hai…")
                                        scope.launch {
                                            val r = withContext(Dispatchers.IO) { AdvanceEngine.publish(context) }
                                            busy = false
                                            r.fold(
                                                { path ->
                                                    published = path
                                                    AdvanceEngine.clearCache(context)
                                                    note("✅ Engine ready: $path (${files.size} files)")
                                                    Toast.makeText(context, "Engine ready: $path", Toast.LENGTH_LONG).show()
                                                },
                                                { e -> note("❌ ${e.message}") }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text(if (published == null) "Engine install karo" else "Dobara pack karo", fontSize = 12.sp) }

                                OutlinedButton(
                                    onClick = {
                                        if (termuxOk) { TermuxBridge.openTermux(context); note("Termux khula.") }
                                        else TermuxBridge.openFdroid(context)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text(if (termuxOk) "Termux kholo" else "Termux install karo", fontSize = 12.sp) }
                            }
                            published?.let { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(p, color = Color(0xFFBFEFD2), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Text("Termux path: ${AdvanceEngine.TERMUX_ZIP}", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            if (!termuxOk) Text(
                                "Note: engine Python + ffmpeg chalata hai, isliye commands Termux mein chalte hain. " +
                                    "Termux F-Droid se install karo, phir ek baar `termux-setup-storage` chalao.",
                                color = Color(0xFFF59E0B), fontSize = 11.sp
                            )
                            if (log.isNotBlank()) Text(log, color = Aqua, fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Text(
                        "2 · Engine ka 9-step protocol", color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                    )
                }

                items(AdvanceEngine.STEPS, key = { it.n }) { step ->
                    StepCard(step = step, onRun = { cmd ->
                        TermuxBridge.runInTermux(context, cmd).fold(
                            { note("▶ Step ${step.n + 1} Termux mein bheja: ${step.title}") },
                            { e -> note("❌ ${e.message}") }
                        )
                    }, onCopy = {
                        TermuxBridge.copy(context, it)
                        Toast.makeText(context, "Command copy ho gaya", Toast.LENGTH_SHORT).show()
                    })
                }

                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF120E1C), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Engine ke andar kya hai", color = Aqua, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "core/ (render, align, cutout, key, mix, elements, trends, phonetic_align, " +
                                    "parallel_generator, flow_bridge, dedupe_guard, direct_sync, cartesia_tts) · " +
                                    "skills/ (bang-motion, embedded-captions, vox-doc, vox-doc-sound, stickman-hybrid) · " +
                                    "fonts/ (English + Hindi + Nepali) · elements/ cutouts · styles/ · poore docs.",
                                color = Muted, fontSize = 11.sp
                            )
                            Text("Credits: SAIF AI", color = Color(0xFFB8A2FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: AdvanceEngine.Step, onRun: (String) -> Unit, onCopy: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp), color = Card,
        modifier = Modifier.fillMaxWidth().clickable { open = !open }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).background(Violet.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("${step.n + 1}", color = Aqua, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(step.what, color = Muted, fontSize = 11.sp)
                }
                Text(if (open) "−" else "+", color = Aqua, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF0E0B16), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        step.command, color = Color(0xFFCFE9FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRun(step.command) },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 42.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Termux mein chalao", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onCopy(step.command) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 42.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, tint = Aqua, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
