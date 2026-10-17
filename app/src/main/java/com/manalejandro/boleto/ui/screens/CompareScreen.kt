package com.manalejandro.boleto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manalejandro.boleto.R
import com.manalejandro.boleto.core.Formats
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.ui.ComparisonState
import com.manalejandro.boleto.ui.UiState
import com.manalejandro.boleto.ui.components.GameSegmentedSelector
import com.manalejandro.boleto.ui.components.SectionCard
import com.manalejandro.boleto.ui.components.SectionTitle
import com.manalejandro.boleto.ui.components.icon
import com.manalejandro.boleto.ui.components.label
import com.manalejandro.boleto.ui.theme.accent
import java.util.Locale

private val WINDOW_OPTIONS = listOf(100, 250, 500)
private val BETS_OPTIONS = listOf(1, 2, 3)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareScreen(
    state: UiState,
    onRun: (LotteryGame, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var game by rememberSaveable { mutableStateOf(LotteryGame.EUROMILLIONS) }
    var window by rememberSaveable { mutableIntStateOf(250) }
    var bets by rememberSaveable { mutableIntStateOf(2) }
    val locale = LocalConfiguration.current.locales[0]
    val comparison = state.comparison

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameSegmentedSelector(selected = game, onSelect = { game = it })

        SectionCard {
            SectionTitle(
                icon = Icons.Rounded.Leaderboard,
                text = stringResource(R.string.compare_title),
                tint = game.accent,
            )
            Text(
                text = stringResource(R.string.compare_intro, window, bets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.compare_window),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WINDOW_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = window == option,
                        onClick = { window = option },
                        label = { Text(option.toString()) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.compare_bets),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BETS_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = bets == option,
                        onClick = { bets = option },
                        label = { Text(option.toString()) },
                    )
                }
            }

            Button(
                onClick = { onRun(game, window, bets) },
                enabled = comparison !is ComparisonState.Running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.compare_run),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        when (comparison) {
            is ComparisonState.Running -> SectionCard {
                LinearProgressIndicator(
                    progress = { comparison.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.compare_running, (comparison.fraction * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is ComparisonState.Done -> ComparisonResults(comparison, game, locale)

            is ComparisonState.Error -> SectionCard {
                Text(
                    text = stringResource(R.string.play_ai_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Unit
        }

        Text(
            text = stringResource(R.string.compare_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.compare_ai_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ComparisonResults(
    done: ComparisonState.Done,
    game: LotteryGame,
    locale: Locale,
) {
    val maxAverage = done.results.maxOf { it.avgHits }.coerceAtLeast(0.0001)
    SectionCard {
        done.results.forEach { result ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = result.strategy.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (result.strategy == Strategy.AI) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = result.strategy.label(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Formats.decimal(result.avgHits, locale),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((result.avgHits / maxAverage).toFloat())
                            .fillMaxHeight()
                            .background(
                                if (result.strategy == Strategy.AI) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    game.accent
                                }
                            )
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.compare_best) + ": " + result.bestHits,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.compare_prize_rate) + ": " +
                            Formats.percent(result.prizeRate, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
