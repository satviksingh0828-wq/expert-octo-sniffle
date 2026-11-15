package com.clawd.mobile.orca

import android.content.Context
import kotlin.math.min

/**
 * Small persistent personalization layer.
 *
 * ORCA learns only coarse context buckets (browser/coding/media/messaging/other)
 * crossed with a broad time-of-day bucket. It stores intent counters, never raw
 * screen text, URLs, contacts, notification bodies, SSIDs, or sensor traces.
 *
 * The learned prior is deliberately capped so personalization can guide the
 * neural classifier without overpowering clear real-time evidence.
 */
class OrcaBehaviorMemory {
    enum class AppCategory { BROWSER, CODING, MUSIC, VIDEO, MESSAGING, OTHER }
    enum class TimeBucket { MORNING, DAY, EVENING, NIGHT }

    data class ContextKey(
        val category: AppCategory,
        val timeBucket: TimeBucket,
        val weekend: Boolean
    ) {
        val id: String
            get() = "${category.name}_${timeBucket.name}_${if (weekend) "WEEKEND" else "WEEKDAY"}"
    }

    data class Prior(
        val probabilities: FloatArray,
        val samples: Int,
        val blendWeight: Float
    )

    companion object {
        private const val PREFS = "orca_behavior_memory_v1"
        private const val MAX_COUNTER = 50_000
        private const val MAX_BLEND = 0.24f
    }

    fun contextKey(packageName: String, hour: Int, weekend: Boolean): ContextKey =
        ContextKey(categoryFor(packageName), timeBucketFor(hour), weekend)

    fun categoryFor(packageName: String): AppCategory {
        val pkg = packageName.lowercase()
        return when {
            has(pkg, "chrome", "firefox", "browser", "brave", "vivaldi", "edge", "opera") ->
                AppCategory.BROWSER
            has(pkg, "termux", "code", "editor", "github", "replit", "ide", "studio") ->
                AppCategory.CODING
            has(pkg, "spotify", "music", "soundcloud", "deezer", "podcast", "audio") ->
                AppCategory.MUSIC
            has(pkg, "youtube", "netflix", "primevideo", "hotstar", "disney", "video", "vlc") ->
                AppCategory.VIDEO
            has(pkg, "whatsapp", "telegram", "signal", "messenger", "discord", "messages") ->
                AppCategory.MESSAGING
            else -> AppCategory.OTHER
        }
    }

    fun timeBucketFor(hour: Int): TimeBucket = when (hour.coerceIn(0, 23)) {
        in 5..10 -> TimeBucket.MORNING
        in 11..16 -> TimeBucket.DAY
        in 17..22 -> TimeBucket.EVENING
        else -> TimeBucket.NIGHT
    }

    fun prior(context: Context, key: ContextKey): Prior {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val counts = IntArray(OrcaIntent.entries.size)
        var total = 0
        for (intent in OrcaIntent.entries) {
            val value = prefs.getInt(counterKey(key, intent), 0).coerceAtLeast(0)
            counts[intent.ordinal] = value
            total += value
        }

        // Laplace smoothing prevents zero-probability intents.
        val denom = total + OrcaIntent.entries.size
        val probabilities = FloatArray(OrcaIntent.entries.size) { index ->
            (counts[index] + 1f) / denom.toFloat()
        }

        // Personalization ramps in slowly and never exceeds 24% of soft evidence.
        val blend = if (total <= 0) 0f else min(MAX_BLEND, total / 120f * MAX_BLEND)
        return Prior(probabilities, total, blend)
    }

    fun observe(
        context: Context,
        key: ContextKey,
        intent: OrcaIntent,
        weight: Int
    ) {
        if (weight <= 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val counter = counterKey(key, intent)
        val totalKey = totalKey(key)
        val current = prefs.getInt(counter, 0)
        val total = prefs.getInt(totalKey, 0)
        prefs.edit()
            .putInt(counter, (current + weight).coerceAtMost(MAX_COUNTER))
            .putInt(totalKey, (total + weight).coerceAtMost(MAX_COUNTER))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun summary(context: Context, key: ContextKey): String {
        val prior = prior(context, key)
        if (prior.samples == 0) return "personalization new"
        val top = prior.probabilities.indices.maxByOrNull { prior.probabilities[it] }
            ?: OrcaIntent.IDLE.ordinal
        return "personalization ${prior.samples} samples, prefers ${OrcaIntent.entries[top].display}"
    }

    private fun counterKey(key: ContextKey, intent: OrcaIntent) =
        "c_${key.id}_${intent.name}"

    private fun totalKey(key: ContextKey) = "t_${key.id}"

    private fun has(text: String, vararg fragments: String) =
        fragments.any(text::contains)
}
