package com.example.agent

import org.json.JSONObject
import java.util.Locale

/**
 * Universal Action Normalizer for the Phone Agent.
 * Reconciles variations across providers (OpenAI, Groq, Gemini, DeepSeek, universal JSON)
 * into strictly canonical AgentCall representations.
 */
object ActionNormalizer {

    private val CANONICAL_TOOL_NAMES = ToolSchema.ALL_TOOLS.map { it.name }.toSet()

    /**
     * Normalizes an incoming raw AgentCall into canonical tool names and standard parameter keys.
     */
    fun normalize(rawCall: AgentCall): AgentCall {
        val rawName = rawCall.name.trim().lowercase(Locale.ROOT)
        val args = rawCall.arguments

        val normalizedArgs = JSONObject()
        val keys = args.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            normalizedArgs.put(k.lowercase(Locale.ROOT), args.get(k))
        }

        val canonicalName = when (rawName) {
            "click_at", "tap", "tap_xy", "click" -> "click"
            "tap_element", "click_element" -> "tap_element"
            "type_text", "type_text_at", "type", "type_into" -> "type_into"
            "open_app", "launch_app", "start_app" -> "open_app"
            "key_event", "press_key", "go_back", "system_action", "press_system" -> "press_system"
            "wait_for", "wait" -> "wait"
            "complete", "done", "finish" -> "finish"
            "yield", "yield_to_user" -> "yield_to_user"
            "run_skill" -> "run_skill"
            "speak" -> "speak"
            "ask_user" -> "ask_user"
            "note" -> "note"
            "swipe", "scroll" -> "swipe"
            "scroll_until" -> "scroll_until"
            else -> rawName
        }

        // Parameter normalization per tool
        when (canonicalName) {
            "open_app" -> {
                val app = normalizedArgs.optString("app_name", "")
                    .ifBlank { normalizedArgs.optString("app_name_or_package", "") }
                    .ifBlank { normalizedArgs.optString("package", "") }
                    .ifBlank { normalizedArgs.optString("app", "") }
                    .ifBlank { normalizedArgs.optString("name", "") }
                normalizedArgs.put("app_name", app)
            }

            "tap_element" -> {
                val targetId = if (normalizedArgs.has("id")) {
                    normalizedArgs.opt("id")?.toString() ?: ""
                } else if (normalizedArgs.has("index")) {
                    normalizedArgs.opt("index")?.toString() ?: ""
                } else if (normalizedArgs.has("element_id")) {
                    normalizedArgs.opt("element_id")?.toString() ?: ""
                } else {
                    ""
                }
                normalizedArgs.put("id", targetId)
            }

            "click" -> {
                val x = normalizedArgs.optInt("x", normalizedArgs.optInt("point_x", 500))
                val y = normalizedArgs.optInt("y", normalizedArgs.optInt("point_y", 500))
                normalizedArgs.put("x", x)
                normalizedArgs.put("y", y)
            }

            "type_into" -> {
                val text = normalizedArgs.optString("text", "")
                    .ifBlank { normalizedArgs.optString("query", "") }
                    .ifBlank { normalizedArgs.optString("value", "") }
                normalizedArgs.put("text", text)

                val targetId = if (normalizedArgs.has("id")) {
                    normalizedArgs.opt("id")?.toString() ?: ""
                } else if (normalizedArgs.has("index")) {
                    normalizedArgs.opt("index")?.toString() ?: ""
                } else {
                    ""
                }
                if (targetId.isNotBlank()) {
                    normalizedArgs.put("id", targetId)
                }
            }

            "press_system" -> {
                val btn = normalizedArgs.optString("button", "")
                    .ifBlank { normalizedArgs.optString("key", "") }
                    .ifBlank { normalizedArgs.optString("action", "back") }
                    .lowercase(Locale.ROOT)
                val mappedBtn = when (btn) {
                    "enter", "search" -> "enter"
                    "home" -> "home"
                    "recents" -> "recents"
                    "notifications" -> "notifications"
                    "quick_settings" -> "quick_settings"
                    "lock", "lock_screen" -> "lock_screen"
                    "screenshot" -> "screenshot"
                    else -> "back"
                }
                normalizedArgs.put("button", mappedBtn)
            }

            "wait" -> {
                val duration = normalizedArgs.optInt("duration_ms", normalizedArgs.optInt("timeout_ms", 1000))
                normalizedArgs.put("duration_ms", duration.coerceIn(200, 5000))
            }
        }

        return AgentCall(
            id = rawCall.id,
            name = canonicalName,
            arguments = normalizedArgs,
            intent = rawCall.intent
        )
    }

    fun isCanonical(name: String): Boolean {
        return CANONICAL_TOOL_NAMES.contains(name) || name == "run_skill"
    }
}
