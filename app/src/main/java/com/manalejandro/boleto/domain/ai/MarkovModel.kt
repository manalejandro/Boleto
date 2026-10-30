package com.manalejandro.boleto.domain.ai

import kotlin.math.exp
import kotlin.math.ln

/**
 * First-order co-occurrence model over consecutive draws.
 *
 * For every ordered pair of consecutive draws it counts how often the number
 * `n` appeared in the second one when the number `m` was in the first, and
 * turns those counts into conditional probabilities with Laplace smoothing:
 *
 *     P(n | m) = (co[m][n] + α) / (Σₖ co[m][k] + α · max)
 *
 * The score of a candidate for the next draw is the geometric mean of
 * P(n | m) over the numbers `m` of the last observed draw, which keeps every
 * score on the same scale no matter how many numbers conditioned it.
 *
 * Like [FeatureExtractor], the model is advanced draw by draw, so the scores
 * for draw `t` only use information from draws `< t`: no leakage, and the
 * walk-forward backtest stays honest.
 */
class MarkovModel(private val max: Int, private val alpha: Double = 0.5) {

    private val co = Array(max + 1) { DoubleArray(max + 1) }
    private val rowTotals = DoubleArray(max + 1)
    private var last: List<Int> = emptyList()

    /** Consecutive draw pairs observed so far. */
    var transitions = 0
        private set

    fun advance(numbers: List<Int>) {
        val previous = last
        if (previous.isNotEmpty()) {
            for (m in previous) {
                val row = co[m]
                for (n in numbers) {
                    if (n in 1..max) {
                        row[n] += 1.0
                        rowTotals[m] += 1.0
                    }
                }
            }
            transitions++
        }
        last = numbers.filter { it in 1..max }
    }

    /**
     * Conditional score of every number for the next draw (index 0 unused), or
     * null when there is not enough history to condition on.
     */
    fun scores(): DoubleArray? {
        if (last.isEmpty() || transitions < MIN_TRANSITIONS) return null
        val scores = DoubleArray(max + 1)
        for (n in 1..max) {
            var logSum = 0.0
            for (m in last) {
                val p = (co[m][n] + alpha) / (rowTotals[m] + alpha * max)
                logSum += ln(p)
            }
            scores[n] = exp(logSum / last.size)
        }
        return scores
    }

    companion object {
        /** Minimum consecutive pairs before the model is trusted. */
        const val MIN_TRANSITIONS = 30
    }
}
