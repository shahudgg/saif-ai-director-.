package com.example.builder

import android.content.Context
import android.util.Log
import com.example.data.remote.AIApiUtility

data class FixAttemptResult(
    val isFixed: Boolean,
    val html: String,
    val roundsAttempted: Int,
    val remainingErrors: List<String>
)

/**
 * Self-Correction & Auto-Healing Engine for SAIF Build.
 * When static analysis (HtmlGuard) or sandbox probes (SandboxRunner) identify
 * runtime errors, unclosed tags, or broken element IDs, BuildFixer autonomously
 * synthesizes surgical fixes with a strict multi-round budget (up to 3 rounds).
 */
object BuildFixer {
    private const val TAG = "BuildFixer"
    const val MAX_FIX_ROUNDS = 3

    suspend fun autoFix(
        context: Context?,
        initialHtml: String,
        errors: List<String>,
        base64Images: List<String> = emptyList(),
        onProgress: suspend (round: Int, status: String) -> Unit = { _, _ -> }
    ): FixAttemptResult {
        if (errors.isEmpty()) {
            return FixAttemptResult(
                isFixed = true,
                html = initialHtml,
                roundsAttempted = 0,
                remainingErrors = emptyList()
            )
        }

        var currentHtml = initialHtml
        var currentErrors = errors

        for (round in 1..MAX_FIX_ROUNDS) {
            Log.i(TAG, "Starting Auto-Fix Round $round of $MAX_FIX_ROUNDS for ${currentErrors.size} errors")
            onProgress(round, "🔧 Auto-healing build issue (Round $round/$MAX_FIX_ROUNDS)...")

            val errorBulletPoints = currentErrors.mapIndexed { idx, err -> "${idx + 1}. $err" }.joinToString("\n")
            val fixPrompt = """
CURRENT_HTML_CODE:
$currentHtml

VALIDATION_ERRORS_DETECTED:
$errorBulletPoints

CRITICAL INSTRUCTIONS:
1. Surgically fix all the validation/runtime errors listed above.
2. Ensure every document.getElementById/querySelector in JavaScript has a matching element in HTML.
3. Ensure all <script>, <style>, and <div> tags are properly closed and all JS brackets are balanced.
4. Keep all existing design, animations, features, and functionality completely intact.
5. Return ONLY the full, complete HTML document starting with <!DOCTYPE html> and ending with </html>.
            """.trimIndent()

            val callResult = AIApiUtility.executeWithFallback(
                prompt = fixPrompt,
                mode = "build",
                customSystemPrompt = AutonomousAppBuilder.BUILDER_SYSTEM_PROMPT,
                base64Images = base64Images
            )

            if (!callResult.isSuccess) {
                Log.w(TAG, "Round $round LLM call failed: ${callResult.exceptionOrNull()?.message}")
                continue
            }

            val rawOutput = callResult.getOrNull() ?: ""
            val candidate = AutonomousAppBuilder.extractHtmlDocument(rawOutput)
                ?: AutonomousAppBuilder.healTruncatedHtml(rawOutput)

            if (candidate.isNullOrBlank()) {
                Log.w(TAG, "Round $round produced no extractable HTML")
                continue
            }

            // Verify with HtmlGuard
            val guardReport = HtmlGuard.inspect(candidate)
            val guardErrors = guardReport.errors.map { "${it.rule}: ${it.message}" }

            // Verify with SandboxRunner
            val sandboxReport = SandboxRunner.evaluate(context, guardReport.sanitizedHtml)
            val combinedErrors = (guardErrors + sandboxReport.errors).distinct()

            currentHtml = guardReport.sanitizedHtml
            currentErrors = combinedErrors

            if (combinedErrors.isEmpty()) {
                Log.i(TAG, "Successfully healed all errors in Round $round!")
                return FixAttemptResult(
                    isFixed = true,
                    html = currentHtml,
                    roundsAttempted = round,
                    remainingErrors = emptyList()
                )
            }
        }

        return FixAttemptResult(
            isFixed = false,
            html = currentHtml,
            roundsAttempted = MAX_FIX_ROUNDS,
            remainingErrors = currentErrors
        )
    }
}
