package com.manalejandro.boleto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.ModelTraining
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Savings
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
        Strategy.BAYES -> R.string.play_strategy_bayes
        Strategy.MARKOV -> R.string.play_strategy_markov
        Strategy.AI -> R.string.play_strategy_ai
        Strategy.HYBRID -> R.string.play_strategy_hybrid
        Strategy.SMART -> R.string.play_strategy_smart
    }
)

/** Localized one-line explanation of a generation strategy. */
@Composable
fun Strategy.description(): String = stringResource(
    when (this) {
        Strategy.RANDOM -> R.string.play_strategy_desc_random
        Strategy.HOT -> R.string.play_strategy_desc_hot
        Strategy.COLD -> R.string.play_strategy_desc_cold
        Strategy.BALANCED -> R.string.play_strategy_desc_balanced
        Strategy.BAYES -> R.string.play_strategy_desc_bayes
        Strategy.MARKOV -> R.string.play_strategy_desc_markov
        Strategy.AI -> R.string.play_strategy_desc_ai
        Strategy.HYBRID -> R.string.play_strategy_desc_hybrid
        Strategy.SMART -> R.string.play_strategy_desc_smart
    }
)

/** Icon of a generation strategy. */
fun Strategy.icon(): ImageVector = when (this) {
    Strategy.RANDOM -> Icons.Rounded.Shuffle
    Strategy.HOT -> Icons.Rounded.LocalFireDepartment
    Strategy.COLD -> Icons.Rounded.AcUnit
    Strategy.BALANCED -> Icons.Rounded.Balance
    Strategy.BAYES -> Icons.Rounded.QueryStats
    Strategy.MARKOV -> Icons.Rounded.Hub
    Strategy.AI -> Icons.Rounded.AutoAwesome
    Strategy.HYBRID -> Icons.Rounded.ModelTraining
    Strategy.SMART -> Icons.Rounded.Savings
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

/** Localized label of a Joker category (0 = 1ª … 6 = 7ª). */
@Composable
fun jokerPrizeLabel(rank: Int): String = stringResource(
    when (rank) {
        0 -> R.string.joker_prize_1
        1 -> R.string.joker_prize_2
        2 -> R.string.joker_prize_3
        3 -> R.string.joker_prize_4
        4 -> R.string.joker_prize_5
        5 -> R.string.joker_prize_6
        else -> R.string.joker_prize_7
    }
)
