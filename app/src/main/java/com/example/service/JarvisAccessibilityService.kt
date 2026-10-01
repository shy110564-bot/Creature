package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            val pkg = event?.packageName?.toString().orEmpty()
            if (pkg.isNotBlank() && !pkg.contains("com.example")) {
                _liveAppPackage.value = pkg
            }
            val summary = extractWindowTextSummary()
            if (summary.isNotBlank()) {
                _liveScreenText.value = summary
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun extractWindowTextSummary(): String {
        val root = rootInActiveWindow ?: return _liveScreenText.value
        val collected = mutableListOf<String>()
        collectNodesText(root, collected)
        return collected.distinct().take(25).joinToString(" • ")
    }

    private fun collectNodesText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size >= 30) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotBlank() && text.length <= 140) {
            out.add(text)
        } else if (desc.isNotBlank() && desc.length <= 100) {
            out.add(desc)
        }
        for (i in 0 until node.childCount) {
            collectNodesText(node.getChild(i), out)
        }
    }

    fun clickNodeByText(query: String): Boolean {
        val root = rootInActiveWindow
        val cleanQuery = query.trim().lowercase()
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val h = dm.heightPixels.toFloat()

        if (root != null) {
            // Support pipe-separated candidate targets (e.g. "View channel|चैनल देखें|AK EXPLOITS|@")
            if (query.contains("|")) {
                val candidates = query.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                for (cand in candidates) {
                    val matches = root.findAccessibilityNodeInfosByText(cand)
                    if (!matches.isNullOrEmpty()) {
                        for (node in matches) {
                            if (clickOrTapNode(node)) return true
                        }
                    }
                    val fuzzy = findNodeFuzzy(root, cand.lowercase())
                    if (fuzzy != null && clickOrTapNode(fuzzy)) return true
                }
            }

            // Third / 3rd video or item on screen
            if (cleanQuery in listOf("third", "teesra", "tisra", "3rd", "third video", "teesra video", "tisra video")) {
                val clickables = mutableListOf<AccessibilityNodeInfo>()
                collectMainClickableNodes(root, clickables)
                val sorted = clickables.sortedBy {
                    val r = Rect()
                    it.getBoundsInScreen(r)
                    r.top
                }
                val targetNode = sorted.getOrNull(2) ?: sorted.lastOrNull()
                if (targetNode != null && clickOrTapNode(targetNode)) return true
                performTap(cx, h * 0.82f)
                return true
            }

            // Second / 2nd video or item on screen
            if (cleanQuery in listOf("second", "dusra", "doosra", "2nd", "second video", "dusra video", "neeche wala video")) {
                val clickables = mutableListOf<AccessibilityNodeInfo>()
                collectMainClickableNodes(root, clickables)
                val sorted = clickables.sortedBy {
                    val r = Rect()
                    it.getBoundsInScreen(r)
                    r.top
                }
                val targetNode = sorted.getOrNull(1) ?: sorted.firstOrNull()
                if (targetNode != null && clickOrTapNode(targetNode)) return true
                performTap(cx, h * 0.64f)
                return true
            }

            // If user says "first", "pehla", "1st", "video", or empty -> click the first prominent video/item
            if (cleanQuery.isBlank() ||
                cleanQuery in listOf(
                    "button", "first", "pehla", "pehle", "1st", "video",
                    "first video", "pehla video", "play video", "upar wala video", "ye video"
                )
            ) {
                val clickables = mutableListOf<AccessibilityNodeInfo>()
                collectMainClickableNodes(root, clickables)
                val sorted = clickables.sortedBy {
                    val r = Rect()
                    it.getBoundsInScreen(r)
                    r.top
                }
                val firstClickable = sorted.firstOrNull() ?: findFirstMainClickableNode(root)
                if (firstClickable != null && clickOrTapNode(firstClickable)) {
                    return true
                }
                performTap(cx, h * 0.36f)
                return true
            }

            // 1. Try Android's built-in text search
            val matches = root.findAccessibilityNodeInfosByText(query)
            if (!matches.isNullOrEmpty()) {
                for (node in matches) {
                    if (clickOrTapNode(node)) return true
                }
            }

            // 2. Deep recursive fuzzy search on text & contentDescription
            val fuzzyNode = findNodeFuzzy(root, cleanQuery)
            if (fuzzyNode != null && clickOrTapNode(fuzzyNode)) {
                return true
            }

            // 3. Partial word match if multi-word query
            val queryWords = cleanQuery.split(" ").filter { it.length >= 3 }
            for (word in queryWords) {
                val wordNode = findNodeFuzzy(root, word)
                if (wordNode != null && clickOrTapNode(wordNode)) {
                    return true
                }
            }

            // 4. Fallback: click first main clickable element on screen
            val fallbackNode = findFirstMainClickableNode(root)
            if (fallbackNode != null && clickOrTapNode(fallbackNode)) {
                return true
            }
        }

        performTap(cx, h * 0.38f)
        return true
    }

    private fun collectMainClickableNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null || out.size >= 8) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (node.isClickable && rect.top > 220 && rect.height() > 80 && rect.width() > 200) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            collectMainClickableNodes(node.getChild(i), out)
        }
    }

    private fun clickOrTapNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            current = current.parent
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty && rect.centerX() > 0 && rect.centerY() > 0) {
            performTap(rect.exactCenterX(), rect.exactCenterY())
            return true
        }
        return false
    }

    private fun findNodeFuzzy(node: AccessibilityNodeInfo?, queryLower: String): AccessibilityNodeInfo? {
        if (node == null || queryLower.isBlank()) return null
        val t = node.text?.toString()?.lowercase().orEmpty()
        val d = node.contentDescription?.toString()?.lowercase().orEmpty()
        if (t.contains(queryLower) || d.contains(queryLower)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findNodeFuzzy(node.getChild(i), queryLower)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstMainClickableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        // Prefer clickable items in the main content area (below top bar)
        if (node.isClickable && rect.top > 220 && rect.height() > 60) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findFirstMainClickableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun typeTextIntoFocusedNode(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditableNode(root)
        if (focused != null) {
            focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val typed = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            // Also try clicking Send/Search button if present
            clickSendOrSearchIfPresent(root)
            return typed
        }
        return false
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun clickSendOrSearchIfPresent(root: AccessibilityNodeInfo) {
        val sendLabels = listOf("Send", "भेजें", "Search", "खोजें")
        for (label in sendLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (!nodes.isNullOrEmpty()) {
                for (n in nodes) {
                    if (n.isClickable) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
            }
        }
    }

    fun performSystemNavigation(navAction: String): Boolean {
        return when (navAction.uppercase()) {
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "SCREENSHOT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else false
            }
            else -> false
        }
    }

    fun performDirectionalScroll(direction: String) {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val topY = dm.heightPixels * 0.24f
        val bottomY = dm.heightPixels * 0.78f
        val leftX = dm.widthPixels * 0.18f
        val rightX = dm.widthPixels * 0.82f

        when (direction.uppercase()) {
            // "UP" / "upar karo": user wants to scroll UP (or move content up/down)
            "UP" -> {
                val root = rootInActiveWindow
                val scrolled = findScrollableNode(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false
                if (!scrolled) {
                    performSwipe(cx, topY, cx, bottomY)
                }
            }
            // "DOWN" / "neeche scroll karo": advance Downward through feed (like YouTube/Reels/Webpage)
            "DOWN" -> {
                val root = rootInActiveWindow
                val scrolled = findScrollableNode(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
                if (!scrolled) {
                    performSwipe(cx, bottomY, cx, topY)
                }
            }
            "LEFT" -> performSwipe(rightX, cy, leftX, cy)
            "RIGHT" -> performSwipe(leftX, cy, rightX, cy)
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 90))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 320))
            .build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        private val _liveAppPackage = MutableStateFlow("Phone Screen")
        val liveAppPackage: StateFlow<String> = _liveAppPackage.asStateFlow()

        private val _liveScreenText = MutableStateFlow("")
        val liveScreenText: StateFlow<String> = _liveScreenText.asStateFlow()
    }
}
