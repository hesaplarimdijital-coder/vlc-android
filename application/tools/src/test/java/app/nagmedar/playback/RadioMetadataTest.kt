package app.nagmedar.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1106: a stream's "now playing" text as the notification, the lock screen and the bars show it. */
class RadioMetadataTest {

    private val station = "Kuzey FM"

    @Test
    fun anArtistAndATitleAreSplit() {
        val shown = RadioMetadata.display(station, "Ada Karan - Sabaha Kadar")
        assertEquals("Sabaha Kadar", shown.title)
        assertEquals("Ada Karan", shown.artist)
        // the station goes under them, as the album
        assertEquals(station, shown.album)
        assertTrue(shown.hasTrack)
    }

    @Test
    fun theFirstDashSplits() {
        val shown = RadioMetadata.display(station, "Ada Karan - Sabaha Kadar - Canlı")
        assertEquals("Ada Karan", shown.artist)
        assertEquals("Sabaha Kadar - Canlı", shown.title)
    }

    @Test
    fun enAndEmDashesSplitToo() {
        assertEquals("Ada Karan", RadioMetadata.display(station, "Ada Karan – Sabaha Kadar").artist)
        assertEquals("Ada Karan", RadioMetadata.display(station, "Ada Karan — Sabaha Kadar").artist)
        // the one that comes first
        val mixed = RadioMetadata.display(station, "A — B - C")
        assertEquals("A", mixed.artist)
        assertEquals("B - C", mixed.title)
    }

    @Test
    fun aDashInsideAWordIsNoSeparator() {
        val shown = RadioMetadata.display(station, "Ay-Işığı")
        assertEquals("Ay-Işığı", shown.title)
        // the station is the line under the title
        assertEquals(station, shown.artist)
        assertEquals("", shown.album)
        assertTrue(shown.hasTrack)
    }

    @Test
    fun aTextWithoutAnArtistIsTheTitle() {
        val shown = RadioMetadata.display(station, "Sabah Seansı")
        assertEquals("Sabah Seansı", shown.title)
        assertEquals(station, shown.artist)
    }

    @Test
    fun theStationBeforeTheDashIsNoArtist() {
        val shown = RadioMetadata.display(station, "Kuzey FM - Haberler")
        assertEquals("Haberler", shown.title)
        assertEquals(station, shown.artist)
        assertEquals("", shown.album)
    }

    @Test
    fun nothingPlayingShowsTheStation() {
        for (text in listOf(null, "", "   ", "-", " - ", "...", "Unknown", "N/A", "null", "http://kuzey.example/canli", "www.kuzey.example", station, "kuzey fm")) {
            val shown = RadioMetadata.display(station, text)
            assertEquals("for [$text]", station, shown.title)
            assertEquals("", shown.artist)
            assertEquals("", shown.album)
            assertFalse(shown.hasTrack)
            assertNull(RadioMetadata.track(text, station))
        }
    }

    @Test
    fun aDashWithOneSideEmptyIsNotSplit() {
        assertEquals(StreamTrack(null, "Ada Karan -"), RadioMetadata.track("Ada Karan -", null))
        assertEquals(StreamTrack(null, "- Sabaha Kadar"), RadioMetadata.track("- Sabaha Kadar", null))
    }

    @Test
    fun spacesQuotesAndControlCharactersAreCleaned() {
        assertEquals("Ada Karan - Sabaha Kadar", RadioMetadata.clean("  Ada   Karan \t- Sabaha Kadar\n"))
        assertEquals("Ada Karan - Sabaha Kadar", RadioMetadata.clean("'Ada Karan - Sabaha Kadar'"))
        assertEquals("Ada Karan - Sabaha Kadar", RadioMetadata.clean("\"Ada Karan - Sabaha Kadar\""))
        assertEquals("Can't stop", RadioMetadata.clean("Can't stop"))
        assertEquals("", RadioMetadata.clean(null))
        val shown = RadioMetadata.display(station, "\u0000Ada Karan\u0007 -  Sabaha Kadar ")
        assertEquals("Ada Karan", shown.artist)
        assertEquals("Sabaha Kadar", shown.title)
    }

    @Test
    fun withoutAStationNameTheLinesAreEmpty() {
        val bare = RadioMetadata.display(null, "Sabah Seansı")
        assertEquals("Sabah Seansı", bare.title)
        assertEquals("", bare.artist)
        val split = RadioMetadata.display(" ", "Ada Karan - Sabaha Kadar")
        assertEquals("Ada Karan", split.artist)
        assertEquals("", split.album)
        assertEquals("", RadioMetadata.display(null, null).title)
    }

    @Test
    fun theMetadataChangesWhenTheTitleDoes() {
        // what the session compares to know that the notification and the lock screen need an update
        val before = RadioMetadata.display(station, "Ada Karan - Sabaha Kadar")
        val after = RadioMetadata.display(station, "Ada Karan - Gece Mavisi")
        assertFalse(before == after)
        assertEquals(before, RadioMetadata.display(station, "Ada Karan - Sabaha Kadar"))
    }

    @Test
    fun aStreamShowsItsLogoWhetherOrNotItSendsATitle() {
        // a station with no ICY title still gets its logo as the artwork
        assertEquals("https://logo.example/caz.png", RadioMetadata.logo(isStream = true, artworkMrl = "https://logo.example/caz.png"))
        assertEquals("http://logo.example/caz.png", RadioMetadata.logo(true, " http://logo.example/caz.png "))
        assertEquals("HTTPS://LOGO.EXAMPLE/A.PNG", RadioMetadata.logo(true, "HTTPS://LOGO.EXAMPLE/A.PNG"))
    }

    @Test
    fun onlyAStreamsWebLogoIsUsed() {
        assertNull(RadioMetadata.logo(false, "https://logo.example/caz.png"))
        assertNull(RadioMetadata.logo(true, null))
        assertNull(RadioMetadata.logo(true, ""))
        assertNull(RadioMetadata.logo(true, "file:///sdcard/cover.jpg"))
        assertNull(RadioMetadata.logo(true, "content://art/1"))
        assertNull(RadioMetadata.logo(true, "https://logo.example/a b.png"))
    }
}
