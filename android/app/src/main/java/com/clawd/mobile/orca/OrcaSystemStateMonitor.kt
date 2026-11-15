package com.clawd.mobile.orca

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Event-driven system-state context: doze/device-idle, user-present and thermal
 * pressure. No extra dangerous runtime permission is required.
 */
class OrcaSystemStateMonitor(context: Context) {
    private val app = context.applicationContext
    private val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> dispatch()
                Intent.ACTION_USER_PRESENT -> dispatch(userPresent = true)
                Intent.ACTION_SCREEN_OFF -> dispatch(userPresent = false)
                Intent.ACTION_SCREEN_ON -> dispatch()
            }
        }
    }

    private val thermalListener =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { status ->
                OrcaContextBrain.onSystemState(app, thermalStatus = status)
            }
        } else null

    fun start() {
        if (registered) return
        registered = true

        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { listener ->
                try {
                    power.addThermalStatusListener(
                        ContextCompat.getMainExecutor(app),
                        listener
                    )
                } catch (_: Exception) { }
            }
        }

        dispatch()
        OrcaLogStore.append(app, "System-state monitor active: idle, user-present, thermal")
    }

    fun stop() {
        if (!registered) return
        registered = false
        try { app.unregisterReceiver(receiver) } catch (_: Exception) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { listener ->
                try { power.removeThermalStatusListener(listener) } catch (_: Exception) { }
            }
        }
    }

    private fun dispatch(userPresent: Boolean? = null) {
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { power.currentThermalStatus } catch (_: Exception) { PowerManager.THERMAL_STATUS_NONE }
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        OrcaContextBrain.onSystemState(
            app,
            deviceIdle = power.isDeviceIdleMode,
            interactive = power.isInteractive,
            userPresent = userPresent,
            thermalStatus = thermal
        )
    }
}
