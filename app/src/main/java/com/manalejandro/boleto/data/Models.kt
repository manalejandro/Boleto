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
    /** Associated Joker game: a 7-digit code, 1 € extra per ticket (La Primitiva). */
    val hasJoker: Boolean,
    /** Official price of one bet, in euro cents. */
    val betPriceCents: Int,
    /** Official price of the Joker per draw, in euro cents (0 = no Joker). */
    val jokerPriceCents: Int,
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
        hasJoker = false,
        betPriceCents = 250, // includes the associated "El Millón" game
        jokerPriceCents = 0,
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
        hasJoker = true,
        betPriceCents = 100,
        jokerPriceCents = 100,
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
    /** Winning Joker code of the draw (7 digits), La Primitiva only. */
    val joker: String? = null,
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
    /** Joker code of the ticket (7 digits), La Primitiva only. */
    val joker: String? = null,
    /** Draws the ticket takes part in (ISO dates, chronological). */
    val drawDates: List<String> = emptyList(),
)

/** How the numbers are picked. */
enum class Strategy {
    /** Uniform chance: every combination is equally likely. */
    RANDOM,

    /** Favours the most frequent numbers of the last 50 draws. */
    HOT,

    /** Favours the numbers that went longest without appearing. */
    COLD,

    /** Mix of recent-hot and overdue numbers, filled with uniform picks. */
    BALANCED,

    /**
     * Bayesian frequency estimate: recency-weighted appearance counts
     * (exponential decay) smoothed with a symmetric Dirichlet prior.
     */
    BAYES,

    /**
     * First-order co-occurrence model: scores every number by how often it
     * appeared right after the numbers of the last draw (Laplace-smoothed
     * conditional probabilities).
     */
    MARKOV,

    /** Logistic regression trained on-device with the stored history. */
    AI,

    /**
     * Ensemble that blends the trained AI model, the Bayesian estimate and
     * the Markov model into a single probability per number. Missing
     * components are dropped and the remaining weights renormalised.
     */
    HYBRID,

    /**
     * Expected-value picker: same odds of winning as any other strategy, but
     * avoids the numbers everybody plays (birthdays, popular patterns) to
     * reduce the chance of sharing a prize, and keeps the structure of a
     * typical historical draw (sum window, odd/even balance, consecutives,
     * decades).
     */
    SMART;

    /** True when the strategy needs the on-device trained model. */
    fun usesAiModel(): Boolean = this == AI || this == HYBRID
}
