package com.example.agent.brain

import android.util.Log
import com.example.agent.AgentCall
import com.example.agent.AgentOutcome
import com.example.agent.PlaybookStore
import com.example.agent.RateGovernor
import com.example.agent.ScreenSnapshot
import com.example.agent.StepRecord
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

object ComputerUseBrain : AgentBrain {
    private const val TAG = "SAIF_AGENT"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var previousInteractionId: String? = null
    private var failureCount = 0

    fun resetSession() {
        previousInteractionId = null
        failureCount = 0
    }

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
            return@withContext FunctionCallingBrain.decide(task, sessionCtx, notes, history, snapshot, thinkingLevel)
        }

        val model = ModelResolver.resolve(ModelRole.AGENT)
        val url = "https://generativelanguage.googleapis.com/v1beta/interactions"

        try {
            val root = JSONObject()
            root.put("model", model)

            // System instruction
            val sysInstruction = JSONObject().apply {
                put("type", "text")
                put("text", AgentPrompts.BRAIN_SYSTEM)
            }
            root.put("system_instruction", sysInstruction)

            // Tools: Computer Use (mobile) + custom extensions
            val toolsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "computer_use")
                    put("environment", "mobile")
                    put("enable_prompt_injection_detection", true)
                })
            }
            root.put("tools", toolsArray)

            // Input payload
            val inputParts = JSONArray()

            val textContent = StringBuilder()
            textContent.append("GOAL: ").append(task).append("\n")
            if (sessionCtx.isNotBlank()) textContent.append("SESSION: ").append(sessionCtx).append("\n")
            if (notes.isNotBlank()) textContent.append("NOTES: ").append(notes).append("\n")

            val playbook = PlaybookStore.getHint(snapshot.packageName)
            if (!playbook.isNullOrBlank()) {
                textContent.append("APP PLAYBOOK: ").append(playbook).append("\n")
            }

            textContent.append("HISTORY:\n")
            for (step in history.takeLast(4)) {
                textContent.append("- Step ${step.step}: ${step.call.name} -> ${if (step.result.ok) "OK" else "FAIL"}\n")
            }
            textContent.append("\nCURRENT SCREEN ELEMENTS:\n")
            textContent.append(snapshot.toCompactText(3500))

            inputParts.put(JSONObject().apply {
                put("type", "text")
                put("text", textContent.toString())
            })

            // Image part
            if (!snapshot.screenshotJpegBase64.isNullOrBlank() && !snapshot.secure) {
                inputParts.put(JSONObject().apply {
                    put("type", "image")
                    put("data", snapshot.screenshotJpegBase64)
                    put("mime_type", "image/jpeg")
                })
            }

            root.put("input", inputParts)

            if (!previousInteractionId.isNullOrBlank()) {
                root.put("previous_interaction_id", previousInteractionId)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", key)
                .post(root.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "ComputerUse Interactions API returned HTTP ${response.code}: $bodyString")
                failureCount++
                if (failureCount >= 2 || response.code == 404 || response.code == 400) {
                    Log.i(TAG, "Switching to FunctionCallingBrain fallback permanently for this session")
                    AgentBrainFactory.markComputerUseFailed()
                    return@withContext FunctionCallingBrain.decide(task, sessionCtx, notes, history, snapshot, thinkingLevel)
                }
                return@withContext FunctionCallingBrain.decide(task, sessionCtx, notes, history, snapshot, thinkingLevel)
            }

            val json = JSONObject(bodyString)
            previousInteractionId = if (json.has("interaction_id")) json.optString("interaction_id").ifBlank { null } else null

            val steps = json.optJSONArray("steps")
            val calls = mutableListOf<AgentCall>()
            var thought = ""

            if (steps != null) {
                for (i in 0 until steps.length()) {
                    val stepObj = steps.getJSONObject(i)
                    val outText = stepObj.optString("model_output", "")
                    if (outText.isNotBlank()) thought += outText + " "

                    val fc = stepObj.optJSONObject("function_call")
                    if (fc != null) {
                        val callId = fc.optString("id", "call_${System.currentTimeMillis()}_$i")
                        val callName = fc.optString("name", "")
                        val callArgs = fc.optJSONObject("arguments") ?: JSONObject()
                        val intent = callArgs.optString("intent", "")
                        calls.add(AgentCall(callId, callName, callArgs, intent))
                    }
                }
            }

            val finishCall = calls.firstOrNull { it.name == "finish" || it.name == "yield_to_user" }
            if (finishCall != null) {
                val success = finishCall.name == "finish" && finishCall.arguments.optBoolean("success", true)
                val summary = finishCall.arguments.optString("summary", finishCall.arguments.optString("reason", "Task finished"))
                return@withContext BrainDecision(
                    calls = calls,
                    thought = thought.trim(),
                    finished = true,
                    outcome = AgentOutcome(success, summary, history.size + 1, 0, data = finishCall.arguments.opt("data"))
                )
            }

            BrainDecision(calls = calls, thought = thought.trim())
        } catch (e: Exception) {
            Log.e(TAG, "ComputerUseBrain error, falling back: ${e.message}")
            failureCount++
            if (failureCount >= 2) AgentBrainFactory.markComputerUseFailed()
            FunctionCallingBrain.decide(task, sessionCtx, notes, history, snapshot, thinkingLevel)
        }
    }
}
