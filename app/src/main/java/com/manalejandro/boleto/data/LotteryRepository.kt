package com.manalejandro.boleto.data

import android.content.Context
import com.manalejandro.boleto.core.IsoDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Progress of a running sync, reported to the UI. */
data class SyncProgress(
    val game: LotteryGame,
    val gameIndex: Int,
    val gameCount: Int,
    val fetched: Int,
)

/** Result of a completed sync. */
data class SyncSummary(
    val added: Map<LotteryGame, Int>,
    val failed: List<LotteryGame>,
)

/**
 * Single point of access to the draw history: seeds from the bundled archive,
 * updates from the official source and keeps everything on disk.
 */
class LotteryRepository(private val context: Context) {

    private val store = HistoryStore(context)
    val prefs = Prefs(context)

    private val laeApi = LaeApi()
    private val euroMillionsApi = EuroMillionsApi()

    /** Loads the stored history for a game, seeding from assets when needed. */
    suspend fun load(game: LotteryGame): List<Draw> = withContext(Dispatchers.IO) {
        store.seedIfNeeded(game)
        store.load(game)
    }

    suspend fun loadAll(): Map<LotteryGame, List<Draw>> = withContext(Dispatchers.IO) {
        LotteryGame.entries.associateWith { game ->
            store.seedIfNeeded(game)
            store.load(game)
        }
    }

    /**
     * Downloads every draw missing since the last stored one. The LAE endpoint
     * caps the response at [LaeApi.MAX_RESULTS] draws, so the calendar is
     * walked in windows and truncated windows are split automatically.
     */
    suspend fun sync(
        game: LotteryGame,
        gameIndex: Int,
        gameCount: Int,
        onProgress: (SyncProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val local = load(game)
        val lastStored = local.lastOrNull()?.date
        var cursor = when {
            lastStored == null -> game.firstDate
            else -> maxOf(game.firstDate, IsoDate.addDays(lastStored, -7))
        }
        val today = IsoDate.today()
        val fetched = ArrayList<Draw>()
        var guard = 0
        while (cursor <= today && guard++ < 400) {
            val end = minOf(IsoDate.addDays(cursor, WINDOW_DAYS - 1), today)
            val batch = try {
                laeApi.fetchRange(game, cursor, end)
            } catch (e: Exception) {
                if (game == LotteryGame.EUROMILLIONS && fetched.isEmpty()) {
                    // Official service unreachable: fall back to the community API.
                    fetched += euroMillionsApi.fetchAll()
                    break
                }
                throw e
            }
            fetched += batch
            onProgress(SyncProgress(game, gameIndex, gameCount, fetched.size))
            if (batch.size >= LaeApi.MAX_RESULTS) {
                val oldest = batch.minOf { it.date }
                val next = IsoDate.addDays(oldest, -1)
                cursor = if (next > cursor) next else IsoDate.addDays(cursor, 1)
            } else {
                cursor = IsoDate.addDays(end, 1)
            }
        }

        val merged = LinkedHashMap<String, Draw>(local.size + fetched.size)
        local.forEach { merged[it.date] = it }
        fetched.forEach { merged[it.date] = it }
        val all = merged.values.sortedBy { it.date }
        store.save(game, all)
        prefs.setLastUpdate(game, System.currentTimeMillis())

        val localDates = local.mapTo(HashSet()) { it.date }
        fetched.count { it.date !in localDates }
    }

    suspend fun syncAll(onProgress: (SyncProgress) -> Unit): SyncSummary = withContext(Dispatchers.IO) {
        val added = LinkedHashMap<LotteryGame, Int>()
        val failed = ArrayList<LotteryGame>()
        LotteryGame.entries.forEachIndexed { index, game ->
            try {
                added[game] = sync(game, index + 1, LotteryGame.entries.size, onProgress)
            } catch (_: Exception) {
                failed += game
            }
        }
        SyncSummary(added, failed)
    }

    suspend fun saveTicket(boleto: Boleto) = withContext(Dispatchers.IO) {
        prefs.setTicket(boleto)
    }

    private companion object {
        /** Window size for archive walking; keeps results well below the cap. */
        const val WINDOW_DAYS = 180
    }
}
