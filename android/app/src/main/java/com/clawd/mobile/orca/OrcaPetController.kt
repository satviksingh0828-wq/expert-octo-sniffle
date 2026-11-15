package com.clawd.mobile.orca

import android.content.Context
import android.content.Intent
import com.clawd.mobile.overlay.FloatingPetService

/** Automatic local controller for packaged Calico animations. */
object OrcaPetController {
    val animations: Set<String> = OrcaActivityEngine.animations

    fun react(context: Context, animation: String) {
        if (animation !in animations) {
            OrcaLogStore.append(context, "Rejected unknown animation: $animation")
            return
        }
        if (!FloatingPetService.isRunning) {
            OrcaLogStore.append(context, "Animation queued by policy but pet service is not running: $animation")
            return
        }
        val intent = Intent(context, FloatingPetService::class.java).apply {
            action = FloatingPetService.ACTION_ORCA_ANIMATION
            putExtra(FloatingPetService.EXTRA_ORCA_ANIMATION, animation)
        }
        try {
            context.startService(intent)
            OrcaLogStore.append(context, "Pet animation applied: $animation")
        } catch (error: Exception) {
            OrcaLogStore.append(context, "Pet animation delivery failed: ${error.javaClass.simpleName}")
        }
    }
}
