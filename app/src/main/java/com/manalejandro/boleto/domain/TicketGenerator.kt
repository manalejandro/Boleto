package com.manalejandro.boleto.domain

import com.manalejandro.boleto.data.Boleto
import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.data.TicketLine
import com.manalejandro.boleto.domain.ai.AiModel
import com.manalejandro.boleto.domain.ai.FeatureExtractor
import kotlin.math.pow
import kotlin.random.Random

/**
 * Sampling context at a point in time: rolling statistics of every number
 * plus (optionally) the probabilities produced by the AI model.
 */
class GenerationContext(
    val game: LotteryGame,
    val extractor: FeatureExtractor,
    val starExtractor: FeatureExtractor,
    val mainProbabilities: DoubleArray?,
    val starProbabilities: DoubleArray?,
)

/**
 * Generates tickets with the available strategies. Sampling is always done
 * without replacement (Plackett–Luce over the strategy weights).
 */
object TicketGenerator {

    private const val AI_TEMPERATURE = 0.8
    private const val HOT_SMOOTHING = 0.35
    private const val COLD_SMOOTHING = 0.35
    private const val DELAY_CAP = 60
    private const val POOL_SIZE = 12

    fun context(game: LotteryGame, history: List<Draw>, date: String, model: AiModel?): GenerationContext {
        val extractor = FeatureExtractor(game.mainMax)
        val starExtractor = FeatureExtractor(maxOf(game.starMax, 1))
        history.forEach { draw ->
            extractor.advance(draw.numbers)
            if (game.starCount > 0) starExtractor.advance(draw.stars)
        }
        return GenerationContext(
            game = game,
            extractor = extractor,
            starExtractor = starExtractor,
            mainProbabilities = model?.mainProbabilities(history, date),
            starProbabilities = model?.starProbabilities(history, date),
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
    ): Boleto {
        val context = context(game, history, date, model)
        return generate(context, strategy, bets, date, random)
    }

    fun generate(
        context: GenerationContext,
        strategy: Strategy,
        bets: Int,
        date: String,
        random: Random,
    ): Boleto {
        val lines = (0 until bets).map { line(context, strategy, date, random) }
        return Boleto(context.game, lines, strategy, date)
    }

    fun line(context: GenerationContext, strategy: Strategy, date: String, random: Random): TicketLine {
        val game = context.game
        val numbers = when (strategy) {
            Strategy.BALANCED -> balancedLine(game.mainMax, game.mainCount, context.extractor, random)
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

    private fun mainWeights(context: GenerationContext, strategy: Strategy): DoubleArray {
        val max = context.game.mainMax
        val weights = DoubleArray(max + 1)
        when (strategy) {
            Strategy.RANDOM -> for (number in 1..max) weights[number] = 1.0
            Strategy.HOT -> for (number in 1..max) {
                weights[number] = HOT_SMOOTHING + context.extractor.windowFrequency(FeatureExtractor.WINDOW_50, number)
            }
            Strategy.COLD -> for (number in 1..max) {
                weights[number] = COLD_SMOOTHING + context.extractor.delay(number).coerceAtMost(DELAY_CAP)
            }
            Strategy.AI -> {
                val probabilities = context.mainProbabilities
                for (number in 1..max) {
                    weights[number] = if (probabilities != null) {
                        (probabilities[number] + 0.02).pow(AI_TEMPERATURE)
                    } else {
                        1.0
                    }
                }
            }
            Strategy.BALANCED -> for (number in 1..max) weights[number] = 1.0
        }
        return weights
    }

    private fun starWeights(context: GenerationContext, strategy: Strategy): DoubleArray {
        val max = context.game.starMax
        val weights = DoubleArray(max + 1)
        when (strategy) {
            Strategy.RANDOM -> for (number in 1..max) weights[number] = 1.0
            Strategy.HOT -> for (number in 1..max) {
                weights[number] = HOT_SMOOTHING + context.starExtractor.windowFrequency(FeatureExtractor.WINDOW_50, number)
            }
            Strategy.COLD -> for (number in 1..max) {
                weights[number] = COLD_SMOOTHING + context.starExtractor.delay(number).coerceAtMost(DELAY_CAP)
            }
            Strategy.AI -> {
                val probabilities = context.starProbabilities
                for (number in 1..max) {
                    weights[number] = if (probabilities != null) {
                        (probabilities[number] + 0.02).pow(AI_TEMPERATURE)
                    } else {
                        1.0
                    }
                }
            }
            Strategy.BALANCED -> for (number in 1..max) weights[number] = 1.0
        }
        return weights
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
