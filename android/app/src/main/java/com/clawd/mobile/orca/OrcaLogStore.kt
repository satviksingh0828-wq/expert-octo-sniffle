package com.clawd.mobile.orca

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small bounded local log used by the test UI; no screen text is stored here. */
object OrcaLogStore {
    private const val PREFS = "orca_test_logs"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 200
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun append(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY, null)
        // SharedPreferences StringSet has undefined ordering, so use a newline log instead.
        val old = prefs.getString("log_text", "").orEmpty()
        val next = (old.lines().filter { it.isNotBlank() } + "${format.format(Date())}  $message")
            .takeLast(MAX_ENTRIES)
            .joinToString("\n")
        prefs.edit().remove(KEY).putString("log_text", next).apply()
    }

    @Synchronized
    fun snapshot(context: Context): List<String> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("log_text", "")
        .orEmpty()
        .lines()
        .filter { it.isNotBlank() }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
