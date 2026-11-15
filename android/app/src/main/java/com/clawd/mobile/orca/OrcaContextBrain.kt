package com.clawd.mobile.orca

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.clawd.mobile.overlay.FloatingPetService
import java.util.Calendar

/**
 * Privacy-conscious temporal context brain.
 *
 * Fuses coarse Android activity metadata, media state, device state, network,
 * audio route and motion into a local neural prediction. Obvious states use
 * deterministic priority; softer neural states use smoothing and hysteresis.
 */
object OrcaContextBrain {
    private const val PAUSE_MS = 10_000L
    private const val ALERT_MS = 4_500L
    private const val SCROLL_MS = 3_500L
    private const val IDLE_MS = 90_000L
    private const val SOFT_STABLE_MS = 1_200L
    private const val SOFT_MIN_DWELL_MS = 2_400L
    private const val APP_SWITCH_BURST_MS = 12_000L
    private const val EMA_KEEP = 0.62f

    private val handler = Handler(Looper.getMainLooper())
    private val neural by lazy { OrcaNeuralNetwork() }
    private val behaviorMemory by lazy { OrcaBehaviorMemory() }
    private var restored = false
    private var appContext: Context? = null

    private var currentPackage = ""
    private var previousPackage = ""
    private var editorFocused = false
    private var lastTypedAt = 0L
    private var lastScrollAt = 0L
    private var lastTapAt = 0L
    private var lastNotificationAt = 0L
    private var lastActivityAt = 0L
    private var lastAppSwitchAt = 0L
    private var appSwitchBurst = 0

    private var musicPlaying = false
    private var mediaPackage = ""
    private var screenOn = true

    private var batteryFraction = 1f
    private var charging = false
    private var powerSave = false
    private var landscape = false

    private var networkConnected = false
    private var wifi = false
    private var cellular = false
    private var wiredAudio = false
    private var bluetoothAudio = false
    private var moving = false
    private var strongMotion = false

    private var deviceIdle = false
    private var interactive = true
    private var userPresent = true
    private var thermalStatus = PowerManager.THERMAL_STATUS_NONE
    private var sessionStartedAt = 0L

    private var searchHint = false
    private var videoHint = false
    private var readingHint = false
    private var loadingHint = false
    private var errorHint = false
    private var successHint = false
    private var lastContextAt = 0L

    private var lastIntent: OrcaIntent? = null
    private var lastIntentAt = 0L
    private var pendingIntent: OrcaIntent? = null
    private var pendingIntentAt = 0L
    private var lastConfidence = 0f
    private var lastMargin = 0f
    private var lastFeatures = FloatArray(OrcaNeuralNetwork.INPUTS)
    private var smoothedProbabilities = FloatArray(OrcaNeuralNetwork.OUTPUTS)

    private val scheduledRefresh = Runnable { synchronized(this) { refreshInternal() } }

    @Synchronized
    private fun restore(context: Context) {
        if (restored) return
        restored = true
        val saved = context.getSharedPreferences("orca_brain", Context.MODE_PRIVATE)
            .getString("weights_v3_multisignal", null)
        if (saved != null && !neural.importWeights(saved)) {
            OrcaLogStore.append(context, "Stored neural weights incompatible; using fresh multisignal model")
        }
    }

    @Synchronized
    fun onForeground(context: Context, pkg: String) {
        if (!active(context) || pkg == currentPackage) return
        val time = now()
        previousPackage = currentPackage
        if (previousPackage.isNotEmpty()) {
            appSwitchBurst = if (recent(lastAppSwitchAt, APP_SWITCH_BURST_MS, time)) {
                (appSwitchBurst + 1).coerceAtMost(8)
            } else 1
            lastAppSwitchAt = time
        }
        currentPackage = pkg
        if (sessionStartedAt == 0L) sessionStartedAt = time
        editorFocused = false
        lastTypedAt = 0L
        lastScrollAt = 0L
        lastTapAt = 0L
        lastActivityAt = time
        clearHints()
        refresh(context)
    }

    @Synchronized
    fun onTextChanged(context: Context, pkg: String) {
        if (!active(context)) return
        onForeground(context, pkg)
        editorFocused = true
        lastTypedAt = now()
        lastActivityAt = lastTypedAt
        refresh(context)
    }

    @Synchronized
    fun onFocus(context: Context, pkg: String, editable: Boolean) {
        if (!active(context)) return
        onForeground(context, pkg)
        editorFocused = editable
        if (!editable) lastTypedAt = 0L
        lastActivityAt = now()
        refresh(context)
    }

    @Synchronized
    fun onScroll(context: Context, pkg: String) {
        if (!active(context)) return
        onForeground(context, pkg)
        lastScrollAt = now()
        lastActivityAt = lastScrollAt
        refresh(context)
    }

    @Synchronized
    fun onTap(context: Context, pkg: String) {
        if (!active(context)) return
        onForeground(context, pkg)
        lastTapAt = now()
        lastActivityAt = lastTapAt
        refresh(context)
    }

    @Synchronized
    fun onVisibleContext(context: Context, pkg: String, visible: String) {
        if (!active(context)) return
        onForeground(context, pkg)
        val text = visible.lowercase()
        searchHint = has(text, "search", "results", "query", "google search", "bing")
        videoHint = has(text, "video", "subtitles", "full screen", "watch", "stream", "shorts")
        readingHint = has(text, "article", "news", "chapter", "read", "documentation", "lesson", "book", "paragraph")
        loadingHint = has(text, "loading", "processing", "please wait", "connecting", "compiling")
        errorHint = has(text, "error", "failed", "exception", "crashed", "something went wrong")
        successHint = has(text, "completed", "successfully", "congratulations", "task complete")
        lastContextAt = now()
        refresh(context)
    }

    @Synchronized
    fun onNotification(context: Context) {
        if (!active(context)) return
        lastNotificationAt = now()
        refresh(context)
    }

    @Synchronized
    fun onPlayback(context: Context, sourcePackage: String, playing: Boolean) {
        if (!active(context)) return
        mediaPackage = sourcePackage
        musicPlaying = playing
        refresh(context)
    }

    @Synchronized
    fun onScreen(context: Context, on: Boolean) {
        if (!active(context)) return
        screenOn = on
        if (on) lastActivityAt = now()
        refresh(context)
    }

    @Synchronized
    fun onDeviceContext(
        context: Context,
        batteryPercent: Int? = null,
        isCharging: Boolean? = null,
        isPowerSave: Boolean? = null,
        isLandscape: Boolean? = null
    ) {
        if (!active(context)) return
        batteryPercent?.let { batteryFraction = it.coerceIn(0, 100) / 100f }
        isCharging?.let { charging = it }
        isPowerSave?.let { powerSave = it }
        isLandscape?.let { landscape = it }
        refresh(context)
    }

    @Synchronized
    fun onExtendedDeviceContext(
        context: Context,
        networkConnected: Boolean? = null,
        wifi: Boolean? = null,
        cellular: Boolean? = null,
        wiredAudio: Boolean? = null,
        bluetoothAudio: Boolean? = null,
        moving: Boolean? = null,
        strongMotion: Boolean? = null
    ) {
        if (!active(context)) return
        networkConnected?.let { this.networkConnected = it }
        wifi?.let { this.wifi = it }
        cellular?.let { this.cellular = it }
        wiredAudio?.let { this.wiredAudio = it }
        bluetoothAudio?.let { this.bluetoothAudio = it }
        moving?.let { this.moving = it }
        strongMotion?.let { this.strongMotion = it }
        refresh(context)
    }

    @Synchronized
    fun onSystemState(
        context: Context,
        deviceIdle: Boolean? = null,
        interactive: Boolean? = null,
        userPresent: Boolean? = null,
        thermalStatus: Int? = null
    ) {
        if (!active(context)) return
        deviceIdle?.let { this.deviceIdle = it }
        interactive?.let { this.interactive = it }
        userPresent?.let { this.userPresent = it }
        thermalStatus?.let { this.thermalStatus = it }
        refresh(context)
    }
    @Synchronized
    fun onSensitiveApp(context: Context) {
        currentPackage = ""
        previousPackage = ""
        editorFocused = false
        lastTypedAt = 0L
        appSwitchBurst = 0
        clearHints()
        pendingIntent = null
        handler.removeCallbacks(scheduledRefresh)
        applyIntent(context.applicationContext, OrcaIntent.IDLE, 1f, "privacy reset")
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(scheduledRefresh)
        currentPackage = ""
        previousPackage = ""
        editorFocused = false
        lastTypedAt = 0L
        musicPlaying = false
        mediaPackage = ""
        pendingIntent = null
        lastIntent = null
        lastIntentAt = 0L
        lastConfidence = 0f
        lastMargin = 0f
        appSwitchBurst = 0
        sessionStartedAt = 0L
        deviceIdle = false
        interactive = true
        userPresent = true
        thermalStatus = PowerManager.THERMAL_STATUS_NONE
        lastFeatures.fill(0f)
        smoothedProbabilities.fill(0f)
        clearHints()
    }

    @Synchronized
    fun teach(context: Context, desired: OrcaIntent) {
        restore(context)
        repeat(16) { neural.train(lastFeatures, desired, rate = 0.045f) }
        context.getSharedPreferences("orca_brain", Context.MODE_PRIVATE).edit()
            .putString("weights_v3_multisignal", neural.exportWeights()).apply()
        pendingIntent = null
        applyIntent(context.applicationContext, desired, 1f, "user correction")
        OrcaLogStore.append(context, "User taught ORCA: ${desired.display}; numeric weights updated locally")
    }

    @Synchronized
    fun debugSummary(): String {
        val route = when {
            bluetoothAudio -> "BT"
            wiredAudio -> "wired"
            else -> "speaker"
        }
        val net = when {
            wifi -> "Wi-Fi"
            cellular -> "cellular"
            networkConnected -> "network"
            else -> "offline"
        }
        val motion = if (strongMotion) "high" else if (moving) "moving" else "still"
        val activity = lastIntent?.display ?: "Waiting"
        val confidencePercent = (lastConfidence * 100).toInt()
        val marginPercent = (lastMargin * 100).toInt()
        val batteryPercent = (batteryFraction * 100).toInt()
        val chargeMark = if (charging) "+" else ""
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val key = behaviorMemory.contextKey(currentPackage, hour, isWeekend())
        val memory = appContext?.let { behaviorMemory.summary(it, key) } ?: "personalization waiting"
        val sessionMinutes = if (sessionStartedAt > 0L) {
            ((now() - sessionStartedAt) / 60_000L).coerceAtLeast(0L)
        } else 0L
        return "Activity: $activity | AI $confidencePercent% margin $marginPercent% | " +
            "$net | audio $route | motion $motion | battery $batteryPercent%$chargeMark | " +
            "thermal ${thermalLabel()} | session ${sessionMinutes}m | $memory"
    }

    private fun active(context: Context): Boolean =
        OrcaPrivacy.enabled(context) && FloatingPetService.isRunning

    private fun now(): Long = SystemClock.elapsedRealtime()

    private fun recent(timestamp: Long, duration: Long, time: Long): Boolean =
        timestamp > 0L && time - timestamp < duration

    private fun has(text: String, vararg fragments: String): Boolean =
        fragments.any { text.contains(it) }

    private fun clearHints() {
        searchHint = false
        videoHint = false
        readingHint = false
        loadingHint = false
        errorHint = false
        successHint = false
        lastContextAt = 0L
    }

    private fun featureVector(time: Long): FloatArray {
        val pkg = currentPackage.lowercase()
        val browser = has(pkg, "chrome", "firefox", "browser", "brave", "vivaldi", "edge")
        val musicApp = has(pkg, "spotify", "music", "soundcloud", "deezer", "audio", "podcast")
        val videoApp = has(pkg, "youtube", "netflix", "primevideo", "hotstar", "disney", "video", "vlc")
        val codingApp = has(pkg, "termux", "code", "editor", "github", "replit", "ide")
        val messagingApp = has(pkg, "whatsapp", "telegram", "signal", "messenger", "discord", "messages")
        val typing = editorFocused && recent(lastTypedAt, PAUSE_MS, time)
        val paused = editorFocused && lastTypedAt > 0L && !typing
        val mediaInForeground = musicPlaying && mediaPackage == currentPackage
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour in 0..5
        val isDaytime = hour in 7..21
        val rapidSwitching = appSwitchBurst >= 3 && recent(lastAppSwitchAt, APP_SWITCH_BURST_MS, time)

        return floatArrayOf(
            if (typing) 1f else 0f,
            if (paused) 1f else 0f,
            if (editorFocused) 1f else 0f,
            if (recent(lastScrollAt, SCROLL_MS, time)) 1f else 0f,
            if (recent(lastTapAt, 2500L, time)) 1f else 0f,
            if (mediaInForeground) 1f else 0f,
            if (musicApp) 1f else 0f,
            if (videoApp) 1f else 0f,
            if (browser) 1f else 0f,
            if (codingApp) 1f else 0f,
            if (messagingApp) 1f else 0f,
            if (searchHint) 1f else 0f,
            if (videoHint) 1f else 0f,
            if (readingHint) 1f else 0f,
            if (loadingHint) 1f else 0f,
            if (errorHint && recent(lastContextAt, 6_000L, time)) 1f else 0f,
            if (successHint && recent(lastContextAt, 6_000L, time)) 1f else 0f,
            if (recent(lastNotificationAt, ALERT_MS, time)) 1f else 0f,
            if (lastActivityAt > 0L && time - lastActivityAt > IDLE_MS) 1f else 0f,
            if (!screenOn) 1f else 0f,
            if (charging) 1f else 0f,
            if (batteryFraction <= 0.15f) 1f else 0f,
            if (powerSave) 1f else 0f,
            if (landscape) 1f else 0f,
            if (isNight) 1f else 0f,
            if (isDaytime) 1f else 0f,
            if (networkConnected) 1f else 0f,
            if (wifi) 1f else 0f,
            if (cellular) 1f else 0f,
            if (wiredAudio) 1f else 0f,
            if (bluetoothAudio) 1f else 0f,
            if (moving) 1f else 0f,
            if (strongMotion) 1f else 0f,
            if (rapidSwitching) 1f else 0f
        )
    }

    private fun refresh(context: Context) {
        restore(context)
        refreshInternal(context)
    }

    private fun refreshInternal(context: Context? = null) {
        val app = context?.applicationContext ?: appContext ?: return
        appContext = app
        if (!active(app)) {
            handler.removeCallbacks(scheduledRefresh)
            return
        }

        val time = now()
        val features = featureVector(time)
        lastFeatures = features.copyOf()
        val rawPrediction = neural.predict(features)
        updateSmoothedProbabilities(rawPrediction.probabilities)
        val fusedProbabilities = fuseWithPersonalization(app, smoothedProbabilities)

        val ranked = fusedProbabilities.indices.sortedByDescending { fusedProbabilities[it] }
        val bestIndex = ranked.firstOrNull() ?: OrcaIntent.IDLE.ordinal
        val secondIndex = ranked.getOrNull(1) ?: bestIndex
        val neuralIntent = OrcaIntent.entries[bestIndex]
        val neuralConfidence = fusedProbabilities[bestIndex]
        val margin = (neuralConfidence - fusedProbabilities[secondIndex]).coerceAtLeast(0f)
        lastConfidence = neuralConfidence
        lastMargin = margin

        val hard = hardIntent(features)
        val proposed = hard ?: fallbackIntent(features, neuralIntent, neuralConfidence, margin)
        val chosen = if (hard != null) {
            pendingIntent = null
            proposed
        } else {
            stabilizeSoftIntent(proposed, neuralConfidence, time)
        }

        if (chosen != null && chosen != lastIntent) {
            applyIntent(
                app,
                chosen,
                if (hard != null) 1f else neuralConfidence,
                if (hard != null) "hard signal" else "temporal neural"
            )
        }
        scheduleNext(time)
    }

    private fun updateSmoothedProbabilities(raw: FloatArray) {
        if (smoothedProbabilities.all { it == 0f }) {
            for (i in raw.indices) smoothedProbabilities[i] = raw[i]
            return
        }
        for (i in raw.indices) {
            smoothedProbabilities[i] =
                smoothedProbabilities[i] * EMA_KEEP + raw[i] * (1f - EMA_KEEP)
        }
    }

    private fun hardIntent(features: FloatArray): OrcaIntent? = when {
        !screenOn || deviceIdle || !interactive -> OrcaIntent.SLEEP
        thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE && features[18] == 1f -> OrcaIntent.SLEEP
        sessionStartedAt > 0L && now() - sessionStartedAt >= 90L * 60_000L && features[18] == 1f ->
            OrcaIntent.SLEEP
        features[0] == 1f -> OrcaIntent.TYPING
        features[1] == 1f -> OrcaIntent.THINKING
        features[17] == 1f -> OrcaIntent.NOTIFICATION
        features[15] == 1f -> OrcaIntent.ERROR
        features[16] == 1f -> OrcaIntent.SUCCESS
        features[5] == 1f && features[6] == 1f -> OrcaIntent.MUSIC
        features[5] == 1f && features[7] == 1f -> OrcaIntent.VIDEO
        features[21] == 1f && features[22] == 1f && features[18] == 1f -> OrcaIntent.SLEEP
        else -> null
    }

    private fun fallbackIntent(
        features: FloatArray,
        neuralIntent: OrcaIntent,
        confidence: Float,
        margin: Float
    ): OrcaIntent = when {
        features[18] == 1f -> OrcaIntent.IDLE
        confidence >= 0.48f && margin >= 0.08f -> neuralIntent
        features[9] == 1f -> OrcaIntent.CODING
        features[13] == 1f -> OrcaIntent.READING
        features[8] == 1f && features[26] == 1f -> OrcaIntent.BROWSING
        features[7] == 1f && features[23] == 1f -> OrcaIntent.VIDEO
        else -> OrcaIntent.IDLE
    }

    private fun stabilizeSoftIntent(
        proposed: OrcaIntent,
        confidence: Float,
        time: Long
    ): OrcaIntent? {
        if (proposed == lastIntent) {
            pendingIntent = null
            return proposed
        }
        if (lastIntent == null) return proposed

        if (time - lastIntentAt < SOFT_MIN_DWELL_MS && confidence < 0.78f) {
            setPending(proposed, time)
            return null
        }
        if (pendingIntent != proposed) {
            setPending(proposed, time)
            return null
        }
        return if (time - pendingIntentAt >= SOFT_STABLE_MS) {
            pendingIntent = null
            proposed
        } else null
    }

    private fun setPending(intent: OrcaIntent, time: Long) {
        if (pendingIntent != intent) {
            pendingIntent = intent
            pendingIntentAt = time
        }
    }

    private fun applyIntent(context: Context, intent: OrcaIntent, confidence: Float, reason: String) {
        lastIntent = intent
        lastIntentAt = now()
        OrcaPetController.react(context, intent.animation)

        val learnWeight = when {
            reason == "user correction" -> 8
            reason == "hard signal" && intent in persistentActivities -> 2
            reason == "temporal neural" && confidence >= 0.82f && intent in persistentActivities -> 1
            else -> 0
        }
        if (learnWeight > 0 && currentPackage.isNotEmpty()) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val key = behaviorMemory.contextKey(currentPackage, hour, isWeekend())
            behaviorMemory.observe(context, key, intent, learnWeight)
        }

        OrcaLogStore.append(
            context,
            "Brain: ${intent.display} | $reason | confidence ${(confidence * 100).toInt()}% | app ${currentPackage.take(64)}"
        )
    }

    private fun fuseWithPersonalization(context: Context, base: FloatArray): FloatArray {
        if (currentPackage.isEmpty()) return base.copyOf()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val key = behaviorMemory.contextKey(currentPackage, hour, isWeekend())
        val prior = behaviorMemory.prior(context, key)
        if (prior.blendWeight <= 0f) return base.copyOf()

        val fused = FloatArray(base.size)
        var total = 0f
        for (i in base.indices) {
            fused[i] = base[i] * (1f - prior.blendWeight) +
                prior.probabilities[i] * prior.blendWeight
            total += fused[i]
        }
        if (total > 0f) {
            for (i in fused.indices) fused[i] /= total
        }
        return fused
    }

    private fun isWeekend(): Boolean {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }

    private fun thermalLabel(): String = when {
        thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        else -> "normal"
    }

    private val persistentActivities = setOf(
        OrcaIntent.TYPING,
        OrcaIntent.THINKING,
        OrcaIntent.READING,
        OrcaIntent.BROWSING,
        OrcaIntent.CODING,
        OrcaIntent.MUSIC,
        OrcaIntent.VIDEO,
        OrcaIntent.IDLE,
        OrcaIntent.SLEEP
    )

    private fun scheduleNext(time: Long) {
        handler.removeCallbacks(scheduledRefresh)
        val deadlines = mutableListOf<Long>()
        if (editorFocused && recent(lastTypedAt, PAUSE_MS, time)) deadlines += lastTypedAt + PAUSE_MS
        if (recent(lastNotificationAt, ALERT_MS, time)) deadlines += lastNotificationAt + ALERT_MS
        if (recent(lastScrollAt, SCROLL_MS, time)) deadlines += lastScrollAt + SCROLL_MS
        if (recent(lastTapAt, 2_500L, time)) deadlines += lastTapAt + 2_500L
        if ((errorHint || successHint) && recent(lastContextAt, 6_000L, time)) deadlines += lastContextAt + 6_000L
        if (lastActivityAt > 0L && time - lastActivityAt <= IDLE_MS) deadlines += lastActivityAt + IDLE_MS + 1L
        if (pendingIntent != null) deadlines += pendingIntentAt + SOFT_STABLE_MS
        if (appSwitchBurst > 0 && recent(lastAppSwitchAt, APP_SWITCH_BURST_MS, time)) {
            deadlines += lastAppSwitchAt + APP_SWITCH_BURST_MS
        }
        deadlines.minOrNull()?.let {
            handler.postDelayed(scheduledRefresh, (it - time + 5L).coerceAtLeast(10L))
        }
    }
}
