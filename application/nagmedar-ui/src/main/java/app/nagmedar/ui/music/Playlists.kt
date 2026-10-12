package app.nagmedar.ui.music

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** S-804: a playlist as its tab shows it. */
@Immutable
data class PlaylistSummary(val id: Long, val name: String, val count: Int, val durationMs: Long)

/** One line of the open playlist; [key] tells two lines of one track apart and follows its line when it moves. */
@Immutable
data class PlaylistLine(val key: Long, val track: Track)

/**
 * Where the playlists are kept: the medialibrary in the app, a list in the tests. Positions are
 * the lines' indices; [move] puts the line at [from] at index [to] of the list it leaves.
 */
interface PlaylistStore {
    /** Goes up when the playlists change elsewhere (the classic interface, a scan finding a playlist file). */
    val changes: StateFlow<Int>

    suspend fun playlists(): List<PlaylistSummary>

    suspend fun tracks(id: Long): List<Track>

    /** A new playlist with [trackIds] in it; its id, null when it could not be made. */
    suspend fun create(name: String, trackIds: List<Long> = emptyList()): Long?

    suspend fun rename(id: Long, name: String)

    suspend fun delete(id: Long)

    suspend fun move(id: Long, from: Int, to: Int)

    /** The lines at [positions] taken out. */
    suspend fun remove(id: Long, positions: List<Int>)

    /** [trackId] put in at [position]. */
    suspend fun insert(id: Long, position: Int, trackId: Long)

    suspend fun append(id: Long, trackIds: List<Long>)
}

/**
 * S-804: the playlists screen's state: the tabs, the one open and its lines. Each change shows
 * at once and goes to the [store] in the order it was made; what was taken out comes back with
 * [undo]'s bar. A change made elsewhere ([PlaylistStore.changes]) reloads it.
 */
@Stable
class PlaylistsState(private val store: PlaylistStore, private val scope: CoroutineScope) {

    /** The playlists, in the order they were made; null until read. */
    var playlists: List<PlaylistSummary>? by mutableStateOf(null)
        private set

    var openId: Long? by mutableStateOf(null)
        private set

    var lines: List<PlaylistLine> by mutableStateOf(emptyList())
        private set

    val open: PlaylistSummary? get() = playlists?.firstOrNull { it.id == openId }

    private val writes = Mutex()
    private var nextKey = 0L

    /** Counts the changes made here: a reload that started before one leaves what it shows. */
    private var edits = 0

    private fun lineOf(track: Track) = PlaylistLine(nextKey++, track)

    /**
     * The playlists read again after the changes on their way, the open one kept if it still is
     * ([prefer] first: a new one, say). Lines that read as they show keep their keys, so a change
     * made here and coming back from the store moves nothing.
     */
    suspend fun reload(prefer: Long? = null) {
        val at = edits
        val (all, id, tracks) = writes.withLock {
            val all = store.playlists()
            val id = (all.firstOrNull { it.id == prefer } ?: all.firstOrNull { it.id == openId } ?: all.firstOrNull())?.id
            Triple(all, id, if (id == null) emptyList() else store.tracks(id))
        }
        if (edits != at) return
        playlists = all
        if (id != openId || tracks.map { it.id } != lines.map { it.track.id }) {
            openId = id
            lines = tracks.map(::lineOf)
        }
    }

    fun openList(id: Long) {
        if (id == openId) return
        openId = id
        lines = emptyList()
        scope.launch { writes.withLock { if (openId == id) lines = store.tracks(id).map(::lineOf) } }
    }

    /** A store call after the ones before it; the tabs' counts read again after. */
    private fun write(block: suspend () -> Unit) {
        scope.launch {
            writes.withLock {
                block()
                playlists = store.playlists()
            }
        }
    }

    fun create(name: String, trackIds: List<Long> = emptyList(), done: (Long?) -> Unit = {}) {
        scope.launch {
            val id = writes.withLock { store.create(name, trackIds) }
            if (id != null) reload(prefer = id)
            done(id)
        }
    }

    fun rename(id: Long, name: String) {
        edits++
        playlists = playlists?.map { if (it.id == id) it.copy(name = name) else it }
        write { store.rename(id, name) }
    }

    /**
     * The playlist of [id] deleted; [restored] tells the screen how to bring it back (made anew
     * with its name and its tracks, in order).
     */
    fun delete(id: Long, restored: (restore: () -> Unit) -> Unit) {
        val summary = playlists?.firstOrNull { it.id == id } ?: return
        scope.launch {
            val tracks = writes.withLock {
                val tracks = if (id == openId) lines.map { it.track.id } else store.tracks(id).map { it.id }
                store.delete(id)
                tracks
            }
            reload()
            restored { create(summary.name, tracks) }
        }
    }

    /** The line at [from] to index [to]. */
    fun move(from: Int, to: Int) {
        val id = openId ?: return
        if (from == to || from !in lines.indices || to !in lines.indices) return
        edits++
        lines = lines.toMutableList().apply { add(to, removeAt(from)) }
        write { store.move(id, from, to) }
    }

    /** The lines of [keys] taken out; they are returned with their places, for the undo bar. */
    fun remove(keys: Set<Any>): List<Pair<Int, PlaylistLine>> {
        val id = openId ?: return emptyList()
        val gone = lines.withIndex().filter { it.value.key in keys }.map { it.index to it.value }
        if (gone.isEmpty()) return gone
        edits++
        lines = lines.filter { it.key !in keys }
        write { store.remove(id, gone.map { it.first }) }
        return gone
    }

    /** What [remove] took out, back in its places (if the same playlist is still open). */
    fun restore(gone: List<Pair<Int, PlaylistLine>>, playlist: Long) {
        edits++
        if (openId == playlist) {
            val next = lines.toMutableList()
            for ((position, line) in gone.sortedBy { it.first }) next.add(position.coerceAtMost(next.size), line)
            lines = next
        }
        write { for ((position, line) in gone.sortedBy { it.first }) store.insert(playlist, position, line.track.id) }
    }

    /** [tracks] at the end of the playlist of [id]. */
    fun append(id: Long, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        edits++
        if (id == openId) lines = lines + tracks.map(::lineOf)
        write { store.append(id, tracks.map { it.id }) }
    }

    companion object {
        /** [base], or "[base] 2", "[base] 3"… when a playlist has it. */
        fun uniqueName(base: String, taken: Collection<String>): String {
            if (base !in taken) return base
            var n = 2
            while ("$base $n" in taken) n++
            return "$base $n"
        }
    }
}
