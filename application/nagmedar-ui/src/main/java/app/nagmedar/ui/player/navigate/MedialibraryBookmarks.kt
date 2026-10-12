package app.nagmedar.ui.player.navigate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * The playing film's bookmarks in the medialibrary (S-608), where VLC keeps its own: a film the
 * library does not hold yet (opened from a file manager, a stream) is added to it as VLC adds
 * the media it plays, so its bookmarks stay.
 */
internal class MedialibraryBookmarks(private val activity: VideoPlayerActivity) : BookmarkStore {

    /** The film as the library holds it; added when [create] and it holds it not. */
    private suspend fun media(create: Boolean): MediaWrapper? {
        val (playing, length) = withContext(Dispatchers.Main) {
            val service = activity.service ?: return@withContext null to 0L
            service.currentMediaWrapper to service.length
        }
        playing ?: return null
        if (playing.id > 0) return playing
        return withContext(Dispatchers.IO) {
            val library = Medialibrary.getInstance()
            if (!library.isInitiated) return@withContext null
            library.getMedia(playing.uri)?.takeIf { it.id > 0 }
                ?: if (create) library.addMedia(playing.uri.toString(), length)?.takeIf { it.id > 0 } else null
        }
    }

    override suspend fun load(): List<BookmarkMark> {
        val media = media(create = false) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            media.bookmarks.orEmpty().map { BookmarkMark(it.time, it.title.orEmpty()) }
        }
    }

    override suspend fun add(timeMs: Long, name: String): Boolean {
        val media = media(create = true) ?: return false
        return withContext(Dispatchers.IO) {
            val bookmark = media.addBookmark(timeMs) ?: return@withContext false
            bookmark.setName(name)
            true
        }
    }

    override suspend fun rename(timeMs: Long, name: String): Boolean {
        val media = media(create = false) ?: return false
        return withContext(Dispatchers.IO) {
            media.bookmarks.orEmpty().firstOrNull { it.time == timeMs }?.setName(name) ?: false
        }
    }

    override suspend fun remove(timeMs: Long): Boolean {
        val media = media(create = false) ?: return false
        return withContext(Dispatchers.IO) { media.removeBookmark(timeMs) }
    }
}
