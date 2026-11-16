package com.clawd.mobile.orca

import org.junit.Assert.*
import org.junit.Test

class CalicoModelLibraryTest {
    @Test fun modelVariantsDeclareHardwareLimits() {
        val cpuCandidate = CalicoModelVariant(
            "litert-community/Qwen3-0.6B::Qwen3-0.6B.litertlm",
            "litert-community/Qwen3-0.6B",
            "Qwen3-0.6B.litertlm", "main", 614_000_000L,
            "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",
            "Qwen3-0.6B")
        assertFalse(cpuCandidate.hardwareSpecific)
        val pixelNpu = cpuCandidate.copy(path = "gemma4_Google_Tensor_G6.litertlm")
        assertTrue(pixelNpu.hardwareSpecific)
        val snapdragonNpu = cpuCandidate.copy(path = "gemma4_Qualcomm_QNN.litertlm")
        assertTrue(snapdragonNpu.hardwareSpecific)
    }

    @Test fun builtInModelStillHasNoDownloadRequirement() {
        assertEquals("pet", "pet")
        assertTrue(CalicoModelLibrary.LEGACY_ID.endsWith(".litertlm"))
    }
}
