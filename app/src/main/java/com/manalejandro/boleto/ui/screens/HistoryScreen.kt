package com.manalejandro.boleto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.manalejandro.boleto.R
import com.manalejandro.boleto.core.Formats
import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.ui.UiState
import com.manalejandro.boleto.ui.components.DrawBalls
import com.manalejandro.boleto.ui.components.GameSegmentedSelector
import com.manalejandro.boleto.ui.components.KeyValueRow
import java.util.Locale

@Composable
fun HistoryScreen(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    var game by rememberSaveable { mutableStateOf(LotteryGame.EUROMILLIONS) }
    var filter by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Draw?>(null) }
    val locale = LocalConfiguration.current.locales[0]

    val draws = state.draws[game].orEmpty()
    val filtered = remember(draws, filter) {
        val number = filter.trim().toIntOrNull()
        if (number == null) {
            draws
        } else {
            draws.filter { draw ->
                number in draw.numbers || number in draw.stars ||
                    draw.complementary == number || draw.refund == number
            }
        }
    }
    val items = remember(filtered) { buildYearItems(filtered) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        GameSegmentedSelector(selected = game, onSelect = { game = it })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = filter,
            onValueChange = { value ->
                if (value.length <= 2 && value.all { it.isDigit() }) filter = value
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.history_filter_number)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.isNotEmpty()) {
                    IconButton(onClick = { filter = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.history_filter_clear))
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_draws_count, filtered.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        if (draws.isEmpty()) R.string.history_empty else R.string.history_no_results
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    items = items,
                    key = { item ->
                        when (item) {
                            is String -> "year-$item"
                            is Draw -> item.date
                            else -> item.toString()
                        }
                    },
                ) { item ->
                    when (item) {
                        is String -> YearHeader(item)
                        is Draw -> HistoryRow(item, locale) { detail = item }
                    }
                }
            }
        }
    }

    detail?.let { draw ->
        DrawDetailDialog(draw = draw, locale = locale, onDismiss = { detail = null })
    }
}

private fun buildYearItems(draws: List<Draw>): List<Any> {
    val items = ArrayList<Any>(draws.size + 48)
    var currentYear = ""
    for (draw in draws.asReversed()) {
        val year = draw.date.substring(0, 4)
        if (year != currentYear) {
            currentYear = year
            items += year
        }
        items += draw
    }
    return items
}

@Composable
private fun YearHeader(year: String) {
    Text(
        text = year,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun HistoryRow(draw: Draw, locale: Locale, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = IsoDate.formatDisplay(draw.date, locale, "EEE, d MMM"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            draw.jackpot?.let { jackpot ->
                Text(
                    text = Formats.euros(jackpot, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        DrawBalls(draw = draw, ballSize = 30.dp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

@Composable
private fun DrawDetailDialog(draw: Draw, locale: Locale, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        title = {
            Text(IsoDate.formatDisplay(draw.date, locale, "EEEE, d MMMM yyyy"))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DrawBalls(draw = draw, ballSize = 38.dp)
                if (draw.stars.isNotEmpty()) {
                    KeyValueRow(
                        label = stringResource(R.string.history_stars),
                        value = draw.stars.joinToString(", ") { it.toString().padStart(2, '0') },
                    )
                }
                draw.complementary?.let { complementary ->
                    KeyValueRow(
                        label = stringResource(R.string.history_complementario),
                        value = complementary.toString().padStart(2, '0'),
                    )
                }
                draw.refund?.let { refund ->
                    KeyValueRow(
                        label = stringResource(R.string.history_reintegro),
                        value = refund.toString(),
                    )
                }
                draw.jackpot?.let { jackpot ->
                    KeyValueRow(
                        label = stringResource(R.string.history_jackpot),
                        value = Formats.euros(jackpot, locale),
                    )
                }
            }
        },
    )
}
