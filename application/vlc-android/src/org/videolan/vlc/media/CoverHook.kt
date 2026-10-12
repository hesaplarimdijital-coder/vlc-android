package org.videolan.vlc.media

import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.File

/**
 * Nağmedar's album covers (S-1006) for what shows the music outside the app (S-1009): the
 * notification and the lock screen, the media session (Android Auto), the widgets. They are
 * kept by the new interface's module, which this one does not see: its observer of the
 * playback service ([PlaybackObserver]) sets [covers] while the service lives.
 */
interface AlbumCovers {
    /** The cover kept for [media]'s album; null when none is kept yet. Quick: no lookup, no network. */
    fun coverFile(media: MediaWrapper): File?

    /** The cover kept for the album [title] of [artist]; null when none is kept yet. */
    fun albumCoverFile(artist: String?, title: String?): File?
}

object CoverHook {
    @Volatile
    var covers: AlbumCovers? = null

    /** [media]'s own cover file, when Nağmedar keeps one. */
    fun file(media: MediaWrapper?): File? = media?.let { runCatching { covers?.coverFile(it) }.getOrNull() }

    /** [album]'s own cover file, when Nağmedar keeps one. */
    fun file(album: Album?): File? = album?.let { runCatching { covers?.albumCoverFile(it.albumArtist, it.title) }.getOrNull() }

    /** [media]'s cover: Nağmedar's kept one, else the medialibrary's art (a path or an address). */
    fun artwork(media: MediaWrapper?): String? = file(media)?.path ?: media?.artworkMrl

    /** The kept cover's version, its time: an address made of it changes with the cover; 0 for none. */
    fun version(media: MediaWrapper?): Long = file(media)?.lastModified() ?: 0L
}
