package app.nagmedar.ui.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** S-803: the library's ways through the tracks, and its depth and rate filter. */
class MusicLibraryTest {

    private val turkish = Locale.forLanguageTag("tr")

    private fun track(
        id: Long,
        title: String,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        genre: String? = null,
        year: Int? = null,
        disc: Int = 1,
        number: Int = 0,
        folder: String = "/Music",
        tech: TrackTech? = null,
    ) = Track(id, "file://$folder/$id.flac", title, artist, albumArtist, album, genre, year, number, disc, 200_000, folder, tech = tech)

    private val flac24 = TrackTech("flac", "flac", 96_000, 24, 2, false, composer = "Buhûrîzâde Mustafa Itrî")
    private val flac16 = TrackTech("flac", "flac", 44_100, 16, 2, false)
    private val mp3 = TrackTech("mp3", "mp3", 44_100, 0, 2, true, bitrate = 320_000)
    private val dsd = TrackTech("dsd_lsbf_planar", "dsf", 352_800, 0, 2, true, dsdRate = 2_822_400)

    private val tracks = listOf(
        track(1, "Neva Kâr", "Kadri Erdem", "Itrî", genre = "Klasik", year = 1999, number = 2, tech = flac24),
        track(2, "Segâh Tekbîr", "Kadri Erdem", "Itrî", genre = "klasik", year = 1999, number = 1, tech = flac24),
        track(3, "Gece", "Cemre Kaya", "Gece Yolcusu", genre = "Rock", year = 1977, tech = mp3),
        track(4, "Yağmur", "Cemre Kaya", "Gece Yolcusu", genre = "Rock", year = 1977, tech = flac16),
        track(5, "Uzak", "Çağla Akın", "Tek", genre = "Pop", year = 1988, tech = dsd),
        track(6, "Duet", "Deniz Su", "Konuk", albumArtist = "Çeşitli Sanatçılar", genre = "Pop", year = 2004, folder = "/Music/Konuk"),
        track(7, "İsimsiz", folder = "/Download"),
    )

    private val library = MusicLibrary(tracks, turkish)

    @Test
    fun artistsInTurkishOrderUnknownLast() {
        val artists = library.groups(MusicView.Artists)
        // the album artist files the duet; "Ç" after "C", before "D"; the untagged track last
        assertEquals(listOf("Cemre Kaya", "Çağla Akın", "Çeşitli Sanatçılar", "Kadri Erdem", null), artists.map { it.title })
        assertEquals(listOf(4L, 3L), artists[0].tracks.map { it.id }.sortedDescending())
    }

    @Test
    fun albumsByArtistThenYearTracksInAlbumOrder() {
        val albums = library.groups(MusicView.Albums)
        assertEquals("Gece Yolcusu", albums.first().title)
        val itri = albums.single { it.title == "Itrî" }
        assertEquals("Kadri Erdem", itri.artist)
        assertEquals(1999, itri.year)
        // track 1 before track 2
        assertEquals(listOf("Segâh Tekbîr", "Neva Kâr"), itri.tracks.map { it.title })
        assertNull(albums.last().title)
    }

    @Test
    fun genresFoldCaseYearsNewestFirst() {
        val genres = library.groups(MusicView.Genres)
        assertEquals(listOf("Klasik", "Pop", "Rock", null), genres.map { it.title })
        assertEquals(2, genres.first().tracks.size)
        assertEquals(listOf("2004", "1999", "1988", "1977", null), library.groups(MusicView.Years).map { it.title })
    }

    @Test
    fun composersFromTheProbeAndFolders() {
        val composers = library.groups(MusicView.Composers)
        assertEquals(listOf("Buhûrîzâde Mustafa Itrî", null), composers.map { it.title })
        assertEquals(2, composers.first().tracks.size)
        assertEquals(listOf("/Download", "/Music", "/Music/Konuk"), library.groups(MusicView.Folders).map { it.title })
    }

    @Test
    fun theDepthAndRateFilter() {
        assertEquals(Depth.Bits24, flac24.depth)
        assertEquals(RateBand.R96, flac24.rateBand)
        assertEquals(Depth.Lossy, mp3.depth)
        assertFalse(mp3.lossless)
        assertEquals(Depth.Dsd, dsd.depth)
        assertTrue(dsd.lossless)
        assertNull(dsd.rateBand)
        val hiRes = TechFilter(depths = setOf(Depth.Bits24, Depth.Dsd))
        assertEquals(setOf(1L, 2L, 5L), library.groups(MusicView.Tracks, hiRes).single().tracks.map { it.id }.toSet())
        val cd = TechFilter(depths = setOf(Depth.Bits16), rates = setOf(RateBand.R44))
        assertEquals(listOf(4L), library.groups(MusicView.Tracks, cd).single().tracks.map { it.id })
        // tracks the probe has not read pass only without a filter
        assertEquals(7, library.groups(MusicView.Tracks).single().tracks.size)
        assertEquals(listOf("Klasik"), library.groups(MusicView.Genres, TechFilter(rates = setOf(RateBand.R96))).map { it.title })
    }

    @Test
    fun releaseYearsFromTheMedialibrary() {
        // "date=2026" as the medialibrary keeps it: 1 January 2026, 00:00 UTC in seconds
        assertEquals(2026, releaseYearOf(1_767_225_600))
        assertEquals(1999, releaseYearOf(1999))
        assertEquals(9999, releaseYearOf(9999))
        assertNull(releaseYearOf(0))
        assertNull(releaseYearOf(-1))
    }
}
