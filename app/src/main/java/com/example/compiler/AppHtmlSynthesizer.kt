package com.example.compiler

import com.example.ui.components.ProjectData
import java.io.File

/**
 * Synthesizes and provides standalone HTML5/CSS3/JavaScript mobile applications
 * for projects built with SAIF AI Studio.
 *
 * Returns the exact index.html created by Saif AI Build without falling back
 * to hardcoded templates.
 */
object AppHtmlSynthesizer {

    /**
     * Synthesizes the full HTML code for a project.
     * Returns the existing index.html (after stripAuthGate).
     * If no index.html exists, throws a clear error ("Build the app first").
     */
    fun synthesize(project: ProjectData, projectDir: File, forceRegenerate: Boolean = false): String {
        val rootHtml = File(projectDir, "index.html")
        val assetHtml = File(projectDir, "app/src/main/assets/index.html")

        val htmlContent = when {
            rootHtml.exists() && rootHtml.length() > 50 -> rootHtml.readText()
            assetHtml.exists() && assetHtml.length() > 50 -> assetHtml.readText()
            else -> null
        }

        if (htmlContent.isNullOrBlank() || !htmlContent.contains("<html", ignoreCase = true)) {
            throw IllegalStateException("Build the app first. No valid index.html was found in '${project.appName}'.")
        }

        return stripAuthGate(htmlContent)
    }

    /**
     * Strips legacy authentication overlay elements if present.
     */
    fun stripAuthGate(html: String): String {
        if (!html.contains("saif-auth-overlay") && !html.contains("saif_app_auth_session")) return html
        return html
            .replace(Regex("""<!-- ==================== SAIF AI AUTHENTICATION GATE ==================== -->[\s\S]*?<!-- ==================== END SAIF AI AUTHENTICATION GATE ==================== -->"""), "")
            .replace(Regex("""<style>[\s\S]*?#saif-auth-overlay[\s\S]*?</style>"""), "")
            .replace(Regex("""<div id="saif-auth-overlay">[\s\S]*?</div>\s*</div>"""), "")
            .replace(Regex("""<div id="saif-auth-badge"[\s\S]*?</div>"""), "")
            .replace(Regex("""<script>[\s\S]*?saif_app_auth_session[\s\S]*?</script>"""), "")
            .trim()
    }

    fun wrapWithAuthGate(baseHtml: String, appName: String): String {
        return stripAuthGate(baseHtml)
    }
}
