package com.manalejandro.boleto

import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.LotteryGame
import com.manalejandro.boleto.domain.DrawSchedule
import com.manalejandro.boleto.domain.TicketCost
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Draw calendar and official prices. */
class DrawsAndCostTest {

    // 2026-10-19 is a Monday, 2026-10-25 a Sunday.
    private val mondayMorning = millisFor("2026-10-19", 10)
    private val mondayNight = millisFor("2026-10-19", 22)
    private val sundayMorning = millisFor("2026-10-25", 10)

    @Test
    fun `la primitiva draws are monday thursday and saturday`() {
        assertEquals(
            listOf("2026-10-19", "2026-10-22", "2026-10-24"),
            DrawSchedule.nextDraws(LotteryGame.PRIMITIVA, mondayMorning, 3),
        )
    }

    @Test
    fun `euro millions draws are tuesday and friday`() {
        assertEquals(
            listOf("2026-10-20", "2026-10-23", "2026-10-27"),
            DrawSchedule.nextDraws(LotteryGame.EUROMILLIONS, mondayMorning, 3),
        )
    }

    @Test
    fun `today does not count once the draw has started`() {
        // At 22:00 Monday's draw has already taken place.
        assertEquals(
            listOf("2026-10-22"),
            DrawSchedule.nextDraws(LotteryGame.PRIMITIVA, mondayNight, 1),
        )
    }

    @Test
    fun `remaining draws of the week follow the official weekly option`() {
        assertEquals(3, DrawSchedule.remainingThisWeek(LotteryGame.PRIMITIVA, mondayMorning))
        assertEquals(2, DrawSchedule.remainingThisWeek(LotteryGame.EUROMILLIONS, mondayMorning))
        // Sunday: no draw left this week, the next one is Monday.
        assertEquals(1, DrawSchedule.remainingThisWeek(LotteryGame.PRIMITIVA, sundayMorning))
    }

    @Test
    fun `ticket cost uses the official prices`() {
        // La Primitiva: 1 € per bet, Joker 1 € per draw.
        assertEquals(100, TicketCost.totalCents(LotteryGame.PRIMITIVA, 1, 1, false))
        assertEquals(300, TicketCost.totalCents(LotteryGame.PRIMITIVA, 3, 1, false))
        assertEquals(1200, TicketCost.totalCents(LotteryGame.PRIMITIVA, 3, 3, true))
        // EuroMillones: 2,50 € per bet, no Joker available.
        assertEquals(250, TicketCost.totalCents(LotteryGame.EUROMILLIONS, 1, 1, false))
        assertEquals(500, TicketCost.totalCents(LotteryGame.EUROMILLIONS, 1, 2, false))
        assertEquals(500, TicketCost.totalCents(LotteryGame.EUROMILLIONS, 1, 2, true))
    }

    private fun millisFor(iso: String, hour: Int): Long {
        val calendar = Calendar.getInstance().apply {
            time = requireNotNull(IsoDate.parse(iso))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        return calendar.timeInMillis
    }
}
