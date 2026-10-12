package app.nagmedar.ui.lyrics

import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.LyricsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1004: the playing track's lyrics, looked for once a track; the delay, everywhere at once and kept. */
class LyricsSessionTest {

    private val rows = LinkedHashMap<Pair<String, String>, LyricsEntry>()
    private val cache = object : LyricsCache {
        override fun forTrack(key: String) = rows.values.filter { it.trackKey == key }

        override fun save(entry: LyricsEntry) {
            rows[entry.trackKey to entry.source] = entry
        }

        override fun clearDownloaded() = 0

        override fun downloadedCount() = 0
    }
    private var device: List<Lyrics> = listOf(LyricsSamples.synced.copy(origin = LyricsOrigin.File))
    private var reads = 0
    private val finder = LyricsFinder(cache, { reads++; device }, service = null, online = { false }, io = Dispatchers.Unconfined)
    private val session = LyricsSession({ finder }, CoroutineScope(Dispatchers.Unconfined), keepAfterMs = 0)
    private val track = LyricsSamples.track

    @Test
    fun lookedForOnceATrack() {
        session.show(track)
        val found = session.load as LyricsLoad.Found
        assertEquals(track, found.track)
        assertEquals(LyricsOrigin.File, found.lyrics.origin)
        // the phone's player and the full-screen view show the same track: no second look
        session.show(track)
        assertEquals(1, reads)
        session.show(track.copy(title = "Liman", lengthMs = 200_000))
        assertEquals(2, reads)
        session.show(null)
        assertEquals(LyricsLoad.Idle, session.load)
    }

    @Test
    fun missingAndLookedForAgain() {
        device = emptyList()
        session.show(track)
        assertEquals(LyricsLoad.Missing(track), session.load)
        // an .lrc put beside it
        device = listOf(LyricsSamples.synced.copy(origin = LyricsOrigin.File))
        session.again()
        assertTrue(session.load is LyricsLoad.Found)
    }

    @Test
    fun theDelayEverywhereAndKept() {
        session.show(track)
        assertEquals(100L, session.shift(LyricsSession.STEP_MS))
        assertEquals(300L, session.setDelay(300))
        assertEquals(300L, session.lyrics!!.offsetMs)
        // kept for the track and its source
        assertEquals(300L, rows[track.key to LyricsSource.FILE]!!.offsetMs)
        // the next time the track plays
        val again = LyricsSession({ finder }, CoroutineScope(Dispatchers.Unconfined), keepAfterMs = 0)
        again.show(track)
        assertEquals(300L, again.lyrics!!.offsetMs)
        // a minute either way at most
        assertEquals(60_000L, session.setDelay(90_000))
        assertEquals(-60_000L, session.shift(-200_000))
    }

    @Test
    fun noDelayForPlainLyrics() {
        device = listOf(LyricsSamples.plain)
        session.show(track)
        assertNull(session.shift(LyricsSession.STEP_MS))
        assertNull(LyricsSession({ finder }, CoroutineScope(Dispatchers.Unconfined)).shift(100))
    }
}
