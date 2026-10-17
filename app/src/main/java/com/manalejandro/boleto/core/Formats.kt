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
}
