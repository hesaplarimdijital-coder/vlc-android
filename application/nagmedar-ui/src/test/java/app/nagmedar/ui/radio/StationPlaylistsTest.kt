package app.nagmedar.ui.radio

import app.nagmedar.ui.music.PlaylistFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1106: stations from M3U, M3U8 and PLS files, as the testmedia/generate.py radio fixtures and their malformed variants have them. */
class StationPlaylistsTest {

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/radio/$name")) { "no test resource $name" }.use { it.readBytes() }

    private fun read(name: String) = StationPlaylists.read(resource(name), name)

    private fun found(name: String): StationImport.Found = read(name) as StationImport.Found

    @Test
    fun theM3u8OfTheTestMedia() {
        val result = found("stations.m3u8")
        assertEquals(
            listOf(
                PlaylistStation("Nağmedar Test FM (AAC)", "https://example.org/stream/aac", "https://example.org/logo.png", "Test"),
                PlaylistStation("Nağmedar Test FM (MP3)", "https://example.org/stream/mp3"),
            ),
            result.stations,
        )
        assertEquals(0, result.skipped)
    }

    @Test
    fun thePlsOfTheTestMedia() {
        val result = found("station.pls")
        assertEquals(listOf(PlaylistStation("Nağmedar Test FM", "https://example.org/stream/aac")), result.stations)
    }

    @Test
    fun anIptvListKeepsItsLogosGroupsAndNames() {
        val stations = found("iptv.m3u8").stations
        assertEquals(4, stations.size)
        // the comma inside the attributes is not the name's; the name is not split into artist and title
        assertEquals(PlaylistStation("Gece Cazı - 24 saat", "https://example.org/stream/caz.mp3", "https://example.org/caz.png", "Caz, Blues"), stations[0])
        // #EXTGRP gives the group of the entries after it
        assertEquals(PlaylistStation("Haber Hattı", "http://example.org:8000/haber", null, "Haber"), stations[1])
        // no name after the comma: tvg-name
        assertEquals(PlaylistStation("Yalnız Ad", "rtsp://example.org/yalniz", null, "Müzik"), stations[2])
        // no #EXTINF at all: the host names it
        assertEquals("example.org", stations[3].name)
    }

    @Test
    fun anOldTurkishListInWindows1254() {
        val stations = found("turkce.m3u").stations
        assertEquals(listOf("Şarkılar Işığı İstanbul", "Çağrı Radyo"), stations.map { it.name })
    }

    @Test
    fun onlyStreamAddressesAreStationsAndEachOnce() {
        val result = found("mixed.m3u8")
        assertEquals(listOf("Kopya" to "https://example.org/stream/aac", "Geçerli" to "mms://example.org/live"), result.stations.map { it.name to it.url })
        // a path on the device, a relative entry, a file address, an address with a space, a repeat, a script
        assertEquals(6, result.skipped)
    }

    @Test
    fun aNameWithBrokenQuotesStillGivesTheStation() {
        val stations = found("broken-quotes.m3u8").stations
        assertEquals(1, stations.size)
        assertEquals("Tırnak FM", stations[0].name)
        assertEquals("https://example.org/stream/tirnak", stations[0].url)
        // the "logo" the broken attribute swallowed has spaces in it: none
        assertNull(stations[0].logoUrl)
    }

    @Test
    fun aTruncatedPlsGivesWhatIsWhole() {
        val stations = found("truncated.pls").stations
        assertEquals(listOf("Bir" to "https://example.org/stream/bir", "example.org" to "https://example.org/stream/uc"), stations.map { it.name to it.url })
    }

    @Test
    fun filesWithNoStationAreEmpty() {
        assertEquals(StationImport.Empty, read("empty.m3u8"))
        assertEquals(StationImport.Empty, read("no-urls.m3u8"))
        assertEquals(StationImport.Empty, read("no-entries.pls"))
        assertEquals(StationImport.Empty, read("binary.m3u"))
    }

    @Test
    fun filesThatAreNoPlaylistAreSaidSo() {
        assertEquals(StationImport.NotAPlaylist, read("not-a-playlist.txt"))
        assertEquals(StationImport.NotAPlaylist, read("list.xspf"))
        assertEquals(StationImport.NotAPlaylist, StationPlaylists.read(ByteArray(0), "bos"))
    }

    @Test
    fun aFileWithoutAnExtensionIsReadByItsFirstBytes() {
        val stations = found("sniffed.txt").stations
        assertEquals(listOf("Adsız uzantı"), stations.map { it.name })
    }

    @Test
    fun aHugeFileIsNotAStationList() {
        assertEquals(StationImport.TooLarge, StationPlaylists.read(ByteArray(StationPlaylists.MAX_BYTES + 1) { '#'.code.toByte() }, "dev.m3u8"))
    }

    @Test
    fun aLongListIsCutAtTheLimit() {
        val text = buildString {
            append("#EXTM3U\n")
            repeat(StationPlaylists.MAX_STATIONS + 100) { append("#EXTINF:-1,Radyo $it\nhttps://example.org/stream/$it\n") }
        }
        val result = StationPlaylists.parse(text, PlaylistFormat.M3U8) as StationImport.Found
        assertEquals(StationPlaylists.MAX_STATIONS, result.stations.size)
        assertEquals(100, result.skipped)
    }

    @Test
    fun namesAreCleanedAndShortened() {
        val long = "A".repeat(500)
        val text = "#EXTM3U\n#EXTINF:-1,  \"Gece\t\tCazı\"  \nhttps://example.org/a\n#EXTINF:-1,$long\nhttps://example.org/b\n"
        val stations = (StationPlaylists.parse(text, PlaylistFormat.M3U) as StationImport.Found).stations
        assertEquals("Gece Cazı", stations[0].name)
        assertEquals(120, stations[1].name.length)
    }

    @Test
    fun whatIsAStreamAddress() {
        assertTrue(StationPlaylists.isStream("https://example.org/s"))
        assertTrue(StationPlaylists.isStream("HTTP://example.org:8000/s;"))
        assertTrue(StationPlaylists.isStream("rtsp://10.0.0.2/live"))
        assertFalse(StationPlaylists.isStream("http://"))
        assertFalse(StationPlaylists.isStream("http:///x"))
        assertFalse(StationPlaylists.isStream("ftp://example.org/x"))
        assertFalse(StationPlaylists.isStream("content://media/1"))
        assertFalse(StationPlaylists.isStream("example.org/stream"))
        assertFalse(StationPlaylists.isStream("https://example.org/a b"))
    }
}
