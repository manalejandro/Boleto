package com.manalejandro.boleto.domain.ai.ondevice

import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.domain.TicketFormat

/**
 * Localized lines used to build the prompt. The UI layer resolves them with
 * `stringResource`, so the model always answers in the language of the app.
 */
data class InsightLabels(
    val system: String,
    val gameLine: String,
    val strategyLine: String,
    val betsHeader: String,
    val betLines: List<String>,
    /** Only present when the ticket plays the Joker. */
    val jokerLine: String? = null,
    /** Draws the ticket takes part in. */
    val drawsLine: String? = null,
    val historyLine: String,
    val hotLine: String,
    val coldLine: String,
    val overdueLine: String,
    /** Only present when the local AI model has been trained. */
    val modelLine: String?,
    val oddsLine: String,
)

/**
 * Builds the grounded prompt handed to Gemini Nano. Every fact comes from the
 * verified archive, so the model only has to narrate — it cannot invent
 * numbers.
 */
object InsightPrompt {

    /**
     * Odds of the top prize with a single bet. Both games share the same
     * figure: C(49,6) × 10 refund digits = C(50,5) × C(12,2) = 139,838,160.
     */
    fun topPrizeOdds(game: LotteryGame): Long = when (game) {
        LotteryGame.PRIMITIVA -> 139_838_160L
        LotteryGame.EUROMILLIONS -> 139_838_160L
    }

    /** "04 07 08 22 40 49" plus "★03 ★11" for EuroMillones. */
    fun formatNumbers(numbers: List<Int>, stars: List<Int> = emptyList()): String =
        TicketFormat.numbers(numbers, stars)

    /** "38, 39, 01, 45, 41" */
    fun formatList(numbers: List<Int>, count: Int = 5): String =
        TicketFormat.list(numbers, count)

    /** The final user prompt sent to the model. */
    fun buildUserPrompt(labels: InsightLabels): String = buildString {
        appendLine(labels.gameLine)
        appendLine(labels.strategyLine)
        appendLine(labels.betsHeader)
        labels.betLines.forEach { line -> appendLine(line) }
        labels.jokerLine?.let { appendLine(it) }
        labels.drawsLine?.let { appendLine(it) }
        appendLine(labels.historyLine)
        appendLine(labels.hotLine)
        appendLine(labels.coldLine)
        appendLine(labels.overdueLine)
        labels.modelLine?.let { appendLine(it) }
        append(labels.oddsLine)
    }.trim()
}
