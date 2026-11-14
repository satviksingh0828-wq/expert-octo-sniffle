package com.clawd.mobile.orca

import android.content.Context
import android.content.Intent
import com.clawd.mobile.overlay.FloatingPetService

/** Only known packaged animations can reach the floating window. */
object OrcaPetController {
    val animations = setOf(
        "calico-idle.apng", "calico-thinking.apng", "calico-happy.apng",
        "calico-error.apng", "calico-notification.apng", "calico-sleeping.apng",
        "calico-working-typing.apng", "calico-working-conducting.apng",
        "calico-working-carrying.apng", "calico-working-building.apng",
        "calico-working-juggling.apng", "calico-working-sweeping.apng"
    )
    fun react(context: Context, animation: String) {
        if (animation !in animations || !FloatingPetService.isRunning) return
        val intent = Intent(context, FloatingPetService::class.java).apply {
            action = FloatingPetService.ACTION_ORCA_ANIMATION
            putExtra(FloatingPetService.EXTRA_ORCA_ANIMATION, animation)
        }
        try { context.startService(intent) } catch (_: Exception) { /* Android may restrict background starts */ }
    }
}
