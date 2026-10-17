package com.manalejandro.boleto.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manalejandro.boleto.R
import com.manalejandro.boleto.core.Formats
import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.ui.SyncState
import com.manalejandro.boleto.ui.UiState
import com.manalejandro.boleto.ui.components.BallStyle
import com.manalejandro.boleto.ui.components.DrawBalls
import com.manalejandro.boleto.ui.components.KeyValueRow
import com.manalejandro.boleto.ui.components.LotteryBall
import com.manalejandro.boleto.ui.components.SectionCard
import com.manalejandro.boleto.ui.components.SectionTitle
import com.manalejandro.boleto.ui.components.label
import com.manalejandro.boleto.ui.theme.accent

@Composable
fun HomeScreen(
    state: UiState,
    onSync: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(onGenerate = onGenerate)
        NextDrawCard()
        LatestDrawsCard(state)
        HistoryDataCard(state = state, onSync = onSync)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroCard(onGenerate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4A3FD6), Color(0xFF2A1E8F), Color(0xFF17123F))
                )
            )
    ) {
        // Decorative balls bleeding out of the top-right corner.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-14).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LotteryBall("23", BallStyle.EURO_MAIN, 30.dp)
            LotteryBall("07", BallStyle.EURO_STAR, 36.dp)
            LotteryBall("11", BallStyle.PRIMI_MAIN, 30.dp)
        }
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo_boleto),
                    contentDescription = stringResource(R.string.cd_logo),
                    modifier = Modifier.size(62.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.home_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onGenerate,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF2A1E8F),
                ),
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_quick_play),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Next draw date of both games, computed from their weekly schedule. */
@Composable
private fun NextDrawCard() {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard {
        SectionTitle(Icons.Rounded.CalendarMonth, stringResource(R.string.home_next_draw))
        LotteryGame.entries.forEach { game ->
            KeyValueRow(
                label = game.label(),
                value = IsoDate.formatDisplay(nextDrawDate(game), locale, "EEEE d MMM"),
            )
        }
    }
}

private fun nextDrawDate(game: LotteryGame): String {
    var date = IsoDate.today()
    repeat(7) {
        if (IsoDate.dayOfWeek(date) in game.drawDays) return date
        date = IsoDate.addDays(date, 1)
    }
    return date
}

@Composable
private fun LatestDrawsCard(state: UiState) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard {
        SectionTitle(Icons.Rounded.EmojiEvents, stringResource(R.string.home_latest_draws))
        LotteryGame.entries.forEach { game ->
            val draw = state.draws[game]?.lastOrNull() ?: return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = game.label(),
                        style = MaterialTheme.typography.titleSmall,
                        color = game.accent,
                    )
                    Text(
                        text = IsoDate.formatDisplay(draw.date, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DrawBalls(draw = draw, ballSize = 34.dp)
                draw.jackpot?.let { jackpot ->
                    Text(
                        text = stringResource(R.string.home_jackpot, Formats.euros(jackpot, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDataCard(state: UiState, onSync: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val sync = state.sync
    val running = sync is SyncState.Running
    SectionCard {
        SectionTitle(Icons.Rounded.Storage, stringResource(R.string.home_history_data))
        LotteryGame.entries.forEach { game ->
            val draws = state.draws[game].orEmpty()
            KeyValueRow(
                label = game.label(),
                value = stringResource(R.string.home_draws_count, draws.size),
            )
            val first = draws.firstOrNull()?.date
            val last = draws.lastOrNull()?.date
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

        val lastUpdate = state.lastUpdates.values.filterNotNull().maxOrNull()
        Text(
            text = if (lastUpdate != null) {
                stringResource(R.string.home_last_update, IsoDate.formatTimestamp(lastUpdate, locale))
            } else {
                stringResource(R.string.home_never_updated)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (running) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.home_updating) + " " +
                        (sync as SyncState.Running).game.label() + " · " + sync.fetched,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = onSync,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_update))
        }
        Text(
            text = stringResource(R.string.home_welcome_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
