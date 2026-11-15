package com.clawd.mobile.orca

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.SystemClock

/**
 * User-opt-in notification listener. Reacts to metadata only: it never reads,
 * stores or forwards notification titles, bodies, contacts, or message content.
 */
class OrcaNotificationService : NotificationListenerService() {
    private var lastAt = 0L
    private var lastKey = ""

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!OrcaPrivacy.enabled(this) || !FloatingEnabled()) return
        if (sbn.packageName == packageName || !OrcaPrivacy.allowedPackage(sbn.packageName)) return
        if (sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val now = SystemClock.elapsedRealtime()
        if (lastKey == sbn.key && now - lastAt < 10_000L) return
        lastKey = sbn.key
        lastAt = now
        OrcaPetController.reactEvent(applicationContext, "notification")
    }

    private fun FloatingEnabled(): Boolean =
        com.clawd.mobile.overlay.FloatingPetService.isRunning
}
