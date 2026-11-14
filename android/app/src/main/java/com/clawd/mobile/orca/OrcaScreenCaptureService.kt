package com.clawd.mobile.orca

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** User-initiated MediaProjection with an ongoing notification and a STOP action.
 * Screenshots remain in memory; OCR and image labels are computed by bundled local models.
 * The original pixel data is discarded after processing. This is not a stealth recorder.
 */
class OrcaScreenCaptureService : Service() {
    companion object {
        const val START = "com.clawd.mobile.orca.CAPTURE_START"
        const val STOP = "com.clawd.mobile.orca.CAPTURE_STOP"
        const val RESULT_CODE = "projection_code"
        const val RESULT_DATA = "projection_data"
        private const val CHANNEL = "orca_screen_capture"
        private const val NOTIFICATION = 29331
        @Volatile var isRunning = false
            private set
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private val processing = AtomicBoolean(false)
    private var lastAnalyzed = 0L
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action != START || isRunning) return START_NOT_STICKY
        val token = readConsentToken(intent)
        if (token == null) { stopSelf(); return START_NOT_STICKY }
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "ORCA screen capture", NotificationManager.IMPORTANCE_LOW)
            )
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else startForeground(NOTIFICATION, notification())
            val mgr = getSystemService(MediaProjectionManager::class.java)
            projection = mgr.getMediaProjection(intent.getIntExtra(RESULT_CODE, 0), token)
            projection?.registerCallback(projectionCallback, Handler(mainLooper))
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val density = metrics.densityDpi
            thread = HandlerThread("OrcaScreenFrames").apply { start() }
            val handler = Handler(thread!!.looper)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
                setOnImageAvailableListener({ reader -> analyzeLatest(reader) }, handler)
            }
            display = projection?.createVirtualDisplay(
                "ORCA visual understanding", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, handler
            )
            isRunning = display != null
            if (!isRunning) stopSelf()
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun readConsentToken(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(RESULT_DATA, Intent::class.java)
        else intent.getParcelableExtra(RESULT_DATA)

    private var lastDescription = ""
    private fun analyzeLatest(reader: ImageReader) {
        val frame = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzed < 8000L || !processing.compareAndSet(false, true)) return
            lastAnalyzed = now
            val plane = frame.planes[0]
            val paddedWidth = frame.width + (plane.rowStride - plane.pixelStride * frame.width) / plane.pixelStride
            val raw = Bitmap.createBitmap(paddedWidth, frame.height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(raw, 0, 0, frame.width, frame.height)
            if (cropped !== raw) raw.recycle()
            val screenshot = if (cropped.width > 1280) {
                val scaled = Bitmap.createScaledBitmap(cropped, 1280, (cropped.height * 1280f / cropped.width).toInt().coerceAtLeast(1), true)
                if (scaled !== cropped) cropped.recycle()
                scaled
            } else cropped
            OrcaVisualAnalyzer.analyze(screenshot) { description ->
                processing.set(false)
                if (isRunning && description.isNotBlank() && description != lastDescription) {
                    lastDescription = description
                    OrcaModel.decide(applicationContext, "Android screen capture", OrcaPrivacy.sanitize(description)) { animation ->
                        if (isRunning) OrcaPetController.react(applicationContext, animation)
                    }
                }
            }
        } catch (_: Exception) { processing.set(false) }
        finally { frame.close() }
    }

    private fun notification(): Notification {
        val stop = Intent(this, OrcaScreenCaptureService::class.java).setAction(STOP)
        val action = PendingIntent.getService(this, 23, stop, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("ORCA is viewing your screen")
            .setContentText("On-device analysis only. Tap Stop to end screen sharing.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", action)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        imageReader?.setOnImageAvailableListener(null, null)
        display?.release()
        imageReader?.close()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        thread?.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
