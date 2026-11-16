package com.clawd.mobile.orca

import org.junit.Assert.*
import org.junit.Test

class CalicoBrowserContextTest {
    @Test fun classifiesTopicsFromTabTitle() {
        assertEquals("coding", CalicoBrowserContext.classify("GitHub Kotlin Repository", "")?.category)
        assertEquals("studying", CalicoBrowserContext.classify("CA Foundation exam chapter", "")?.category)
        assertEquals("music", CalicoBrowserContext.classify("Spotify music playlist", "")?.category)
        assertEquals("video", CalicoBrowserContext.classify("YouTube - watch video", "")?.category)
        assertEquals("web-error", CalicoBrowserContext.classify("404 Not Found", "")?.category)
    }

    @Test fun blocksSensitiveBrowsing() {
        assertNull(CalicoBrowserContext.classify("Private tab", "incognito browsing"))
        assertNull(CalicoBrowserContext.classify("Password manager", "account"))
    }

    @Test fun unknownTitlesVaryWithoutNetwork() {
        val first = CalicoBrowserContext.classify("Green mountains", "")
        val second = CalicoBrowserContext.classify("Learn to bake bread", "")
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(CalicoBrowserContext.isBrowser("com.android.chrome"))
        assertFalse(CalicoBrowserContext.isBrowser("com.calico.chat"))
        assertEquals(first, CalicoBrowserContext.classify("Green mountains", ""))
    }
}
