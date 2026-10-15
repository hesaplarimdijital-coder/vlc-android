package app.nagmedar.ui.radio

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A word for the user about what an action did; the screen says it in its own words. */
sealed interface RadioNotice {
    /** The vote was taken. */
    data class Voted(val station: String) : RadioNotice

    /** A vote for the station was cast less than a day ago. */
    data class AlreadyVoted(val station: String) : RadioNotice

    /** The directory did not take the vote. */
    data class VoteRefused(val station: String) : RadioNotice

    /** The directory could not be reached to vote. */
    data object VoteOffline : RadioNotice

    /** [station] was taken out of [from]'s list; [RadioController.undo] puts it back. */
    data class Removed(val station: Station, val from: RadioTab) : RadioNotice

    /**
     * [count] stations were imported from a playlist file; [skipped] of its entries were no stream
     * address or repeats; [present] were already kept (and were updated).
     */
    data class Imported(val count: Int, val skipped: Int, val present: Int = 0) : RadioNotice

    /** The file picked gave no stations. */
    data class ImportFailed(val problem: ImportProblem) : RadioNotice

    /** An action failed for a reason the user can do nothing about but try again (the database, a provider). */
    data object Failed : RadioNotice
}

/** Why a playlist file gave no stations. */
enum class ImportProblem { NotAPlaylist, Empty, TooLarge, Unreadable }

/** The radio screen's lists, and (S-207) the recordings made from its stations. */
enum class RadioTab { Favourites, Recent, Search, Imported, Recordings }

/** S-1105: a vote is cast once a station a day, counted on this device. */
object VoteRule {
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Whether a vote may be cast at [now] when the last was at [lastVotedAt] (0: never). A clock set back does not lock the vote. */
    fun canVote(lastVotedAt: Long, now: Long): Boolean = lastVotedAt <= 0 || now < lastVotedAt || now - lastVotedAt >= DAY_MS

    /** How long until the next vote may be cast; 0 when it may be now. */
    fun waitMs(lastVotedAt: Long, now: Long): Long = if (canVote(lastVotedAt, now)) 0L else lastVotedAt + DAY_MS - now
}

/**
 * S-1105, S-1106: what the radio screen does: the lists (favourites, recent, the directory's search,
 * imported), playing a station, voting for one, favouriting, taking one out and importing a
 * playlist file. The screen reads the lists, [tab], [now] and [notice]; nothing here needs the
 * screen, so the tests drive it. [playingNow] is the station on the air with its title (the
 * playback service's, in the app).
 */
@Stable
class RadioController(
    private val directory: RadioDirectory,
    private val library: RadioLibrary,
    private val player: RadioPlayer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    debounceMs: Long = RadioSearch.DEBOUNCE_MS,
    playingNow: Flow<RadioNow?> = flowOf(null),
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    val search = RadioSearch(directory, library, scope, debounceMs)

    /** The list on show. */
    var tab by mutableStateOf(RadioTab.Search)
        private set

    /** The favourite stations; null until the database has answered. */
    var favourites by mutableStateOf<List<Station>?>(null)
        private set

    /** The stations played, the latest first; null until the database has answered. */
    var recent by mutableStateOf<List<Station>?>(null)
        private set

    /** The stations imported from playlist files; null until the database has answered. */
    var imported by mutableStateOf<List<Station>?>(null)
        private set

    /** The station on the air and its title; null when the playing media is not one started here. */
    var now by mutableStateOf<RadioNow?>(null)
        private set

    /** The latest word for the user; the screen says it and calls [noticed]. */
    var notice by mutableStateOf<RadioNotice?>(null)
        private set

    init {
        scope.launch { library.favourites().collect { favourites = it } }
        scope.launch { library.recent().collect { recent = it } }
        scope.launch { library.imported().collect { imported = it } }
        scope.launch { playingNow.collect { now = it } }
    }

    /** Votes being sent: a second tap on the same station waits for the first. */
    private val voting = HashSet<String>()

    /** Runs [block]; a failure that is not the caller's to handle (the database, say) is said, not crashed on. */
    private fun guarded(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = RadioNotice.Failed
            }
        }
    }

    fun select(value: RadioTab) {
        tab = value
    }

    fun noticed() {
        notice = null
    }

    /** Plays [station]: its click is reported, it is kept and noted as played. */
    fun play(station: Station) {
        guarded { search.replace(player.start(station)) }
    }

    /** Votes for [station], once a day: the directory counts it and the station remembers when. */
    fun vote(station: Station) {
        val uuid = station.uuid ?: return
        // two quick taps send one vote
        if (!voting.add(uuid)) return
        guarded {
            try {
                val at = clock()
                // the row in hand may be stale: what the library knows of the last vote counts too
                val last = maxOf(station.lastVotedAt, library.marks(listOf(uuid))[uuid]?.lastVotedAt ?: 0L)
                if (!VoteRule.canVote(last, at)) {
                    notice = RadioNotice.AlreadyVoted(station.name)
                    return@guarded
                }
                val result = try {
                    directory.vote(uuid)
                } catch (e: RadioFailure) {
                    notice = RadioNotice.VoteOffline
                    return@guarded
                }
                if (result.ok) {
                    val voted = library.voted(station, at, station.votes + 1)
                    search.replace(voted)
                    notice = RadioNotice.Voted(station.name)
                } else {
                    notice = RadioNotice.VoteRefused(station.name)
                }
            } finally {
                voting.remove(uuid)
            }
        }
    }

    /** [station] becomes a favourite, or stops being one. */
    fun toggleFavourite(station: Station) {
        guarded { search.replace(library.favourite(station, !station.favourite)) }
    }

    /** Takes [station] out of the list on show: unstarred in favourites, forgotten in recent, deleted in imported. [undo] brings it back. */
    fun remove(station: Station) {
        val from = tab
        guarded {
            when (from) {
                RadioTab.Favourites -> search.replace(library.favourite(station, false))
                RadioTab.Recent -> library.forget(station)
                RadioTab.Imported -> library.remove(station)
                // the recordings are not stations: their tab has its own controller
                RadioTab.Search, RadioTab.Recordings -> return@guarded
            }
            notice = RadioNotice.Removed(station, from)
        }
    }

    /** Puts back what [Removed] took out. */
    fun undo(removed: RadioNotice.Removed) {
        guarded {
            library.restore(removed.station)
            search.replace(removed.station)
        }
    }

    /** The stations of a playlist file picked by the user: [bytes] of the file called [fileName]. */
    fun import(bytes: ByteArray, fileName: String) {
        guarded {
            // reading a file (decoding, matching) is work for a worker, not for the main thread
            val read = withContext(work) { StationPlaylists.read(bytes, fileName) }
            notice = when (val result = read) {
                is StationImport.Found -> {
                    val counts = library.import(result.stations)
                    tab = RadioTab.Imported
                    RadioNotice.Imported(counts.added, result.skipped, counts.present)
                }
                StationImport.NotAPlaylist -> RadioNotice.ImportFailed(ImportProblem.NotAPlaylist)
                StationImport.Empty -> RadioNotice.ImportFailed(ImportProblem.Empty)
                StationImport.TooLarge -> RadioNotice.ImportFailed(ImportProblem.TooLarge)
            }
        }
    }

    /** The file picked could not be read. */
    fun importUnreadable() {
        notice = RadioNotice.ImportFailed(ImportProblem.Unreadable)
    }
}
