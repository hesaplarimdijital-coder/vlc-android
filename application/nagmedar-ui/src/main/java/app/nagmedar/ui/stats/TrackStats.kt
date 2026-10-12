package app.nagmedar.ui.stats

import android.content.Context
import androidx.compose.runtime.Immutable
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.TrackStatsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope

/** S-808: a track's rating (0 none, 1–5 stars) and what was done with it. */
@Immutable
data class TrackStats(
    val rating: Int = 0,
    val plays: Int = 0,
    val skips: Int = 0,
    val firstPlayedAt: Long = 0,
    val lastPlayedAt: Long = 0,
)

/**
 * S-808: every track's [TrackStats], by its key (its uri), as Nağmedar's database keeps them:
 * the library's columns, sorts and smart lists read [stats]. A rating shows at once; the
 * database follows, and [stats] with it.
 */
object TrackStatsIndex {

    private val all = MutableStateFlow<Map<String, TrackStats>>(emptyMap())
    val stats: StateFlow<Map<String, TrackStats>> get() = all

    @Volatile
    private var following = false

    /** [stats] follows the database from now on; once is enough. */
    fun follow(context: Context) {
        if (following) return
        following = true
        val dao = NagmedarDatabase.get(context).trackStats()
        AppScope.launch(Dispatchers.IO) {
            runCatching {
                dao.observe().collect { rows -> all.value = rows.associate { it.trackKey to it.toStats() } }
            }
            following = false
        }
    }

    /** [key]'s rating: 0 (none) to 5 stars. */
    fun rate(context: Context, key: String, rating: Int) {
        val stars = rating.coerceIn(0, 5)
        all.update { it + (key to (it[key] ?: TrackStats()).copy(rating = stars)) }
        val at = System.currentTimeMillis()
        AppScope.launch(Dispatchers.IO) { runCatching { NagmedarDatabase.get(context).trackStats().rate(key, stars, at) } }
    }

    /** A play of [key] counted ([played]), or a skip. */
    fun record(context: Context, key: String, played: Boolean) {
        val at = System.currentTimeMillis()
        AppScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = NagmedarDatabase.get(context).trackStats()
                if (played) dao.played(key, at) else dao.skipped(key, at)
            }
        }
    }

    private fun TrackStatsEntry.toStats() = TrackStats(rating, playCount, skipCount, firstPlayedAt, lastPlayedAt)
}
