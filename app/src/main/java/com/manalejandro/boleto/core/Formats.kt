package com.manalejandro.boleto.core

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Locale-aware number formatting helpers. */
object Formats {

    /** Euro amounts without decimals, e.g. "53.000.000 €" / "€53,000,000". */
    fun euros(amount: Long, locale: Locale): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = Currency.getInstance("EUR")
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    /** Percentage with the given number of decimals, e.g. "12,4 %". */
    fun percent(value: Double, locale: Locale, decimals: Int = 1): String {
        val format = NumberFormat.getPercentInstance(locale)
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        return format.format(value)
    }

    /** Decimal number, e.g. "1,25". */
    fun decimal(value: Double, locale: Locale, decimals: Int = 2): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        return format.format(value)
    }

    /** Human readable data size, e.g. "1,8 GB" or "512 MB". */
    fun dataSize(bytes: Long, locale: Locale): String {
        val gigabytes = bytes / 1_073_741_824.0
        return if (gigabytes >= 1.0) {
            decimal(gigabytes, locale, 1) + " GB"
        } else {
            decimal(bytes / 1_048_576.0, locale, 0) + " MB"
        }
    }

    /** Euro amounts from cents, e.g. "2,50 €" or "12 €". */
    fun eurosFromCents(cents: Long, locale: Locale): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = Currency.getInstance("EUR")
        val decimals = if (cents % 100L == 0L) 0 else 2
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        return format.format(cents / 100.0)
    }
}
