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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One-time, opt-in download. Inference always runs in this app, not in Termux. */
object CalicoModelManager {
    private const val PREFS = "calico_model_install"
    private const val MODEL_NAME = "Qwen3-0.6B.litertlm"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
    private const val SHA256 =
        "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
    private const val MIN_BYTES = 400_000_000L
    private const val MAX_BYTES = 950_000_000L
    const val APPROXIMATE_MB = 614
    const val INSTALL_HELP =
        "Install Calico's offline Qwen3-0.6B model (about 614 MB) once inside this app. " +
        "Tap Download AI while connected to Wi-Fi. Afterward chat works with no API key, " +
        "Termux process, or external AI server."

    private val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).followRedirects(true).build()

    fun modelFile(context: Context) = File(
        File(context.applicationContext.filesDir, "calico_models"), MODEL_NAME)

    fun ready(context: Context): Boolean {
        val f = modelFile(context)
        return f.isFile && f.length() in MIN_BYTES..MAX_BYTES &&
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("sha256", null) == SHA256
    }

    /** A progress callback on Main. Never starts automatically on first launch. */
    suspend fun install(context: Context, onProgress: suspend (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            if (ready(app)) {
                withContext(Dispatchers.Main) { onProgress(100) }
                return@withContext
            }
            val destination = modelFile(app)
            val folder = destination.parentFile ?: throw IOException("Missing model directory")
            if (!folder.exists() && !folder.mkdirs()) {
                throw IOException("Unable to create private model directory.")
            }
            if (folder.usableSpace < 1_000_000_000L) {
                throw IOException("At least 1 GB of free storage is needed.")
            }
            val partial = File(folder, MODEL_NAME + ".part")
            partial.delete()
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("sha256").apply()
            try {
                val request = Request.Builder().url(MODEL_URL).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Model download failed: HTTP " + response.code)
                    }
                    val body = response.body ?: throw IOException("Empty model download.")
                    val length = body.contentLength().takeIf { it > 0 } ?: 614_000_000L
                    if (length > MAX_BYTES) throw IOException("Unexpected model size.")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = 0L
                    var lastProgress = -1
                    body.byteStream().use { input ->
                        FileOutputStream(partial).use { output ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buf)
                                if (count < 0) break
                                if (count == 0) continue
                                received += count
                                if (received > MAX_BYTES) throw IOException("Unexpected model size.")
                                digest.update(buf, 0, count)
                                output.write(buf, 0, count)
                                val pct = ((received * 100 / length).toInt()).coerceIn(0, 99)
                                if (pct != lastProgress) {
                                    lastProgress = pct
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    if (received !in MIN_BYTES..MAX_BYTES) {
                        throw IOException("Incomplete model; download again.")
                    }
                    val hash = digest.digest().joinToString("") {
                        "%02x".format(it.toInt() and 0xff)
                    }
                    if (hash != SHA256) throw IOException(
                        "Model checksum failed. The untrusted file has been discarded.")
                }
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Cannot replace the previous model.")
                }
                if (!partial.renameTo(destination)) {
                    throw IOException("Cannot finish model installation.")
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("sha256", SHA256).apply()
                withContext(Dispatchers.Main) { onProgress(100) }
            } finally {
                if (partial.exists()) partial.delete()
            }
        }
}

/** Single-threaded LiteRT-LM CPU engine. Pet behavior remains a separate local AI. */
object CalicoOnDeviceModel {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Calico-LiteRT-CPU").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

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
            if (!CalicoModelManager.ready(app)) throw IOException(CalicoModelManager.INSTALL_HELP)
            val path = CalicoModelManager.modelFile(app).absolutePath
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
