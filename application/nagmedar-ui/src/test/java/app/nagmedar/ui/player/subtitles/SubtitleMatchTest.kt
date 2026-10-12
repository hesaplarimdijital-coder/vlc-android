package app.nagmedar.ui.player.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The subtitles found beside “Kuzey Işığı.mkv” in the screenshots: Turkish, its forced lines, English, one without a language. */
internal val sampleFound = listOf(
    SubtitleCandidate("Kuzey Işığı.tr.srt", "file:///films/Kuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.tr.srt", language = "tr"),
    SubtitleCandidate("Kuzey Işığı.tr.forced.srt", "file:///films/Kuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.tr.forced.srt", language = "tr", forced = true),
    SubtitleCandidate("Kuzey Işığı.en.SDH.srt", "file:///films/Kuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.en.SDH.srt", language = "en", sdh = true),
    SubtitleCandidate("Kuzey Işığı.srt", "file:///films/Kuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.srt"),
)

/**
 * S-701: a video's subtitles are the files named as it is, with a language, "forced" or "SDH"
 * after its name, as file names write them; other films' and other files are left out; a VobSub
 * is its index; the user's language comes first.
 */
class SubtitleMatchTest {

    private fun files(vararg names: String) = names.map { it to "file:///films/$it" }

    @Test
    fun theFilmsOwnInTheUsersLanguageFirst() {
        val found = matchSubtitles(
            "Kuzey Işığı.mkv",
            files("Kuzey Işığı.mkv", "Kuzey Işığı.en.srt", "Kuzey Işığı.tr.srt", "Kuzey Işığı.srt", "Kuzey Işığı.tr.forced.srt", "Poster.jpg"),
            prefer = "tr",
        )
        assertEquals(listOf("Kuzey Işığı.tr.srt", "Kuzey Işığı.tr.forced.srt", "Kuzey Işığı.en.srt", "Kuzey Işığı.srt"), found.map { it.name })
        assertEquals(listOf("tr", "tr", "en", null), found.map { it.language })
        assertEquals(listOf(false, true, false, false), found.map { it.forced })
        assertEquals("file:///films/Kuzey Işığı.tr.srt", found.first().uri)
    }

    @Test
    fun languagesAsFileNamesWriteThem() {
        val found = matchSubtitles(
            "Film (2024).mkv",
            files("Film (2024).Turkish.srt", "Film (2024) - ENG.ass", "Film (2024)_ger.vtt", "Film (2024) [Türkçe].ssa", "Film (2024).eng.SDH.srt", "Film (2024).fre.srt"),
        ).associate { it.name to it.language }
        assertEquals(
            mapOf(
                "Film (2024).Turkish.srt" to "tr",
                "Film (2024) - ENG.ass" to "en",
                "Film (2024)_ger.vtt" to "de",
                "Film (2024) [Türkçe].ssa" to "tr",
                "Film (2024).eng.SDH.srt" to "en",
                "Film (2024).fre.srt" to "fr",
            ),
            found,
        )
    }

    @Test
    fun otherFilmsAndOtherFilesAreLeftOut() {
        val found = matchSubtitles("Film.mkv", files("Film 2.tr.srt", "Film Part II.srt", "Filmler.srt", "Film.1080p.srt", "Film.nfo", "Belgesel.tr.srt"))
        assertEquals(emptyList<String>(), found.map { it.name })
    }

    @Test
    fun aVobSubIsItsIndex() {
        val found = matchSubtitles("Film.mkv", files("Film.idx", "Film.sub", "Film.tr.sub"))
        assertEquals(listOf("Film.idx", "Film.tr.sub"), found.map { it.name })
    }

    @Test
    fun hiIsTheHardOfHearing() {
        val found = matchSubtitles("Film.mkv", files("Film.en.hi.srt", "Film.hi.srt")).associateBy { it.name }
        assertEquals("en", found.getValue("Film.en.hi.srt").language)
        assertEquals(true, found.getValue("Film.en.hi.srt").sdh)
        assertNull(found.getValue("Film.hi.srt").language)
        assertEquals(true, found.getValue("Film.hi.srt").sdh)
    }

    @Test
    fun wordsThatTellALanguage() {
        assertEquals("tr", languageOf("tur"))
        assertEquals("tr", languageOf("TR"))
        assertEquals("tr", languageOf("Türkçe"))
        assertEquals("en", languageOf("english"))
        assertEquals("de", languageOf("ger"))
        assertEquals("nl", languageOf("dut"))
        assertNull(languageOf("ii"))
        assertNull(languageOf("1080p"))
        assertNull(languageOf("yify"))
    }

    @Test
    fun subtitleFilesByTheirNames() {
        listOf("a.srt", "a.ASS", "a.ssa", "a.vtt", "a.sub", "a.idx").forEach { assertEquals(it, true, isSubtitleFile(it)) }
        listOf("a.mkv", "a.txt", "a.nfo", "srt").forEach { assertEquals(it, false, isSubtitleFile(it)) }
    }
}
