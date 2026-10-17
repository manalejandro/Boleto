package com.manalejandro.boleto.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Small date helpers around the ISO-8601 day format used across the app
 * (yyyy-MM-dd). java.time is avoided on purpose: the app supports API 24
 * without core library desugaring.
 */
object IsoDate {

    const val ISO_PATTERN = "yyyy-MM-dd"

    fun today(): String = formatDate(Date())

    fun parse(iso: String): Date? = try {
        SimpleDateFormat(ISO_PATTERN, Locale.US).apply { isLenient = false }.parse(iso)
    } catch (_: Exception) {
        null
    }

    fun formatDate(date: Date): String = SimpleDateFormat(ISO_PATTERN, Locale.US).format(date)

    /** ISO day of week: 1 = Monday … 7 = Sunday. */
    fun dayOfWeek(iso: String): Int {
        val date = parse(iso) ?: return 1
        val calendar = Calendar.getInstance().apply { time = date }
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    /** 1-based month. */
    fun month(iso: String): Int {
        val date = parse(iso) ?: return 1
        val calendar = Calendar.getInstance().apply { time = date }
        return calendar.get(Calendar.MONTH) + 1
    }

    fun addDays(iso: String, days: Int): String {
        val date = parse(iso) ?: return iso
        val calendar = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_MONTH, days)
        }
        return formatDate(calendar.time)
    }

    /** Human readable date, e.g. "Sat, 12 Oct 2026". */
    fun formatDisplay(iso: String, locale: Locale, pattern: String = "EEE, d MMM yyyy"): String {
        val date = parse(iso) ?: return iso
        val text = SimpleDateFormat(pattern, locale).format(date)
        return text.replaceFirstChar { it.titlecase(locale) }
    }

    /** Human readable timestamp for "last update" labels. */
    fun formatTimestamp(millis: Long, locale: Locale, pattern: String = "d MMM yyyy · HH:mm"): String {
        val text = SimpleDateFormat(pattern, locale).format(Date(millis))
        return text.replaceFirstChar { it.titlecase(locale) }
    }
}
