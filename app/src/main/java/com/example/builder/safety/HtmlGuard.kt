package com.example.builder.safety

import java.util.regex.Pattern

data class HtmlGuardResult(
    val isValid: Boolean,
    val issues: List<String>
)

/**
 * Strict static analyzer for generated HTML5 mobile applications.
 * Ensures the document is well-formed, safe, free of leaked model thoughts,
 * and completely self-contained for Android WebView.
 */
object HtmlGuard {

    private const val MAX_SIZE_BYTES = 400 * 1024 // 400 KB

    private val ALLOWED_CDN_DOMAINS = listOf(
        "cdnjs.cloudflare.com",
        "cdn.jsdelivr.net",
        "unpkg.com",
        "fonts.googleapis.com",
        "fonts.gstatic.com"
    )

    private val REASONING_PHRASES = listOf(
        "we need to",
        "let's",
        "the user wants",
        "maybe we",
        "we should",
        "i think"
    )

    private val BANNED_TOKENS = listOf(
        "<think>",
        "</think>",
        "<|channel|>",
        "<|message|>",
        "<|end|>",
        "assistantfinal"
    )

    fun validate(html: String): HtmlGuardResult {
        val issues = mutableListOf<String>()
        val trimmed = html.trim()

        if (trimmed.isEmpty()) {
            return HtmlGuardResult(false, listOf("HTML output is completely empty."))
        }

        // 1. Size check
        if (trimmed.toByteArray(Charsets.UTF_8).size > MAX_SIZE_BYTES) {
            issues.add("Document size exceeds maximum limit of 400 KB (${trimmed.length / 1024} KB).")
        }

        // 2. Starts with <!DOCTYPE html>
        if (!trimmed.startsWith("<!DOCTYPE html", ignoreCase = true)) {
            issues.add("Document must begin with <!DOCTYPE html> declaration.")
        }

        // 3. Exactly one html, head, body tag
        val lower = trimmed.lowercase()
        if (!lower.contains("<html") || !lower.contains("</html>")) {
            issues.add("Missing <html> or </html> tag.")
        }
        if (!lower.contains("<head") || !lower.contains("</head>")) {
            issues.add("Missing <head> or </head> tag.")
        }
        if (!lower.contains("<body") || !lower.contains("</body>")) {
            issues.add("Missing <body> or </body> tag.")
        }

        // 4. Balanced script and style tags
        val openScripts = countOccurrences(lower, "<script")
        val closeScripts = countOccurrences(lower, "</script>")
        if (openScripts != closeScripts) {
            issues.add("Unbalanced <script> tags: $openScripts opened vs $closeScripts closed.")
        }

        val openStyles = countOccurrences(lower, "<style")
        val closeStyles = countOccurrences(lower, "</style>")
        if (openStyles != closeStyles) {
            issues.add("Unbalanced <style> tags: $openStyles opened vs $closeStyles closed.")
        }

        // 5. CSS brace balance
        val styleBlocks = extractTagContents(trimmed, "style")
        for (style in styleBlocks) {
            val openBraces = countOccurrences(style, "{")
            val closeBraces = countOccurrences(style, "}")
            if (openBraces != closeBraces) {
                issues.add("CSS syntax error: Unbalanced braces in <style> block ($openBraces '{' vs $closeBraces '}').")
            }
        }

        // 6. CDN scripts allowlist & no ES modules
        val scriptSrcPattern = Pattern.compile("""<script[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val matcher = scriptSrcPattern.matcher(trimmed)
        while (matcher.find()) {
            val src = matcher.group(1) ?: ""
            if (src.startsWith("http://") || src.startsWith("https://")) {
                val isAllowed = ALLOWED_CDN_DOMAINS.any { domain -> src.contains(domain, ignoreCase = true) }
                if (!isAllowed) {
                    issues.add("Unauthorized external script CDN: '$src'. Only trusted CDNs ($ALLOWED_CDN_DOMAINS) are allowed.")
                }
            }
        }

        // Ban ES-module imports
        if (lower.contains("type=\"module\"") || lower.contains("type='module'")) {
            issues.add("ES-module imports (<script type=\"module\">) are forbidden in WebView. Use classic scripts only.")
        }

        // 7. Ban alert(), confirm(), prompt() in scripts
        val jsBlocks = extractTagContents(trimmed, "script")
        for (js in jsBlocks) {
            if (Pattern.compile("""\balert\s*\(""", Pattern.CASE_INSENSITIVE).matcher(js).find()) {
                issues.add("alert() is forbidden. Use styled in-page toasts or modal dialogs.")
            }
            if (Pattern.compile("""\bconfirm\s*\(""", Pattern.CASE_INSENSITIVE).matcher(js).find()) {
                issues.add("confirm() is forbidden. Use styled in-page modal dialogs.")
            }
            if (Pattern.compile("""\bprompt\s*\(""", Pattern.CASE_INSENSITIVE).matcher(js).find()) {
                issues.add("prompt() is forbidden. Use in-page input modals.")
            }

            // localStorage must be protected by try/catch
            if (js.contains("localStorage") && !js.contains("try") && !js.contains("catch")) {
                issues.add("localStorage access must be wrapped in try/catch with in-memory fallback for WebView safety.")
            }
        }

        // 8. Placeholders check
        val bannedPlaceholders = listOf("TODO:", "TODO ", "rest of code here", "lorem ipsum dolor")
        for (p in bannedPlaceholders) {
            if (lower.contains(p.lowercase())) {
                issues.add("Found incomplete placeholder in code: '$p'.")
            }
        }

        // 9. Hard-banned model tokens
        for (token in BANNED_TOKENS) {
            if (lower.contains(token.lowercase())) {
                issues.add("Leaked internal model token found: '$token'.")
            }
        }

        // 10. Reasoning-prose detector on visible DOM text (outside script/style)
        val visibleDomText = stripScriptsAndStyles(trimmed)
        val proseNodes = visibleDomText.split(Regex("""<[^>]+>""")).map { it.trim() }.filter { it.length > 200 }
        for (node in proseNodes) {
            val lowerNode = node.lowercase()
            val matchedPhrases = REASONING_PHRASES.count { lowerNode.contains(it) }
            if (matchedPhrases >= 3) {
                issues.add("Leaked chain-of-thought reasoning prose detected in DOM: \"${node.take(90)}...\"")
                break
            }
        }

        return HtmlGuardResult(isValid = issues.isEmpty(), issues = issues)
    }

    private fun countOccurrences(text: String, sub: String): Int {
        var count = 0
        var idx = 0
        while (text.indexOf(sub, idx).also { idx = it } != -1) {
            count++
            idx += sub.length
        }
        return count
    }

    private fun extractTagContents(html: String, tag: String): List<String> {
        val list = mutableListOf<String>()
        val pattern = Pattern.compile("""<$tag[^>]*>([\s\S]*?)</$tag>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        while (matcher.find()) {
            matcher.group(1)?.let { list.add(it) }
        }
        return list
    }

    private fun stripScriptsAndStyles(html: String): String {
        return html
            .replace(Regex("""<script[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<style[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
    }
}
