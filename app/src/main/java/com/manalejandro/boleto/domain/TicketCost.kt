package com.manalejandro.boleto.domain

import com.manalejandro.boleto.data.LotteryGame

/**
 * Official cost of a ticket, in euro cents.
 *
 * Prices checked against SELAE in 2026: La Primitiva 1 € per bet, Euromillones
 * 2.50 € per bet (includes the associated "El Millón" game) and the Joker 1 €
 * per draw played.
 */
object TicketCost {

    fun totalCents(game: LotteryGame, bets: Int, draws: Int, withJoker: Boolean): Int {
        val betsTotal = game.betPriceCents * bets.coerceAtLeast(0) * draws.coerceAtLeast(0)
        val jokerTotal = if (withJoker && game.hasJoker) {
            game.jokerPriceCents * draws.coerceAtLeast(0)
        } else {
            0
        }
        return betsTotal + jokerTotal
    }
}
