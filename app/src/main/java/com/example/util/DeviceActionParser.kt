package com.example.util

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class ParsedActionResult(
    val actions: List<DeviceAction>,
    val cleanSpokenText: String
)

object DeviceActionParser {
    private const val TAG = "DeviceActionParser"

    /**
     * Extracts actions from AI response if present (<<<ACTIONS [...] ACTIONS>>>),
     * and strips the action block so that TTS only speaks the conversational reply.
     * If no AI action block is present, falls back to parsing the user prompt directly.
     */
    fun parse(aiResponse: String, userPrompt: String): ParsedActionResult {
        val actions = mutableListOf<DeviceAction>()
        var cleanText = aiResponse

        // 1. Try parsing JSON actions from AI output
        val jsonPattern = Regex("<<<ACTIONS([\\s\\S]*?)ACTIONS>>>")
        val match = jsonPattern.find(aiResponse)
        if (match != null) {
            val jsonStr = match.groupValues[1].trim()
            cleanText = aiResponse.replace(match.value, "").trim()
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val action = parseActionJson(obj)
                    if (action != null) {
                        actions.add(action)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing AI actions JSON: ${e.message}")
            }
        }

        // Also check for markdown json_actions format fallback
        val codeBlockPattern = Regex("```json_actions([\\s\\S]*?)```")
        val codeMatch = codeBlockPattern.find(cleanText)
        if (codeMatch != null) {
            val jsonStr = codeMatch.groupValues[1].trim()
            cleanText = cleanText.replace(codeMatch.value, "").trim()
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val action = parseActionJson(obj)
                    if (action != null && !actions.contains(action)) {
                        actions.add(action)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing codeblock actions JSON: ${e.message}")
            }
        }

        // 2. Also run deterministic NLP parser on userPrompt to catch any macro/device actions
        val promptActions = parseFromUserPrompt(userPrompt)
        for (pa in promptActions) {
            // Merge deterministic prompt action if not already present
            if (actions.none { it::class == pa::class }) {
                actions.add(pa)
            }
        }

        return ParsedActionResult(actions, cleanText)
    }

    private fun parseActionJson(obj: JSONObject): DeviceAction? {
        return try {
            val type = obj.optString("type", obj.optString("action", "")).uppercase()
            when (type) {
                "OPEN_APP" -> {
                    val app = obj.optString("app", obj.optString("appName", ""))
                    if (app.isNotBlank()) DeviceAction.OpenApp(app) else null
                }
                "ALARM", "SET_ALARM" -> {
                    val hour = obj.optInt("hour", 8)
                    val minute = obj.optInt("minute", 0)
                    val msg = obj.optString("message", "Alarm")
                    DeviceAction.SetAlarm(hour, minute, msg)
                }
                "WHATSAPP", "WHATSAPP_MESSAGE" -> {
                    val recipient = obj.optString("recipient", obj.optString("contact", obj.optString("to", "")))
                    val msg = obj.optString("message", obj.optString("text", "Hii"))
                    DeviceAction.WhatsAppMessage(recipient, msg)
                }
                "WHATSAPP_CALL" -> {
                    val recipient = obj.optString("recipient", obj.optString("contact", ""))
                    DeviceAction.WhatsAppCall(recipient)
                }
                "YOUTUBE", "YOUTUBE_SEARCH" -> {
                    val query = obj.optString("query", obj.optString("search", ""))
                    DeviceAction.YouTubeSearch(query)
                }
                "YOUTUBE_CONTROL" -> {
                    val cmd = obj.optString("command", obj.optString("action", "play"))
                    DeviceAction.YouTubeControl(cmd)
                }
                "INSTAGRAM", "INSTAGRAM_ACTION" -> {
                    val sub = obj.optString("subAction", obj.optString("action", "open"))
                    DeviceAction.InstagramAction(sub)
                }
                "CALL", "DIAL", "CALL_PHONE" -> {
                    val target = obj.optString("target", obj.optString("number", obj.optString("contact", "")))
                    val direct = obj.optBoolean("direct", true)
                    DeviceAction.DialPhone(target, direct)
                }
                "SMS", "SEND_SMS" -> {
                    val recipient = obj.optString("recipient", obj.optString("contact", obj.optString("to", "")))
                    val msg = obj.optString("message", obj.optString("text", ""))
                    DeviceAction.SendSms(recipient, msg)
                }
                "FLASHLIGHT", "TORCH" -> {
                    val enable = if (obj.has("enable")) obj.getBoolean("enable") else null
                    DeviceAction.ToggleFlashlight(enable)
                }
                "WIFI", "OPEN_WIFI" -> DeviceAction.OpenWifi
                "BLUETOOTH", "OPEN_BLUETOOTH" -> DeviceAction.OpenBluetooth
                "SETTINGS", "OPEN_SETTINGS" -> DeviceAction.OpenSettings
                "NOTES", "OPEN_NOTES" -> {
                    val text = obj.optString("text", obj.optString("note", ""))
                    DeviceAction.OpenNotes(if (text.isBlank()) null else text)
                }
                "GALLERY", "OPEN_GALLERY" -> DeviceAction.OpenGallery
                "FILES", "OPEN_FILES" -> DeviceAction.OpenFiles
                "WEB_SEARCH" -> {
                    val query = obj.optString("query", "")
                    DeviceAction.WebSearch(query)
                }
                "CAMERA", "OPEN_CAMERA" -> {
                    val mode = obj.optString("mode", "photo")
                    DeviceAction.CameraAction(mode)
                }
                "ACCESSIBILITY_CLICK", "CLICK" -> {
                    val target = obj.optString("target", obj.optString("text", ""))
                    if (target.isNotBlank()) DeviceAction.AccessibilityClick(target) else null
                }
                "ACCESSIBILITY_TYPE", "TYPE" -> {
                    val text = obj.optString("text", "")
                    if (text.isNotBlank()) DeviceAction.AccessibilityType(text) else null
                }
                "ACCESSIBILITY_SCROLL", "SCROLL" -> {
                    val dir = obj.optString("direction", "down")
                    DeviceAction.AccessibilityScroll(dir)
                }
                "ACCESSIBILITY_NAV", "NAV" -> {
                    val nav = obj.optString("navType", obj.optString("action", "back"))
                    DeviceAction.AccessibilityNav(nav)
                }
                "NOTIFICATIONS", "READ_NOTIFICATIONS" -> {
                    val filter = obj.optString("filter", obj.optString("app", ""))
                    DeviceAction.ReadNotifications(if (filter.isBlank()) null else filter)
                }
                "VOLUME" -> {
                    val dir = obj.optString("direction", "up")
                    DeviceAction.VolumeControl(dir)
                }
                "CAPCUT", "CAPCUT_PROJECT", "CAPCUT_EDIT", "VIDEO_EDIT" -> {
                    val media = obj.optString("media", obj.optString("mediaType", "video"))
                    DeviceAction.CapCutProject(media)
                }
                "WHATSAPP_LIVE_LOCATION", "LIVE_LOCATION" -> {
                    val recipient = obj.optString("recipient", obj.optString("contact", obj.optString("to", "")))
                    val duration = obj.optString("duration", "1 hour")
                    DeviceAction.WhatsAppLiveLocation(recipient, duration)
                }
                "YOUTUBE_PLAY_SUBSCRIBE", "YOUTUBE_SUBSCRIBE" -> {
                    val query = obj.optString("query", obj.optString("channel", obj.optString("search", "")))
                    val sub = obj.optBoolean("subscribe", true)
                    val like = obj.optBoolean("like", false)
                    DeviceAction.YouTubePlayAndSubscribe(query, sub, like)
                }
                "SCROLL_FEED", "AUTO_SCROLL", "SHORTS_SCROLL", "REELS_SCROLL" -> {
                    val platform = obj.optString("platform", "shorts")
                    val times = obj.optInt("times", 1)
                    DeviceAction.AutoScrollFeed(platform, times)
                }
                "HUMAN_TAP", "TAP" -> {
                    val x = obj.optDouble("x", 500.0).toFloat()
                    val y = obj.optDouble("y", 800.0).toFloat()
                    DeviceAction.HumanTap(x, y)
                }
                "HUMAN_SWIPE", "SWIPE" -> {
                    val dir = obj.optString("direction", "up")
                    val dist = obj.optDouble("distance", 0.5).toFloat()
                    DeviceAction.HumanSwipe(dir, dist)
                }
                "HUMAN_DOUBLE_TAP", "DOUBLE_TAP" -> {
                    val x = if (obj.has("x")) obj.getDouble("x").toFloat() else null
                    val y = if (obj.has("y")) obj.getDouble("y").toFloat() else null
                    DeviceAction.HumanDoubleTap(x, y)
                }
                "INSPECT_SCREEN", "SCREEN_SUMMARY" -> {
                    DeviceAction.InspectScreen
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseActionJson: ${e.message}")
            null
        }
    }

    /**
     * Local Deterministic NLP parser for Hindi/Hinglish/English commands.
     * Understands conjunctions like "aur", "and", "phir", "ke baad" to chain multiple tasks!
     */
    fun parseFromUserPrompt(prompt: String): List<DeviceAction> {
        val actions = mutableListOf<DeviceAction>()
        if (prompt.isBlank()) return actions

        val lowerPrompt = prompt.lowercase().trim()

        // 1. High-priority full WhatsApp command pattern: "watsaap kholo lucky2 ko hii likh kar bhejo"
        if ((lowerPrompt.contains("whatsapp") || lowerPrompt.contains("watsapp") || lowerPrompt.contains("whats app")) &&
            (lowerPrompt.contains("bhejo") || lowerPrompt.contains("send") || lowerPrompt.contains("likh") || lowerPrompt.contains("msg") || lowerPrompt.contains("message"))
        ) {
            val contactMatch = Regex("""(?i)\b([a-zA-Z0-9_\u0900-\u097F]+)\s*ko\b""").find(prompt)
                ?: Regex("""(?i)\b(?:to|par)\s+([a-zA-Z0-9_\u0900-\u097F]+)""").find(prompt)
            var rec = contactMatch?.groupValues?.get(1)?.trim() ?: ""
            if (rec.lowercase() in listOf("whatsapp", "watsapp", "kisi", "kisi ko", "karo", "do")) {
                rec = if (rec.lowercase() == "kisi" || rec.lowercase() == "kisi ko") "kisi ko" else ""
            }

            var msg = "Hii"
            when {
                lowerPrompt.contains("hii") || lowerPrompt.contains("hi") -> msg = "Hii"
                lowerPrompt.contains("hello") -> msg = "Hello"
                else -> {
                    val quoted = Regex("""['"]([^'"]+)['"]""").find(prompt)
                    if (quoted != null) {
                        msg = quoted.groupValues[1].trim()
                    } else {
                        val beforeLikh = prompt.substringBefore("likh", prompt.substringBefore("bhejo", ""))
                        val afterKo = beforeLikh.substringAfter("ko", "").trim()
                        if (afterKo.isNotBlank() && !afterKo.contains("whatsapp", ignoreCase = true)) {
                            msg = afterKo
                        }
                    }
                }
            }
            actions.add(DeviceAction.WhatsAppMessage(rec, msg))
            return actions
        }

        // 2. High-priority YouTube Multi-Step: "youtube open karo aur song search kar ke paheli video play kar do"
        if (lowerPrompt.contains("youtube") && (lowerPrompt.contains("search") || lowerPrompt.contains("song") || lowerPrompt.contains("gana") || lowerPrompt.contains("play") || lowerPrompt.contains("video"))) {
            var songQuery = lowerPrompt.replace(Regex("""(?i)\b(open|kholo|youtube|par|pe|me|and|aur|fir|then|search|play|chalao|karo|pahele|pehli|pehla|wali|video|ko|kar|do|karke|ek|koi)\b"""), " ").trim()
            if (songQuery.isBlank() || songQuery == "song" || songQuery == "gana" || songQuery == "video") {
                songQuery = "latest trending song"
            }

            actions.add(DeviceAction.OpenApp("youtube"))
            actions.add(DeviceAction.YouTubeSearch(songQuery))
            if (lowerPrompt.contains("play") || lowerPrompt.contains("chalao") || lowerPrompt.contains("pehli") || lowerPrompt.contains("pahele") || lowerPrompt.contains("video")) {
                actions.add(DeviceAction.YouTubeControl("first"))
            }
            if (lowerPrompt.contains("scroll")) {
                actions.add(DeviceAction.AccessibilityScroll("down"))
            }
            if (lowerPrompt.contains("subscribe")) {
                actions.add(DeviceAction.YouTubePlayAndSubscribe(songQuery, autoSubscribe = true, autoLike = false))
            }
            return actions
        }

        // 3. Direct Navigation Commands (Home, Back, Recents, Scroll)
        if (lowerPrompt.contains("home button") || lowerPrompt.contains("home screen") || lowerPrompt.contains("home par") || lowerPrompt.contains("home aao") || lowerPrompt == "home") {
            actions.add(DeviceAction.AccessibilityNav("home"))
            return actions
        }
        if (lowerPrompt.contains("back button") || lowerPrompt.contains("back karo") || lowerPrompt.contains("ek baar back") || lowerPrompt.contains("piche aao") || lowerPrompt == "back") {
            actions.add(DeviceAction.AccessibilityNav("back"))
            return actions
        }
        if (lowerPrompt.contains("scroll") || lowerPrompt == "scroll" || lowerPrompt.contains("neeche karo") || lowerPrompt.contains("upar karo")) {
            val dir = if (lowerPrompt.contains("up") || lowerPrompt.contains("upar")) "up" else "down"
            actions.add(DeviceAction.AccessibilityScroll(dir))
            return actions
        }

        // 4. Split multiple tasks by conjunction words: "aur", "and", "phir", "then", "ke baad", etc.
        val splitRegex = Regex("\\b(aur phir|aur|and then|and|phir|then|ke baad|fir|baad me|iske baad|saath hi|saath me|karo aur|kar do aur)\\b|[,;\\n]")
        val chunks = lowerPrompt.split(splitRegex).map { it.trim() }.filter { it.isNotBlank() }

        for (chunk in chunks) {
            val action = parseSingleChunk(chunk)
            if (action != null) {
                actions.add(action)
            }
        }

        // If no actions found from split chunks, try full prompt
        if (actions.isEmpty()) {
            val action = parseSingleChunk(lowerPrompt)
            if (action != null) {
                actions.add(action)
            }
        }

        return actions
    }

    private fun parseSingleChunk(text: String): DeviceAction? {
        val t = text.trim()

        // 0. CapCut Autonomous Video Project Creator (Gallery latest video edit)
        if (t.contains("capcut") || t.contains("cap cut")) {
            return DeviceAction.CapCutProject("latest")
        }

        // 1. Screen Inspector
        if (t.contains("screen par kya") || t.contains("screen dekho") || t.contains("inspect screen") || t.contains("screen check")) {
            return DeviceAction.InspectScreen
        }

        // 2. Human Physical Gestures (Tap, Double tap, Swipe, Touches, Typing)
        if (t.contains("double tap") || t.contains("do baar tap")) {
            return DeviceAction.HumanDoubleTap()
        }
        if (t.contains("swipe up") || t.contains("upar swipe")) {
            return DeviceAction.HumanSwipe("up")
        }
        if (t.contains("swipe down") || t.contains("neeche swipe")) {
            return DeviceAction.HumanSwipe("down")
        }
        if (t.contains("scroll") || t.contains("स्क्रॉल")) {
            val isUp = t.contains("up") || t.contains("upar")
            return DeviceAction.AccessibilityScroll(if (isUp) "up" else "down")
        }
        if (t.contains("tap karo") || t.contains("touch karo") || t.contains("touch") || t.contains("tap")) {
            // Check for explicit numeric coordinates e.g. "500 800 par tap karo"
            val coordRegex = Regex("(\\d{2,4})\\s+(\\d{2,4})")
            val coordMatch = coordRegex.find(t)
            if (coordMatch != null) {
                val x = coordMatch.groupValues[1].toFloatOrNull() ?: 500f
                val y = coordMatch.groupValues[2].toFloatOrNull() ?: 900f
                return DeviceAction.HumanTap(x, y)
            }

            val tapMatch = Regex("(?:par|pe)?\\s*([a-zA-Z0-9_ ]+)\\s*(?:tap karo|touch karo|par touch|par tap)").find(t)
            val target = tapMatch?.groupValues?.get(1)?.trim() ?: ""
            if (target.isNotBlank() && target != "screen" && target != "phone") {
                return DeviceAction.AccessibilityClick(target)
            }
            return DeviceAction.HumanTap(540f, 960f)
        }
        if (t.contains("type karo") || t.contains("likho") || t.contains("screen par type") || t.contains("screen par likh")) {
            val typeRegex = Regex("(?:type karo|likho|type kar do|likh do)\\s*(.*)")
            val m = typeRegex.find(t)
            val textToType = m?.groupValues?.get(1)?.trim() ?: ""
            if (textToType.isNotBlank()) {
                return DeviceAction.AccessibilityType(textToType)
            }
        }

        // 3. Shorts & Reels Auto-Scroll
        if (t.contains("shorts") || t.contains("reels") || t.contains("reel") || t.contains("tiktok")) {
            if (t.contains("scroll") || t.contains("agla") || t.contains("next") || t.contains("swipe") || t.contains("chalao") || t.contains("badhao")) {
                val platform = if (t.contains("reel")) "reels" else "shorts"
                return DeviceAction.AutoScrollFeed(platform, 1)
            }
        }

        // 4. Flashlight / Torch
        if (t.contains("flashlight") || t.contains("torch") || t.contains("batti") || t.contains("light")) {
            val isOff = t.contains("band") || t.contains("off") || t.contains("bujhao") || t.contains("hatao")
            val isOn = t.contains("on") || t.contains("chalu") || t.contains("jalao") || t.contains("kholo") || t.contains("start")
            if (isOff) return DeviceAction.ToggleFlashlight(false)
            if (isOn) return DeviceAction.ToggleFlashlight(true)
            if (t.contains("torch") || t.contains("flashlight")) return DeviceAction.ToggleFlashlight(null)
        }

        // 5. Wi-Fi
        if (t.contains("wifi") || t.contains("wi-fi") || (t.contains("internet") && (t.contains("kholo") || t.contains("on") || t.contains("setting")))) {
            return DeviceAction.OpenWifi
        }

        // 6. Bluetooth
        if (t.contains("bluetooth") || t.contains("blootooth") || t.contains("blutooth")) {
            return DeviceAction.OpenBluetooth
        }

        // 7. Alarm
        if (t.contains("alarm") || t.contains("alram")) {
            val (hour, minute) = extractTime(t)
            return DeviceAction.SetAlarm(hour, minute, "Alarm")
        }

        // 8. WhatsApp & Live Location
        if (t.contains("whatsapp") || t.contains("watsapp") || t.contains("whats app")) {
            // Live location sharing
            if (t.contains("live location") || (t.contains("location") && (t.contains("send") || t.contains("bhejo") || t.contains("share")))) {
                val rec = when {
                    t.contains("to ") -> t.substringAfter("to ").substringBefore(" on").substringBefore(" par").substringBefore(" whatsapp").trim()
                    t.contains(" ko") -> {
                        val beforeKo = t.substringBefore(" ko").trim()
                        beforeKo.split(" ").lastOrNull()?.trim() ?: ""
                    }
                    else -> ""
                }
                val duration = when {
                    t.contains("8") -> "8 hours"
                    t.contains("15") -> "15 minutes"
                    else -> "1 hour"
                }
                return DeviceAction.WhatsAppLiveLocation(rec, duration)
            }

            // WhatsApp chat scrolling
            if (t.contains("scroll")) {
                return DeviceAction.AccessibilityScroll(if (t.contains("up") || t.contains("upar")) "up" else "down")
            }

            if (t.contains("call") || t.contains("video call")) {
                val callRecipient = Regex("(?:par|pe)?\\s*([a-zA-Z0-9_+]+)\\s*ko").find(t)?.groupValues?.get(1) ?: ""
                return DeviceAction.WhatsAppCall(callRecipient)
            }

            val recipientRegex = Regex("(?:par|pe)?\\s*([a-zA-Z0-9_+]+)\\s*ko")
            val recipientMatch = recipientRegex.find(t)
            val recipient = recipientMatch?.groupValues?.get(1) ?: ""

            val msgRegex = Regex("(?:send kar do|send karo|bhejo|likho|msg kar do|message karo)\\s*(.*)")
            val altMsgRegex = Regex("(?:par|pe)\\s*(?:[a-zA-Z0-9_+]+ ko)?\\s*\"?([^\"]+?)\"?\\s*(?:send|bhejo)")
            
            var msg = "Hii"
            if (t.contains("hii") || t.contains("hi")) msg = "Hii"
            else if (t.contains("hello")) msg = "Hello"
            else {
                val m = msgRegex.find(t) ?: altMsgRegex.find(t)
                if (m != null) {
                    val extracted = m.groupValues[1].trim()
                    if (extracted.isNotBlank() && !extracted.contains("whatsapp")) {
                        msg = extracted
                    }
                }
            }
            return DeviceAction.WhatsAppMessage(recipient, msg)
        }

        // 9. Independent Video Controls & Subscribe (Works across chained steps and active screens)
        if (t.contains("first video") || t.contains("pehli video") || t.contains("pehla video") ||
            t.contains("pahele wali video") || t.contains("pahli video") || t.contains("is video ko open") ||
            t.contains("video open") || t.contains("video play")) {
            return DeviceAction.YouTubeControl("first")
        }
        if (t.contains("subscribe") || t.contains("सदस्य") || t.contains("channel subscribe")) {
            return DeviceAction.YouTubePlayAndSubscribe("", autoSubscribe = true, autoLike = false)
        }
        if (t.contains("pause") || t.contains("rok do") || (t.contains("stop") && !t.contains("app"))) {
            return DeviceAction.YouTubeControl("pause")
        }
        if (t.contains("next video") || t.contains("agli video")) {
            return DeviceAction.YouTubeControl("next")
        }

        // Search Bar / Search Continue ("ab koi song search karo search bar kholo")
        if (t.contains("search bar") || t.contains("search kholo") || (t.contains("search karo") && (t.contains("song") || t.contains("gana") || t.contains("video")))) {
            var q = t.replace(Regex("""(?i)\b(ab|koi|song|search|bar|kholo|open|karo|par|pe|me|aur|fir|then)\b"""), " ").trim()
            if (q.isBlank() || q == "gana" || q == "song" || q == "video") {
                q = "latest trending song"
            }
            return DeviceAction.YouTubeSearch(q)
        }

        // 10. YouTube & Media Search
        if (t.contains("youtube") || t.contains("yt") || t.contains("song search") || t.contains("gana search")) {
            val shouldSubscribe = t.contains("subscribe") || t.contains("सदस्य")
            val shouldLike = t.contains("like") || t.contains("पसंद")
            val shouldPlay = t.contains("play") || t.contains("chalao") || t.contains("bajao") || t.contains("sunao") || t.contains("song") || t.contains("gana")

            if (shouldSubscribe || shouldPlay) {
                var clean = t.replace("youtube", "").replace("yt", "")
                             .replace("video play karo", "").replace("play karo", "")
                             .replace("pahele wali video ko play kar do", "").replace("pehli video play karo", "")
                             .replace("aur channel subscribe kar do", "").replace("channel subscribe kar do", "")
                             .replace("subscribe kar do", "").replace("subscribe karo", "")
                             .replace("aur subscribe kar do", "").replace("aur subscribe karo", "")
                             .replace("song play karo", "").replace("gana play karo", "")
                             .replace("play karo", "").replace("play kar do", "")
                             .replace("chalao", "").replace("bajao", "").replace("sunao", "")
                             .replace("par", "").replace("pe", "").replace("ka video", "").trim()
                if (clean.isBlank() || clean == "video" || clean == "song" || clean == "gana" || clean == "search") {
                    clean = "latest trending song"
                }
                return DeviceAction.YouTubePlayAndSubscribe(clean, autoSubscribe = shouldSubscribe, autoLike = shouldLike)
            }

            val ytSearchRegex = Regex("(?:youtube|yt)(?:\\s*par)?\\s*(.+?)\\s*(?:search karo|search kar|play karo|chalao|dhoondo|kholo)")
            val match = ytSearchRegex.find(t)
            val query = match?.groupValues?.get(1)?.trim() ?: ""
            val cleanQuery = if (query.contains("kuch") || query == "video" || query == "gana") "" else query
            return DeviceAction.YouTubeSearch(cleanQuery)
        }

        // 11. Instagram
        if (t.contains("instagram") || t.contains("insta")) {
            if (t.contains("profile") || t.contains("meri profile")) return DeviceAction.InstagramAction("profile")
            if (t.contains("message") || t.contains("dm") || t.contains("chat")) return DeviceAction.InstagramAction("messages")
            if (t.contains("story") || t.contains("camera")) return DeviceAction.InstagramAction("story")
            return DeviceAction.InstagramAction("open")
        }

        // 12. Call / Phone ("papa ko call lagao", "call papa", "phone milao")
        if (t.contains("call") || t.contains("phone milao") || t.contains("dial") || t.contains("phone lagao")) {
            val phoneRegex = Regex("""(?:call|dial|milao|lagao)\s*(?:karo|lagao)?\s*([+0-9a-zA-Z\u0900-\u097F]+)""")
            val altRegex = Regex("""([+0-9a-zA-Z\u0900-\u097F]+)\s*ko\s*(?:call|phone)""")
            val match = altRegex.find(t) ?: phoneRegex.find(t)
            val target = match?.groupValues?.get(1)?.trim() ?: ""
            val cleanTarget = if (target.lowercase() in listOf("karo", "lagao", "kisi", "kisi ko", "")) "papa" else target
            return DeviceAction.DialPhone(cleanTarget, directCall = true)
        }

        // 9. SMS
        if (t.contains("sms") || t.contains("message bhejo") || t.contains("sandesh")) {
            val smsRecipient = Regex("(?:par|pe)?\\s*([a-zA-Z0-9_+]+)\\s*ko").find(t)?.groupValues?.get(1) ?: ""
            val msgMatch = Regex("(?:bhejo|likho)\\s*(.*)").find(t)
            val smsText = msgMatch?.groupValues?.get(1)?.trim() ?: "Hello"
            return DeviceAction.SendSms(smsRecipient, smsText)
        }

        // 10. Notes
        if (t.contains("note") || t.contains("notes")) {
            return DeviceAction.OpenNotes()
        }

        // 11. Camera
        if (t.contains("camera") || t.contains("photo khicho") || t.contains("selfie")) {
            return DeviceAction.CameraAction("photo")
        }
        if (t.contains("video banao") || t.contains("video recording")) {
            return DeviceAction.CameraAction("video")
        }

        // 12. Gallery / Files
        if (t.contains("gallery") || t.contains("photos")) return DeviceAction.OpenGallery
        if (t.contains("files") || t.contains("file manager")) return DeviceAction.OpenFiles

        // 13. Messages & Notifications ("massage abhi kya kya aaye hai", "sms kya aaya hai")
        val isCheckingMessages = t.contains("notification") || t.contains("suchna") || t.contains("suchnaye") ||
                t.contains("massage") || t.contains("message") || t.contains("messages") || t.contains("msg") ||
                t.contains("sms") || t.contains("unread") || t.contains("kya aaya") || t.contains("kya aaye")
        if (isCheckingMessages && (t.contains("aaye") || t.contains("aaya") || t.contains("check") || t.contains("read") || t.contains("padho") || t.contains("batao") || t.contains("dikhao") || t.contains("kya") || t.contains("sunao"))) {
            val filter = if (t.contains("whatsapp") || t.contains("watsapp")) "whatsapp" else null
            return DeviceAction.ReadNotifications(filter)
        }

        // 14. Scrolling & Gestures ("scroll karo", "neeche scroll", "upar scroll")
        if (t.contains("scroll") || t.contains("neeche karo") || t.contains("upar karo") || t.contains("neeche jao") || t.contains("upar jao")) {
            val dir = if (t.contains("up") || t.contains("upar")) "up" else "down"
            return DeviceAction.AccessibilityScroll(dir)
        }

        // 15. System Navigation ("ab home screen par aao", "home par chalo", "back jao", "recents")
        if (t.contains("home") || t.contains("main screen") || t.contains("home screen")) {
            return DeviceAction.AccessibilityNav("home")
        }
        if (t == "back" || t.contains("piche") || t.contains("back jao") || t.contains("wapas")) {
            return DeviceAction.AccessibilityNav("back")
        }
        if (t.contains("recent") || t.contains("recents") || t.contains("recent apps") || t.contains("tasks")) {
            return DeviceAction.AccessibilityNav("recents")
        }
        if (t.contains("notification shade") || t.contains("notification bar") || t.contains("notifications open")) {
            return DeviceAction.AccessibilityNav("notifications")
        }
        if (t.contains("click karo") || t.contains("dabao")) {
            val targetMatch = Regex("(?:par|pe)?\\s*([a-zA-Z0-9_ ]+)\\s*(?:click karo|dabao)").find(t)
            val target = targetMatch?.groupValues?.get(1)?.trim() ?: ""
            if (target.isNotBlank()) return DeviceAction.AccessibilityClick(target)
        }

        // 15. Volume
        if (t.contains("volume") || t.contains("aawaz")) {
            if (t.contains("badhao") || t.contains("up") || t.contains("increase") || t.contains("tez")) {
                return DeviceAction.VolumeControl("up")
            }
            if (t.contains("kam") || t.contains("ghatao") || t.contains("down") || t.contains("dheemi")) {
                return DeviceAction.VolumeControl("down")
            }
            if (t.contains("mute") || t.contains("silent") || t.contains("band")) {
                return DeviceAction.VolumeControl("mute")
            }
        }

        // 16. Open Settings
        if (t == "settings" || t == "setting" || t.contains("settings kholo") || t.contains("setting open karo")) {
            return DeviceAction.OpenSettings
        }

        // 17. Open specific / any App
        val appKeywords = listOf(
            "instagram", "facebook", "snapchat", "telegram", "spotify", "chrome",
            "camera", "calculator", "calendar", "gallery", "photos", "maps", "clock",
            "gmail", "play store", "playstore", "drive", "files", "twitter", "netflix"
        )
        for (appName in appKeywords) {
            if (t.contains(appName)) {
                return DeviceAction.OpenApp(appName)
            }
        }

        // Generic "X kholo" or "X open karo"
        val openRegex = Regex("(?:open|kholo|chalao|start|launch)\\s*([a-zA-Z0-9_]+)")
        val openAltRegex = Regex("([a-zA-Z0-9_]+)\\s*(?:open karo|kholo|chalao|start karo|launch karo)")
        val openMatch = openRegex.find(t) ?: openAltRegex.find(t)
        if (openMatch != null) {
            val app = openMatch.groupValues[1].trim()
            if (app.isNotBlank() && app != "kar" && app != "karo" && app != "use" && app != "kisi") {
                return DeviceAction.OpenApp(app)
            }
        }

        return null
    }

    private fun extractTime(text: String): Pair<Int, Int> {
        var hour = 8
        var minute = 0

        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?")
        val match = timeRegex.find(text)
        if (match != null) {
            hour = match.groupValues[1].toIntOrNull() ?: 8
            minute = match.groupValues[2].toIntOrNull() ?: 0
            val modifier = match.groupValues[3].lowercase()

            val isPm = modifier == "pm" || text.contains("shaam") || text.contains("dopahar") || text.contains("raat")
            val isAm = modifier == "am" || text.contains("subah")

            if (isPm && hour < 12) {
                hour += 12
            } else if (isAm && hour == 12) {
                hour = 0
            }
        }
        return Pair(hour, minute)
    }
}
