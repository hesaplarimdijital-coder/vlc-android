package app.nagmedar.ui.convert

import app.nagmedar.audio.convert.ConvertCodec
import app.nagmedar.ui.music.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1010: the new files' names and folders, and the choices as the engine takes them. */
class ConvertPlannerTest {

    private fun track(
        uri: String,
        title: String,
        artist: String? = "Ada Karan",
        albumArtist: String? = null,
        album: String? = "Gece Yolcusu",
        number: Int = 0,
        disc: Int = 0,
        year: Int? = null,
    ) = Track(
        id = 0, uri = uri, title = title, artist = artist, albumArtist = albumArtist, album = album, genre = null,
        year = year, trackNumber = number, discNumber = disc, durationMs = 0, folder = "/m",
    )

    private fun plan(tracks: List<Track>, naming: ConvertNaming = ConvertNaming.Folders, extension: String = "opus") =
        ConvertPlanner.plan(tracks, naming, extension, "Bilinmeyen sanatçı", "Bilinmeyen albüm")

    @Test
    fun foldersOfTheAlbumArtistAndTheAlbum() {
        val items = plan(
            listOf(
                track("file:///m/03.flac", "Çığlık", artist = "Konuk", albumArtist = "Şebnem Ağaoğlu", number = 3),
                track("file:///m/x.flac", "Işık", artist = null, album = null),
            ),
        )
        assertEquals("Şebnem Ağaoğlu/Gece Yolcusu/03 Çığlık.opus", items[0].path)
        assertEquals(listOf("Şebnem Ağaoğlu", "Gece Yolcusu"), items[0].folders)
        assertEquals("03 Çığlık.opus", items[0].name)
        // no number, no tags: the unknown folders, the title alone
        assertEquals("Bilinmeyen sanatçı/Bilinmeyen albüm/Işık.opus", items[1].path)
    }

    @Test
    fun aDiscNumberOnAnAlbumOfSeveral() {
        val items = plan(
            listOf(
                track("file:///m/1.flac", "Bir", number = 1, disc = 1),
                track("file:///m/2.flac", "İki", number = 7, disc = 2),
                track("file:///n/1.flac", "Tek", album = "Başka", number = 4, disc = 1),
            ),
            extension = "m4a",
        )
        assertEquals("1-01 Bir.m4a", items[0].name)
        assertEquals("2-07 İki.m4a", items[1].name)
        // an album of one disc keeps its numbers plain
        assertEquals("04 Tek.m4a", items[2].name)
    }

    @Test
    fun flatNamesWithTheTracksArtist() {
        val items = plan(
            listOf(
                track("file:///m/1.flac", "Bir", artist = "Konuk", albumArtist = "Ada Karan", number = 1),
                track("file:///m/2.flac", "İki", artist = null, albumArtist = null),
            ),
            naming = ConvertNaming.Flat,
            extension = "mp3",
        )
        assertEquals("Konuk - Bir.mp3", items[0].path)
        assertTrue(items[0].folders.isEmpty())
        assertEquals("İki.mp3", items[1].path)
    }

    @Test
    fun twoTracksOfOneNameAreToldApart() {
        val items = plan(
            listOf(
                track("file:///a/1.flac", "Sabah", number = 1),
                track("file:///b/1.flac", "SABAH", number = 1),
                track("file:///c/1.flac", "sabah", number = 1),
            ),
        )
        assertEquals(listOf("01 Sabah.opus", "01 SABAH (2).opus", "01 sabah (3).opus"), items.map { it.name })
    }

    @Test
    fun namesAFileSystemTakes() {
        assertEquals("AC_DC_ Back_ _Black_", ConvertPlanner.clean("AC/DC: Back? \"Black\""))
        assertEquals("_hidden", ConvertPlanner.clean("._hidden"))
        assertEquals("Son", ConvertPlanner.clean("  Son. . "))
        assertEquals("a_b", ConvertPlanner.clean("a\u0001b"))
        assertEquals("_", ConvertPlanner.clean("..."))
        // long names stay within the bytes allowed, not in the middle of a letter
        val long = ConvertPlanner.clean("ş".repeat(300))
        assertTrue(long.toByteArray(Charsets.UTF_8).size <= ConvertPlanner.MAX_NAME_BYTES)
        assertEquals("ş".repeat(100), long)
        assertEquals("ab", ConvertPlanner.cut("ab😀", 5))
    }

    @Test
    fun aTrackWithoutATitleIsNamedByItsFile() {
        assertEquals("03 Gece", ConvertPlanner.fileStem("content://com.android.externalstorage.documents/document/primary%3AM%C3%BCzik%2F03%20Gece.flac"))
        assertEquals("a+b", ConvertPlanner.fileStem("file:///m/a+b.flac#cue=2"))
        val item = plan(listOf(track("file:///m/05%20Yol.flac", "", number = 5))).single()
        assertEquals("05 05 Yol.opus", item.name)
        assertEquals("05 Yol", item.title)
    }

    @Test
    fun aCueTrackCarriesTheSheetsTags() {
        val cue = track("file:///m/album.flac#cue=2", "İkinci", artist = "Ada Karan", albumArtist = "Çeşitli", number = 2, year = 1997)
        val whole = track("file:///m/whole.flac", "Bütün", number = 1)
        val items = plan(listOf(cue, whole))
        assertTrue(items[0].isCuePart)
        assertEquals(
            mapOf("TITLE" to "İkinci", "ARTIST" to "Ada Karan", "ALBUMARTIST" to "Çeşitli", "ALBUM" to "Gece Yolcusu", "DATE" to "1997", "TRACKNUMBER" to "2"),
            items[0].tags,
        )
        // a file's own tags go along as they are
        assertFalse(items[1].isCuePart)
        assertTrue(items[1].tags.isEmpty())
    }

    @Test
    fun theEnginesSettings() {
        val opus = ConvertOptions(preset = ConvertPreset.Opus192, rate = ConvertRate.Hz44100).settings()
        assertEquals(ConvertCodec.Opus, opus.codec)
        assertEquals(192_000, opus.bitrate)
        // Opus is always 48 kHz: the rate asked is not passed on
        assertEquals(0, opus.rate)
        val v0 = ConvertOptions(preset = ConvertPreset.Mp3V0, rate = ConvertRate.Hz48000).settings()
        assertEquals(0, v0.bitrate)
        assertEquals(0, v0.vbrQuality)
        assertEquals(48_000, v0.rate)
        val cbr = ConvertOptions(preset = ConvertPreset.Mp3Cbr320).settings()
        assertEquals(320_000, cbr.bitrate)
        assertEquals(0, cbr.rate)
        val aac = ConvertOptions(preset = ConvertPreset.Aac256, rate = ConvertRate.Hz44100).settings()
        assertEquals(ConvertCodec.Aac, aac.codec)
        assertEquals(256_000, aac.bitrate)
        assertEquals(44_100, aac.rate)
        assertFalse(ConvertOptions(preset = ConvertPreset.Opus128).rateApplies)
        assertTrue(ConvertOptions(preset = ConvertPreset.Aac256).rateApplies)
    }

    @Test
    fun thePresetsByTheirKeys() {
        ConvertPreset.entries.forEach { assertEquals(it, ConvertPreset.of(it.key)) }
        assertEquals(ConvertPreset.DEFAULT, ConvertPreset.of("flac"))
        assertEquals(ConvertPreset.DEFAULT, ConvertPreset.of(null))
        assertEquals(listOf(128, 160, 192, null, 320, 256), ConvertPreset.entries.map { it.kbps })
        assertEquals(ConvertRate.Source, ConvertRate.of("nonsense"))
        assertEquals(ConvertNaming.Flat, ConvertNaming.of("Flat"))
    }
}
