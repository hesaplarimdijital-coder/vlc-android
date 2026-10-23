package app.nagmedar.ui.playback

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/** Where what plays comes from, as the phone's player says it above the artwork. */
enum class PlayFrom { Album, Artist, Playlist, Queue }

/** An album, an artist or a playlist a list was played from, by [name]; [PlayFrom.Queue]'s name is empty. */
@Immutable
data class PlaySource(val from: PlayFrom, val name: String)

/** A list played from [source]: the locations of its tracks ([PlaySources.locationOf]). */
@Immutable
data class StartedList(val source: PlaySource, val locations: Set<String>)

/**
 * The album, artist or playlist the queue was last started from (the music view's groups, the playlists), for the phone
 * player's overline: "Albümden çalınıyor · Itrî'nin İzinde". A list started anywhere else forgets it.
 */
object PlaySources {
    /** The list last started with a source; null after one started without. */
    // process-wide state: made in the global snapshot by ProcessState.warmUp, before any window composes
    var started by mutableStateOf<StartedList?>(null)
        private set

    /** [list] starts playing, from [source] (null: from nowhere the player names). Main thread. */
    fun start(source: PlaySource?, list: List<MediaWrapper>) {
        started = source?.let { StartedList(it, list.mapNotNullTo(HashSet(), ::locationOf)) }
    }

    /** The key a media is known by here: its location, else its uri. */
    fun locationOf(media: MediaWrapper): String? = media.location ?: media.uri?.toString()

    /**
     * Where [playing] (a location) comes from in a [queue] of locations: the source of the list [started] while it is
     * one of that list's tracks; the queue ([PlayFrom.Queue]) for a track added to the queue after it, while some of
     * the list is still there; null when nothing is known (the queue was started from elsewhere since).
     */
    fun playingFrom(started: StartedList?, playing: String?, queue: List<String>): PlaySource? {
        val list = started ?: return null
        if (playing == null) return null
        if (playing in list.locations) return list.source
        return if (queue.any { it in list.locations }) PlaySource(PlayFrom.Queue, "") else null
    }
}
