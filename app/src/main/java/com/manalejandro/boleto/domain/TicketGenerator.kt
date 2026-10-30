package com.manalejandro.boleto.domain

import com.manalejandro.boleto.data.Boleto
import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.data.TicketLine
import com.manalejandro.boleto.domain.ai.AiModel
import com.manalejandro.boleto.domain.ai.FeatureExtractor
import com.manalejandro.boleto.domain.ai.MarkovModel
import kotlin.math.pow
import kotlin.random.Random

/**
 * Sampling context at a point in time: rolling statistics of every number
 * plus (optionally) the probabilities produced by the AI model and the
 * conditional scores of the Markov co-occurrence model.
 */
class GenerationContext(
    val game: LotteryGame,
    val extractor: FeatureExtractor,
    val starExtractor: FeatureExtractor,
    val mainProbabilities: DoubleArray?,
    val starProbabilities: DoubleArray?,
    /** Markov scores for the next draw (null: not enough history yet). */
    val mainMarkov: DoubleArray? = null,
    val starMarkov: DoubleArray? = null,
)

/**
 * Generates tickets with the available strategies. Sampling is always done
 * without replacement (Plackett–Luce over the strategy weights), except for
 * the two strategies that build the line themselves ([Strategy.BALANCED] and
 * [Strategy.SMART]).
 */
object TicketGenerator {

    private const val AI_TEMPERATURE = 0.8
    private const val HOT_SMOOTHING = 0.35
    private const val COLD_SMOOTHING = 0.35
    private const val DELAY_CAP = 60
    private const val POOL_SIZE = 12

    /** Concentration of the symmetric Dirichlet prior of the Bayesian strategy. */
    private const val BAYES_PRIOR = 1.0

    /** Weights of the hybrid ensemble, renormalised over the available parts. */
    private const val HYBRID_AI_WEIGHT = 0.5
    private const val HYBRID_BAYES_WEIGHT = 0.3
    private const val HYBRID_MARKOV_WEIGHT = 0.2

    /** Rejection-sampling attempts of the smart strategy before best effort. */
    private const val SMART_ATTEMPTS = 40

    fun context(game: LotteryGame, history: List<Draw>, date: String, model: AiModel?): GenerationContext {
        val extractor = FeatureExtractor(game.mainMax)
        val starExtractor = FeatureExtractor(maxOf(game.starMax, 1))
        val markov = MarkovModel(game.mainMax)
        val starMarkov = if (game.starCount > 0) MarkovModel(game.starMax) else null
        history.forEach { draw ->
            extractor.advance(draw.numbers)
            markov.advance(draw.numbers)
            if (game.starCount > 0) {
                starExtractor.advance(draw.stars)
                starMarkov?.advance(draw.stars)
            }
        }
        return GenerationContext(
            game = game,
            extractor = extractor,
            starExtractor = starExtractor,
            mainProbabilities = model?.mainProbabilities(history, date),
            starProbabilities = model?.starProbabilities(history, date),
            mainMarkov = markov.scores(),
            starMarkov = starMarkov?.scores(),
        )
    }

    fun generate(
        game: LotteryGame,
        history: List<Draw>,
        strategy: Strategy,
        bets: Int,
        date: String,
        model: AiModel? = null,
        random: Random = Random.Default,
        withJoker: Boolean = false,
        drawsCount: Int = 1,
        now: Long = System.currentTimeMillis(),
    ): Boleto {
        val context = context(game, history, date, model)
        return generate(context, strategy, bets, date, random, withJoker, drawsCount, now)
    }

    fun generate(
        context: GenerationContext,
        strategy: Strategy,
        bets: Int,
        date: String,
        random: Random,
        withJoker: Boolean = false,
        drawsCount: Int = 1,
        now: Long = System.currentTimeMillis(),
    ): Boleto {
        val lines = (0 until bets).map { line(context, strategy, date, random) }
        val joker = if (withJoker && context.game.hasJoker) randomJoker(random) else null
        val drawDates = DrawSchedule.nextDraws(context.game, now, drawsCount.coerceAtLeast(1))
        return Boleto(context.game, lines, strategy, date, joker, drawDates)
    }

    /** The Joker code is always assigned at random by the system, 7 digits. */
    fun randomJoker(random: Random): String = random.nextInt(10_000_000).toString().padStart(7, '0')

    fun line(context: GenerationContext, strategy: Strategy, date: String, random: Random): TicketLine {
        val game = context.game
        val numbers = when (strategy) {
            Strategy.BALANCED -> balancedLine(game.mainMax, game.mainCount, context.extractor, random)
            Strategy.SMART -> smartLine(game.mainCount, mainWeights(context, strategy), context.extractor, random)
            else -> sampleDistinct(mainWeights(context, strategy), game.mainCount, random)
        }
        val stars = if (game.starCount > 0) {
            when (strategy) {
                Strategy.BALANCED -> balancedLine(game.starMax, game.starCount, context.starExtractor, random)
                else -> sampleDistinct(starWeights(context, strategy), game.starCount, random)
            }
        } else {
            emptyList()
        }
        val refund = if (game.hasRefund) random.nextInt(10) else null
        return TicketLine(numbers, stars, refund)
    }

    private fun mainWeights(context: GenerationContext, strategy: Strategy): DoubleArray =
        weightsFor(
            strategy = strategy,
            max = context.game.mainMax,
            extractor = context.extractor,
            probabilities = context.mainProbabilities,
            markov = context.mainMarkov,
        )

    private fun starWeights(context: GenerationContext, strategy: Strategy): DoubleArray =
        weightsFor(
            strategy = strategy,
            max = context.game.starMax,
            extractor = context.starExtractor,
            probabilities = context.starProbabilities,
            markov = context.starMarkov,
        )

    /** Sampling weights of every number (index 0 unused) for one strategy. */
    private fun weightsFor(
        strategy: Strategy,
        max: Int,
        extractor: FeatureExtractor,
        probabilities: DoubleArray?,
        markov: DoubleArray?,
    ): DoubleArray {
        val weights = DoubleArray(max + 1)
        when (strategy) {
            Strategy.RANDOM -> for (number in 1..max) weights[number] = 1.0
            Strategy.HOT -> for (number in 1..max) {
                weights[number] = HOT_SMOOTHING + extractor.windowFrequency(FeatureExtractor.WINDOW_50, number)
            }
            Strategy.COLD -> for (number in 1..max) {
                weights[number] = COLD_SMOOTHING + extractor.delay(number).coerceAtMost(DELAY_CAP)
            }
            // Bayesian posterior mean: recency-weighted likelihood (the EWMA
            // counts) plus a symmetric Dirichlet prior, so every number keeps
            // a strictly positive probability.
            Strategy.BAYES -> for (number in 1..max) {
                weights[number] = BAYES_PRIOR + extractor.ewma(number)
            }
            Strategy.MARKOV -> for (number in 1..max) {
                weights[number] = markov?.get(number) ?: 1.0
            }
            Strategy.AI -> for (number in 1..max) {
                weights[number] = if (probabilities != null) {
                    (probabilities[number] + 0.02).pow(AI_TEMPERATURE)
                } else {
                    1.0
                }
            }
            Strategy.HYBRID -> blend(weights, max, extractor, probabilities, markov)
            Strategy.SMART -> for (number in 1..max) {
                weights[number] = SmartTicket.popularityWeight(number)
            }
            // Balanced builds the line itself (hot + cold pools, see below).
            Strategy.BALANCED -> for (number in 1..max) weights[number] = 1.0
        }
        return weights
    }

    /**
     * Hybrid ensemble: every available estimator is normalised to a
     * probability distribution and mixed with fixed weights (AI 50 %, Bayes
     * 30 %, Markov 20 %). Missing estimators (untrained model, short history)
     * are dropped and the remaining weights renormalised, so the strategy
     * degrades gracefully to pure statistics.
     */
    private fun blend(
        weights: DoubleArray,
        max: Int,
        extractor: FeatureExtractor,
        probabilities: DoubleArray?,
        markov: DoubleArray?,
    ) {
        val components = ArrayList<Pair<Double, DoubleArray>>(3)
        if (probabilities != null) {
            components += HYBRID_AI_WEIGHT to normalize(probabilities, max)
        }
        val bayes = DoubleArray(max + 1) { number ->
            if (number == 0) 0.0 else BAYES_PRIOR + extractor.ewma(number)
        }
        components += HYBRID_BAYES_WEIGHT to normalize(bayes, max)
        if (markov != null) {
            components += HYBRID_MARKOV_WEIGHT to normalize(markov, max)
        }
        val totalWeight = components.sumOf { it.first }
        for (number in 1..max) {
            var mixture = 0.0
            for ((weight, distribution) in components) {
                mixture += weight / totalWeight * distribution[number]
            }
            weights[number] = mixture
        }
    }

    /** Scales the scores of 1..[max] to a probability distribution. */
    private fun normalize(source: DoubleArray, max: Int): DoubleArray {
        var total = 0.0
        for (number in 1..max) total += source[number]
        if (total <= 0.0) {
            return DoubleArray(max + 1) { number -> if (number == 0) 0.0 else 1.0 / max }
        }
        return DoubleArray(max + 1) { number -> if (number == 0) 0.0 else source[number] / total }
    }

    /** Plackett–Luce sampling without replacement. */
    private fun sampleDistinct(weights: DoubleArray, count: Int, random: Random): List<Int> {
        val max = weights.size - 1
        val available = weights.copyOf()
        val picked = ArrayList<Int>(count)
        repeat(count.coerceAtMost(max)) {
            var total = 0.0
            for (number in 1..max) total += available[number]
            val chosen = if (total <= 0.0) {
                (1..max).first { it !in picked }
            } else {
                var threshold = random.nextDouble() * total
                var result = -1
                for (number in 1..max) {
                    threshold -= available[number]
                    if (threshold <= 0.0) {
                        result = number
                        break
                    }
                }
                if (result < 0) result = (1..max).last { available[it] > 0.0 }
                result
            }
            picked += chosen
            available[chosen] = 0.0
        }
        return picked.sorted()
    }

    /** A mix of recent-hot and overdue numbers, filled with uniform picks. */
    private fun balancedLine(
        max: Int,
        count: Int,
        extractor: FeatureExtractor,
        random: Random,
    ): List<Int> {
        val hotPool = (1..max)
            .sortedByDescending { extractor.windowFrequency(FeatureExtractor.WINDOW_50, it) }
            .take(POOL_SIZE)
        val coldPool = (1..max)
            .sortedByDescending { extractor.delay(it) }
            .take(POOL_SIZE)
        val used = HashSet<Int>(count * 2)
        val picked = ArrayList<Int>(count)
        val hotCount = (count / 3).coerceAtLeast(1)
        val coldCount = (count / 3).coerceAtLeast(1)
        pickFrom(hotPool, hotCount, used, picked, random)
        pickFrom(coldPool, coldCount, used, picked, random)
        val remaining = (1..max).filterNot { it in used }.shuffled(random)
        for (number in remaining) {
            if (picked.size >= count) break
            picked += number
        }
        return picked.sorted()
    }

    /**
     * Expected-value line: samples with weights that penalise the numbers
     * everybody plays and rejects candidates outside the structure of a
     * typical historical draw (sum window, odd/even balance, consecutive
     * pairs, decades). Rejecting candidates does not change the odds of the
     * accepted ones — every combination is still equally likely to win — it
     * only tilts the ticket towards combinations that, historically, are
     * shared between fewer winners. Falls back to the candidate with the
     * fewest violations if none passes within [SMART_ATTEMPTS].
     */
    private fun smartLine(
        count: Int,
        weights: DoubleArray,
        extractor: FeatureExtractor,
        random: Random,
    ): List<Int> {
        val sumMean = extractor.drawSumMean
        val sumSd = extractor.drawSumSd
        var best: List<Int>? = null
        var bestViolations = Int.MAX_VALUE
        for (attempt in 0 until SMART_ATTEMPTS) {
            val candidate = sampleDistinct(weights, count, random)
            val violations = SmartTicket.violations(candidate, sumMean, sumSd)
            if (violations == 0) return candidate
            if (violations < bestViolations) {
                bestViolations = violations
                best = candidate
            }
        }
        return best ?: sampleDistinct(weights, count, random)
    }

    private fun pickFrom(
        pool: List<Int>,
        count: Int,
        used: MutableSet<Int>,
        picked: MutableList<Int>,
        random: Random,
    ) {
        var added = 0
        for (number in pool.shuffled(random)) {
            if (added >= count) break
            if (used.add(number)) {
                picked += number
                added++
            }
        }
    }
}
