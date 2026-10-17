package com.manalejandro.boleto.domain.ai

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Training data stored as a flat matrix to keep memory small. */
class Samples(
    val count: Int,
    val features: Int,
    val x: FloatArray,
    val y: FloatArray,
)

/**
 * Plain logistic regression trained with full-batch Adam and L2
 * regularisation. Small enough to train on-device in a couple of seconds.
 *
 * Features are standardised (z-score) with statistics computed on the
 * training set and stored inside the model: without standardisation the
 * optimiser gets stuck around the base rate of the game instead of learning
 * the (weak) patterns of the series.
 */
class LogisticModel(val numFeatures: Int) {

    val weights = FloatArray(numFeatures)
    var bias = 0f

    /** Per-feature statistics used to standardise inputs. */
    val featureMean = FloatArray(numFeatures)
    val featureScale = FloatArray(numFeatures) { 1f }

    /** Standardised value of feature [index]. */
    private fun standardised(x: FloatArray, index: Int): Float =
        (x[index] - featureMean[index]) * featureScale[index]

    fun score(x: FloatArray): Float {
        var z = bias
        for (index in 0 until numFeatures) z += weights[index] * standardised(x, index)
        return z
    }

    fun predict(x: FloatArray): Float = sigmoid(score(x))

    companion object {

        fun sigmoid(z: Float): Float = (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()

        fun train(samples: Samples, epochs: Int, learningRate: Float, l2: Float): LogisticModel {
            val model = LogisticModel(samples.features)
            if (samples.count == 0) return model

            val features = samples.features
            val n = samples.count
            val x = samples.x

            // --- feature standardisation -----------------------------------
            var index = 0
            for (sample in 0 until n) {
                for (j in 0 until features) model.featureMean[j] += x[index + j]
                index += features
            }
            for (j in 0 until features) model.featureMean[j] /= n

            val variance = FloatArray(features)
            index = 0
            for (sample in 0 until n) {
                for (j in 0 until features) {
                    val delta = x[index + j] - model.featureMean[j]
                    variance[j] += delta * delta
                }
                index += features
            }
            for (j in 0 until features) {
                val std = sqrt(variance[j] / n)
                model.featureScale[j] = if (std > 1e-6f) 1f / std else 1f
            }

            // --- Adam ------------------------------------------------------
            val gradientW = FloatArray(features)
            var gradientB = 0f
            val mW = FloatArray(features)
            val vW = FloatArray(features)
            var mB = 0f
            var vB = 0f
            val beta1 = 0.9f
            val beta2 = 0.999f
            val epsilon = 1e-8f
            val inverseN = 1f / n

            for (epoch in 1..epochs) {
                java.util.Arrays.fill(gradientW, 0f)
                gradientB = 0f
                index = 0
                for (sample in 0 until n) {
                    var z = model.bias
                    for (j in 0 until features) {
                        z += model.weights[j] * (x[index + j] - model.featureMean[j]) * model.featureScale[j]
                    }
                    val error = sigmoid(z) - samples.y[sample]
                    for (j in 0 until features) {
                        val value = (x[index + j] - model.featureMean[j]) * model.featureScale[j]
                        gradientW[j] += error * value
                    }
                    gradientB += error
                    index += features
                }
                for (j in 0 until features) {
                    gradientW[j] = gradientW[j] * inverseN + l2 * model.weights[j]
                }
                gradientB *= inverseN

                val correction1 = 1f - beta1.pow(epoch)
                val correction2 = 1f - beta2.pow(epoch)
                for (j in 0 until features) {
                    mW[j] = beta1 * mW[j] + (1f - beta1) * gradientW[j]
                    vW[j] = beta2 * vW[j] + (1f - beta2) * gradientW[j] * gradientW[j]
                    val mHat = mW[j] / correction1
                    val vHat = vW[j] / correction2
                    model.weights[j] -= learningRate * mHat / (sqrt(vHat) + epsilon)
                }
                mB = beta1 * mB + (1f - beta1) * gradientB
                vB = beta2 * vB + (1f - beta2) * gradientB * gradientB
                val mBHat = mB / correction1
                val vBHat = vB / correction2
                model.bias -= learningRate * mBHat / (sqrt(vBHat) + epsilon)
            }
            return model
        }
    }
}
