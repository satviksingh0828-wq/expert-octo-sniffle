package com.clawd.mobile.orca

/**
 * Built-in offline screen-description AI policy.
 * It never calls an API and never needs a downloaded model. It classifies the
 * sanitized description into a pet intent, then selects a packaged animation.
 */
object OrcaActivityEngine {
    private val happy = listOf("success", "succeeded", "congrat", "thank", "love", "great job", "complete", "completed")
    private val error = listOf("error", "failed", "failure", "crash", "exception", "cannot", "couldn't", "something went wrong")
    private val notification = listOf("notification", "new message", "message", "email", "alert", "mention", "reply")
    private val typing = listOf("typing", "compose", "write", "editing", "editor", "draft", "keyboard")
    private val building = listOf("build", "compile", "terminal", "gradle", "install", "deploy", "code", "debug")
    private val conducting = listOf("music", "playing", "audio", "podcast", "song", "meeting")
    private val juggling = listOf("search", "results", "list", "multiple", "tabs", "tasks", "queue")
    private val thinking = listOf("loading", "searching", "thinking", "processing", "please wait", "waiting")
    private val sleeping = listOf("sleep", "good night", "do not disturb", "screen off", "lock screen")

    /** Every packaged Calico animation is allowlisted, including mini reactions. */
    val animations: Set<String> = setOf(
        "calico-collapsing.apng", "calico-dozing.apng", "calico-error.apng", "calico-happy.apng",
        "calico-idle-follow.svg", "calico-idle.apng", "calico-mini-alert.apng", "calico-mini-crabwalk.apng",
        "calico-mini-enter.apng", "calico-mini-happy.apng", "calico-mini-idle.apng", "calico-mini-peek.apng",
        "calico-mini-sleep.apng", "calico-notification.apng", "calico-react-drag.apng", "calico-react-left.apng",
        "calico-react-poke.apng", "calico-sleeping.apng", "calico-thinking.apng", "calico-waking.apng",
        "calico-working-building.apng", "calico-working-carrying.apng", "calico-working-conducting.apng",
        "calico-working-juggling.apng", "calico-working-sweeping.apng", "calico-working-typing.apng",
        "calico-yawning.apng"
    )

    fun decide(packageName: String, screenText: String): String {
        val text = "$packageName $screenText".lowercase()
        return when {
            sleeping.any(text::contains) -> "calico-sleeping.apng"
            error.any(text::contains) -> "calico-error.apng"
            notification.any(text::contains) -> "calico-notification.apng"
            happy.any(text::contains) -> "calico-happy.apng"
            building.any(text::contains) -> "calico-working-building.apng"
            typing.any(text::contains) -> "calico-working-typing.apng"
            conducting.any(text::contains) -> "calico-working-conducting.apng"
            juggling.any(text::contains) -> "calico-working-juggling.apng"
            thinking.any(text::contains) -> "calico-thinking.apng"
            else -> idleFor(text)
        }
    }

    /**
     * Deterministic non-AI responses to actual Android events. Event kinds are
     * coarse metadata only; text entered into another app is never required.
     */
    fun eventAnimation(kind: String): String? = when (kind) {
        "typing", "editor_focus" -> "calico-working-typing.apng"
        "notification", "message" -> "calico-notification.apng"
        "music" -> "calico-working-conducting.apng"
        "tap" -> "calico-react-poke.apng"
        "scroll" -> "calico-mini-crabwalk.apng"
        "task_complete", "success" -> "calico-happy.apng"
        "failure" -> "calico-error.apng"
        "thinking" -> "calico-thinking.apng"
        "building" -> "calico-working-building.apng"
        "multitasking" -> "calico-working-juggling.apng"
        "moving" -> "calico-working-carrying.apng"
        "cleaning" -> "calico-working-sweeping.apng"
        "peek" -> "calico-mini-peek.apng"
        "alert" -> "calico-mini-alert.apng"
        "wake" -> "calico-waking.apng"
        "sleep" -> "calico-sleeping.apng"
        "yawn" -> "calico-yawning.apng"
        "doze" -> "calico-dozing.apng"
        "collapse" -> "calico-collapsing.apng"
        "enter" -> "calico-mini-enter.apng"
        "celebrate" -> "calico-mini-happy.apng"
        "wander" -> "calico-mini-crabwalk.apng"
        "idle" -> "calico-idle.apng"
        else -> null
    }

    fun describe(animation: String): String = when (animation) {
        "calico-error.apng" -> "error state"
        "calico-notification.apng" -> "notification state"
        "calico-happy.apng" -> "positive/success state"
        "calico-working-building.apng" -> "coding/build state"
        "calico-working-typing.apng" -> "typing/editing state"
        "calico-working-conducting.apng" -> "audio/meeting state"
        "calico-working-juggling.apng" -> "multiple tasks/results state"
        "calico-thinking.apng" -> "loading/thinking state"
        "calico-sleeping.apng" -> "sleep state"
        else -> "idle state"
    }

    private fun idleFor(text: String): String = when (text.hashCode().absoluteValue % 4) {
        0 -> "calico-idle-follow.svg"
        1 -> "calico-idle.apng"
        2 -> "calico-mini-peek.apng"
        else -> "calico-mini-idle.apng"
    }

    private val Int.absoluteValue: Int get() = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)
}
