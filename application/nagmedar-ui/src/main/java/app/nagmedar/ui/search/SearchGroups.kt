package app.nagmedar.ui.search

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.music.MusicGroup
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.Track
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * 1.0.7: an artist or an album of the music library as the search finds it (the search page's and the
 * palette's): until then both found tracks only. It opens as the players' artist and album links do: the
 * Music view shows the group [group] of [view].
 */
@Immutable
data class GroupItem(
    /** Stable across sessions, and without control characters: the palette keeps what ran lately by it. */
    val key: String,
    /** [MusicView.Artists] or [MusicView.Albums]. */
    val view: MusicView,
    /** The group's key in the music library ([MusicGroup.key]). */
    val group: String,
    val title: String,
    /** Who an album is filed under; empty for an artist. */
    val artist: String = "",
    /** An artist's albums; 0 for an album. */
    val albums: Int = 0,
    val tracks: Int = 0,
    /** Every track of it is on a network share: left out, as those tracks are, unless they are asked for. */
    val onNetwork: Boolean = false,
    /** A track of it, for an album's cover. */
    val media: MediaWrapper? = null,
)

/**
 * 1.0.7: the artists, then the albums, of [library] as the Music view groups them ([MusicLibrary.groups]: the
 * same keys, so each opens there); the tracks without the tag are no artist and no album. [media] gives a
 * track's item of the medialibrary, for the covers.
 */
fun searchGroups(library: MusicLibrary, media: (Track) -> MediaWrapper? = { null }): List<GroupItem> {
    fun item(view: MusicView, prefix: String, group: MusicGroup): GroupItem? {
        val title = group.title?.takeIf { it.isNotBlank() } ?: return null
        val first = group.tracks.firstOrNull() ?: return null
        val artists = view == MusicView.Artists
        return GroupItem(
            key = prefix + printable(group.key),
            view = view,
            group = group.key,
            title = title,
            artist = if (artists) "" else group.artist.orEmpty(),
            albums = if (artists) group.albumCount else 0,
            tracks = group.tracks.size,
            onNetwork = group.tracks.all { isOnNetwork(it.uri) },
            media = if (artists) null else media(first),
        )
    }
    // an album's key says what it is ("album:…"); an artist's is the folded name alone
    return library.groups(MusicView.Artists).mapNotNull { item(MusicView.Artists, "artist:", it) } +
        library.groups(MusicView.Albums).mapNotNull { item(MusicView.Albums, "", it) }
}

/** [key] with its control characters (an album's key parts its artist and its name with one) as a visible sign. */
private fun printable(key: String): String = buildString(key.length) {
    for (c in key) append(if (c < ' ') '\u241F' else c)
}
