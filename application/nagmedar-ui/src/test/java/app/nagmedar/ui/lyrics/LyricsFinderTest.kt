package app.nagmedar.ui.lyrics

import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1003: where a track's lyrics come from, what is kept of LRCLIB's, and when it is asked. */
class LyricsFinderTest {

    private class Cache : LyricsCache {
        val rows = LinkedHashMap<Pair<String, String>, LyricsEntry>()

        override fun forTrack(key: String) = rows.values.filter { it.trackKey == key }

        override fun save(entry: LyricsEntry) {
            rows[entry.trackKey to entry.source] = entry
        }

        override fun clearDownloaded(): Int {
            val gone = rows.keys.filter { it.second == LyricsSource.LRCLIB }
            gone.forEach { rows.remove(it) }
            return gone.size
        }

        override fun downloadedCount() = rows.values.count { it.source == LyricsSource.LRCLIB && (it.text.isNotEmpty() || it.language != null) }
    }

    private class Lrclib : LyricsOnline {
        val asked = ArrayList<LyricsQuery>()
        var answer: OnlineLyrics? = null
        var failure: LyricsFailure? = null

        override suspend fun lookup(query: LyricsQuery): OnlineLyrics? {
            asked += query
            failure?.let { throw it }
            return answer
        }
    }

    private val cache = Cache()
    private val lrclib = Lrclib()
    private var device: List<Lyrics> = emptyList()
    private var online = true
    private var now = 1_760_000_000_000L
    private val finder = LyricsFinder(cache, { device }, lrclib, online = { online }, clock = { now }, io = Dispatchers.Unconfined)

    private val track = LyricsTrack("file:///sdcard/Music/Ada Karan/Sabaha Kadar.flac", "Sabaha Kadar", "Ada Karan", "Gece Mavisi", 241_600)
    private val synced = "[00:12.30]Gece mavisi\n[00:17.50]çöktü şehre"

    private fun find() = runBlocking { finder.find(track) }

    @Test
    fun theDevicesSyncedLyricsFirst() {
        device = listOf(Lyrics.of("düz söz", LyricsOrigin.Embedded)!!, Lyrics.of(synced, LyricsOrigin.File)!!)
        val found = find()!!
        assertEquals(LyricsOrigin.File, found.origin)
        assertTrue(found.synced)
        // LRCLIB was not needed
        assertEquals(emptyList<LyricsQuery>(), lrclib.asked)
    }

    @Test
    fun lrclibsSyncedBeforeTheDevicesPlain() {
        device = listOf(Lyrics.of("düz söz", LyricsOrigin.Embedded)!!)
        lrclib.answer = OnlineLyrics(synced, "Gece mavisi\nçöktü şehre")
        val found = find()!!
        assertEquals(LyricsOrigin.Online, found.origin)
        assertEquals(listOf("Gece mavisi", "çöktü şehre"), found.lines.map { it.text })
        assertEquals(listOf(LyricsQuery("Ada Karan", "Sabaha Kadar", "Gece Mavisi", 242)), lrclib.asked)
        // LRCLIB's plain lyrics come after the device's
        cache.rows.clear()
        lrclib.answer = OnlineLyrics(null, "LRCLIB'in düz sözü")
        assertEquals(LyricsOrigin.Embedded, find()!!.origin)
    }

    @Test
    fun keptForOffline() {
        lrclib.answer = OnlineLyrics(synced, null)
        assertEquals(LyricsOrigin.Online, find()!!.origin)
        assertEquals(1, cache.downloadedCount())
        // offline, months later: what was kept, and LRCLIB is not asked again
        lrclib.failure = LyricsFailure.Offline()
        now += 90L * 24 * 60 * 60 * 1000
        val kept = find()!!
        assertEquals(listOf(12_300L, 17_500L), kept.lines.map { it.startMs })
        assertEquals(1, lrclib.asked.size)
    }

    @Test
    fun aMissIsAskedAgainAfterAWeek() {
        lrclib.answer = null
        assertNull(find())
        assertEquals(0, cache.downloadedCount())
        now += 6L * 24 * 60 * 60 * 1000
        assertNull(find())
        assertEquals(1, lrclib.asked.size)
        now += 2L * 24 * 60 * 60 * 1000
        lrclib.answer = OnlineLyrics(synced, null)
        assertEquals(LyricsOrigin.Online, find()!!.origin)
        assertEquals(2, lrclib.asked.size)
    }

    @Test
    fun plainLyricsAreAskedAgainAfterAMonth() {
        lrclib.answer = OnlineLyrics(null, "düz")
        assertEquals(false, find()!!.synced)
        now += 31L * 24 * 60 * 60 * 1000
        lrclib.answer = OnlineLyrics(synced, "düz")
        assertTrue(find()!!.synced)
        assertEquals(2, lrclib.asked.size)
        // gone from LRCLIB later: what was found stays
        cache.save(cache.rows.values.single().copy(synced = false, text = "düz"))
        now += 31L * 24 * 60 * 60 * 1000
        lrclib.answer = null
        assertEquals("düz", find()!!.lines.single().text)
    }

    @Test
    fun busyOrOfflineIsNotAMiss() {
        lrclib.failure = LyricsFailure.Busy(60_000)
        assertNull(find())
        lrclib.failure = null
        lrclib.answer = OnlineLyrics(synced, null)
        assertEquals(LyricsOrigin.Online, find()!!.origin)
        assertEquals(2, lrclib.asked.size)
    }

    @Test
    fun theNetworkOnlyWhenAllowedAndForATaggedTrack() {
        online = false
        lrclib.answer = OnlineLyrics(synced, null)
        assertNull(find())
        online = true
        assertNull(runBlocking { finder.find(track.copy(artist = null)) })
        assertEquals(emptyList<LyricsQuery>(), lrclib.asked)
    }

    @Test
    fun instrumental() {
        lrclib.answer = OnlineLyrics(null, null, instrumental = true)
        val found = find()!!
        assertTrue(found.instrumental)
        assertEquals(emptyList<LyricLine>(), found.lines)
        // the device's plain lyrics say more
        device = listOf(Lyrics.of("düz söz", LyricsOrigin.Embedded)!!)
        assertEquals(LyricsOrigin.Embedded, find()!!.origin)
    }

    @Test
    fun theShiftIsKeptPerTrackAndSource() {
        device = listOf(Lyrics.of(synced, LyricsOrigin.File)!!)
        val found = find()!!
        runBlocking { finder.setOffset(track, found, 300) }
        assertEquals(300L, find()!!.offsetMs)
        // the same song in another file: the same key
        assertEquals(300L, runBlocking { finder.find(track.copy(uri = "file:///sdcard/Music/kopya.flac")) }!!.offsetMs)
        lrclib.answer = OnlineLyrics(synced, null)
        device = emptyList()
        val online = find()!!
        assertEquals(0L, online.offsetMs)
        runBlocking { finder.setOffset(track, online, -1_200) }
        assertEquals(-1_200L, find()!!.offsetMs)
    }

    @Test
    fun theUsersOwnFirst() {
        device = listOf(Lyrics.of(synced, LyricsOrigin.Embedded)!!)
        cache.save(LyricsEntry(track.key, LyricsSource.USER, synced = false, text = "Elle yazılmış"))
        assertEquals(LyricsOrigin.User, find()!!.origin)
    }

    @Test
    fun clearing() {
        lrclib.answer = OnlineLyrics(synced, null)
        find()
        device = listOf(Lyrics.of(synced, LyricsOrigin.File)!!)
        runBlocking { finder.setOffset(track, find()!!, 100) }
        assertEquals(1, runBlocking { finder.clearDownloaded() })
        assertEquals(listOf(LyricsSource.FILE), cache.rows.values.map { it.source })
    }
}
