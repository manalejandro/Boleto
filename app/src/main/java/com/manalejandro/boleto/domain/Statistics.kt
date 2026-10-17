package com.manalejandro.boleto.domain

import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame

/** Aggregated statistics for one game. Index 0 of the arrays is unused. */
data class GameStats(
    val totalDraws: Int,
    val firstDate: String?,
    val lastDate: String?,
    val mainFrequency: IntArray,
    val starFrequency: IntArray,
    val mainDelay: IntArray,
    val starDelay: IntArray,
    val hotNumbers: List<Int>,
    val coldNumbers: List<Int>,
    val overdueNumbers: List<Int>,
) {
    fun maxMainFrequency(): Int = mainFrequency.maxOrNull() ?: 1
    fun maxStarFrequency(): Int = starFrequency.maxOrNull() ?: 1
}

fun computeStats(game: LotteryGame, draws: List<Draw>): GameStats {
    val mainFrequency = IntArray(game.mainMax + 1)
    val starFrequency = IntArray(game.starMax + 1)
    draws.forEach { draw ->
        draw.numbers.forEach { if (it in 1..game.mainMax) mainFrequency[it]++ }
        draw.stars.forEach { if (it in 1..game.starMax) starFrequency[it]++ }
    }

    // Delay: number of draws since each number last appeared (0 = newest draw).
    val mainDelay = IntArray(game.mainMax + 1) { draws.size }
    val starDelay = IntArray(game.starMax + 1) { draws.size }
    val mainSeen = BooleanArray(game.mainMax + 1)
    val starSeen = BooleanArray(game.starMax + 1)
    for (index in draws.indices.reversed()) {
        val delay = draws.size - 1 - index
        draws[index].numbers.forEach { number ->
            if (number in 1..game.mainMax && !mainSeen[number]) {
                mainSeen[number] = true
                mainDelay[number] = delay
            }
        }
        draws[index].stars.forEach { number ->
            if (number in 1..game.starMax && !starSeen[number]) {
                starSeen[number] = true
                starDelay[number] = delay
            }
        }
    }

    val numbers = (1..game.mainMax).toList()
    val hot = numbers.sortedWith(compareByDescending<Int> { mainFrequency[it] }.thenBy { it })
        .take(game.mainCount)
    val cold = numbers.sortedWith(compareBy<Int> { mainFrequency[it] }.thenBy { it })
        .take(game.mainCount)
    val overdue = numbers.sortedWith(compareByDescending<Int> { mainDelay[it] }.thenBy { it })
        .take(game.mainCount)

    return GameStats(
        totalDraws = draws.size,
        firstDate = draws.firstOrNull()?.date,
        lastDate = draws.lastOrNull()?.date,
        mainFrequency = mainFrequency,
        starFrequency = starFrequency,
        mainDelay = mainDelay,
        starDelay = starDelay,
        hotNumbers = hot,
        coldNumbers = cold,
        overdueNumbers = overdue,
    )
}
