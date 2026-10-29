package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.remote.AIApiUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Autonomous Android Screen & Multi-Command Agent.
 * Replicates the multi-stage autonomous phone controller (as seen in modern LAM / Agent systems):
 * - Deconstructs compound, multi-command prompts into sequential steps
 * - Provides full screen control: human-like typing, coordinate taps, scrolling, swiping, gestures
 * - Executes deep app automation across WhatsApp, YouTube, Chrome, Settings, etc.
 * - Communicates real-time progress via the existing voice system
 * - Universally compatible with ALL API providers & keys via AIApiUtility
 */
data class AutonomousStep(
    val stepIndex: Int,
    val title: String,
    val actionType: String, // LAUNCH_APP, CLICK_TEXT, CLICK_VIEW_ID, TAP_COORDINATE, HUMAN_TYPE, SCROLL, SWIPE, GLOBAL_NAV, WAIT, INSPECT, SPEAK
    val target: String = "",
    val payload: String = "",
    val spokenUpdate: String = "",
    val pressEnterAfter: Boolean = true
)

data class AutonomousTaskPlan(
    val userGoal: String,
    val steps: List<AutonomousStep>
)

object AutonomousScreenAgent {
    private const val TAG = "AutonomousScreenAgent"

    /**
     * Checks if the user prompt is requesting multi-step device actions, screen automation,
     * or compound tasks that require the autonomous screen controller.
     */
    fun isAutonomousDeviceTask(prompt: String): Boolean {
        val lower = prompt.lowercase().trim()

        val actionVerbs = listOf(
            "open", "kholo", "chalao", "play", "search", "dhoondo", "khojo",
            "type", "likho", "send", "bhejo", "scroll", "swipe", "tap", "click",
            "message", "call", "alarm", "like", "subscribe", "turn on", "turn off",
            "band karo", "shuru karo", "screen", "human touch"
        )

        val connectors = listOf(
            " and ", " aur ", " fir ", " then ", " ke baad ", " uske baad ",
            " baad me ", " khol kar ", " karke ", " se ", ", fir ", ", aur "
        )

        val hasMultiCommand = connectors.any { lower.contains(it) } && actionVerbs.any { lower.contains(it) }
        val hasScreenKeywords = lower.contains("screen") || lower.contains("type karo") || lower.contains("scroll karo") ||
                lower.contains("click karo") || lower.contains("tap karo") || lower.contains("human") || lower.contains("automate")

        val appsMentioned = listOf("whatsapp", "youtube", "instagram", "chrome", "google", "settings", "calculator", "notes", "camera", "gallery", "capcut", "uber", "zomato")
        val mentionsAppAction = appsMentioned.any { lower.contains(it) } && actionVerbs.any { lower.contains(it) }

        return hasMultiCommand || hasScreenKeywords || (mentionsAppAction && (lower.length > 20 || lower.split(" ").size >= 4))
    }

    /**
     * Deconstructs user prompt into a structured multi-step plan.
     * Uses fast-path deterministic parsing first (0ms latency), falling back to AIApiUtility
     * for complex, novel multi-stage actions across all LLM providers and keys.
     */
    suspend fun planTask(context: Context, userGoal: String): AutonomousTaskPlan = withContext(Dispatchers.IO) {
        // 1. Fast-path deterministic plan (0ms latency, eliminates 4-second remote LLM delay!)
        val fastPlan = deterministicDecompose(userGoal)
        if (fastPlan.isNotEmpty()) {
            Log.d(TAG, "Fast-path deterministic plan matched ${fastPlan.size} steps for: '$userGoal'")
            return@withContext AutonomousTaskPlan(userGoal = userGoal, steps = fastPlan)
        }

        val service = SaifAccessibilityService.instance
        val screenContext = service?.inspectScreenHierarchy()?.take(1500) ?: ""

        val systemPrompt = """
            You are SAIF Autonomous Mobile Agent, an expert native Android screen controller.
            The user wants to accomplish this multi-command task on their phone: '$userGoal'.
            ${if (screenContext.isNotBlank()) "CURRENT ACTIVE SCREEN CONTEXT:\n$screenContext" else ""}
            
            Decompose this goal into a strict sequence of atomic human-like Android device actions.
            Available action types:
            - LAUNCH_APP: target = app name (e.g. "whatsapp", "youtube", "chrome", "settings", "calculator", "notes")
            - CLICK_TEXT: target = visible text or button label to tap (e.g. "Search", "Send", "Rahul", "Play")
            - CLICK_VIEW_ID: target = view id substring
            - TAP_COORDINATE: payload = "x,y" (e.g. "500,800")
            - HUMAN_TYPE: target = field hint/label (e.g. "search", "message"), payload = text to type, pressEnter = true/false
            - SCROLL: payload = "down" | "up" | "left" | "right"
            - SWIPE: payload = "startX,startY,endX,endY"
            - GLOBAL_NAV: target = "home" | "back" | "recents" | "notifications"
            - YOUTUBE_PLAY: payload = query
            - YOUTUBE_SUBSCRIBE: target = "subscribe"
            - WHATSAPP_SEND: target = recipient, payload = message
            - CALL_PHONE: payload = contact or phone number
            - READ_MESSAGES: target = "messages"
            - WAIT: payload = milliseconds (e.g. "1000")
            - SPEAK: spokenUpdate = brief voice status to tell user
            
            RETURN ONLY a raw JSON array of step objects:
            [
              {"step": 1, "title": "Open App", "action": "LAUNCH_APP", "target": "whatsapp", "spoken": "Opening WhatsApp..."},
              {"step": 2, "title": "Type message", "action": "HUMAN_TYPE", "target": "message", "payload": "Hello", "spoken": "Typing message..."},
              {"step": 3, "title": "Send", "action": "CLICK_TEXT", "target": "send", "spoken": "Sent!"}
            ]
        """.trimIndent()

        var planSteps = emptyList<AutonomousStep>()

        try {
            val apiRes = AIApiUtility.executeWithFallback(
                prompt = userGoal,
                mode = "general",
                customSystemPrompt = systemPrompt
            )
            if (apiRes.isSuccess) {
                val raw = apiRes.getOrNull() ?: ""
                planSteps = parsePlanFromJson(raw)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI planning call failed: ${e.message}")
        }

        // If API returned no steps or offline, use smart deterministic fallback
        if (planSteps.isEmpty()) {
            planSteps = deterministicDecompose(userGoal)
        }

        AutonomousTaskPlan(userGoal = userGoal, steps = planSteps)
    }

    /**
     * Executes the task plan autonomously on the live screen with real-time feedback.
     */
    suspend fun executeTaskPlan(
        context: Context,
        plan: AutonomousTaskPlan,
        onProgress: (suspend (step: AutonomousStep, message: String) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.Main) {
        val results = mutableListOf<String>()
        val service = SaifAccessibilityService.instance

        Log.i(TAG, "Starting Autonomous Task Execution with ${plan.steps.size} steps for: '${plan.userGoal}'")

        for (step in plan.steps) {
            try {
                if (step.spokenUpdate.isNotBlank()) {
                    try {
                        com.example.utils.TTSManager.speak(step.spokenUpdate, context)
                    } catch (e: Exception) {
                        Log.w(TAG, "TTS speak failed for step update: ${e.message}")
                    }
                    onProgress?.invoke(step, step.spokenUpdate)
                    delay(600L)
                }

                val status = executeSingleStep(context, step, service)
                results.add(status)
                Log.d(TAG, "Step ${step.stepIndex} [${step.actionType}]: $status")

                // Natural human interaction delay between steps
                delay(450L)
            } catch (e: Exception) {
                Log.e(TAG, "Error in step ${step.stepIndex}: ${e.message}", e)
                results.add("Step ${step.stepIndex} Error: ${e.message}")
            }
        }

        results
    }

    private suspend fun executeSingleStep(
        context: Context,
        step: AutonomousStep,
        service: SaifAccessibilityService?
    ): String {
        return when (step.actionType.uppercase()) {
            "LAUNCH_APP" -> {
                val appName = step.target.ifBlank { step.payload }
                val isAlreadyOpen = when (appName.lowercase()) {
                    "youtube", "yt" -> service?.isYouTubeInForeground() ?: false
                    "whatsapp", "watsapp" -> service?.isWhatsAppInForeground() ?: false
                    "dialer", "phone" -> service?.isDialerInForeground() ?: false
                    else -> service?.isAppInForeground(appName) ?: false
                }
                if (!isAlreadyOpen) {
                    val ok = DeviceActionManager.launchApp(context, appName)
                    delay(1200L) // Wait for app launch animation
                    if (ok) "$appName khol diya hai." else "$appName phone me nahi mila."
                } else {
                    "$appName pehle se open hai, wahi se continue kar rahe hain."
                }
            }

            "YOUTUBE_PLAY" -> {
                val query = step.payload.ifBlank { step.target }
                DeviceActionManager.automateYouTubePlayAndSubscribe(context, query, autoSubscribe = false)
            }

            "YOUTUBE_OPEN_SEARCH" -> {
                if (service != null && service.isYouTubeInForeground()) {
                    service.findAndClickSearchButton()
                    if (step.payload.isNotBlank()) {
                        delay(400L)
                        service.findAndType(step.payload)
                        delay(300L)
                        service.pressEnterOrSearch()
                    }
                    "YouTube search open kiya."
                } else {
                    DeviceActionManager.searchYouTube(context, step.payload)
                    "YouTube search khol diya."
                }
            }

            "PLAY_STORE_SEARCH" -> {
                val query = step.payload.ifBlank { step.target }
                DeviceActionManager.searchPlayStore(context, query)
                "Play Store par '$query' search kar diya hai sir."
            }

            "CLICK_VIDEO" -> {
                val ok = service?.clickYouTubeFirstVideo() ?: false
                delay(1800L) // Wait for YouTube player to buffer and display video info & subscribe button!
                if (ok) "Pehli video play kar di hai sir." else "Video click nahi ho saka."
            }

            "YOUTUBE_SUBSCRIBE" -> {
                var ok = false
                for (attempt in 1..4) {
                    if (service?.clickYouTubeSubscribe() == true) {
                        ok = true
                        break
                    }
                    delay(500L)
                }
                if (ok) "Channel subscribe kar diya hai sir." else "Subscribe button nahi mila."
            }

            "WHATSAPP_SEND" -> {
                val recipient = step.target
                val message = step.payload.ifBlank { "Hii" }
                DeviceActionManager.automateWhatsAppMessage(context, recipient, message)
            }

            "CALL_PHONE" -> {
                val target = step.payload.ifBlank { step.target }
                DeviceActionManager.callOrDialPhone(context, target, directCall = true)
            }

            "READ_MESSAGES" -> {
                DeviceActionManager.readNotificationsSummary(context, null)
            }

            "HUMAN_TYPE" -> {
                val textToType = step.payload.ifBlank { step.target }
                if (service != null) {
                    val typed = service.performHumanTyping(
                        text = textToType,
                        targetFieldHint = step.target.ifBlank { null },
                        pressEnterAfter = step.pressEnterAfter,
                        charDelayMs = 30L
                    )
                    if (typed) "'$textToType' type kar diya." else "Input field nahi mila."
                } else {
                    DeviceActionManager.executeAction(context, DeviceAction.AccessibilityType(textToType))
                }
            }

            "CLICK_TEXT" -> {
                val targetText = step.target.ifBlank { step.payload }
                if (service != null) {
                    val clicked = service.findAndClickFuzzy(targetText)
                    if (clicked) "'$targetText' par click kar diya." else "'$targetText' screen par nahi mila."
                } else {
                    DeviceActionManager.executeAction(context, DeviceAction.AccessibilityClick(targetText))
                }
            }

            "CLICK_VIEW_ID" -> {
                val viewId = step.target.ifBlank { step.payload }
                val clicked = service?.findAndClickByViewId(viewId) ?: false
                if (clicked) "Element click kar diya." else "View ID nahi mila."
            }

            "TAP_COORDINATE" -> {
                val coords = step.payload.split(",")
                val x = coords.getOrNull(0)?.trim()?.toFloatOrNull() ?: 500f
                val y = coords.getOrNull(1)?.trim()?.toFloatOrNull() ?: 800f
                val tapped = service?.performHumanTap(x, y) ?: false
                if (tapped) "($x, $y) coordinate par tap kiya." else "Tap execute nahi ho saka."
            }

            "SCROLL" -> {
                val dir = step.payload.ifBlank { step.target }.ifBlank { "down" }.lowercase()
                val isDown = dir == "down" || dir == "neeche" || dir == "forward"
                val scrolled = service?.performHumanScrollDirection(if (isDown) "down" else "up", distanceRatio = 0.50f) ?: false
                if (scrolled) "Screen scroll kiya." else "Scroll perform nahi hua."
            }

            "SWIPE" -> {
                val parts = step.payload.split(",")
                if (parts.size >= 4) {
                    val x1 = parts[0].trim().toFloatOrNull() ?: 500f
                    val y1 = parts[1].trim().toFloatOrNull() ?: 1200f
                    val x2 = parts[2].trim().toFloatOrNull() ?: 500f
                    val y2 = parts[3].trim().toFloatOrNull() ?: 400f
                    val swiped = service?.performHumanSwipe(x1, y1, x2, y2) ?: false
                    if (swiped) "Swipe gesture complete." else "Swipe nahi ho saka."
                } else {
                    service?.scroll(forward = true)
                    "Swiped."
                }
            }

            "GLOBAL_NAV" -> {
                val nav = step.target.ifBlank { step.payload }.lowercase()
                DeviceActionManager.performNav(context, nav)
                "Navigation: $nav"
            }

            "WAIT" -> {
                val ms = step.payload.toLongOrNull() ?: 1000L
                delay(ms)
                "Waited ${ms}ms."
            }

            "INSPECT_SCREEN" -> {
                DeviceActionManager.inspectCurrentScreen(context)
            }

            "SPEAK" -> {
                step.spokenUpdate
            }

            else -> {
                // Check if macro
                if (step.target.contains("youtube", ignoreCase = true) && step.payload.isNotBlank()) {
                    DeviceActionManager.executeAction(context, DeviceAction.YouTubeSearch(step.payload))
                } else {
                    "Step executed."
                }
            }
        }
    }

    private fun parsePlanFromJson(raw: String): List<AutonomousStep> {
        val steps = mutableListOf<AutonomousStep>()
        var cleanJson = raw.trim()

        if (cleanJson.contains("```json")) {
            cleanJson = cleanJson.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (cleanJson.contains("```")) {
            cleanJson = cleanJson.substringAfter("```").substringBeforeLast("```").trim()
        }

        try {
            val start = cleanJson.indexOf('[')
            val end = cleanJson.lastIndexOf(']')
            if (start in 0 until end) {
                val arr = JSONArray(cleanJson.substring(start, end + 1))
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val sIdx = obj.optInt("step", i + 1)
                    val title = obj.optString("title", "Step $sIdx")
                    val action = obj.optString("action", obj.optString("type", "LAUNCH_APP")).uppercase()
                    val target = obj.optString("target", "")
                    val payload = obj.optString("payload", obj.optString("text", ""))
                    val spoken = obj.optString("spoken", obj.optString("speech", ""))
                    val pressEnter = obj.optBoolean("pressEnter", true)

                    steps.add(
                        AutonomousStep(
                            stepIndex = sIdx,
                            title = title,
                            actionType = action,
                            target = target,
                            payload = payload,
                            spokenUpdate = spoken,
                            pressEnterAfter = pressEnter
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing plan JSON: ${e.message}")
        }
        return steps
    }

    /**
     * Deterministic deconstruction parser for compound instructions.
     */
    private fun deterministicDecompose(userGoal: String): List<AutonomousStep> {
        val steps = mutableListOf<AutonomousStep>()
        val lower = userGoal.lowercase().trim()

        // 1. YouTube compound flow: "is video ko open kar ke neeche scroll kar ke subscribe karo"
        if ((lower.contains("video") || lower.contains("chalao") || lower.contains("open")) &&
            lower.contains("scroll") && (lower.contains("subscribe") || lower.contains("सदस्य"))) {
            steps.add(AutonomousStep(1, "Open Video", "CLICK_VIDEO", spokenUpdate = "Video open kar raha hoon..."))
            steps.add(AutonomousStep(2, "Scroll Down", "SCROLL", payload = "down", spokenUpdate = "Neeche scroll kar raha hoon..."))
            steps.add(AutonomousStep(3, "Subscribe Channel", "YOUTUBE_SUBSCRIBE", spokenUpdate = "Channel subscribe kar raha hoon sir..."))
            return steps
        }

        // 2. YouTube Search Bar / Continue Search: "ab koi song search karo search bar kholo" / "search button dabao"
        if ((lower.contains("search bar") || lower.contains("search kholo") || lower.contains("search button") || (lower.contains("search karo") && !lower.contains("play"))) &&
            (lower.contains("song") || lower.contains("gana") || lower.contains("video") || lower.contains("kholo") || lower.contains("open") || lower.contains("dabao"))) {
            val q = userGoal.replace(Regex("""(?i)\b(ab|koi|song|search|bar|button|kholo|open|dabao|daba|karo|par|pe|me|aur|fir|then)\b"""), " ").trim()
            steps.add(AutonomousStep(1, "Open Search Bar", "YOUTUBE_OPEN_SEARCH", payload = q, spokenUpdate = "Search bar open kar raha hoon..."))
            return steps
        }

        // 3. Play Store Multi-Step: "Open Play Store, Search BGMI & Install"
        if (lower.contains("play store") || lower.contains("playstore") || lower.contains("google play") || (lower.contains("store") && lower.contains("search"))) {
            var appQuery = userGoal.replace(Regex("""(?i)\b(open|kholo|play\s*store|playstore|google\s*play|store|par|pe|me|and|aur|fir|then|search|dhoondo|install|download|karo|banao|app|game|ko|kar|do|karke|ek|koi)\b"""), " ").trim()
            if (appQuery.isBlank()) {
                appQuery = "BGMI"
            }
            steps.add(AutonomousStep(1, "Open Play Store", "LAUNCH_APP", target = "playstore", spokenUpdate = "Play Store open kar raha hoon sir..."))
            steps.add(AutonomousStep(2, "Search on Play Store", "PLAY_STORE_SEARCH", payload = appQuery, spokenUpdate = "Play Store par '$appQuery' search kar raha hoon sir..."))
            if (lower.contains("install") || lower.contains("download")) {
                steps.add(AutonomousStep(3, "Tap Install", "CLICK_TEXT", target = "Install", spokenUpdate = "Install par tap kiya..."))
            }
            return steps
        }

        // 4. YouTube Multi-Step: "youtube open karo aur song search kar ke paheli video play kar do"
        if (lower.contains("youtube") && (lower.contains("play") || lower.contains("search") || lower.contains("chalao") || lower.contains("gana") || lower.contains("song") || lower.contains("video"))) {
            var query = userGoal.replace(Regex("""(?i)\b(open|kholo|youtube|par|pe|me|and|aur|fir|then|search|play|chalao|gana|song|karo|banao|pahele|pehli|pehla|wali|video|ko|kar|do|karke|ek|koi)\b"""), " ").trim()
            if (query.isBlank() || query == "video" || query == "song" || query == "gana") {
                query = "latest trending song"
            }
            steps.add(AutonomousStep(1, "Open YouTube", "LAUNCH_APP", target = "youtube", spokenUpdate = "Main YouTube khol raha hoon sir..."))
            steps.add(AutonomousStep(2, "Search on YouTube", "YOUTUBE_OPEN_SEARCH", payload = query, spokenUpdate = "YouTube par '$query' search kar raha hoon sir..."))
            if (lower.contains("play") || lower.contains("chalao") || lower.contains("pehli") || lower.contains("pahele") || lower.contains("video")) {
                steps.add(AutonomousStep(3, "Play Video", "CLICK_VIDEO", spokenUpdate = "Pehli video play kar raha hoon sir..."))
            }
            if (lower.contains("scroll")) {
                steps.add(AutonomousStep(steps.size + 1, "Scroll Down", "SCROLL", payload = "down", spokenUpdate = "Screen scroll kar raha hoon..."))
            }
            if (lower.contains("subscribe")) {
                steps.add(AutonomousStep(steps.size + 1, "Subscribe Channel", "YOUTUBE_SUBSCRIBE", spokenUpdate = "Channel subscribe kar raha hoon sir..."))
            }
            return steps
        }

        // 4. Calling: "papa ko call lagao" / "call papa"
        if (lower.contains("call") || lower.contains("phone lagao") || lower.contains("phone milao") || lower.contains("dial")) {
            val phoneRegex = Regex("""(?i)(?:call|dial|milao|lagao)\s*(?:karo|lagao)?\s*([+0-9a-zA-Z\u0900-\u097F]+)""")
            val altRegex = Regex("""(?i)([+0-9a-zA-Z\u0900-\u097F]+)\s*ko\s*(?:call|phone)""")
            val match = altRegex.find(userGoal) ?: phoneRegex.find(userGoal)
            val target = match?.groupValues?.get(1)?.trim() ?: ""
            val cleanTarget = if (target.lowercase() in listOf("karo", "lagao", "kisi", "kisi ko")) "papa" else target
            steps.add(AutonomousStep(1, "Call Contact", "CALL_PHONE", payload = cleanTarget, spokenUpdate = "$cleanTarget ko call mila raha hoon sir..."))
            return steps
        }

        // 5. WhatsApp: "watsaap kholo lucky2 ko hii likh kar bhejo" / "watsapp par kisi ko hi likh kar bhejo"
        if (lower.contains("whatsapp") || lower.contains("watsapp") || lower.contains("whats app")) {
            var recipient = ""
            var message = "Hii"

            val contactMatch = Regex("""(?i)\b([a-zA-Z0-9_\u0900-\u097F]+)\s*ko\b""").find(userGoal)
                ?: Regex("""(?i)\b(?:to|par)\s+([a-zA-Z0-9_\u0900-\u097F]+)""").find(userGoal)
            if (contactMatch != null) {
                recipient = contactMatch.groupValues[1].trim()
            }
            if (recipient.lowercase() in listOf("whatsapp", "watsapp", "kisi", "kisi ko", "karo", "do")) {
                recipient = if (recipient.lowercase() == "kisi" || recipient.lowercase() == "kisi ko") "kisi ko" else ""
            }

            when {
                lower.contains("hii") || lower.contains("hi") -> message = "Hii"
                lower.contains("hello") -> message = "Hello"
                else -> {
                    val msgMatch = Regex("""['"]([^'"]+)['"]""").find(userGoal)
                    if (msgMatch != null) {
                        message = msgMatch.groupValues[1].trim()
                    } else if (lower.contains("likh") || lower.contains("type") || lower.contains("bhejo") || lower.contains("send")) {
                        val beforeLikh = userGoal.substringBefore("likh", userGoal.substringBefore("bhejo", ""))
                        val afterKo = beforeLikh.substringAfter("ko", "").trim()
                        if (afterKo.isNotBlank() && !afterKo.contains("whatsapp", ignoreCase = true)) {
                            message = afterKo
                        }
                    }
                }
            }

            val targetLabel = if (recipient.isNotBlank() && recipient != "kisi ko") recipient else "contact"
            steps.add(AutonomousStep(1, "Open WhatsApp", "LAUNCH_APP", target = "whatsapp", spokenUpdate = "Main WhatsApp khol raha hoon sir..."))
            steps.add(AutonomousStep(2, "Send WhatsApp Message", "WHATSAPP_SEND", target = recipient, payload = message, spokenUpdate = "WhatsApp par $targetLabel ko '$message' bhej raha hoon sir..."))
            return steps
        }

        // 6. Messages & Notifications: "massage abhi kya kya aaye hai"
        val isCheckingMessages = lower.contains("notification") || lower.contains("suchna") || lower.contains("suchnaye") ||
                lower.contains("massage") || lower.contains("message") || lower.contains("messages") || lower.contains("msg") ||
                lower.contains("sms") || lower.contains("unread")
        if (isCheckingMessages && (lower.contains("aaye") || lower.contains("aaya") || lower.contains("kya") || lower.contains("check") || lower.contains("read") || lower.contains("padho") || lower.contains("batao") || lower.contains("dikhao") || lower.contains("sunao"))) {
            steps.add(AutonomousStep(1, "Read Messages", "READ_MESSAGES", spokenUpdate = "Aapke messages check kar raha hoon sir..."))
            return steps
        }

        // 7. Screen Inspection: "screen par kya hai", "screen dekho", "kya chal raha hai"
        if (lower.contains("screen par kya") || lower.contains("screen dekho") || lower.contains("screen inspect") || lower.contains("kya chal raha hai") || lower.contains("screen check")) {
            steps.add(AutonomousStep(1, "Inspect Screen", "INSPECT_SCREEN", spokenUpdate = "Screen check kar raha hoon sir..."))
            return steps
        }

        // 8. Home Navigation: "ab home screen par aao", "home button dabao"
        if (lower.contains("home screen") || lower.contains("home par") || lower.contains("main screen") || lower.contains("home aao") || lower.contains("home button") || lower == "home") {
            steps.add(AutonomousStep(1, "Go to Home Screen", "GLOBAL_NAV", target = "home", spokenUpdate = "Home screen par jaa rahe hain sir..."))
            return steps
        }

        // 9. Back Navigation: "back button dabao", "ek baar back karo"
        if (lower.contains("back button") || lower.contains("back karo") || lower.contains("ek baar back") || lower.contains("piche aao") || lower.contains("wapas aao") || lower == "back") {
            steps.add(AutonomousStep(1, "Go Back", "GLOBAL_NAV", target = "back", spokenUpdate = "Back button daba rahe hain sir..."))
            return steps
        }

        // 10. Scrolling: "scroll karo", "neeche scroll karo"
        if (lower.contains("scroll") || lower.contains("neeche karo") || lower.contains("upar karo") || lower.contains("neeche jao") || lower.contains("upar jao")) {
            val dir = if (lower.contains("up") || lower.contains("upar")) "up" else "down"
            steps.add(AutonomousStep(1, "Scroll Screen", "SCROLL", payload = dir, spokenUpdate = "Screen scroll kar raha hoon..."))
            return steps
        }

        // 9. Generic Conjunction Splitter: "X aur fir Y"
        val splitRegex = Regex("""(?i)\s+(?:aur\s+fir|and\s+then|aur|and|fir|then|ke\s+baad|uske\s+baad|,\s*)\s+""")
        val parts = userGoal.split(splitRegex).map { it.trim() }.filter { it.isNotBlank() }

        var stepCounter = 1
        for (part in parts) {
            val pLower = part.lowercase()
            when {
                pLower.contains("open") || pLower.contains("kholo") || pLower.contains("launch") -> {
                    val app = part.replace(Regex("""(?i)\b(open|kholo|launch|app|application)\b"""), "").trim()
                    steps.add(AutonomousStep(stepCounter++, "Open $app", "LAUNCH_APP", target = app, spokenUpdate = "$app open kar raha hoon..."))
                }
                pLower.contains("type") || pLower.contains("likho") -> {
                    val text = part.replace(Regex("""(?i)\b(type|likho|karo|me|par)\b"""), "").trim()
                    steps.add(AutonomousStep(stepCounter++, "Type text", "HUMAN_TYPE", target = "", payload = text, spokenUpdate = "Text type kar raha hoon..."))
                }
                pLower.contains("scroll") || pLower.contains("neeche") || pLower.contains("upar") -> {
                    val dir = if (pLower.contains("upar") || pLower.contains("up")) "up" else "down"
                    steps.add(AutonomousStep(stepCounter++, "Scroll $dir", "SCROLL", payload = dir, spokenUpdate = "Screen scroll kar raha hoon..."))
                }
                pLower.contains("click") || pLower.contains("tap") || pLower.contains("dabao") -> {
                    val target = part.replace(Regex("""(?i)\b(click|tap|dabao|par|pe|karo)\b"""), "").trim()
                    steps.add(AutonomousStep(stepCounter++, "Click $target", "CLICK_TEXT", target = target, spokenUpdate = "$target par tap kiya..."))
                }
                pLower.contains("home") -> {
                    steps.add(AutonomousStep(stepCounter++, "Go Home", "GLOBAL_NAV", target = "home", spokenUpdate = "Home screen par jaa rahe hain..."))
                }
                pLower.contains("back") -> {
                    steps.add(AutonomousStep(stepCounter++, "Go Back", "GLOBAL_NAV", target = "back", spokenUpdate = "Wapas jaa rahe hain..."))
                }
                else -> {
                    steps.add(AutonomousStep(stepCounter++, "Execute action", "CLICK_TEXT", target = part, spokenUpdate = ""))
                }
            }
        }

        if (steps.isEmpty()) {
            steps.add(AutonomousStep(1, "Execute", "CLICK_TEXT", target = userGoal, spokenUpdate = "Action execute kar rahe hain..."))
        }

        return steps
    }
}
