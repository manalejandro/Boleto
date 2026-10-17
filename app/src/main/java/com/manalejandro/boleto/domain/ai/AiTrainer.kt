package com.manalejandro.boleto.domain.ai

import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame

/**
 * Trains [AiModel] instances on-device. The whole history is used, except for
 * a hold-out window at the end that is only used to measure honest metrics.
 */
object AiTrainer {

    const val MIN_DRAWS = 300
    const val MIN_HISTORY = 80
    const val MAX_TRAIN_DRAWS = 2000
    const val EPOCHS = 150
    const val LR = 0.05f
    const val L2 = 1e-4f
    const val DEFAULT_HOLDOUT = 150

    /** Trains a model with an internal hold-out used for metrics. */
    fun train(game: LotteryGame, draws: List<Draw>, holdout: Int = DEFAULT_HOLDOUT): AiModel? {
        if (draws.size < MIN_DRAWS) return null
        val holdoutSize = minOf(holdout, draws.size / 4).coerceAtLeast(30)
        val trainEnd = draws.size - holdoutSize
        val mainModel = trainOn(game, draws, trainEnd)
        val (precision, baseline) = evaluate(game, draws, trainEnd, mainModel)
        return AiModel(
            game = game,
            main = mainModel.main,
            stars = mainModel.stars,
            trainedDraws = trainEnd,
            trainedThrough = draws[trainEnd - 1].date,
            holdoutDraws = holdoutSize,
            holdoutPrecision = precision,
            holdoutBaseline = baseline,
            trainedAt = System.currentTimeMillis(),
        )
    }

    /** Trains on draws `[0, trainEnd)` with no internal hold-out (used by the backtester). */
    fun trainOn(game: LotteryGame, draws: List<Draw>, trainEnd: Int): AiModel {
        val mainSamples = buildSamples(game.mainMax, draws, trainEnd) { it.numbers }
        val mainModel = LogisticModel.train(mainSamples, EPOCHS, LR, L2)
        val starModel = if (game.starCount > 0) {
            val starSamples = buildSamples(game.starMax, draws, trainEnd) { it.stars }
            LogisticModel.train(starSamples, EPOCHS, LR, L2)
        } else null
        return AiModel(
            game = game,
            main = mainModel,
            stars = starModel,
            trainedDraws = trainEnd,
            trainedThrough = draws.getOrNull(trainEnd - 1)?.date.orEmpty(),
            holdoutDraws = 0,
            holdoutPrecision = 0.0,
            holdoutBaseline = game.mainCount.toDouble() / game.mainMax,
            trainedAt = System.currentTimeMillis(),
        )
    }

    private fun buildSamples(
        max: Int,
        draws: List<Draw>,
        trainEnd: Int,
        selector: (Draw) -> List<Int>,
    ): Samples {
        val start = maxOf(MIN_HISTORY, trainEnd - MAX_TRAIN_DRAWS)
        val extractor = FeatureExtractor(max)
        for (index in 0 until start) extractor.advance(selector(draws[index]))

        val count = (trainEnd - start).coerceAtLeast(0) * max
        val x = FloatArray(count * FeatureExtractor.FEATURE_COUNT)
        val y = FloatArray(count)
        var sample = 0
        for (index in start until trainEnd) {
            val date = draws[index].date
            val drawn = selector(draws[index]).toHashSet()
            for (number in 1..max) {
                val features = extractor.features(number, date)
                System.arraycopy(features, 0, x, sample * FeatureExtractor.FEATURE_COUNT, features.size)
                y[sample] = if (number in drawn) 1f else 0f
                sample++
            }
            extractor.advance(selector(draws[index]))
        }
        return Samples(sample, FeatureExtractor.FEATURE_COUNT, x, y)
    }

    /** Average hits per bet on the hold-out window, plus the pure-chance baseline. */
    private fun evaluate(
        game: LotteryGame,
        draws: List<Draw>,
        trainEnd: Int,
        model: AiModel,
    ): Pair<Double, Double> {
        val extractor = FeatureExtractor(game.mainMax)
        for (index in 0 until trainEnd) extractor.advance(draws[index].numbers)

        var hits = 0.0
        var evaluated = 0
        for (index in trainEnd until draws.size) {
            val date = draws[index].date
            val best = (1..game.mainMax)
                .map { number -> number to model.main.predict(extractor.features(number, date)) }
                .sortedByDescending { it.second }
                .take(game.mainCount)
                .map { it.first }
            hits += best.count { it in draws[index].numbers }
            evaluated++
            extractor.advance(draws[index].numbers)
        }
        val precision = if (evaluated == 0) 0.0 else hits / evaluated / game.mainCount
        return precision to game.mainCount.toDouble() / game.mainMax
    }
}
