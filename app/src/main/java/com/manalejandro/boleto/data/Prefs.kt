package com.manalejandro.boleto.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small typed wrapper around SharedPreferences. */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("boleto_prefs", Context.MODE_PRIVATE)

    fun lastUpdate(game: LotteryGame): Long? =
        prefs.getLong(KEY_LAST_UPDATE + game.key, 0L).takeIf { it > 0 }

    fun setLastUpdate(game: LotteryGame, millis: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE + game.key, millis).apply()
    }

    fun ticket(game: LotteryGame): Boleto? {
        val raw = prefs.getString(KEY_TICKET + game.key, null) ?: return null
        return runCatching { parseTicket(game, JSONObject(raw)) }.getOrNull()
    }

    fun setTicket(boleto: Boleto) {
        prefs.edit().putString(KEY_TICKET + boleto.game.key, ticketToJson(boleto).toString()).apply()
    }

    fun modelJson(game: LotteryGame): String? = prefs.getString(KEY_MODEL + game.key, null)

    fun setModelJson(game: LotteryGame, json: String) {
        prefs.edit().putString(KEY_MODEL + game.key, json).apply()
    }

    /** Version of the bundled archive the device has been seeded with. */
    fun dataVersion(): Int = prefs.getInt(KEY_DATA_VERSION, 0)

    fun setDataVersion(version: Int) {
        prefs.edit().putInt(KEY_DATA_VERSION, version).apply()
    }

    companion object {
        private const val KEY_LAST_UPDATE = "last_update_"
        private const val KEY_TICKET = "ticket_"
        private const val KEY_MODEL = "model_"
        private const val KEY_DATA_VERSION = "data_version_"

        fun ticketToJson(boleto: Boleto): JSONObject {
            val lines = JSONArray()
            boleto.lines.forEach { line ->
                lines.put(
                    JSONObject()
                        .put("n", JSONArray(line.numbers))
                        .put("s", JSONArray(line.stars))
                        .put("r", line.refund ?: JSONObject.NULL)
                )
            }
            return JSONObject()
                .put("strategy", boleto.strategy.name)
                .put("createdAt", boleto.createdAt)
                .put("joker", boleto.joker ?: JSONObject.NULL)
                .put("draws", JSONArray(boleto.drawDates))
                .put("lines", lines)
        }

        fun parseTicket(game: LotteryGame, json: JSONObject): Boleto {
            val strategy = runCatching {
                Strategy.valueOf(json.optString("strategy"))
            }.getOrDefault(Strategy.RANDOM)
            val linesJson = json.optJSONArray("lines") ?: JSONArray()
            val lines = (0 until linesJson.length()).mapNotNull { index ->
                val line = linesJson.optJSONObject(index) ?: return@mapNotNull null
                val numbers = line.optJSONArray("n")?.toIntList().orEmpty()
                if (numbers.isEmpty()) return@mapNotNull null
                TicketLine(
                    numbers = numbers,
                    stars = line.optJSONArray("s")?.toIntList().orEmpty(),
                    refund = if (line.isNull("r")) null else line.optInt("r"),
                )
            }
            val drawsJson = json.optJSONArray("draws")
            val drawDates = (0 until (drawsJson?.length() ?: 0)).mapNotNull { index ->
                drawsJson?.optString(index)?.takeIf { it.length == 10 }
            }
            return Boleto(
                game = game,
                lines = lines,
                strategy = strategy,
                createdAt = json.optString("createdAt"),
                joker = if (json.isNull("joker")) {
                    null
                } else {
                    LaeApi.parseJoker(json.optString("joker"))
                },
                drawDates = drawDates,
            )
        }

        private fun JSONArray.toIntList(): List<Int> =
            (0 until length()).map { optInt(it) }
    }
}
