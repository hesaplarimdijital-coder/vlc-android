package app.nagmedar.ui.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-804: the playlists screen's changes, shown at once and kept in the store in their order. */
class PlaylistsStateTest {

    private fun test(block: suspend CoroutineScope.(FakePlaylistStore, PlaylistsState) -> Unit) = runBlocking {
        val store = FakePlaylistStore("Akşam" to listOf(0, 1, 2), "Yol" to listOf(3, 4, 5, 6))
        val state = PlaylistsState(store, this)
        state.reload()
        block(store, state)
    }

    /** Every change launched so far reaches the store. */
    private suspend fun CoroutineScope.settle() {
        repeat(3) {
            coroutineContext[Job]!!.children.forEach { it.join() }
            yield()
        }
    }

    private val PlaylistsState.titles get() = lines.map { it.track.title }

    @Test
    fun theFirstPlaylistOpens() = test { _, state ->
        assertEquals(listOf("Akşam", "Yol"), state.playlists!!.map { it.name })
        assertEquals("Akşam", state.open!!.name)
        assertEquals(listOf("Segâh Tekbîr", "Neva Kâr", "Nihâvend Saz Semâîsi"), state.titles)
    }

    @Test
    fun movesAndRemovalsShowAtOnceAndReachTheStore() = test { store, state ->
        state.move(0, 2)
        assertEquals(listOf("Neva Kâr", "Nihâvend Saz Semâîsi", "Segâh Tekbîr"), state.titles)
        val gone = state.remove(setOf(state.lines[0].key))
        assertEquals(listOf("Nihâvend Saz Semâîsi", "Segâh Tekbîr"), state.titles)
        settle()
        assertEquals(state.titles, store.titles(1))
        assertEquals(2, state.playlists!!.first().count)
        // brought back where it was
        state.restore(gone, 1)
        assertEquals(listOf("Neva Kâr", "Nihâvend Saz Semâîsi", "Segâh Tekbîr"), state.titles)
        settle()
        assertEquals(state.titles, store.titles(1))
    }

    @Test
    fun aReloadAfterItsOwnChangeMovesNothing() = test { store, state ->
        val keys = state.lines.map { it.key }
        state.move(2, 0)
        settle()
        state.reload()
        assertEquals(listOf(keys[2], keys[0], keys[1]), state.lines.map { it.key })
        // a change made elsewhere is read
        store.append(1, listOf(MusicSamples.tracks[10].id))
        state.reload()
        assertEquals("İsimsiz", state.titles.last())
    }

    @Test
    fun anotherTabAndAppend() = test { store, state ->
        val yol = state.playlists!![1].id
        state.openList(yol)
        settle()
        assertEquals(listOf("Gece Yolcusu", "Yağmur", "Son Vapur", "Uzak"), state.titles)
        state.append(yol, listOf(MusicSamples.tracks[8]))
        assertEquals("Ada", state.titles.last())
        settle()
        assertEquals(state.titles, store.titles(yol))
    }

    @Test
    fun aDeletedPlaylistComesBackWithItsTracks() = test { store, state ->
        var restore: (() -> Unit)? = null
        state.delete(1) { restore = it }
        settle()
        assertEquals(listOf("Yol"), state.playlists!!.map { it.name })
        assertEquals("Yol", state.open!!.name)
        restore!!()
        settle()
        val back = store.kept.entries.last()
        assertEquals("Akşam", back.value.first)
        assertEquals(listOf("Segâh Tekbîr", "Neva Kâr", "Nihâvend Saz Semâîsi"), back.value.second.map { it.title })
        assertNotEquals(1L, back.key)
        // the new one opens
        assertEquals(back.key, state.openId)
    }

    @Test
    fun renamedAndNamedApart() = test { store, state ->
        state.rename(1, "Gece")
        assertEquals("Gece", state.open!!.name)
        settle()
        assertEquals("Gece", store.kept.getValue(1).first)
        assertEquals("Yeni liste", PlaylistsState.uniqueName("Yeni liste", listOf("Akşam")))
        assertEquals("Yeni liste 3", PlaylistsState.uniqueName("Yeni liste", listOf("Yeni liste", "Yeni liste 2")))
    }

    @Test
    fun noPlaylistsNothingOpen() = runBlocking {
        val state = PlaylistsState(FakePlaylistStore(), this)
        state.reload()
        assertEquals(emptyList<PlaylistSummary>(), state.playlists)
        assertNull(state.open)
        assertEquals(emptyList<PlaylistLine>(), state.lines)
    }
}
