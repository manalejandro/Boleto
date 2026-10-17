package com.manalejandro.boleto.data

/**
 * The two games supported by the app. All game rules live here so the rest of
 * the code stays generic.
 */
enum class LotteryGame(
    val key: String,
    /** game_id used by the official LAE web service. */
    val laeGameId: String,
    /** Bundled asset with the full history. */
    val assetName: String,
    /** How many main numbers a ticket has to pick. */
    val mainCount: Int,
    /** Highest main number (numbers are 1..mainMax). */
    val mainMax: Int,
    /** Extra "star" numbers (EuroMillions only). */
    val starCount: Int,
    val starMax: Int,
    /** La Primitiva draws a complementary number used for the 2nd category. */
    val hasComplementary: Boolean,
    /** La Primitiva draws a refund digit (0..9). */
    val hasRefund: Boolean,
    /** Earliest date available in the official archive. */
    val firstDate: String,
    /** ISO days of week the game is drawn (1 = Monday … 7 = Sunday). */
    val drawDays: Set<Int>,
) {
    EUROMILLIONS(
        key = "emil",
        laeGameId = "EMIL",
        assetName = "history_emil.csv",
        mainCount = 5,
        mainMax = 50,
        starCount = 2,
        starMax = 12,
        hasComplementary = false,
        hasRefund = false,
        firstDate = "2004-02-13",
        drawDays = setOf(2, 5), // Tuesday & Friday
    ),
    PRIMITIVA(
        key = "lapr",
        laeGameId = "LAPR",
        assetName = "history_lapr.csv",
        mainCount = 6,
        mainMax = 49,
        starCount = 0,
        starMax = 0,
        hasComplementary = true,
        hasRefund = true,
        firstDate = "1985-10-17",
        drawDays = setOf(1, 4, 6), // Monday, Thursday & Saturday
    );

    companion object {
        fun fromKey(key: String): LotteryGame? = entries.firstOrNull { it.key == key }
    }
}

/** A single historical draw. */
data class Draw(
    val game: LotteryGame,
    /** ISO date, yyyy-MM-dd. */
    val date: String,
    /** Main numbers, ascending. */
    val numbers: List<Int>,
    /** Star numbers (EuroMillions), ascending. */
    val stars: List<Int> = emptyList(),
    val complementary: Int? = null,
    val refund: Int? = null,
    /** Estimated jackpot for the draw, in euros. */
    val jackpot: Long? = null,
)

/** One bet inside a ticket. */
data class TicketLine(
    val numbers: List<Int>,
    val stars: List<Int> = emptyList(),
    val refund: Int? = null,
)

/** A generated ticket ready to be played. */
data class Boleto(
    val game: LotteryGame,
    val lines: List<TicketLine>,
    val strategy: Strategy,
    val createdAt: String,
)

/** How the numbers are picked. */
enum class Strategy {
    RANDOM,
    HOT,
    COLD,
    BALANCED,
    AI,
}
