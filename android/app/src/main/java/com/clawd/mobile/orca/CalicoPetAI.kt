package com.clawd.mobile.orca

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.clawd.mobile.overlay.FloatingPetService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The optional user-selected model is shared by chat and pet. */
object CalicoModelChoice {
    private const val PREF = "calico_model_choice"
    private const val KEY = "active_chat_and_pet"

    fun get(context: Context): String =
        if (context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, "pet") == "qwen") "qwen" else "pet"

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, if (value == "qwen") "qwen" else "pet").apply()
    }

    fun qwenEnabled(context: Context): Boolean =
        get(context) == "qwen" && CalicoModelManager.ready(context)
}

/**
 * Optional Qwen animation refinements. The pet's existing neural brain is
 * instant and always active as fallback. Qwen decisions are throttled, verified
 * against the asset allowlist, and use *no raw title, URL or screen text*.
 */
object CalicoPetAI {
    private const val MIN_INTERVAL_MS = 45_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastAttempt = 0L
    private var contextGeneration = 0L
    private var running: Job? = null

    @Synchronized
    fun invalidate() {
        contextGeneration++
    }

    @Synchronized
    fun refine(context: Context, category: String, builtInAnimation: String) {
        val app = context.applicationContext
        if (!OrcaPrivacy.enabled(app) || !FloatingPetService.isRunning ||
            !CalicoModelChoice.qwenEnabled(app)) return
        if (category in setOf("sleep", "sensitive", "notification", "typing", "screen off")) return
        if (builtInAnimation !in OrcaPetController.animations) return
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isInteractive == false || power?.isPowerSaveMode == true) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttempt < MIN_INTERVAL_MS || running?.isActive == true) return
        lastAttempt = now
        val token = ++contextGeneration
        val label = category.lowercase().replace(Regex("[^a-z -]"), "").take(35)
        val options = listOf(
            "calico-happy.apng", "calico-thinking.apng", "calico-error.apng",
            "calico-working-building.apng", "calico-working-typing.apng",
            "calico-working-conducting.apng", "calico-working-juggling.apng",
            "calico-working-carrying.apng", "calico-working-sweeping.apng",
            "calico-mini-peek.apng", "calico-mini-crabwalk.apng",
            "calico-mini-happy.apng", "calico-idle.apng", "calico-idle-follow.svg"
        )
        running = scope.launch {
            try {
                val prompt = "You direct animations for Calico, an Android cat. Choose exactly ONE " +
                    "filename from this list for activity '" + label + "': " +
                    options.joinToString(", ") + ". " +
                    "The existing pet brain proposed '" + builtInAnimation + "'. " +
                    "Reply ONLY with the one filename; no explanation."
                val output = CalicoOnDeviceModel.respond(
                    app, listOf(CalicoChatMessage("user", prompt))
                ) { /* Only use the final validated animation. */ }
                val animation = options.firstOrNull { output.contains(it) }
                if (animation != null) {
                    synchronized(this@CalicoPetAI) {
                        if (token == contextGeneration &&
                            CalicoModelChoice.qwenEnabled(app) &&
                            (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                                ?.isInteractive != false) {
                            OrcaPetController.react(app, animation)
                        }
                    }
                }
            } catch (_: Exception) {
                // Chat and pet remain usable when the LLM is busy or unavailable.
            }
        }
    }
}
