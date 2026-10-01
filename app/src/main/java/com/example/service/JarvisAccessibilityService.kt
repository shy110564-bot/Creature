package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            val pkg = event?.packageName?.toString().orEmpty()
            if (pkg.isNotBlank() && !pkg.contains("com.example") && !pkg.contains("systemui")) {
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

    /**
     * Retrieves all root nodes across interactive application windows so overlays never block screen reading or clicking.
     */
    private fun getAllRootNodes(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        runCatching {
            val winList = windows
            if (!winList.isNullOrEmpty()) {
                // Prioritize APPLICATION windows that are not our own overlay
                for (w in winList) {
                    val r = w.root ?: continue
                    val pkg = r.packageName?.toString().orEmpty()
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && !pkg.contains("com.example")) {
                        roots.add(r)
                    }
                }
                for (w in winList) {
                    val r = w.root ?: continue
                    if (r !in roots) {
                        roots.add(r)
                    }
                }
            }
        }
        rootInActiveWindow?.let { active ->
            if (active !in roots) {
                roots.add(0, active)
            }
        }
        return roots
    }

    fun extractWindowTextSummary(): String {
        val roots = getAllRootNodes()
        if (roots.isEmpty()) return _liveScreenText.value
        val collected = mutableListOf<String>()
        for (root in roots) {
            collectNodesText(root, collected)
            if (collected.size >= 35) break
        }
        return collected.distinct().take(30).joinToString(" • ")
    }

    /**
     * Returns a structured list of visible clickable/text elements with their screen percentage coordinates [x%, y%]
     * so the AI Brain can see every button/item on screen and decide what to click.
     */
    fun extractDetailedScreenElementsWithCoords(): String {
        val roots = getAllRootNodes()
        if (roots.isEmpty()) return _liveScreenText.value
        val dm = resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1)
        val h = dm.heightPixels.coerceAtLeast(1)
        val items = mutableListOf<String>()
        for (root in roots) {
            collectNodesWithCoords(root, w, h, items)
            if (items.size >= 35) break
        }
        return items.distinct().take(35).joinToString("\n")
    }

    private fun collectNodesWithCoords(
        node: AccessibilityNodeInfo?,
        screenW: Int,
        screenH: Int,
        out: MutableList<String>
    ) {
        if (node == null || out.size >= 40) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val label = if (text.isNotBlank()) text else desc
        if (label.isNotBlank() && label.length <= 120) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty && rect.centerX() in 1 until screenW && rect.centerY() in 1 until screenH) {
                val xPct = ((rect.exactCenterX() / screenW) * 100).toInt()
                val yPct = ((rect.exactCenterY() / screenH) * 100).toInt()
                out.add("\"$label\" @[${xPct}%,${yPct}%]")
            }
        }
        for (i in 0 until node.childCount) {
            collectNodesWithCoords(node.getChild(i), screenW, screenH, out)
        }
    }

    private fun collectNodesText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size >= 35) return
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

    fun performTapPercent(xPercent: Float, yPercent: Float) {
        val dm = resources.displayMetrics
        val x = (dm.widthPixels * (xPercent.coerceIn(2f, 98f) / 100f))
        val y = (dm.heightPixels * (yPercent.coerceIn(2f, 98f) / 100f))
        performTap(x, y)
    }

    fun clickNodeByText(query: String): Boolean {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val h = dm.heightPixels.toFloat()

        // 0. Check if AI Vision Brain passed exact percentage coordinates "XY:50,38"
        if (query.startsWith("XY:", ignoreCase = true)) {
            val parts = query.substringAfter(":").split(",")
            val xPct = parts.getOrNull(0)?.trim()?.toFloatOrNull()
            val yPct = parts.getOrNull(1)?.trim()?.toFloatOrNull()
            if (xPct != null && yPct != null) {
                performTapPercent(xPct, yPct)
                return true
            }
        }

        val cleanQuery = query.trim().lowercase()
        val roots = getAllRootNodes()

        // Expand common semantic button names into multi-language candidates
        val expandedQuery = when {
            cleanQuery.contains("view channel") || cleanQuery.contains("channel view") || cleanQuery.contains("channel dekho") ->
                "View channel|चैनल देखें|AK EXPLOITS|@"
            cleanQuery.contains("subscribe") || cleanQuery.contains("sadasyata") ->
                "Subscribe|सदस्यता लें|Subscribed"
            cleanQuery == "like" || cleanQuery == "like karo" || cleanQuery == "video like" ->
                "Like|पसंद|like this"
            cleanQuery == "share" || cleanQuery == "share karo" ->
                "Share|शेयर"
            cleanQuery == "search" || cleanQuery == "search bar" || cleanQuery == "search box" ->
                "Search|खोजें|Search YouTube"
            else -> query
        }

        if (roots.isNotEmpty()) {
            // 1. Support pipe-separated candidate targets (e.g. "View channel|चैनल देखें|AK EXPLOITS|@")
            if (expandedQuery.contains("|")) {
                val candidates = expandedQuery.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                for (cand in candidates) {
                    for (root in roots) {
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
            }

            // 2. Third / 3rd video or item on screen
            if (cleanQuery in listOf("third", "teesra", "tisra", "3rd", "third video", "teesra video", "tisra video")) {
                val clickables = mutableListOf<AccessibilityNodeInfo>()
                roots.forEach { collectMainClickableNodes(it, clickables) }
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

            // 3. Second / 2nd video or item on screen
            if (cleanQuery in listOf("second", "dusra", "doosra", "2nd", "second video", "dusra video", "neeche wala video")) {
                val clickables = mutableListOf<AccessibilityNodeInfo>()
                roots.forEach { collectMainClickableNodes(it, clickables) }
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

            // 4. First / 1st video or default prominent item
            if (cleanQuery.isBlank() ||
                cleanQuery in listOf(
                    "button", "first", "pehla", "pehle", "1st", "video",
                    "first video", "pehla video", "play video", "upar wala video", "ye video", "first item"
                )
            ) {
                val clickables = mutableListOf<AccessibilityNodeInfo>()
                roots.forEach { collectMainClickableNodes(it, clickables) }
                val sorted = clickables.sortedBy {
                    val r = Rect()
                    it.getBoundsInScreen(r)
                    r.top
                }
                val firstClickable = sorted.firstOrNull() ?: roots.firstNotNullOfOrNull { findFirstMainClickableNode(it) }
                if (firstClickable != null && clickOrTapNode(firstClickable)) {
                    return true
                }
                performTap(cx, h * 0.36f)
                return true
            }

            // 5. Try Android's built-in text search across all windows
            for (root in roots) {
                val matches = root.findAccessibilityNodeInfosByText(query)
                if (!matches.isNullOrEmpty()) {
                    for (node in matches) {
                        if (clickOrTapNode(node)) return true
                    }
                }
            }

            // 6. Smart Best-Match Scoring across all visible nodes (text, contentDescription, viewId)
            val bestNode = findBestMatchingNodeOnScreen(roots, cleanQuery)
            if (bestNode != null && clickOrTapNode(bestNode)) {
                return true
            }

            // 7. Deep recursive fuzzy search on text, contentDescription & viewIdResourceName
            for (root in roots) {
                val fuzzyNode = findNodeFuzzy(root, cleanQuery)
                if (fuzzyNode != null && clickOrTapNode(fuzzyNode)) {
                    return true
                }
            }

            // 8. Partial word match if multi-word query
            val queryWords = cleanQuery.split(" ").filter { it.length >= 2 }
            for (word in queryWords) {
                for (root in roots) {
                    val wordNode = findNodeFuzzy(root, word)
                    if (wordNode != null && clickOrTapNode(wordNode)) {
                        return true
                    }
                }
            }

            // 9. Fallback: click first main clickable element on screen
            for (root in roots) {
                val fallbackNode = findFirstMainClickableNode(root)
                if (fallbackNode != null && clickOrTapNode(fallbackNode)) {
                    return true
                }
            }
        }

        performTap(cx, h * 0.38f)
        return true
    }

    private fun findBestMatchingNodeOnScreen(
        roots: List<AccessibilityNodeInfo>,
        queryLower: String
    ): AccessibilityNodeInfo? {
        if (queryLower.isBlank()) return null
        val queryWords = queryLower.split(" ").map { it.trim() }.filter { it.length >= 2 }
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 0
        val dm = resources.displayMetrics

        fun scoreTree(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val t = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val d = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.lowercase()?.substringAfterLast("/")?.replace("_", " ").orEmpty()
            val combined = "$t $d $id".trim()

            if (combined.isNotBlank()) {
                var score = 0
                if (t == queryLower || d == queryLower) score += 120
                if (t.contains(queryLower) || d.contains(queryLower)) score += 80
                if (queryLower.length >= 3 && ( (t.length >= 3 && queryLower.contains(t)) || (d.length >= 3 && queryLower.contains(d)) )) {
                    score += 55
                }
                for (w in queryWords) {
                    if (t.contains(w) || d.contains(w) || id.contains(w)) {
                        score += 25
                    }
                }
                if (score > 0) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (!rect.isEmpty && rect.centerX() in 1 until dm.widthPixels && rect.centerY() in 40 until dm.heightPixels) {
                        score += 20
                        if (node.isClickable) score += 10
                        if (score > bestScore) {
                            bestScore = score
                            bestNode = node
                        }
                    }
                }
            }
            for (i in 0 until node.childCount) {
                scoreTree(node.getChild(i))
            }
        }

        for (r in roots) {
            scoreTree(r)
        }
        return if (bestScore >= 35) bestNode else null
    }

    private fun collectMainClickableNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null || out.size >= 12) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (node.isClickable && rect.top > 200 && rect.height() > 80 && rect.width() > 180) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            collectMainClickableNodes(node.getChild(i), out)
        }
    }

    private fun clickOrTapNode(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 1. If the exact matched node is directly clickable, click it AND tap its exact coordinates
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }

        // 2. If the node has valid screen coordinates (e.g., Litho sub-elements like "View channel" in YouTube),
        // dispatch a physical tap directly to the node's center so we don't accidentally click a parent card's center!
        if (!rect.isEmpty && rect.centerX() > 0 && rect.centerY() > 0) {
            performTap(rect.exactCenterX(), rect.exactCenterY())
            return true
        }

        // 3. Fallback: walk up parent hierarchy if node had no screen bounds
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun findNodeFuzzy(node: AccessibilityNodeInfo?, queryLower: String): AccessibilityNodeInfo? {
        if (node == null || queryLower.isBlank()) return null
        val t = node.text?.toString()?.lowercase().orEmpty()
        val d = node.contentDescription?.toString()?.lowercase().orEmpty()
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        if (t.contains(queryLower) || d.contains(queryLower) || id.contains(queryLower)) {
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
        if (node.isClickable && rect.top > 200 && rect.height() > 60) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findFirstMainClickableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun typeTextIntoFocusedNode(text: String): Boolean {
        val roots = getAllRootNodes()
        for (root in roots) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditableNode(root)
            if (focused != null) {
                focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val typed = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                clickSendOrSearchIfPresent(root)
                return typed
            }
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
        val topY = dm.heightPixels * 0.22f
        val bottomY = dm.heightPixels * 0.78f
        val leftX = dm.widthPixels * 0.16f
        val rightX = dm.widthPixels * 0.84f

        val roots = getAllRootNodes()
        val mainScrollNode = roots.firstNotNullOfOrNull { findLargeVerticalScrollableNode(it, dm.heightPixels) }

        when (direction.uppercase()) {
            "UP" -> {
                val scrolledBackward = mainScrollNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false
                if (!scrolledBackward) {
                    // If already at the very top of a feed when user says "scroll up" (meaning swipe finger up),
                    // or if custom view doesn't support A11y scroll action, perform physical swipe
                    val scrolledForward = mainScrollNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
                    if (!scrolledForward) {
                        performSwipe(cx, topY, cx, bottomY)
                    }
                }
            }
            "DOWN" -> {
                val scrolledForward = mainScrollNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
                if (!scrolledForward) {
                    performSwipe(cx, bottomY, cx, topY)
                }
            }
            "LEFT" -> performSwipe(rightX, cy, leftX, cy)
            "RIGHT" -> performSwipe(leftX, cy, rightX, cy)
        }
    }

    /**
     * Only picks a large vertical scrollable container (> 40% screen height) so horizontal filter bars
     * never hijack vertical scroll commands.
     */
    private fun findLargeVerticalScrollableNode(node: AccessibilityNodeInfo?, screenH: Int): AccessibilityNodeInfo? {
        if (node == null) return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (node.isScrollable && rect.height() > screenH * 0.40f) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findLargeVerticalScrollableNode(node.getChild(i), screenH)
            if (found != null) return found
        }
        return null
    }

    fun performTap(x: Float, y: Float) {
        mainHandler.post {
            runCatching {
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 85))
                    .build()
                dispatchGesture(gesture, null, null)
            }
        }
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        mainHandler.post {
            runCatching {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 280))
                    .build()
                dispatchGesture(gesture, null, null)
            }
        }
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
