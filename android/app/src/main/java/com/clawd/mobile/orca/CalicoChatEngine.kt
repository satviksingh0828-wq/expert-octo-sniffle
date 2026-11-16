package com.clawd.mobile.orca

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A real, API-key-free local language-model client. Uses loopback only (Termux llama.cpp). */
object CalicoChatEngine {
    const val ENDPOINT = "http://127.0.0.1:8080/v1/chat/completions"
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    const val OFFLINE_HELP =
        "Calico's pet brain is running offline. For free-form AI conversation, start a local " +
        "llama.cpp OpenAI-compatible server on this Android device at 127.0.0.1:8080. " +
        "No API key, cloud account, or remote model is used."

    /** Streams actual model tokens; never silently substitutes generated-looking scripted replies. */
    suspend fun respond(history: List<CalicoChatMessage>, onToken: suspend (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content",
                "You are Calico, a friendly, concise, playful calico-cat companion. " +
                "Keep answers useful, be clear about limits, and never pretend you can access " +
                "apps, private screens or accounts without user authorization."))
            for (message in history.takeLast(18)) {
                messages.put(JSONObject().put("role", message.role).put("content",
                    message.content.take(4000)))
            }
            val payload = JSONObject()
                .put("model", "local")
                .put("messages", messages)
                .put("stream", true)
                .put("max_tokens", 768)
                .put("temperature", 0.65)
                .toString()
            val request = Request.Builder().url(ENDPOINT)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val output = StringBuilder()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(
                    "Local model returned HTTP " + response.code + ". " + OFFLINE_HELP)
                val source = response.body?.source() ?: throw IOException("Empty local model response")
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val raw = line.substring(6).trim()
                    if (raw == "[DONE]") break
                    val event = try { JSONObject(raw) } catch (_: Exception) { continue }
                    val delta = event.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content").orEmpty()
                    if (delta.isNotEmpty()) {
                        output.append(delta)
                        withContext(Dispatchers.Main) { onToken(delta) }
                    }
                }
            }
            if (output.isEmpty()) throw IOException("The local model returned no text.")
            output.toString()
        }

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
