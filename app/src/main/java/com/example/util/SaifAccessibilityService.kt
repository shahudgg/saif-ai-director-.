package com.example.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

import android.view.Display
import android.view.KeyEvent
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

data class ScreenshotCaptureResult(
    val base64: String?,
    val isSecure: Boolean
)

@Suppress("DEPRECATION")
class SaifAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SaifAccessibility"

        @Volatile
        var instance: SaifAccessibilityService? = null
            private set

        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true

            val expectedServiceName = "${context.packageName}/${SaifAccessibilityService::class.java.name}"
            val enabledServices = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (e: Exception) {
                null
            } ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                    componentName.contains(context.packageName, ignoreCase = true) ||
                    componentName.contains(SaifAccessibilityService::class.java.simpleName, ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isServiceEnabled(context)) {
                    android.widget.Toast.makeText(
                        context,
                        "Agar toggle grayed out ho, toh App Info me 3 dots se 'Allow restricted settings' enable karein.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            }
        }
    }

    private var currentPackage: String = ""
    var currentActivityClass: String = ""
        private set
    var currentWindowTitle: String = ""
        private set
    var lastEventTime: Long = System.currentTimeMillis()
        private set
    var lastWindowChangeAt: Long = System.currentTimeMillis()
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "SaifAccessibilityService connected successfully!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            lastEventTime = System.currentTimeMillis()
            val type = event.eventType
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                lastWindowChangeAt = lastEventTime
            }
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                event.className?.let { currentActivityClass = it.toString() }
                event.text?.let { if (it.isNotEmpty()) currentWindowTitle = it.joinToString(" ") }
            }
            event.packageName?.let {
                val pkgName = it.toString()
                if (pkgName.isNotBlank() && !pkgName.contains("inputmethod")) {
                    currentPackage = pkgName
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception caught in onAccessibilityEvent: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SaifAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "SaifAccessibilityService destroyed")
    }

    fun getCurrentPackage(): String {
        val activePkg = rootInActiveWindow?.packageName?.toString()
        if (!activePkg.isNullOrBlank() && !activePkg.contains("inputmethod")) {
            currentPackage = activePkg
        }
        return currentPackage
    }

    fun getCurrentAppLabel(context: Context): String {
        val pkg = getCurrentPackage()
        if (pkg.isBlank()) return "Home Screen"
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            when {
                pkg.contains("youtube") -> "YouTube"
                pkg.contains("whatsapp") -> "WhatsApp"
                pkg.contains("launcher") || pkg.contains("home") -> "Home Screen"
                pkg.contains("chrome") -> "Chrome"
                pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("telecom") -> "Phone Dialer"
                pkg.contains("camera") -> "Camera"
                pkg.contains("settings") -> "Settings"
                pkg.contains("instagram") -> "Instagram"
                else -> pkg
            }
        }
    }

    fun isAppInForeground(packageNameSubstring: String): Boolean {
        val active = getCurrentPackage()
        return active.contains(packageNameSubstring, ignoreCase = true) ||
                (rootInActiveWindow?.packageName?.toString() ?: "").contains(packageNameSubstring, ignoreCase = true)
    }

    fun isYouTubeInForeground(): Boolean = isAppInForeground("youtube")
    fun isWhatsAppInForeground(): Boolean = isAppInForeground("whatsapp")
    fun isDialerInForeground(): Boolean = isAppInForeground("dialer") || isAppInForeground("telecom") || isAppInForeground("phone")

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun pressRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun openNotificationsShade(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * Guaranteed screen scrolling:
     * Dispatches real physical touch swipe gestures (which work on YouTube, Instagram, WhatsApp, browsers, etc.)
     * and also triggers accessibility node scroll for complete coverage.
     */
    fun scroll(forward: Boolean = true): Boolean {
        val dir = if (forward) "down" else "up"
        // 1. Physical human swipe gesture (guaranteed visual scroll on modern Android custom views & Compose)
        val gestureScrolled = performHumanScrollDirection(dir, distanceRatio = 0.50f)

        // 2. Also try accessibility node scroll as companion
        val root = rootInActiveWindow
        if (root != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            val scrollableNode = findFirstScrollableNode(root)
            scrollableNode?.performAction(action)
        }

        return gestureScrolled
    }

    // ==================== HUMAN TOUCH & PHYSICAL GESTURES ====================

    /**
     * Dispatches an awaited real human physical finger tap at (x, y) coordinates.
     */
    suspend fun performHumanTapAwait(x: Float, y: Float, durationMs: Long = 80L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(40L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture)
        } catch (e: Exception) {
            Log.e(TAG, "Error in performHumanTapAwait: ${e.message}")
            false
        }
    }

    /**
     * Dispatches a real human physical finger tap at the given (x, y) coordinates.
     */
    fun performHumanTap(x: Float, y: Float, durationMs: Long = 80L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(40L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error in performHumanTap: ${e.message}")
            false
        }
    }

    /**
     * Dispatches an awaited human physical swipe gesture between start and end coordinates.
     */
    suspend fun performHumanSwipeAwait(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 320L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply {
                moveTo(startX, startY)
                val ctrlX = (startX + endX) / 2f + (Math.random().toFloat() * 12f - 6f)
                val ctrlY = (startY + endY) / 2f + (Math.random().toFloat() * 12f - 6f)
                quadTo(ctrlX, ctrlY, endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture)
        } catch (e: Exception) {
            Log.e(TAG, "Error in performHumanSwipeAwait: ${e.message}")
            false
        }
    }

    /**
     * Dispatches a real human physical swipe gesture between start and end coordinates.
     */
    fun performHumanSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 320L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error in performHumanSwipe: ${e.message}")
            false
        }
    }

    /**
     * Performs a natural human scroll or flick gesture in the requested direction using real display metrics.
     */
    fun performHumanScrollDirection(direction: String = "down", distanceRatio: Float = 0.5f): Boolean {
        val metrics = getRealDisplayMetrics()
        val screenWidth = metrics.first.toFloat()
        val screenHeight = metrics.second.toFloat()
        val centerX = screenWidth / 2f

        return when (direction.lowercase()) {
            "down", "neeche", "forward" -> {
                // To scroll down, finger swipes upwards
                val startY = screenHeight * 0.75f
                val endY = screenHeight * (0.75f - distanceRatio.coerceIn(0.2f, 0.65f))
                performHumanSwipe(centerX, startY, centerX, endY, 300L)
            }
            "up", "upar", "backward" -> {
                // To scroll up, finger swipes downwards
                val startY = screenHeight * 0.25f
                val endY = screenHeight * (0.25f + distanceRatio.coerceIn(0.2f, 0.65f))
                performHumanSwipe(centerX, startY, centerX, endY, 300L)
            }
            "left", "baye" -> {
                val startX = screenWidth * 0.8f
                val endX = screenWidth * 0.2f
                performHumanSwipe(startX, screenHeight / 2f, endX, screenHeight / 2f, 300L)
            }
            "right", "daye" -> {
                val startX = screenWidth * 0.2f
                val endX = screenWidth * 0.8f
                performHumanSwipe(startX, screenHeight / 2f, endX, screenHeight / 2f, 300L)
            }
            else -> false
        }
    }

    /**
     * Performs a human-like double tap.
     */
    fun performHumanDoubleTap(x: Float, y: Float): Boolean {
        val tap1 = performHumanTap(x, y, 60L)
        Handler(Looper.getMainLooper()).postDelayed({
            performHumanTap(x, y, 60L)
        }, 110L)
        return tap1
    }

    /**
     * Performs a human-like long press.
     */
    fun performHumanLongPress(x: Float, y: Float, durationMs: Long = 750L): Boolean {
        return performHumanTap(x, y, durationMs)
    }

    // ==================== SMART NODE MATCHING & CLICKING ====================

    fun findAndClick(target: String, excludeKeywords: List<String> = emptyList()): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanTarget = target.trim().lowercase()
        if (cleanTarget.isBlank()) return false

        val autoExclude = if (cleanTarget.contains("search") || cleanTarget.contains("खोजें")) {
            listOf("voice", "mic", "speak", "आवाज़", "microphone") + excludeKeywords
        } else {
            excludeKeywords
        }

        val matchingNodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByQuery(root, cleanTarget, matchingNodes, autoExclude)

        var clicked = false
        for (node in matchingNodes) {
            if (performClickOnNodeOrParent(node)) {
                clicked = true
                break
            }
        }
        return clicked
    }

    fun clickElementByQuery(query: String): Boolean {
        if (findAndClick(query)) return true
        if (findAndClickFuzzy(query)) return true
        if (findAndClickByViewId(query)) return true
        return false
    }

    fun clickFirstYouTubeVideo(): Boolean = clickYouTubeFirstVideo()

    fun collectHierarchySummary(): String = inspectScreenHierarchy()

    /**
     * Dedicated search button clicker that strictly skips microphone / voice search elements.
     */
    fun findAndClickSearchButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val dm = resources.displayMetrics
        val screenWidth = if (dm.widthPixels > 100) dm.widthPixels.toFloat() else 1080f
        val screenHeight = if (dm.heightPixels > 100) dm.heightPixels.toFloat() else 2400f

        val voiceKeywords = listOf("voice", "mic", "microphone", "speak", "आवाज़", "audio", "clear")

        val searchViewIds = listOf(
            "menu_item_search", "menu_item_0", "menu_item_1", "search_button",
            "search_src_text", "search_edit_text", "search_box", "search_bar",
            "search_query", "search_view"
        )
        for (id in searchViewIds) {
            val matching = mutableListOf<AccessibilityNodeInfo>()
            findNodesByViewIdRecursive(root, id, matching)
            for (node in matching) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                val isVoice = voiceKeywords.any { desc.contains(it) || text.contains(it) }
                if (!isVoice) {
                    if (performClickOnNodeOrParent(node)) {
                        return true
                    }
                }
            }
        }

        val searchTerms = listOf("search youtube", "search", "खोजें", "search here")
        for (term in searchTerms) {
            val matching = mutableListOf<AccessibilityNodeInfo>()
            findNodesByQuery(root, term, matching, voiceKeywords)
            for (node in matching) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                val isVoice = voiceKeywords.any { desc.contains(it) || text.contains(it) }
                if (!isVoice) {
                    if (performClickOnNodeOrParent(node)) {
                        return true
                    }
                }
            }
        }

        // Coordinate fallback:
        // On YouTube & Android top bars, search icon is at X=0.88f (NOT 0.78f where mic is!).
        // If search edit text is already visible, center is at X=0.45f.
        val fallbackX = if (isYouTubeInForeground()) screenWidth * 0.88f else screenWidth * 0.86f
        val fallbackY = screenHeight * 0.055f
        return performHumanTap(fallbackX, fallbackY)
    }

    /**
     * Finds and clicks the phone dialer green Call/Dial button.
     */
    fun clickDialerCallButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val dm = resources.displayMetrics

        val callViewIds = listOf("dialpad_floating_action_button", "call_button", "dial_button", "incall_first_wave", "fab", "dialpad_call_button")
        for (id in callViewIds) {
            val matching = mutableListOf<AccessibilityNodeInfo>()
            findNodesByViewIdRecursive(root, id, matching)
            for (node in matching) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }

        val callTerms = listOf("call", "dial", "कॉल", "sim 1", "sim 2")
        for (term in callTerms) {
            val matching = mutableListOf<AccessibilityNodeInfo>()
            findNodesByQuery(root, term, matching)
            for (node in matching) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // Call button is in the lower half of screen
                if (rect.top >= dm.heightPixels * 0.60f) {
                    if (performClickOnNodeOrParent(node)) {
                        return true
                    }
                }
            }
        }

        // Physical fallback for the green call button on Android dialers (bottom center)
        return performHumanTap(dm.widthPixels * 0.5f, dm.heightPixels * 0.86f)
    }

    /**
     * Checks multiple query candidates in order and clicks the first matching element.
     */
    fun findAndClickAny(queries: List<String>): Boolean {
        for (q in queries) {
            if (findAndClick(q)) return true
        }
        return false
    }

    /**
     * Searches nodes whose viewIdResourceName contains the given substring and clicks it.
     */
    fun findAndClickByViewId(viewIdSubstr: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val clean = viewIdSubstr.trim().lowercase()
        val matchingNodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByViewIdRecursive(root, clean, matchingNodes)

        var clicked = false
        for (node in matchingNodes) {
            if (performClickOnNodeOrParent(node)) {
                clicked = true
                break
            }
        }
        return clicked
    }

    /**
     * Clicks the first media item / card / thumbnail in a grid or list (e.g. CapCut media picker).
     */
    fun clickFirstGridItem(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findFirstScrollableNode(root) ?: root
        var clicked = false
        for (i in 0 until scrollable.childCount) {
            val child = scrollable.getChild(i) ?: continue
            val rect = Rect()
            child.getBoundsInScreen(rect)
            if (rect.width() > 50 && rect.height() > 50) {
                if (performClickOnNodeOrParent(child)) {
                    clicked = true
                    break
                }
            }
        }
        return clicked
    }

    /**
     * Checks if any of the given text tokens exist anywhere in the active window.
     */
    fun hasTextOnScreen(queries: List<String>): Boolean {
        val text = getVisibleScreenText().lowercase()
        return queries.any { text.contains(it.lowercase()) }
    }

    fun findAndType(text: String, targetFieldHint: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        findEditableNodes(root, editableNodes)

        var typed = false
        var targetNode: AccessibilityNodeInfo? = null

        if (!targetFieldHint.isNullOrBlank()) {
            val hint = targetFieldHint.lowercase()
            targetNode = editableNodes.firstOrNull { node ->
                val nodeText = (node.text?.toString() ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").lowercase()
                nodeText.contains(hint) || desc.contains(hint)
            }
        }

        if (targetNode == null && editableNodes.isNotEmpty()) {
            targetNode = editableNodes.firstOrNull { it.isFocused } ?: editableNodes.first()
        }

        if (targetNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            typed = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return typed
    }

    /**
     * Performs realistic human-like typing into the target or focused input field.
     * Types progressively with natural human micro-delays, and can trigger search/enter action.
     */
    suspend fun performHumanTyping(
        text: String,
        targetFieldHint: String? = null,
        pressEnterAfter: Boolean = true,
        charDelayMs: Long = 30L
    ): Boolean {
        val root = rootInActiveWindow ?: return false
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        findEditableNodes(root, editableNodes)

        var targetNode: AccessibilityNodeInfo? = null
        if (!targetFieldHint.isNullOrBlank()) {
            val hint = targetFieldHint.lowercase()
            targetNode = editableNodes.firstOrNull { node ->
                val nodeText = (node.text?.toString() ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").lowercase()
                nodeText.contains(hint) || desc.contains(hint)
            }
        }

        if (targetNode == null && editableNodes.isNotEmpty()) {
            targetNode = editableNodes.firstOrNull { it.isFocused } ?: editableNodes.first()
        }

        if (targetNode == null) {
            return false
        }

        // Focus or tap target node
        if (!targetNode.isFocused) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val rect = Rect()
            targetNode.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                performHumanTap(rect.centerX().toFloat(), rect.centerY().toFloat(), 60L)
            }
            kotlinx.coroutines.delay(100)
        }

        // Human typing simulation with cadence
        val sb = java.lang.StringBuilder()
        for (i in text.indices) {
            sb.append(text[i])
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, sb.toString())
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (charDelayMs > 0 && i < text.length - 1 && (i % 3 == 0)) {
                kotlinx.coroutines.delay(charDelayMs)
            }
        }

        // Final text ensure
        val finalArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, finalArgs)

        if (pressEnterAfter) {
            kotlinx.coroutines.delay(200)
            pressEnterOrSearch()
        }
        return true
    }

    /**
     * Triggers Search, Enter, or clicks the active Search/Send action icon.
     */
    fun pressEnterOrSearch(): Boolean {
        val searchSendQueries = listOf("search", "खोजें", "send", "भेजें", "enter", "go", "submit", "done")
        if (findAndClickAny(searchSendQueries)) return true

        if (findAndClickByViewId("search_button") || findAndClickByViewId("search_go_btn") || findAndClickByViewId("send")) return true

        // Keyboard search/enter button location fallback
        val dm = resources.displayMetrics
        val keyboardSearchX = dm.widthPixels * 0.90f
        val keyboardSearchY = dm.heightPixels * 0.94f
        return performHumanTap(keyboardSearchX, keyboardSearchY, 70L)
    }

    /**
     * Enhanced fuzzy multilingual search and click.
     */
    fun findAndClickFuzzy(query: String): Boolean {
        val q = query.trim().lowercase()
        val synonyms = when {
            q.contains("search") || q.contains("dhoondo") || q.contains("khojo") ->
                listOf("search", "खोजें", "dhoondo", "search button", "search icon")
            q.contains("send") || q.contains("bhejo") ->
                listOf("send", "भेजें", "send message", "इरसाल")
            q.contains("play") || q.contains("chalao") ->
                listOf("play", "चलाएं", "resume")
            q.contains("pause") || q.contains("roko") ->
                listOf("pause", "रोकें")
            q.contains("like") || q.contains("pasand") ->
                listOf("like", "पसंद करें", "thumbs up")
            q.contains("subscribe") ->
                listOf("subscribe", "सदस्यता लें", "सदस्य बनें")
            else -> listOf(query)
        }
        return findAndClickAny(synonyms)
    }

    fun clickFirstClickableInList(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findFirstScrollableNode(root) ?: root
        var clicked = false
        for (i in 0 until scrollable.childCount) {
            val child = scrollable.getChild(i) ?: continue
            if (performClickOnNodeOrParent(child)) {
                clicked = true
                break
            }
        }
        return clicked
    }

    fun getVisibleScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectTextFromNode(root, sb)
        return sb.toString().trim()
    }

    /**
     * Inspects active window hierarchy and returns a human-readable summary of buttons, fields, and text.
     */
    fun inspectScreenHierarchy(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectHierarchySummary(root, sb, 0)
        return sb.toString().trim()
    }

    private fun collectHierarchySummary(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 6) return
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val isClickable = node.isClickable
        val isEditable = node.isEditable

        if (text.isNotBlank() || desc.isNotBlank() || isClickable || isEditable) {
            val label = text.ifBlank { desc }
            val typeStr = when {
                isEditable -> "[Input Field]"
                isClickable -> "[Button/Action]"
                else -> "[Text]"
            }
            if (label.isNotBlank()) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                sb.append("$typeStr \"$label\" at (${rect.centerX()}, ${rect.centerY()})\n")
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectHierarchySummary(child, sb, depth + 1)
        }
    }

    private fun findNodesByQuery(
        node: AccessibilityNodeInfo,
        query: String,
        results: MutableList<AccessibilityNodeInfo>,
        excludeKeywords: List<String> = emptyList()
    ) {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isExcluded = excludeKeywords.any { exc ->
            text.contains(exc) || desc.contains(exc) || viewId.contains(exc)
        }

        if (!isExcluded && (text.contains(query) || desc.contains(query) || viewId.contains(query))) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByQuery(child, query, results, excludeKeywords)
        }
    }

    private fun findNodesByViewIdRecursive(
        node: AccessibilityNodeInfo,
        idSubstr: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains(idSubstr)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByViewIdRecursive(child, idSubstr, results)
        }
    }

    private fun findEditableNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNodes(child, results)
        }
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * First attempts native accessibility action click. If that returns false or node is not clickable,
     * falls back to a real human physical gesture tap at the center of the node's bounds!
     */
    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
            current = current.parent
        }

        // Fallback: Dispatched real human physical tap at center of node bounds!
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            return performHumanTap(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        return false
    }

    // ==================== SPECIALIZED APP AUTOMATION HELPERS ====================

    /**
     * Clicks the WhatsApp Send button via ID, content description, or physical coordinates.
     */
    fun clickWhatsAppSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val dm = resources.displayMetrics

        // 1. Try finding by resource ID containing "send"
        val sendNodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByViewIdRecursive(root, "send", sendNodes)
        for (node in sendNodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // Send button in WhatsApp is always on the right side of the screen
            if (rect.right >= dm.widthPixels * 0.65f) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }

        // 2. Try by content description
        val sendQueries = listOf("send", "भेजें", "send message", "सेंड", "इरसाल करें")
        for (q in sendQueries) {
            val matching = mutableListOf<AccessibilityNodeInfo>()
            findNodesByQuery(root, q, matching)
            for (node in matching) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.right >= dm.widthPixels * 0.65f) {
                    if (performClickOnNodeOrParent(node)) {
                        return true
                    }
                }
            }
        }

        // 3. Physical coordinate fallback:
        // Try both keyboard-open position (middle-right) and keyboard-closed position (bottom-right)
        val tappedKeyboardOpen = performHumanTap(dm.widthPixels * 0.92f, dm.heightPixels * 0.54f)
        val tappedKeyboardClosed = performHumanTap(dm.widthPixels * 0.92f, dm.heightPixels * 0.94f)
        return tappedKeyboardOpen || tappedKeyboardClosed
    }

    /**
     * Finds and clicks the first real video card in YouTube search results,
     * strictly skipping microphone/voice search buttons, filter chips, and top bars.
     */
    fun clickYouTubeFirstVideo(): Boolean {
        val root = rootInActiveWindow ?: return false
        val dm = resources.displayMetrics
        val screenWidth = if (dm.widthPixels > 100) dm.widthPixels.toFloat() else 1080f
        val screenHeight = if (dm.heightPixels > 100) dm.heightPixels.toFloat() else 2400f

        // Search strictly below top bar and filter chips (start at 18% of screen height)
        val minY = (screenHeight * 0.18f).toInt()
        val maxY = (screenHeight * 0.88f).toInt()
        val candidateNodes = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodesBelow(root, minY, candidateNodes)

        // Filter out chips, mic, voice, and header items
        val ignoredWords = listOf("voice", "mic", "search with your voice", "आवाज़", "all", "shorts", "videos", "music", "trending", "live", "filter", "chip", "cast")

        var clicked = false
        // First pass: look for video cards with explicit view count, duration, or video descriptions
        for (node in candidateNodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.top in minY..maxY && rect.height() >= 120 && rect.width() >= (screenWidth * 0.55f).toInt()) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                val combined = "$desc $text"

                val isIgnored = ignoredWords.any { combined.contains(it) }
                val isRealVideo = (combined.contains("views") || combined.contains("ago") || combined.contains("minute") || combined.contains("hour") || combined.contains("duration") || combined.contains("video")) && !isIgnored

                if (isRealVideo) {
                    if (performClickOnNodeOrParent(node)) {
                        clicked = true
                        break
                    }
                }
            }
        }

        // Second pass: if no explicit views text found, click the first large card below the header
        if (!clicked) {
            for (node in candidateNodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.top in minY..maxY && rect.height() >= 140 && rect.width() >= (screenWidth * 0.55f).toInt()) {
                    val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                    val text = node.text?.toString()?.lowercase() ?: ""
                    val combined = "$desc $text"
                    if (!ignoredWords.any { combined.contains(it) }) {
                        if (performClickOnNodeOrParent(node)) {
                            clicked = true
                            break
                        }
                    }
                }
            }
        }

        if (clicked) return true

        // Reliable physical tap fallback at center of the first video card in YouTube search results
        val tapX = screenWidth * 0.5f
        val tapY = screenHeight * 0.35f
        return performHumanTap(tapX, tapY)
    }

    /**
     * Finds and clicks the Subscribe button on the active YouTube player or channel page.
     */
    fun clickYouTubeSubscribe(): Boolean {
        val root = rootInActiveWindow ?: return false
        val subQueries = listOf("subscribe", "सदस्य बनें", "सदस्यता लें")
        for (q in subQueries) {
            val matching = mutableListOf<AccessibilityNodeInfo>()
            findNodesByQuery(root, q, matching)
            for (node in matching) {
                val text = (node.text?.toString() ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").lowercase()
                if (!text.contains("subscribed") && !desc.contains("subscribed") && !text.contains("सदस्यता ली गई")) {
                    if (performClickOnNodeOrParent(node)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun collectClickableNodesBelow(node: AccessibilityNodeInfo, minY: Int, results: MutableList<AccessibilityNodeInfo>) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.top >= minY && (node.isClickable || node.isCheckable || node.childCount == 0)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableNodesBelow(child, minY, results)
        }
    }

    private fun collectTextFromNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            sb.append(text).append("\n")
        } else {
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank()) {
                sb.append(desc).append("\n")
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextFromNode(child, sb)
        }
    }

    // ==================== AGENT UPGRADE METHODS ====================

    fun getRealDisplayMetrics(): Pair<Int, Int> {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = wm.currentWindowMetrics
                val bounds = windowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } else {
                val dm = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                Pair(dm.widthPixels, dm.heightPixels)
            }
        } catch (e: Exception) {
            val dm = resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    suspend fun captureScreenshotBase64(): ScreenshotCaptureResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext ScreenshotCaptureResult(null, false)
        }

        suspendCancellableCoroutine { cont ->
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            try {
                                val hwBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                hwBuffer.close()

                                if (hwBitmap == null) {
                                    if (cont.isActive) cont.resume(ScreenshotCaptureResult(null, true))
                                    return
                                }

                                val softBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                hwBitmap.recycle()

                                if (softBitmap == null) {
                                    if (cont.isActive) cont.resume(ScreenshotCaptureResult(null, true))
                                    return
                                }

                                val targetLongSide = com.example.agent.AgentConfig.SHOT_LONG_SIDE
                                val targetQuality = com.example.agent.AgentConfig.SHOT_JPEG_Q

                                val w = softBitmap.width
                                val h = softBitmap.height
                                val maxDim = maxOf(w, h)
                                val scale = if (maxDim > targetLongSide) targetLongSide.toFloat() / maxDim else 1f
                                val targetW = (w * scale).toInt().coerceAtLeast(1)
                                val targetH = (h * scale).toInt().coerceAtLeast(1)

                                val scaledBitmap = if (scale < 1f) {
                                    Bitmap.createScaledBitmap(softBitmap, targetW, targetH, true)
                                } else {
                                    softBitmap
                                }

                                val baos = ByteArrayOutputStream()
                                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, targetQuality, baos)
                                val jpegBytes = baos.toByteArray()

                                if (scaledBitmap != softBitmap) scaledBitmap.recycle()
                                softBitmap.recycle()

                                val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                                if (cont.isActive) cont.resume(ScreenshotCaptureResult(base64, false))
                            } catch (e: Exception) {
                                Log.w(TAG, "Screenshot processing error: ${e.message}")
                                if (cont.isActive) cont.resume(ScreenshotCaptureResult(null, false))
                            } finally {
                                try { executor.shutdown() } catch (ignored: Exception) {}
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            try {
                                Log.w(TAG, "takeScreenshot failed with code $errorCode")
                                val isSecure = errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                                if (cont.isActive) cont.resume(ScreenshotCaptureResult(null, isSecure))
                            } finally {
                                try { executor.shutdown() } catch (ignored: Exception) {}
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "takeScreenshot error: ${e.message}")
                try { executor.shutdown() } catch (ignored: Exception) {}
                if (cont.isActive) cont.resume(ScreenshotCaptureResult(null, false))
            }
        }
    }

    suspend fun waitForStable(timeoutMs: Long = 3000L, quietMs: Long = 300L): Boolean {
        val start = System.currentTimeMillis()
        var consecutiveStable = 0
        var lastDigest = ""
        while (System.currentTimeMillis() - start < timeoutMs) {
            val quietDuration = System.currentTimeMillis() - lastEventTime
            if (quietDuration >= quietMs) {
                val currentText = getVisibleScreenText().take(120)
                if (currentText == lastDigest && currentText.isNotBlank()) {
                    consecutiveStable++
                    if (consecutiveStable >= 2) return true
                } else {
                    consecutiveStable = 0
                    lastDigest = currentText
                }
            }
            delay(80)
        }
        return false
    }

    suspend fun waitForIdle(timeoutMs: Long = 2500, quietMs: Long = 350) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val quietDuration = System.currentTimeMillis() - lastEventTime
            if (quietDuration >= quietMs) {
                return
            }
            delay(50)
        }
    }

    suspend fun waitForPackage(pkgSubstring: String, timeoutMs: Long = 3000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isAppInForeground(pkgSubstring)) return true
            delay(100)
        }
        return false
    }

    suspend fun waitForText(query: String, timeoutMs: Long = 3000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null) {
                val matches = root.findAccessibilityNodeInfosByText(query)
                val found = matches.isNotEmpty()
                if (found) return true
            }
            delay(100)
        }
        return false
    }

    suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return suspendCancellableCoroutine { cont ->
            try {
                val ok = dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
                if (!ok && cont.isActive) {
                    cont.resume(false)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    suspend fun performHumanBézierSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 320L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply {
                moveTo(startX, startY)
                // Slight cubic curve for human-like finger motion
                val ctrlX = (startX + endX) / 2f + (Math.random().toFloat() * 16f - 8f)
                val ctrlY = (startY + endY) / 2f + (Math.random().toFloat() * 16f - 8f)
                quadTo(ctrlX, ctrlY, endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(120L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAwait(gesture)
        } catch (e: Exception) {
            Log.e(TAG, "Error in performHumanBézierSwipe: ${e.message}")
            false
        }
    }

    private var volDownPressedAt = 0L
    private var volUpPressedAt = 0L

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        val now = System.currentTimeMillis()
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) volDownPressedAt = now
                else if (event.action == KeyEvent.ACTION_UP) volDownPressedAt = 0L
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) volUpPressedAt = now
                else if (event.action == KeyEvent.ACTION_UP) volUpPressedAt = 0L
            }
        }

        if (volDownPressedAt > 0 && volUpPressedAt > 0 &&
            Math.abs(volDownPressedAt - volUpPressedAt) < 600
        ) {
            Log.i("SAIF_AGENT", "Emergency kill switch triggered via volume key chord!")
            // Kill active tasks via runtime
            try {
                com.example.agent.PhoneAgentRuntime.cancelAll()
            } catch (ignored: Exception) {}
            return true
        }

        return super.onKeyEvent(event)
    }
}

