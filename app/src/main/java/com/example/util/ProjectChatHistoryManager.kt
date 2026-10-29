package com.example.util

import android.content.Context
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.SaifDatabase
import com.example.ui.components.AiStudioProgressStep
import com.example.ui.components.BuildAiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists and caches SAIF AI coding assistant messages per project.
 * Ensures conversations are never lost when leaving or reopening from the 3-dots menu,
 * and syncs with the central chat history database so project entries appear in the drawer.
 */
object ProjectChatHistoryManager {

    private const val PREFS_NAME = "saif_ai_project_chats"
    private val memoryCache = ConcurrentHashMap<String, List<BuildAiMessage>>()

    fun getMessages(context: Context, appName: String): List<BuildAiMessage> {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val cached = memoryCache[sanitized]
        if (cached != null && cached.isNotEmpty()) {
            return cached.map { if (it.isOptimizing) it.copy(isOptimizing = false) else it }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString("chat_$sanitized", null)
        if (!rawJson.isNullOrBlank()) {
            val list = parseJson(rawJson)
            if (list.isNotEmpty()) {
                val cleaned = list.map { if (it.isOptimizing) it.copy(isOptimizing = false) else it }
                memoryCache[sanitized] = cleaned
                return cleaned
            }
        }
        return emptyList()
    }

    fun saveMessages(context: Context, appName: String, messages: List<BuildAiMessage>) {
        if (appName.isBlank() || messages.isEmpty()) return
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        memoryCache[sanitized] = messages

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (msg in messages) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("sender", msg.sender)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
                put("isCodeSnippet", msg.isCodeSnippet)
                put("isOptimizing", false) // Never persist optimizing state
                put("isGlitchFix", msg.isGlitchFix)

                val summariesArr = JSONArray()
                for (s in msg.changeSummary) summariesArr.put(s)
                put("changeSummary", summariesArr)

                val appliedArr = JSONArray()
                for (f in msg.autoAppliedFiles) appliedArr.put(f)
                put("autoAppliedFiles", appliedArr)

                val stepsArr = JSONArray()
                for (step in msg.steps) {
                    val sObj = JSONObject().apply {
                        put("title", step.title)
                        put("isCompleted", step.isCompleted)
                        put("isRunning", step.isRunning)
                    }
                    stepsArr.put(sObj)
                }
                put("steps", stepsArr)

                val thoughtsArr = JSONArray()
                for (t in msg.thoughts) thoughtsArr.put(t)
                put("thoughts", thoughtsArr)

                put("elapsedSeconds", msg.elapsedSeconds)
                put("durationText", msg.durationText)
                put("activePhase", msg.activePhase)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("chat_$sanitized", jsonArray.toString()).apply()

        // Asynchronously sync with SaifDatabase Room so project displays in Home drawer (without writing messages into home screen chat)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SaifDatabase.getDatabase(context)
                val chatDao = db.chatDao()
                var session = chatDao.getSessionByTitle(appName)
                if (session == null) {
                    session = ChatSessionEntity(
                        title = appName,
                        mode = "project"
                    )
                    chatDao.insertSession(session)
                } else if (session.mode != "project") {
                    chatDao.updateSession(session.copy(mode = "project", updatedAt = System.currentTimeMillis()))
                } else {
                    chatDao.updateSessionTimestamp(session.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearMessages(context: Context, appName: String) {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        memoryCache.remove(sanitized)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("chat_$sanitized").apply()
    }

    private fun parseJson(rawJson: String): List<BuildAiMessage> {
        val list = mutableListOf<BuildAiMessage>()
        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val summariesList = mutableListOf<String>()
                val sumArr = obj.optJSONArray("changeSummary")
                if (sumArr != null) {
                    for (j in 0 until sumArr.length()) {
                        summariesList.add(sumArr.getString(j))
                    }
                }

                val appliedList = mutableListOf<String>()
                val appArr = obj.optJSONArray("autoAppliedFiles")
                if (appArr != null) {
                    for (j in 0 until appArr.length()) {
                        appliedList.add(appArr.getString(j))
                    }
                }

                val stepsList = mutableListOf<AiStudioProgressStep>()
                val stepsJsonArr = obj.optJSONArray("steps")
                if (stepsJsonArr != null) {
                    for (k in 0 until stepsJsonArr.length()) {
                        val sObj = stepsJsonArr.optJSONObject(k)
                        if (sObj != null) {
                            stepsList.add(
                                AiStudioProgressStep(
                                    title = sObj.optString("title", ""),
                                    isCompleted = sObj.optBoolean("isCompleted", true),
                                    isRunning = sObj.optBoolean("isRunning", false)
                                )
                            )
                        }
                    }
                }

                val thoughtsList = mutableListOf<String>()
                val thoughtsArr = obj.optJSONArray("thoughts")
                if (thoughtsArr != null) {
                    for (t in 0 until thoughtsArr.length()) {
                        thoughtsList.add(thoughtsArr.getString(t))
                    }
                }

                list.add(
                    BuildAiMessage(
                        id = obj.optString("id"),
                        sender = obj.optString("sender", "assistant"),
                        content = obj.optString("content", ""),
                        timestamp = obj.optString("timestamp", ""),
                        isCodeSnippet = obj.optBoolean("isCodeSnippet", false),
                        steps = stepsList,
                        changeSummary = summariesList,
                        autoAppliedFiles = appliedList,
                        isOptimizing = false,
                        isGlitchFix = obj.optBoolean("isGlitchFix", false),
                        thoughts = thoughtsList,
                        elapsedSeconds = obj.optInt("elapsedSeconds", 0),
                        durationText = obj.optString("durationText", ""),
                        activePhase = obj.optString("activePhase", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
