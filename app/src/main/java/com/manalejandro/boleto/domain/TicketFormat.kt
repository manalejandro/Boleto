package com.manalejandro.boleto.domain

/**
 * Text formatting shared by the share text and the prompt sent to the
 * on-device model, so the numbers always look the same.
 */
object TicketFormat {

    /** "04 07 08 22 40 49" plus "★03 ★11" for EuroMillones. */
    fun numbers(numbers: List<Int>, stars: List<Int> = emptyList()): String = buildString {
        append(numbers.joinToString(" ") { it.toString().padStart(2, '0') })
        if (stars.isNotEmpty()) {
            append(' ')
            append(stars.joinToString(" ") { "★" + it.toString().padStart(2, '0') })
        }
    }

    /** "38, 39, 01, 45, 41" */
    fun list(numbers: List<Int>, count: Int = 5): String =
        numbers.take(count).joinToString(", ") { it.toString().padStart(2, '0') }
}
