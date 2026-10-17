package com.manalejandro.boleto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manalejandro.boleto.R
import com.manalejandro.boleto.core.Formats
import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.Boleto
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.domain.GameStats
import com.manalejandro.boleto.domain.ai.AiModel
import com.manalejandro.boleto.domain.ai.ondevice.InsightLabels
import com.manalejandro.boleto.domain.ai.ondevice.InsightPrompt
import com.manalejandro.boleto.domain.ai.ondevice.OnDeviceAiStatus
import com.manalejandro.boleto.ui.InsightState
import com.manalejandro.boleto.ui.OnDeviceAiUi
import com.manalejandro.boleto.ui.components.SectionCard
import com.manalejandro.boleto.ui.components.SectionTitle
import com.manalejandro.boleto.ui.components.label

/**
 * Card that explains the generated ticket in natural language using Gemini
 * Nano on the device. The prompt is grounded with the verified archive, and
 * the whole feature degrades gracefully on phones without AICore.
 */
@Composable
fun InsightSection(
    game: LotteryGame,
    ticket: Boleto,
    stats: GameStats?,
    model: AiModel?,
    aiState: OnDeviceAiUi,
    insight: InsightState?,
    onCheckStatus: (force: Boolean) -> Unit,
    onDownload: () -> Unit,
    onGenerate: (systemPrompt: String, userPrompt: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val gameLabel = game.label()
    val strategyLabel = ticket.strategy.label()

    LaunchedEffect(Unit) { onCheckStatus(false) }

    val systemPrompt = stringResource(R.string.ai_prompt_system)
    val labels = InsightLabels(
        system = systemPrompt,
        gameLine = stringResource(R.string.ai_prompt_game, gameLabel),
        strategyLine = stringResource(R.string.ai_prompt_strategy, strategyLabel),
        betsHeader = stringResource(R.string.ai_prompt_bets),
        betLines = ticket.lines.mapIndexed { index, line ->
            val numbers = InsightPrompt.formatNumbers(line.numbers, line.stars)
            val withRefund = line.refund?.let { refund ->
                stringResource(R.string.ai_prompt_bet_refund, numbers, refund)
            } ?: numbers
            stringResource(R.string.ai_prompt_bet_line, index + 1, withRefund)
        },
        historyLine = stats?.let { summary ->
            stringResource(
                R.string.ai_prompt_history,
                Formats.decimal(summary.totalDraws.toDouble(), locale, 0),
                summary.firstDate?.let { IsoDate.formatDisplay(it, locale, "d MMM yyyy") }.orEmpty(),
                summary.lastDate?.let { IsoDate.formatDisplay(it, locale, "d MMM yyyy") }.orEmpty(),
            )
        }.orEmpty(),
        hotLine = stats?.let {
            stringResource(R.string.ai_prompt_hot, InsightPrompt.formatList(it.hotNumbers))
        }.orEmpty(),
        coldLine = stats?.let {
            stringResource(R.string.ai_prompt_cold, InsightPrompt.formatList(it.coldNumbers))
        }.orEmpty(),
        overdueLine = stats?.let {
            stringResource(R.string.ai_prompt_overdue, InsightPrompt.formatList(it.overdueNumbers))
        }.orEmpty(),
        modelLine = model?.let {
            stringResource(
                R.string.ai_prompt_model,
                Formats.percent(it.holdoutPrecision, locale),
                Formats.percent(it.holdoutBaseline, locale),
            )
        },
        oddsLine = stringResource(
            R.string.ai_prompt_odds,
            Formats.decimal(InsightPrompt.topPrizeOdds(game).toDouble(), locale, 0),
        ),
    )
    val userPrompt = remember(labels) { InsightPrompt.buildUserPrompt(labels) }

    SectionCard(modifier = modifier) {
        SectionTitle(
            icon = Icons.Rounded.AutoAwesome,
            text = stringResource(R.string.ai_insight_title),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.ai_insight_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val status = aiState.status) {
            OnDeviceAiStatus.Unsupported -> Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.ai_insight_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // AICore can be temporarily unavailable right after a device
                // setup or a reset, so offer a manual re-check.
                TextButton(onClick = { onCheckStatus(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_retry))
                }
            }

            OnDeviceAiStatus.Downloadable -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.ai_insight_download_size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_insight_download))
                }
            }

            is OnDeviceAiStatus.Downloading -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (status.bytesDownloaded.toFloat() / status.totalBytes).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = if (status.totalBytes > 0) {
                        stringResource(
                            R.string.ai_insight_downloading,
                            Formats.dataSize(status.bytesDownloaded, locale) + " / " +
                                Formats.dataSize(status.totalBytes, locale),
                        )
                    } else {
                        stringResource(R.string.ai_insight_downloading_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OnDeviceAiStatus.Available -> Unit

            is OnDeviceAiStatus.Failed -> Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.ai_insight_download_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                status.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_insight_download))
                }
            }
        }

        when (insight) {
            is InsightState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.ai_insight_generating),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is InsightState.Ready -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                ) {
                    Text(
                        text = insight.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ai_insight_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onGenerate(systemPrompt, userPrompt) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_insight_generate))
                    }
                }
            }

            is InsightState.Error -> Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.ai_insight_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { onGenerate(systemPrompt, userPrompt) }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_insight_generate))
                }
            }

            null -> if (aiState.status == OnDeviceAiStatus.Available) {
                Button(
                    onClick = { onGenerate(systemPrompt, userPrompt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ai_insight_generate),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
