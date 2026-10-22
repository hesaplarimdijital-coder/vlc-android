package app.nagmedar.ui.music

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * What a player asks the music view to open: an artist's or an album's tracks ([view] Artists or Albums), as the
 * library groups them; [keys] are the groups that may hold the track, tried in turn (who its album is filed under,
 * then its own artist).
 */
@Immutable
data class MusicLink(val view: MusicView, val keys: List<String>)

/**
 * The players' artist and album names are links (the now playing pane, the phone's player, the bars): a click asks
 * for the music view here, the shell goes there (and closes the phone's player), and the music view opens the group
 * and takes the request.
 */
object MusicLinks {
    /** The request not yet taken by the music view; null for none. */
    var pending by mutableStateOf<MusicLink?>(null)
        private set

    fun open(link: MusicLink) {
        pending = link
    }

    /** The music view took [link] (a newer request stays). */
    fun taken(link: MusicLink) {
        if (pending == link) pending = null
    }

    /** The artist's tracks of [media]; null without an artist, and for what is not music (a video, an episode). */
    fun artistOf(media: MediaWrapper?): MusicLink? {
        if (media == null || !isMusic(media)) return null
        val own = media.artistName?.takeIf { it.isNotBlank() }
        val filed = media.albumArtistName?.takeIf { it.isNotBlank() } ?: own ?: return null
        return MusicLink(MusicView.Artists, listOfNotNull(MusicLibrary.artistKey(filed), own?.let { MusicLibrary.artistKey(it) }).distinct())
    }

    /** The album's tracks of [media]; null without an album, and for what is not music. */
    fun albumOf(media: MediaWrapper?): MusicLink? {
        if (media == null || !isMusic(media)) return null
        val album = media.albumName?.takeIf { it.isNotBlank() } ?: return null
        val own = media.artistName?.takeIf { it.isNotBlank() }
        val filed = media.albumArtistName?.takeIf { it.isNotBlank() } ?: own
        return MusicLink(MusicView.Albums, listOf(MusicLibrary.albumKey(filed, album), MusicLibrary.albumKey(own, album)).distinct())
    }

    private fun isMusic(media: MediaWrapper): Boolean = media.type == MediaWrapper.TYPE_AUDIO && !media.isPodcast
}
