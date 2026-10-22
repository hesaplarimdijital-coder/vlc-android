package app.nagmedar.ui.library.metadata

import android.content.Context
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.library.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope

/** Whether a video was watched: to the end, part of the way, or not yet. */
enum class WatchState { Watched, Partly, Unwatched }

val VideoItem.watchState: WatchState
    get() = when {
        progress > 0f -> WatchState.Partly
        watched -> WatchState.Watched
        else -> WatchState.Unwatched
    }

/**
 * Series progress: films and episodes marked watched or unwatched by hand, in the medialibrary
 * (its play count and the place playback resumes from), as the classic interface marks them. The
 * screens read the library again when [changes] grows.
 */
object WatchMarks {
    private val counter = MutableStateFlow(0L)

    /** Grows after every mark: the library screens load again. */
    val changes: StateFlow<Long> = counter

    /** Marks [items] watched (played to the end once more, no place to resume) or unwatched (never played). */
    fun mark(items: List<VideoItem>, watched: Boolean) {
        val media = items.mapNotNull { it.media }
        if (media.isEmpty()) return
        AppScope.launch {
            withContext(Dispatchers.IO) { media.forEach { runCatching { markOne(it, watched) } } }
            counter.value++
        }
    }

    private fun markOne(media: MediaWrapper, watched: Boolean) {
        val library = Medialibrary.getInstance()
        if (!library.isInitiated || media.id == 0L) return
        // the place it was left goes either way: a film marked watched does not resume half way
        library.setLastTime(media.id, 0)
        if (watched) {
            if (media.seen == 0L) media.markAsPlayed()
        } else {
            media.setPlayCount(0L)
        }
    }

    /** The menu's "İzlendi olarak işaretle" or "İzlenmedi olarak işaretle" for [items], as their state asks. */
    fun menuEntries(context: Context, items: List<VideoItem>): List<MenuEntry> {
        if (items.none { it.media != null }) return emptyList()
        val res = context.resources
        val anyUnwatched = items.any { it.watchState != WatchState.Watched }
        val anyStarted = items.any { it.watchState != WatchState.Unwatched }
        return listOfNotNull(
            if (anyUnwatched) MenuEntry.Item(res.getString(R.string.nag_mark_watched), { mark(items, true) }, icon = NagIcons.Check) else null,
            if (anyStarted) MenuEntry.Item(res.getString(R.string.nag_mark_unwatched), { mark(items, false) }, icon = NagIcons.Resume) else null,
        )
    }
}
