package com.clawd.mobile.orca

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.clawd.mobile.overlay.FloatingPetService

/** Main-process event-to-animation bridge; no model inference required. */
object OrcaPetController {
    val animations: Set<String> = OrcaActivityEngine.animations
    private var lastAnimation: String? = null
    private var lastAt = 0L
    private var notificationProtectedUntil = 0L

    @Synchronized
    fun reactEvent(context: Context, event: String) {
        val animation = OrcaActivityEngine.eventAnimation(event) ?: return
        val now = SystemClock.elapsedRealtime()
        if (event == "notification" || event == "message") {
            notificationProtectedUntil = now + 4_000L
        }
        react(context, animation)
    }

    @Synchronized
    fun react(context: Context, animation: String) {
        if (animation !in animations) {
            OrcaLogStore.append(context, "Rejected unknown animation: $animation")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < notificationProtectedUntil &&
            animation != "calico-notification.apng") return
        if (animation == lastAnimation && now - lastAt < 1_000L) return
        if (!FloatingPetService.isRunning) {
            OrcaLogStore.append(context, "Pet service not running; ignored: $animation")
            return
        }
        val intent = Intent(context, FloatingPetService::class.java).apply {
            action = FloatingPetService.ACTION_ORCA_ANIMATION
            putExtra(FloatingPetService.EXTRA_ORCA_ANIMATION, animation)
        }
        try {
            context.startService(intent)
            lastAnimation = animation
            lastAt = now
            // The service and WebView must confirm actual rendering separately.
            OrcaLogStore.append(context, "Animation requested: $animation")
        } catch (error: Exception) {
            OrcaLogStore.append(context, "Pet animation delivery failed: ${error.javaClass.simpleName}")
        }
    }
}
