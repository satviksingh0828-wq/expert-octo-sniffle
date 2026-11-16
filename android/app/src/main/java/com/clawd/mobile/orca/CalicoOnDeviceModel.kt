package com.clawd.mobile.orca

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors

/** Single-threaded LiteRT-LM CPU engine. Pet behavior remains a separate local AI. */
object CalicoOnDeviceModel {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Calico-LiteRT-CPU").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

    /** Safely unload a model before deleting files from the app's private storage. */
    suspend fun unload() = withContext(dispatcher) {
        mutex.withLock {
            engine?.close()
            engine = null
            loadedPath = null
        }
    }

    private const val IDENTITY =
        "You are Calico, a cheerful cream, orange and brown animated calico cat " +
        "who lives inside this user's Android phone. You are a caring and useful " +
        "offline AI companion. Keep responses concise unless asked for detail. " +
        "Calico is your name, and your emotions control 28 real pet animations. " +
        "Never claim access to private screens or online Google accounts without " +
        "an explicit permission and actual Android action. You do not use cloud AI."

    suspend fun respond(
        context: Context,
        history: List<CalicoChatMessage>,
        onToken: suspend (String) -> Unit
    ): String = withContext(dispatcher) {
        mutex.withLock {
            val app = context.applicationContext
            val model = CalicoModelLibrary.activeFile(app) ?: throw IOException(
                "Select an installed local model from Calico's Models screen.")
            val path = model.absolutePath
            if (engine == null || loadedPath != path) {
                engine?.close()
                engine = null
                loadedPath = null
                val candidate = Engine(EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    cacheDir = app.cacheDir.absolutePath
                ))
                // Never close a partially initialized Engine.
                candidate.initialize()
                engine = candidate
                loadedPath = path
            }
            val prompt = history.lastOrNull { it.role == "user" }?.content
                ?: throw IOException("Enter a message first.")
            val previous = history.dropLast(1).takeLast(12).map { message ->
                if (message.role == "user") Message.user(message.content.take(2000))
                else Message.model(message.content.take(2000))
            }
            val conversation = requireNotNull(engine).createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(IDENTITY),
                    initialMessages = previous
                )
            )
            val reply = StringBuilder()
            try {
                conversation.sendMessageAsync(prompt.take(4000)).collect { chunk ->
                    val token = chunk.toString()
                    if (token.isNotBlank()) {
                        reply.append(token)
                        withContext(Dispatchers.Main) { onToken(token) }
                    }
                }
                if (reply.isEmpty()) throw IOException("The local model returned an empty reply.")
                reply.toString()
            } finally {
                conversation.close()
            }
        }
    }
}
