package com.manalejandro.boleto.domain

import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.domain.ai.AiTrainer
import com.manalejandro.boleto.domain.ai.FeatureExtractor
import kotlin.random.Random

/** Aggregated result of evaluating one strategy over the backtest window. */
data class StrategyResult(
    val strategy: Strategy,
    val avgHits: Double,
    val bestHits: Int,
    val prizeDraws: Int,
    val totalDraws: Int,
    val categoryCounts: IntArray,
) {
    val prizeRate: Double get() = if (totalDraws == 0) 0.0 else prizeDraws.toDouble() / totalDraws
}

/**
 * Walk-forward (hold-out) evaluation of every strategy over the last
 * [window] draws. At each draw only the information available *before* it is
 * used, and the AI model is trained exclusively on draws prior to the
 * evaluated window — the same protocol used to report honest metrics.
 */
object Backtester {

    const val MIN_DRAWS = 400
    const val MIN_WINDOW = 20

    fun run(
        game: LotteryGame,
        draws: List<Draw>,
        window: Int,
        betsPerDraw: Int,
        onProgress: (Float) -> Unit = {},
    ): List<StrategyResult> {
        val total = draws.size
        if (total < MIN_DRAWS) return emptyList()
        val evalWindow = minOf(window, total - 250)
        if (evalWindow < MIN_WINDOW) return emptyList()
        val trainEnd = total - evalWindow

        val model = AiTrainer.trainOn(game, draws, trainEnd)

        val extractor = FeatureExtractor(game.mainMax)
        val starExtractor = FeatureExtractor(maxOf(game.starMax, 1))
        for (index in 0 until trainEnd) {
            extractor.advance(draws[index].numbers)
            if (game.starCount > 0) starExtractor.advance(draws[index].stars)
        }

        class Accumulator {
            var hits = 0
            var best = 0
            var prize = 0
            val categories = IntArray(PrizeRules.categoryCount(game))
        }

        val strategies = Strategy.entries
        val accumulators = strategies.associateWith { Accumulator() }

        for (index in trainEnd until total) {
            val draw = draws[index]
            val date = draw.date
            val context = GenerationContext(
                game = game,
                extractor = extractor,
                starExtractor = starExtractor,
                mainProbabilities = model.mainProbabilities(extractor, date),
                starProbabilities = if (game.starCount > 0) model.starProbabilities(starExtractor, date) else null,
            )
            strategies.forEach { strategy ->
                val accumulator = accumulators.getValue(strategy)
                repeat(betsPerDraw) { bet ->
                    val random = Random(index * 1_000L + bet * 37L)
                    val line = TicketGenerator.line(context, strategy, date, random)
                    val hits = line.numbers.count { it in draw.numbers }
                    accumulator.hits += hits
                    if (hits > accumulator.best) accumulator.best = hits
                    val rank = PrizeRules.categoryRank(game, line, draw)
                    if (rank != null) {
                        accumulator.prize++
                        accumulator.categories[rank]++
                    }
                }
            }
            extractor.advance(draw.numbers)
            if (game.starCount > 0) starExtractor.advance(draw.stars)
            onProgress((index - trainEnd + 1).toFloat() / evalWindow)
        }

        val bets = (evalWindow * betsPerDraw).coerceAtLeast(1)
        return strategies.map { strategy ->
            val accumulator = accumulators.getValue(strategy)
            StrategyResult(
                strategy = strategy,
                avgHits = accumulator.hits.toDouble() / bets,
                bestHits = accumulator.best,
                prizeDraws = accumulator.prize,
                totalDraws = evalWindow,
                categoryCounts = accumulator.categories,
            )
        }
    }
}
