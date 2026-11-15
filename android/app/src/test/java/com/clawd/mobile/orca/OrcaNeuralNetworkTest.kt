package com.clawd.mobile.orca

import org.junit.Assert.*
import org.junit.Test

class OrcaNeuralNetworkTest {
    private fun features(vararg active: Int) =
        FloatArray(OrcaNeuralNetwork.INPUTS) { i -> if (i in active) 1f else 0f }

    @Test fun probabilityDistributionIsNormalized() {
        val network = OrcaNeuralNetwork()
        val result = network.predict(features(0, 2))
        assertEquals(OrcaNeuralNetwork.OUTPUTS, result.probabilities.size)
        assertEquals(1f, result.probabilities.sum(), 0.001f)
        assertTrue(result.probabilities.all { it in 0f..1f })
    }

    @Test fun syntheticTypingTrainingPredictsTyping() {
        val network = OrcaNeuralNetwork()
        assertEquals(OrcaIntent.TYPING, network.predict(features(0, 2)).intent)
    }

    @Test fun syntheticPausedEditorPredictsThinking() {
        val network = OrcaNeuralNetwork()
        assertEquals(OrcaIntent.THINKING, network.predict(features(1, 2)).intent)
    }

    @Test fun modelWeightsRoundTrip() {
        val network = OrcaNeuralNetwork()
        val input = features(5, 6)
        val previous = network.predict(input)
        val serialized = network.exportWeights()
        val restored = OrcaNeuralNetwork()
        assertTrue(restored.importWeights(serialized))
        assertEquals(previous.intent, restored.predict(input).intent)
        assertEquals(previous.confidence, restored.predict(input).confidence, 0.00001f)
        assertFalse(restored.importWeights("invalid!"))
    }

    @Test fun userCorrectionImprovesChosenIntent() {
        val network = OrcaNeuralNetwork()
        val input = features(8, 13)
        val before = network.predict(input).probabilities[OrcaIntent.READING.ordinal]
        repeat(20) { network.train(input, OrcaIntent.READING) }
        val after = network.predict(input).probabilities[OrcaIntent.READING.ordinal]
        assertTrue(after > before)
    }
}
