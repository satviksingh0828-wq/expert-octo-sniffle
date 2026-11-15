package com.clawd.mobile.orca

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Random
import kotlin.math.exp
import kotlin.math.tanh

/**
 * Lightweight, real, offline feed-forward activity classifier.
 *
 * 34 normalized context signals -> 32 tanh neurons -> 12 softmax intents.
 * Bootstrap data is synthetic device/activity metadata. Raw screen content is
 * never part of the persisted model. Explicit user corrections can fine-tune it.
 */
class OrcaNeuralNetwork {
    companion object {
        const val INPUTS = 34
        private const val HIDDEN = 32
        const val OUTPUTS = 12
    }

    data class Prediction(
        val intent: OrcaIntent,
        val confidence: Float,
        val probabilities: FloatArray
    )

    private val w1 = FloatArray(HIDDEN * INPUTS)
    private val b1 = FloatArray(HIDDEN)
    private val w2 = FloatArray(OUTPUTS * HIDDEN)
    private val b2 = FloatArray(OUTPUTS)

    init {
        val rng = Random(2026111502L)
        for (i in w1.indices) w1[i] = (rng.nextGaussian() * 0.13).toFloat()
        for (i in w2.indices) w2[i] = (rng.nextGaussian() * 0.13).toFloat()
        bootstrap()
    }

    @Synchronized
    fun predict(input: FloatArray): Prediction {
        require(input.size == INPUTS)
        val (_, probabilities) = forward(input)
        val best = probabilities.indices.maxByOrNull { probabilities[it] } ?: OrcaIntent.IDLE.ordinal
        return Prediction(OrcaIntent.entries[best], probabilities[best], probabilities)
    }

    @Synchronized
    fun train(input: FloatArray, desired: OrcaIntent, rate: Float = 0.03f) {
        require(input.size == INPUTS)
        val (hidden, probabilities) = forward(input)
        val outputGrad = FloatArray(OUTPUTS) { index ->
            probabilities[index] - if (index == desired.ordinal) 1f else 0f
        }
        val hiddenGrad = FloatArray(HIDDEN) { hiddenIndex ->
            var sum = 0f
            for (outputIndex in 0 until OUTPUTS) {
                sum += outputGrad[outputIndex] * w2[outputIndex * HIDDEN + hiddenIndex]
            }
            sum * (1f - hidden[hiddenIndex] * hidden[hiddenIndex])
        }

        for (outputIndex in 0 until OUTPUTS) {
            val base = outputIndex * HIDDEN
            for (hiddenIndex in 0 until HIDDEN) {
                w2[base + hiddenIndex] -= rate * outputGrad[outputIndex] * hidden[hiddenIndex]
            }
            b2[outputIndex] -= rate * outputGrad[outputIndex]
        }
        for (hiddenIndex in 0 until HIDDEN) {
            val base = hiddenIndex * INPUTS
            for (inputIndex in 0 until INPUTS) {
                w1[base + inputIndex] -= rate * hiddenGrad[hiddenIndex] * input[inputIndex]
            }
            b1[hiddenIndex] -= rate * hiddenGrad[hiddenIndex]
        }
    }

    private fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
        val hidden = FloatArray(HIDDEN) { hiddenIndex ->
            var sum = b1[hiddenIndex]
            for (inputIndex in 0 until INPUTS) {
                sum += w1[hiddenIndex * INPUTS + inputIndex] * input[inputIndex]
            }
            tanh(sum.toDouble()).toFloat()
        }
        val logits = FloatArray(OUTPUTS) { outputIndex ->
            var sum = b2[outputIndex]
            for (hiddenIndex in 0 until HIDDEN) {
                sum += w2[outputIndex * HIDDEN + hiddenIndex] * hidden[hiddenIndex]
            }
            sum
        }
        val max = logits.maxOrNull() ?: 0f
        var total = 0f
        for (index in logits.indices) {
            logits[index] = exp((logits[index] - max).toDouble()).toFloat()
            total += logits[index]
        }
        for (index in logits.indices) logits[index] /= total.coerceAtLeast(1e-9f)
        return hidden to logits
    }

    /**
     * Reproducible synthetic bootstrap. Extra device signals are deliberately
     * weak correlations; obvious real-time states remain controlled by the
     * deterministic state arbiter in OrcaContextBrain.
     */
    private fun bootstrap() {
        val rng = Random(34012L)
        repeat(10_800) { n ->
            val label = OrcaIntent.entries[n % OUTPUTS]
            val x = FloatArray(INPUTS) { if (rng.nextFloat() < 0.045f) 1f else 0f }
            when (label) {
                OrcaIntent.TYPING -> {
                    x[0] = 1f; x[2] = 1f; x[33] = if (rng.nextBoolean()) 1f else 0f
                }
                OrcaIntent.THINKING -> {
                    x[1] = 1f; x[2] = 1f; x[0] = 0f
                }
                OrcaIntent.READING -> {
                    x[13] = 1f; x[0] = 0f; x[1] = 0f
                    if (rng.nextBoolean()) x[27] = 1f
                }
                OrcaIntent.BROWSING -> {
                    x[8] = 1f; x[26] = 1f
                    if (rng.nextBoolean()) x[3] = 1f
                    if (rng.nextBoolean()) x[27] = 1f
                    if (rng.nextBoolean()) x[28] = 1f
                }
                OrcaIntent.CODING -> {
                    x[9] = 1f; x[0] = 0f
                    if (rng.nextBoolean()) x[26] = 1f
                }
                OrcaIntent.MUSIC -> {
                    x[5] = 1f; x[6] = 1f
                    if (rng.nextBoolean()) x[29] = 1f else x[30] = 1f
                }
                OrcaIntent.VIDEO -> {
                    x[7] = 1f; x[12] = 1f; x[5] = 1f; x[23] = 1f
                }
                OrcaIntent.NOTIFICATION -> x[17] = 1f
                OrcaIntent.SUCCESS -> x[16] = 1f
                OrcaIntent.ERROR -> x[15] = 1f
                OrcaIntent.SLEEP -> {
                    x[18] = 1f
                    if (rng.nextBoolean()) x[19] = 1f
                    if (rng.nextBoolean()) x[22] = 1f
                    if (rng.nextBoolean()) x[24] = 1f
                }
                OrcaIntent.IDLE -> {
                    x.fill(0f)
                    x[18] = 1f
                    if (rng.nextBoolean()) x[25] = 1f
                }
            }
            train(x, label, 0.065f)
        }
    }

    /** Numeric weights only; no raw user content is serialized. */
    @Synchronized
    fun exportWeights(): String {
        val values = w1 + b1 + w2 + b2
        val bytes = ByteBuffer.allocate(4 * values.size).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(bytes::putFloat)
        return Base64.getEncoder().encodeToString(bytes.array())
    }

    @Synchronized
    fun importWeights(serialized: String): Boolean = try {
        val raw = Base64.getDecoder().decode(serialized)
        val expected = 4 * (w1.size + b1.size + w2.size + b2.size)
        if (raw.size != expected) {
            false
        } else {
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val decoded = FloatArray(raw.size / 4) { buffer.float }
            if (decoded.any { !it.isFinite() }) {
                false
            } else {
                var at = 0
                for (array in arrayOf(w1, b1, w2, b2)) {
                    for (index in array.indices) array[index] = decoded[at++]
                }
                true
            }
        }
    } catch (_: IllegalArgumentException) {
        false
    }
}

enum class OrcaIntent(val animation: String, val display: String) {
    TYPING("calico-working-typing.apng", "Typing"),
    THINKING("calico-thinking.apng", "Thinking"),
    READING("calico-mini-peek.apng", "Reading"),
    BROWSING("calico-working-juggling.apng", "Browsing"),
    CODING("calico-working-building.apng", "Coding"),
    MUSIC("calico-working-conducting.apng", "Music"),
    VIDEO("calico-idle.apng", "Watching"),
    NOTIFICATION("calico-notification.apng", "Notification"),
    SUCCESS("calico-happy.apng", "Happy"),
    ERROR("calico-error.apng", "Error"),
    SLEEP("calico-sleeping.apng", "Sleeping"),
    IDLE("calico-idle.apng", "Idle")
}
