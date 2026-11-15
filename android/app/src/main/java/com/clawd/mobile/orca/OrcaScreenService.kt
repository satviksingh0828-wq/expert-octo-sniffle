package com.clawd.mobile.orca

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** User-enabled, local-only screen context bridge. Never reads password nodes. */
class OrcaScreenService : AccessibilityService() {
    private var lastAt = 0L
    private var lastContext = ""

    private var lastEventAt = 0L
    private var lastEventKind = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!OrcaPrivacy.enabled(this)) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!OrcaPrivacy.allowedPackage(pkg)) {
            OrcaContextBrain.onSensitiveApp(applicationContext)
            return
        }
        OrcaContextBrain.onForeground(applicationContext, pkg)
        val source = event.source
        // Never handle password fields or infer behavior from their contents.
        if (source?.isPassword == true) return
        val eventKind = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "typing"
            AccessibilityEvent.TYPE_VIEW_FOCUSED ->
                if (source?.isEditable == true) "editor_focus" else "editor_blur"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "tap"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scroll"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "notification"
            else -> null
        }
        if (eventKind == "typing") OrcaContextBrain.onTextChanged(applicationContext, pkg)
        if (eventKind != null) {
            val now = android.os.SystemClock.elapsedRealtime()
            val minInterval = when (eventKind) {
                "typing", "editor_focus" -> 700L
                "scroll" -> 5_000L
                "tap" -> 3_000L
                else -> 2_000L
            }
            if (eventKind != lastEventKind || now - lastEventAt >= minInterval) {
                lastEventAt = now
                lastEventKind = eventKind
                when (eventKind) {
                    "typing" -> Unit
                    "editor_focus" -> OrcaContextBrain.onFocus(applicationContext, pkg, true)
                    "editor_blur" -> OrcaContextBrain.onFocus(applicationContext, pkg, false)
                    "scroll" -> OrcaContextBrain.onScroll(applicationContext, pkg)
                    "tap" -> OrcaContextBrain.onTap(applicationContext, pkg)
                    "notification" -> OrcaContextBrain.onNotification(applicationContext)
                }
            }
            // Typing, clicks, and scrolls do not need to read text from apps.
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAt < 3_000L) return
        val fragments = ArrayList<String>()
        val root = rootInActiveWindow
        if (root != null) {
            try { collectText(root, fragments, 0) } finally { root.recycle() }
        }
        if (fragments.isEmpty()) return
        val context = OrcaPrivacy.sanitize(fragments.joinToString(" | ").take(1200))
        if (context == lastContext) return
        lastContext = context
        lastAt = now
        OrcaContextBrain.onVisibleContext(applicationContext, pkg, context)
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 8 || out.size >= 20 || node.isPassword || node.isEditable) return
        val value = node.text?.toString()?.trim()
        if (!value.isNullOrEmpty() && value.length <= 200 && !out.contains(value)) out.add(value)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectText(child, out, depth + 1) } finally { child.recycle() }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { OrcaContextBrain.stop(); super.onDestroy() }
}
