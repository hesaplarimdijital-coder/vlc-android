package app.nagmedar.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which section shows a video by the sections' folders (Settings › Library): Films and Series their
 * own folders, or the library but the folders chosen for Videos; Videos its folders and whatever
 * neither of them shows, so that no video is lost.
 */
class LibraryScopesTest {

    private val root = "/storage/emulated/0"

    private fun item(path: String) =
        VideoItem("file://$path", path.substringAfterLast('/').substringBeforeLast('.'), 2_700_000, 0f, 1920, 1080, null, path)

    private fun scopes(vararg folders: Pair<LibrarySection, String>) =
        folders.fold(LibraryScopes.Empty) { scopes, (section, path) -> scopes.plus(section, SourceFolder(path.removePrefix("-"), exclude = path.startsWith("-"))) }

    private fun split(scopes: LibraryScopes, vararg paths: String): VideoSplit =
        splitVideos(paths.map { item("$root/$it") }, ScopeRules(scopes) { Locations.ofPath(it) }) { Locations.ofPath(it.path) }

    private fun List<VideoItem>.names() = map { it.path.removePrefix("$root/") }

    private val library = arrayOf(
        "Diziler/Kurtlar Vadisi/Kurtlar Vadisi 12. Bölüm.mp4",
        "Diziler/Kuzey Rüzgârı/Sezon 1/S01E01.mkv",
        "Movies/Kuzey Işığı (2024).mkv",
        "DCIM/Camera/VID_20240101_123456.mp4",
        "Download/Dark.S01E02.mkv",
    )

    @Test
    fun aFileOfASeriesFolderThatIsNoEpisodeIsAVideo() {
        // "12. Bölüm" without "N. Sezon" is no episode: the Series page has no place for it, Films is not its place
        for (scopes in listOf(
            scopes(LibrarySection.Series to "$root/Diziler"),
            scopes(LibrarySection.Series to "$root/Diziler", LibrarySection.Movies to "$root/Movies"),
        )) {
            val split = split(scopes, *library)
            assertEquals(listOf("Diziler/Kuzey Rüzgârı/Sezon 1/S01E01.mkv"), split.episodes.names())
            assertEquals(listOf("Movies/Kuzey Işığı (2024).mkv"), split.films.names())
            assertEquals(
                listOf("Diziler/Kurtlar Vadisi/Kurtlar Vadisi 12. Bölüm.mp4", "DCIM/Camera/VID_20240101_123456.mp4", "Download/Dark.S01E02.mkv"),
                split.clips.names(),
            )
        }
    }

    @Test
    fun withoutFoldersEverySectionShowsWhatIsItsOwn() {
        val split = split(LibraryScopes.Empty, *library)
        assertEquals(listOf("Diziler/Kuzey Rüzgârı/Sezon 1/S01E01.mkv", "Download/Dark.S01E02.mkv"), split.episodes.names())
        assertEquals(listOf("Diziler/Kurtlar Vadisi/Kurtlar Vadisi 12. Bölüm.mp4", "Movies/Kuzey Işığı (2024).mkv"), split.films.names())
        assertEquals(listOf("DCIM/Camera/VID_20240101_123456.mp4"), split.clips.names())
    }

    @Test
    fun onlyTheFoldersOfVideosLeaveFilmsAndSeries() {
        val split = split(
            scopes(LibrarySection.Videos to "$root/Ev videoları", LibrarySection.Movies to "$root/Filmler"),
            "Ev videoları/Düğün.mp4",
            "Ev videoları/Dark.S01E03.mkv",
            "Filmler/Arrival (2016).mkv",
            // an episode in the folder of Films is Series' while Series has no folders: never looked up as a film
            "Filmler/The.Expanse.S01E01.mkv",
        )
        assertEquals(listOf("Filmler/The.Expanse.S01E01.mkv"), split.episodes.names())
        assertEquals(listOf("Filmler/Arrival (2016).mkv"), split.films.names())
        assertEquals(listOf("Ev videoları/Düğün.mp4", "Ev videoları/Dark.S01E03.mkv"), split.clips.names())
    }

    @Test
    fun noVideoIsLostWhenEverySectionHasFolders() {
        val scopes = scopes(
            LibrarySection.Movies to "$root/Movies",
            LibrarySection.Series to "$root/Diziler",
            LibrarySection.Videos to "$root/DCIM",
        )
        val split = split(scopes, *library, "WhatsApp/Media/WhatsApp Video/VID-20240101-WA0001.mp4")
        val shown = (split.films + split.episodes + split.clips).map { it.path }.toSet()
        assertEquals((library.toList() + "WhatsApp/Media/WhatsApp Video/VID-20240101-WA0001.mp4").map { "$root/$it" }.toSet(), shown)
        assertTrue(split.clips.names().containsAll(listOf("Download/Dark.S01E02.mkv", "WhatsApp/Media/WhatsApp Video/VID-20240101-WA0001.mp4")))
    }

    @Test
    fun aFolderLeftOutKeepsItsVideosOut() {
        val split = split(scopes(LibrarySection.Videos to "-$root/WhatsApp"), "WhatsApp/Media/WhatsApp Video/VID-20240101-WA0001.mp4", "DCIM/Camera/VID_20240101_123456.mp4")
        assertEquals(listOf("DCIM/Camera/VID_20240101_123456.mp4"), split.clips.names())
        assertTrue(split.films.isEmpty())
    }

    @Test
    fun aFileIsInBothSectionsThatChoseItsFolder() {
        val split = split(scopes(LibrarySection.Movies to "$root/Media", LibrarySection.Videos to "$root/Media/Ev"), "Media/Ev/Doğum günü.mp4", "Media/Film.mkv")
        assertEquals(listOf("Media/Ev/Doğum günü.mp4", "Media/Film.mkv"), split.films.names())
        assertEquals(listOf("Media/Ev/Doğum günü.mp4"), split.clips.names())
    }
}
