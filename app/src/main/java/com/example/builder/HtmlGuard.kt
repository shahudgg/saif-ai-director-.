package com.example.builder

import android.util.Log

enum class GuardSeverity {
    ERROR,
    WARNING
}

data class GuardIssue(
    val rule: String,
    val message: String,
    val severity: GuardSeverity,
    val line: Int = -1,
    val fixSuggestion: String? = null
)

data class GuardReport(
    val isValid: Boolean,
    val issues: List<GuardIssue>,
    val sanitizedHtml: String
) {
    val errors: List<GuardIssue> get() = issues.filter { it.severity == GuardSeverity.ERROR }
    val warnings: List<GuardIssue> get() = issues.filter { it.severity == GuardSeverity.WARNING }
}

/**
 * Static Analysis & Integrity Guard for Autonomous HTML5 Apps.
 * Ensures generated code is self-contained, mobile-optimized, balanced,
 * and free of fatal WebView blockers before runtime execution.
 */
object HtmlGuard {
    private const val TAG = "HtmlGuard"

    val ALLOWED_CDN_DOMAINS = listOf(
        "cdn.jsdelivr.net",
        "cdnjs.cloudflare.com",
        "unpkg.com",
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "cdn.tailwindcss.com",
        "kit.fontawesome.com",
        "cdn.skypack.dev",
        "esm.sh",
        "code.jquery.com",
        "cdn.plot.ly",
        "cdn.sheetjs.com"
    )

    private val BLOCKING_DIALOG_REGEX = Regex("""\b(window\.)?(alert|confirm|prompt)\s*\(""", RegexOption.IGNORE_CASE)
    private val PLACEHOLDER_CODE_REGEX = Regex("""(?i)(//\s*\.\.\.\s*rest of code|/\*\s*\.\.\.\s*rest of code\s*\*/|//\s*TODO:\s*implement|/\*\s*TODO\s*\*/|<!--\s*TODO\s*-->|//\s*add your code here)""")

    /**
     * Inspects and sanitizes the HTML string.
     * Auto-fixes missing doctype, viewport, charset, unclosed tags, and injects safe dialog polyfill.
     */
    fun inspect(html: String): GuardReport {
        if (html.isBlank()) {
            return GuardReport(
                isValid = false,
                issues = listOf(GuardIssue("RULE_EMPTY", "HTML document is empty.", GuardSeverity.ERROR)),
                sanitizedHtml = ""
            )
        }

        val issues = mutableListOf<GuardIssue>()
        var workingHtml = html.trim()

        // 1. RULE_DOCTYPE: Check & ensure <!DOCTYPE html>
        if (!workingHtml.startsWith("<!DOCTYPE html", ignoreCase = true)) {
            issues.add(
                GuardIssue(
                    rule = "RULE_DOCTYPE",
                    message = "Document missing <!DOCTYPE html> declaration at start.",
                    severity = GuardSeverity.WARNING,
                    fixSuggestion = "Prepend <!DOCTYPE html>"
                )
            )
            workingHtml = "<!DOCTYPE html>\n$workingHtml"
        }

        // 2. RULE_SAIF_MARKER: Check <!-- saif-ai-build -->
        if (!workingHtml.contains("<!-- saif-ai-build -->")) {
            issues.add(
                GuardIssue(
                    rule = "RULE_SAIF_MARKER",
                    message = "Missing <!-- saif-ai-build --> identity tag.",
                    severity = GuardSeverity.WARNING,
                    fixSuggestion = "Inject marker in <head>"
                )
            )
            workingHtml = injectSaifMarker(workingHtml)
        }

        // 3. RULE_CHARSET: Check <meta charset="UTF-8">
        if (!workingHtml.contains("charset=", ignoreCase = true)) {
            issues.add(
                GuardIssue(
                    rule = "RULE_CHARSET",
                    message = "Missing charset declaration.",
                    severity = GuardSeverity.WARNING,
                    fixSuggestion = "Add <meta charset=\"UTF-8\"> to <head>"
                )
            )
            workingHtml = injectHeadTag(workingHtml, """<meta charset="UTF-8">""")
        }

        // 4. RULE_VIEWPORT: Mobile-first viewport
        if (!workingHtml.contains("name=\"viewport\"", ignoreCase = true) && !workingHtml.contains("name='viewport'", ignoreCase = true)) {
            issues.add(
                GuardIssue(
                    rule = "RULE_VIEWPORT",
                    message = "Missing mobile viewport meta tag.",
                    severity = GuardSeverity.WARNING,
                    fixSuggestion = "Add mobile viewport meta tag to <head>"
                )
            )
            workingHtml = injectHeadTag(
                workingHtml,
                """<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">"""
            )
        }

        // 5. RULE_BALANCED_TAGS: Check balanced critical tags
        val criticalTags = listOf("script", "style", "html", "body")
        for (tag in criticalTags) {
            val openCount = Regex("""<$tag(\s+[^>]*)?>""", RegexOption.IGNORE_CASE).findAll(workingHtml).count()
            val closeCount = Regex("""</$tag>""", RegexOption.IGNORE_CASE).findAll(workingHtml).count()
            if (openCount != closeCount) {
                issues.add(
                    GuardIssue(
                        rule = "RULE_BALANCED_TAGS",
                        message = "Unbalanced <$tag> tags (opened: $openCount, closed: $closeCount).",
                        severity = GuardSeverity.ERROR,
                        fixSuggestion = "Ensure every <$tag> tag is closed."
                    )
                )
                // Auto-healing for unclosed tags
                if (openCount > closeCount) {
                    val missing = openCount - closeCount
                    repeat(missing) {
                        workingHtml += "\n</$tag>"
                    }
                }
            }
        }

        // 6. RULE_JS_BRACKETS: Check JS bracket balance inside inline <script> blocks
        val scriptBlocks = extractScriptBlocks(workingHtml)
        for ((index, script) in scriptBlocks.withIndex()) {
            val bracketIssue = checkBracketBalance(script)
            if (bracketIssue != null) {
                issues.add(
                    GuardIssue(
                        rule = "RULE_JS_BRACKETS",
                        message = "Script block #${index + 1} has syntax error: $bracketIssue",
                        severity = GuardSeverity.ERROR,
                        fixSuggestion = "Balance brackets in JavaScript code."
                    )
                )
            }
        }

        // 7. RULE_BLOCKING_CALLS: Check and neutralize alert(), confirm(), prompt()
        if (BLOCKING_DIALOG_REGEX.containsMatchIn(workingHtml)) {
            issues.add(
                GuardIssue(
                    rule = "RULE_BLOCKING_CALLS",
                    message = "Detected synchronous alert/confirm/prompt call which freezes Android WebView.",
                    severity = GuardSeverity.WARNING,
                    fixSuggestion = "Polyfilled with non-blocking in-page toast/modal shim."
                )
            )
            workingHtml = injectSafeDialogPolyfill(workingHtml)
        }

        // 8. RULE_WHITELISTED_CDNS & LOCAL FILE REFERENCES
        val scriptSrcMatches = Regex("""<script[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(workingHtml)
        for (match in scriptSrcMatches) {
            val src = match.groupValues[1].trim()
            if (!src.startsWith("http://") && !src.startsWith("https://") && !src.startsWith("//") && !src.startsWith("data:")) {
                issues.add(
                    GuardIssue(
                        rule = "RULE_LOCAL_REFERENCE",
                        message = "Found external local script reference: '$src'. Apps must be self-contained or use public CDN.",
                        severity = GuardSeverity.ERROR,
                        fixSuggestion = "Bundle code inline or use public CDN."
                    )
                )
            } else {
                val domain = extractDomain(src)
                if (domain != null && !isAllowedCdn(domain)) {
                    issues.add(
                        GuardIssue(
                            rule = "RULE_UNTRUSTED_CDN",
                            message = "External CDN '$domain' is not on the recommended CDN list.",
                            severity = GuardSeverity.WARNING,
                            fixSuggestion = "Use cdnjs, unpkg, or jsdelivr."
                        )
                    )
                }
            }
        }

        // 9. RULE_PLACEHOLDER_CODE: Check for placeholder / truncated comments
        if (PLACEHOLDER_CODE_REGEX.containsMatchIn(workingHtml)) {
            issues.add(
                GuardIssue(
                    rule = "RULE_PLACEHOLDER_CODE",
                    message = "Found placeholder comments ('// ... rest of code', 'TODO: implement'). Code must be complete.",
                    severity = GuardSeverity.ERROR,
                    fixSuggestion = "Request full implementation without placeholders."
                )
            )
        }

        // 10. Inject LocalStorage Safe Polyfill (prevents SecurityError crashes)
        if (!workingHtml.contains("saif_storage_polyfill_installed")) {
            workingHtml = injectStoragePolyfill(workingHtml)
        }

        val hasFatalErrors = issues.any { it.severity == GuardSeverity.ERROR }
        return GuardReport(
            isValid = !hasFatalErrors,
            issues = issues,
            sanitizedHtml = workingHtml
        )
    }

    private fun injectSaifMarker(html: String): String {
        val headIdx = html.indexOf("<head>", ignoreCase = true)
        if (headIdx != -1) {
            val insertAt = headIdx + "<head>".length
            return html.substring(0, insertAt) + "\n<!-- saif-ai-build -->" + html.substring(insertAt)
        }
        val headAttrMatch = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (headAttrMatch != null) {
            val insertAt = headAttrMatch.range.last + 1
            return html.substring(0, insertAt) + "\n<!-- saif-ai-build -->" + html.substring(insertAt)
        }
        val htmlMatch = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (htmlMatch != null) {
            val insertAt = htmlMatch.range.last + 1
            return html.substring(0, insertAt) + "\n<head>\n<!-- saif-ai-build -->\n</head>" + html.substring(insertAt)
        }
        return "<!-- saif-ai-build -->\n$html"
    }

    private fun injectHeadTag(html: String, tag: String): String {
        val headIdx = html.indexOf("<head>", ignoreCase = true)
        if (headIdx != -1) {
            val insertAt = headIdx + "<head>".length
            return html.substring(0, insertAt) + "\n" + tag + html.substring(insertAt)
        }
        val headAttrMatch = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (headAttrMatch != null) {
            val insertAt = headAttrMatch.range.last + 1
            return html.substring(0, insertAt) + "\n" + tag + html.substring(insertAt)
        }
        return "$tag\n$html"
    }

    private fun extractScriptBlocks(html: String): List<String> {
        val blocks = mutableListOf<String>()
        val scriptRegex = Regex("""<script(?![^>]*src=)[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        for (match in scriptRegex.findAll(html)) {
            val content = match.groupValues[1]
            if (content.isNotBlank()) {
                blocks.add(content)
            }
        }
        return blocks
    }

    private fun checkBracketBalance(jsCode: String): String? {
        val stack = ArrayDeque<Char>()
        var inSingleQuote = false
        var inDoubleQuote = false
        var inBacktick = false
        var inLineComment = false
        var inBlockComment = false
        var escape = false

        var i = 0
        while (i < jsCode.length) {
            val c = jsCode[i]
            val next = if (i + 1 < jsCode.length) jsCode[i + 1] else ' '

            if (escape) {
                escape = false
                i++
                continue
            }

            if (c == '\\') {
                escape = true
                i++
                continue
            }

            if (inLineComment) {
                if (c == '\n' || c == '\r') inLineComment = false
                i++
                continue
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }

            if (inSingleQuote) {
                if (c == '\'') inSingleQuote = false
                i++
                continue
            }

            if (inDoubleQuote) {
                if (c == '"') inDoubleQuote = false
                i++
                continue
            }

            if (inBacktick) {
                if (c == '`') inBacktick = false
                i++
                continue
            }

            // Check comment openers
            if (c == '/' && next == '/') {
                inLineComment = true
                i += 2
                continue
            }
            if (c == '/' && next == '*') {
                inBlockComment = true
                i += 2
                continue
            }

            // Check string openers
            if (c == '\'') { inSingleQuote = true; i++; continue }
            if (c == '"') { inDoubleQuote = true; i++; continue }
            if (c == '`') { inBacktick = true; i++; continue }

            // Track bracket matching
            when (c) {
                '{', '(', '[' -> stack.addLast(c)
                '}' -> {
                    if (stack.isEmpty() || stack.removeLast() != '{') return "Unexpected closing brace '}'"
                }
                ')' -> {
                    if (stack.isEmpty() || stack.removeLast() != '(') return "Unexpected closing parenthesis ')'"
                }
                ']' -> {
                    if (stack.isEmpty() || stack.removeLast() != '[') return "Unexpected closing bracket ']'"
                }
            }
            i++
        }

        if (stack.isNotEmpty()) {
            return "Unclosed opening delimiter '${stack.last()}'"
        }
        return null
    }

    private fun extractDomain(url: String): String? {
        val clean = url.removePrefix("https://").removePrefix("http://").removePrefix("//")
        val slashIdx = clean.indexOf('/')
        val hostAndPort = if (slashIdx != -1) clean.substring(0, slashIdx) else clean
        return hostAndPort.split(':')[0].lowercase()
    }

    private fun isAllowedCdn(domain: String): Boolean {
        return ALLOWED_CDN_DOMAINS.any { allowed -> domain == allowed || domain.endsWith(".$allowed") }
    }

    private fun injectSafeDialogPolyfill(html: String): String {
        val polyfill = """
        <script id="saif-dialog-shim">
        (function() {
            if (window._saifDialogPolyfill) return;
            window._saifDialogPolyfill = true;
            window.alert = function(msg) {
                console.log("[SAIF Dialog Alert]", msg);
                var toast = document.createElement('div');
                toast.style.cssText = 'position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#1e293b;color:#fff;padding:12px 20px;border-radius:12px;font-family:sans-serif;font-size:14px;box-shadow:0 8px 24px rgba(0,0,0,0.3);z-index:999999;transition:opacity 0.3s;max-width:85vw;text-align:center;pointer-events:none;';
                toast.textContent = msg || '';
                document.body ? document.body.appendChild(toast) : window.addEventListener('DOMContentLoaded', function(){ document.body.appendChild(toast); });
                setTimeout(function(){ toast.style.opacity = '0'; setTimeout(function(){ if(toast.parentNode) toast.parentNode.removeChild(toast); }, 350); }, 3000);
            };
            window.confirm = function(msg) {
                console.log("[SAIF Dialog Confirm Auto-Approved]", msg);
                return true;
            };
            window.prompt = function(msg, def) {
                console.log("[SAIF Dialog Prompt Fallback]", msg);
                return def || "";
            };
        })();
        </script>
        """.trimIndent()
        return injectHeadTag(html, polyfill)
    }

    private fun injectStoragePolyfill(html: String): String {
        val polyfill = """
        <script id="saif-storage-shim">
        (function() {
            window.saif_storage_polyfill_installed = true;
            try {
                var testKey = '__saif_test_storage__';
                window.localStorage.setItem(testKey, testKey);
                window.localStorage.removeItem(testKey);
            } catch(e) {
                console.warn("[SAIF] In-memory fallback for localStorage activated.");
                var memStore = {};
                window.localStorage = {
                    getItem: function(k) { return memStore.hasOwnProperty(k) ? memStore[k] : null; },
                    setItem: function(k, v) { memStore[k] = String(v); },
                    removeItem: function(k) { delete memStore[k]; },
                    clear: function() { memStore = {}; },
                    get length() { return Object.keys(memStore).length; },
                    key: function(i) { return Object.keys(memStore)[i] || null; }
                };
            }
        })();
        </script>
        """.trimIndent()
        return injectHeadTag(html, polyfill)
    }
}
