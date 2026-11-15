package com.clawd.mobile.orca

import android.content.Context

/** Local-only controller facade. No network, API key, model download, or external AI service. */
object OrcaModel {
    fun installed(context: Context): Boolean = true
    fun nativeAvailable(): Boolean = false

    fun decide(context: Context, app: String, visible: String, typing: Boolean = false, done: (String) -> Unit) {
        val animation = if (typing) "calico-working-typing.apng" else OrcaActivityEngine.decide(app, visible)
        OrcaLogStore.append(context, "Screen description classified as ${OrcaActivityEngine.describe(animation)} -> $animation")
        done(animation)
    }

    fun chat(context: Context, question: String, done: (String) -> Unit) {
        done("ORCA uses its built-in offline screen-to-animation controller. It does not send data to an AI API.")
    }
}
