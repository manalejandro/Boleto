package com.manalejandro.boleto.ui.theme

import androidx.compose.ui.graphics.Color
import com.manalejandro.boleto.data.LotteryGame

// ---------------------------------------------------------------------------
// Brand palette
// ---------------------------------------------------------------------------

// Light
val PrimaryLight = Color(0xFF4438CA)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE3DFFF)
val OnPrimaryContainerLight = Color(0xFF12005E)
val SecondaryLight = Color(0xFF9A6B00)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFFFDF9E)
val OnSecondaryContainerLight = Color(0xFF301F00)
val TertiaryLight = Color(0xFF10693F)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFB8F2CF)
val OnTertiaryContainerLight = Color(0xFF00210F)
val BackgroundLight = Color(0xFFFAF8FF)
val OnBackgroundLight = Color(0xFF1B1B25)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF1B1B25)
val SurfaceVariantLight = Color(0xFFE8E4F2)
val OnSurfaceVariantLight = Color(0xFF474459)
val OutlineLight = Color(0xFF78748B)

// Dark
val PrimaryDark = Color(0xFFC2BCFF)
val OnPrimaryDark = Color(0xFF2311A8)
val PrimaryContainerDark = Color(0xFF3A2FBE)
val OnPrimaryContainerDark = Color(0xFFE3DFFF)
val SecondaryDark = Color(0xFFF5C542)
val OnSecondaryDark = Color(0xFF3F2E00)
val SecondaryContainerDark = Color(0xFF5B4300)
val OnSecondaryContainerDark = Color(0xFFFFDF9E)
val TertiaryDark = Color(0xFF6FD9A0)
val OnTertiaryDark = Color(0xFF00391F)
val TertiaryContainerDark = Color(0xFF00522F)
val OnTertiaryContainerDark = Color(0xFFB8F2CF)
val BackgroundDark = Color(0xFF12111C)
val OnBackgroundDark = Color(0xFFE5E1EE)
val SurfaceDark = Color(0xFF1B1A26)
val OnSurfaceDark = Color(0xFFE5E1EE)
val SurfaceVariantDark = Color(0xFF47455A)
val OnSurfaceVariantDark = Color(0xFFC9C5D8)
val OutlineDark = Color(0xFF938FA6)

// ---------------------------------------------------------------------------
// Game identity
// ---------------------------------------------------------------------------

val EuroMillionsAccent = Color(0xFF2B4FE0)
val EuroMillionsStar = Color(0xFFF2B705)
val PrimitivaAccent = Color(0xFF0F7B4F)
val PrimitivaExtra = Color(0xFF5A6072)
val RefundRed = Color(0xFFC62828)

val LotteryGame.accent: Color
    get() = when (this) {
        LotteryGame.EUROMILLIONS -> EuroMillionsAccent
        LotteryGame.PRIMITIVA -> PrimitivaAccent
    }

// ---------------------------------------------------------------------------
// Ticket (paper) palette — deliberately identical in both themes: a real
// ticket is printed on paper.
// ---------------------------------------------------------------------------

val TicketPaper = Color(0xFFFFFCF0)
val TicketPaperEdge = Color(0xFFE8E0C8)
val TicketInk = Color(0xFF23202F)
val TicketInkSoft = Color(0xFF6B6678)
