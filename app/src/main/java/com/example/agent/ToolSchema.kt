package com.example.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth (canonical schema) for all Phone Agent tools across all AI providers:
 * - Gemini functionDeclarations
 * - OpenAI / Groq / xAI / OpenRouter / DeepSeek tools schema
 * - Universal fallback text prompt
 */
object ToolSchema {

    data class ToolProperty(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean = true,
        val enumValues: List<String>? = null
    )

    data class ToolDefinition(
        val name: String,
        val description: String,
        val properties: List<ToolProperty>
    )

    val ALL_TOOLS: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "tap_element",
            description = "Tap on an interactive element by its snapshot-scoped element id (e.g. 's1e5') or index.",
            properties = listOf(
                ToolProperty("id", "string", "Snapshot-scoped element id (e.g. 's1e5') or numeric index.", required = true),
                ToolProperty("intent", "string", "Brief reason why tapping this element helps achieve the goal.", required = false)
            )
        ),
        ToolDefinition(
            name = "click",
            description = "Dispatch a verified human tap at normalized screen coordinates (0-999).",
            properties = listOf(
                ToolProperty("x", "integer", "Normalized horizontal X coordinate from 0 (left) to 999 (right).", required = true),
                ToolProperty("y", "integer", "Normalized vertical Y coordinate from 0 (top) to 999 (bottom).", required = true),
                ToolProperty("intent", "string", "Brief description of the target at these coordinates.", required = false)
            )
        ),
        ToolDefinition(
            name = "type_into",
            description = "Type text into an editable input field. Verifies text was written successfully.",
            properties = listOf(
                ToolProperty("text", "string", "Text to type into the field.", required = true),
                ToolProperty("id", "string", "Optional target element id or index.", required = false),
                ToolProperty("clear_first", "boolean", "Whether to clear existing text in field first (default: true).", required = false),
                ToolProperty("press_enter", "boolean", "Whether to send an Enter/Search IME action after typing (default: false).", required = false),
                ToolProperty("intent", "string", "Why typing this text is needed.", required = false)
            )
        ),
        ToolDefinition(
            name = "swipe",
            description = "Perform a natural curved finger swipe gesture in a given direction.",
            properties = listOf(
                ToolProperty("direction", "string", "Direction to swipe: 'up', 'down', 'left', 'right'.", required = true, enumValues = listOf("up", "down", "left", "right")),
                ToolProperty("distance", "string", "Distance of swipe: 'short', 'medium', 'long'.", required = false, enumValues = listOf("short", "medium", "long")),
                ToolProperty("intent", "string", "Purpose of swipe.", required = false)
            )
        ),
        ToolDefinition(
            name = "scroll_until",
            description = "Scrolls the screen smoothly until the requested query text becomes visible.",
            properties = listOf(
                ToolProperty("text", "string", "Text or query to find while scrolling.", required = true),
                ToolProperty("direction", "string", "Scroll direction ('down' or 'up'). Default 'down'.", required = false, enumValues = listOf("down", "up"))
            )
        ),
        ToolDefinition(
            name = "open_app",
            description = "Opens an application by its common name or package, verified through a 4-step launch ladder.",
            properties = listOf(
                ToolProperty("app_name", "string", "Name or package of the app to open (e.g. 'YouTube', 'WhatsApp', 'CapCut').", required = true)
            )
        ),
        ToolDefinition(
            name = "press_system",
            description = "Executes an Android system action.",
            properties = listOf(
                ToolProperty("button", "string", "System action to trigger: 'back', 'home', 'recents', 'notifications', 'quick_settings', 'lock_screen', 'screenshot'.", required = true, enumValues = listOf("back", "home", "recents", "notifications", "quick_settings", "lock_screen", "screenshot"))
            )
        ),
        ToolDefinition(
            name = "wait",
            description = "Pause execution for a duration to allow UI animations or media to settle.",
            properties = listOf(
                ToolProperty("duration_ms", "integer", "Milliseconds to wait (between 200 and 5000).", required = true)
            )
        ),
        ToolDefinition(
            name = "speak",
            description = "Speaks a brief milestone update to the user in Hinglish/English (max 12 words). Does not block task.",
            properties = listOf(
                ToolProperty("text", "string", "Milestone message to speak to user.", required = true)
            )
        ),
        ToolDefinition(
            name = "ask_user",
            description = "Ask the user a clarification question by voice when an action is ambiguous.",
            properties = listOf(
                ToolProperty("question", "string", "Spoken question to ask.", required = true),
                ToolProperty("options", "array", "Optional choices for user to pick from.", required = false)
            )
        ),
        ToolDefinition(
            name = "note",
            description = "Save an extracted fact or memory note for upcoming steps in this task (e.g. view count, channel name).",
            properties = listOf(
                ToolProperty("text", "string", "Note content to preserve across task steps.", required = true)
            )
        ),
        ToolDefinition(
            name = "finish",
            description = "Complete the task and report the final outcome to the user.",
            properties = listOf(
                ToolProperty("success", "boolean", "Whether the user's overall goal was successfully accomplished.", required = true),
                ToolProperty("summary", "string", "Concise, friendly spoken summary in natural Hinglish/English.", required = true)
            )
        ),
        ToolDefinition(
            name = "yield_to_user",
            description = "Yield control to the user when biometric authentication, OTP entry, or sensitive login is required.",
            properties = listOf(
                ToolProperty("reason", "string", "Explanation of why user must take over manually.", required = true)
            )
        )
    )

    /**
     * Generates Gemini function declarations.
     */
    fun getGeminiFunctionDeclarations(): JSONArray {
        val array = JSONArray()
        for (tool in ALL_TOOLS) {
            val obj = JSONObject()
            obj.put("name", tool.name)
            obj.put("description", tool.description)

            val params = JSONObject()
            params.put("type", "OBJECT")

            val props = JSONObject()
            val reqList = JSONArray()

            for (prop in tool.properties) {
                val p = JSONObject()
                p.put("type", when (prop.type) {
                    "integer" -> "INTEGER"
                    "boolean" -> "BOOLEAN"
                    "array" -> "ARRAY"
                    else -> "STRING"
                })
                p.put("description", prop.description)
                if (!prop.enumValues.isNullOrEmpty()) {
                    val enumArr = JSONArray()
                    prop.enumValues.forEach { enumArr.put(it) }
                    p.put("enum", enumArr)
                }
                props.put(prop.name, p)
                if (prop.required) {
                    reqList.put(prop.name)
                }
            }

            params.put("properties", props)
            if (reqList.length() > 0) {
                params.put("required", reqList)
            }
            obj.put("parameters", params)
            array.put(obj)
        }
        return array
    }

    /**
     * Generates OpenAI-format tools array for Groq / OpenAI / xAI / OpenRouter / DeepSeek.
     */
    fun getOpenAiTools(): JSONArray {
        val array = JSONArray()
        for (tool in ALL_TOOLS) {
            val func = JSONObject()
            func.put("name", tool.name)
            func.put("description", tool.description)

            val params = JSONObject()
            params.put("type", "object")

            val props = JSONObject()
            val reqList = JSONArray()

            for (prop in tool.properties) {
                val p = JSONObject()
                p.put("type", when (prop.type) {
                    "integer" -> "integer"
                    "boolean" -> "boolean"
                    "array" -> "array"
                    else -> "string"
                })
                p.put("description", prop.description)
                if (!prop.enumValues.isNullOrEmpty()) {
                    val enumArr = JSONArray()
                    prop.enumValues.forEach { enumArr.put(it) }
                    p.put("enum", enumArr)
                }
                props.put(prop.name, p)
                if (prop.required) {
                    reqList.put(prop.name)
                }
            }

            params.put("properties", props)
            if (reqList.length() > 0) {
                params.put("required", reqList)
            }
            func.put("parameters", params)

            val toolWrapper = JSONObject()
            toolWrapper.put("type", "function")
            toolWrapper.put("function", func)
            array.put(toolWrapper)
        }
        return array
    }

    /**
     * Generates formatted text prompt describing all available tools.
     */
    fun getSystemPromptText(): String {
        val sb = StringBuilder()
        sb.append("AVAILABLE TOOLS (Output strict JSON calling one tool per decision):\n")
        for ((idx, tool) in ALL_TOOLS.withIndex()) {
            sb.append("${idx + 1}. ${tool.name}: ${tool.description}\n")
            val paramsStr = tool.properties.joinToString(", ") { p ->
                val typeDesc = if (p.enumValues != null) p.enumValues.joinToString("|") else p.type
                "\"${p.name}\": <$typeDesc${if (!p.required) " optional" else ""}>"
            }
            sb.append("   Schema: { \"name\": \"${tool.name}\", \"parameters\": { $paramsStr } }\n")
        }
        return sb.toString()
    }
}
