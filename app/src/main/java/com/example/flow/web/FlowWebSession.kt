package com.example.flow.web

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
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
import com.example.flow.FlowSubmitResult
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
import org.json.JSONArray
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

    /** ROUND 15.4 — the prompt index the capture layer is currently armed for (-1 = nothing). */
    @Volatile private var armedIndex: Int = -1
    /** ROUND 15.4 — every media URL already handed to a slot; a URL is never used twice. */
    private val usedMedia: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

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
        val ok = r != null && r.contains("ok")
        if (ok) armedIndex = promptIndex else armedIndex = -1
        return ok
    }

    /**
     * ROUND 15.4 — START A NEW FLOW PROJECT.
     *
     * The owner's report was exact: a picture was generated, but the app downloaded a picture from an old
     * project instead. The cure is to never work inside an old project at all. This creates a fresh one
     * (or proves the current page is already empty), waits for its composer, re-injects the capture layer
     * and clears the "already used" list — so only pictures born in this new project can ever be captured.
     */
    override suspend fun newProject(): Boolean {
        var attempt = 0
        var created = false
        while (attempt < 6 && !created) {
            attempt++
            val raw = runCatching { eval(FlowJs.CREATE_PROJECT.replace("%s", JSONObject.quote(FLOW_HOME))) }
                .getOrNull().orEmpty()
            when {
                raw.contains("ok:fresh-project") -> created = true
                raw.contains("ok:clicked") -> { delay(4_000L); created = true }
                raw.contains("navigating") -> delay(5_000L)
                else -> delay(3_000L)
            }
        }
        if (!created) {
            _log.value = (_log.value + "\nNaya Flow project nahi ban paya — is baar purane page par kaam.").trim()
            return false
        }
        // The new project renders its composer after navigation: wait for it, then re-arm the capture layer.
        var ready = false
        var waited = 0
        while (waited < 20 && !ready) {
            waited++
            delay(1_200L)
            ready = runCatching { info().hasComposer }.getOrDefault(false)
        }
        runCatching { eval(FlowJs.SETUP) }
        runCatching { eval(FlowJs.CAPTURE) }
        armedIndex = -1
        usedMedia.clear()
        _log.value = (_log.value +
            "\nNaya Flow project: " + if (ready) "ready ✓ (purane project ki images band)" else "composer abhi nahi mila").trim()
        return ready
    }

    // ------------------------------------------------------- ROUND 15.3 · REAL TAPS

    /**
     * ROUND 15.3 — the arrow, for real this time.
     *
     * Why the previous approaches failed: Flow's send arrow is a plain icon button that ignores
     * synthetic `click()` / pointer events (React listens for real pointer input), and the soft
     * keyboard kept the composer half-scrolled so a stale coordinate missed. So:
     *
     *  1. PREPARE_SEND scrolls the composer into view, blurs the IME and un-blocks the
     *     "Google Flow can make mistakes" status banner,
     *  2. FIND_SEND reports the live viewport coordinates of the square-ish icon button in the
     *     composer's bottom-right corner (plus the raw corner as a fallback),
     *  3. Kotlin converts those CSS pixels to device pixels with the WebView's own scale and
     *     dispatches a real DOWN → MOVE → UP sequence through `dispatchTouchEvent` — this is
     *     pixel-for-pixel what the owner's finger does,
     *  4. confirmation is read from the page (composer cleared / busy / new <img>), never assumed.
     *
     * A JS click is only a secondary fallback, and the prompt is typed exactly once — retries
     * re-tap, they never re-type.
     */
    override suspend fun submitSmart(prompt: String): FlowSubmitResult {
        val typed = eval(FlowJs.TYPE_PROMPT.replace("%s", JSONObject.quote(prompt)))
        val typedOk = typed != null && typed.contains("ok")
        if (!typedOk) {
            _log.value = (_log.value + "\nPrompt box nahi mila — Flow page ready nahi hai.").trim()
            return FlowSubmitResult(clicked = false, confirmed = false, how = "nocomposer")
        }
        delay(500L)
        return pressSend()
    }

    /** Press the arrow on an already-typed prompt. Safe to call repeatedly. */
    override suspend fun pressSend(): FlowSubmitResult {
        val before = genState()
        runCatching { eval(FlowJs.PREPARE_SEND) }
        delay(700L)
        var spot = findSendSpot()
        if (spot == null && before == null) {
            return FlowSubmitResult(clicked = false, confirmed = false, how = "page-not-ready")
        }
        val targets = ArrayList<Pair<Double, Double>>()
        if (spot != null && spot.ok && spot.x > 1 && spot.y > 1) targets.add(spot.x to spot.y)
        if (spot != null && spot.cornerX > 1 && spot.cornerY > 1) {
            targets.add(spot.cornerX to spot.cornerY)
            targets.add((spot.cornerX - 14) to (spot.cornerY - 10))
        }
        if (targets.isEmpty()) {
            // Nothing to tap (composer hidden) — one JS attempt, then report honestly.
            val js = eval(FlowJs.CLICK_SEND)
            val ok = js != null && js.contains("ok")
            val confirmed = ok && waitConfirm(before, 6_000L)
            return FlowSubmitResult(clicked = ok, confirmed = confirmed, how = if (ok) "js" else "no-arrow")
        }
        var tried = 0
        var how = "none"
        for (target in targets) {
            tried++
            how = "tap:" + (spot?.name ?: "corner")
            val tapped = tapAt(target.first, target.second)
            if (!tapped) continue
            if (waitConfirm(before, 8_000L)) {
                _log.value = (_log.value + "\nArrow dab gaya ✓ · " + how).trim()
                return FlowSubmitResult(clicked = true, confirmed = true, how = how)
            }
            // Secondary: JS click on the best-scored control (harmless if the tap already worked).
            val js = eval(FlowJs.CLICK_SEND)
            if (js != null && js.contains("ok") && waitConfirm(before, 4_500L)) {
                _log.value = (_log.value + "\nArrow dab gaya ✓ · tap + js").trim()
                return FlowSubmitResult(clicked = true, confirmed = true, how = "tap+js")
            }
            if (tried >= 2) break
            spot = findSendSpot() ?: spot
        }
        _log.value = (_log.value + "\nArrow tap bheja, page ne abhi confirm nahi kiya — picture ka wait kar raha hoon.").trim()
        return FlowSubmitResult(clicked = tried > 0, confirmed = false, how = how)
    }

    private data class SendSpot(
        val x: Double,
        val y: Double,
        val cornerX: Double,
        val cornerY: Double,
        val name: String,
        val score: Int,
        val ok: Boolean
    )

    /** evaluateJavascript returns a JSON string; it may be quoted depending on the JS return type. */
    private fun unquoteJson(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("\"")) return t
        return runCatching { JSONArray("[$t]").optString(0) }.getOrDefault(t.trim('"'))
    }

    private suspend fun findSendSpot(): SendSpot? {
        val raw = eval(FlowJs.FIND_SEND) ?: return null
        return runCatching {
            val o = JSONObject(unquoteJson(raw))
            val corner = o.optJSONObject("corner")
            SendSpot(
                x = o.optDouble("x", 0.0),
                y = o.optDouble("y", 0.0),
                cornerX = corner?.optDouble("x", 0.0) ?: 0.0,
                cornerY = corner?.optDouble("y", 0.0) ?: 0.0,
                name = o.optString("name", ""),
                score = o.optInt("score", 0),
                ok = o.optBoolean("ok")
            )
        }.getOrNull()
    }

    /** A REAL finger-style tap at CSS (x, y): DOWN → MOVE → UP through the WebView touch pipeline. */
    private suspend fun tapAt(xCss: Double, yCss: Double): Boolean = withContext(Dispatchers.Main.immediate) {
        val wv = webView ?: return@withContext false
        runCatching {
            val density = context.resources.displayMetrics.density
            val zoom = if (wv.scale > 0.05f) wv.scale else density
            val x = (xCss * zoom).toFloat().coerceAtLeast(2f)
            val y = (yCss * zoom).toFloat().coerceAtLeast(2f)
            wv.requestFocus()
            val t0 = SystemClock.uptimeMillis()
            fun fire(action: Int, dt: Long, px: Float, py: Float) {
                val ev = MotionEvent.obtain(t0, t0 + dt, action, px, py, 0)
                try { wv.dispatchTouchEvent(ev) } finally { ev.recycle() }
            }
            fire(MotionEvent.ACTION_DOWN, 0L, x, y)
            delay(45L)
            fire(MotionEvent.ACTION_MOVE, 22L, x + 0.6f, y + 0.4f)
            delay(40L)
            fire(MotionEvent.ACTION_UP, 70L, x + 0.8f, y + 0.6f)
            true
        }.getOrDefault(false)
    }

    /**
     * ROUND 15.5 — find the control whose label matches [pattern] and TAP it with a real MotionEvent.
     * Flow's React UI ignores synthetic clicks (that is why the arrow never pressed), so settings
     * controls get exactly the same real-touch treatment as the arrow. Returns the matched label or null.
     */
    private suspend fun tapMatching(pattern: String): String? {
        val raw = runCatching { eval(FlowJs.FIND_TEXT.replace("%s", JSONObject.quote(pattern))) }.getOrNull() ?: return null
        val o = runCatching { JSONObject(unquoteJson(raw)) }.getOrNull() ?: return null
        if (!o.optBoolean("ok")) return null
        val x = o.optDouble("x", 0.0)
        val y = o.optDouble("y", 0.0)
        if (x <= 1.0 || y <= 1.0) return null
        if (!tapAt(x, y)) return null
        delay(500L)
        return o.optString("label").ifBlank { "tap" }
    }

    private suspend fun waitConfirm(before: GenState?, windowMs: Long): Boolean {
        var waited = 0L
        while (waited < windowMs) {
            delay(1_100L)
            waited += 1_100L
            val now = genState() ?: continue
            val wasLen = before?.value?.length ?: 0
            if (wasLen > 8 && now.value.length < wasLen / 2) return true
            val wasImages = before?.images ?: Int.MAX_VALUE
            if (now.images > wasImages) return true
            if (now.busy) return true
        }
        return false
    }

    // ------------------------------------------------- ROUND 15.3 · Flow's own settings

    /** Pick Flow's aspect ratio. Returns true only when the control reported success. */
    override suspend fun setAspect(aspect: String): Boolean {
        runCatching { eval(FlowJs.PREPARE_SEND) }
        delay(250L)
        val opened = runCatching { eval(FlowJs.OPEN_SETTINGS) }.getOrNull().let { it != null && it.contains("ok") }
        delay(900L)
        val raw = runCatching { eval(FlowJs.SET_ASPECT.replace("%s", JSONObject.quote(aspect))) }.getOrNull()
        val res = raw?.let { unquoteJson(it) }.orEmpty()
        var ok = res.startsWith("ok") || res.startsWith("already")
        if (!ok) {
            // ROUND 15.5 — synthetic clicks are ignored by Flow's UI: tap the ratio itself, for real.
            val ratio = aspect.replace(":", "\\s*:\\s*").replace("/", "\\s*/\\s*")
            val label = runCatching { tapMatching("^\\s*" + ratio + "\\s*$") }.getOrNull()
                ?: runCatching { tapMatching(ratio) }.getOrNull()
            ok = label != null
            if (label != null) delay(400L)
        }
        if (!ok) runCatching { eval(FlowJs.CLOSE_MENUS) }
        delay(300L)
        val aspectNote = when {
            ok && opened -> "set ✓"
            ok -> "set ✓ (seedha)"
            else -> "control nahi mila — manual"
        }
        _log.value = (_log.value + "\nFlow aspect " + aspect + ": " + aspectNote).trim()
        return ok
    }

    /** Switch Flow to ONE image per run ("1 image at a time"). */
    override suspend fun setOneImage(): Boolean {
        runCatching { eval(FlowJs.PREPARE_SEND) }
        delay(250L)
        val menuRaw = runCatching { eval(FlowJs.OPEN_COUNT_MENU) }.getOrNull()
        if (menuRaw == null || !menuRaw.contains("ok:")) {
            _log.value = (_log.value + "\nFlow output chip nahi mila — 1-image setting manual.").trim()
            return false
        }
        val parts = unquoteJson(menuRaw).split(":")
        val cx = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val cy = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        delay(800L)
        // ROUND 15.5 — "1 image at a time" is picked with a REAL tap (Flow ignores synthetic clicks).
        val tappedOne = runCatching { tapMatching("^(x?1|1\\s?(image|photo|picture|result)s?|one image|only one)$") }.getOrNull()
        val pickJs = FlowJs.PICK_ONE_IMAGE.replace("%1", cx.toString()).replace("%2", cy.toString())
        val picked = tappedOne != null || runCatching { eval(pickJs) }.getOrNull().let { it != null && it.contains("ok:") }
        delay(600L)
        runCatching { eval(FlowJs.CLOSE_MENUS) }
        delay(300L)
        val chip = runCatching { eval(FlowJs.CHIP_STATE) }.getOrNull()?.let { unquoteJson(it) }.orEmpty()
        val verified = picked && Regex("(^|[^0-9])x?1([^0-9]|$)").containsMatchIn(chip)
        val oneNote = when {
            verified -> "set ✓"
            picked -> "chuna, chip: " + chip.take(30)
            else -> "option nahi mila — manual"
        }
        _log.value = (_log.value + "\nFlow 1-image: " + oneNote).trim()
        return verified || picked
    }

    private suspend fun genState(): GenState? {
        val raw = eval(FlowJs.GEN_STATE) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            GenState(o.optString("value"), o.optBoolean("busy"), o.optInt("images"), o.optBoolean("hasComposer"))
        }.getOrNull()
    }

    private data class GenState(val value: String, val busy: Boolean, val images: Int, val hasComposer: Boolean)


    override suspend fun submit(prompt: String): Boolean {
        val quoted = JSONObject.quote(prompt)
        val js = FlowJs.SUBMIT.replace("%s", quoted)
        val r = eval(js)
        return r != null && r.contains("ok") && !r.contains("nocomposer") && !r.contains("nosetup")
    }

    override suspend fun cookieHeader(): String =
        cookies.getCookie("https://flow.google.com/") ?: ""

    override suspend fun cookieFor(url: String): String = runCatching { cookies.getCookie(url) ?: "" }.getOrDefault("")

    override suspend fun openProject(): Boolean {
        val r = eval(FlowJs.NEW_PROJECT)
        return r != null && r.contains("clicked")
    }

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
            // ROUND 15.4 — only the prompt that is armed right now, and never the same URL twice.
            if (armedIndex < 0) return
            if (promptIndex >= 0 && promptIndex != armedIndex) return
            if (!usedMedia.add(url)) return
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
            if (armedIndex < 0 && promptIndex >= 0) return
            if (promptIndex >= 0 && promptIndex != armedIndex) return
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
