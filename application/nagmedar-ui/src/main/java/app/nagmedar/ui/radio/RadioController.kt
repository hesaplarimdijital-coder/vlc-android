package app.nagmedar.ui.radio

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    /** An action failed for a reason the user can do nothing about but try again (the database, a provider). */
    data object Failed : RadioNotice
}

/** S-1105: a vote is cast once a station a day, counted on this device. */
object VoteRule {
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Whether a vote may be cast at [now] when the last was at [lastVotedAt] (0: never). A clock set back does not lock the vote. */
    fun canVote(lastVotedAt: Long, now: Long): Boolean = lastVotedAt <= 0 || now < lastVotedAt || now - lastVotedAt >= DAY_MS

    /** How long until the next vote may be cast; 0 when it may be now. */
    fun waitMs(lastVotedAt: Long, now: Long): Long = if (canVote(lastVotedAt, now)) 0L else lastVotedAt + DAY_MS - now
}

/**
 * S-1105: what the radio screen does: the search, playing a station and voting for one. The
 * screen reads [search] and [notice]; nothing here needs the screen, so the tests drive it.
 */
@Stable
class RadioController(
    private val directory: RadioDirectory,
    private val library: RadioLibrary,
    private val player: RadioPlayer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    debounceMs: Long = RadioSearch.DEBOUNCE_MS,
) {
    val search = RadioSearch(directory, library, scope, debounceMs)

    /** The latest word for the user; the screen says it and calls [noticed]. */
    var notice by mutableStateOf<RadioNotice?>(null)
        private set

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
                val now = clock()
                // the row in hand may be stale: what the library knows of the last vote counts too
                val last = maxOf(station.lastVotedAt, library.marks(listOf(uuid))[uuid]?.lastVotedAt ?: 0L)
                if (!VoteRule.canVote(last, now)) {
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
                    val voted = library.voted(station, now, station.votes + 1)
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
}
