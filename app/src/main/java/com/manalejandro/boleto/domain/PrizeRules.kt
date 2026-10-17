package com.manalejandro.boleto.domain

import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.TicketLine

/**
 * Official prize categories of both games, expressed as a 0-based rank
 * (0 = top prize). `null` means the ticket wins nothing.
 */
object PrizeRules {

    /** EuroMillions categories, most valuable first. */
    val EURO_MILLIONS_LABELS = listOf(
        "5+2", "5+1", "5+0", "4+2", "4+1", "4+0",
        "3+2", "2+2", "3+1", "3+0", "1+2", "2+1", "2+0",
    )

    const val PRIMITIVA_CATEGORIES = 7

    fun categoryCount(game: LotteryGame): Int = when (game) {
        LotteryGame.EUROMILLIONS -> EURO_MILLIONS_LABELS.size
        LotteryGame.PRIMITIVA -> PRIMITIVA_CATEGORIES
    }

    /** Rank of the best category the line would have won in the given draw. */
    fun categoryRank(game: LotteryGame, line: TicketLine, draw: Draw): Int? =
        when (game) {
            LotteryGame.EUROMILLIONS -> euroMillionsRank(
                matches = line.numbers.count { it in draw.numbers },
                stars = line.stars.count { it in draw.stars },
            )
            LotteryGame.PRIMITIVA -> primitivaRank(
                matches = line.numbers.count { it in draw.numbers },
                complementary = line.numbers.contains(draw.complementary),
                refund = line.refund != null && line.refund == draw.refund,
            )
        }

    private fun euroMillionsRank(matches: Int, stars: Int): Int? = when {
        matches == 5 && stars == 2 -> 0
        matches == 5 && stars == 1 -> 1
        matches == 5 -> 2
        matches == 4 && stars == 2 -> 3
        matches == 4 && stars == 1 -> 4
        matches == 4 -> 5
        matches == 3 && stars == 2 -> 6
        matches == 2 && stars == 2 -> 7
        matches == 3 && stars == 1 -> 8
        matches == 3 -> 9
        matches == 1 && stars == 2 -> 10
        matches == 2 && stars == 1 -> 11
        matches == 2 -> 12
        else -> null
    }

    private fun primitivaRank(matches: Int, complementary: Boolean, refund: Boolean): Int? = when {
        matches == 6 && refund -> 0 // Special category
        matches == 6 -> 1
        matches == 5 && complementary -> 2
        matches == 5 -> 3
        matches == 4 -> 4
        matches == 3 -> 5
        refund -> 6
        else -> null
    }
}

/** Outcome of checking a ticket against the whole stored history. */
data class CheckOutcome(
    val categoryCounts: Map<Int, Int>,
    val bestRank: Int?,
    val bestDate: String?,
    val drawsChecked: Int,
)

fun checkLines(game: LotteryGame, lines: List<TicketLine>, draws: List<Draw>): CheckOutcome {
    val counts = HashMap<Int, Int>()
    var bestRank: Int? = null
    var bestDate: String? = null
    lines.forEach { line ->
        draws.forEach { draw ->
            val rank = PrizeRules.categoryRank(game, line, draw) ?: return@forEach
            counts[rank] = (counts[rank] ?: 0) + 1
            if (bestRank == null || rank < bestRank!!) {
                bestRank = rank
                bestDate = draw.date
            }
        }
    }
    return CheckOutcome(counts, bestRank, bestDate, draws.size)
}
