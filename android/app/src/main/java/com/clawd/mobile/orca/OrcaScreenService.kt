package com.clawd.mobile.orca

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** User-enabled, local-only screen context bridge. Never reads password nodes. */
class OrcaScreenService : AccessibilityService() {
    private var lastAt = 0L
    private var lastContext = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!OrcaPrivacy.enabled(this)) return
        val source = event.source
        val typingEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED && source?.isEditable == true)
        if (!typingEvent && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val now = android.os.SystemClock.elapsedRealtime()
        val throttleMs = if (typingEvent) 700L else 9000L
        if (now - lastAt < throttleMs) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || !OrcaPrivacy.allowedPackage(pkg)) return
        val fragments = ArrayList<String>()
        event.text.forEach { value ->
            value.toString().trim().takeIf { it.isNotEmpty() }?.let { fragments.add(it) }
        }
        if (source?.isPassword == true) return
        val root = rootInActiveWindow
        if (root != null) {
            try { collectText(root, fragments, 0) } finally { root.recycle() }
        }
        if (typingEvent && source?.isEditable == true) {
            fragments.add("[text input active]")
        }
        if (fragments.isEmpty()) return
        val context = OrcaPrivacy.sanitize(fragments.joinToString(" | ").take(1200))
        if (!typingEvent && context == lastContext) return
        lastContext = context
        lastAt = now
        // In-app delivery: no Termux, HTTP endpoint, or external server required.
        OrcaModel.decide(applicationContext, pkg, context, typingEvent) { decision ->
            if (typingEvent) OrcaLogStore.append(applicationContext, "Text input event detected in $pkg")
            OrcaLogStore.append(applicationContext, "Accessibility description received from $pkg; selected $decision")
            if (OrcaPrivacy.enabled(applicationContext)) OrcaPetController.react(applicationContext, decision)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 12 || out.size >= 24 || node.isPassword) return
        val value = node.text?.toString()?.trim()
        if (!value.isNullOrEmpty() && value.length <= 200 && !out.contains(value)) out.add(value)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectText(child, out, depth + 1) } finally { child.recycle() }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy() }
}
