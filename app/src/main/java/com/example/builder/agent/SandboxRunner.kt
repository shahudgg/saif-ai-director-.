package com.example.builder.agent

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Headless in-app WebView sandbox runner.
 * Loads candidate HTML apps, traps JS errors, console warnings, and uncaught rejections,
 * verifies DOM probes, tests interactive elements, and generates a structured TestReport.
 */
object SandboxRunner {
    private const val TAG = "SandboxRunner"

    /**
     * Executes headless testing of the provided HTML document in a 360x780dp mobile viewport.
     */
    suspend fun testHtml(
        context: Context,
        htmlContent: String,
        spec: AppSpec?
    ): TestReport = withContext(Dispatchers.Main) {
        val jsErrors = mutableListOf<String>()
        val consoleLogs = mutableListOf<String>()
        val domProbeResults = mutableMapOf<String, Boolean>()
        var horizontalOverflow = false
        var smallTouchTargets = 0
        var isBlankScreen = false

        // Temporary file for file:// protocol support
        val tempTestFile = File(context.cacheDir, "sandbox_test_${System.currentTimeMillis()}.html")
        tempTestFile.writeText(htmlContent)

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                var webView: WebView? = null
                try {
                    webView = WebView(context.applicationContext).apply {
                        layout(0, 0, 1080, 2340) // Standard 360x780dp at 3x density
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // JavaScript interface bridge for receiving sandbox errors
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun reportJsError(msg: String, url: String, line: Int, col: Int) {
                                val err = "JS Error: $msg at line $line"
                                synchronized(jsErrors) { jsErrors.add(err) }
                                Log.e(TAG, err)
                            }

                            @JavascriptInterface
                            fun reportUnhandledRejection(reason: String) {
                                val err = "Unhandled Promise Rejection: $reason"
                                synchronized(jsErrors) { jsErrors.add(err) }
                                Log.e(TAG, err)
                            }

                            @JavascriptInterface
                            fun reportProbeResult(probe: String, found: Boolean) {
                                synchronized(domProbeResults) { domProbeResults[probe] = found }
                            }

                            @JavascriptInterface
                            fun reportMetrics(overflow: Boolean, smallTargets: Int, blank: Boolean) {
                                horizontalOverflow = overflow
                                smallTouchTargets = smallTargets
                                isBlankScreen = blank
                            }
                        }, "SaifSandboxHook")

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                val msg = consoleMessage?.message() ?: ""
                                val level = consoleMessage?.messageLevel()
                                if (level == ConsoleMessage.MessageLevel.ERROR) {
                                    synchronized(jsErrors) {
                                        jsErrors.add("Console Error: $msg (line ${consoleMessage?.lineNumber()})")
                                    }
                                }
                                synchronized(consoleLogs) { consoleLogs.add(msg) }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Run test probe script inside page
                                Handler(Looper.getMainLooper()).postDelayed({
                                    injectAndRunProbes(view, spec) {
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            try { view?.destroy() } catch (ignored: Exception) {}
                                            if (continuation.isActive) continuation.resume(Unit)
                                        }, 400)
                                    }
                                }, 800)
                            }
                        }

                        loadUrl("file://${tempTestFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    try { webView?.destroy() } catch (ignored: Exception) {}
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } finally {
            tempTestFile.delete()
        }

        // Build structured TestReport
        val issues = mutableListOf<TestIssue>()
        for (err in jsErrors) {
            issues.add(TestIssue("ERROR", err))
        }

        val failedProbes = mutableListOf<String>()
        domProbeResults.forEach { (probe, passed) ->
            if (!passed) {
                failedProbes.add(probe)
                issues.add(TestIssue("WARNING", "Expected element probe missing: $probe"))
            }
        }

        if (horizontalOverflow) {
            issues.add(TestIssue("WARNING", "Horizontal overflow detected (page scrollWidth > innerWidth)"))
        }

        if (isBlankScreen) {
            issues.add(TestIssue("ERROR", "Blank screen detected: No visible text rendered on load"))
        }

        val totalChecks = 10 + (spec?.features?.sumOf { it.domProbes.size } ?: 0)
        val errorWeight = jsErrors.size * 15 + (if (isBlankScreen) 40 else 0)
        val warnWeight = failedProbes.size * 5 + (if (horizontalOverflow) 10 else 0) + (smallTouchTargets * 2)
        val score = (100 - errorWeight - warnWeight).coerceIn(10, 100)
        val passed = jsErrors.isEmpty() && !isBlankScreen

        TestReport(
            passed = passed,
            passedCount = (totalChecks - issues.size).coerceAtLeast(0),
            totalCount = totalChecks,
            issues = issues,
            jsErrors = jsErrors,
            failedDomProbes = failedProbes,
            hasHorizontalOverflow = horizontalOverflow,
            smallTapTargetsCount = smallTouchTargets,
            isBlankScreen = isBlankScreen,
            qualityScore = score
        )
    }

    private fun injectAndRunProbes(webView: WebView?, spec: AppSpec?, onComplete: () -> Unit) {
        if (webView == null) {
            onComplete()
            return
        }

        val probesArray = JSONArray()
        spec?.features?.forEach { f ->
            f.domProbes.forEach { p -> probesArray.put(p) }
        }

        val script = """
            (() => {
                try {
                    // 1. Error trapping
                    window.addEventListener('error', (e) => {
                        if (window.SaifSandboxHook) {
                            window.SaifSandboxHook.reportJsError(e.message || '', e.filename || '', e.lineno || 0, e.colno || 0);
                        }
                    });
                    window.addEventListener('unhandledrejection', (e) => {
                        if (window.SaifSandboxHook) {
                            window.SaifSandboxHook.reportUnhandledRejection(String(e.reason));
                        }
                    });

                    // 2. Check DOM probes
                    const probes = ${probesArray};
                    for (let p of probes) {
                        try {
                            const found = !!document.querySelector(p);
                            if (window.SaifSandboxHook) window.SaifSandboxHook.reportProbeResult(p, found);
                        } catch (e) {
                            if (window.SaifSandboxHook) window.SaifSandboxHook.reportProbeResult(p, false);
                        }
                    }

                    // 3. Check layout metrics & overflow
                    const overflow = document.documentElement.scrollWidth > (window.innerWidth + 5);
                    const visibleText = document.body ? document.body.innerText.trim() : '';
                    const isBlank = visibleText.length < 15;

                    // 4. Check small tap targets (< 40px)
                    let smallTargets = 0;
                    const buttons = document.querySelectorAll('button, a, input, select');
                    for (let btn of buttons) {
                        const rect = btn.getBoundingClientRect();
                        if (rect.width > 0 && rect.height > 0) {
                            if (rect.width < 38 || rect.height < 38) smallTargets++;
                        }
                    }

                    if (window.SaifSandboxHook) {
                        window.SaifSandboxHook.reportMetrics(overflow, smallTargets, isBlank);
                    }

                    // 5. Auto-explore: simulate harmless click on first 3 tabs/buttons
                    let clicked = 0;
                    for (let btn of buttons) {
                        if (clicked >= 3) break;
                        const label = (btn.innerText || btn.value || '').toLowerCase();
                        if (!label.includes('delete') && !label.includes('clear') && !label.includes('reset') && !label.includes('logout')) {
                            try { btn.click(); clicked++; } catch (e) {}
                        }
                    }
                } catch (e) {
                    if (window.SaifSandboxHook) {
                        window.SaifSandboxHook.reportJsError(e.message || '', '', 0, 0);
                    }
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) {
            onComplete()
        }
    }
}
