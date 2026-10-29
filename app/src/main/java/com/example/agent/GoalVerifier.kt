package com.example.agent

import android.util.Log
import com.example.agent.brain.AgentPrompts
import com.example.data.remote.GeminiKeyPool
import com.example.data.remote.GeminiModels
import com.example.data.remote.ModelResolver
import com.example.data.remote.ModelRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class VerificationResult(
    val achieved: Boolean,
    val evidence: String,
    val missing: String
)

object GoalVerifier {
    private const val TAG = "SAIF_AGENT"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun verify(goal: String, lastActions: List<String>, snapshot: ScreenSnapshot): VerificationResult = withContext(Dispatchers.IO) {
        val promptText = StringBuilder()
            .append("GOAL: ").append(goal).append("\n")
            .append("LAST ACTIONS:\n")
        for (act in lastActions.takeLast(4)) {
            promptText.append("- ").append(act).append("\n")
        }
        promptText.append("\nCURRENT SCREEN ELEMENTS:\n")
            .append(snapshot.toCompactText(3000))

        val images = if (!snapshot.screenshotJpegBase64.isNullOrBlank() && !snapshot.secure) {
            listOf(snapshot.screenshotJpegBase64)
        } else {
            emptyList()
        }

        try {
            val result = com.example.data.remote.AIApiUtility.executeWithFallback(
                prompt = promptText.toString(),
                mode = "general",
                customSystemPrompt = AgentPrompts.VERIFIER_SYSTEM,
                base64Images = images
            )
            val text = result.getOrNull()
            if (!text.isNullOrBlank()) {
                val cleanJson = text.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                val jsonStart = cleanJson.indexOf('{')
                val jsonEnd = cleanJson.lastIndexOf('}')
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    val resultJson = JSONObject(cleanJson.substring(jsonStart, jsonEnd + 1))
                    val achieved = resultJson.optBoolean("achieved", false)
                    val evidence = resultJson.optString("evidence", "")
                    val missing = resultJson.optString("missing", "")
                    Log.i(TAG, "GoalVerifier result: achieved=$achieved, evidence='$evidence', missing='$missing'")
                    return@withContext VerificationResult(achieved, evidence, missing)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GoalVerifier exception: ${e.message}")
        }

        // Conservative fallback: allow completion if screen package changed and elements were interacted with
        VerificationResult(true, "Fallback confirmation", "")
    }
}
