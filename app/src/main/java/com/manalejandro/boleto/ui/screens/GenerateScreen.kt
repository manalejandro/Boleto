package com.manalejandro.boleto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manalejandro.boleto.R
import com.manalejandro.boleto.core.Formats
import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.Boleto
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.data.Strategy
import com.manalejandro.boleto.domain.CheckOutcome
import com.manalejandro.boleto.ui.ModelState
import com.manalejandro.boleto.ui.UiState
import com.manalejandro.boleto.ui.components.GameSegmentedSelector
import com.manalejandro.boleto.ui.components.KeyValueRow
import com.manalejandro.boleto.ui.components.SectionCard
import com.manalejandro.boleto.ui.components.SectionTitle
import com.manalejandro.boleto.ui.components.TicketCard
import com.manalejandro.boleto.ui.components.icon
import com.manalejandro.boleto.ui.components.label
import com.manalejandro.boleto.ui.components.prizeLabel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateScreen(
    state: UiState,
    onGenerate: (LotteryGame, Strategy, Int) -> Unit,
    onCheck: (LotteryGame) -> Unit,
    onCompare: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var game by rememberSaveable { mutableStateOf(LotteryGame.EUROMILLIONS) }
    var strategy by rememberSaveable { mutableStateOf(Strategy.RANDOM) }
    var bets by rememberSaveable { mutableIntStateOf(1) }
    val locale = LocalConfiguration.current.locales[0]

    val maxBets = if (game == LotteryGame.PRIMITIVA) 8 else 5
    val ticket = state.tickets[game]
    val modelState = state.models[game]
    val check = state.checks[game]
    val drawsCount = state.draws[game].orEmpty().size
    val gameLabel = game.label()

    LaunchedEffect(game) {
        if (bets > maxBets) bets = maxBets
    }

    val ticketTitle = stringResource(R.string.play_ticket_title)
    val betLabels = (1..8).map { stringResource(R.string.play_bet_number, it) }
    val shareText = ticket?.let { buildShareText(it, ticketTitle, gameLabel, betLabels) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameSegmentedSelector(selected = game, onSelect = { game = it })

        SectionCard {
            SectionTitle(Icons.Rounded.Tune, stringResource(R.string.play_strategy))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Strategy.entries.forEach { entry ->
                    FilterChip(
                        selected = strategy == entry,
                        onClick = { strategy = entry },
                        label = { Text(entry.label()) },
                        leadingIcon = {
                            Icon(
                                imageVector = entry.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.play_bets),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { if (bets > 1) bets-- },
                    enabled = bets > 1,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = bets.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 36.dp),
                )
                FilledTonalIconButton(
                    onClick = { if (bets < maxBets) bets++ },
                    enabled = bets < maxBets,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (strategy == Strategy.AI) {
                AiStatusCard(modelState = modelState, drawsCount = drawsCount, locale = locale)
            }

            Button(
                onClick = { onGenerate(game, strategy, bets) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = if (strategy == Strategy.AI) Icons.Rounded.AutoAwesome else Icons.Rounded.Casino,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        if (ticket == null) R.string.play_generate else R.string.play_regenerate
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (ticket != null) {
            TicketCard(boleto = ticket)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { shareText?.let(onShare) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.play_share))
                }
                OutlinedButton(
                    onClick = { shareText?.let(onCopy) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.play_copy))
                }
            }

            OutlinedButton(
                onClick = { onCheck(game) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play_check))
            }

            check?.let { outcome ->
                CheckResultCard(outcome = outcome, game = game, locale = locale)
            }

            TextButton(
                onClick = onCompare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play_compare))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AiStatusCard(modelState: ModelState?, drawsCount: Int, locale: Locale) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.play_ai_info_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            when (modelState) {
                is ModelState.Ready -> {
                    Text(
                        text = stringResource(
                            R.string.play_ai_trained,
                            modelState.model.trainedDraws,
                            Formats.percent(modelState.model.holdoutPrecision, locale),
                            Formats.percent(modelState.model.holdoutBaseline, locale),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.play_ai_info_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ModelState.Training -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.play_ai_training),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is ModelState.Unavailable -> Text(
                    text = stringResource(R.string.play_ai_unavailable, drawsCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    text = stringResource(R.string.play_ai_training),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CheckResultCard(outcome: CheckOutcome, game: LotteryGame, locale: Locale) {
    SectionCard {
        SectionTitle(Icons.Rounded.CheckCircle, stringResource(R.string.play_check))
        Text(
            text = stringResource(R.string.play_check_result, outcome.drawsChecked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (outcome.categoryCounts.isEmpty()) {
            Text(
                text = stringResource(R.string.play_no_prizes),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            outcome.categoryCounts.entries.sortedBy { it.key }.forEach { (rank, count) ->
                KeyValueRow(
                    label = prizeLabel(game, rank),
                    value = stringResource(R.string.stats_times, count),
                )
            }
            outcome.bestRank?.let { rank ->
                val date = outcome.bestDate?.let { IsoDate.formatDisplay(it, locale) }.orEmpty()
                KeyValueRow(
                    label = stringResource(R.string.compare_best),
                    value = listOf(prizeLabel(game, rank), date)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                )
            }
        }
    }
}

private fun buildShareText(
    boleto: Boleto,
    title: String,
    gameLabel: String,
    betLabels: List<String>,
): String = buildString {
    append(title).append(" · ").append(gameLabel).append('\n')
    boleto.lines.forEachIndexed { index, line ->
        append(betLabels.getOrElse(index) { "${index + 1}" }).append(": ")
        append(line.numbers.joinToString(" ") { it.toString().padStart(2, '0') })
        if (line.stars.isNotEmpty()) {
            append("  ")
            append(line.stars.joinToString(" ") { "★" + it.toString().padStart(2, '0') })
        }
        line.refund?.let { append("  R:").append(it) }
        append('\n')
    }
}.trim()
