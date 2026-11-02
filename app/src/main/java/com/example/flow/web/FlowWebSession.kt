package com.example.flow.web

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.flow.FLOW_HOME
import com.example.flow.FlowCaptured
import com.example.flow.FlowDownloader
import com.example.flow.FlowDriver
import com.example.flow.FlowMediaRules
import com.example.flow.FlowPageInfo
import com.example.flow.FlowRecord
import com.example.flow.FlowRoles
import com.example.flow.FlowSettings
import com.example.flow.FlowUserAgent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Round 14 — ONE real WebView for Flow, owned by a full-screen Activity.
 *
 * Fixes for the owner's complaints:
 *  - **scroll / pinch zoom works**: the WebView now lives in its own Activity (not a Compose Dialog),
 *    gets all touch events, forces a zoomable viewport (Flow/Gemini ship `user-scalable=no`) and has
 *    on-screen A−/A+ buttons that apply a CSS zoom the page cannot block.
 *  - **login whenever you want**: the session is a normal persistent WebView cookie jar, a Google
 *    account chooser can be opened any time from the top bar, and there is a Logout button. Nothing is
 *    "one time" and nothing is locked after the first import.
 *  - **real downloads**: downloads carry the WebView cookies + Referer, and blob:/canvas results are
 *    read inside the page and streamed to Kotlin in base64 chunks.
 *  - **never the profile photo**: only media that appears AFTER a prompt is armed is reported, and
 *    avatars/logos/icons/header art are rejected by URL + element + real-pixel-size rules.
 */
class FlowWebSession(private val context: Context) : FlowDriver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var webView: WebView? = null
    private var pollJob: Job? = null
    private val cookies get() = CookieManager.getInstance()

    private val _info = MutableStateFlow(FlowPageInfo(url = FlowSettings.lastUrl(context)))
    val pageInfo: StateFlow<FlowPageInfo> = _info.asStateFlow()

    private val _log = MutableStateFlow("Flow browser ready — login karo, phir prompts generate honge.")
    val log: StateFlow<String> = _log.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    /** How many Flow pictures are already in the library (shown live in the browser). */
    private val _libraryCount = MutableStateFlow(0)
    val libraryCount: StateFlow<Int> = _libraryCount.asStateFlow()

    private val _zoom = MutableStateFlow(100)
    val zoomPercent: StateFlow<Int> = _zoom.asStateFlow()

    private val channel = Channel<FlowCaptured>(Channel.UNLIMITED)
    val captures: ReceiveChannel<FlowCaptured> = channel

    @Volatile var captureEnabled: Boolean = true

    /** Fired for media the page itself downloads (so it also lands in the library). */
    var onDownloaded: ((FlowRecord) -> Unit)? = null

    private val base64Builders = ConcurrentHashMap<String, StringBuilder>()
    private val base64Meta = ConcurrentHashMap<String, Base64Meta>()
    private val pendingBlobs = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val blobMeta = ConcurrentHashMap<String, FlowCaptured>()
    private val token = AtomicInteger(0)
    private var zoomValue = 1.0f

    private class Base64Meta(
        val mime: String, val width: Int, val height: Int,
        val promptIndex: Int, val name: String
    )

    // ------------------------------------------------------------------ lifecycle

    fun create(): WebView {
        webView?.let { return it }
        val wv = WebView(context)
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.setBackgroundColor(Color.BLACK)
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.requestFocus()

        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(wv, true)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            loadWithOverviewMode = false
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = 100
            mediaPlaybackRequiresUserGesture = false
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = if (FlowSettings.desktopMode(context)) FlowUserAgent.desktop() else FlowUserAgent.mobile()
            allowFileAccess = false
            allowContentAccess = true
        }

        wv.addJavascriptInterface(Bridge(), "SaifFlow")

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                _progress.value = 8
                if (url != null) _info.value = _info.value.copy(url = url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (url != null) {
                    _info.value = _info.value.copy(url = url)
                    FlowSettings.setLastUrl(context, url)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return openExternallyIfNeeded(url)
            }

            @Deprecated("Kept for old WebView builds")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url != null && openExternallyIfNeeded(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                _progress.value = 100
                val v = view ?: return
                v.evaluateJavascript(FlowJs.SETUP, null)
                v.evaluateJavascript(FlowJs.CAPTURE, null)
                _info.value = _info.value.copy(url = url ?: _info.value.url)
                scope.launch { refreshInfo() }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    _log.value = "Page load issue: " + (error?.description?.toString() ?: "unknown") + " — internet / login check karo"
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _progress.value = newProgress
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                _log.value = message?.take(140) ?: "Flow message"
                result?.confirm()
                return true
            }
        }

        wv.setDownloadListener { url, _, _, mime, _ ->
            if (url == null || !url.startsWith("http")) return@setDownloadListener
            _log.value = "File download shuru…"
            scope.launch {
                val rec = withContext(Dispatchers.IO) {
                    FlowDownloader.download(context, url, cookies.getCookie(url) ?: "", -1, "", FlowRoles.BACKGROUND, "flow_download")
                }
                if (rec != null) {
                    refreshLibraryCount()
                    _log.value = "Downloaded & added: " + rec.title
                    onDownloaded?.invoke(rec)
                } else {
                    _log.value = "Download fail: " + FlowDownloader.lastError.ifBlank { "unknown" }
                }
            }
        }

        webView = wv
        wv.loadUrl(FlowSettings.lastUrl(context))
        startPolling()
        refreshLibraryCount()
        return wv
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (isActive) {
                refreshInfo()
                delay(2500)
            }
        }
    }

    fun destroy() {
        pollJob?.cancel(); pollJob = null
        runCatching { cookies.flush() }
        webView?.let { wv ->
            runCatching {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.removeJavascriptInterface("SaifFlow")
                wv.destroy()
            }
        }
        webView = null
    }

    // ------------------------------------------------------------------ navigation / controls

    fun go(url: String) {
        val clean = if (url.startsWith("http")) url else "https://$url"
        webView?.loadUrl(clean)
    }

    fun reload() { webView?.reload() }
    fun goBack() { if (webView?.canGoBack() == true) webView?.goBack() }
    fun goForward() { if (webView?.canGoForward() == true) webView?.goForward() }

    /** A− / A+ : CSS zoom the page cannot disable + the native zoom as a second path. */
    fun zoom(delta: Float) {
        zoomValue = (zoomValue * delta).coerceIn(0.5f, 2.5f)
        _zoom.value = (zoomValue * 100).toInt()
        webView?.let { wv ->
            if (delta > 1f) wv.zoomIn() else wv.zoomOut()
            wv.evaluateJavascript(FlowJs.SET_ZOOM.replace("%s", String.format(Locale.US, "%.2f", zoomValue)), null)
        }
    }

    fun resetZoom() {
        zoomValue = 1f
        _zoom.value = 100
        webView?.evaluateJavascript(FlowJs.SET_ZOOM.replace("%s", "1"), null)
    }

    fun scrollByPage(down: Boolean) {
        val wv = webView ?: return
        val h = wv.height.coerceAtLeast(200)
        wv.scrollBy(0, if (down) (h * 0.8f).toInt() else -(h * 0.8f).toInt())
    }

    fun setDesktopMode(on: Boolean) {
        FlowSettings.setDesktopMode(context, on)
        webView?.let { wv ->
            wv.settings.userAgentString = if (on) FlowUserAgent.desktop() else FlowUserAgent.mobile()
            wv.reload()
        }
    }

    /** "Login karo" — opens the Google account chooser inside the same session. */
    fun openLogin() {
        webView?.loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fflow.google.com%2F")
    }

    /** Sign out for real (cookies + web storage), so a different account can be used any time. */
    fun logout() {
        val wv = webView
        scope.launch {
            runCatching {
                cookies.removeAllCookies(null)
                cookies.flush()
                WebStorage.getInstance().deleteAllData()
            }
            wv?.evaluateJavascript(FlowJs.CLEAR_STORAGE, null)
            wv?.clearCache(true)
            wv?.clearHistory()
            wv?.loadUrl(FLOW_HOME)
            _log.value = "Logged out. Kabhi bhi dobara login kar sakte ho."
            delay(400)
            refreshInfo()
        }
    }

    fun importPageNow(countCallBack: (Int) -> Unit) {
        val wv = webView ?: run { countCallBack(0); return }
        wv.evaluateJavascript(FlowJs.IMPORT_PAGE) { v ->
            val n = v?.trim()?.toIntOrNull() ?: 0
            countCallBack(n)
        }
    }

    private fun openExternallyIfNeeded(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:")) return false
        val intent = runCatching { Intent(Intent.ACTION_VIEW, Uri.parse(url)) }.getOrNull() ?: return true
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return true
    }

    // ------------------------------------------------------------------ FlowDriver

    override suspend fun info(): FlowPageInfo {
        val raw = eval(FlowJs.PAGE_INFO) ?: return _info.value
        return runCatching {
            val o = JSONObject(raw)
            val parsed = FlowPageInfo(
                url = o.optString("url", _info.value.url),
                hasComposer = o.optBoolean("hasComposer"),
                needsLogin = o.optBoolean("needsLogin"),
                imageCount = o.optInt("images")
            )
            _info.value = parsed
            parsed
        }.getOrElse { _info.value }
    }

    override suspend fun arm(promptIndex: Int): Boolean {
        val js = FlowJs.ARM.replace("%d", promptIndex.toString())
        val r = eval(js)
        return r != null && r.contains("ok")
    }

    override suspend fun submit(prompt: String): Boolean {
        val quoted = JSONObject.quote(prompt)
        val js = FlowJs.SUBMIT.replace("%s", quoted)
        val r = eval(js)
        return r != null && r.contains("ok") && !r.contains("nocomposer") && !r.contains("nosetup")
    }

    override suspend fun cookieHeader(): String =
        cookies.getCookie("https://flow.google.com/") ?: ""

    override suspend fun cookieFor(url: String): String = runCatching { cookies.getCookie(url) ?: "" }.getOrDefault("")

    override suspend fun resolveBlob(url: String): String? {
        val wv = webView ?: return null
        val id = "b" + token.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        pendingBlobs[id] = deferred
        withContext(Dispatchers.Main.immediate) {
            wv.evaluateJavascript(
                "(function(){var S=window.__saifFlow; if(!S||!S.resolveBlob) return 'no'; return S.resolveBlob(" +
                    JSONObject.quote(url) + "," + JSONObject.quote(id) + ");})();", null
            )
        }
        val value = withTimeoutOrNull(20_000L) { deferred.await() }
        pendingBlobs.remove(id)
        return value
    }

    private suspend fun refreshInfo() {
        runCatching { info() }
        refreshLibraryCount()
    }

    private fun refreshLibraryCount() {
        _libraryCount.value = runCatching { com.example.flow.FlowLibrary.count(context) }.getOrDefault(0)
    }

    private suspend fun eval(js: String): String? {
        val wv = webView ?: return null
        return withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(7000L) {
                suspendCancellableCoroutine<String?> { cont ->
                    try {
                        wv.evaluateJavascript(js) { value -> if (cont.isActive) cont.resume(value) }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ JS bridge

    private inner class Bridge {
        @JavascriptInterface
        fun onMedia(url: String, mime: String, width: Int, height: Int, promptIndex: Int, name: String) {
            if (!captureEnabled) return
            if (url.isBlank()) return
            if (FlowMediaRules.isUiChrome(url) && !url.startsWith("blob:")) return
            if (url.startsWith("blob:")) {
                // Blob results exist only inside the page: read them there and stream back as base64.
                val wv = webView ?: return
                val id = "b" + token.incrementAndGet()
                blobMeta[id] = FlowCaptured(url = url, mime = if (mime == "video") "video" else "image",
                    width = width, height = height, promptIndex = promptIndex, title = name)
                scope.launch(Dispatchers.Main.immediate) {
                    wv.evaluateJavascript(
                        "(function(){var S=window.__saifFlow; if(!S||!S.resolveBlob) return 'no'; return S.resolveBlob(" +
                            JSONObject.quote(url) + "," + JSONObject.quote(id) + ");})();", null
                    )
                }
                return
            }
            if (width > 0 && width < FlowMediaRules.MIN_SIDE) return
            if (height > 0 && height < FlowMediaRules.MIN_SIDE) return
            channel.trySend(FlowCaptured(url = url, mime = mime, width = width, height = height,
                promptIndex = promptIndex, title = name))
        }

        @JavascriptInterface
        fun onBase64Start(id: String, mime: String, width: Int, height: Int, promptIndex: Int, name: String, total: Int) {
            if (!captureEnabled) return
            base64Builders[id] = StringBuilder()
            base64Meta[id] = Base64Meta(mime, width, height, promptIndex, name)
        }

        @JavascriptInterface
        fun onBase64Chunk(id: String, index: Int, chunk: String) {
            base64Builders[id]?.append(chunk)
        }

        @JavascriptInterface
        fun onBase64End(id: String) {
            val data = base64Builders.remove(id)?.toString() ?: return
            val meta = base64Meta.remove(id)
            pendingBlobs.remove(id)?.complete(data)
            val captured = blobMeta.remove(id)
            if (captured != null) {
                channel.trySend(captured.copy(base64 = data))
                return
            }
            if (data.length < 2000 || meta == null) return
            channel.trySend(FlowCaptured(
                url = "canvas:" + id, mime = meta.mime, width = meta.width, height = meta.height,
                base64 = data, promptIndex = meta.promptIndex, title = meta.name
            ))
        }

        @JavascriptInterface
        fun onLog(message: String) {
            _log.value = message.take(160)
        }
    }
}
