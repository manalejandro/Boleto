package com.manalejandro.boleto.data

import android.content.Context
import java.io.File

/**
 * Persists the draw history as compact CSV files inside the app storage.
 *
 * Columns
 *  - La Primitiva: `date;n1..n6;complementario;reintegro;jackpot`
 *  - EuroMillones: `date;n1..n5;star1;star2;jackpot`
 *
 * On first run the full archive bundled in `assets/` is copied over so the
 * app works offline from the very first launch.
 */
class HistoryStore(private val context: Context) {

    private fun fileFor(game: LotteryGame) = File(context.filesDir, game.assetName)

    /** Copies the bundled archive to internal storage the first time. */
    fun seedIfNeeded(game: LotteryGame, force: Boolean = false) {
        val file = fileFor(game)
        if (!force && file.exists() && file.length() > 0) return
        try {
            context.assets.open(game.assetName).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            // A missing asset is not fatal: the app can still download online.
        }
    }

    fun load(game: LotteryGame): List<Draw> {
        val file = fileFor(game)
        if (!file.exists()) return emptyList()
        return try {
            parseCsv(game, file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(game: LotteryGame, draws: List<Draw>) {
        val file = fileFor(game)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(formatCsv(game, draws))
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    companion object {

        fun parseCsv(game: LotteryGame, text: String): List<Draw> {
            val draws = ArrayList<Draw>(4096)
            for (line in text.lineSequence()) {
                if (line.isBlank()) continue
                val parts = line.trim().split(';')
                if (parts.size < 8) continue
                val date = parts[0]
                if (date.length != 10) continue
                val numbers = (1..game.mainCount).mapNotNull { parts.getOrNull(it)?.toIntOrNull() }
                if (numbers.size != game.mainCount) continue
                val stars = (1..game.starCount).mapNotNull {
                    parts.getOrNull(game.mainCount + it)?.toIntOrNull()
                }
                val complementary = if (game.hasComplementary) {
                    parts.getOrNull(game.mainCount + 1)?.toIntOrNull()
                } else null
                val refund = if (game.hasRefund) {
                    parts.getOrNull(game.mainCount + 2)?.toIntOrNull()
                } else null
                val jackpotIndex = 1 + game.mainCount + game.starCount +
                    (if (game.hasComplementary) 1 else 0) + (if (game.hasRefund) 1 else 0)
                val jackpot = parts.getOrNull(jackpotIndex)?.toLongOrNull()?.takeIf { it > 0 }
                val joker = if (game.hasJoker) {
                    LaeApi.parseJoker(parts.getOrNull(jackpotIndex + 1))
                } else {
                    null
                }
                draws += Draw(
                    game = game,
                    date = date,
                    numbers = numbers.sorted(),
                    stars = stars.sorted(),
                    complementary = complementary,
                    refund = refund,
                    jackpot = jackpot,
                    joker = joker,
                )
            }
            return draws
        }

        fun formatCsv(game: LotteryGame, draws: List<Draw>): String {
            val builder = StringBuilder(draws.size * 40)
            for (draw in draws) {
                builder.append(draw.date)
                draw.numbers.forEach { builder.append(';').append(it.toString().padStart(2, '0')) }
                draw.stars.forEach { builder.append(';').append(it.toString().padStart(2, '0')) }
                if (game.hasComplementary) builder.append(';').append(draw.complementary?.toString() ?: "")
                if (game.hasRefund) builder.append(';').append(draw.refund?.toString() ?: "")
                builder.append(';').append(draw.jackpot?.toString() ?: "")
                if (game.hasJoker) builder.append(';').append(draw.joker ?: "")
                builder.append('\n')
            }
            return builder.toString()
        }
    }
}
