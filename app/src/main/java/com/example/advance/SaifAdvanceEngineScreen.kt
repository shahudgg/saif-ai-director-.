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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * SAIF ADVANCE EDITING ENGINE — real home for the engine that ships inside the APK.
 *
 * Shows the five parts with their true file counts, publishes the whole tree to Downloads so Termux or a
 * browser can use it, exposes the exact command for every step, and reads the bundled spec documents
 * inside the app. Credits are SAIF AI throughout.
 */
@Composable
fun SaifAdvanceEngineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { SaifEngine.assetFiles(context) }
    val sizeMb = remember {
        var b = 0L
        for (f in files) b += runCatching { context.assets.open(f).use { it.available().toLong() } }.getOrDefault(0L)
        "%.1f".format(b / 1048576.0)
    }
    var published by remember { mutableStateOf(SaifEngine.publishedPath(context)) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf<Pair<String, String>?>(null) }
    val termuxOk = remember { TermuxBridge.isInstalled(context) }

    fun note(t: String) { log = t }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("SAIF ADVANCE EDITING ENGINE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${files.size} files · $sizeMb MB · app ke andar bundled", color = Muted, fontSize = 11.sp)
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
                            Text("1 · Engine phone pe nikaalo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Poora engine ek zip mein pack hokar Download/${SaifEngine.FOLDER}/ mein rakha jayega — " +
                                    "Termux usi ko unzip karega, browser usi se extension load karega.",
                                color = Muted, fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (busy) return@Button
                                        busy = true
                                        note("Engine pack ho raha hai…")
                                        scope.launch {
                                            val r = withContext(Dispatchers.IO) { SaifEngine.publish(context) }
                                            busy = false
                                            r.fold(
                                                { path ->
                                                    published = path
                                                    note("✅ Engine ready: $path (${files.size} files)")
                                                    Toast.makeText(context, "Engine ready: $path", Toast.LENGTH_LONG).show()
                                                },
                                                { e -> note("❌ ${e.message}") }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) { Text(if (published == null) "Engine pack karo" else "Dobara pack karo", fontSize = 12.sp) }
                                OutlinedButton(
                                    onClick = {
                                        if (termuxOk) { TermuxBridge.openTermux(context); note("Termux khula.") }
                                        else TermuxBridge.openFdroid(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) { Text(if (termuxOk) "Termux kholo" else "Termux install karo", fontSize = 12.sp) }
                            }
                            published?.let { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(p, color = Color(0xFFBFEFD2), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Text("Termux: unzip $ZIP_TERMUX && cd ~/${SaifEngine.TERMUX_DIR}",
                                    color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp), color = Color(0xFF0E0B16),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    TermuxBridge.copy(context, "cd ~ && unzip -o $ZIP_TERMUX -d ~/${SaifEngine.TERMUX_DIR} && ls ~/${SaifEngine.TERMUX_DIR}")
                                    Toast.makeText(context, "Unzip command copy ho gaya", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    "cd ~ && unzip -o $ZIP_TERMUX -d ~/${SaifEngine.TERMUX_DIR} && ls ~/${SaifEngine.TERMUX_DIR}\n(tap = copy)",
                                    color = Color(0xFFCFE9FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            if (!termuxOk) Text(
                                "Termux F-Droid se install karo; ek baar `termux-setup-storage` chalao taaki Download folder dikhe.",
                                color = Color(0xFFF59E0B), fontSize = 11.sp
                            )
                            if (log.isNotBlank()) Text(log, color = Aqua, fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Text("2 · Engine ke 5 hisse", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                }

                items(SaifEngine.PARTS, key = { it.id }) { part ->
                    PartCard(
                        part = part,
                        fileCount = SaifEngine.countIn(context, part.folder),
                        onRun = { cmd ->
                            TermuxBridge.runInTermux(context, cmd).fold(
                                { note("▶ Termux mein bheja: ${part.title}") },
                                { e -> note("❌ ${e.message}") }
                            )
                        },
                        onCopy = { cmd ->
                            TermuxBridge.copy(context, cmd)
                            Toast.makeText(context, "Command copy ho gaya", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item {
                    Text("3 · Docs (app ke andar padho)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                }
                items(SaifEngine.DOCS, key = { it.second }) { (title, path) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp), color = Card,
                        modifier = Modifier.fillMaxWidth().clickable { doc = title to path }
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("kholo ›", color = Aqua, fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF120E1C), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Credits", color = Aqua, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("SAIF AI — engine, bridge, extension aur docs sab isi app ke andar ship hote hain.",
                                color = Muted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        doc?.let { (title, path) ->
            val text = remember(path) { SaifEngine.readText(context, path) }
            Box(Modifier.fillMaxSize().background(Bg)) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { doc = null }) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            TermuxBridge.copy(context, text)
                            Toast.makeText(context, "Doc copy ho gaya", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Filled.ContentCopy, "Copy", tint = Aqua) }
                    }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                        Text(text, color = Color(0xFFE6E1F0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

private const val ZIP_TERMUX = "~/storage/shared/Download/SAIF_AI_Engine/SAIF_AI_Engine.zip"

@Composable
private fun PartCard(
    part: SaifEngine.Part,
    fileCount: Int,
    onRun: (String) -> Unit,
    onCopy: (String) -> Unit
) {
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
                ) { Text("${fileCount}", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(part.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(part.what, color = Muted, fontSize = 11.sp)
                }
                Text(if (open) "−" else "+", color = Aqua, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                for ((label, cmd) in part.commands) {
                    Text(label, color = Color(0xFFB8A2FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF0E0B16), modifier = Modifier.fillMaxWidth()) {
                        Text(cmd, color = Color(0xFFCFE9FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(9.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        Button(
                            onClick = { onRun(cmd) },
                            colors = ButtonDefaults.buttonColors(containerColor = Violet),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onCopy(cmd) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, tint = Aqua, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
