package com.clawd.mobile.orca

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class CalicoModelRepo(
    val id: String,
    val title: String,
    val revision: String,
    val downloads: Long,
    val likes: Int,
    val gated: Boolean
)

data class CalicoModelVariant(
    val id: String,
    val repository: String,
    val path: String,
    val revision: String,
    val sizeBytes: Long,
    val sha256: String,
    val title: String
) {
    val hardwareSpecific: Boolean get() {
        val name = path.lowercase()
        return listOf("tensor_g", "qualcomm", "qnn", "_npu", "_tpu",
            "mediatek", "neuro", "hexagon").any(name::contains)
    }
}

data class CalicoInstalledModel(
    val id: String,
    val title: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Public Hugging Face LiteRT community catalog, fetched on demand from inside
 * Android. Search results are discovered, not hardcoded into an APK release.
 *
 * IMPORTANT: .gguf, .safetensors, gated repositories and hardware-specific NPU
 * builds are not advertised as ready for this CPU-only LiteRT-LM runtime.
 */
object CalicoModelLibrary {
    private const val PREFS = "calico_litert_library"
    private const val KEY_INSTALLED = "installed_v1"
    private const val KEY_CACHE = "repo_cache_v1"
    private const val MAX_MODEL_BYTES = 16_000_000_000L
    const val LEGACY_ID = "litert-community/Qwen3-0.6B::Qwen3-0.6B.litertlm"
    private const val LEGACY_SHA =
        "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
    private const val API = "https://huggingface.co/api/models"
    private val shaPattern = Regex("^[0-9a-fA-F]{64}$")
    private val repositoryPattern = Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$")
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storageRoot(context: Context): File =
        File(context.applicationContext.filesDir, "calico_models/library")

    private fun legacyFile(context: Context): File =
        File(context.applicationContext.filesDir, "calico_models/Qwen3-0.6B.litertlm")

    private fun storageFile(context: Context, id: String): File {
        if (id == LEGACY_ID) return legacyFile(context)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(storageRoot(context), hash + ".litertlm")
    }

    private fun array(context: Context): JSONArray = try {
        JSONArray(preferences(context).getString(KEY_INSTALLED, "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun verifiedRows(context: Context): JSONArray {
        val rows = array(context)
        // Read-only migration from Calico 0.18. Never trust a file merely because it exists.
        val old = legacyFile(context)
        val previouslyVerified = context.getSharedPreferences(
            "calico_model_install", Context.MODE_PRIVATE
        ).getString("sha256", null) == LEGACY_SHA
        if (previouslyVerified && old.isFile && old.length() in 400_000_000L..950_000_000L &&
            (0 until rows.length()).none { rows.optJSONObject(it)?.optString("id") == LEGACY_ID }) {
            rows.put(JSONObject().put("id", LEGACY_ID).put("title", "Qwen3-0.6B (legacy)")
                .put("size", old.length()).put("sha256", LEGACY_SHA))
            preferences(context).edit().putString(KEY_INSTALLED, rows.toString()).apply()
        }
        return rows
    }

    @Synchronized
    fun installed(context: Context): List<CalicoInstalledModel> {
        val rows = verifiedRows(context)
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id")
            val sha = row.optString("sha256")
            val file = storageFile(context, id)
            if (!shaPattern.matches(sha) || !file.isFile || file.length() <= 0L ||
                file.length() != row.optLong("size")) return@mapNotNull null
            CalicoInstalledModel(id, row.optString("title"), file.length(), sha)
        }
    }

    fun isInstalled(context: Context, id: String): Boolean =
        installed(context).any { it.id == id }

    fun activeFile(context: Context): File? {
        val id = CalicoModelChoice.get(context)
        if (id == "pet" || !isInstalled(context, id)) return null
        return storageFile(context, id)
    }

    fun activeTitle(context: Context): String =
        installed(context).firstOrNull { it.id == CalicoModelChoice.get(context) }?.title
            ?: "Built-in pet brain"

    fun ready(context: Context): Boolean = activeFile(context) != null

    @Synchronized
    private fun addInstalled(context: Context, option: CalicoModelVariant) {
        val current = verifiedRows(context)
        val updated = JSONArray()
        for (index in 0 until current.length()) {
            val row = current.optJSONObject(index) ?: continue
            if (row.optString("id") != option.id) updated.put(row)
        }
        updated.put(JSONObject().put("id", option.id).put("title", option.title)
            .put("size", option.sizeBytes).put("sha256", option.sha256))
        preferences(context).edit().putString(KEY_INSTALLED, updated.toString()).apply()
    }

    /** Installed model deletion only. The caller must unload the native engine first. */
    @Synchronized
    fun remove(context: Context, id: String): Boolean {
        if (id == CalicoModelChoice.get(context)) return false
        if (!isInstalled(context, id)) return false
        val path = storageFile(context, id)
        if (!path.delete()) return false
        val rows = verifiedRows(context)
        val kept = JSONArray()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optString("id") != id) kept.put(row)
        }
        preferences(context).edit().putString(KEY_INSTALLED, kept.toString()).apply()
        return true
    }

    private fun allowedRepository(repoId: String): Boolean =
        repositoryPattern.matches(repoId) && !repoId.contains("..")

    private fun allowedPath(path: String): Boolean =
        path.endsWith(".litertlm", ignoreCase = true) &&
            path.length in 9..250 && !path.startsWith("/") &&
            path.split("/").all { it.isNotBlank() && it != "." && it != ".." }

    private fun encodedSegments(path: String): String =
        path.split("/").joinToString("/") { Uri.encode(it) }

    private fun jsonArrayFrom(url: String): JSONArray {
        if (!url.startsWith("https://huggingface.co/api/models")) {
            throw IOException("Unsupported model catalog host")
        }
        val req = Request.Builder().url(url).header("Accept", "application/json").get().build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IOException(
                "Catalog returned HTTP " + response.code +
                    if (response.code == 429) " (rate limit). Try again later." else "")
            val body = response.body?.string() ?: throw IOException("Empty catalog response")
            if (body.length > 8_000_000) throw IOException("Catalog response too large")
            return JSONArray(body)
        }
    }

    /**
     * On each Models screen open and search submission, refresh from the public
     * Hub API. Search includes the LiteRT community and other LiteRT repositories.
     * Other unsupported model formats are excluded.
     */
    suspend fun search(context: Context, phrase: String): List<CalicoModelRepo> =
        withContext(Dispatchers.IO) {
            val query = phrase.trim().take(70)
            val URLs = if (query.isBlank()) listOf(
                API + "?author=litert-community&limit=100&full=true",
                API + "?search=litertlm&limit=100&full=true"
            ) else listOf(
                API + "?author=litert-community&search=" + Uri.encode(query) +
                    "&limit=100&full=true",
                API + "?search=" + Uri.encode(query) + "&limit=100&full=true"
            )
            val results = LinkedHashMap<String, CalicoModelRepo>()
            var failed: IOException? = null
            for (url in URLs) {
                try {
                    val arr = jsonArrayFrom(url)
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONObject(i) ?: continue
                        val id = row.optString("id")
                        if (!allowedRepository(id)) continue
                        val gatedValue = row.opt("gated")
                        val gated = gatedValue == true ||
                            (gatedValue is String && gatedValue != "false")
                        if (gated || row.optBoolean("private", false)) continue
                        val siblings = row.optJSONArray("siblings") ?: JSONArray()
                        val hasLiteRTFile = (0 until siblings.length()).any { index ->
                            siblings.optJSONObject(index)?.optString("rfilename")
                                ?.endsWith(".litertlm", ignoreCase = true) == true
                        }
                        val discoverable = id.startsWith("litert-community/") ||
                            id.contains("litert", ignoreCase = true) ||
                            hasLiteRTFile
                        if (!discoverable) continue
                        val info = CalicoModelRepo(
                            id = id,
                            title = id.substringAfterLast("/"),
                            revision = row.optString("sha").takeIf {
                                it.matches(Regex("^[0-9a-fA-F]{40}$"))
                            } ?: "main",
                            downloads = row.optLong("downloads").coerceAtLeast(0),
                            likes = row.optInt("likes").coerceAtLeast(0),
                            gated = false
                        )
                        results[id] = info
                    }
                } catch (error: IOException) { failed = error }
                catch (_: Exception) { failed = IOException("Could not read Hugging Face catalog") }
            }
            val found = results.values.sortedWith(compareByDescending<CalicoModelRepo> {
                it.downloads
            }.thenBy { it.title }).take(150)
            if (found.isNotEmpty()) {
                if (query.isBlank()) {
                    val cached = JSONArray()
                    found.forEach { item ->
                        cached.put(JSONObject().put("id", item.id).put("revision", item.revision)
                            .put("downloads", item.downloads).put("likes", item.likes))
                    }
                    preferences(context).edit().putString(KEY_CACHE, cached.toString()).apply()
                }
                found
            } else if (query.isBlank()) {
                val cache = try {
                    JSONArray(preferences(context).getString(KEY_CACHE, "[]"))
                } catch (_: Exception) { JSONArray() }
                val saved = (0 until cache.length()).mapNotNull { i ->
                    val row = cache.optJSONObject(i) ?: return@mapNotNull null
                    val id = row.optString("id")
                    if (!allowedRepository(id)) return@mapNotNull null
                    CalicoModelRepo(id, id.substringAfterLast("/"),
                        row.optString("revision").ifBlank { "main" },
                        row.optLong("downloads"), row.optInt("likes"), false)
                }
                if (saved.isEmpty() && failed != null) throw failed
                saved
            } else {
                if (failed != null) throw failed
                emptyList()
            }
        }

    /** Model detail search returns actual .litertlm files, checksums and sizes. */
    suspend fun variants(repo: CalicoModelRepo): List<CalicoModelVariant> =
        withContext(Dispatchers.IO) {
            if (!allowedRepository(repo.id) || repo.gated) {
                throw IOException("Private or invalid model repository")
            }
            val revision = repo.revision.takeIf {
                it.matches(Regex("^[0-9a-fA-F]{40}$"))
            } ?: "main"
            val url = API + "/" + encodedSegments(repo.id) +
                "/tree/" + revision + "?recursive=true"
            val files = jsonArrayFrom(url)
            (0 until files.length()).mapNotNull { i ->
                val item = files.optJSONObject(i) ?: return@mapNotNull null
                val path = item.optString("path")
                if (item.optString("type") != "file" || !allowedPath(path)) {
                    return@mapNotNull null
                }
                val lfs = item.optJSONObject("lfs")
                val expected = (lfs?.optString("oid").orEmpty())
                    .takeIf { shaPattern.matches(it) } ?: ""
                val size = lfs?.optLong("size")?.takeIf { it > 0L }
                    ?: item.optLong("size", 0L)
                if (size <= 0L || size > MAX_MODEL_BYTES) return@mapNotNull null
                CalicoModelVariant(
                    id = repo.id + "::" + path,
                    repository = repo.id,
                    path = path,
                    revision = revision,
                    sizeBytes = size,
                    sha256 = expected.lowercase(),
                    title = path.substringAfterLast("/")
                )
            }.take(180)
        }

    /**
     * Resumable install: partial bytes stored under app-private storage and
     * SHA-256 verified against Hugging Face LFS before atomic activation.
     */
    suspend fun install(
        context: Context,
        option: CalicoModelVariant,
        onProgress: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!allowedRepository(option.repository) || !allowedPath(option.path) ||
            option.id != option.repository + "::" + option.path ||
            !shaPattern.matches(option.sha256) ||
            option.sizeBytes !in 1L..MAX_MODEL_BYTES) {
            throw IOException("This model has no verifiable public LiteRT-LM download.")
        }
        val folder = storageRoot(context)
        if (!folder.isDirectory && !folder.mkdirs()) {
            throw IOException("Cannot create model directory.")
        }
        if (isInstalled(context, option.id)) {
            withContext(Dispatchers.Main) { onProgress(100) }
            return@withContext
        }
        val target = storageFile(context, option.id)
        val part = File(folder, target.name + ".part")
        var offset = if (part.isFile) part.length() else 0L
        if (offset > option.sizeBytes) {
            part.delete()
            offset = 0L
        }
        if (folder.usableSpace < option.sizeBytes - offset + 250_000_000L) {
            throw IOException("Not enough free storage for " + option.title + ".")
        }
        val rev = option.revision.takeIf {
            it.matches(Regex("^[0-9a-fA-F]{40}$"))
        } ?: "main"
        val url = "https://huggingface.co/" + encodedSegments(option.repository) +
            "/resolve/" + rev + "/" + encodedSegments(option.path)
        val builder = Request.Builder().url(url).get()
        if (offset > 0L) builder.header("Range", "bytes=" + offset + "-")
        val call = client.newCall(builder.build())
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException(
                    "Download returned HTTP " + response.code +
                        if (response.code == 401 || response.code == 403) {
                            ". This model may be gated or unavailable without an account."
                        } else "")
                val rangeHeader = response.header("Content-Range").orEmpty()
                val resumed = offset > 0L && response.code == 206 &&
                    rangeHeader.startsWith("bytes " + offset + "-")
                if (!resumed) {
                    offset = 0L
                    part.delete()
                }
                val input = response.body?.byteStream()
                    ?: throw IOException("Missing download stream")
                var bytes = offset
                input.use {
                    FileOutputStream(part, resumed).use { output ->
                        val buf = ByteArray(128 * 1024)
                        var progress = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n == 0) continue
                            bytes += n
                            if (bytes > option.sizeBytes) throw IOException("Model is larger than expected.")
                            output.write(buf, 0, n)
                            val percent = (bytes * 100 / option.sizeBytes).toInt().coerceIn(0, 99)
                            if (percent != progress) {
                                progress = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (bytes != option.sizeBytes) throw IOException(
                    "Download incomplete; use Resume to continue.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(part).use { input ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") {
                "%02x".format(it.toInt() and 255)
            }
            if (hash != option.sha256) {
                part.delete()
                throw IOException("Model failed SHA-256 integrity verification.")
            }
            if (target.exists() && !target.delete()) throw IOException(
                "Cannot replace existing model.")
            if (!part.renameTo(target)) throw IOException("Cannot finalize downloaded model.")
            addInstalled(context, option)
            withContext(Dispatchers.Main) { onProgress(100) }
        } catch (error: Exception) {
            if (error !is kotlinx.coroutines.CancellationException &&
                error.message?.contains("SHA-256") == true) part.delete()
            throw error
        } finally { call.cancel() }
    }
}
