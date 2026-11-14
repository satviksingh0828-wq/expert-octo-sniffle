package com.clawd.mobile.orca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaActivityEngineTest {
    @Test fun affectionateMessageIsHappy() {
        assertEquals("calico-happy.apng", OrcaActivityEngine.decide("com.chat", "Hey, I love you"))
    }
    @Test fun errorEventHasErrorAnimation() {
        assertEquals("calico-error.apng", OrcaActivityEngine.decide("com.editor", "Something went wrong"))
    }
    @Test fun typingEventHasWorkingAnimation() {
        assertEquals("calico-working-typing.apng", OrcaActivityEngine.decide("com.chat", "Typing a message"))
    }
    @Test fun unknownScreenFallsBackToIdle() {
        assertEquals("calico-idle.apng", OrcaActivityEngine.decide("com.other", "blank screen"))
    }
    @Test fun privacyExcludesSensitiveApps() {
        assertFalse(OrcaPrivacy.allowedPackage("com.example.mobilebank"))
        assertFalse(OrcaPrivacy.allowedPackage("com.example.passwordmanager"))
        assertTrue(OrcaPrivacy.allowedPackage("com.example.chat"))
    }
    @Test fun privacyRedactsCommonCodes() {
        val result = OrcaPrivacy.sanitize("OTP 123456 and email alice@example.com")
        assertFalse(result.contains("123456"))
        assertFalse(result.contains("alice@example.com"))
    }
}
