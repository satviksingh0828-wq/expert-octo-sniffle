package com.clawd.mobile.orca

import android.content.Context

/** Sensitive data never leaves the app; explicit monitoring consent defaults to OFF. */
object OrcaPrivacy {
    private const val FILE = "orca_local_privacy"
    private val sensitivePackages = listOf(
        "bank", "wallet", "payment", "authenticator", "password", "settings", "health", "medical", "secure"
    )
    private val longNumbers = Regex("\\b[0-9]{4,8}\\b")
    private val emails = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val codes = Regex("(?i)\\b(?:otp|verification code|one.time code|password|pin)\\b.{0,30}")

    fun enabled(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("monitor", false)
    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("monitor", value).apply()
    }
    fun allowedPackage(packageName: String): Boolean = sensitivePackages.none { packageName.contains(it, ignoreCase = true) }

    /** Best-effort redaction; do not rely on it to remove all secrets. */
    fun sanitize(visible: String): String = codes.replace(emails.replace(longNumbers.replace(visible, "[number]"), "[email]"), "[private]")
}
