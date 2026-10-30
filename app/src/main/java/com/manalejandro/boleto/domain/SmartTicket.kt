package com.manalejandro.boleto.domain

/**
 * Rules of the "smart" (expected-value) strategy.
 *
 * Every combination has exactly the same odds of being drawn, so this
 * strategy does not try to predict anything: it tries to *share the prize
 * with fewer people*. Player behaviour is far from uniform — birthdays
 * concentrate picks on 1..31 (and months on 1..12) — so a winning combination
 * full of low numbers is historically split between many more winners.
 * Avoiding those habits does not change the odds of winning, but it raises
 * the expected payout per ticket: the only mathematically sound "edge" in a
 * fair lottery.
 *
 * The structural rules reject *implausible* tickets instead of chasing a
 * "typical" one: the sum window is deliberately asymmetric, because sums far
 * below the historical mean are exactly where birthday-heavy popular tickets
 * cluster (rejected at −1 sd), while moderately high sums are rare but
 * plausible draws that almost nobody plays (allowed up to +1.5 sd). Odd/even
 * balance, consecutive pairs and decades keep the combination looking like a
 * normal draw instead of an obvious pattern.
 */
object SmartTicket {

    /**
     * Relative weight of a number: the more popular the pick, the lower the
     * weight. Tiers reflect documented player habits (birthday calendars and
     * lucky low numbers), not any property of the draws themselves.
     */
    fun popularityWeight(number: Int): Double = when {
        number <= 12 -> 0.30 // months and the lowest days
        number <= 31 -> 0.55 // birthday days
        else -> 1.0
    }

    /** True when the combination looks like a typical historical draw. */
    fun acceptable(numbers: List<Int>, sumMean: Double, sumSd: Double): Boolean =
        violations(numbers, sumMean, sumSd) == 0

    /** Number of structural rules the combination breaks (0 = acceptable). */
    fun violations(numbers: List<Int>, sumMean: Double, sumSd: Double): Int {
        val count = numbers.size
        if (count == 0) return 0
        var violations = 0

        // 1. Plausible sum window, asymmetric on purpose: reject sums more
        //    than 1 sd *below* the mean (the birthday-cluster region, where
        //    prizes split between more winners) and more than 1.5 sd above
        //    it. Skipped when there are no sum statistics yet.
        if (sumSd > 0.0) {
            val deviation = numbers.sum() - sumMean
            if (deviation < -sumSd || deviation > 1.5 * sumSd) violations++
        }

        // 2. Roughly balanced odd/even mix (balanced ± 1).
        val odd = numbers.count { it % 2 == 1 }
        if (odd < count / 2 - 1 || odd > (count + 1) / 2 + 1) violations++

        // 3. At most one pair of consecutive numbers.
        val sorted = numbers.sorted()
        var consecutive = 0
        for (index in 1 until count) {
            if (sorted[index] == sorted[index - 1] + 1) consecutive++
        }
        if (consecutive > 1) violations++

        // 4. Not concentrated in a single decade (max half of the ticket).
        val perDecade = numbers.groupingBy { (it - 1) / 10 }.eachCount()
        if (perDecade.values.any { it > (count + 1) / 2 }) violations++

        return violations
    }
}
