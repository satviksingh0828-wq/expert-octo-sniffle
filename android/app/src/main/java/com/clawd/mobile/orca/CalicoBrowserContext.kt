package com.clawd.mobile.orca

/**
 * Local, ephemeral browser-title classifier. It never saves the page title,
 * browser history, URLs, or content. Accessible metadata differs by browser.
 */
object CalicoBrowserContext {
    data class Mood(val category: String, val animation: String)

    fun isBrowser(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return listOf("chrome", "chromium", "firefox", "brave", "vivaldi", "kiwi",
            "browser", "opera", "duckduckgo", "microsoft.emmx", "edge",
            "samsung.android.app.sbrowser", "arc.search").any(pkg::contains)
    }

    fun isSensitive(title: String, visible: String): Boolean {
        val sample = (title + " " + visible.take(700)).lowercase()
        return listOf(
            "incognito", "private browsing", "private tab", "secret mode",
            "password manager", "enter password", "sign in to your bank",
            "online banking", "your otp", "verification code", "one-time password",
            "credit card number", "payment information", "authentication code",
            "confidential", "medical record"
        ).any(sample::contains)
    }

    /**
     * Prefer the actual tab title over generic browser chrome/toolbar labels.
     * The fallback visible text is limited and never leaves memory.
     */
    fun classify(title: String, visible: String): Mood? {
        val label = title.trim().take(180)
        val text = (label + " " + visible.take(560)).lowercase()
        if (text.isBlank() || isSensitive(label, visible)) return null
        fun has(vararg words: String) = words.any(text::contains)
        return when {
            has("error", "failed", "404", "500 internal", "crash", "not found",
                "site can't be reached", "can't be reached") ->
                Mood("web-error", "calico-error.apng")
            has("congratulations", "success", "thank you for", "completed", "achievement") ->
                Mood("success", "calico-happy.apng")
            has("youtube", "watch video", "video player", "twitch", "netflix",
                "shorts", "live stream", "movie") ->
                Mood("video", "calico-mini-peek.apng")
            has("spotify", "song lyrics", "music video", "listen", "album",
                "podcast", "soundcloud") ->
                Mood("music", "calico-working-conducting.apng")
            has("github", "gitlab", "stackoverflow", "stack overflow", "android studio",
                "kotlin", "javascript", "typescript", "python", "programming",
                "coding", "documentation", "developer", "gradle", "source code") ->
                Mood("coding", "calico-working-building.apng")
            has("exam", "study", "homework", "lesson", "tutorial", "revision",
                "school", "college", "education", "class 9", "ca foundation",
                "learn", "chapter", "ncert") ->
                Mood("studying", "calico-working-typing.apng")
            has("wikipedia", "article", "news", "research", "journal", "ebook",
                "book", "guide", "read online", "blog") ->
                Mood("reading", "calico-mini-peek.apng")
            has("shopping", "cart", "flipkart", "amazon", "product", "buy",
                "checkout", "store") ->
                Mood("shopping", "calico-working-juggling.apng")
            has("maps", "directions", "route", "travel", "hotel",
                "train tickets", "flights") ->
                Mood("exploring", "calico-mini-crabwalk.apng")
            has("mail", "inbox", "gmail", "message", "whatsapp web", "discord") ->
                Mood("messages", "calico-notification.apng")
            has("search results", "google search", "search for", "search -",
                "bing", "duckduckgo") ->
                Mood("searching", "calico-thinking.apng")
            has("calendar", "tasks", "to do", "todo", "schedule", "planning",
                "notion", "trello") ->
                Mood("planning", "calico-working-juggling.apng")
            else -> {
                // Even unrelated tabs should not all be the identical animation.
                val variants = arrayOf("calico-mini-peek.apng", "calico-mini-idle.apng",
                    "calico-idle-follow.svg", "calico-working-juggling.apng")
                Mood("exploring-web", variants[(label.lowercase().hashCode() and Int.MAX_VALUE) % variants.size])
            }
        }
    }
}
