package com.example.agent

import android.util.Log
import com.example.agent.brain.AgentPrompts
import com.example.data.remote.ModelResolver
import com.example.data.remote.ModelRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale

enum class RouteDestination {
    TASK,
    CHAT,
    CONTROL
}

data class RouteDecision(
    val route: RouteDestination,
    val goal: String,
    val controlCmd: String? = null
)

/**
 * High-accuracy intent router with word-boundary matching,
 * task-lane isolation, and multi-provider compatibility.
 */
object IntentRouter {
    private const val TAG = "SAIF_AGENT"

    // High-confidence, exact stop words. Deliberately excludes loose words like "bas" or "chup"
    // which occur naturally in sentences ("bas ek minute", "yeh basic hai").
    private val STOP_WORD_REGEX = Regex(
        """^(stop|ruko|rukiye|cancel|band karo|rok do|khatam karo)$""",
        RegexOption.IGNORE_CASE
    )

    private val STATUS_QUERY_REGEX = Regex(
        """\b(kya kar rahe ho|status batao|kaha tak pahuncha|task status)\b""",
        RegexOption.IGNORE_CASE
    )

    suspend fun route(utterance: String, isTaskRunning: Boolean): RouteDecision {
        val clean = utterance.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[.,!?]"""), "")
            .trim()

        // 0. If UserDialogBridge is actively waiting for an answer to a question (e.g. contact selection)
        if (UserDialogBridge.isAwaitingAnswer()) {
            val handled = UserDialogBridge.provideAnswer(utterance)
            if (handled) {
                return RouteDecision(RouteDestination.CHAT, utterance)
            }
        }

        // 1. Strict isolated STOP command check
        if (STOP_WORD_REGEX.matches(clean)) {
            return RouteDecision(RouteDestination.CONTROL, utterance, controlCmd = "stop")
        }

        if (STATUS_QUERY_REGEX.containsMatchIn(clean)) {
            return RouteDecision(RouteDestination.CONTROL, utterance, controlCmd = "status")
        }

        // 2. Fast-path heuristic classification (0ms latency, eliminates 3.5-second classification delay)
        val heuristic = heuristicClassify(utterance, isTaskRunning)
        if (heuristic.route == RouteDestination.TASK) {
            return heuristic
        }

        // 3. Simple greeting or conversational phrases -> route to CHAT immediately
        val conversationalGreeting = Regex("""^(hi|hii|hello|hey|namaste|kya haal hai|kaise ho|who are you|tum kaun ho|good morning|good evening)$""", RegexOption.IGNORE_CASE)
        if (conversationalGreeting.matches(clean)) {
            return RouteDecision(RouteDestination.CHAT, utterance)
        }

        // 4. LLM Classification fallback for complex ambiguous requests (short timeout 1800ms)
        val llmResult = withTimeoutOrNull(1800L) {
            classifyWithFastModel(utterance, isTaskRunning)
        }

        if (llmResult != null) {
            return llmResult
        }

        return heuristic
    }

    private suspend fun classifyWithFastModel(utterance: String, isTaskRunning: Boolean): RouteDecision? = withContext(Dispatchers.IO) {
        val sysText = AgentPrompts.ROUTER_SYSTEM.replace("{{RUNNING_TASK_OR_NONE}}", if (isTaskRunning) "YES" else "NONE")

        try {
            val result = com.example.data.remote.AIApiUtility.executeWithFallback(
                prompt = utterance,
                mode = "general",
                customSystemPrompt = sysText
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
                    val resJson = JSONObject(cleanJson.substring(jsonStart, jsonEnd + 1))
                    val routeStr = resJson.optString("route", "chat").lowercase(Locale.ROOT)
                    val goal = resJson.optString("goal", utterance)
                    val control = if (resJson.has("control") && !resJson.isNull("control")) resJson.optString("control") else null

                    val destination = when (routeStr) {
                        "task" -> RouteDestination.TASK
                        "control" -> RouteDestination.CONTROL
                        else -> RouteDestination.CHAT
                    }
                    return@withContext RouteDecision(destination, goal, control)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast router LLM failed, using heuristic: ${e.message}")
        }
        null
    }

    private fun heuristicClassify(utterance: String, isTaskRunning: Boolean): RouteDecision {
        val lower = utterance.lowercase(Locale.ROOT)

        // Strict command patterns with word boundaries (avoids matching "I like this", "what is a call option", "likely")
        val taskPatterns = listOf(
            Regex("""\b(kholo|open)\s+[a-z0-9]+"""),
            Regex("""\b(chalao|play|chalu karo)\b"""),
            Regex("""\b(bhejo|send message|live location)\b"""),
            Regex("""\b(call karo|phone lagao|dial)\b"""),
            Regex("""\b(search karo|khojo)\b"""),
            Regex("""\b(like karo|subscribe karo)\b"""),
            Regex("""\b(scroll karo|scroll down|next video)\b"""),
            Regex("""\b(update all|update karo)\b"""),
            Regex("""\b(torch|flashlight|flashlight on|flashlight off)\b"""),
            Regex("""\b(alarm lagao|timer lagao)\b"""),
            Regex("""\b(volume badhao|volume kam karo)\b"""),
            Regex("""\byoutube\b.*(chalao|search|play|kholo)"""),
            Regex("""\bwhatsapp\b.*(bhejo|kholo|message)"""),
            Regex("""\binstagram\b.*(reel|story|kholo)"""),
            Regex("""\bcapcut\b.*(project|video|edit)""")
        )

        for (pattern in taskPatterns) {
            if (pattern.containsMatchIn(lower)) {
                return RouteDecision(RouteDestination.TASK, utterance)
            }
        }

        return RouteDecision(RouteDestination.CHAT, utterance)
    }
}
