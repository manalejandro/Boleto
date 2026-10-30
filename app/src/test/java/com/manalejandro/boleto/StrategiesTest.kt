package com.manalejandro.boleto

import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.domain.Backtester
import com.manalejandro.boleto.domain.SmartTicket
import com.manalejandro.boleto.domain.TicketGenerator
import com.manalejandro.boleto.domain.ai.FeatureExtractor
import com.manalejandro.boleto.domain.ai.MarkovModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StrategiesTest {

    // ------------------------------------------------------------------ Bayes

    @Test
    fun `ewma counts weigh recent draws more`() {
        val extractor = FeatureExtractor(49)
        // 7 appears in every draw, 8 only in the first half.
        repeat(100) { extractor.advance(listOf(7, 8, 20, 30, 40, 48)) }
        repeat(100) { extractor.advance(listOf(7, 21, 22, 31, 41, 49)) }

        assertTrue(extractor.ewma(7) > extractor.ewma(8))
        assertTrue(extractor.ewma(8) > extractor.ewma(9))
        assertEquals(0.0, extractor.ewma(9), 1e-9)
    }

    @Test
    fun `bayes strategy favours the recently hot number`() {
        // Number 7 appears in every one of the last 150 synthetic draws.
        val random = Random(9)
        val draws = (0 until 300).map { index ->
            val numbers = LinkedHashSet<Int>()
            if (index >= 150) numbers += 7
            while (numbers.size < 6) numbers += random.nextInt(1, 50)
            Draw(LotteryGame.PRIMITIVA, dateFor(index), numbers.sorted())
        }

        val counts = IntArray(50)
        repeat(300) { seed ->
            val ticket = TicketGenerator.generate(
                game = LotteryGame.PRIMITIVA,
                history = draws,
                strategy = Strategy.BAYES,
                bets = 1,
                date = "2026-10-17",
                random = Random(seed),
            )
            ticket.lines[0].numbers.forEach { number -> counts[number]++ }
        }
        assertEquals(7, (1..49).maxByOrNull { counts[it] })
    }

    // ----------------------------------------------------------------- Markov

    @Test
    fun `markov scores the numbers that follow the last draw`() {
        val model = MarkovModel(49)
        val a = listOf(3, 21, 22, 23, 24, 25)
        val b = listOf(7, 41, 42, 43, 44, 45)
        repeat(200) { index -> model.advance(if (index % 2 == 0) a else b) }

        // The last draw advanced is b, and after b the history always shows a.
        val scores = model.scores()
        assertNotNull(scores)
        assertEquals(50, scores!!.size)
        for (number in a) {
            assertTrue("expected $number to score above an unseen number", scores[number] > scores[9])
        }
        assertTrue("numbers of the last draw should not repeat", scores[7] < scores[3])
    }

    @Test
    fun `markov needs a minimum history`() {
        val model = MarkovModel(49)
        repeat(10) { model.advance(listOf(1, 2, 3, 4, 5, 6)) }
        assertNull(model.scores())
    }

    // ----------------------------------------------------------------- Hybrid

    @Test
    fun `hybrid strategy works without a trained model`() {
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 400)
        val ticket = TicketGenerator.generate(
            game = LotteryGame.PRIMITIVA,
            history = draws,
            strategy = Strategy.HYBRID,
            bets = 2,
            date = "2026-10-17",
            model = null,
            random = Random(3),
        )
        ticket.lines.forEach { line ->
            assertEquals(6, line.numbers.size)
            assertEquals(6, line.numbers.toSet().size)
            assertTrue(line.numbers.all { it in 1..49 })
        }
    }

    // ------------------------------------------------------------------- Smart

    @Test
    fun `smart rules accept typical combinations and reject extremes`() {
        assertTrue(SmartTicket.acceptable(listOf(8, 17, 24, 35, 39, 47), 150.0, 30.0))
        assertFalse(SmartTicket.acceptable(listOf(1, 2, 3, 4, 5, 6), 150.0, 30.0))
        assertFalse(SmartTicket.acceptable(listOf(40, 41, 42, 43, 44, 45), 150.0, 30.0))
        assertTrue(SmartTicket.violations(listOf(1, 2, 3, 4, 5, 6), 150.0, 30.0) >= 2)
        // Without sum statistics yet, the sum window is skipped.
        assertTrue(SmartTicket.acceptable(listOf(8, 17, 24, 35, 39, 47), 0.0, 0.0))
    }

    @Test
    fun `draw sum statistics follow the advanced draws`() {
        val extractor = FeatureExtractor(49)
        extractor.advance(listOf(1, 2, 3, 4))    // sum 10
        extractor.advance(listOf(2, 4, 6, 8))    // sum 20
        extractor.advance(listOf(3, 6, 9, 12))   // sum 30
        assertEquals(20.0, extractor.drawSumMean, 1e-9)
        assertEquals(10.0, extractor.drawSumSd, 1e-9)
    }

    @Test
    fun `smart strategy tilts away from birthday numbers`() {
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 400)
        val context = TicketGenerator.context(LotteryGame.PRIMITIVA, draws, "2026-10-17", null)
        val random = Random(11)
        var low = 0
        var total = 0
        repeat(200) {
            val line = TicketGenerator.line(context, Strategy.SMART, "2026-10-17", random)
            assertEquals(6, line.numbers.size)
            assertEquals(6, line.numbers.toSet().size)
            line.numbers.forEach { number ->
                total++
                if (number <= 31) low++
            }
        }
        // Uniform picking would put ~63 % of the numbers at or below 31
        // (31 of 49); the popularity penalty has to move that clearly down.
        assertTrue(
            "expected fewer than 55 % of picks in 1..31, got ${100.0 * low / total} %",
            low.toDouble() / total < 0.55,
        )
    }

    // -------------------------------------------------------------- Backtester

    @Test
    fun `backtester evaluates every strategy`() {
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 900)
        val results = Backtester.run(LotteryGame.PRIMITIVA, draws, window = 30, betsPerDraw = 1)
        assertEquals(Strategy.entries.size, results.size)
        results.forEach { result ->
            assertEquals(30, result.totalDraws)
            assertTrue(result.avgHits >= 0.0)
            // One bet per draw: every prize comes from a distinct draw.
            assertTrue(result.prizeDraws <= result.totalDraws)
            assertEquals(result.prizeDraws, result.categoryCounts.sum())
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun syntheticDraws(game: LotteryGame, count: Int): List<Draw> {
        val random = Random(1234)
        return (0 until count).map { index ->
            val numbers = HashSet<Int>()
            while (numbers.size < game.mainCount) numbers += random.nextInt(1, game.mainMax + 1)
            val stars = HashSet<Int>()
            while (stars.size < game.starCount) stars += random.nextInt(1, game.starMax + 1)
            Draw(
                game = game,
                date = dateFor(index),
                numbers = numbers.sorted(),
                stars = stars.sorted(),
                complementary = if (game.hasComplementary) random.nextInt(1, game.mainMax + 1) else null,
                refund = if (game.hasRefund) random.nextInt(10) else null,
            )
        }
    }

    private fun dateFor(index: Int): String {
        val year = 2000 + index / 365
        val dayOfYear = index % 365
        val month = dayOfYear / 31 + 1
        val day = dayOfYear % 31 + 1
        return "%04d-%02d-%02d".format(year, month.coerceAtMost(12), day.coerceAtMost(28))
    }
}
