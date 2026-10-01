package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
        return collected.distinct().take(18).joinToString(" • ")
    }

    private fun collectNodesText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size >= 24) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotBlank() && text.length <= 120) {
            out.add(text)
        } else if (desc.isNotBlank() && desc.length <= 80) {
            out.add(desc)
        }
        for (i in 0 until node.childCount) {
            collectNodesText(node.getChild(i), out)
        }
    }

    fun clickNodeByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(query)
        if (!matches.isNullOrEmpty()) {
            for (node in matches) {
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null) {
                    if (clickable.isClickable) {
                        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    clickable = clickable.parent
                }
            }
        }
        performTap(540f, 1200f)
        return true
    }

    fun typeTextIntoFocusedNode(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
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
