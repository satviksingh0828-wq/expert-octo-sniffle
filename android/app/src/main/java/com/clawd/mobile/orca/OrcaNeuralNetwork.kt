package com.clawd.mobile.orca

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Random
import kotlin.math.exp
import kotlin.math.tanh

/**
 * Small, real, fully offline feed-forward neural network.
 *
 * 26 normalized context signals -> 24 tanh neurons -> 12 softmax intent probabilities.
 * Bootstrap training uses synthetic activity *metadata* examples, not personal screen
 * content. Optional user corrections are trained with gradient descent and persisted.
 * This is an activity classifier, not a language/vision model.
 */
class OrcaNeuralNetwork {
    companion object {
        const val INPUTS = 26
        private const val HIDDEN = 24
        const val OUTPUTS = 12
    }

    data class Prediction(val intent: OrcaIntent, val confidence: Float, val probabilities: FloatArray)

    private val w1 = FloatArray(HIDDEN * INPUTS)
    private val b1 = FloatArray(HIDDEN)
    private val w2 = FloatArray(OUTPUTS * HIDDEN)
    private val b2 = FloatArray(OUTPUTS)

    init {
        val rng = Random(20261115L)
        for (i in w1.indices) w1[i] = (rng.nextGaussian() * 0.16).toFloat()
        for (i in w2.indices) w2[i] = (rng.nextGaussian() * 0.16).toFloat()
        bootstrap()
    }

    /** Return a full probability distribution, not a string-keyword lookup. */
    @Synchronized
    fun predict(input: FloatArray): Prediction {
        require(input.size == INPUTS)
        val (_, probabilities) = forward(input)
        val best = probabilities.indices.maxByOrNull { probabilities[it] } ?: 11
        return Prediction(OrcaIntent.entries[best], probabilities[best], probabilities)
    }

    /** One SGD backprop step; used only with an explicit correction by the user. */
    @Synchronized
    fun train(input: FloatArray, desired: OrcaIntent, rate: Float = 0.035f) {
        require(input.size == INPUTS)
        val (hidden, probabilities) = forward(input)
        val outGrad = FloatArray(OUTPUTS) { k ->
            probabilities[k] - if (k == desired.ordinal) 1f else 0f
        }
        val hiddenGrad = FloatArray(HIDDEN) { j ->
            var gradient = 0f
            for (k in 0 until OUTPUTS) gradient += outGrad[k] * w2[k * HIDDEN + j]
            gradient * (1f - hidden[j] * hidden[j])
        }
        for (k in 0 until OUTPUTS) {
            val base = k * HIDDEN
            for (j in 0 until HIDDEN) w2[base + j] -= rate * outGrad[k] * hidden[j]
            b2[k] -= rate * outGrad[k]
        }
        for (j in 0 until HIDDEN) {
            val base = j * INPUTS
            for (i in 0 until INPUTS) w1[base + i] -= rate * hiddenGrad[j] * input[i]
            b1[j] -= rate * hiddenGrad[j]
        }
    }

    private fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
        val hidden = FloatArray(HIDDEN) { j ->
            var sum = b1[j]
            for (i in 0 until INPUTS) sum += w1[j * INPUTS + i] * input[i]
            tanh(sum.toDouble()).toFloat()
        }
        val logits = FloatArray(OUTPUTS) { k ->
            var sum = b2[k]
            for (j in 0 until HIDDEN) sum += w2[k * HIDDEN + j] * hidden[j]
            sum
        }
        val max = logits.maxOrNull() ?: 0f
        var total = 0f
        for (k in logits.indices) {
            logits[k] = exp((logits[k] - max).toDouble()).toFloat()
            total += logits[k]
        }
        for (k in logits.indices) logits[k] /= total.coerceAtLeast(1e-9f)
        return hidden to logits
    }

    /**
     * Balanced, reproducible startup training. The neural model learns correlations
     * between activity signals; hard safety/timeout priorities remain outside it.
     */
    private fun bootstrap() {
        val rng = Random(1007L)
        repeat(7200) { n ->
            val label = OrcaIntent.entries[n % OUTPUTS]
            val x = FloatArray(INPUTS) { if (rng.nextFloat() < 0.075f) 1f else 0f }
            // Index definitions are documented in OrcaContextBrain.featureVector().
            when (label) {
                OrcaIntent.TYPING -> { x[0] = 1f; x[2] = 1f }
                OrcaIntent.THINKING -> { x[1] = 1f; x[2] = 1f; x[0] = 0f }
                OrcaIntent.READING -> { x[13] = 1f; x[0] = 0f; x[1] = 0f }
                OrcaIntent.BROWSING -> { x[8] = 1f; x[3] = 1f; x[0] = 0f }
                OrcaIntent.CODING -> { x[9] = 1f; x[0] = 0f; x[1] = 0f }
                OrcaIntent.MUSIC -> { x[5] = 1f; x[6] = 1f }
                OrcaIntent.VIDEO -> { x[7] = 1f; x[12] = 1f }
                OrcaIntent.NOTIFICATION -> x[17] = 1f
                OrcaIntent.SUCCESS -> x[16] = 1f
                OrcaIntent.ERROR -> x[15] = 1f
                OrcaIntent.SLEEP -> { x[19] = 1f; x[18] = 1f; if (rng.nextBoolean()) x[21] = 1f; if (rng.nextBoolean()) x[22] = 1f; if (rng.nextBoolean()) x[24] = 1f }
                OrcaIntent.IDLE -> { x.fill(0f); if (rng.nextBoolean()) x[18] = 1f; if (rng.nextBoolean()) x[20] = 1f; if (rng.nextBoolean()) x[25] = 1f }
            }
            train(x, label, 0.07f)
        }
    }

    /** Binary weights only: never stores, serializes or trains on raw screen text. */
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
        if (raw.size != expected) false else {
            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val decoded = FloatArray(raw.size / 4) { buffer.float }
            if (decoded.any { !it.isFinite() }) false else {
                var at = 0
                for (array in arrayOf(w1, b1, w2, b2))
                    for (i in array.indices) array[i] = decoded[at++]
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
