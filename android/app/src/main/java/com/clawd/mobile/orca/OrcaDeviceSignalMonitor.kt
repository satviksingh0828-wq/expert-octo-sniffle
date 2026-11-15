package com.clawd.mobile.orca

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/**
 * Permission-light device signal monitor for ORCA.
 *
 * Captures only coarse booleans/intensity buckets: connectivity transport,
 * audio route, and physical motion. It never records SSIDs, device names,
 * Bluetooth addresses, microphone audio, location, or raw sensor history.
 */
class OrcaDeviceSignalMonitor(private val context: Context) : SensorEventListener {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sensors = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var registered = false
    private var accel: Sensor? = null
    private var lastMotionDispatchAt = 0L
    private var motionEma = 0f
    private var screenActive = true
    private var powerSave = false
    private var sensorRegistered = false
    private var screenActive = true
    private var batterySaver = false
    private var sensorRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = dispatchNetwork()
        override fun onLost(network: Network) = dispatchNetwork()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            dispatchNetwork(caps)
    }

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = dispatchAudio()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = dispatchAudio()
    }

    fun start() {
        if (registered) return
        registered = true

        try {
            connectivity.registerDefaultNetworkCallback(networkCallback, handler)
        } catch (_: Exception) {
            // Some vendor builds can reject callback registration; initial snapshot still works.
        }
        try {
            audio.registerAudioDeviceCallback(audioCallback, handler)
        } catch (_: Exception) { }

        accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        updateSensorRegistration()

        dispatchNetwork()
        dispatchAudio()
        OrcaLogStore.append(app, "Device signals active: network, audio route, coarse motion")
    }

    fun stop() {
        if (!registered) return
        registered = false
        try { connectivity.unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
        try { audio.unregisterAudioDeviceCallback(audioCallback) } catch (_: Exception) { }
        sensors.unregisterListener(this)
        sensorRegistered = false
        resetMotion()
    }

    /** Event-driven sampling policy; no accelerometer use with screen off or battery saver. */
    fun setSamplingPolicy(screenOn: Boolean? = null, powerSave: Boolean? = null) {
        screenOn?.let { screenActive = it }
        powerSave?.let { batterySaver = it }
        updateSensorRegistration()
    }

    private fun updateSensorRegistration() {
        val wanted = registered && screenActive && !batterySaver && accel != null
        if (wanted == sensorRegistered) return
        if (wanted) {
            sensorRegistered = sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL, handler)
        } else {
            sensors.unregisterListener(this)
            sensorRegistered = false
            resetMotion()
        }
    }

    private fun resetMotion() {
        motionEma = 0f
        lastMotionDispatchAt = 0L
        OrcaContextBrain.onExtendedDeviceContext(app, moving = false, strongMotion = false)
    }

    private fun dispatchNetwork(caps: NetworkCapabilities? = null) {
        if (!registered) return
        val current = caps ?: connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        OrcaContextBrain.onExtendedDeviceContext(
            app,
            networkConnected = current != null,
            wifi = current?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            cellular = current?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        )
    }

    private fun dispatchAudio() {
        if (!registered) return
        val outputs = try {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        } catch (_: Exception) {
            emptyList()
        }
        val wired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val bluetooth = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        OrcaContextBrain.onExtendedDeviceContext(app, wiredAudio = wired, bluetoothAudio = bluetooth)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!registered || !sensorRegistered || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val values = event.values
        if (values.size < 3) return
        val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        // Remove gravity and smooth. Value remains a coarse physical-motion score.
        val delta = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)
        motionEma = motionEma * 0.82f + delta * 0.18f

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastMotionDispatchAt < 1500L) return
        lastMotionDispatchAt = now
        OrcaContextBrain.onExtendedDeviceContext(
            app,
            moving = motionEma >= 0.9f,
            strongMotion = motionEma >= 2.5f
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
