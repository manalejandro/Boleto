package com.manalejandro.boleto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manalejandro.boleto.data.Draw
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.ui.theme.PrimitivaExtra
import com.manalejandro.boleto.ui.theme.RefundRed

/** Visual style of a lottery ball, matching the real game artwork. */
enum class BallStyle {
    EURO_MAIN,
    EURO_STAR,
    PRIMI_MAIN,
    PRIMI_EXTRA,
}

private data class BallPalette(val top: Color, val bottom: Color, val text: Color)

private fun paletteFor(style: BallStyle): BallPalette = when (style) {
    BallStyle.EURO_MAIN -> BallPalette(Color(0xFFFFFFFF), Color(0xFFD8E0F6), Color(0xFF1B2F8A))
    BallStyle.EURO_STAR -> BallPalette(Color(0xFFFFE9A3), Color(0xFFF2B705), Color(0xFF4A3200))
    BallStyle.PRIMI_MAIN -> BallPalette(Color(0xFF3FBF85), Color(0xFF0C6B44), Color.White)
    BallStyle.PRIMI_EXTRA -> BallPalette(Color(0xFF8A93A8), Color(0xFF545A6B), Color.White)
}

fun styleFor(game: LotteryGame): BallStyle = when (game) {
    LotteryGame.EUROMILLIONS -> BallStyle.EURO_MAIN
    LotteryGame.PRIMITIVA -> BallStyle.PRIMI_MAIN
}

/** A glossy lottery ball. */
@Composable
fun LotteryBall(
    text: String,
    style: BallStyle,
    size: Dp = 38.dp,
    modifier: Modifier = Modifier,
) {
    val palette = paletteFor(style)
    val density = LocalDensity.current
    val radius = with(density) { (size / 2).toPx() }
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(palette.top, palette.bottom),
                    center = Offset(radius * 0.65f, radius * 0.55f),
                    radius = radius * 1.45f,
                ),
                shape = CircleShape,
            )
            .border(1.dp, Color.Black.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = palette.text,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f / density.fontScale).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The refund digit of La Primitiva, rendered as a small badge. */
@Composable
fun RefundBadge(value: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .background(RefundRed, RoundedCornerShape(if (compact) 6.dp else 8.dp))
            .padding(horizontal = if (compact) 5.dp else 8.dp, vertical = if (compact) 1.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "R:$value",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        )
    }
}

private fun pad(number: Int) = number.toString().padStart(2, '0')

/** The winning combination of a draw: numbers, stars, complementary and refund. */
@Composable
fun DrawBalls(
    draw: Draw,
    modifier: Modifier = Modifier,
    ballSize: Dp = 36.dp,
    showExtras: Boolean = true,
) {
    val game = draw.game
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (ballSize > 30.dp) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        draw.numbers.forEach { number ->
            LotteryBall(pad(number), styleFor(game), ballSize)
        }
        if (showExtras) {
            draw.stars.forEach { star ->
                LotteryBall(pad(star), BallStyle.EURO_STAR, ballSize)
            }
            draw.complementary?.let { complementary ->
                LotteryBall(pad(complementary), BallStyle.PRIMI_EXTRA, ballSize * 0.9f)
            }
            draw.refund?.let { refund ->
                RefundBadge(refund, compact = ballSize < 32.dp)
            }
        }
    }
}

/** A plain row of balls for a ticket line. */
@Composable
fun NumberBalls(
    numbers: List<Int>,
    style: BallStyle,
    modifier: Modifier = Modifier,
    ballSize: Dp = 32.dp,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        numbers.forEach { number ->
            LotteryBall(pad(number), style, ballSize)
        }
    }
}

/** Small colored ball used inside statistics chips. */
@Composable
fun StatBall(number: Int, style: BallStyle, size: Dp = 34.dp) {
    LotteryBall(pad(number), style, size)
}

/** Extra colour used for the complementary number ball. */
val ComplementaryColor = PrimitivaExtra
