package com.clawd.mobile.orca

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A real, API-key-free local language-model client. Uses loopback only (Termux llama.cpp). */
/** Real native LiteRT-LM inference, fully inside Calico; no Termux or cloud calls. */
object CalicoChatEngine {
    const val OFFLINE_HELP = "Choose an installed local AI model in Models, or use Calico\'s built-in pet brain. No API key is required."
    suspend fun respond(
        context: Context,
        history: List<CalicoChatMessage>,
        onToken: suspend (String) -> Unit
    ): String = CalicoOnDeviceModel.respond(context, history, onToken)

    /** Reuse the pet's offline intent-to-animation vocabulary inside chat. */
    fun animationFor(text: String): String {
        val value = text.lowercase()
        return when {
            listOf("error", "failed", "crash", "broken", "not working").any(value::contains) ->
                "calico-error.apng"
            listOf("sad", "upset", "anxious", "worried", "lonely").any(value::contains) ->
                "calico-dozing.apng"
            listOf("thank", "love", "yay", "great", "awesome", "happy", "hello", "hi!").any(value::contains) ->
                "calico-happy.apng"
            listOf("code", "kotlin", "python", "java", "fix", "build", "program").any(value::contains) ->
                "calico-working-building.apng"
            listOf("study", "exam", "school", "notes", "write", "homework").any(value::contains) ->
                "calico-working-typing.apng"
            listOf("song", "music", "listen", "spotify").any(value::contains) ->
                "calico-working-conducting.apng"
            "?" in value || listOf("why", "how", "explain", "what").any(value::contains) ->
                "calico-thinking.apng"
            else -> "calico-mini-peek.apng"
        }
    }
}

data class CalicoChatMessage(val role: String, val content: String)

data class CalicoChatThread(
    val id: String,
    val title: String,
    val messages: List<CalicoChatMessage>
)

/** Conversation history stays in this app's private on-device storage, never synced. */
object CalicoChatStore {
    private const val NAME = "calico_private_chat"
    private const val KEY = "threads_v1"

    fun load(context: Context): List<CalicoChatThread> = try {
        val saved = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY, "[]")
        val data = JSONArray(saved)
        (0 until data.length()).mapNotNull { i ->
            val row = data.optJSONObject(i) ?: return@mapNotNull null
            val items = row.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until items.length()).mapNotNull { j ->
                val m = items.optJSONObject(j) ?: return@mapNotNull null
                val role = m.optString("role")
                if (role !in listOf("user", "assistant")) return@mapNotNull null
                CalicoChatMessage(role, m.optString("content"))
            }
            CalicoChatThread(row.optString("id"), row.optString("title"), messages)
        }.filter { it.id.isNotBlank() }.take(24)
    } catch (_: Exception) { emptyList() }

    fun save(context: Context, threads: List<CalicoChatThread>) {
        val rows = JSONArray()
        for (thread in threads.take(24)) {
            val messages = JSONArray()
            for (msg in thread.messages.takeLast(60)) {
                messages.put(JSONObject().put("role", msg.role).put("content", msg.content.take(8000)))
            }
            rows.put(JSONObject().put("id", thread.id).put("title", thread.title.take(72))
                .put("messages", messages))
        }
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, rows.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
