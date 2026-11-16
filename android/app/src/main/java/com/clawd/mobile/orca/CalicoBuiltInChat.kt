package com.clawd.mobile.orca

/**
 * The existing in-APK pet policy can classify situations and pick animations;
 * it is not a text-generating LLM. This honest, no-download mode gives
 * limited deterministic chat, while using exactly the pet's activity policy.
 */
object CalicoBuiltInChat {
    const val MODEL_LABEL = "Built-in Calico pet brain"
    const val DESCRIPTION = "Already installed • reacts to activities • basic chat, not a language model"

    fun reply(text: String): String {
        val input = text.trim().lowercase()
        val petAnimation = OrcaActivityEngine.decide("calico.chat", input)
        val activity = OrcaActivityEngine.describe(petAnimation)
        return when {
            input.isBlank() -> "I'm Calico! Tell me what's happening and I'll react."
            Regex("\\b(hello|hi|hey|good morning|good evening)\\b").containsMatchIn(input) ->
                "Mrrp! 🐱 I'm Calico, your little cream, orange and brown cat companion. I'm here with you!"
            listOf("your name", "who are you", "what are you").any(input::contains) ->
                "I'm Calico, your offline Android pet. The built-in pet brain watches permitted activity signals and chooses my animations. For longer AI conversations you can download Qwen inside this app."
            listOf("what can you do", "help", "features", "commands").any(input::contains) ->
                "I can react to activity and chat topics, watch the current browser tab when you enable Accessibility, move around your screen, sleep, and open supported Android apps. This built-in mode is a small activity classifier, not a general-purpose language model. Select the downloadable Qwen model for free-form answers."
            listOf("how are you", "feeling", "how's it going").any(input::contains) ->
                "I'm feeling curious and ready to keep you company! 🐾"
            listOf("sleep", "good night", "go to bed").any(input::contains) ->
                "Purr... good night. My sleeping animation is ready. 🌙"
            listOf("love you", "thank you", "thanks", "great job", "well done").any(input::contains) ->
                "Aww! That makes Calico very happy. 🧡"
            listOf("sorry", "sad", "worried", "stressed", "anxious").any(input::contains) ->
                "I'm here to keep you company. Want to tell me a little more? 🐱"
            listOf("music", "song", "dancing", "dance").any(input::contains) ->
                "Music time! My conductor animation is ready. 🎵"
            listOf("study", "exam", "homework", "learn").any(input::contains) ->
                "Study buddy mode! I can react while you work. For explaining a chapter or answering questions, download the optional local Qwen model."
            listOf("error", "crash", "failed", "code", "debug", "kotlin").any(input::contains) ->
                "I noticed a coding or error topic and selected my matching animation. To analyze code or debug an error, select and download the optional local language model."
            else ->
                "I recognized this as a $activity topic and chose a cat reaction. This built-in pet brain has limited replies, so I can't answer every question. Open Models and choose Qwen3-0.6B for full offline conversations without an API key."
        }
    }
}
