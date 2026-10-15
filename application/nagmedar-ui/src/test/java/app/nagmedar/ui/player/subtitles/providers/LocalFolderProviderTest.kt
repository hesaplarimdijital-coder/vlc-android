package app.nagmedar.ui.player.subtitles.providers

import app.nagmedar.ui.player.subtitles.OnlineFailure
import app.nagmedar.ui.player.subtitles.OnlineProblem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-707: the user's own folder as a provider: searched by the film's name, no network, no key. */
class LocalFolderProviderTest {

    private class Files(var folder: Boolean = true, names: List<String>) : LocalSubtitleFiles {
        val files = names.map { LocalSubtitleFile(it, "content://folder/$it") }
        val contents = mutableMapOf<String, ByteArray>()

        override fun hasFolder() = folder

        override suspend fun list() = files

        override suspend fun read(uri: String) = contents[uri]
    }

    private val names = listOf(
        "Kuzey Işığı.tr.srt",
        "Kuzey Işığı.en.srt",
        "Kuzey Işığı 2.tr.srt",
        "Kuzey.Isigi.2024.1080p.WEB-DL.tr.srt",
        "Dizi.S01E02.tr.srt",
        "Dizi 1x03.en.srt",
        "Baska Film.tr.srt",
        "notlar.txt",
        "Kuzey Işığı.idx",
    )

    private val files = Files(names = names)
    private val provider = LocalFolderProvider(files)

    private fun search(name: String?, languages: List<String> = emptyList(), season: Int? = null, episode: Int? = null) =
        runBlocking { provider.search(SubtitleQuery(name = name, languages = languages, season = season, episode = episode)) }

    @Test
    fun theFilmsNameFindsItsFilesWhateverTheAccents() {
        val found = search("Kuzey Isigi 2024 1080p WEB-DL")
        assertEquals(
            setOf("Kuzey Işığı.tr.srt", "Kuzey Işığı.en.srt", "Kuzey.Isigi.2024.1080p.WEB-DL.tr.srt", "Kuzey Işığı 2.tr.srt"),
            found.map { it.fileName }.toSet(),
        )
        assertTrue(found.all { it.provider == "local-folder" && it.providerName == "Local folder" })
    }

    @Test
    fun theLanguageIsReadFromTheNameAndFiltersWhenAsked() {
        val tr = search("Kuzey Isigi", languages = listOf("tr"))
        assertTrue(tr.all { it.language == "tr" })
        assertEquals(3, tr.size)
        assertEquals(listOf("en"), search("Kuzey Isigi", languages = listOf("en")).map { it.language })
        assertTrue(search("Kuzey Isigi", languages = listOf("de")).isEmpty())
    }

    @Test
    fun theClosestNameRanksFirst() {
        val ranked = SubtitleSearch.rank(search("Kuzey Isigi 2024 1080p WEB-DL", languages = listOf("tr")), listOf("tr"))
        // the release's own name shares the most with the question; the sequel ranks below the film
        assertEquals("Kuzey.Isigi.2024.1080p.WEB-DL.tr.srt", ranked.first().fileName)
        assertEquals("Kuzey Işığı 2.tr.srt", ranked.last().fileName)
    }

    @Test
    fun aSeriesIsFoundByItsSeasonAndEpisode() {
        assertEquals(listOf("Dizi.S01E02.tr.srt"), search("Dizi S01E02").map { it.fileName })
        assertEquals(listOf("Dizi 1x03.en.srt"), search("Dizi", season = 1, episode = 3).map { it.fileName })
        assertTrue(search("Dizi", season = 2, episode = 1).isEmpty())
    }

    @Test
    fun onlySubtitleFilesAreResultsAndNothingIsFoundWithoutAName() {
        assertTrue(search("notlar").isEmpty())
        assertTrue(search(null).isEmpty())
        assertTrue(search("   ").isEmpty())
        assertTrue(search("Hic Olmayan Film").isEmpty())
    }

    @Test
    fun withoutAFolderItCannotBeAskedAndSaysWhy() {
        files.folder = false
        assertEquals(OnlineProblem.NoFolder, provider.unavailable())
        val thrown = runCatching { search("Kuzey Isigi") }.exceptionOrNull()
        assertTrue(thrown is OnlineFailure.NoFolder)
        files.folder = true
        assertNull(provider.unavailable())
    }

    @Test
    fun aDownloadIsTheFilesBytes() {
        val bytes = "1\n00:00:01,000 --> 00:00:02,000\nmerhaba\n".toByteArray()
        files.contents["content://folder/Kuzey Işığı.tr.srt"] = bytes
        val found = search("Kuzey Isigi", languages = listOf("tr")).first { it.fileName == "Kuzey Işığı.tr.srt" }
        val file = runBlocking { provider.download(found) }
        assertArrayEquals(bytes, file.bytes)
        assertEquals("Kuzey Işığı.tr.srt", file.fileName)
        assertNull(file.quota)
        // a file that cannot be read
        files.contents.clear()
        assertTrue(runCatching { runBlocking { provider.download(found) } }.isFailure)
    }

    @Test
    fun whatItCanDoAndWhatItAsksFor() {
        val c = provider.capabilities
        assertTrue(c.searchByName && c.searchBySeasonEpisode && c.searchByLanguage && c.download)
        assertFalse(c.searchByHash || c.searchByImdb || c.needsKey || c.needsLogin)
        assertNull(c.rateLimit)
        assertEquals("local-folder", provider.id)
    }

    @Test
    fun wordsAreFoldedAndCutAtTheYear() {
        val words = LocalFolderProvider.Words.of("Kuzey Işığı (2024) 1080p S02E05")
        assertEquals(listOf("kuzey", "isigi"), words.title)
        assertEquals(2, words.season)
        assertEquals(5, words.episode)
        assertEquals("isigi", LocalFolderProvider.Words.fold("Işığı"))
    }

    // ---------------------------------------------------------------- the walk of a folder

    private fun entries(count: Int, delayMs: Long = 0, directory: (Int) -> Boolean = { false }) = object : Iterator<TreeEntry> {
        var next = 0
        override fun hasNext() = next < count
        override fun next(): TreeEntry {
            // a blocking read of a row, as a slow provider's cursor does
            if (delayMs > 0) Thread.sleep(delayMs)
            val n = next++
            return TreeEntry("id$n", "Dosya $n.srt", directory(n))
        }
    }

    @Test
    fun aWalkReadsTheFilesAndTheFoldersDownToItsDepth() {
        val found = ArrayList<LocalSubtitleFile>()
        runBlocking {
            walkTree(
                root = "root", maxDepth = 1, maxFiles = 100,
                children = { id -> if (id == "root") entries(3) { it == 1 } else if (id == "id1") entries(2) { it == 0 } else entries(5) },
                file = { LocalSubtitleFile(it.name, "content://f/${it.id}") },
                into = found,
            )
        }
        // two files at the top, one in the folder; the folder in the folder is deeper than allowed
        assertEquals(3, found.size)
    }

    @Test
    fun aWalkStopsAtItsFileLimit() {
        val found = ArrayList<LocalSubtitleFile>()
        runBlocking { walkTree("root", 2, 10, { entries(1000) }, { LocalSubtitleFile(it.name, it.id) }, found) }
        assertEquals(10, found.size)
    }

    @Test
    fun aWalkThatIsCancelledStopsAtOnceEvenWhileRowsBlock() {
        val found = ArrayList<LocalSubtitleFile>()
        val started = System.nanoTime()
        val thrown = runCatching {
            runBlocking {
                kotlinx.coroutines.withTimeout(300) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // 100 000 rows of 20 ms each: it would run for half an hour
                        walkTree("root", 2, 200_000, { entries(100_000, delayMs = 20) }, { LocalSubtitleFile(it.name, it.id) }, found)
                    }
                }
            }
        }.exceptionOrNull()
        assertTrue(thrown is kotlinx.coroutines.TimeoutCancellationException)
        assertTrue(System.nanoTime() - started < java.util.concurrent.TimeUnit.SECONDS.toNanos(5))
        assertTrue(found.size < 1000)
    }

    @Test
    fun aFolderThatCannotBeReadIsSkippedAndTheRestKept() {
        val found = ArrayList<LocalSubtitleFile>()
        runBlocking {
            walkTree(
                "root", 2, 100,
                { id -> if (id == "id0") error("no access") else entries(3) { it == 0 } },
                { LocalSubtitleFile(it.name, it.id) },
                found,
            )
        }
        assertEquals(2, found.size)
    }
}
