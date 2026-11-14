package com.clawd.mobile.orca

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.clawd.mobile.data.PrefsStore
import com.clawd.mobile.overlay.FloatingPetService

/** Best-effort restart of the pet after a reboot, only if user left it enabled.
 * Screen capture is deliberately NEVER restarted: Android needs fresh consent.
 */
class OrcaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PrefsStore.getInstance(context).isFloatingPetEnabled() || !Settings.canDrawOverlays(context)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, FloatingPetService::class.java))
        } catch (_: Exception) { /* Some Android OEMs prohibit background starts */ }
    }
}
