package app.nagmedar.ui.radio

import app.nagmedar.data.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** S-1105: a station as a database row and back, and the letters of a station with no logo. */
class StationMappingTest {

    @Test
    fun aStationIsARowAndBack() {
        val station = RadioSamples.jazz.copy(id = 7, homepage = "https://caz.example", favourite = true, lastVotedAt = 5, lastPlayedAt = 9)
        val row = station.toEntity()
        assertEquals("a1-caz", row.stationUuid)
        assertEquals("jazz,gece,enstrümantal", row.tags)
        assertEquals("https://logo.example/caz.png", row.faviconUrl)
        assertEquals(7L, row.id)
        assertEquals(station, row.toStation())
    }

    @Test
    fun noTagsIsNoTextAndBack() {
        val row = RadioSamples.oneVote.toEntity()
        assertNull(row.tags)
        assertEquals(emptyList<String>(), row.toStation().tags)
        assertEquals(emptyList<String>(), RadioStation(name = "x", streamUrl = "http://x.example", tags = " , ,").toStation().tags)
    }

    @Test
    fun aStationOfTheUsersHasNoDirectoryId() {
        val own = Station(uuid = null, name = "Benim Radyom", streamUrl = "http://ev.example:8000/radyo")
        assertTrue(own.isUser)
        assertEquals("url:http://ev.example:8000/radyo", own.key)
        assertNull(own.toEntity().stationUuid)
        assertTrue(own.toEntity().toStation().isUser)
        assertEquals("a1-caz", RadioSamples.jazz.key)
    }

    @Test
    fun theMonogramIsTheInitialsOfTheFirstTwoWords() {
        assertEquals("GC", monogram("Gece Cazı", Locale.forLanguageTag("tr")))
        assertEquals("KF", monogram("Kuzey FM", Locale.ROOT))
        assertEquals("H", monogram("  Haber  ", Locale.ROOT))
        assertEquals("BR", monogram("Bir Radyo Daha", Locale.ROOT))
        // punctuation alone is no letter
        assertEquals("R", monogram("— Radyo", Locale.ROOT))
        assertEquals("", monogram("   ", Locale.ROOT))
        // Turkish dotted capital
        assertEquals("İZ", monogram("istanbul zaman", Locale.forLanguageTag("tr")))
        assertEquals("IZ", monogram("istanbul zaman", Locale.ENGLISH))
    }
}
