package com.example.ui.components

import android.app.DownloadManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.llm.ImageGen
import com.example.studio.ai.AiProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Round 10 — SAIF FLOW AI STUDIO.
 *
 * An in-app browser (persistent WebView session, so the owner can LOG IN once) for Flow / FX / Whisk
 * (flow.google.com, labs.google/fx). Images and clips the owner generates there are captured by an
 * injected collector and saved into the app's asset library ([FlowAssetStore]), which the AI Director's
 * asset hunter searches exactly like Telegram assets. A tools dialog can then crop / AI-edit /
 * background-remove / restyle any imported image with the user's own provider key ([ImageGen]).
 */
object FlowAssetStore {
    private const val FILE = "flow_assets.json"
    private const val MAX_BYTES = 60L * 1024 * 1024

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()

    private fun toJson(a: TelegramAssetItem) = JSONObject()
        .put("assetId", a.assetId).put("title", a.title).put("type", a.type).put("ext", a.extension)
        .put("category", a.category).put("subject", a.subject)
        .put("kw", JSONArray(a.searchKeywords)).put("tags", JSONArray(a.tags))
        .put("userId", a.userId).put("fileId", a.fileId).put("messageId", a.messageId).put("size", a.fileSize)

    private fun fromJson(o: JSONObject) = TelegramAssetItem(
        assetId = o.optString("assetId"), title = o.optString("title"), type = o.optString("type"),
        extension = o.optString("ext"), category = o.optString("category"), subject = o.optString("subject"),
        searchKeywords = (o.optJSONArray("kw")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()),
        tags = (o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()),
        userId = o.optString("userId", "all"), fileId = o.optString("fileId"),
        messageId = o.optLong("messageId"), fileSize = o.optLong("size")
    )

    @Synchronized
    fun items(ctx: Context): List<TelegramAssetItem> = runCatching {
        val f = file(ctx); if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { fromJson(it) } }
    }.getOrElse { emptyList() }

    fun count(ctx: Context): Int = items(ctx).size

    /** Indexes an already-downloaded local file. fileId is "local:<abs path>" so downloadCandidate can serve it without any network. */
    @Synchronized
    fun add(ctx: Context, path: String, name: String, type: String, ext: String): TelegramAssetItem {
        val f = File(path)
        val item = TelegramAssetItem(
            assetId = "FLOW_${f.nameWithoutExtension.hashCode().toUInt().toString(16).uppercase()}",
            title = name.ifBlank { f.nameWithoutExtension }, type = type, extension = ext,
            category = if (type == "video") "b_roll" else "overlay_graphic",
            subject = "imported from Flow AI browser", searchKeywords = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.map { it.lowercase() }.distinct(),
            tags = emptyList(), userId = "all", fileId = "local:$path", messageId = 900_000_000L + f.lastModified(),
            fileSize = f.length()
        )
        val list = items(ctx).toMutableList()
        val idx = list.indexOfFirst { it.fileId == item.fileId }
        if (idx >= 0) list[idx] = item else list.add(item)
        file(ctx).writeText(JSONArray().also { a -> list.forEach { a.put(toJson(it)) } }.toString())
        return item
    }

    @Synchronized
    fun remove(ctx: Context, fileId: String) {
        val list = items(ctx).filterNot { it.fileId == fileId }
        file(ctx).writeText(JSONArray().also { a -> list.forEach { a.put(toJson(it)) } }.toString())
        runCatching { File(fileId.removePrefix("local:")).delete() }
    }

    /** Downloads a URL into flow_downloads and indexes it. Returns the stored item or null. */
    suspend fun importUrl(ctx: Context, url: String, nameHint: String = ""): TelegramAssetItem? = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext null
        try {
            val resp = http.newCall(Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                .addHeader("Referer", "https://flow.google.com/").get().build()).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body ?: return@withContext null
            val ct = resp.header("Content-Type") ?: ""
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@withContext null
            val type = when {
                ct.contains("video") || url.contains(".mp4") || url.contains(".webm") || url.contains(".mov") -> "video"
                else -> "image"
            }
            var ext = url.substringAfterLast('.', "").substringBefore('?').lowercase().take(4)
            if (ext !in setOf("png", "jpg", "jpeg", "webp", "mp4", "webm", "mov", "gif")) ext = if (type == "video") "mp4" else "png"
            if (ext == "jpeg") ext = "jpg"
            val dir = File(ctx.filesDir, "flow_downloads").apply { mkdirs() }
            val base = (nameHint.ifBlank { url.substringAfterLast('/').substringBefore('?') }.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40))
                .ifBlank { "flow_${System.currentTimeMillis()}" }
            val f = File(dir, "${System.currentTimeMillis()}_$base.$ext")
            f.writeBytes(bytes)
            add(ctx, f.absolutePath, base, type, ext)
        } catch (_: Throwable) { null }
    }
}

/** JS bridge: the page reports every generated media element; we download + index the new ones. */
private class FlowBridge(
    private val scope: CoroutineScope, private val ctx: Context,
    private val onImported: (Int) -> Unit, private val onStatus: (String) -> Unit
) {
    private val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @JavascriptInterface
    fun onMedia(url: String, mime: String, name: String) {
        if (!url.startsWith("http")) return
        if (!seen.add(url)) return
        scope.launch {
            val item = FlowAssetStore.importUrl(ctx, url, name)
            if (item != null) withContext(Dispatchers.Main) {
                onImported(FlowAssetStore.count(ctx))
                onStatus("Imported “${item.title}” — now in your asset library")
            }
        }
    }

    @JavascriptInterface
    fun onLog(msg: String) { onStatus(msg.take(120)) }
}

/** JS that collects every img/video URL currently on the page and reports the new ones to the bridge. */
private const val COLLECT_JS = """
(function(){
  var out=[];
  document.querySelectorAll('img,video').forEach(function(e){
    var u=e.currentSrc||e.src||'';
    if(!u && e.tagName==='VIDEO'){var s=e.querySelector('source'); if(s) u=s.src||'';}
    if(u && u.indexOf('http')===0){
      out.push({url:u, mime:(e.tagName==='VIDEO'?'video':'image'), name:(e.alt||e.getAttribute('aria-label')||e.getAttribute('title')||'flow_asset')});
    }
  });
  out.forEach(function(m){ try{ window.SaifFlow.onMedia(m.url, m.mime, m.name); }catch(err){} });
  return out.length;
})()
"""

/**
 * Round 12 — driver JS: lets the SAIF AGENT drive the logged-in Flow page itself. queue() fills the prompt
 * box, clicks the generate/create button, waits 8 s between prompts (like the reference video) and reports
 * progress through the bridge. Every generated image is still picked up by COLLECT_JS and auto-imported.
 */
private const val AUTO_JS = """
window.__saifStop = false;
window.SaifFlowAuto = {
  queue: function(json) {
    var prompts = JSON.parse(json); var i = 0; window.__saifStop = false;
    function step() {
      if (window.__saifStop) { window.SaifFlow.onLog('STOPPED at ' + i + '/' + prompts.length); return; }
      if (i >= prompts.length) { window.SaifFlow.onLog('DONE ' + prompts.length); return; }
      var p = prompts[i];
      window.SaifFlow.onLog('Generating ' + (i + 1) + '/' + prompts.length + ': ' + p.substring(0, 70));
      var ta = document.querySelector('textarea') || document.querySelector('[contenteditable="true"]');
      if (ta) {
        ta.focus();
        if (ta.tagName === 'TEXTAREA') { ta.value = p; } else { ta.innerText = p; }
        ta.dispatchEvent(new Event('input', { bubbles: true }));
        setTimeout(function() {
          var btns = Array.prototype.slice.call(document.querySelectorAll('button'));
          var b = btns.find(function(x) { var t = (x.innerText || '') + ' ' + (x.getAttribute('aria-label') || ''); return /create|generate|make|image/i.test(t) && !x.disabled; });
          if (b) { b.click(); } else { var f = document.querySelector('form'); if (f) { if (f.requestSubmit) { f.requestSubmit(); } else { f.submit(); } } }
        }, 600);
      } else {
        window.SaifFlow.onLog('No prompt box found — open the Flow create page first');
      }
      i = i + 1;
      setTimeout(step, 8000);
    }
    step();
  },
  stop: function() { window.__saifStop = true; }
};
'ok'
"""

/** Shared persistent WebView for Flow: collector JS + agent driver JS + download-to-library listener. */
private fun flowWebView(ctx: Context, scope: CoroutineScope, bridge: FlowBridge, onDownload: (Int, String) -> Unit): WebView = WebView(ctx).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
    settings.mediaPlaybackRequiresUserGesture = false
    settings.builtInZoomControls = true
    settings.setSupportZoom(true)
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    addJavascriptInterface(bridge, "SaifFlow")
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, u: String?) {
            val v = view ?: return
            v.evaluateJavascript(AUTO_JS, null)
            v.evaluateJavascript(COLLECT_JS, null)
            // keep collecting as the Flow UI renders new results
            val ticker = object : Runnable {
                override fun run() {
                    v.evaluateJavascript(COLLECT_JS, null)
                    v.postDelayed(this, 3000)
                }
            }
            v.postDelayed(ticker, 3000)
        }
    }
    webChromeClient = WebChromeClient()
    setDownloadListener(DownloadListener { dlUrl, _, _, _, _ ->
        scope.launch {
            val item = FlowAssetStore.importUrl(ctx.applicationContext, dlUrl, "download")
            withContext(Dispatchers.Main) {
                val msg = if (item != null) "Downloaded & imported “" + item.title + "”" else "Download failed for that file"
                onDownload(FlowAssetStore.count(ctx.applicationContext), msg)
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        }
    })
}

/**
 * Full-screen in-app browser with a PERSISTENT session (login to Flow / FX / Whisk once, it stays logged in).
 * Generated images/clips are auto-imported into the app asset library; the button imports everything
 * currently visible on the page.
 */
@Composable
fun FlowAiBrowserDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://flow.google.com/") }
    var imported by remember { mutableStateOf(FlowAssetStore.count(ctx)) }
    var status by remember { mutableStateOf("Login to Flow / FX, generate images — they are imported automatically.") }

    val bridge = remember { FlowBridge(scope, ctx.applicationContext, { imported = it }, { status = it }) }
    val webView = remember { flowWebView(ctx, scope, bridge) { c, msg -> imported = c; status = msg } }
    DisposableEffect(Unit) { onDispose { runCatching { webView.destroy() } } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF09080E)) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    Icon(Icons.Default.Public, null, tint = Color(0xFF00E5FF))
                    OutlinedTextField(url, { url = it }, Modifier.weight(1f), singleLine = true, label = { Text("URL") },
                        trailingIcon = { TextButton(onClick = { webView.loadUrl(url.trim()) }) { Text("Go", color = Color(0xFF00E5FF)) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf("Flow" to "https://flow.google.com/", "FX / Whisk" to "https://labs.google/fx/", "Gemini" to "https://gemini.google.com/").forEach { (label, u) ->
                        OutlinedButton(onClick = { url = u; webView.loadUrl(u) }) { Text(label, fontSize = 11.sp) }
                    }
                    OutlinedButton(onClick = { webView.evaluateJavascript(COLLECT_JS, null); status = "Scanning page for images/clips…" }) { Text("Import page media", fontSize = 11.sp) }
                }
                Text(status, color = Color(0xFF9EE6B8), fontSize = 11.sp)
                Text("Imported into your asset library: $imported — the AI Director can use these in videos (crop / edit / animate / bg-remove).",
                    color = Color(0xFF9E95AC), fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black))
            }
        }
    }
}

/**
 * AI asset tools over the Flow-imported (and any local) images: AI edit / background remove / restyle with the
 * user's own provider key, local crop, delete, and manual URL import.
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
                Text("Flow-imported images: crop, AI-edit, background-remove — all with YOUR API key from Settings. Imported files join the video asset library automatically.",
                    color = Color(0xFF9E95AC), fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(importUrl, { importUrl = it }, Modifier.weight(1f), singleLine = true, label = { Text("Paste image URL") })
                    TextButton(enabled = importUrl.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch {
                            val it = FlowAssetStore.importUrl(ctx, importUrl.trim(), "pasted")
                            note = if (it != null) "Imported “${it.title}”" else "Could not import that URL"
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
                                TextButton(enabled = !busy, onClick = { cropTarget = a }) { Text("✂️ Crop", fontSize = 10.sp) }
                            }
                            TextButton(enabled = !busy, onClick = { FlowAssetStore.remove(ctx, a.fileId); refresh() }) { Text("🗑", fontSize = 10.sp) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) Text("Working with your AI key…", color = Color(0xFFFFB86B), fontSize = 11.sp)
                    TextButton(enabled = !busy && items.any { it.type == "image" }, onClick = {
                        busy = true
                        scope.launch {
                            val prof = AiProfile.fromSettings(ctx)
                            var n = 0
                            for (a in items.filter { it.type == "image" }.take(12)) {
                                val src = withContext(Dispatchers.IO) { runCatching { File(a.fileId.removePrefix("local:")).readBytes() }.getOrNull() } ?: continue
                                val bytes = ImageGen.edit("Remove the background completely. Keep only the main subject with sharp edges on a transparent PNG background. No text, no watermark.", src, prof.provider, prof.apiKey, prof.activeModel, prof.customBaseUrl).getOrNull()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val dir = File(ctx.filesDir, "flow_downloads").apply { mkdirs() }
                                    val f = File(dir, "nobg_" + System.currentTimeMillis() + ".png")
                                    f.writeBytes(bytes)
                                    FlowAssetStore.add(ctx, f.absolutePath, a.title + "-nobg", "image", "png")
                                    n++
                                }
                            }
                            note = if (n > 0) n.toString() + " image(s) ka background remove karke nayi copies bana di (library mein add)" else "bg-remove fail hua — Settings ka key/provider check karo"
                            busy = false; refresh()
                        }
                    }) { Text("🪄 Sabka bg-remove", fontSize = 10.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    // AI edit / background-remove dialog
    editTarget?.let { a ->
        val path = a.fileId.removePrefix("local:")
        AlertDialog(onDismissRequest = { editTarget = null },
            title = { Text("AI edit: ${a.title.take(30)}") },
            text = { OutlinedTextField(editPrompt, { editPrompt = it }, Modifier.fillMaxWidth(), maxLines = 4,
                placeholder = { Text("e.g. remove the background, keep only the person, transparent PNG • or: make it cyberpunk neon style", fontSize = 12.sp) }) },
            confirmButton = { TextButton(enabled = editPrompt.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val prof = AiProfile.fromSettings(ctx)
                    val src = withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() }
                    val r = if (src != null) ImageGen.edit(editPrompt.trim(), src, prof.provider, prof.apiKey, prof.activeModel, prof.customBaseUrl)
                            else Result.failure<ByteArray>(IllegalStateException("file unreadable"))
                    val bytes = r.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val dir = File(ctx.filesDir, "flow_downloads").apply { mkdirs() }
                        val f = File(dir, "edited_${System.currentTimeMillis()}.png")
                        f.writeBytes(bytes)
                        FlowAssetStore.add(ctx, f.absolutePath, "${a.title}-edit", "image", "png")
                        note = "Saved edited image — added to the library"
                    } else note = "AI edit failed: ${r.exceptionOrNull()?.message ?: "unknown"} — check the provider/key in Settings"
                    busy = false; editTarget = null; refresh()
                }
            }) { Text("Run with my API key") } },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel") } })
    }

    // Local crop dialog
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
                                val dir = File(ctx.filesDir, "flow_downloads").apply { mkdirs() }
                                val f = File(dir, "cropped_${System.currentTimeMillis()}.png")
                                java.io.FileOutputStream(f).use { o -> out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o) }
                                FlowAssetStore.add(ctx, f.absolutePath, "${a.title}-crop", "image", "png")
                                withContext(Dispatchers.Main) { note = "Cropped image saved to the library"; cropTarget = null; refresh() }
                            }
                        })
                    }
                }
            }
        } else { cropTarget = null }
    }
}


/**
 * Round 12 — FLOW BATCH AUTOPILOT (the reference video's "browser button" flow, fully in-app):
 * the SAIF agent builds N image prompts for the topic, then DRIVES the logged-in Flow page itself —
 * fills the prompt box, clicks generate, waits 8 s between prompts — and every generated image/video
 * is auto-downloaded into the asset library (FlowAssetStore). onDone(count) fires when the queue finishes.
 */
@Composable
fun FlowBatchDialog(topic: String, aspect: String, style: String, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompts by remember { mutableStateOf<List<String>?>(null) }
    var loadErr by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SAIF Agent Flow ke liye image prompts bana raha hai…") }
    var imported by remember { mutableStateOf(FlowAssetStore.count(ctx)) }
    var queued by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val services = remember { com.example.studio.director.DirectorServices(ctx.applicationContext) }

    LaunchedEffect(topic) {
        val r = services.flowImagePrompts(topic, 15, aspect, style)
        prompts = r.getOrNull()
        loadErr = r.exceptionOrNull()?.message ?: ""
    }
    LaunchedEffect(finished) {
        if (finished) {
            kotlinx.coroutines.delay(2500)
            onDone(FlowAssetStore.count(ctx.applicationContext))
        }
    }

    val bridge = remember {
        FlowBridge(scope, ctx.applicationContext, { imported = it }, { m ->
            status = m
            if (m.startsWith("DONE")) finished = true
        })
    }
    val webView = remember { flowWebView(ctx, scope, bridge) { c, msg -> imported = c; status = msg } }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView.evaluateJavascript("window.SaifFlowAuto && window.SaifFlowAuto.stop()", null)
                webView.destroy()
            }
        }
    }

    // Auto-queue as soon as the prompt list is ready; retry until the driver JS is injected (page loaded).
    LaunchedEffect(prompts) {
        val p = prompts
        if (p != null && p.isNotEmpty() && !queued) {
            queued = true
            var tries = 0
            val starter = object : Runnable {
                override fun run() {
                    tries++
                    webView.evaluateJavascript(
                        "if(window.SaifFlowAuto){window.SaifFlowAuto.queue(" + JSONArray(p).toString() + ");1}else{0}",
                        { v -> if (v != "1" && tries < 40) webView.postDelayed(this, 2000) })
                }
            }
            webView.postDelayed(starter, 2500)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF09080E)) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        webView.evaluateJavascript("window.SaifFlowAuto && window.SaifFlowAuto.stop()", null)
                        onDismiss()
                    }) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("🖼 Flow batch — agent khud drive karega", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Topic: " + topic.take(70), color = Color(0xFF9E95AC), fontSize = 10.sp, maxLines = 1)
                    }
                    Text(imported.toString() + " imported", color = Color(0xFF9EE6B8), fontSize = 11.sp)
                }
                val p = prompts
                if (p == null) {
                    if (loadErr.isBlank()) Text("Agent prompts bana raha hai…", color = Color(0xFFFFB86B), fontSize = 11.sp)
                    else Text("Prompt error: " + loadErr, color = Color(0xFFFF6B6B), fontSize = 11.sp)
                } else {
                    Text(p.size.toString() + " prompts · 8s gap · " + (if (queued) "auto-generate chal raha hai" else "queue ready") + " — Flow mein login hona chahiye (pehle setup)", color = Color(0xFF9E95AC), fontSize = 10.sp)
                }
                Text(status, color = Color(0xFF00E5FF), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black))
            }
        }
    }
}
