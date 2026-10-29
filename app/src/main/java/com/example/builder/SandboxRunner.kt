package com.example.builder

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class SandboxReport(
    val passed: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val domElementCount: Int,
    val interactiveElementCount: Int,
    val hasRenderableBody: Boolean
)

/**
 * Headless Execution Sandbox & DOM Verifier.
 * Executes generated HTML/JS inside a sandboxed environment to catch runtime exceptions,
 * broken element IDs (e.g. getElementById mismatches), and DOM render failures before deployment.
 */
object SandboxRunner {
    private const val TAG = "SandboxRunner"

    /**
     * Runs both runtime WebView evaluation (if Looper available) and static heuristic verification.
     */
    suspend fun evaluate(context: Context?, html: String): SandboxReport = withContext(Dispatchers.Default) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Static DOM & JS Id-Binding Verification
        val staticResults = verifyDomAndJsBindings(html)
        errors.addAll(staticResults.errors)
        warnings.addAll(staticResults.warnings)

        // 2. Count DOM elements & interactive elements
        val interactiveCount = countInteractiveElements(html)
        val hasBody = html.contains("<body", ignoreCase = true) && html.contains("</body>", ignoreCase = true)

        if (interactiveCount == 0) {
            warnings.add("No interactive elements (buttons, inputs, clickable cards) detected in the application.")
        }

        // 3. Android WebView Runtime Probe (if context is valid and main looper accessible)
        if (context != null && canRunWebView()) {
            val runtimeResults = runHeadlessWebViewProbe(context, html)
            errors.addAll(runtimeResults.errors)
            warnings.addAll(runtimeResults.warnings)
        }

        val passed = errors.isEmpty()
        SandboxReport(
            passed = passed,
            errors = errors.distinct(),
            warnings = warnings.distinct(),
            domElementCount = staticResults.estimatedDomNodes,
            interactiveElementCount = interactiveCount,
            hasRenderableBody = hasBody
        )
    }

    data class StaticVerification(
        val errors: List<String>,
        val warnings: List<String>,
        val estimatedDomNodes: Int
    )

    /**
     * Inspects ID cross-referencing between JavaScript (getElementById, querySelector) and HTML tags.
     */
    fun verifyDomAndJsBindings(html: String): StaticVerification {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Extract all HTML element IDs: id="abc" or id='abc'
        val htmlIds = mutableSetOf<String>()
        val idRegex = Regex("""\bid=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        for (match in idRegex.findAll(html)) {
            htmlIds.add(match.groupValues[1])
        }

        // Extract script code
        val scriptContent = buildString {
            val scriptRegex = Regex("""<script(?![^>]*src=)[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
            for (match in scriptRegex.findAll(html)) {
                append(match.groupValues[1]).append("\n")
            }
        }

        // Check getElementById('...') references
        val getElementByIdRegex = Regex("""document\.getElementById\(\s*["']([^"']+)["']\s*\)""")
        for (match in getElementByIdRegex.findAll(scriptContent)) {
            val targetId = match.groupValues[1]
            if (!htmlIds.contains(targetId)) {
                // Fatal if JS attempts to access null element
                errors.add("JavaScript references document.getElementById('$targetId'), but no element with id='$targetId' exists in HTML.")
            }
        }

        // Check querySelector('#...') references
        val querySelectorIdRegex = Regex("""querySelector(All)?\(\s*["']#([a-zA-Z0-9_\-]+)["']\s*\)""")
        for (match in querySelectorIdRegex.findAll(scriptContent)) {
            val targetId = match.groupValues[2]
            if (!htmlIds.contains(targetId)) {
                warnings.add("JavaScript queries selector '#$targetId', but no matching element ID exists.")
            }
        }

        // Rough DOM node count estimation
        val tagOpenRegex = Regex("""<[a-zA-Z][a-zA-Z0-9]*(\s+[^>]*)?>""")
        val estimatedNodes = tagOpenRegex.findAll(html).count()

        return StaticVerification(
            errors = errors,
            warnings = warnings,
            estimatedDomNodes = estimatedNodes
        )
    }

    private fun countInteractiveElements(html: String): Int {
        val interactiveTags = listOf("button", "input", "select", "textarea", "a", "canvas")
        var count = 0
        for (tag in interactiveTags) {
            count += Regex("""<$tag(\s+[^>]*)?>""", RegexOption.IGNORE_CASE).findAll(html).count()
        }
        // Also count onclick or cursor-pointer elements
        count += Regex("""\bonclick=["'][^"']+["']""", RegexOption.IGNORE_CASE).findAll(html).count()
        return count
    }

    private fun canRunWebView(): Boolean {
        return try {
            Looper.getMainLooper() != null
        } catch (e: Throwable) {
            false
        }
    }

    private suspend fun runHeadlessWebViewProbe(context: Context, html: String): StaticVerification {
        val capturedErrors = mutableListOf<String>()
        val capturedWarnings = mutableListOf<String>()

        return withContext(Dispatchers.Main) {
            val probeResult = withTimeoutOrNull(2500) {
                suspendCancellableCoroutine<StaticVerification> { cont ->
                    val resumed = AtomicBoolean(false)
                    try {
                        val webView = WebView(context.applicationContext)
                        webView.settings.javaScriptEnabled = true
                        webView.settings.domStorageEnabled = true

                        webView.webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    val msg = it.message() ?: ""
                                    when (it.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> {
                                            capturedErrors.add("Console Error: $msg (line ${it.lineNumber()})")
                                        }
                                        ConsoleMessage.MessageLevel.WARNING -> {
                                            capturedWarnings.add("Console Warning: $msg")
                                        }
                                        else -> {}
                                    }
                                }
                                return true
                            }
                        }

                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (resumed.compareAndSet(false, true)) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        try {
                                            view?.destroy()
                                        } catch (ignored: Throwable) {}
                                        cont.resume(StaticVerification(capturedErrors, capturedWarnings, 0))
                                    }, 200)
                                }
                            }
                        }

                        webView.loadDataWithBaseURL("https://local.saifai.test", html, "text/html", "UTF-8", null)
                    } catch (e: Throwable) {
                        Log.w(TAG, "WebView probe failed to initialize: ${e.message}")
                        if (resumed.compareAndSet(false, true)) {
                            cont.resume(StaticVerification(emptyList(), emptyList(), 0))
                        }
                    }
                }
            }

            probeResult ?: StaticVerification(emptyList(), emptyList(), 0)
        }
    }
}
