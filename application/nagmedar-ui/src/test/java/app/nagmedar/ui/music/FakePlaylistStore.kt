package app.nagmedar.ui.music

import kotlinx.coroutines.flow.MutableStateFlow

/** S-804: playlists in a map, for the tests: what the medialibrary would keep, by its rules. */
class FakePlaylistStore(vararg playlists: Pair<String, List<Int>>) : PlaylistStore {

    private val library = MusicSamples.tracks.associateBy { it.id }
    private var nextId = 1L

    /** The playlists as kept: id to name and tracks. */
    val kept = LinkedHashMap<Long, Pair<String, MutableList<Track>>>()

    override val changes = MutableStateFlow(0)

    init {
        for ((name, indices) in playlists) kept[nextId++] = name to indices.map { MusicSamples.tracks[it] }.toMutableList()
    }

    fun titles(id: Long): List<String> = kept.getValue(id).second.map { it.title }

    override suspend fun playlists(): List<PlaylistSummary> =
        kept.map { (id, list) -> PlaylistSummary(id, list.first, list.second.size, list.second.sumOf { it.durationMs }) }

    override suspend fun tracks(id: Long): List<Track> = kept[id]?.second?.toList().orEmpty()

    override suspend fun create(name: String, trackIds: List<Long>): Long {
        val id = nextId++
        kept[id] = name to trackIds.map { library.getValue(it) }.toMutableList()
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        kept[id]?.let { kept[id] = name to it.second }
    }

    override suspend fun delete(id: Long) {
        kept.remove(id)
    }

    override suspend fun move(id: Long, from: Int, to: Int) {
        kept[id]?.second?.let { it.add(to, it.removeAt(from)) }
    }

    override suspend fun remove(id: Long, positions: List<Int>) {
        kept[id]?.second?.let { list -> positions.sortedDescending().forEach { list.removeAt(it) } }
    }

    override suspend fun insert(id: Long, position: Int, trackId: Long) {
        kept[id]?.second?.add(position, library.getValue(trackId))
    }

    override suspend fun append(id: Long, trackIds: List<Long>) {
        kept[id]?.second?.addAll(trackIds.map { library.getValue(it) })
    }
}
