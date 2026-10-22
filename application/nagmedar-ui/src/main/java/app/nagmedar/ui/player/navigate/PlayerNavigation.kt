package app.nagmedar.ui.player.navigate

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.R
import app.nagmedar.ui.player.ChapterMark
import app.nagmedar.ui.player.FramePreviews
import app.nagmedar.ui.player.RetrieverFramePreviews
import app.nagmedar.ui.player.playerClock
import app.nagmedar.ui.player.podcastChapterMarks
import kotlinx.coroutines.MainScope
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * The player's ways around a film (S-608): Ctrl+T's dialog, the bookmarks (Ctrl+D adds one where
 * the film is), the chapters, and the small frames the timeline, the chapters and the bookmarks
 * show, read once for all of them.
 */
@Stable
internal class PlayerNavigation(private val activity: VideoPlayerActivity) {
    val bookmarks = BookmarksController(MedialibraryBookmarks(activity), MainScope())
    private val frames = SharedFrames(activity.applicationContext)

    /** "Jump to time" is open. */
    var jumping by mutableStateOf(false)

    /** The bookmark being named, in its dialog. */
    var renaming by mutableStateOf<BookmarkMark?>(null)

    /** Frames of the film at [uri], shared by whoever shows them. */
    fun previewsFor(uri: Uri?): FramePreviews? = frames.of(uri)

    fun timeMs(): Long = activity.service?.getTime() ?: 0L

    fun lengthMs(): Long = activity.service?.length ?: 0L

    fun chapters(): List<ChapterMark> =
        activity.service?.getChapters(-1)?.map { ChapterMark(it.timeOffset, it.name.orEmpty()) }.orEmpty().ifEmpty { podcastChapterMarks() }

    /** A precise seek, from the user: a chapter, a bookmark, a typed time. */
    fun seek(ms: Long) {
        activity.service?.seek(ms, fromUser = true)
    }

    /** Ctrl+D: a bookmark where the film is; the video says which. */
    fun addBookmark() {
        val service = activity.service ?: return
        val mark = bookmarks.add(service.getTime()) { number -> activity.getString(R.string.nag_bm_default_name, number) }
        activity.overlayDelegate.showInfo(activity.getString(R.string.nag_bm_added, mark.name, playerClock(mark.timeMs)), 1500)
    }

    /** Another film: its own bookmarks. */
    fun onMediaStarted() {
        bookmarks.refresh()
    }

    fun release() {
        frames.close()
    }
}

/** One reader of frames for the film playing, given to the timeline, the chapters and the bookmarks alike. */
internal class SharedFrames(private val context: Context) {
    private var uri: Uri? = null
    private var previews: RetrieverFramePreviews? = null

    @Synchronized
    fun of(uri: Uri?): FramePreviews? {
        if (uri != this.uri) {
            previews?.close()
            previews = uri?.let { RetrieverFramePreviews(context, it) }
            this.uri = uri
        }
        return previews
    }

    @Synchronized
    fun close() {
        previews?.close()
        previews = null
        uri = null
    }
}
