package com.manalejandro.boleto.domain

import com.manalejandro.boleto.core.IsoDate
import com.manalejandro.boleto.data.LotteryGame
import java.util.Calendar

/**
 * Draw calendar of the games.
 *
 * Both games are drawn at 21:30 (Spanish peninsular time). The app simply uses
 * the device clock: a draw that has not started yet can still be played, so
 * "today" only counts while the draw has not taken place.
 */
object DrawSchedule {

    /** Draws start at 21:30; from 21:00 on, today's draw is considered played. */
    private const val DRAW_CUTOFF_HOUR = 21

    /**
     * The next [count] draw dates (ISO) from [now], chronological.
     *
     * The official rules allow playing the next draw or the draws remaining in
     * the current week, so [count] is normally small.
     */
    fun nextDraws(game: LotteryGame, now: Long = System.currentTimeMillis(), count: Int = 1): List<String> {
        val dates = ArrayList<String>(count)
        var calendar = today(now)
        var guard = 0
        while (dates.size < count && guard++ < 60) {
            val iso = IsoDate.formatDate(calendar.time)
            val isToday = IsoDate.formatDate(today(now).time) == iso
            val alreadyDrawn = isToday && calendar.get(Calendar.HOUR_OF_DAY) >= DRAW_CUTOFF_HOUR
            if (IsoDate.dayOfWeek(iso) in game.drawDays && !alreadyDrawn) {
                dates += iso
            }
            calendar = nextDay(calendar)
        }
        return dates
    }

    /**
     * Draws still to come this week (until Sunday), which is what the official
     * "sorteo semanal" covers. Always at least 1: the next draw.
     */
    fun remainingThisWeek(game: LotteryGame, now: Long = System.currentTimeMillis()): Int {
        val calendar = today(now)
        val daysToSunday = (Calendar.SUNDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
        var count = 0
        var cursor = today(now)
        for (offset in 0..daysToSunday) {
            val iso = IsoDate.formatDate(cursor.time)
            val isToday = offset == 0
            val alreadyDrawn = isToday && cursor.get(Calendar.HOUR_OF_DAY) >= DRAW_CUTOFF_HOUR
            if (IsoDate.dayOfWeek(iso) in game.drawDays && !alreadyDrawn) count++
            cursor = nextDay(cursor)
        }
        return count.coerceAtLeast(1)
    }

    private fun today(now: Long): Calendar = Calendar.getInstance().apply { timeInMillis = now }

    private fun nextDay(calendar: Calendar): Calendar =
        (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
}
