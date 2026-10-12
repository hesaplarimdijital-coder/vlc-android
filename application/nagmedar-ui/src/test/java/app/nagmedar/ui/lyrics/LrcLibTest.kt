package app.nagmedar.ui.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** S-1003: what LRCLIB is asked, and its answers read (as lrclib.net gives them). */
@RunWith(AndroidJUnit4::class)
class LrcLibTest {

    private val query = LyricsQuery("Ada Karan", "Sabaha Kadar", "Gece Mavisi", 242)

    @Test
    fun theAddresses() {
        assertEquals(
            "https://lrclib.net/api/get?artist_name=Ada%20Karan&track_name=Sabaha%20Kadar&album_name=Gece%20Mavisi&duration=242",
            LrcLib.exactUrl(OkHttpLrcLib.BASE_URL, query).toString(),
        )
        // no album, no length: left out
        assertEquals(
            "https://lrclib.net/api/get?artist_name=%C3%87a%C4%9Fla&track_name=Uzak",
            LrcLib.exactUrl(OkHttpLrcLib.BASE_URL, LyricsQuery("Çağla", "Uzak", " ", null)).toString(),
        )
        assertEquals(
            "https://lrclib.net/api/search?track_name=Sabaha%20Kadar&artist_name=Ada%20Karan",
            LrcLib.searchUrl(OkHttpLrcLib.BASE_URL, query).toString(),
        )
        assertEquals("Nagmedar 3.6.0 (Android)", OkHttpLrcLib.userAgent("3.6.0"))
    }

    @Test
    fun oneRecord() {
        val synced = LrcLib.one(
            """{"id":3396226,"trackName":"Sabaha Kadar","artistName":"Ada Karan","albumName":"Gece Mavisi","duration":242.0,"instrumental":false,""" +
                """"plainLyrics":"Gece mavisi\nçöktü şehre","syncedLyrics":"[00:12.30] Gece mavisi\n[00:17.50] çöktü şehre"}""",
        )!!
        assertEquals("[00:12.30] Gece mavisi\n[00:17.50] çöktü şehre", synced.synced)
        assertEquals("Gece mavisi\nçöktü şehre", synced.plain)
        assertFalse(synced.instrumental)
        val plain = LrcLib.one("""{"id":1,"instrumental":false,"plainLyrics":"Sadece düz","syncedLyrics":null}""")!!
        assertNull(plain.synced)
        assertEquals("Sadece düz", plain.plain)
        val instrumental = LrcLib.one("""{"id":2,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}""")!!
        assertTrue(instrumental.instrumental)
        assertNull(LrcLib.one("""{"id":3,"instrumental":false,"plainLyrics":"","syncedLyrics":null}"""))
    }

    @Test
    fun theBestOfASearch() {
        val results = """[
            {"id":1,"duration":180.0,"instrumental":false,"plainLyrics":"başka sürüm","syncedLyrics":"[00:01.00]başka sürüm"},
            {"id":2,"duration":241.0,"instrumental":false,"plainLyrics":"düz","syncedLyrics":null},
            {"id":3,"duration":243.5,"instrumental":false,"plainLyrics":"düz","syncedLyrics":"[00:01.00]zamanlı"},
            {"id":4,"duration":242.0,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}
        ]"""
        // about the track's length, timed first: the 180 s one is another recording
        assertEquals("[00:01.00]zamanlı", LrcLib.best(results, 242)!!.synced)
        // without a length: the first timed one
        assertEquals("[00:01.00]başka sürüm", LrcLib.best(results, null)!!.synced)
        assertNull(LrcLib.best(results, 600))
        assertNull(LrcLib.best("[]", 242))
    }

    @Test
    fun aGarbledAnswerIsTheServers() {
        try {
            LrcLib.one("<html>Bad gateway</html>")
            fail()
        } catch (e: LyricsFailure.Server) {
            assertEquals(200, e.status)
        }
    }

    @Test
    fun retryAfter() {
        assertEquals(120_000L, LrcLib.retryAfterMs(" 120 "))
        assertNull(LrcLib.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(LrcLib.retryAfterMs(null))
    }
}
