package com.manalejandro.boleto.data

import com.manalejandro.boleto.core.Http
import org.json.JSONArray

/**
 * Client for the official Loterías y Apuestas del Estado (LAE) web service.
 *
 * The endpoint returns the most recent [MAX_RESULTS] draws inside the given
 * range, so callers must walk the calendar in windows (see
 * [com.manalejandro.boleto.data.LotteryRepository.sync]).
 */
class LaeApi {

    @Throws(Exception::class)
    fun fetchRange(game: LotteryGame, startIso: String, endIso: String): List<Draw> {
        val url = buildString {
            append(BASE_URL)
            append("?game_id=").append(game.laeGameId)
            append("&celebrados=true")
            append("&fechaInicioInclusiva=").append(startIso.replace("-", ""))
            append("&fechaFinInclusiva=").append(endIso.replace("-", ""))
        }
        val body = Http.get(url, BROWSER_HEADERS)
        val array = JSONArray(body)
        val draws = ArrayList<Draw>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val date = item.optString("fecha_sorteo").take(10)
            if (date.length != 10) continue
            val parsed = parseCombinacion(game, item.optString("combinacion")) ?: continue
            val jackpot = item.optString("premio_bote").trim().toLongOrNull()?.takeIf { it > 0 }
            draws += Draw(
                game = game,
                date = date,
                numbers = parsed.numbers,
                stars = parsed.stars,
                complementary = parsed.complementary,
                refund = parsed.refund,
                jackpot = jackpot,
            )
        }
        return draws
    }

    /** Result of parsing the "combinacion" string of a LAE draw. */
    data class ParsedCombinacion(
        val numbers: List<Int>,
        val stars: List<Int>,
        val complementary: Int?,
        val refund: Int?,
    )

    companion object {
        private const val BASE_URL = "https://www.loteriasyapuestas.es/servicios/buscadorSorteos"

        /** Hard limit of draws returned per request by the LAE endpoint. */
        const val MAX_RESULTS = 115

        val BROWSER_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "es-ES,es;q=0.9,en;q=0.8",
            "Referer" to "https://www.loteriasyapuestas.es/es/resultados",
            "Origin" to "https://www.loteriasyapuestas.es",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
        )

        private val NUMBER_REGEX = Regex("\\d+")
        private val COMPLEMENTARY_REGEX = Regex("C\\(\\s*(\\d*)\\s*\\)")
        private val REFUND_REGEX = Regex("R\\(\\s*(\\d*)\\s*\\)")

        /**
         * Parses strings such as:
         *  - La Primitiva: `"04 - 07 - 08 - 22 - 40 - 49 C(44) R(2)"`
         *  - EuroMillones: `" 01 - 07 - 10 - 26 - 34 - 02 - 04"`
         */
        fun parseCombinacion(game: LotteryGame, raw: String?): ParsedCombinacion? {
            if (raw.isNullOrBlank()) return null
            val text = raw.replace('\u00A0', ' ').trim()
            val complementary = COMPLEMENTARY_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
            val refund = REFUND_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
            val cleaned = REFUND_REGEX.replace(COMPLEMENTARY_REGEX.replace(text, ""), "")
            val numbers = NUMBER_REGEX.findAll(cleaned).map { it.value.toInt() }.toList()
            return when {
                game.mainCount == 6 && numbers.size >= 6 -> ParsedCombinacion(
                    numbers = numbers.take(6).sorted(),
                    stars = emptyList(),
                    complementary = complementary,
                    refund = refund,
                )
                game.mainCount == 5 && numbers.size >= 7 -> ParsedCombinacion(
                    numbers = numbers.take(5).sorted(),
                    stars = numbers.drop(5).take(2).sorted(),
                    complementary = null,
                    refund = null,
                )
                else -> null
            }
        }
    }
}
