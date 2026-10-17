package com.manalejandro.boleto.data

import com.manalejandro.boleto.core.Http
import org.json.JSONArray

/**
 * Fallback source for the EuroMillions history (used only when the official
 * LAE service is unreachable). Returns the complete archive in one request.
 */
class EuroMillionsApi {

    @Throws(Exception::class)
    fun fetchAll(): List<Draw> {
        val body = Http.get(URL, mapOf("User-Agent" to "Boleto-Android/1.0"), timeoutMs = 45_000)
        val array = JSONArray(body)
        val draws = ArrayList<Draw>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val date = item.optString("date").take(10)
            if (date.length != 10) continue
            val numbers = item.optJSONArray("numbers")?.toIntList().orEmpty().sorted()
            val stars = item.optJSONArray("stars")?.toIntList().orEmpty().sorted()
            if (numbers.size < 5 || stars.size < 2) continue
            draws += Draw(
                game = LotteryGame.EUROMILLIONS,
                date = date,
                numbers = numbers.take(5),
                stars = stars.take(2),
            )
        }
        return draws
    }

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).mapNotNull { index -> optString(index).trim().toIntOrNull() }

    companion object {
        private const val URL = "https://euromillions.api.pedromealha.dev/v1/draws"
    }
}
