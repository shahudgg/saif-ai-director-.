package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.flow.FlowDownloader
import com.example.flow.FlowLibrary
import com.example.flow.FlowRecord
import com.example.flow.FlowRoles
import com.example.flow.web.FlowBrowserActivity
import com.example.llm.ImageGen
import com.example.studio.ai.AiProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Round 14 — the Flow library facade.
 *
 * The heavy lifting now lives in `com.example.flow` (library, rules, downloader, verifier) and in the
 * full-screen [FlowBrowserActivity]. This object exists so the rest of the app (asset tools, the
 * Telegram library merge, the old fallback placement) keeps one stable API: `items()` returns
 * `TelegramAssetItem`s whose `fileId` is `local:<absolute path>`, i.e. no download is needed later.
 */
object FlowAssetStore {
    fun items(ctx: Context): List<TelegramAssetItem> = FlowLibrary.records(ctx).map { toItem(it) }

    fun count(ctx: Context): Int = FlowLibrary.count(ctx)

    fun records(ctx: Context): List<FlowRecord> = FlowLibrary.records(ctx)

    fun toItem(r: FlowRecord) = TelegramAssetItem(
        assetId = r.id,
        title = r.title,
        type = r.kind,
        extension = r.ext,
        category = if (r.role == FlowRoles.KEYFRAME || r.role == FlowRoles.STICKER) "overlay_graphic" else "b_roll",
        subject = "Generated in Flow AI · " + r.role,
        searchKeywords = r.title.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.map { it.lowercase() }.distinct(),
        tags = listOf("flow-ai", r.role),
        userId = "all",
        fileId = "local:" + r.path,
        messageId = r.createdAt,
        fileSize = r.bytes,
        localCachedPath = r.path
    )

    /** Indexes an already-downloaded file (AI edit / crop / background-remove results). */
    fun add(ctx: Context, path: String, name: String, type: String, ext: String): TelegramAssetItem {
        val f = File(path)
        val hash = FlowLibrary.hashOf(f)
        var width = 0
        var height = 0
        if (type == "image") {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            width = opts.outWidth
            height = opts.outHeight
        }
        val record = FlowLibrary.add(ctx, FlowRecord(
            id = "FLOW_" + (hash.take(12).ifBlank { f.nameWithoutExtension }),
            title = name.ifBlank { f.nameWithoutExtension }, path = f.absolutePath,
            kind = type, ext = ext, bytes = f.length(), width = width, height = height, hash = hash,
            source = "edited-in-app", role = FlowRoles.KEYFRAME
        ))
        return toItem(record)
    }

    fun remove(ctx: Context, fileId: String) {
        val path = fileId.removePrefix("local:")
        val rec = FlowLibrary.byPath(ctx, path)
        if (rec != null) FlowLibrary.remove(ctx, rec.id) else runCatching { File(path).delete() }
    }

    /** Import a URL the owner pasted (no session cookies needed). */
    suspend fun importUrl(ctx: Context, url: String, nameHint: String = ""): TelegramAssetItem? = withContext(Dispatchers.IO) {
        val rec = FlowDownloader.download(
            ctx, url, cookie = "", promptIndex = -1, sceneId = "", role = FlowRoles.BACKGROUND,
            title = nameHint.ifBlank { "pasted" }, strict = false
        )
        rec?.let { toItem(it) }
    }
}

/**
 * AI asset tools over the Flow library: crop, AI edit, glow, background-remove, delete, paste a URL.
 * All AI work uses the owner's own provider key from Settings.
 */
@Composable
fun FlowAssetToolsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(FlowAssetStore.items(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<TelegramAssetItem?>(null) }
    var editPrompt by remember { mutableStateOf("") }
    var cropTarget by remember { mutableStateOf<TelegramAssetItem?>(null) }
    var importUrl by remember { mutableStateOf("") }

    fun refresh() { items = FlowAssetStore.items(ctx) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 620.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF17131F)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SAIF AI ASSET TOOLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Flow browser se aayi images: crop, AI-edit, glow, background-remove — sab aapki API key se. Har file library ka hissa hai aur video mein lag sakti hai.",
                    color = Color(0xFF9E95AC), fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(importUrl, { importUrl = it }, Modifier.weight(1f), singleLine = true, label = { Text("Paste image URL") })
                    TextButton(enabled = importUrl.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch {
                            val imported = FlowAssetStore.importUrl(ctx, importUrl.trim(), "pasted")
                            note = if (imported != null) "Imported “${imported.title}”" else "URL import fail: " + FlowDownloader.lastError
                            importUrl = ""; busy = false; refresh()
                        }
                    }) { Text("Import") }
                }
                if (note.isNotBlank()) Text(note, color = Color(0xFF9EE6B8), fontSize = 10.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.fileId }) { a ->
                        val path = a.fileId.removePrefix("local:")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (a.type == "image") AsyncImage(model = File(path), contentDescription = a.title, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                            else Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF211B2E)), contentAlignment = Alignment.Center) { Text("🎬", fontSize = 18.sp) }
                            Column(Modifier.weight(1f)) {
                                Text(a.title.take(40), color = Color.White, fontSize = 12.sp, maxLines = 1)
                                Text("${a.type} • ${(a.fileSize / 1024)} KB • usable in videos", color = Color(0xFF9E95AC), fontSize = 10.sp)
                            }
                            if (a.type == "image") {
                                TextButton(enabled = !busy, onClick = { editTarget = a; editPrompt = "" }) { Text("🪄 AI edit", fontSize = 10.sp) }
                                TextButton(enabled = !busy, onClick = {
                                    editTarget = a
                                    editPrompt = "Add a premium neon glow around the main subject plus a subtle rim light, like an animated key visual. Keep the composition, colours and subject identical. No text, no watermark."
                                }) { Text("✨ Glow", fontSize = 10.sp) }
                                TextButton(enabled = !busy, onClick = { cropTarget = a }) { Text("✂️ Crop", fontSize = 10.sp) }
                            }
                            TextButton(enabled = !busy, onClick = { FlowAssetStore.remove(ctx, a.fileId); refresh() }) { Text("🗑", fontSize = 10.sp) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) Text("Aapki AI key se kaam chal raha hai…", color = Color(0xFFFFB86B), fontSize = 11.sp)
                    TextButton(enabled = !busy && items.any { it.type == "image" }, onClick = {
                        busy = true
                        scope.launch {
                            val prof = AiProfile.fromSettings(ctx)
                            var n = 0
                            for (a in items.filter { it.type == "image" }.take(12)) {
                                val src = withContext(Dispatchers.IO) { runCatching { File(a.fileId.removePrefix("local:")).readBytes() }.getOrNull() } ?: continue
                                val bytes = ImageGen.edit("Remove the background completely. Keep only the main subject with sharp edges on a transparent PNG background. No text, no watermark.",
                                    src, prof.provider, prof.apiKey, prof.activeModel, prof.customBaseUrl).getOrNull()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val dir = FlowDownloader.dir(ctx)
                                    val f = File(dir, "nobg_" + System.currentTimeMillis() + ".png")
                                    f.writeBytes(bytes)
                                    FlowAssetStore.add(ctx, f.absolutePath, a.title + "-nobg", "image", "png")
                                    n++
                                }
                            }
                            note = if (n > 0) "$n image(s) ka background remove karke nayi copies bana di (library mein add)" else "bg-remove fail hua — Settings ka key/provider check karo"
                            busy = false; refresh()
                        }
                    }) { Text("🪄 Sabka bg-remove", fontSize = 10.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    editTarget?.let { a ->
        val path = a.fileId.removePrefix("local:")
        AlertDialog(onDismissRequest = { editTarget = null },
            title = { Text("AI edit: ${a.title.take(30)}") },
            text = {
                OutlinedTextField(editPrompt, { editPrompt = it }, Modifier.fillMaxWidth(), maxLines = 4,
                    placeholder = { Text("e.g. remove the background, keep only the person, transparent PNG • or: make it cyberpunk neon style", fontSize = 12.sp) })
            },
            confirmButton = {
                TextButton(enabled = editPrompt.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val prof = AiProfile.fromSettings(ctx)
                        val src = withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() }
                        val r = if (src != null) ImageGen.edit(editPrompt.trim(), src, prof.provider, prof.apiKey, prof.activeModel, prof.customBaseUrl)
                        else Result.failure<ByteArray>(IllegalStateException("file unreadable"))
                        val bytes = r.getOrNull()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val dir = FlowDownloader.dir(ctx)
                            val f = File(dir, "edited_${System.currentTimeMillis()}.png")
                            f.writeBytes(bytes)
                            FlowAssetStore.add(ctx, f.absolutePath, "${a.title}-edit", "image", "png")
                            note = "Edited image saved — library mein add ho gayi"
                        } else note = "AI edit fail: ${r.exceptionOrNull()?.message ?: "unknown"} — Settings ka provider/key check karo"
                        busy = false; editTarget = null; refresh()
                    }
                }) { Text("Run with my API key") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel") } })
    }

    cropTarget?.let { a ->
        val path = a.fileId.removePrefix("local:")
        val bmp = remember(a.fileId) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        if (bmp != null) {
            Dialog(onDismissRequest = { cropTarget = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF17131F)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Crop: ${a.title.take(30)}", color = Color.White, fontWeight = FontWeight.Bold)
                        ImageCropEditPanel(bitmap = bmp, onDismiss = { cropTarget = null }, onCropConfirmed = { out ->
                            scope.launch(Dispatchers.IO) {
                                val dir = FlowDownloader.dir(ctx)
                                val f = File(dir, "cropped_${System.currentTimeMillis()}.png")
                                java.io.FileOutputStream(f).use { o -> out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o) }
                                FlowAssetStore.add(ctx, f.absolutePath, "${a.title}-crop", "image", "png")
                                withContext(Dispatchers.Main) { note = "Cropped image saved to the library"; cropTarget = null; refresh() }
                            }
                        })
                    }
                }
            }
        } else note = "Crop khola nahi ja saka — file readable nahi hai"
    }
}

/**
 * Old entry points kept for compatibility: they now open the REAL full-screen browser
 * ([FlowBrowserActivity]) instead of the old in-dialog WebView, which is what broke scrolling and zoom.
 */
@Composable
fun FlowAiBrowserDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        FlowBrowserActivity.start(ctx, "", false, "")
        onDismiss()
    }
}

@Composable
fun FlowBatchDialog(topic: String, aspect: String, style: String, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        FlowBrowserActivity.start(ctx, "", true, topic)
        onDismiss()
        onDone(FlowAssetStore.count(ctx))
    }
}
