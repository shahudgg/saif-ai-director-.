package com.example.agent.brain

import android.util.Log
import com.example.agent.ActionNormalizer
import com.example.agent.AgentCall
import com.example.agent.AgentOutcome
import com.example.agent.PlaybookStore
import com.example.agent.RateGovernor
import com.example.agent.ScreenSnapshot
import com.example.agent.StepRecord
import com.example.agent.ToolSchema
import com.example.data.local.ProviderSettingsManager
import com.example.data.remote.GeminiKeyPool
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

object FunctionCallingBrain : AgentBrain {
    private const val TAG = "SAIF_AGENT"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    override suspend fun decide(
        task: String,
        sessionCtx: String,
        notes: String,
        history: List<StepRecord>,
        snapshot: ScreenSnapshot,
        thinkingLevel: String
    ): BrainDecision = withContext(Dispatchers.IO) {
        RateGovernor.acquire()

        val state = ProviderSettingsManager.loadState()
        val isGemini = state.providerName.equals("Gemini", ignoreCase = true)
        val key = if (isGemini) GeminiKeyPool.getNextKey() else null
        if (key.isNullOrBlank()) {
            return@withContext decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
        }

        val model = ModelResolver.resolve(ModelRole.AGENT)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        try {
            val root = JSONObject()

            // 1. System Prompt
            val sysPart = JSONObject().put("text", AgentPrompts.BRAIN_SYSTEM)
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(sysPart)))

            // 2. Canonical Tools
            val toolsArray = JSONArray().put(JSONObject().put("functionDeclarations", ToolSchema.getGeminiFunctionDeclarations()))
            root.put("tools", toolsArray)

            // 3. User Message Parts
            val parts = JSONArray()
            val textBuilder = StringBuilder()
            textBuilder.append("GOAL: ").append(task).append("\n")
            if (sessionCtx.isNotBlank()) textBuilder.append("SESSION: ").append(sessionCtx).append("\n")
            if (notes.isNotBlank()) textBuilder.append("NOTES: ").append(notes).append("\n")

            val playbook = PlaybookStore.getHint(snapshot.packageName)
            if (!playbook.isNullOrBlank()) {
                textBuilder.append("APP PLAYBOOK: ").append(playbook).append("\n")
            }

            textBuilder.append("HISTORY (last ").append(history.takeLast(6).size).append(" steps):\n")
            for (step in history.takeLast(6)) {
                textBuilder.append("- Step ${step.step}: ${step.call.name} -> ${if (step.result.ok) "OK" else "FAIL"}: ${step.result.detail}\n")
            }

            textBuilder.append("\nCURRENT SCREEN:\n")
            textBuilder.append(snapshot.toCompactText(4500))
            parts.put(JSONObject().put("text", textBuilder.toString()))

            // Add screenshot image
            if (!snapshot.screenshotJpegBase64.isNullOrBlank() && !snapshot.secure) {
                parts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", snapshot.screenshotJpegBase64)
                    })
                })
            }

            root.put("contents", JSONArray().put(JSONObject().put("parts", parts)))

            // 4. Generation Config
            val genConfig = JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 600)
            }
            root.put("generationConfig", genConfig)

            val reqBody = root.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(reqBody).build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            response.close()

            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini brain call returned HTTP ${response.code}: $bodyString")
                return@withContext decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
            }

            val respJson = JSONObject(bodyString)
            val candidate = respJson.optJSONArray("candidates")?.optJSONObject(0)
            val contentParts = candidate?.optJSONObject("content")?.optJSONArray("parts")

            if (contentParts == null || contentParts.length() == 0) {
                return@withContext decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
            }

            val calls = mutableListOf<AgentCall>()
            var thought = ""

            for (i in 0 until contentParts.length()) {
                val part = contentParts.getJSONObject(i)
                if (part.has("text")) {
                    thought += part.getString("text") + " "
                }
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val callName = fc.getString("name")
                    val callArgs = fc.optJSONObject("args") ?: JSONObject()
                    val callId = "call_${System.currentTimeMillis()}_$i"
                    val intent = callArgs.optString("intent", "")
                    val rawCall = AgentCall(callId, callName, callArgs, intent)
                    calls.add(ActionNormalizer.normalize(rawCall))
                }
            }

            if (calls.isEmpty()) {
                return@withContext decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
            }

            // Check if model emitted finish or yield
            val finishCall = calls.firstOrNull { it.name == "finish" }
            if (finishCall != null) {
                val success = finishCall.arguments.optBoolean("success", true)
                val summary = finishCall.arguments.optString("summary", "Task completed")
                return@withContext BrainDecision(
                    calls = listOf(finishCall),
                    thought = thought.trim(),
                    finished = true,
                    outcome = AgentOutcome(success, summary, history.size + 1, 0, data = finishCall.arguments.opt("data"))
                )
            }

            val yieldCall = calls.firstOrNull { it.name == "yield_to_user" }
            if (yieldCall != null) {
                val reason = yieldCall.arguments.optString("reason", "Manual user action required")
                return@withContext BrainDecision(
                    calls = listOf(yieldCall),
                    thought = thought.trim(),
                    finished = true,
                    outcome = AgentOutcome(false, reason, history.size + 1, 0)
                )
            }

            BrainDecision(calls = calls, thought = thought.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error in FunctionCallingBrain: ${e.message}", e)
            decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
        }
    }

    suspend fun decideWithUniversalApi(
        task: String,
        sessionCtx: String,
        notes: String,
        history: List<StepRecord>,
        snapshot: ScreenSnapshot
    ): BrainDecision = withContext(Dispatchers.IO) {
        val prompt = StringBuilder()
        prompt.append("You are the autonomous Android phone agent brain.\n")
        prompt.append("GOAL: ").append(task).append("\n")
        if (sessionCtx.isNotBlank()) prompt.append("SESSION: ").append(sessionCtx).append("\n")
        if (notes.isNotBlank()) prompt.append("NOTES: ").append(notes).append("\n")
        val playbook = PlaybookStore.getHint(snapshot.packageName)
        if (!playbook.isNullOrBlank()) prompt.append("APP PLAYBOOK: ").append(playbook).append("\n")

        prompt.append("\nHISTORY (last 4 steps):\n")
        for (step in history.takeLast(4)) {
            prompt.append("- Step ${step.step}: ${step.call.name} -> ${if (step.result.ok) "OK" else "FAIL"}: ${step.result.detail}\n")
        }

        prompt.append("\nCURRENT SCREEN ELEMENTS:\n")
        prompt.append(snapshot.toCompactText(4000))

        prompt.append("\n").append(ToolSchema.getSystemPromptText()).append("""

CRITICAL: Return ONLY a valid JSON object matching this schema:
{
  "name": "<tool_name>",
  "parameters": { ... }
}
Do not include any conversational explanation outside the JSON.
""".trimIndent())

        val apiResult = com.example.data.remote.AIApiUtility.executeWithFallback(
            prompt = prompt.toString(),
            mode = "general"
        )

        var rawText = apiResult.getOrNull() ?: ""
        if (rawText.isBlank()) {
            return@withContext BrainDecision(
                calls = listOf(AgentCall("call_1", "finish", JSONObject().put("success", false).put("summary", "AI service returned no response"))),
                finished = true,
                outcome = AgentOutcome(false, "No response from AI service", history.size + 1, 0)
            )
        }

        var parsedCall = parseJsonToolCall(rawText)

        // One-time repair prompt if initial parsing failed
        if (parsedCall == null) {
            Log.w(TAG, "Universal AI reply not valid JSON. Attempting repair prompt...")
            val repairPrompt = "Convert the following model output into strict JSON matching { \"name\": \"...\", \"parameters\": { ... } }:\n$rawText"
            val repairResult = com.example.data.remote.AIApiUtility.executeWithFallback(
                prompt = repairPrompt,
                mode = "general"
            )
            val repairedText = repairResult.getOrNull() ?: ""
            parsedCall = parseJsonToolCall(repairedText)
        }

        if (parsedCall == null) {
            Log.e(TAG, "Failed to parse model tool call after repair: $rawText")
            val failCall = AgentCall("call_err", "finish", JSONObject().put("success", false).put("summary", "Could not parse model response as a valid tool action."))
            return@withContext BrainDecision(
                calls = listOf(failCall),
                finished = true,
                outcome = AgentOutcome(false, "Could not parse model action", history.size + 1, 0)
            )
        }

        val normalized = ActionNormalizer.normalize(parsedCall)

        if (normalized.name == "finish" || normalized.name == "yield_to_user") {
            val success = normalized.name == "finish" && normalized.arguments.optBoolean("success", true)
            val summary = normalized.arguments.optString("summary", normalized.arguments.optString("reason", "Task finished"))
            return@withContext BrainDecision(
                calls = listOf(normalized),
                finished = true,
                outcome = AgentOutcome(success, summary, history.size + 1, 0, data = normalized.arguments.opt("data"))
            )
        }

        BrainDecision(calls = listOf(normalized))
    }

    private fun parseJsonToolCall(text: String): AgentCall? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return try {
            val jsonClean = text.substring(start, end + 1)
            val actionObj = JSONObject(jsonClean)
            val callName = actionObj.optString("name", "")
            if (callName.isBlank()) return null
            val callArgs = actionObj.optJSONObject("parameters") ?: JSONObject()
            val callId = "call_${System.currentTimeMillis()}"
            val intent = callArgs.optString("intent", "")
            AgentCall(callId, callName, callArgs, intent)
        } catch (e: Exception) {
            null
        }
    }
}
