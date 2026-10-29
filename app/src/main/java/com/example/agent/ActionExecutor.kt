package com.example.agent

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.agent.skills.AgentEnv
import com.example.agent.skills.ContactAliasStore
import com.example.agent.skills.SkillRegistry
import com.example.util.SaifAccessibilityService
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Modernized ActionExecutor (v2) with:
 * - Transparent ActionNormalizer preprocessing.
 * - Snapshot-scoped element resolution (e.g. 's1e4' and numeric indexes).
 * - Multi-tier tap ladder (Node ACTION_CLICK -> Clickable parent -> Awaited physical center tap).
 * - Verified text entry with clipboard paste fallback and IME submission.
 * - Verified 4-tier AppLauncher integration.
 */
object ActionExecutor {
    private const val TAG = "SAIF_AGENT"

    suspend fun execute(rawCall: AgentCall, context: Context, listener: AgentListener? = null): ActionResult {
        val call = ActionNormalizer.normalize(rawCall)
        val name = call.name.lowercase()
        val args = call.arguments
        val service = SaifAccessibilityService.instance

        Log.d(TAG, "Executing normalized call '$name' with args: $args")

        if (!ActionNormalizer.isCanonical(name)) {
            val valid = ToolSchema.ALL_TOOLS.map { it.name }.joinToString(", ")
            return ActionResult(false, "Unknown tool '$name'. Valid tools are: $valid")
        }

        return try {
            when (name) {
                "click" -> {
                    val normX = args.optInt("x", 500).coerceIn(0, 999)
                    val normY = args.optInt("y", 500).coerceIn(0, 999)
                    val metrics = service?.getRealDisplayMetrics() ?: Pair(1080, 2400)
                    val px = (normX.toFloat() / 1000f) * metrics.first
                    val py = (normY.toFloat() / 1000f) * metrics.second

                    val ok = service?.performHumanTapAwait(px, py) ?: false
                    service?.waitForIdle(1000, 200)
                    ActionResult(ok, "Tapped coordinates ($normX, $normY)", fingerprintChanged = ok)
                }

                "tap_element" -> {
                    val targetId = args.optString("id", "").ifBlank { args.optInt("index", -1).toString() }
                    if (targetId == "-1" || targetId.isBlank()) {
                        return ActionResult(false, "Missing element id for tap_element")
                    }

                    val node = ScreenObserver.getNode(targetId)
                    if (node != null) {
                        // 1. Try node action click
                        var ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                        // 2. Try clickable parents up to 3 tiers
                        if (!ok) {
                            var parent = node.parent
                            var depth = 0
                            while (parent != null && depth < 3) {
                                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                    ok = true
                                    break
                                }
                                parent = parent.parent
                                depth++
                            }
                        }

                        // 3. Fallback to awaited physical center tap
                        if (!ok) {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            if (bounds.width() > 0 && bounds.height() > 0) {
                                ok = service?.performHumanTapAwait(bounds.centerX().toFloat(), bounds.centerY().toFloat()) ?: false
                            }
                        }

                        service?.waitForIdle(1200, 250)
                        ActionResult(ok, "Tapped element [$targetId]", fingerprintChanged = ok)
                    } else {
                        ActionResult(false, "Element [$targetId] not found in current screen cache")
                    }
                }

                "type_into" -> {
                    val text = args.optString("text", "")
                    val clearFirst = args.optBoolean("clear_first", true)
                    val pressEnter = args.optBoolean("press_enter", false)
                    val targetId = args.optString("id", "")

                    var targetNode = if (targetId.isNotBlank()) ScreenObserver.getNode(targetId) else null
                    if (targetNode == null) {
                        val root = service?.rootInActiveWindow
                        targetNode = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    }

                    var ok = false
                    if (targetNode != null) {
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        delay(120)
                        if (clearFirst) {
                            val clearArgs = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                            }
                            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
                        }
                        val setArgs = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                        }
                        ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

                        // Verify typed text was inserted; fallback to clipboard paste
                        targetNode.refresh()
                        val currentFieldText = targetNode.text?.toString() ?: ""
                        if (!ok || (!currentFieldText.contains(text) && text.length > 1)) {
                            Log.d(TAG, "ACTION_SET_TEXT unverified. Attempting clipboard paste fallback...")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("saif_text", text))
                            ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        }
                    }

                    if (pressEnter) {
                        delay(250)
                        service?.pressEnterOrSearch()
                    }

                    service?.waitForIdle(1000, 200)
                    ActionResult(ok, "Typed text: '$text'", fingerprintChanged = ok)
                }

                "swipe" -> {
                    val direction = args.optString("direction", "up").lowercase()
                    val distance = args.optString("distance", "medium").lowercase()
                    val ratio = when (distance) {
                        "short" -> 0.25f
                        "long" -> 0.70f
                        else -> 0.45f
                    }
                    val ok = service?.performHumanScrollDirection(direction, ratio) ?: false
                    service?.waitForIdle(1000, 200)
                    ActionResult(ok, "Swiped $direction ($distance)", fingerprintChanged = ok)
                }

                "scroll_until" -> {
                    val query = args.optString("text", "")
                    val direction = args.optString("direction", "down")
                    val maxSwipes = args.optInt("max_swipes", 8).coerceIn(1, 15)

                    var found = false
                    for (i in 0 until maxSwipes) {
                        if (service?.waitForText(query, 500) == true) {
                            found = true
                            break
                        }
                        service?.performHumanScrollDirection(direction, 0.45f)
                        delay(400)
                        service?.waitForIdle(1000, 200)
                    }
                    ActionResult(found, if (found) "Found '$query' after scrolling" else "'$query' not found after $maxSwipes swipes", fingerprintChanged = found)
                }

                "open_app" -> {
                    val appName = args.optString("app_name", "")
                    val launchResult = AppLauncher.launchVerified(context, appName)
                    service?.waitForIdle(1500, 300)
                    ActionResult(launchResult.success, launchResult.message, fingerprintChanged = launchResult.success, data = launchResult.targetPackage)
                }

                "press_system" -> {
                    val button = args.optString("button", "back").lowercase()
                    val ok = when (button) {
                        "enter", "search" -> service?.pressEnterOrSearch() ?: false
                        "home" -> service?.pressHome() ?: false
                        "back" -> service?.pressBack() ?: false
                        "recents" -> service?.pressRecents() ?: false
                        "notifications" -> service?.openNotificationsShade() ?: false
                        "quick_settings" -> service?.openQuickSettings() ?: false
                        "lock_screen" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false else false
                        "screenshot" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false else false
                        else -> service?.pressBack() ?: false
                    }
                    service?.waitForIdle(1000, 200)
                    ActionResult(ok, "System action: $button", fingerprintChanged = ok)
                }

                "wait" -> {
                    val durationMs = if (args.has("duration_ms")) {
                        args.optLong("duration_ms", 1000L)
                    } else {
                        (args.optDouble("seconds", 1.0) * 1000).toLong()
                    }
                    delay(durationMs.coerceIn(200L, 5000L))
                    ActionResult(true, "Waited ${durationMs}ms")
                }

                "speak" -> {
                    val text = args.optString("text", "")
                    if (text.isNotBlank()) {
                        listener?.onMilestone(text)
                    }
                    ActionResult(true, "Spoke: $text")
                }

                "ask_user" -> {
                    val question = args.optString("question", "")
                    val optsArray = args.optJSONArray("options")
                    val opts = if (optsArray != null) (0 until optsArray.length()).map { optsArray.getString(it) } else null
                    val answer = UserDialogBridge.askUser(question, opts, listener)
                    ActionResult(true, "User responded: $answer", data = answer)
                }

                "note" -> {
                    val text = args.optString("text", "")
                    ActionResult(true, "Saved note: $text", data = text)
                }

                "finish" -> {
                    val success = args.optBoolean("success", true)
                    val summary = args.optString("summary", "Task completed")
                    ActionResult(success, summary, data = args.opt("data"))
                }

                "yield_to_user" -> {
                    val reason = args.optString("reason", "Manual user action needed")
                    ActionResult(false, reason)
                }

                "run_skill" -> {
                    val skillName = args.optString("name", "")
                    val skillArgs = args.optJSONObject("args") ?: JSONObject()
                    val skillRes = SkillRegistry.execute(skillName, skillArgs, AgentEnv(context, service, listener))
                    ActionResult(skillRes.success, skillRes.message, fingerprintChanged = skillRes.success, data = skillRes.data)
                }

                else -> {
                    ActionResult(false, "Unsupported tool call: $name")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing action '$name': ${e.message}", e)
            ActionResult(false, "Action '$name' failed: ${e.message}")
        }
    }
}
