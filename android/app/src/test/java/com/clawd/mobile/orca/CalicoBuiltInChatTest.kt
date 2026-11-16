package com.clawd.mobile.orca

import org.junit.Assert.*
import org.junit.Test

class CalicoBuiltInChatTest {
    @Test fun knowsItsName() {
        assertTrue(CalicoBuiltInChat.reply("What is your name?").contains("Calico"))
    }

    @Test fun tellsUserItsLimits() {
        assertTrue(CalicoBuiltInChat.reply("Explain quantum gravity").contains("limited"))
    }

    @Test fun usesPetActivityPolicyWithoutDownload() {
        assertTrue(CalicoBuiltInChat.reply("Help me study for an exam").contains("Study buddy"))
        assertEquals("calico-happy.apng", OrcaActivityEngine.decide("calico.chat", "success"))
    }
}
