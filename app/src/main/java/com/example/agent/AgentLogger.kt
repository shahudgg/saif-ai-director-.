package com.example.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

object AgentLogger {
    const val TAG = "SAIF_AGENT"
    private var traceDir: File? = null

    fun init(context: Context) {
        try {
            val dir = File(context.filesDir, "agent_traces")
            if (!dir.exists()) dir.mkdirs()
            traceDir = dir
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize trace directory: ${e.message}")
        }
    }

    fun redactSensitive(input: String): String {
        var text = input
        // Mask OTP codes (4 to 8 digits)
        text = text.replace(Regex("""\b(\d{4,8})\b"""), "[OTP_REDACTED]")
        // Mask Card numbers
        text = text.replace(Regex("""\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b"""), "[CARD_REDACTED]")
        // Mask passwords in key-value patterns
        text = text.replace(Regex("""(?i)(password|passwd|pin)\s*[:=]\s*\S+"""), "$1=[REDACTED]")
        return text
    }

    fun log(message: String) {
        val safe = redactSensitive(message)
        Log.i(TAG, safe)
    }

    fun logStep(goal: String, step: Int, intent: String, actionName: String, result: ActionResult, durationMs: Long, fingerprint: String) {
        val safeIntent = redactSensitive(intent)
        val safeDetail = redactSensitive(result.detail)
        val msg = "Step $step | Goal: $goal | Intent: $safeIntent | Action: $actionName | OK: ${result.ok} | Detail: $safeDetail | Duration: ${durationMs}ms | FP: $fingerprint"
        Log.i(TAG, msg)

        // Write trace record asynchronously to rolling log
        traceDir?.let { dir ->
            try {
                val record = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("goal", redactSensitive(goal))
                    put("step", step)
                    put("intent", safeIntent)
                    put("action", actionName)
                    put("ok", result.ok)
                    put("detail", safeDetail)
                    put("durationMs", durationMs)
                    put("fingerprint", fingerprint)
                }
                val file = File(dir, "trace_${System.currentTimeMillis() / (1000 * 60 * 60 * 24)}.jsonl")
                FileWriter(file, true).use { writer ->
                    writer.write(record.toString() + "\n")
                }
            } catch (e: Exception) {
                // Ignore trace file write failures
            }
        }
    }
}
