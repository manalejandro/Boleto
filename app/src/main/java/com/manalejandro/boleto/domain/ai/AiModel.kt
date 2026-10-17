package com.manalejandro.boleto.domain.ai

import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import org.json.JSONArray
import org.json.JSONObject

/**
 * A trained, ready-to-use model plus the honest metrics measured on the
 * hold-out window (draws the model never saw during training).
 */
class AiModel(
    val game: LotteryGame,
    val main: LogisticModel,
    val stars: LogisticModel?,
    val trainedDraws: Int,
    val trainedThrough: String,
    val holdoutDraws: Int,
    /** Average hits per bet divided by the numbers played, on the hold-out. */
    val holdoutPrecision: Double,
    /** Precision expected from pure chance, for comparison. */
    val holdoutBaseline: Double,
    val trainedAt: Long,
) {

    /** Probability for every main number (index 0 unused) for the next draw. */
    fun mainProbabilities(history: List<Draw>, date: String): DoubleArray {
        val extractor = FeatureExtractor(game.mainMax)
        history.forEach { extractor.advance(it.numbers) }
        return mainProbabilities(extractor, date)
    }

    /** Same as above but reusing an already advanced extractor (backtesting). */
    fun mainProbabilities(extractor: FeatureExtractor, date: String): DoubleArray =
        DoubleArray(game.mainMax + 1) { number ->
            if (number == 0) 0.0 else main.predict(extractor.features(number, date)).toDouble()
        }

    /** Probability for every star number (index 0 unused) for the next draw. */
    fun starProbabilities(history: List<Draw>, date: String): DoubleArray? {
        if (game.starCount == 0) return null
        val extractor = FeatureExtractor(game.starMax)
        history.forEach { extractor.advance(it.stars) }
        return starProbabilities(extractor, date)
    }

    /** Same as above but reusing an already advanced extractor (backtesting). */
    fun starProbabilities(extractor: FeatureExtractor, date: String): DoubleArray? {
        val starModel = stars ?: return null
        if (game.starCount == 0) return null
        return DoubleArray(game.starMax + 1) { number ->
            if (number == 0) 0.0 else starModel.predict(extractor.features(number, date)).toDouble()
        }
    }

    fun isFresh(draws: List<Draw>): Boolean =
        draws.isNotEmpty() && draws.last().date == trainedThrough

    fun toJson(): String {
        fun modelJson(model: LogisticModel) = JSONObject()
            .put("w", JSONArray(model.weights.map { it.toDouble() }))
            .put("b", model.bias.toDouble())
            .put("mean", JSONArray(model.featureMean.map { it.toDouble() }))
            .put("scale", JSONArray(model.featureScale.map { it.toDouble() }))

        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("game", game.key)
            .put("main", modelJson(main))
            .put("stars", stars?.let { modelJson(it) } ?: JSONObject.NULL)
            .put("trainedDraws", trainedDraws)
            .put("trainedThrough", trainedThrough)
            .put("holdoutDraws", holdoutDraws)
            .put("holdoutPrecision", holdoutPrecision)
            .put("holdoutBaseline", holdoutBaseline)
            .put("trainedAt", trainedAt)
            .toString()
    }

    companion object {
        private const val FORMAT_VERSION = 2

        fun fromJson(game: LotteryGame, raw: String): AiModel? = runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version") != FORMAT_VERSION) return null
            if (json.optString("game") != game.key) return null

            fun parseModel(source: JSONObject?): LogisticModel? {
                source ?: return null
                val weights = source.optJSONArray("w") ?: return null
                val model = LogisticModel(weights.length())
                for (index in 0 until weights.length()) {
                    model.weights[index] = weights.optDouble(index).toFloat()
                }
                model.bias = source.optDouble("b").toFloat()
                source.optJSONArray("mean")?.let { mean ->
                    for (index in 0 until minOf(mean.length(), model.featureMean.size)) {
                        model.featureMean[index] = mean.optDouble(index).toFloat()
                    }
                }
                source.optJSONArray("scale")?.let { scale ->
                    for (index in 0 until minOf(scale.length(), model.featureScale.size)) {
                        model.featureScale[index] = scale.optDouble(index).toFloat()
                    }
                }
                return model
            }

            val main = parseModel(json.optJSONObject("main")) ?: return null
            val stars = parseModel(json.optJSONObject("stars")?.takeIf { !json.isNull("stars") })
            AiModel(
                game = game,
                main = main,
                stars = stars,
                trainedDraws = json.optInt("trainedDraws"),
                trainedThrough = json.optString("trainedThrough"),
                holdoutDraws = json.optInt("holdoutDraws"),
                holdoutPrecision = json.optDouble("holdoutPrecision"),
                holdoutBaseline = json.optDouble("holdoutBaseline"),
                trainedAt = json.optLong("trainedAt"),
            )
        }.getOrNull()
    }
}
