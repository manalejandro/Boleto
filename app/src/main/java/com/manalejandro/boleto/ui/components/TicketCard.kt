package com.manalejandro.boleto.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manalejandro.boleto.R
import com.manalejandro.boleto.core.Formats
import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.Boleto
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.domain.TicketCost
import com.manalejandro.boleto.ui.theme.TicketInk
import com.manalejandro.boleto.ui.theme.TicketInkSoft
import com.manalejandro.boleto.ui.theme.TicketPaper
import com.manalejandro.boleto.ui.theme.TicketPaperEdge
import com.manalejandro.boleto.ui.theme.accent
import kotlin.random.Random

/**
 * The generated ticket, printed on "paper": perforation, barcode and the
 * balls of every bet. Deliberately keeps the paper palette in dark mode too.
 */
@Composable
fun TicketCard(boleto: Boleto, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val locale = LocalConfiguration.current.locales[0]
    val appearance = remember(boleto) { Animatable(0f) }
    LaunchedEffect(boleto) {
        appearance.snapTo(0f)
        appearance.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 320f))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appearance.value
                val scale = 0.96f + 0.04f * appearance.value
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(20.dp),
        color = TicketPaper,
        border = BorderStroke(1.dp, TicketPaperEdge),
        shadowElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.play_ticket_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TicketInk,
                        letterSpacing = 6.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = boleto.game.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TicketInkSoft,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = IsoDate.formatDisplay(boleto.createdAt, locale, "d MMM yyyy"),
                        style = MaterialTheme.typography.bodySmall,
                        color = TicketInkSoft,
                    )
                    Text(
                        text = boleto.strategy.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = boleto.game.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            PerforationRow(background)

            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                boleto.lines.forEachIndexed { index, line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.play_bet_number, index + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = TicketInkSoft,
                            modifier = Modifier.width(58.dp),
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            line.numbers.forEach { number ->
                                LotteryBall(
                                    text = number.toString().padStart(2, '0'),
                                    style = styleFor(boleto.game),
                                    size = 28.dp,
                                )
                            }
                            line.stars.forEach { star ->
                                LotteryBall(
                                    text = star.toString().padStart(2, '0'),
                                    style = BallStyle.EURO_STAR,
                                    size = 28.dp,
                                )
                            }
                            line.refund?.let { refund -> RefundBadge(refund, compact = true) }
                        }
                    }
                }

                boleto.joker?.let { joker ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.play_joker_code),
                            style = MaterialTheme.typography.labelSmall,
                            color = TicketInkSoft,
                            modifier = Modifier.width(58.dp),
                        )
                        Text(
                            text = joker,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp,
                            color = TicketInk,
                        )
                    }
                }
            }

            PerforationRow(background)

            val totalCents = TicketCost.totalCents(
                game = boleto.game,
                bets = boleto.lines.size,
                draws = boleto.drawDates.size.coerceAtLeast(1),
                withJoker = boleto.joker != null,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (boleto.drawDates.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.ticket_draws,
                            boleto.drawDates.joinToString(" · ") {
                                IsoDate.formatDisplay(it, locale, "d MMM")
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TicketInkSoft,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = Formats.eurosFromCents(totalCents.toLong(), locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = TicketInk,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Barcode(
                    seed = boleto.lines.joinToString("|") { it.numbers.joinToString(",") }.hashCode(),
                    color = TicketInk,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = boleto.game.key.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TicketInkSoft,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

@Composable
private fun PerforationRow(background: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val middle = size.height / 2f
        drawLine(
            color = TicketPaperEdge,
            start = Offset(16.dp.toPx(), middle),
            end = Offset(size.width - 16.dp.toPx(), middle),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f)),
        )
        drawCircle(background, radius = 9.dp.toPx(), center = Offset(0f, middle))
        drawCircle(background, radius = 9.dp.toPx(), center = Offset(size.width, middle))
    }
}

@Composable
private fun Barcode(seed: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val random = Random(seed)
        var x = 0f
        while (x < size.width - 2.dp.toPx()) {
            val barWidth = (1 + random.nextInt(3)).dp.toPx()
            val gap = (1 + random.nextInt(3)).dp.toPx()
            val barHeight = size.height * (0.65f + random.nextFloat() * 0.35f)
            drawRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
            x += barWidth + gap
        }
    }
}
