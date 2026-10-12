package app.nagmedar.ui.player.navigate

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.player.ChapterMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A bookmark of the playing film (S-608): where it is and its name. */
@Immutable
data class BookmarkMark(val timeMs: Long, val name: String)

/** Where the playing film's bookmarks are kept: the medialibrary in the app, a list in the tests. */
interface BookmarkStore {
    /** The film's bookmarks; empty when it has none or cannot have any. */
    suspend fun load(): List<BookmarkMark>

    /** False when the film cannot keep one (the library is not ready). */
    suspend fun add(timeMs: Long, name: String): Boolean

    suspend fun rename(timeMs: Long, name: String): Boolean

    suspend fun remove(timeMs: Long): Boolean
}

/**
 * The playing film's bookmarks (S-608), in time order, as the panel's tab and the timeline show
 * them: added where the film is (one a second at most), renamed, removed; read again when another
 * film starts.
 */
@Stable
class BookmarksController(private val store: BookmarkStore, private val scope: CoroutineScope) {
    var marks by mutableStateOf<List<BookmarkMark>>(emptyList())
        private set

    /** The last add failed: the film cannot keep bookmarks. */
    var refused by mutableStateOf(false)
        private set

    fun refresh() {
        scope.launch { marks = store.load().sortedBy { it.timeMs } }
    }

    /**
     * A bookmark at [timeMs], named [name] for its number ("Bookmark 3"); none when one is within a
     * second of it already. Returns what was added, or the bookmark already there.
     */
    fun add(timeMs: Long, name: (Int) -> String): BookmarkMark {
        marks.firstOrNull { abs(it.timeMs - timeMs) < SAME_MS }?.let { return it }
        val mark = BookmarkMark(timeMs.coerceAtLeast(0L), name(marks.size + 1))
        marks = (marks + mark).sortedBy { it.timeMs }
        scope.launch {
            refused = !store.add(mark.timeMs, mark.name)
            marks = store.load().sortedBy { it.timeMs }
        }
        return mark
    }

    fun rename(mark: BookmarkMark, name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean == mark.name) return
        marks = marks.map { if (it.timeMs == mark.timeMs) it.copy(name = clean) else it }
        scope.launch { store.rename(mark.timeMs, clean) }
    }

    fun remove(mark: BookmarkMark) {
        marks = marks.filter { it.timeMs != mark.timeMs }
        scope.launch { store.remove(mark.timeMs) }
    }

    /** The bookmark after [nowMs], past the second the player may be in after a jump to one. */
    fun next(nowMs: Long): BookmarkMark? = marks.firstOrNull { it.timeMs > nowMs + SAME_MS }

    fun previous(nowMs: Long): BookmarkMark? = marks.lastOrNull { it.timeMs < nowMs - SAME_MS }

    companion object {
        /** Within a second is the same place. */
        const val SAME_MS = 1_000L
    }
}

/** The chapter playing at [nowMs], and the one after or before it: Shift+N and Shift+P (S-608). */
fun chapterStep(chapters: List<ChapterMark>, nowMs: Long, forward: Boolean): Int? {
    if (chapters.isEmpty()) return null
    val current = chapters.indexOfLast { it.startMs <= nowMs }.coerceAtLeast(0)
    return if (forward) {
        (current + 1).takeIf { it < chapters.size }
    } else {
        // a few seconds into a chapter, back goes to its start, as players do
        if (nowMs - chapters[current].startMs > BACK_TO_START_MS || current == 0) current else current - 1
    }
}

private const val BACK_TO_START_MS = 3_000L
