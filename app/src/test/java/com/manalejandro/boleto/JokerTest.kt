package com.manalejandro.boleto

import com.manalejandro.boleto.data.Boleto
import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.HistoryStore
import com.manalejandro.boleto.data.LaeApi
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.data.TicketLine
import com.manalejandro.boleto.domain.JokerRules
import com.manalejandro.boleto.domain.TicketGenerator
import com.manalejandro.boleto.domain.checkLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Tests for the Joker game: rules, generation, storage and checking. */
class JokerTest {

    // ------------------------------------------------------------------ rules

    @Test
    fun `joker pays the documented example`() {
        // Official example: winning 6306097, played 6301597
        // → first 3 digits (5th category, 50 €) + last 2 digits (6th, 5 €) = 55 €
        assertEquals(listOf(4, 5), JokerRules.wonRanks("6301597", "6306097"))
        assertEquals(55L, JokerRules.total("6301597", "6306097"))
    }

    @Test
    fun `joker full match pays the first category only`() {
        assertEquals(listOf(0), JokerRules.wonRanks("1234567", "1234567"))
        assertEquals(1_000_000L, JokerRules.total("1234567", "1234567"))
    }

    @Test
    fun `joker prefix and suffix categories are mapped correctly`() {
        // 6 first digits
        assertEquals(listOf(1), JokerRules.wonRanks("1234560", "1234567"))
        // 5 last digits (3rd category)
        assertEquals(listOf(2), JokerRules.wonRanks("9934567", "1234567"))
        // 4 first digits only (4th category)
        assertEquals(listOf(3), JokerRules.wonRanks("1234000", "1234567"))
        // last digit only (7th category)
        assertEquals(listOf(6), JokerRules.wonRanks("0000007", "1234567"))
        // nothing
        assertTrue(JokerRules.wonRanks("0000000", "1234567").isEmpty())
        assertEquals(0L, JokerRules.total("0000000", "1234567"))
    }

    @Test
    fun `joker ignores malformed codes`() {
        assertTrue(JokerRules.wonRanks("123", "1234567").isEmpty())
        assertTrue(JokerRules.wonRanks("1234567", "abcdefg").isEmpty())
    }

    @Test
    fun `joker fixed prizes match the official table`() {
        assertEquals(1_000_000L, JokerRules.amountFor(0))
        assertEquals(10_000L, JokerRules.amountFor(1))
        assertEquals(1_000L, JokerRules.amountFor(2))
        assertEquals(300L, JokerRules.amountFor(3))
        assertEquals(50L, JokerRules.amountFor(4))
        assertEquals(5L, JokerRules.amountFor(5))
        assertEquals(1L, JokerRules.amountFor(6))
    }

    // ------------------------------------------------------------------ generation

    @Test
    fun `joker is only generated for la primitiva and when requested`() {
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 400)

        val withJoker = TicketGenerator.generate(
            game = LotteryGame.PRIMITIVA,
            history = draws,
            strategy = Strategy.RANDOM,
            bets = 2,
            date = "2026-10-19",
            withJoker = true,
            random = Random(1),
        )
        assertNotNull(withJoker.joker)
        assertEquals(7, withJoker.joker!!.length)
        assertTrue(withJoker.joker!!.all(Char::isDigit))

        val withoutJoker = TicketGenerator.generate(
            game = LotteryGame.PRIMITIVA,
            history = draws,
            strategy = Strategy.RANDOM,
            bets = 1,
            date = "2026-10-19",
            withJoker = false,
            random = Random(1),
        )
        assertNull(withoutJoker.joker)

        val euroMillions = TicketGenerator.generate(
            game = LotteryGame.EUROMILLIONS,
            history = syntheticDraws(LotteryGame.EUROMILLIONS, 400),
            strategy = Strategy.RANDOM,
            bets = 1,
            date = "2026-10-19",
            withJoker = true,
            random = Random(1),
        )
        assertNull("EuroMillones has no Joker", euroMillions.joker)
    }

    @Test
    fun `joker codes keep leading zeros`() {
        val draws = syntheticDraws(LotteryGame.PRIMITIVA, 400)
        var found = false
        repeat(200) { seed ->
            val code = TicketGenerator.randomJoker(Random(seed))
            assertEquals(7, code.length)
            assertTrue(code.all(Char::isDigit))
            if (code.startsWith("0")) found = true
        }
        assertTrue("a 7-digit code must be able to start with 0", found)
    }

    // ------------------------------------------------------------------ check

    @Test
    fun `checking a ticket counts joker prizes`() {
        val draws = listOf(
            Draw(
                game = LotteryGame.PRIMITIVA,
                date = "2026-01-01",
                numbers = listOf(1, 2, 3, 4, 5, 6),
                complementary = 7,
                refund = 9,
                joker = "6306097",
            ),
            Draw(
                game = LotteryGame.PRIMITIVA,
                date = "2026-01-03",
                numbers = listOf(10, 11, 12, 13, 14, 15),
                complementary = 16,
                refund = 1,
                joker = "1234560",
            ),
        )
        val outcome = checkLines(
            game = LotteryGame.PRIMITIVA,
            lines = listOf(TicketLine(listOf(20, 21, 22, 23, 24, 25), refund = 5)),
            draws = draws,
            joker = "6301597",
        )
        // Only the first draw matches: 5th + 6th category = 55 €
        assertEquals(mapOf(4 to 1, 5 to 1), outcome.jokerCategoryCounts)
        assertEquals(55L, outcome.jokerTotal)
    }

    // ------------------------------------------------------------------ storage

    @Test
    fun `csv round trip keeps the joker column`() {
        val draws = listOf(
            Draw(
                game = LotteryGame.PRIMITIVA,
                date = "2026-10-17",
                numbers = listOf(8, 9, 12, 14, 29, 45),
                complementary = 43,
                refund = 4,
                jackpot = 37_500_000,
                joker = "8525836",
            ),
            Draw(
                game = LotteryGame.PRIMITIVA,
                date = "1985-10-17",
                numbers = listOf(3, 11, 13, 15, 34, 35),
                complementary = 27,
            ),
        )
        val csv = HistoryStore.formatCsv(LotteryGame.PRIMITIVA, draws)
        val parsed = HistoryStore.parseCsv(LotteryGame.PRIMITIVA, csv)
        assertEquals(draws[0], parsed[0])
        assertEquals("8525836", parsed[0].joker)
        assertNull(parsed[1].joker)
    }

    @Test
    fun `lae joker parsing is defensive`() {
        assertEquals("0687730", LaeApi.parseJoker("0687730"))
        assertNull(LaeApi.parseJoker(""))
        assertNull(LaeApi.parseJoker("123456"))
        assertNull(LaeApi.parseJoker("12345678"))
        assertNull(LaeApi.parseJoker("123456a"))
        assertNull(LaeApi.parseJoker(null))
    }

    // ------------------------------------------------------------------ helpers

    private fun syntheticDraws(game: LotteryGame, count: Int): List<Draw> {
        val random = Random(99)
        return (0 until count).map { index ->
            val numbers = HashSet<Int>()
            while (numbers.size < game.mainCount) numbers += random.nextInt(1, game.mainMax + 1)
            Draw(
                game = game,
                date = "20%02d-%02d-%02d".format(10 + index / 365, index % 12 + 1, index % 28 + 1),
                numbers = numbers.sorted(),
                complementary = if (game.hasComplementary) random.nextInt(1, game.mainMax + 1) else null,
                refund = if (game.hasRefund) random.nextInt(10) else null,
                joker = if (game.hasJoker) random.nextInt(10_000_000).toString().padStart(7, '0') else null,
            )
        }
    }
}
