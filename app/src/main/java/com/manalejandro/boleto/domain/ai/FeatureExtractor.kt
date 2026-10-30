package com.manalejandro.boleto.domain.ai

import com.manalejandro.boleto.core.IsoDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Streaming feature builder for the on-device model.
 *
 * For every candidate number the model sees:
 *  0..4  appearance rate inside rolling windows of 10/25/50/100/250 draws
 *  5     normalised delay (draws since the number last appeared)
 *  6     global appearance rate over the whole history
 *  7..8  day-of-week seasonality (sin/cos)
 *  9..10 month-of-year seasonality (sin/cos)
 *
 * It also keeps two lightweight statistics used by the statistical
 * strategies (not by the model features, so cached models stay valid):
 *  - an exponentially decayed appearance count per number ([ewma]), the
 *    recency-weighted likelihood of the Bayesian strategy;
 *  - the mean and standard deviation of the sum of the drawn numbers
 *    (Welford), used by the structural rules of the smart strategy.
 *
 * The extractor is advanced draw by draw, so features for draw `t` are always
 * computed with information from draws `< t` only: no leakage, which makes the
 * walk-forward backtest honest.
 */
class FeatureExtractor(private val max: Int) {

    var size = 0
        private set

    private val counts = IntArray(max + 1)
    private val lastSeen = IntArray(max + 1) { -1 }
    private val windows = Array(WINDOWS.size) { IntArray(max + 1) }
    private val queues = Array(WINDOWS.size) { ArrayDeque<List<Int>>() }
    private val ewmaCounts = DoubleArray(max + 1)
    private var sumDraws = 0
    private var sumMeanValue = 0.0
    private var sumM2 = 0.0

    fun advance(numbers: List<Int>) {
        for (number in numbers) {
            if (number in 1..max) counts[number]++
        }
        for (index in WINDOWS.indices) {
            val queue = queues[index]
            queue.addLast(numbers)
            for (number in numbers) if (number in 1..max) windows[index][number]++
            while (queue.size > WINDOWS[index]) {
                val removed = queue.removeFirst()
                for (number in removed) if (number in 1..max) windows[index][number]--
            }
        }
        for (number in numbers) if (number in 1..max) lastSeen[number] = size
        for (number in 1..max) ewmaCounts[number] *= EWMA_DECAY
        for (number in numbers) if (number in 1..max) ewmaCounts[number] += 1.0

        // Welford's online variance over the sum of every draw.
        val sum = numbers.sum().toDouble()
        sumDraws++
        val delta = sum - sumMeanValue
        sumMeanValue += delta / sumDraws
        sumM2 += delta * (sum - sumMeanValue)

        size++
    }

    fun features(number: Int, isoDate: String): FloatArray {
        val features = FloatArray(FEATURE_COUNT)
        for (index in WINDOWS.indices) {
            features[index] = windows[index][number].toFloat() / WINDOWS[index]
        }
        features[5] = delay(number).coerceAtMost(100).toFloat() / 100f
        features[6] = if (size == 0) 0f else counts[number].toFloat() / size
        val dayOfWeek = IsoDate.dayOfWeek(isoDate).toDouble()
        val month = IsoDate.month(isoDate).toDouble()
        features[7] = sin(2.0 * Math.PI * dayOfWeek / 7.0).toFloat()
        features[8] = cos(2.0 * Math.PI * dayOfWeek / 7.0).toFloat()
        features[9] = sin(2.0 * Math.PI * month / 12.0).toFloat()
        features[10] = cos(2.0 * Math.PI * month / 12.0).toFloat()
        return features
    }

    fun delay(number: Int): Int =
        if (lastSeen[number] < 0) size else size - 1 - lastSeen[number]

    fun frequency(number: Int): Int = counts[number]

    /** Appearance count inside the window at [index] (see [WINDOWS]). */
    fun windowFrequency(index: Int, number: Int): Int = windows[index][number]

    /**
     * Recency-weighted appearance count: each past appearance contributes
     * [EWMA_DECAY]^age, so recent draws dominate (half-life ≈ 69 draws).
     */
    fun ewma(number: Int): Double = ewmaCounts[number]

    /** Mean of the sum of the drawn numbers across the observed draws. */
    val drawSumMean: Double get() = sumMeanValue

    /** Sample standard deviation of the sum of the drawn numbers. */
    val drawSumSd: Double
        get() = if (sumDraws > 1) sqrt(sumM2 / (sumDraws - 1)) else 0.0

    companion object {
        val WINDOWS = intArrayOf(10, 25, 50, 100, 250)
        const val FEATURE_COUNT = 11
        const val WINDOW_50 = 2

        /** Per-draw decay of the recency-weighted counts (half-life ≈ 69 draws). */
        const val EWMA_DECAY = 0.99
    }
}
