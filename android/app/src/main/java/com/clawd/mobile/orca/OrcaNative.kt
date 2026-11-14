package com.clawd.mobile.orca

/** ARM64 llama.cpp JNI interface packaged inside ORCA's APK. */
object OrcaNative {
    val available: Boolean = try { System.loadLibrary("orca_llama"); true } catch (_: UnsatisfiedLinkError) { false }
    external fun loadModel(path: String, contextSize: Int, threads: Int): Boolean
    /** Raw UTF-8 bytes prevent JNI modified-UTF-8 problems with emojis or other scripts. */
    external fun generate(system: String, user: String, maxTokens: Int): ByteArray
    external fun unloadModel()
}
