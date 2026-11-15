package com.clawd.mobile.orca

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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
    private var sessionManager: MediaSessionManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        updatePlayback(sessions.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            sessionManager = manager
            val component = ComponentName(this, OrcaNotificationService::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsChanged, component, handler)
            updatePlayback(manager.getActiveSessions(component))
        } catch (e: Exception) {
            OrcaLogStore.append(this, "Media session unavailable: ${e.javaClass.simpleName}")
        }
    }

    private fun updatePlayback(sessions: List<MediaController>) {
        if (!OrcaPrivacy.enabled(this)) return
        val playing = sessions.firstOrNull {
            OrcaPrivacy.allowedPackage(it.packageName) &&
                it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        OrcaContextBrain.onPlayback(applicationContext, playing?.packageName.orEmpty(), playing != null)
    }

    private fun refreshMedia() {
        try {
            val component = ComponentName(this, OrcaNotificationService::class.java)
            sessionManager?.getActiveSessions(component)?.let(::updatePlayback)
        } catch (_: Exception) { }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshMedia()
    }

    override fun onListenerDisconnected() {
        try { sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        catch (_: Exception) {}
        sessionManager = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!OrcaPrivacy.enabled(this) || !FloatingEnabled()) return
        if (sbn.packageName == packageName || !OrcaPrivacy.allowedPackage(sbn.packageName)) return
        refreshMedia()
        if (sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val now = SystemClock.elapsedRealtime()
        if (lastKey == sbn.key && now - lastAt < 10_000L) return
        lastKey = sbn.key
        lastAt = now
        OrcaContextBrain.onNotification(applicationContext)
    }

    private fun FloatingEnabled(): Boolean =
        com.clawd.mobile.overlay.FloatingPetService.isRunning
}
