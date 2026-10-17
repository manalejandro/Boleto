package com.manalejandro.boleto

import com.manalejandro.boleto.data.HistoryStore
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.domain.Backtester
import com.manalejandro.boleto.domain.TicketGenerator
import com.manalejandro.boleto.domain.ai.AiTrainer
import com.manalejandro.boleto.domain.computeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the whole pipeline against the real archives bundled in
 * `src/main/assets` (unit tests run with the module folder as working dir).
 */
class BundledHistoryTest {

    private fun load(game: LotteryGame) =
        HistoryStore.parseCsv(game, File("src/main/assets/${game.assetName}").readText())

    @Test
    fun `la primitiva archive is complete and valid`() {
        val draws = load(LotteryGame.PRIMITIVA)
        assertTrue("expected the full archive", draws.size > 4000)
        assertEquals("1985-10-17", draws.first().date)
        assertTrue(draws.last().date >= "2026-10-01")
        draws.forEach { draw ->
            assertEquals(6, draw.numbers.size)
            assertEquals(6, draw.numbers.toSet().size)
            assertTrue(draw.numbers.all { it in 1..49 })
            draw.complementary?.let { assertTrue(it in 1..49) }
            draw.refund?.let { assertTrue(it in 0..9) }
        }
        // ascending and unique dates
        val dates = draws.map { it.date }
        assertEquals(dates.sorted(), dates)
        assertEquals(dates.size, dates.toSet().size)
    }

    @Test
    fun `euro millions archive is complete and valid`() {
        val draws = load(LotteryGame.EUROMILLIONS)
        assertTrue("expected the full archive", draws.size > 1900)
        assertEquals("2004-02-13", draws.first().date)
        draws.forEach { draw ->
            assertEquals(5, draw.numbers.size)
            assertEquals(2, draw.stars.size)
            assertTrue(draw.numbers.all { it in 1..50 })
            assertTrue(draw.stars.all { it in 1..12 })
        }
    }

    @Test
    fun `statistics over the real archive look sane`() {
        val draws = load(LotteryGame.PRIMITIVA)
        val stats = computeStats(LotteryGame.PRIMITIVA, draws)
        assertEquals(draws.size, stats.totalDraws)
        assertEquals(draws.size.toLong() * 6, stats.mainFrequency.sum().toLong())
        // every number must have been drawn at least once since 1985
        (1..49).forEach { number ->
            assertTrue("number $number never drawn", stats.mainFrequency[number] > 0)
        }
    }

    @Test
    fun `model trains on the real archive and reports honest metrics`() {
        val draws = load(LotteryGame.PRIMITIVA)
        val model = AiTrainer.train(LotteryGame.PRIMITIVA, draws)
        assertNotNull(model)
        val baseline = 6.0 / 49.0
        assertEquals(baseline, model!!.holdoutBaseline, 1e-9)
        // The model must stay in the same order of magnitude as pure chance:
        // if it were "predicting" the lottery, something would be very wrong.
        assertTrue(model.holdoutPrecision in 0.0..0.5)
        println(
            "LAPR model: trained on ${model.trainedDraws} draws, " +
                "hold-out precision ${model.holdoutPrecision} vs chance ${model.holdoutBaseline}"
        )
    }

    @Test
    fun `ticket generation works with the real archive`() {
        val draws = load(LotteryGame.EUROMILLIONS)
        val model = AiTrainer.train(LotteryGame.EUROMILLIONS, draws)
        Strategy.entries.forEach { strategy ->
            val ticket = TicketGenerator.generate(
                game = LotteryGame.EUROMILLIONS,
                history = draws,
                strategy = strategy,
                bets = 2,
                date = "2026-10-17",
                model = if (strategy == Strategy.AI) model else null,
            )
            assertEquals(2, ticket.lines.size)
            ticket.lines.forEach { line ->
                assertEquals(5, line.numbers.size)
                assertEquals(2, line.stars.size)
            }
        }
    }

    @Test
    fun `backtest runs over the real archive`() {
        val draws = load(LotteryGame.PRIMITIVA)
        val results = Backtester.run(
            game = LotteryGame.PRIMITIVA,
            draws = draws,
            window = 100,
            betsPerDraw = 1,
        )
        assertEquals(Strategy.entries.size, results.size)
        results.forEach { result ->
            assertTrue(result.avgHits in 0.0..6.0)
            assertTrue(result.bestHits in 0..6)
            println(
                "${result.strategy}: avg=${"%.3f".format(result.avgHits)} best=${result.bestHits} " +
                    "prizeDraws=${result.prizeDraws}/${result.totalDraws}"
            )
        }
    }
}
