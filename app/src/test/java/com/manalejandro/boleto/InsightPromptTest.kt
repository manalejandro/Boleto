package com.manalejandro.boleto

import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.domain.ai.ondevice.InsightLabels
import com.manalejandro.boleto.domain.ai.ondevice.InsightPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the grounded prompt sent to Gemini Nano. */
class InsightPromptTest {

    @Test
    fun `numbers are formatted with leading zeros`() {
        assertEquals("04 07 08 22 40 49", InsightPrompt.formatNumbers(listOf(4, 7, 8, 22, 40, 49)))
        assertEquals(
            "04 07 08 22 40 ★03 ★11",
            InsightPrompt.formatNumbers(listOf(4, 7, 8, 22, 40), listOf(3, 11)),
        )
    }

    @Test
    fun `lists are padded and limited`() {
        assertEquals(
            "38, 39, 01, 45, 41",
            InsightPrompt.formatList(listOf(38, 39, 1, 45, 41, 7, 9)),
        )
    }

    @Test
    fun `prompt contains every grounded section`() {
        val labels = InsightLabels(
            system = "system",
            gameLine = "Game: La Primitiva",
            strategyLine = "Strategy used to generate it: AI model",
            betsHeader = "Bets:",
            betLines = listOf("1) 04 07 08 22 40 49 · refund 3"),
            historyLine = "Verified archive: 4,202 draws (17 Oct 1985 to 15 Oct 2026)",
            hotLine = "Most frequent main numbers: 38, 39, 01, 45, 41",
            coldLine = "Least frequent main numbers: 20, 25, 33, 24, 49",
            overdueLine = "Most overdue main numbers: 43, 06, 36, 34, 26",
            modelLine = "Local AI model hold-out accuracy: 12.9% (pure chance: 12.2%)",
            oddsLine = "Odds of the top prize with a single bet: 1 in 139,838,160",
        )
        val prompt = InsightPrompt.buildUserPrompt(labels)
        listOf(
            labels.gameLine,
            labels.strategyLine,
            labels.betsHeader,
            labels.betLines.first(),
            labels.historyLine,
            labels.hotLine,
            labels.coldLine,
            labels.overdueLine,
            labels.modelLine.orEmpty(),
            labels.oddsLine,
        ).forEach { line -> assertTrue("missing line: $line", prompt.contains(line)) }
        assertTrue(prompt.endsWith(labels.oddsLine))
    }

    @Test
    fun `prompt omits the model line when there is no trained model`() {
        val labels = InsightLabels(
            system = "system",
            gameLine = "Game: EuroMillions",
            strategyLine = "Strategy used to generate it: Random",
            betsHeader = "Bets:",
            betLines = listOf("1) 03 18 22 34 39 ★03 ★11"),
            historyLine = "history",
            hotLine = "hot",
            coldLine = "cold",
            overdueLine = "overdue",
            modelLine = null,
            oddsLine = "odds",
        )
        val prompt = InsightPrompt.buildUserPrompt(labels)
        assertTrue(prompt.contains("hot"))
        assertFalse(prompt.contains("null"))
        assertFalse(prompt.contains("accuracy"))
    }

    @Test
    fun `top prize odds match the official game rules`() {
        assertEquals(binomial(49, 6) * 10L, InsightPrompt.topPrizeOdds(LotteryGame.PRIMITIVA))
        assertEquals(
            binomial(50, 5) * binomial(12, 2),
            InsightPrompt.topPrizeOdds(LotteryGame.EUROMILLIONS),
        )
    }

    private fun binomial(n: Int, k: Int): Long {
        var result = 1L
        for (index in 0 until k) {
            result = result * (n - index) / (index + 1)
        }
        return result
    }
}
