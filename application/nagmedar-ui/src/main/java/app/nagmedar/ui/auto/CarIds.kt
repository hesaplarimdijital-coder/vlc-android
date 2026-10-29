package app.nagmedar.ui.auto

import org.videolan.vlc.media.MediaSessionBrowser
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 1.0: the ids of Android Auto's entries: under the browser's root ([MediaSessionBrowser.ID_ROOT]) a path, a key in it
 * encoded (an album's, a folder's path, a tag) and parameters after "?": "p" the page of a long list, "i" the track a
 * list plays from, "n", "u", "l" a directory station's name, address and logo, "t" why a list is empty ([nothing]).
 */
object CarIds {
    const val ROOT = MediaSessionBrowser.ID_ROOT

    const val MUSIC = MediaSessionBrowser.ID_MUSIC
    const val ALBUMS = "$MUSIC/albums"
    const val ARTISTS = "$MUSIC/artists"
    const val PLAYLISTS = "$MUSIC/playlists"
    const val YEARS = "$MUSIC/years"
    const val FOLDERS = "$MUSIC/folders"
    private const val ALBUM = "$MUSIC/album/"
    private const val ARTIST = "$MUSIC/artist/"
    private const val PLAYLIST = "$MUSIC/playlist/"
    private const val DECADE = "$MUSIC/decade/"
    private const val YEAR = "$MUSIC/year/"
    private const val FOLDER = "$MUSIC/folder/"

    const val PODCASTS = MediaSessionBrowser.ID_PODCASTS
    const val CONTINUE = "$PODCASTS/continue"
    const val SHOWS = "$PODCASTS/shows"
    const val NEW = "$PODCASTS/new"
    private const val SHOW = "$PODCASTS/show/"
    private const val EPISODE = "$PODCASTS/episode/"

    const val RADIO = MediaSessionBrowser.ID_RADIO
    const val FAVOURITES = "$RADIO/favourites"
    const val STATIONS_RECENT = "$RADIO/recent"
    const val TAGS = "$RADIO/tags"
    private const val TAG = "$RADIO/tag/"
    private const val STATION = "$RADIO/station/"
    private const val DIRECTORY = "$RADIO/dir/"

    const val RECENT = MediaSessionBrowser.ID_RECENT
    /** The prefix of a search's track list ([searchTracks]). */
    const val SEARCH = "$ROOT/search/"

    /** An entry that is nothing to open or play: an empty list's or an offline directory's line. */
    const val NOTHING = MediaSessionBrowser.ID_NO_MEDIA

    /** The line that says why a list is empty, its reason ("t", a [CarText]'s name) in its id: tapped, it says so. */
    fun nothing(reason: String) = "$NOTHING?t=$reason"

    fun album(key: String) = ALBUM + encode(key)
    fun artist(key: String) = ARTIST + encode(key)
    fun playlist(id: Long) = "$PLAYLIST$id"
    fun decade(start: Int) = "$DECADE$start"

    /** A year's tracks; 0 for those of no year. */
    fun year(year: Int?) = "$YEAR${year ?: 0}"
    fun folder(path: String) = FOLDER + encode(path)
    fun show(feedId: Long) = "$SHOW$feedId"
    fun episode(id: Long) = "$EPISODE$id"
    fun tag(name: String) = TAG + encode(name)
    fun station(id: Long) = "$STATION$id"

    /** A station of the directory, not kept: what plays it is in its id. */
    fun directoryStation(uuid: String, name: String, url: String, logo: String?) =
        DIRECTORY + encode(uuid) + "?n=" + encode(name) + "&u=" + encode(url) + (logo?.let { "&l=" + encode(it) } ?: "")

    /** The tracks a search found for [query]. */
    fun searchTracks(query: String) = SEARCH + encode(query)

    /** The track at [index] of the list [listId] shows: it plays the list from there. */
    fun track(listId: String, index: Int) = "$listId?i=$index"

    /** The [page]th part of the long list [listId]. */
    fun page(listId: String, page: Int) = "$listId?p=$page"

    /** [id] read: its path, the key after a known prefix, its parameters; null for an id that is not under the root. */
    fun parse(id: String): CarRef? {
        if (!id.startsWith(ROOT)) return null
        val path = id.substringBefore('?')
        val params = id.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to decode(it.substringAfter('=')) }
        val kind = KINDS.firstOrNull { path.startsWith(it.first) && path.length > it.first.length }
        return CarRef(path, kind?.second, kind?.let { decode(path.removePrefix(it.first)) }, params)
    }

    private val KINDS = listOf(
        ALBUM to CarKind.Album, ARTIST to CarKind.Artist, PLAYLIST to CarKind.Playlist, DECADE to CarKind.Decade, YEAR to CarKind.Year,
        FOLDER to CarKind.Folder, SHOW to CarKind.Show, EPISODE to CarKind.Episode, TAG to CarKind.Tag, STATION to CarKind.Station,
        DIRECTORY to CarKind.DirectoryStation, SEARCH to CarKind.Search,
    )

    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name())

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, Charsets.UTF_8.name())
    } catch (e: IllegalArgumentException) {
        text
    }
}

/** The entries with a key in their id. */
enum class CarKind { Album, Artist, Playlist, Decade, Year, Folder, Show, Episode, Tag, Station, DirectoryStation, Search }

/** An id read ([CarIds.parse]): [path] without the parameters, the [kind] and [key] of an entry with one. */
data class CarRef(val path: String, val kind: CarKind?, val key: String?, val params: Map<String, String>) {
    /** The page of a long list asked for; null for its first look, which lists its pages when it has several. */
    val page: Int? get() = params["p"]?.toIntOrNull()

    /** The track a list plays from. */
    val index: Int? get() = params["i"]?.toIntOrNull()

    /** The key as a number (a playlist's, a show's, an episode's, a station's id, a year). */
    val number: Long? get() = key?.toLongOrNull()
}
