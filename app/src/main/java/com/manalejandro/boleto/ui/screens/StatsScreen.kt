package com.manalejandro.boleto.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manalejandro.boleto.R
import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.ui.UiState
import com.manalejandro.boleto.ui.components.GameSegmentedSelector
import com.manalejandro.boleto.ui.components.KeyValueRow
import com.manalejandro.boleto.ui.components.NumberChipsRow
import com.manalejandro.boleto.ui.components.SectionCard
import com.manalejandro.boleto.ui.components.SectionTitle
import com.manalejandro.boleto.ui.components.label
import com.manalejandro.boleto.ui.components.styleFor
import com.manalejandro.boleto.ui.theme.EuroMillionsStar
import com.manalejandro.boleto.ui.theme.accent

@Composable
fun StatsScreen(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    var game by rememberSaveable { mutableStateOf(LotteryGame.EUROMILLIONS) }
    val locale = LocalConfiguration.current.locales[0]
    val stats = state.stats[game]

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameSegmentedSelector(selected = game, onSelect = { game = it })

        if (stats == null || stats.totalDraws == 0) {
            SectionCard {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SectionCard {
                SectionTitle(Icons.Rounded.Insights, stringResource(R.string.stats_draws_analyzed))
                KeyValueRow(
                    label = game.label(),
                    value = stringResource(R.string.home_draws_count, stats.totalDraws),
                )
                val first = stats.firstDate
                val last = stats.lastDate
                if (first != null && last != null) {
                    KeyValueRow(
                        label = "",
                        value = stringResource(
                            R.string.home_range,
                            IsoDate.formatDisplay(first, locale, "d MMM yyyy"),
                            IsoDate.formatDisplay(last, locale, "d MMM yyyy"),
                        ),
                    )
                }
            }

            SectionCard {
                SectionTitle(
                    icon = Icons.Rounded.BarChart,
                    text = stringResource(R.string.stats_frequency),
                    tint = game.accent,
                )
                Text(
                    text = stringResource(R.string.stats_frequency_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FrequencyChart(
                    values = stats.mainFrequency,
                    maxValue = stats.maxMainFrequency(),
                    color = game.accent,
                )
            }

            if (game.starCount > 0) {
                SectionCard {
                    SectionTitle(
                        icon = Icons.Rounded.Star,
                        text = stringResource(R.string.stats_stars_frequency),
                        tint = EuroMillionsStar,
                    )
                    FrequencyChart(
                        values = stats.starFrequency,
                        maxValue = stats.maxStarFrequency(),
                        color = EuroMillionsStar,
                    )
                }
            }

            SectionCard {
                NumberChipsRow(
                    title = stringResource(R.string.stats_hot),
                    icon = Icons.Rounded.LocalFireDepartment,
                    numbers = stats.hotNumbers,
                    style = styleFor(game),
                    tint = Color(0xFFE8590C),
                )
                NumberChipsRow(
                    title = stringResource(R.string.stats_cold),
                    icon = Icons.Rounded.AcUnit,
                    numbers = stats.coldNumbers,
                    style = styleFor(game),
                    tint = Color(0xFF1C7ED6),
                )
                NumberChipsRow(
                    title = stringResource(R.string.stats_most_overdue),
                    icon = Icons.Rounded.HourglassEmpty,
                    numbers = stats.overdueNumbers,
                    style = styleFor(game),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.stats_delay_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FrequencyChart(
    values: IntArray,
    maxValue: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val area = 118.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .height(area + 40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (number in 1 until values.size) {
            BarColumn(
                value = values[number],
                maxValue = maxValue,
                color = color,
                area = area,
                label = number.toString(),
            )
        }
    }
}

@Composable
private fun BarColumn(
    value: Int,
    maxValue: Int,
    color: Color,
    area: Dp,
    label: String,
) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value, maxValue) {
        target = if (maxValue <= 0) 0f else value.toFloat() / maxValue
    }
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "bar",
    )
    Column(
        modifier = Modifier.height(area + 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = value.toString(),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height((area * fraction).coerceAtLeast(2.dp))
                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                .background(
                    Brush.verticalGradient(listOf(color, color.copy(alpha = 0.45f)))
                )
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
