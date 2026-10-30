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
    /** Joker categories won (0-based, 0 = 1ª) and how many times. */
    val jokerCategoryCounts: Map<Int, Int> = emptyMap(),
    /** Total amount the Joker would have paid, in euros. */
    val jokerTotal: Long = 0L,
)

/**
 * The Joker game associated with La Primitiva: a random 7-digit code, 1 € per
 * ticket, that wins fixed prizes for matching the first or the last digits of
 * the winning code. Both matches add up (e.g. first 3 digits + last 2 digits).
 */
object JokerRules {

    /** Fixed prizes of the 1ª..7ª categories (index 0..6), in euros. */
    val PRIZES = longArrayOf(1_000_000L, 10_000L, 1_000L, 300L, 50L, 5L, 1L)

    /**
     * Categories won by [code] against the [winning] code of a draw.
     * A full 7-digit match always pays only the 1st category.
     */
    fun wonRanks(code: String, winning: String): List<Int> {
        if (code.length != 7 || winning.length != 7) return emptyList()
        if (!code.all(Char::isDigit) || !winning.all(Char::isDigit)) return emptyList()

        var prefix = 0
        while (prefix < 7 && code[prefix] == winning[prefix]) prefix++
        if (prefix == 7) return listOf(0)

        var suffix = 0
        while (suffix < 7 && code[6 - suffix] == winning[6 - suffix]) suffix++

        val ranks = ArrayList<Int>(2)
        if (prefix > 0) ranks += 7 - prefix
        if (suffix > 0) ranks += 7 - suffix
        return ranks
    }

    fun amountFor(rank: Int): Long = PRIZES.getOrElse(rank) { 0L }

    /** Total prize for one draw: prefix and suffix matches are added up. */
    fun total(code: String, winning: String): Long =
        wonRanks(code, winning).sumOf { amountFor(it) }
}

fun checkLines(
    game: LotteryGame,
    lines: List<TicketLine>,
    draws: List<Draw>,
    joker: String? = null,
): CheckOutcome {
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

    val jokerCounts = HashMap<Int, Int>()
    var jokerTotal = 0L
    if (joker != null && game.hasJoker) {
        draws.forEach { draw ->
            val winning = draw.joker ?: return@forEach
            JokerRules.wonRanks(joker, winning).forEach { rank ->
                jokerCounts[rank] = (jokerCounts[rank] ?: 0) + 1
                jokerTotal += JokerRules.amountFor(rank)
            }
        }
    }

    return CheckOutcome(
        categoryCounts = counts,
        bestRank = bestRank,
        bestDate = bestDate,
        drawsChecked = draws.size,
        jokerCategoryCounts = jokerCounts,
        jokerTotal = jokerTotal,
    )
}
