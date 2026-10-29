package com.example.builder

import android.content.Context
import android.util.Log
import com.example.compiler.CompilerErrorInfo
import com.example.ui.components.CodeEditorViewModel
import com.example.ui.components.ProjectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Autonomous Full-Stack App Builder Engine for SAIF AI Studio.
 * Generates and edits complete, self-contained, interactive HTML5/CSS3/JS mobile applications
 * directly executing in Android WebView (base_runner container).
 * Strictly operates without hardcoded mock templates or silent fallbacks.
 */
data class AutonomousBuildResult(
    val isSuccess: Boolean,
    val summary: String,
    val updatedFiles: List<String>,
    val rawResponse: String = "",
    val error: String? = null
)

object AutonomousAppBuilder {
    private const val TAG = "AutonomousAppBuilder"

    const val BUILDER_SYSTEM_PROMPT = """You are Saif AI Build, a world-class product designer and senior front-end engineer. The user gives a short or vague idea (English, Hindi or Hinglish). Turn it into a complete, working, beautiful app. Never ask questions; fill the gaps like a senior product team would.
THINK FIRST: work out what the user really wants, then decide the purpose, features, screens and a design direction unique to THIS idea. Never reuse a generic template: a different idea must get a different layout, palette, typography, motion and feature set.
REAL FUNCTIONALITY: working state, interactions, validation, empty states, realistic sample data. Persist with localStorage wrapped in try/catch with an in-memory fallback.
DESIGN: premium modern UI, strong hierarchy, consistent spacing, a curated palette (avoid default blue-purple gradients), 1-2 Google Fonts. It runs inside an Android WebView: mobile-first, touch-friendly (44px targets), 100dvh layouts, safe-area padding, no hover-only interactions, no alert/confirm/prompt (use in-page modals and toasts). Use inline SVG, CSS shapes or emoji instead of external images.
ANIMATION (always high): staggered entrances, scroll reveals, press micro-interactions, animated screen transitions, animated numbers and charts, subtle moving backgrounds. 60fps via transform/opacity; respect prefers-reduced-motion.
OUTPUT: ONLY the raw HTML document starting with <!DOCTYPE html>. Put <!-- saif-ai-build --> right after <head>. All CSS/JS inline; external libraries only as classic <script> tags from a CDN (no ES-module imports, no local fetch), only when useful. No markdown, no commentary, never truncate or write "rest of code here". It must run as-is with zero console errors.
EDITING: if the input has CURRENT_CODE and CHANGE_REQUEST, apply exactly that change, keep everything else intact, and return the FULL updated HTML."""

    /**
     * Executes the autonomous app builder pipeline via BuildAgent.
     */
    suspend fun buildOrModifyApp(
        context: Context,
        project: ProjectData,
        prompt: String,
        editorViewModel: CodeEditorViewModel? = null,
        isGlitchFix: Boolean = false,
        errorInfo: CompilerErrorInfo? = null,
        base64Images: List<String> = emptyList(),
        onProgressUpdate: suspend (statusText: String, stage: Int) -> Unit = { _, _ -> }
    ): AutonomousBuildResult = withContext(Dispatchers.IO) {
        val sanitizedAppName = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val projectDir = File(context.filesDir, "projects/$sanitizedAppName").apply { mkdirs() }
        ensureAndroidContainerStubs(projectDir, project)

        val fullPrompt = if (errorInfo != null) {
            "Fix runtime glitch: ${errorInfo.errorMessage} in ${errorInfo.fileName}:${errorInfo.lineNumber}\n${errorInfo.codeSnippet}\n$prompt"
        } else {
            prompt
        }

        var latestSummary = ""
        val updatedFiles = mutableListOf<String>()

        val result = com.example.builder.agent.BuildAgent.run(
            context = context,
            project = project,
            prompt = fullPrompt,
            qualityMode = if (isGlitchFix) com.example.builder.agent.QualityMode.BALANCED else com.example.builder.agent.QualityMode.MAX,
            base64Images = base64Images,
            onEvent = { event ->
                when (event) {
                    is com.example.builder.agent.BuildEvent.PhaseStarted -> {
                        val stageNum = when (event.phase) {
                            com.example.builder.agent.BuildPhase.S0_INTAKE,
                            com.example.builder.agent.BuildPhase.S1_SPEC,
                            com.example.builder.agent.BuildPhase.S2_PLAN -> 1
                            com.example.builder.agent.BuildPhase.S3_GENERATE -> 2
                            com.example.builder.agent.BuildPhase.S4_ASSEMBLE,
                            com.example.builder.agent.BuildPhase.S5_SANDBOX,
                            com.example.builder.agent.BuildPhase.S6_FIX,
                            com.example.builder.agent.BuildPhase.S7_POLISH,
                            com.example.builder.agent.BuildPhase.S8_COMMIT -> 3
                            com.example.builder.agent.BuildPhase.S9_REPORT -> 4
                        }
                        onProgressUpdate("  ${event.phase.displayName} (${event.index}/${event.total})...", stageNum)
                    }
                    is com.example.builder.agent.BuildEvent.Thought -> {
                        onProgressUpdate("  ${event.summary}", 1)
                    }
                    is com.example.builder.agent.BuildEvent.TaskStarted -> {
                        onProgressUpdate("  Task ${event.id}: ${event.title}...", 2)
                    }
                    is com.example.builder.agent.BuildEvent.Writing -> {
                        onProgressUpdate("  Writing ${event.file} (${event.approxBytes} B)...", 2)
                    }
                    is com.example.builder.agent.BuildEvent.Editing -> {
                        onProgressUpdate("  Patching ${event.file} (${event.hunks} hunks)...", 2)
                    }
                    is com.example.builder.agent.BuildEvent.StaticCheck -> {
                        val status = if (event.passed) "Static checks passed" else "Found issues: ${event.issues.size}"
                        onProgressUpdate("  $status", 3)
                    }
                    is com.example.builder.agent.BuildEvent.SandboxRun -> {
                        onProgressUpdate("  Sandbox testing: ${event.passedCount}/${event.totalCount} checks passed", 3)
                    }
                    is com.example.builder.agent.BuildEvent.Fixing -> {
                        onProgressUpdate("  Auto-fixing in sandbox (Round ${event.round}): ${event.issueSummary}", 3)
                    }
                    is com.example.builder.agent.BuildEvent.Waiting -> {
                        onProgressUpdate("  Waiting for rate-limit window (${event.seconds}s): ${event.reason}", 2)
                    }
                    is com.example.builder.agent.BuildEvent.Committed -> {
                        updatedFiles.add("index.html")
                        updatedFiles.add("app/src/main/assets/index.html")
                        try { editorViewModel?.refreshTree(context) } catch (ignored: Exception) {}
                        onProgressUpdate("  Committed version v${String.format("%04d", event.version)} to disk", 4)
                    }
                    is com.example.builder.agent.BuildEvent.ReportReady -> {
                        latestSummary = event.summary
                        onProgressUpdate("  App Ready! Preview & APK available.", 4)
                    }
                    is com.example.builder.agent.BuildEvent.Failed -> {
                        onProgressUpdate("  Build failed: ${event.userMessage}", 4)
                    }
                    else -> {}
                }
            }
        )

        if (result.isSuccess) {
            val rootHtml = File(projectDir, "index.html")
            val raw = if (rootHtml.exists()) rootHtml.readText() else ""
            AutonomousBuildResult(
                isSuccess = true,
                summary = latestSummary.ifBlank { "Built '${project.appName}' successfully!" },
                updatedFiles = if (updatedFiles.isNotEmpty()) updatedFiles else listOf("index.html", "app/src/main/assets/index.html"),
                rawResponse = raw,
                error = null
            )
        } else {
            val errMsg = result.exceptionOrNull()?.message ?: "Build failed"
            AutonomousBuildResult(
                isSuccess = false,
                summary = "  Build Failed: $errMsg",
                updatedFiles = emptyList(),
                rawResponse = "",
                error = errMsg
            )
        }
    }

    /**
     * Extracts a self-contained HTML document starting at <!DOCTYPE or <html and ending at </html>.
     * Returns null if no opening or closing tag is found (indicating truncation or invalid format).
     */
    fun extractHtmlDocument(rawOutput: String): String? {
        if (rawOutput.isBlank()) return null
        val lower = rawOutput.lowercase()
        val docTypeIdx = lower.indexOf("<!doctype html")
        val htmlIdx = lower.indexOf("<html")

        val startIdx = when {
            docTypeIdx != -1 && htmlIdx != -1 -> Math.min(docTypeIdx, htmlIdx)
            docTypeIdx != -1 -> docTypeIdx
            htmlIdx != -1 -> htmlIdx
            else -> -1
        }

        val endClosing = "</html>"
        val endIdx = lower.lastIndexOf(endClosing)

        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            return null
        }

        return rawOutput.substring(startIdx, endIdx + endClosing.length).trim()
    }

    /**
     * Heals an almost complete HTML document cut off before closing tags.
     * Safely closes open <script>, <style>, <body>, and <html> tags.
     */
    fun healTruncatedHtml(rawOutput: String): String? {
        if (rawOutput.isBlank() || rawOutput.length < 150) return null
        val lower = rawOutput.lowercase()
        val docTypeIdx = lower.indexOf("<!doctype html")
        val htmlIdx = lower.indexOf("<html")
        val startIdx = when {
            docTypeIdx != -1 && htmlIdx != -1 -> Math.min(docTypeIdx, htmlIdx)
            docTypeIdx != -1 -> docTypeIdx
            htmlIdx != -1 -> htmlIdx
            else -> -1
        }
        if (startIdx == -1) return null

        var trimmed = rawOutput.substring(startIdx).trim()
        val lowerTrimmed = trimmed.lowercase()

        // Must have at least an opening body tag or head tag
        if (!lowerTrimmed.contains("<body") && !lowerTrimmed.contains("<head")) return null

        val openScriptCount = Regex("<script[^>]*>", RegexOption.IGNORE_CASE).findAll(trimmed).count()
        val closeScriptCount = Regex("</script>", RegexOption.IGNORE_CASE).findAll(trimmed).count()
        if (openScriptCount > closeScriptCount) {
            trimmed += "\n</script>"
        }

        val openStyleCount = Regex("<style[^>]*>", RegexOption.IGNORE_CASE).findAll(trimmed).count()
        val closeStyleCount = Regex("</style>", RegexOption.IGNORE_CASE).findAll(trimmed).count()
        if (openStyleCount > closeStyleCount) {
            trimmed += "\n</style>"
        }

        if (!trimmed.lowercase().contains("</body>")) {
            trimmed += "\n</body>"
        }
        if (!trimmed.lowercase().contains("</html>")) {
            trimmed += "\n</html>"
        }

        return if (trimmed.endsWith("</html>", ignoreCase = true)) trimmed else null
    }

    /**
     * Ensures the marker <!-- saif-ai-build --> is present right after <head> or at the top of <html.
     */
    private fun ensureSaifBuildTag(html: String): String {
        if (html.contains("<!-- saif-ai-build -->")) return html
        val headIdx = html.indexOf("<head>", ignoreCase = true)
        if (headIdx != -1) {
            val insertAt = headIdx + "<head>".length
            return html.substring(0, insertAt) + "\n<!-- saif-ai-build -->" + html.substring(insertAt)
        }
        val headWithAttrIdx = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (headWithAttrIdx != null) {
            val insertAt = headWithAttrIdx.range.last + 1
            return html.substring(0, insertAt) + "\n<!-- saif-ai-build -->" + html.substring(insertAt)
        }
        val htmlTagIdx = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (htmlTagIdx != null) {
            val insertAt = htmlTagIdx.range.last + 1
            return html.substring(0, insertAt) + "\n<head>\n<!-- saif-ai-build -->\n</head>" + html.substring(insertAt)
        }
        return "<!-- saif-ai-build -->\n$html"
    }

    /**
     * Ensures stub MainActivity and AndroidManifest.xml exist in projectDir
     * so that packaging into standalone APK can proceed cleanly.
     */
    fun ensureAndroidContainerStubs(projectDir: File, project: ProjectData) {
        try {
            val pkgPath = project.packageName.replace('.', '/')
            val javaDir = File(projectDir, "app/src/main/java/$pkgPath").apply { mkdirs() }
            val mainActivityFile = File(javaDir, "MainActivity.java")
            if (!mainActivityFile.exists()) {
                mainActivityFile.writeText(
                    """
                    package ${project.packageName};
                    import android.app.Activity;
                    import android.os.Bundle;

                    public class MainActivity extends Activity {
                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                        }
                    }
                    """.trimIndent()
                )
            }

            val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
            if (!manifestFile.exists()) {
                manifestFile.parentFile?.mkdirs()
                manifestFile.writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="${project.packageName}">
                        <uses-permission android:name="android.permission.INTERNET" />
                        <application
                            android:allowBackup="true"
                            android:icon="@mipmap/ic_launcher"
                            android:label="${project.appName}"
                            android:theme="@android:style/Theme.DeviceDefault">
                            <activity
                                android:name=".MainActivity"
                                android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                    """.trimIndent()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating container stubs", e)
        }
    }
}
