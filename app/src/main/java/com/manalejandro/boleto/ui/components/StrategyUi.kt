package com.manalejandro.boleto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.manalejandro.boleto.R
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.domain.PrizeRules

/** Localized name of a generation strategy. */
@Composable
fun Strategy.label(): String = stringResource(
    when (this) {
        Strategy.RANDOM -> R.string.play_strategy_random
        Strategy.HOT -> R.string.play_strategy_hot
        Strategy.COLD -> R.string.play_strategy_cold
        Strategy.BALANCED -> R.string.play_strategy_balanced
        Strategy.AI -> R.string.play_strategy_ai
    }
)

/** Icon of a generation strategy. */
fun Strategy.icon(): ImageVector = when (this) {
    Strategy.RANDOM -> Icons.Rounded.Shuffle
    Strategy.HOT -> Icons.Rounded.LocalFireDepartment
    Strategy.COLD -> Icons.Rounded.AcUnit
    Strategy.BALANCED -> Icons.Rounded.Balance
    Strategy.AI -> Icons.Rounded.AutoAwesome
}

/**
 * Localized label of a prize category: EuroMillions uses the universal
 * "5+2"-style codes, La Primitiva has named categories.
 */
@Composable
fun prizeLabel(game: LotteryGame, rank: Int): String = when (game) {
    LotteryGame.EUROMILLIONS -> PrizeRules.EURO_MILLIONS_LABELS.getOrElse(rank) { "#$rank" }
    LotteryGame.PRIMITIVA -> stringResource(
        when (rank) {
            0 -> R.string.prize_especial
            1 -> R.string.prize_1
            2 -> R.string.prize_2
            3 -> R.string.prize_3
            4 -> R.string.prize_4
            5 -> R.string.prize_5
            else -> R.string.prize_refund
        }
    )
}
