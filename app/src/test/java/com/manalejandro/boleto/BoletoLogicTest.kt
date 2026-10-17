package com.manalejandro.boleto

import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.HistoryStore
import com.manalejandro.boleto.data.LaeApi
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.data.TicketLine
import com.manalejandro.boleto.domain.PrizeRules
import com.manalejandro.boleto.domain.TicketGenerator
import com.manalejandro.boleto.domain.ai.AiModel
import com.manalejandro.boleto.domain.ai.AiTrainer
import com.manalejandro.boleto.domain.checkLines
import com.manalejandro.boleto.domain.computeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoletoLogicTest {

    // ------------------------------------------------------------------ LAE parsing

    @Test
    fun `parses a La Primitiva combination`() {
        val parsed = LaeApi.parseCombinacion(
            LotteryGame.PRIMITIVA,
            "04 - 07 - 08 - 22 - 40 - 49 C(44) R(2)",
        )
        assertNotNull(parsed)
        assertEquals(listOf(4, 7, 8, 22, 40, 49), parsed!!.numbers)
        assertEquals(44, parsed.complementary)
        assertEquals(2, parsed.refund)
        assertTrue(parsed.stars.isEmpty())
    }

    @Test
    fun `parses an old La Primitiva draw without refund`() {
        val parsed = LaeApi.parseCombinacion(
            LotteryGame.PRIMITIVA,
            "3 - 15 - 30 - 35 - 40 - 43 C(1) R()",
        )
        assertNotNull(parsed)
        assertEquals(listOf(3, 15, 30, 35, 40, 43), parsed!!.numbers)
        assertEquals(1, parsed.complementary)
        assertNull(parsed.refund)
    }

    @Test
    fun `parses a EuroMillions combination`() {
        val parsed = LaeApi.parseCombinacion(
            LotteryGame.EUROMILLIONS,
            " 01 - 07 - 10 - 26 - 34 - 02 - 04",
        )
        assertNotNull(parsed)
        assertEquals(listOf(1, 7, 10, 26, 34), parsed!!.numbers)
        assertEquals(listOf(2, 4), parsed.stars)
    }

    @Test
    fun `rejects malformed combinations`() {
        assertNull(LaeApi.parseCombinacion(LotteryGame.PRIMITIVA, "01 - 02"))
        assertNull(LaeApi.parseCombinacion(LotteryGame.PRIMITIVA, ""))
        assertNull(LaeApi.parseCombinacion(LotteryGame.EUROMILLIONS, null))
    }

    // ------------------------------------------------------------------ CSV storage

    @Test
    fun `csv round trip keeps every field`() {
        val draws = listOf(
            Draw(
                game = LotteryGame.PRIMITIVA,
                date = "2026-10-15",
                numbers = listOf(6, 17, 24, 35, 39, 47),
                complementary = 18,
                refund = 3,
                jackpot = 34_800_000,
            ),
            Draw(
                game = LotteryGame.PRIMITIVA,
                date = "1985-10-17",
                numbers = listOf(3, 11, 13, 15, 34, 35),
                complementary = 27,
            ),
        )
        val parsed = HistoryStore.parseCsv(LotteryGame.PRIMITIVA, HistoryStore.formatCsv(LotteryGame.PRIMITIVA, draws))
        assertEquals(2, parsed.size)
        assertEquals(draws[0], parsed[0])
        assertEquals(draws[1], parsed[1])
        assertNull(parsed[1].refund)
        assertNull(parsed[1].jackpot)
    }

    @Test
    fun `csv round trip works for euro millions`() {
        val draws = listOf(
            Draw(
                game = LotteryGame.EUROMILLIONS,
                date = "2026-10-16",
                numbers = listOf(7, 15, 16, 22, 35),
                stars = listOf(4, 6),
                jackpot = 49_000_000,
            ),
        )
        val parsed = HistoryStore.parseCsv(LotteryGame.EUROMILLIONS, HistoryStore.formatCsv(LotteryGame.EUROMILLIONS, draws))
        assertEquals(draws[0], parsed[0])
    }

    // ------------------------------------------------------------------ generation

    @Test
    fun `every strategy generates valid tickets`() {
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 600)
        Strategy.entries.forEach { strategy ->
            val ticket = TicketGenerator.generate(
                game = LotteryGame.PRIMITIVA,
                history = draws,
                strategy = strategy,
                bets = 3,
                date = "2026-10-17",
                model = if (strategy == Strategy.AI) AiTrainer.train(LotteryGame.PRIMITIVA, draws) else null,
                random = Random(42),
            )
            assertEquals(3, ticket.lines.size)
            ticket.lines.forEach { line ->
                assertEquals(6, line.numbers.size)
                assertEquals(6, line.numbers.toSet().size)
                assertTrue(line.numbers.all { it in 1..49 })
                assertTrue(line.refund in 0..9)
            }
        }
    }

    @Test
    fun `euro millions tickets include two distinct stars`() {
        val draws = syntheticDraws(LotteryGame.EUROMILLIONS, 600)
        val ticket = TicketGenerator.generate(
            game = LotteryGame.EUROMILLIONS,
            history = draws,
            strategy = Strategy.BALANCED,
            bets = 2,
            date = "2026-10-17",
            random = Random(7),
        )
        ticket.lines.forEach { line ->
            assertEquals(5, line.numbers.size)
            assertEquals(2, line.stars.size)
            assertEquals(2, line.stars.toSet().size)
            assertTrue(line.stars.all { it in 1..12 })
            assertNull(line.refund)
        }
    }

    // ------------------------------------------------------------------ prize rules

    @Test
    fun `primitiva categories are ranked correctly`() {
        val draw = Draw(LotteryGame.PRIMITIVA, "2026-01-01", listOf(1, 2, 3, 4, 5, 6), complementary = 7, refund = 9)
        fun rank(numbers: List<Int>, refund: Int?) =
            PrizeRules.categoryRank(LotteryGame.PRIMITIVA, TicketLine(numbers, refund = refund), draw)

        assertEquals(0, rank(listOf(1, 2, 3, 4, 5, 6), 9)) // special
        assertEquals(1, rank(listOf(1, 2, 3, 4, 5, 6), 1))
        assertEquals(2, rank(listOf(1, 2, 3, 4, 5, 7), null))
        assertEquals(3, rank(listOf(1, 2, 3, 4, 5, 8), null))
        assertEquals(4, rank(listOf(1, 2, 3, 4, 8, 9), null))
        assertEquals(5, rank(listOf(1, 2, 3, 8, 9, 10), null))
        assertEquals(6, rank(listOf(10, 11, 12, 13, 14, 15), 9))
        assertNull(rank(listOf(10, 11, 12, 13, 14, 15), 1))
    }

    @Test
    fun `euro millions categories are ranked correctly`() {
        val draw = Draw(LotteryGame.EUROMILLIONS, "2026-01-01", listOf(1, 2, 3, 4, 5), stars = listOf(1, 2))
        fun rank(numbers: List<Int>, stars: List<Int>) =
            PrizeRules.categoryRank(LotteryGame.EUROMILLIONS, TicketLine(numbers, stars), draw)

        assertEquals(0, rank(listOf(1, 2, 3, 4, 5), listOf(1, 2)))
        assertEquals(1, rank(listOf(1, 2, 3, 4, 5), listOf(1, 9)))
        assertEquals(2, rank(listOf(1, 2, 3, 4, 5), listOf(9, 10)))
        assertEquals(3, rank(listOf(1, 2, 3, 4, 9), listOf(1, 2)))
        assertEquals(7, rank(listOf(1, 2, 9, 10, 11), listOf(1, 2)))
        assertEquals(10, rank(listOf(1, 9, 10, 11, 12), listOf(1, 2)))
        assertEquals(12, rank(listOf(1, 2, 9, 10, 11), listOf(3, 4)))
        assertNull(rank(listOf(1, 9, 10, 11, 12), listOf(3, 4)))
    }

    // ------------------------------------------------------------------ statistics & check

    @Test
    fun `statistics are consistent with the history`() {
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 300)
        val stats = computeStats(LotteryGame.PRIMITIVA, draws)
        assertEquals(300, stats.totalDraws)
        assertEquals(300L * 6, stats.mainFrequency.sum().toLong())
        assertTrue(stats.hotNumbers.size == 6)
        assertTrue(stats.coldNumbers.size == 6)
    }

    @Test
    fun `checking a ticket finds historical prizes`() {
        val draws = listOf(
            Draw(LotteryGame.PRIMITIVA, "2026-01-01", listOf(1, 2, 3, 4, 5, 6), complementary = 7, refund = 9),
            Draw(LotteryGame.PRIMITIVA, "2026-01-03", listOf(10, 11, 12, 13, 14, 15), complementary = 16, refund = 1),
        )
        val outcome = checkLines(
            LotteryGame.PRIMITIVA,
            listOf(TicketLine(listOf(1, 2, 3, 4, 5, 6), refund = 9)),
            draws,
        )
        assertEquals(1, outcome.categoryCounts[0])
        assertEquals(0, outcome.bestRank)
        assertEquals(2, outcome.drawsChecked)
    }

    // ------------------------------------------------------------------ AI model

    @Test
    fun `model learns a biased series and survives serialization`() {
        // Number 7 is drawn in half of the synthetic draws: a strong, learnable signal.
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 900, biasedNumber = 7)
        val model = AiTrainer.train(LotteryGame.PRIMITIVA, draws)
        assertNotNull(model)
        assertTrue(
            "hold-out precision should beat pure chance",
            model!!.holdoutPrecision > model.holdoutBaseline,
        )
        val restored = AiModel.fromJson(LotteryGame.PRIMITIVA, model.toJson())
        assertNotNull(restored)
        assertEquals(model.holdoutPrecision, restored!!.holdoutPrecision, 1e-9)
        val probabilities = restored.mainProbabilities(draws, "2026-10-17")
        assertEquals(50, probabilities.size)
        val best = (1..49).maxByOrNull { probabilities[it] }
        assertEquals(7, best)
    }

    // ------------------------------------------------------------------ helpers

    private fun syntheticDraws(
        game: LotteryGame,
        count: Int,
        biasedNumber: Int? = null,
    ): List<Draw> {
        val random = Random(1234)
        return (0 until count).map { index ->
            val numbers = HashSet<Int>()
            if (biasedNumber != null && random.nextDouble() < 0.5) numbers += biasedNumber
            while (numbers.size < game.mainCount) numbers += random.nextInt(1, game.mainMax + 1)
            val stars = HashSet<Int>()
            while (stars.size < game.starCount) stars += random.nextInt(1, game.starMax + 1)
            Draw(
                game = game,
                date = "2000-01-01", // dates are irrelevant for the model features here
                numbers = numbers.sorted(),
                stars = stars.sorted(),
                complementary = if (game.hasComplementary) random.nextInt(1, game.mainMax + 1) else null,
                refund = if (game.hasRefund) random.nextInt(10) else null,
            ).copy(date = dateFor(index))
        }
    }

    private fun dateFor(index: Int): String {
        // Spread the synthetic draws over consecutive days starting in 2000.
        val year = 2000 + index / 365
        val dayOfYear = index % 365
        val month = dayOfYear / 31 + 1
        val day = dayOfYear % 31 + 1
        return "%04d-%02d-%02d".format(year, month.coerceAtMost(12), day.coerceAtMost(28))
    }
}
