package app.nagmedar.ui.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

/** S-804: M3U, M3U8, PLS and XSPF read and written, relative paths kept; a list's files found in the library. */
class PlaylistFilesTest {

    private val music = "/storage/emulated/0/Music"
    private val itri = "$music/Itri"

    @Test
    fun anM3uWithItsNamesLengthsAndPaths() {
        val text = """
            #EXTM3U
            #PLAYLIST:Akşam
            #EXTINF:241,Kadri Erdem - Neva Kâr
            01 Neva Kâr.flac
            #EXTINF:-1,Yağmur
            ..\Cemre Kaya\Yağmur.mp3
            #EXTINF:-1 tvg-name="Radyo, gece",Gece Radyosu
            http://radio.example/stream
            /storage/emulated/0/Music/Ada.flac
            # a comment
            D:\Müzik\Deniz Su\Liman.flac
        """.trimIndent()
        assertEquals(
            listOf(
                PlaylistEntry("$itri/01 Neva Kâr.flac", "Neva Kâr", "Kadri Erdem", durationMs = 241_000),
                PlaylistEntry("$music/Cemre Kaya/Yağmur.mp3", "Yağmur"),
                PlaylistEntry("http://radio.example/stream", "Gece Radyosu"),
                PlaylistEntry("$music/Ada.flac"),
                PlaylistEntry("D:/Müzik/Deniz Su/Liman.flac"),
            ),
            PlaylistFiles.parse(text, PlaylistFormat.M3U, itri),
        )
    }

    @Test
    fun anOldTurkishM3uAndAByteOrderMark() {
        val text = "#EXTINF:10,Şarkı Ğ\nşarkı ığüöç.mp3\n"
        assertEquals(text, PlaylistFiles.decode(text.toByteArray(Charset.forName("windows-1254")), PlaylistFormat.M3U))
        assertEquals(text, PlaylistFiles.decode(text.toByteArray(Charsets.UTF_8), PlaylistFormat.M3U))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        assertEquals(text, PlaylistFiles.decode(bom, PlaylistFormat.M3U8))
        assertEquals("$itri/şarkı ığüöç.mp3", PlaylistFiles.parse(PlaylistFiles.decode(bom, PlaylistFormat.M3U8), PlaylistFormat.M3U8, itri).single().location)
    }

    @Test
    fun aPls() {
        val text = """
            [playlist]
            File1=01 Neva Kâr.flac
            Title1=Kadri Erdem - Neva Kâr
            Length1=498
            File2=http://radio.example/stream
            Title2=Radyo
            Length2=-1
            NumberOfEntries=2
            Version=2
        """.trimIndent()
        assertEquals(
            listOf(
                PlaylistEntry("$itri/01 Neva Kâr.flac", "Neva Kâr", "Kadri Erdem", durationMs = 498_000),
                PlaylistEntry("http://radio.example/stream", "Radyo"),
            ),
            PlaylistFiles.parse(text, PlaylistFormat.PLS, itri),
        )
    }

    @Test
    fun anXspf() {
        val text = """
            <?xml version="1.0" encoding="UTF-8"?>
            <playlist version="1" xmlns="http://xspf.org/ns/0/">
              <title>Akşam</title>
              <trackList>
                <track>
                  <location>file:///storage/emulated/0/Music/Itri/01%20Neva%20K%C3%A2r.flac</location>
                  <title>Neva Kâr</title>
                  <creator>Kadri Erdem</creator>
                  <album>Itrî&apos;nin İzinde &amp; dahası</album>
                  <duration>498000</duration>
                </track>
                <track><location>../Cemre%20Kaya/Ya%C4%9Fmur+1.mp3</location></track>
                <track><location>file:///D:/M%C3%BCzik/Liman.flac</location></track>
                <track><title>no location</title></track>
              </trackList>
            </playlist>
        """.trimIndent()
        assertEquals(
            listOf(
                PlaylistEntry("$itri/01 Neva Kâr.flac", "Neva Kâr", "Kadri Erdem", "Itrî'nin İzinde & dahası", 498_000),
                PlaylistEntry("$music/Cemre Kaya/Yağmur+1.mp3"),
                PlaylistEntry("D:/Müzik/Liman.flac"),
            ),
            PlaylistFiles.parse(text, PlaylistFormat.XSPF, itri),
        )
    }

    @Test
    fun pathsAreWrittenRelativeInTheirOwnTreeOnly() {
        assertEquals("01.flac", PlaylistFiles.relative("$itri/01.flac", itri))
        assertEquals("../Cemre Kaya/Yağmur.mp3", PlaylistFiles.relative("$music/Cemre Kaya/Yağmur.mp3", itri))
        assertEquals("../Music/Ada.flac", PlaylistFiles.relative("$music/Ada.flac", "/storage/emulated/0/Playlists"))
        // another volume: as it is
        assertEquals("/storage/1A2B-3C4D/Music/Ada.flac", PlaylistFiles.relative("/storage/1A2B-3C4D/Music/Ada.flac", itri))
        // no folder known, a URL, a drive: as they are
        assertEquals("$music/Ada.flac", PlaylistFiles.relative("$music/Ada.flac", null))
        assertEquals("http://radio.example/stream", PlaylistFiles.relative("http://radio.example/stream", itri))
        assertEquals("/a/c/d", PlaylistFiles.normalize("/a/b/../c/./d/"))
    }

    private val entries = listOf(
        PlaylistEntry("$itri/01 Neva Kâr.flac", "Neva Kâr", "Kadri Erdem", "Itrî'nin İzinde", 498_000),
        PlaylistEntry("$music/Cemre Kaya/Yağmur & Gece #2.mp3", "Yağmur", "Cemre Kaya", durationMs = 231_400),
        PlaylistEntry("/storage/1A2B-3C4D/Ada.flac", "Ada"),
        PlaylistEntry("http://radio.example/stream?id=1&q=2", "Radyo"),
    )

    @Test
    fun eachFormatReadsBackWhatItWrote() {
        for (format in PlaylistFormat.entries) {
            val text = PlaylistFiles.write(entries, format, itri, "Akşam")
            val back = PlaylistFiles.parse(text, format, itri)
            assertEquals(format.name, entries.map { it.location }, back.map { it.location })
            assertEquals(format.name, entries.map { it.title }, back.map { it.title })
            // M3U and PLS keep whole seconds, XSPF milliseconds
            val lengths = if (format == PlaylistFormat.XSPF) listOf(498_000L, 231_400, 0, 0) else listOf(498_000L, 231_000, 0, 0)
            assertEquals(format.name, lengths, back.map { it.durationMs })
        }
    }

    @Test
    fun theWrittenFiles() {
        assertEquals(
            """
                #EXTM3U
                #PLAYLIST:Akşam
                #EXTINF:498,Kadri Erdem - Neva Kâr
                01 Neva Kâr.flac
                #EXTINF:231,Cemre Kaya - Yağmur
                ../Cemre Kaya/Yağmur & Gece #2.mp3
                #EXTINF:-1,Ada
                /storage/1A2B-3C4D/Ada.flac
                #EXTINF:-1,Radyo
                http://radio.example/stream?id=1&q=2

            """.trimIndent(),
            PlaylistFiles.write(entries, PlaylistFormat.M3U8, itri, "Akşam"),
        )
        val xspf = PlaylistFiles.write(entries, PlaylistFormat.XSPF, itri, "Akşam")
        assertEquals(
            listOf(
                "01%20Neva%20K%C3%A2r.flac",
                "../Cemre%20Kaya/Ya%C4%9Fmur%20%26%20Gece%20%232.mp3",
                "file:///storage/1A2B-3C4D/Ada.flac",
                "http://radio.example/stream?id=1&amp;q=2",
            ),
            Regex("<location>(.*?)</location>").findAll(xspf).map { it.groupValues[1] }.toList(),
        )
        // with no folder known, absolute
        assertEquals("$itri/01 Neva Kâr.flac", PlaylistFiles.write(entries.take(1), PlaylistFormat.M3U, null, "").lines()[2])
    }

    @Test
    fun formatsByTheirNames() {
        assertEquals(PlaylistFormat.M3U8, PlaylistFormat.of("Akşam.M3U8"))
        assertEquals(PlaylistFormat.XSPF, PlaylistFormat.of("a.b.xspf"))
        assertNull(PlaylistFormat.of("liste.txt"))
        assertNull(PlaylistFormat.of("m3u"))
    }

    @Test
    fun aListsFilesAreFoundInTheLibrary() {
        val library = listOf(
            "$itri/01 Neva Kâr.flac",
            "$music/Cemre Kaya/Yağmur.mp3",
            "$music/Albüm A/01.flac",
            "$music/Albüm B/01.flac",
            "$music/Deniz Su/Liman.flac",
        )
        val matcher = PlaylistMatcher(library)
        assertEquals(0, matcher.match("$itri/01 Neva Kâr.flac"))
        assertEquals(1, matcher.match("/STORAGE/emulated/0/Music/cemre kaya/Yağmur.mp3"))
        // made on a computer: the name is enough when one file has it
        assertEquals(4, matcher.match("D:/Müzik/Deniz Su/Liman.flac"))
        // of several, the folder above decides
        assertEquals(3, matcher.match("/home/ece/Music/Albüm B/01.flac"))
        assertNull(matcher.match("/home/ece/Music/Albüm C/01.flac"))
        assertNull(matcher.match("/home/ece/01.flac"))
        // a stream, a file that is not there
        assertNull(matcher.match("http://radio.example/01.flac"))
        assertNull(matcher.match("$music/yok.flac"))
    }
}
