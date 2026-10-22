package app.nagmedar.ui.music

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.search.foldForSearch
import app.nagmedar.ui.stats.TrackStats
import java.text.Collator
import java.util.Locale

/** S-1601: the orders the playlists' overview can be put in. */
enum class PlaylistSort { Name, Recent, Created }

/**
 * S-1601: a playlist or a smart list as a card of the overview shows it. [key] tells the two
 * kinds apart ("p:3", "s:3"); [count] and [durationMs] are null while a smart list's tracks are
 * still being read; [created] is the place in the order the lists were made (a smart list has no
 * date of its own to sort by, so the playlists come before the smart lists).
 */
@Immutable
data class PlaylistCardData(
    val key: String,
    val id: Long,
    val smart: Boolean,
    val name: String,
    val count: Int?,
    val durationMs: Long?,
    val covers: List<CoverTrack>,
    val lastPlayedAt: Long,
    val created: Int,
)

/** S-1601: the overview's cards: made from the lists, filtered by what is typed and put in order. */
object PlaylistCards {

    /** The covers of the first tracks of [tracks], one for each album, at most [limit]. */
    fun coversOf(tracks: List<Track>, limit: Int = 4): List<CoverTrack> {
        val seen = HashSet<String>()
        val covers = ArrayList<CoverTrack>(limit)
        for (track in tracks) {
            val cover = CoverTrack(track.uri, track.album, track.filedArtist)
            if (seen.add(cover.key)) covers += cover
            if (covers.size == limit) break
        }
        return covers
    }

    /** When the last of [uris] was played, 0 for none. */
    fun lastPlayed(uris: List<String>, stats: Map<String, TrackStats>): Long = uris.maxOfOrNull { stats[it]?.lastPlayedAt ?: 0L } ?: 0L

    /** The cards of [playlists] (read as [glances] say) and of [smartLists] (with the [smartTracks] they take now). */
    fun build(
        playlists: List<PlaylistSummary>,
        glances: Map<Long, PlaylistGlance>,
        smartLists: List<SmartList>,
        smartTracks: Map<Long, List<Track>?>,
        stats: Map<String, TrackStats>,
    ): List<PlaylistCardData> {
        val made = playlists.mapIndexed { index, playlist ->
            val glance = glances[playlist.id]
            PlaylistCardData(
                key = "p:${playlist.id}",
                id = playlist.id,
                smart = false,
                name = playlist.name,
                count = playlist.count,
                durationMs = playlist.durationMs,
                covers = glance?.covers.orEmpty(),
                lastPlayedAt = glance?.let { lastPlayed(it.uris, stats) } ?: 0L,
                created = index,
            )
        }
        val smart = smartLists.mapIndexed { index, list ->
            val tracks = smartTracks[list.id]
            PlaylistCardData(
                key = "s:${list.id}",
                id = list.id,
                smart = true,
                name = list.name,
                count = tracks?.size,
                durationMs = tracks?.sumOf { it.durationMs.coerceAtLeast(0) },
                covers = tracks?.let { coversOf(it) }.orEmpty(),
                lastPlayedAt = tracks?.let { lastPlayed(it.map(Track::statsKey), stats) } ?: 0L,
                created = playlists.size + index,
            )
        }
        return made + smart
    }

    /** The cards whose name has [query] in it, letters folded as the library's search does; all of them for a blank query. */
    fun matching(cards: List<PlaylistCardData>, query: String): List<PlaylistCardData> {
        val asked = foldForSearch(query.trim())
        if (asked.isEmpty()) return cards
        return cards.filter { foldForSearch(it.name).contains(asked) }
    }

    /**
     * [cards] in the order of [sort]: by name in the locale's alphabet; by the last play, the
     * latest first and those never played last; by creation, the newest first with the smart
     * lists after the playlists. Equal ones keep a name order.
     */
    fun sorted(cards: List<PlaylistCardData>, sort: PlaylistSort, locale: Locale): List<PlaylistCardData> {
        val collator = Collator.getInstance(locale)
        val byName = Comparator<PlaylistCardData> { a, b -> collator.compare(a.name, b.name) }
        val order = when (sort) {
            PlaylistSort.Name -> byName
            PlaylistSort.Recent -> compareByDescending<PlaylistCardData> { it.lastPlayedAt }.then(byName)
            PlaylistSort.Created -> compareBy<PlaylistCardData> { it.smart }.thenByDescending { it.created }
        }
        return cards.sortedWith(order)
    }
}
