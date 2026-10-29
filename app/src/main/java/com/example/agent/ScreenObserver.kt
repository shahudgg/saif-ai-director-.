package com.example.agent

import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.util.SaifAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

data class UiElement(
    val id: String, // Snapshot-scoped id, e.g. "s1e4"
    val idx: Int,
    val role: String,
    val text: String,
    val desc: String,
    val hint: String,
    val stateDesc: String,
    val viewId: String,
    val bounds: Rect,
    val norm: List<Int>, // [ymin, xmin, ymax, xmax] in 0..999 scale
    val cx: Int, // Normalized center X in 0..999
    val cy: Int, // Normalized center Y in 0..999
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val focused: Boolean,
    val checked: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
    val scrollable: Boolean,
    val parentIdx: Int
)

data class ScreenSnapshot(
    val snapshotId: String,
    val packageName: String,
    val activityClass: String,
    val windowTitle: String,
    val keyboardVisible: Boolean,
    val focusedInputId: String?,
    val hasScrollable: Boolean,
    val secure: Boolean,
    val isLocked: Boolean,
    val displayWidth: Int,
    val displayHeight: Int,
    val elements: List<UiElement>,
    val screenshotJpegBase64: String?,
    val fingerprint: String,
    val screenSignature: String
) {
    fun toCompactText(maxChars: Int = 7500): String {
        val sb = StringBuilder()
        sb.append("PKG: ").append(packageName)
        if (activityClass.isNotBlank()) sb.append(" | ACTIVITY: ").append(activityClass)
        if (windowTitle.isNotBlank()) sb.append(" | TITLE: ").append(windowTitle)
        if (keyboardVisible) sb.append(" [KEYBOARD_OPEN]")
        if (secure) sb.append(" [SECURE/PROTECTED_SCREEN]")
        sb.append(" | SIGNATURE: ").append(screenSignature)
        sb.append("\nLEGEND: [id] role \"text\" desc=\"...\" id=viewId [box=(ymin,xmin,ymax,xmax) center=(cx,cy)] (scale 0-999)\n")
        sb.append("ELEMENTS:\n")

        for (el in elements) {
            val line = StringBuilder()
            line.append("[${el.id}] ").append(el.role)
            if (el.text.isNotBlank()) line.append(" \"").append(el.text).append("\"")
            if (el.desc.isNotBlank() && el.desc != el.text) line.append(" desc=\"").append(el.desc).append("\"")
            if (el.viewId.isNotBlank()) line.append(" id=").append(el.viewId)
            if (el.clickable) line.append(" clickable")
            if (el.editable) line.append(" editable")
            if (el.focused) line.append(" focused")
            if (el.selected) line.append(" selected")
            if (el.scrollable) line.append(" scrollable")
            line.append(" [box=(${el.norm.joinToString(",")}) center=(${el.cx},${el.cy})]\n")

            if (sb.length + line.length > maxChars) {
                sb.append("... [truncated ").append(elements.size - el.idx).append(" elements]\n")
                break
            }
            sb.append(line)
        }
        return sb.toString()
    }
}

object ScreenObserver {
    private const val TAG = "SAIF_AGENT"
    private const val MAX_ELEMENTS = 450
    private const val OVERLAY_PACKAGE = "com.saifai.assistant"

    private val snapshotSequence = AtomicLong(1L)

    // Snapshot-scoped node registries: keeps the last 5 snapshots alive to eliminate stale-index bugs
    private val snapshotRegistries = mutableMapOf<String, MutableMap<String, AccessibilityNodeInfo>>()
    private val recentSnapshotOrder = mutableListOf<String>()

    @Synchronized
    fun registerNode(snapshotId: String, elementId: String, node: AccessibilityNodeInfo) {
        val registry = snapshotRegistries.getOrPut(snapshotId) { mutableMapOf() }
        registry[elementId] = node
    }

    @Synchronized
    fun getNode(elementIdOrIndex: String, targetSnapshotId: String? = null): AccessibilityNodeInfo? {
        val clean = elementIdOrIndex.trim()
        if (clean.isBlank()) return null

        // 1. If target snapshot specified, search it first
        if (targetSnapshotId != null) {
            val reg = snapshotRegistries[targetSnapshotId]
            val node = reg?.get(clean)
            if (node != null) {
                return try {
                    if (node.refresh()) node else null
                } catch (e: Exception) { null }
            }
        }

        // 2. Search most recent snapshot registry
        for (snapId in recentSnapshotOrder.reversed()) {
            val reg = snapshotRegistries[snapId] ?: continue
            val node = reg[clean] ?: reg["${snapId}e$clean"]
            if (node != null) {
                return try {
                    if (node.refresh()) node else null
                } catch (e: Exception) { null }
            }
        }

        return null
    }

    @Synchronized
    private fun pruneOldRegistries() {
        while (recentSnapshotOrder.size > 5) {
            val oldest = recentSnapshotOrder.removeAt(0)
            snapshotRegistries.remove(oldest)
        }
    }

    suspend fun capture(withScreenshot: Boolean = true): ScreenSnapshot = withContext(Dispatchers.Default) {
        val service = SaifAccessibilityService.instance
        val currentSnapId = "s${snapshotSequence.getAndIncrement()}"

        synchronized(ScreenObserver) {
            recentSnapshotOrder.add(currentSnapId)
            snapshotRegistries[currentSnapId] = mutableMapOf()
            pruneOldRegistries()
        }

        if (service == null) {
            return@withContext ScreenSnapshot(
                snapshotId = currentSnapId,
                packageName = "",
                activityClass = "",
                windowTitle = "Service Disabled",
                keyboardVisible = false,
                focusedInputId = null,
                hasScrollable = false,
                secure = false,
                isLocked = false,
                displayWidth = 1080,
                displayHeight = 2400,
                elements = emptyList(),
                screenshotJpegBase64 = null,
                fingerprint = "NO_SERVICE",
                screenSignature = "NO_SERVICE"
            )
        }

        val metrics = service.getRealDisplayMetrics()
        val displayW = metrics.first.coerceAtLeast(1)
        val displayH = metrics.second.coerceAtLeast(1)

        var currentPkg = service.getCurrentPackage()
        val activityClass = service.currentActivityClass
        var windowTitle = service.currentWindowTitle
        var keyboardVisible = false
        var hasScrollable = false
        var focusedInputId: String? = null
        val isLocked = false

        val rawElements = mutableListOf<UiElement>()
        val windows = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) service.windows else emptyList()
        } catch (e: Exception) {
            emptyList<AccessibilityWindowInfo>()
        }

        val rootsToVisit = mutableListOf<AccessibilityNodeInfo>()
        if (windows.isNotEmpty()) {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    keyboardVisible = true
                }
                val title = window.title?.toString() ?: ""
                if (title.contains("SAIF", ignoreCase = true) || title.contains("AssistantOverlay", ignoreCase = true)) {
                    continue
                }
                if (windowTitle.isBlank() && title.isNotBlank()) {
                    windowTitle = title
                }
                val root = window.root
                if (root != null) {
                    val rootPkg = root.packageName?.toString() ?: ""
                    if (!rootPkg.equals(OVERLAY_PACKAGE, ignoreCase = true) && !rootPkg.equals(service.packageName, ignoreCase = true)) {
                        rootsToVisit.add(root)
                    }
                }
            }
        }

        val activeRoot = service.rootInActiveWindow
        if (activeRoot != null) {
            val activePkg = activeRoot.packageName?.toString() ?: ""
            if (!rootsToVisit.any { it == activeRoot } &&
                !activePkg.equals(OVERLAY_PACKAGE, ignoreCase = true) &&
                !activePkg.equals(service.packageName, ignoreCase = true)
            ) {
                rootsToVisit.add(activeRoot)
            }
            if (currentPkg.isBlank()) currentPkg = activePkg
        }

        var elementIndexCounter = 0

        fun processNode(node: AccessibilityNodeInfo, parentIdx: Int) {
            if (elementIndexCounter >= MAX_ELEMENTS) return

            val isVisible = node.isVisibleToUser
            if (!isVisible) return

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.width() <= 0 || bounds.height() <= 0) return
            if (bounds.right <= 0 || bounds.bottom <= 0 || bounds.left >= displayW || bounds.top >= displayH) return

            val text = (node.text?.toString() ?: "").take(60)
            val desc = (node.contentDescription?.toString() ?: "").take(60)
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) (node.hintText?.toString() ?: "").take(60) else ""
            val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) (node.stateDescription?.toString() ?: "").take(60) else ""
            val fullViewId = node.viewIdResourceName ?: ""
            val shortViewId = if (fullViewId.contains(":id/")) fullViewId.substringAfter(":id/") else fullViewId

            val isClickable = node.isClickable
            val isLongClickable = node.isLongClickable
            val isEditable = node.isEditable
            val isFocused = node.isFocused
            val isChecked = node.isChecked
            val isSelected = node.isSelected
            val isEnabled = node.isEnabled
            val isScroll = node.isScrollable

            if (isScroll) hasScrollable = true

            val isMeaningful = isClickable || isLongClickable || isEditable || isScroll ||
                    text.isNotBlank() || desc.isNotBlank() || hint.isNotBlank() || stateDesc.isNotBlank()

            var myIdx = parentIdx
            if (isMeaningful) {
                val idx = elementIndexCounter++
                myIdx = idx
                val elementId = "${currentSnapId}e$idx"

                // Register both with snapshot-scoped id and numeric idx
                registerNode(currentSnapId, elementId, node)
                registerNode(currentSnapId, idx.toString(), node)

                if (isEditable && isFocused) {
                    focusedInputId = elementId
                }

                // Explicit normalized box: [ymin, xmin, ymax, xmax] in 0..999
                val ymin = ((bounds.top.toFloat() / displayH) * 1000).toInt().coerceIn(0, 999)
                val xmin = ((bounds.left.toFloat() / displayW) * 1000).toInt().coerceIn(0, 999)
                val ymax = ((bounds.bottom.toFloat() / displayH) * 1000).toInt().coerceIn(0, 999)
                val xmax = ((bounds.right.toFloat() / displayW) * 1000).toInt().coerceIn(0, 999)
                val norm = listOf(ymin, xmin, ymax, xmax)

                // Exact normalized center
                val cx = ((bounds.centerX().toFloat() / displayW) * 1000).toInt().coerceIn(0, 999)
                val cy = ((bounds.centerY().toFloat() / displayH) * 1000).toInt().coerceIn(0, 999)

                val className = (node.className?.toString() ?: "").lowercase()
                val role = when {
                    isEditable || className.contains("edittext") -> "input"
                    isClickable && (className.contains("button") || className.contains("imagebutton")) -> "button"
                    className.contains("checkbox") || className.contains("switch") -> "switch"
                    className.contains("tab") -> "tab"
                    className.contains("image") -> "image"
                    className.contains("textview") || text.isNotBlank() -> "text"
                    isScroll -> "list"
                    isClickable -> "button"
                    else -> "other"
                }

                rawElements.add(
                    UiElement(
                        id = elementId,
                        idx = idx,
                        role = role,
                        text = text,
                        desc = desc,
                        hint = hint,
                        stateDesc = stateDesc,
                        viewId = shortViewId,
                        bounds = bounds,
                        norm = norm,
                        cx = cx,
                        cy = cy,
                        clickable = isClickable,
                        longClickable = isLongClickable,
                        editable = isEditable,
                        focused = isFocused,
                        checked = isChecked,
                        selected = isSelected,
                        enabled = isEnabled,
                        scrollable = isScroll,
                        parentIdx = parentIdx
                    )
                )
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                processNode(child, myIdx)
            }
        }

        for (root in rootsToVisit) {
            try {
                processNode(root, -1)
            } catch (e: Exception) {
                Log.w(TAG, "Error visiting window root: ${e.message}")
            }
        }

        val sortedElements = rawElements.sortedWith(
            compareBy<UiElement> { it.bounds.top }.thenBy { it.bounds.left }
        )

        var screenshotBase64: String? = null
        var isSecure = false

        if (withScreenshot) {
            val shotResult = service.captureScreenshotBase64()
            screenshotBase64 = shotResult.base64
            isSecure = shotResult.isSecure
        }

        // Order-independent screen signature hashing package, activity, and visible view identifiers
        val sigTokens = sortedElements.map {
            "${it.role}:${it.viewId}:${it.text.take(15)}:${it.desc.take(15)}"
        }.sorted()

        val fullSigString = "$currentPkg:$activityClass:${sigTokens.joinToString(",")}"
        val screenSignature = try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(fullSigString.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            fullSigString.take(16)
        }

        ScreenSnapshot(
            snapshotId = currentSnapId,
            packageName = currentPkg,
            activityClass = activityClass,
            windowTitle = windowTitle,
            keyboardVisible = keyboardVisible,
            focusedInputId = focusedInputId,
            hasScrollable = hasScrollable,
            secure = isSecure,
            isLocked = isLocked,
            displayWidth = displayW,
            displayHeight = displayH,
            elements = sortedElements,
            screenshotJpegBase64 = screenshotBase64,
            fingerprint = screenSignature,
            screenSignature = screenSignature
        )
    }
}
