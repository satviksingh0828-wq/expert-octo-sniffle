package com.clawd.mobile.orca

/**
 * Offline fallback until a native GGUF inference engine is bundled.
 * This is rule-based, NOT Qwen or a trained AI model.
 * Screen text is processed in memory and is not persisted or transmitted.
 */
object OrcaActivityEngine {
    fun decide(packageName: String, screenText: String): String {
        val s = screenText.lowercase()
        return when {
            listOf("i love you", "love you", "congratulations", "thank you so much").any { it in s } -> "calico-happy.apng"
            listOf("error", "failed", "something went wrong").any { it in s } -> "calico-error.apng"
            listOf("new message", "notification").any { it in s } -> "calico-notification.apng"
            listOf("typing", "compose", "write a message").any { it in s } -> "calico-working-typing.apng"
            listOf("music", "playing now", "now playing").any { it in s } -> "calico-working-conducting.apng"
            listOf("loading", "searching", "thinking").any { it in s } -> "calico-thinking.apng"
            else -> "calico-idle.apng"
        }
    }
}
