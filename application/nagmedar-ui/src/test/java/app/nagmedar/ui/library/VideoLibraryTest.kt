package app.nagmedar.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-801: films and episodes from file names, shows with what comes next, folders, filters, order. */
class VideoLibraryTest {

    private fun item(path: String, plays: Long = 0, progress: Float = 0f, added: Long = 0, width: Int = 1920, height: Int = 1080,
                     sidecars: Set<String> = emptySet()) =
        VideoItem("file://$path", path.substringAfterLast('/').substringBeforeLast('.'), 2_700_000, progress, width, height, null, path,
            added, plays, sidecars)

    @Test
    fun episodeNames() {
        fun parse(path: String) = EpisodeParser.parse(path)
        assertEquals(EpisodeInfo("The Expanse", 1, 2, 2, "Remember the Cant"),
            parse("/T7/Diziler/The.Expanse.S01E02.Remember.the.Cant.2160p.WEB-DL.DV.mkv"))
        assertEquals(EpisodeInfo("Dark", 2, 3, 4, "Ghosts"), parse("/T7/Dark S02E03-E04 Ghosts.mkv"))
        assertEquals(EpisodeInfo("Dark", 1, 1, 2, ""), parse("/T7/Dark.S01E01E02.1080p.mkv"))
        assertEquals(EpisodeInfo("show", 1, 2, 2, "title"), parse("/T7/show_1x02_title.mkv"))
        assertEquals(EpisodeInfo("Leyla ile Mecnun", 1, 12, 12, ""), parse("/T7/Leyla ile Mecnun 1. Sezon 12. Bölüm.mp4"))
        assertEquals(EpisodeInfo("Kuzey Rüzgârı", 2, 5, 5, ""), parse("/T7/Diziler/Kuzey Rüzgârı/Sezon 2/S02E05.mkv"))
        assertEquals(EpisodeInfo("Kuzey Rüzgârı", 1, 1, 1, ""), parse("/T7/Diziler/Kuzey Rüzgârı/S01E01.mkv"))
        assertEquals("Doctor Who", parse("/T7/Doctor.Who.(2005).S01E01.Rose.mkv")!!.show)
        // films stay films
        assertNull(parse("/T7/Filmler/Kuzey Işığı (2024) 2160p DV.mkv"))
        assertNull(parse("/T7/Filmler/Movie.2019.1920x1080.mkv"))
        assertNull(parse("/T7/Filmler/2001 A Space Odyssey.mkv"))
    }

    @Test
    fun showsGroupTheirSeasonsAndKnowWhatComesNext() {
        val library = buildVideoLibrary(
            listOf(
                item("/T7/Kuzey Rüzgârı/Sezon 1/S01E02.mkv", plays = 1),
                item("/T7/Kuzey Rüzgârı/Sezon 1/S01E01.mkv", plays = 1),
                item("/T7/Kuzey Rüzgârı/Sezon 2/S02E01.mkv"),
                item("/T7/kuzey rüzgârı S01E03.mkv"),
                item("/T7/Filmler/Kuzey Işığı.mkv"),
            ),
        )
        assertEquals(listOf("Kuzey Işığı"), library.movies.map { it.title })
        val show = library.shows.single()
        assertEquals("Kuzey Rüzgârı", show.title)
        assertEquals(listOf(1, 2), show.seasons.map { it.number })
        assertEquals(listOf("S01E01", "S01E02", "S01E03"), show.seasons[0].episodes.map { it.code })
        assertEquals(2, show.watched)
        assertEquals("S01E03", show.next!!.code)
    }

    @Test
    fun anEpisodeInProgressComesFirst() {
        val show = buildVideoLibrary(
            listOf(
                item("/T7/Dark/S01E01.mkv", plays = 1),
                item("/T7/Dark/S01E02.mkv", progress = 0.4f),
                item("/T7/Dark/S01E03.mkv", plays = 1),
            ),
        ).shows.single()
        assertEquals("S01E02", show.next!!.code)
        val done = buildVideoLibrary(listOf(item("/T7/Dark/S01E01.mkv", plays = 1))).shows.single()
        assertNull(done.next)
    }

    @Test
    fun filmsByFolderAndInProgress() {
        val library = buildVideoLibrary(
            listOf(
                item("/T7/Filmler/Bilim Kurgu/Kuzey Işığı.mkv", progress = 0.52f, added = 2),
                item("/T7/Filmler/Bilim Kurgu/Cam Şehir.mkv"),
                item("/sdcard/Movies/Mavi Kanal.mkv", progress = 0.1f, added = 5),
            ),
        )
        assertEquals(listOf("Bilim Kurgu", "Movies"), library.folders.map { it.name })
        assertEquals(2, library.folders[0].items.size)
        assertEquals(listOf("Mavi Kanal", "Kuzey Işığı"), library.continueMovies.map { it.title })
    }

    @Test
    fun filtersCombineAndOrderSorts() {
        val kuzey = item("/T7/Kuzey Işığı.mkv", width = 3840, height = 2160, added = 3)
        val liman = item("/T7/Sessiz Liman.mkv", width = 3840, height = 2160, added = 1, sidecars = setOf("tur"))
        val mavi = item("/T7/Mavi Kanal.mkv", plays = 1, added = 2)
        val tech = mapOf(kuzey.key to TechSamples.kuzeyIsigi, liman.key to TechSamples.sessizLiman, mavi.key to TechSamples.maviKanal)
        val all = listOf(kuzey, liman, mavi)
        assertEquals(listOf(kuzey, liman), all.filtered(setOf(MovieFilter.UHD), tech))
        assertEquals(listOf(kuzey), all.filtered(setOf(MovieFilter.DolbyVision), tech))
        assertEquals(listOf(kuzey, liman), all.filtered(setOf(MovieFilter.HDR, MovieFilter.Atmos), tech))
        // a subtitle file next to it, or an embedded Turkish track
        assertEquals(listOf(kuzey, liman), all.filtered(setOf(MovieFilter.TurkishSubtitles), tech))
        assertEquals(listOf(kuzey, liman), all.filtered(setOf(MovieFilter.Unwatched), tech))
        assertEquals(all, all.filtered(emptySet(), tech))
        assertEquals(listOf(kuzey, mavi, liman), all.ordered(MovieOrder.Added, tech))
        assertEquals(listOf(kuzey, mavi, liman), all.ordered(MovieOrder.Title, tech))
        // Dolby Vision before HDR10 at the same size, then HD
        assertEquals(listOf(kuzey, liman, mavi), all.ordered(MovieOrder.Resolution, tech))
    }

    @Test
    fun sidecarSubtitles() {
        val files = listOf("Kuzey Işığı.mkv", "Kuzey Işığı.tr.srt", "Kuzey Işığı.English.ass", "Kuzey Işığı.srt", "Kuzey Işığı 2.srt",
            "Kuzey Işığı2.tr.srt", "Başka.tr.srt", "Kuzey Işığı.nfo")
        assertEquals(setOf("tur", "eng", "und"), Sidecars.languages("Kuzey Işığı.mkv", files))
        assertTrue(Sidecars.languages("Mavi Kanal.mkv", files).isEmpty())
    }
}
