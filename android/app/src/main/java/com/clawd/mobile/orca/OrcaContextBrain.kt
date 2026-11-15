package com.clawd.mobile.orca

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.clawd.mobile.overlay.FloatingPetService

/**
 * Privacy-conscious, event-driven local context memory and animation director.
 * Input: foreground app class, UI events/timing, optional visible non-editable
 * context hints, actual media session playback and notification arrival.
 * Output: one classified PetIntent and corresponding packaged Calico asset.
 *
 * No screenshots, screen text, chats, contacts, URLs or song titles are stored.
 * Raw text is reduced to booleans at the point of capture.
 */
object OrcaContextBrain {
    private const val PAUSE_MS = 10_000L
    private const val ALERT_MS = 4_500L
    private const val SCROLL_MS = 3_500L
    private const val IDLE_MS = 90_000L

    private val handler = Handler(Looper.getMainLooper())
    private val neural by lazy { OrcaNeuralNetwork() }
    private var restored = false
    private var currentPackage = ""
    private var editorFocused = false
    private var lastTypedAt = 0L
    private var lastScrollAt = 0L
    private var lastTapAt = 0L
    private var lastNotificationAt = 0L
    private var lastActivityAt = 0L
    private var musicPlaying = false
    private var mediaPackage = ""
    private var screenOn = true

    // All text-derived flags are coarse, ephemeral and never persisted.
    private var searchHint = false
    private var videoHint = false
    private var readingHint = false
    private var loadingHint = false
    private var errorHint = false
    private var successHint = false

    private var lastIntent: OrcaIntent? = null
    private var lastConfidence = 0f
    private var lastFeatures = FloatArray(OrcaNeuralNetwork.INPUTS)
    private val scheduledRefresh = Runnable { synchronized(this) { refreshInternal() } }

    @Synchronized
    private fun restore(context: Context) {
        if (restored) return
        restored = true
        val saved = context.getSharedPreferences("orca_brain", Context.MODE_PRIVATE)
            .getString("weights_v1", null)
        if (saved != null && !neural.importWeights(saved)) {
            OrcaLogStore.append(context, "Stored neural weights were invalid; using defaults")
        }
    }

    @Synchronized
    fun onForeground(context: Context, pkg: String) {
        if (!active(context)) return
        if (pkg == currentPackage) return
        currentPackage = pkg
        editorFocused = false
        lastTypedAt = 0L
        lastScrollAt = 0L
        lastTapAt = 0L
        lastActivityAt = now()
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
        // A background music session never overrides foreground typing or study.
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

    /** Clearing context on sensitive apps avoids stale reactions and data retention. */
    @Synchronized
    fun onSensitiveApp(context: Context) {
        currentPackage = ""
        editorFocused = false
        lastTypedAt = 0L
        clearHints()
        handler.removeCallbacks(scheduledRefresh)
        lastIntent = OrcaIntent.IDLE
        if (active(context)) OrcaPetController.react(context, OrcaIntent.IDLE.animation)
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(scheduledRefresh)
        currentPackage = ""
        editorFocused = false
        lastTypedAt = 0L
        musicPlaying = false
        mediaPackage = ""
        lastIntent = null
        lastConfidence = 0f
        lastFeatures.fill(0f)
        clearHints()
    }

    /** UI correction trains the on-device MLP and saves ONLY numeric weights. */
    @Synchronized
    fun teach(context: Context, desired: OrcaIntent) {
        restore(context)
        repeat(12) { neural.train(lastFeatures, desired, rate = 0.055f) }
        context.getSharedPreferences("orca_brain", Context.MODE_PRIVATE).edit()
            .putString("weights_v1", neural.exportWeights()).apply()
        OrcaLogStore.append(context, "User corrected activity to ${desired.display}; local neural weights updated")
        lastIntent = desired
        if (active(context)) OrcaPetController.react(context, desired.animation)
    }

    @Synchronized
    fun debugSummary(): String = "Activity: ${lastIntent?.display ?: "Waiting"} | " +
        "Confidence: ${(lastConfidence * 100).toInt()}% | " +
        "App: ${currentPackage.ifEmpty { "none" }} | Model: offline neural MLP"

    private fun active(context: Context): Boolean =
        OrcaPrivacy.enabled(context) && FloatingPetService.isRunning

    private fun now(): Long = SystemClock.elapsedRealtime()
    private fun recent(timestamp: Long, duration: Long, time: Long) =
        timestamp > 0L && time - timestamp < duration

    private fun has(text: String, vararg fragments: String) =
        fragments.any { text.contains(it) }

    private fun clearHints() {
        searchHint = false; videoHint = false; readingHint = false
        loadingHint = false; errorHint = false; successHint = false
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
        return floatArrayOf(
            if (typing) 1f else 0f,                       // 0 typing recently
            if (paused) 1f else 0f,                       // 1 paused in editor
            if (editorFocused) 1f else 0f,                // 2 editor focused
            if (recent(lastScrollAt, SCROLL_MS, time)) 1f else 0f, // 3 scrolling
            if (recent(lastTapAt, 2500L, time)) 1f else 0f,       // 4 tapping
            if (mediaInForeground) 1f else 0f,           // 5 playing media
            if (musicApp) 1f else 0f,                    // 6 music app
            if (videoApp) 1f else 0f,                    // 7 video app
            if (browser) 1f else 0f,                     // 8 browser
            if (codingApp) 1f else 0f,                   // 9 coding app
            if (messagingApp) 1f else 0f,                // 10 messaging
            if (searchHint) 1f else 0f,                  // 11 search
            if (videoHint) 1f else 0f,                   // 12 video content
            if (readingHint) 1f else 0f,                 // 13 reading
            if (loadingHint) 1f else 0f,                 // 14 loading
            if (errorHint) 1f else 0f,                   // 15 error
            if (successHint) 1f else 0f,                 // 16 success
            if (recent(lastNotificationAt, ALERT_MS, time)) 1f else 0f, // 17 alert
            if (lastActivityAt > 0L && time - lastActivityAt > IDLE_MS) 1f else 0f, // 18 idle
            if (!screenOn) 1f else 0f                     // 19 screen off
        )
    }

    private fun refresh(context: Context) {
        restore(context)
        refreshInternal(context)
    }

    /** A single state arbiter; stable event priorities override uncertain MLP guesses. */
    private fun refreshInternal(context: Context? = null) {
        // Timer fires without a Context; keep a safe application Context only.
        val app = context?.applicationContext ?: appContext ?: return
        appContext = app
        if (!active(app)) {
            handler.removeCallbacks(scheduledRefresh)
            return
        }
        val time = now()
        val features = featureVector(time)
        lastFeatures = features.copyOf()
        val prediction = neural.predict(features)
        lastConfidence = prediction.confidence
        val chosen = when {
            !screenOn -> OrcaIntent.SLEEP
            features[0] == 1f -> OrcaIntent.TYPING
            features[1] == 1f -> OrcaIntent.THINKING
            features[17] == 1f -> OrcaIntent.NOTIFICATION
            features[15] == 1f -> OrcaIntent.ERROR
            features[16] == 1f -> OrcaIntent.SUCCESS
            features[5] == 1f && features[6] == 1f -> OrcaIntent.MUSIC
            features[5] == 1f && features[7] == 1f -> OrcaIntent.VIDEO
            features[18] == 1f -> OrcaIntent.IDLE
            prediction.confidence >= 0.42f -> prediction.intent
            features[9] == 1f -> OrcaIntent.CODING
            features[13] == 1f -> OrcaIntent.READING
            features[8] == 1f -> OrcaIntent.BROWSING
            else -> OrcaIntent.IDLE
        }
        if (lastIntent != chosen) {
            lastIntent = chosen
            OrcaPetController.react(app, chosen.animation)
            OrcaLogStore.append(app, "Brain: ${chosen.display} | neural ${prediction.intent.display} " +
                "(${(prediction.confidence * 100).toInt()}%) | app ${currentPackage.take(64)}")
        }
        // Single next-deadline timer; no permanent polling or screen reads.
        handler.removeCallbacks(scheduledRefresh)
        val deadlines = listOfNotNull(
            lastTypedAt.takeIf { editorFocused && recent(it, PAUSE_MS, time) }?.plus(PAUSE_MS),
            lastNotificationAt.takeIf { recent(it, ALERT_MS, time) }?.plus(ALERT_MS),
            lastScrollAt.takeIf { recent(it, SCROLL_MS, time) }?.plus(SCROLL_MS),
            lastTapAt.takeIf { recent(it, 2500L, time) }?.plus(2500L),
            lastActivityAt.takeIf { it > 0L && time - it <= IDLE_MS }?.plus(IDLE_MS + 1L)
        )
        deadlines.minOrNull()?.let { handler.postDelayed(scheduledRefresh, (it - time + 5L).coerceAtLeast(10L)) }
    }

    private var appContext: Context? = null
}
