package app.nagmedar.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 1.0.1: the queue's keys. "Sıradaki" keys its rows by them; a file queued twice is keyed by the
 * times it came before, and a removal undone after the list was read again must not bring back a
 * key the list holds by then.
 */
class PlayQueueKeysTest {

    private fun entry(key: String) = QueueEntry(key = key, title = key.substringAfterLast('/'), subtitle = "", lengthMs = 0)

    @Test
    fun aFileTwiceIsKeyedByTheTimesItCameBefore() {
        val keys = rekeyed(listOf(entry("/a.flac#1"), entry("/b.flac#1"), entry("/a.flac#2"))).map { it.key }
        assertEquals(listOf("/a.flac#1", "/b.flac#1", "/a.flac#2"), keys)
    }

    @Test
    fun anUndoneRemovalIsKeyedAgainInItsPlace() {
        // [a#1, a#2]: a#1 removed, the list read again (the other is a#1 now), the removal undone
        val read = listOf(entry("/a.flac#1"))
        val restored = rekeyed(read.toMutableList().apply { addAll(0, listOf(entry("/a.flac#1"))) })
        assertEquals(listOf("/a.flac#1", "/a.flac#2"), restored.map { it.key })
    }

    @Test
    fun keysAlreadyRightStayTheSameEntries() {
        val list = listOf(entry("/a.flac#1"), entry("/x#y.mp3#1"), entry("/x#y.mp3#2"))
        val again = rekeyed(list)
        list.indices.forEach { assertSame(list[it], again[it]) }
    }
}
