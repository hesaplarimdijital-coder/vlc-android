package app.nagmedar.ui.player.subtitles.providers

import app.nagmedar.ui.player.subtitles.OnlineFailure
import app.nagmedar.ui.player.subtitles.OnlineProblem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * S-707: the providers asked at once, each within its time; their lists merged without doubles
 * and ranked; a provider that fails reported by name while the others' results stay.
 */
class ProviderSearchTest {

    private val query = SubtitleQuery(hash = "8e245d9679d31e12", name = "Northern Light 2024", languages = listOf("tr", "en"))

    private fun search(vararg providers: SubtitleProvider, timeoutMs: Long = 2_000) = runBlocking { SubtitleSearch(timeoutMs).search(providers.toList(), query) }

    @Test
    fun theProvidersAreAskedAtTheSameTime() {
        // each waits until the other has started: asked one after the other they would wait for ever
        val started = AtomicInteger()
        val rendezvous: suspend () -> Unit = {
            started.incrementAndGet()
            while (started.get() < 2) kotlinx.coroutines.delay(5)
        }
        val a = FakeProvider("a", results = listOf(result("a", "1")), onSearch = rendezvous)
        val b = FakeProvider("b", results = listOf(result("b", "1", release = "Other.Release")), onSearch = rendezvous)
        val outcome = search(a, b, timeoutMs = 1_500)
        assertEquals(2, outcome.results.size)
        assertEquals(listOf(null, null), outcome.reports.map { it.problem })
        assertEquals(query, a.queries.single())
        assertEquals(query, b.queries.single())
    }

    @Test
    fun aSlowProviderTimesOutAndTheOthersStay() {
        val slow = FakeProvider("slow", "Slow One", results = listOf(result("slow", "1")), delayMs = 5_000)
        val quick = FakeProvider("quick", "Quick One", results = listOf(result("quick", "7")))
        val outcome = search(slow, quick, timeoutMs = 150)
        assertEquals(listOf("quick/7"), outcome.results.map { it.key })
        assertEquals(listOf(ProviderReport("slow", "Slow One", 0, OnlineProblem.Timeout), ProviderReport("quick", "Quick One", 1, null)), outcome.reports)
        // someone answered: nothing is wrong with the search as a whole
        assertNull(outcome.failure)
    }

    @Test
    fun aFailedProviderIsReportedAndTheOthersStay() {
        val down = FakeProvider("down", failure = OnlineFailure.Offline())
        val over = FakeProvider("over", failure = OnlineFailure.QuotaOver(null))
        val broken = FakeProvider("broken", failure = IllegalStateException("bad json"))
        val fine = FakeProvider("fine", results = listOf(result("fine", "1"), result("fine", "2", release = "Second")))
        val outcome = search(down, over, broken, fine)
        assertEquals(2, outcome.results.size)
        assertEquals(listOf(OnlineProblem.Offline, OnlineProblem.QuotaOver, OnlineProblem.Server, null), outcome.reports.map { it.problem })
        assertEquals(listOf(0, 0, 0, 2), outcome.reports.map { it.found })
        assertNull(outcome.failure)
    }

    @Test
    fun whenAllFailTheFirstProblemIsTheSearchs() {
        val outcome = search(FakeProvider("a", failure = OnlineFailure.Offline()), FakeProvider("b", problem = OnlineProblem.NoKey))
        assertTrue(outcome.results.isEmpty())
        assertEquals(OnlineProblem.Offline, outcome.failure)
        // none found, none failed: an empty list, not an error
        assertNull(search(FakeProvider("c")).failure)
    }

    @Test
    fun aProviderThatCannotBeAskedIsNotAskedButIsReported() {
        val noKey = FakeProvider("key", problem = OnlineProblem.NoKey)
        val noFolder = FakeProvider("folder", problem = OnlineProblem.NoFolder)
        val fine = FakeProvider("fine", results = listOf(result("fine", "1")))
        val outcome = search(noKey, noFolder, fine)
        assertTrue(noKey.queries.isEmpty() && noFolder.queries.isEmpty())
        assertEquals(listOf(OnlineProblem.NoKey, OnlineProblem.NoFolder, null), outcome.reports.map { it.problem })
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun theSameFileFromTwoProvidersIsOneResult() {
        val os = result("os", "11", language = "tr", downloads = 500, providerName = "OpenSubtitles")
        val local = result("local", "content://x/1", language = "tr", providerName = "Local folder")
        val merged = SubtitleSearch.merge(listOf(listOf(os), listOf(local)))
        assertEquals(1, merged.size)
        // the one with more downloads, who else has it
        assertEquals("os/11", merged.single().key)
        assertEquals(listOf("Local folder"), merged.single().alsoFrom)
        assertEquals("OpenSubtitles + Local folder", merged.single().sources)
        // the release is written differently, the file is the same
        val again = SubtitleSearch.merge(listOf(listOf(os), listOf(local.copy(release = "northern light 2024 1080p web-dl"))))
        assertEquals(1, again.size)
    }

    @Test
    fun theFilesOwnMatchWinsTheMergeAndKeepsItsMark() {
        val plain = result("a", "1", language = "tr", downloads = 9_000, providerName = "A")
        val timed = result("b", "2", language = "tr", downloads = 3, hashMatch = true, providerName = "B")
        val merged = SubtitleSearch.merge(listOf(listOf(plain), listOf(timed))).single()
        assertEquals("b/2", merged.key)
        assertTrue(merged.hashMatch)
        assertEquals(listOf("A"), merged.alsoFrom)
        // the other way round: the same
        assertEquals("b/2", SubtitleSearch.merge(listOf(listOf(timed), listOf(plain))).single().key)
    }

    @Test
    fun differentLanguagesOrReleasesAreNotDoubles() {
        val tr = result("a", "1", language = "tr")
        val en = result("b", "2", language = "en")
        val other = result("b", "3", language = "tr", release = "Northern.Light.2024.720p.BluRay")
        assertEquals(3, SubtitleSearch.merge(listOf(listOf(tr), listOf(en, other))).size)
    }

    @Test
    fun oneProvidersOwnResultsAreNeverMerged() {
        val first = result("a", "1", language = "tr")
        val second = result("a", "2", language = "tr")
        assertEquals(2, SubtitleSearch.merge(listOf(listOf(first, second))).size)
        // the same result listed twice is one
        assertEquals(1, SubtitleSearch.merge(listOf(listOf(first, first))).size)
    }

    @Test
    fun theFilesMatchesFirstThenLanguagesThenDownloadsThenRating() {
        val found = listOf(
            result("a", "en-popular", language = "en", downloads = 90_000, release = "R1"),
            result("a", "tr-few", language = "tr", downloads = 10, release = "R2"),
            result("b", "tr-many", language = "tr", downloads = 5_000, release = "R3"),
            result("b", "en-timed", language = "en", downloads = 1, hashMatch = true, release = "R4"),
            result("b", "tr-rated", language = "tr", downloads = 10, rating = 0.9, release = "R5"),
            result("b", "de", language = "de", downloads = 99_999, release = "R6"),
        )
        val ranked = SubtitleSearch.rank(found, listOf("tr", "en")).map { it.id }
        assertEquals(listOf("en-timed", "tr-many", "tr-rated", "tr-few", "en-popular", "de"), ranked)
    }

    @Test
    fun theOrderOfTheProvidersDecidesTheRest() {
        val first = result("a", "1", release = "R1", language = "tr", downloads = 5)
        val second = result("b", "2", release = "R2", language = "tr", downloads = 5)
        val outcome = search(FakeProvider("a", results = listOf(first)), FakeProvider("b", results = listOf(second)))
        assertEquals(listOf("a/1", "b/2"), outcome.results.map { it.key })
        val swapped = search(FakeProvider("b", results = listOf(second)), FakeProvider("a", results = listOf(first)))
        assertEquals(listOf("b/2", "a/1"), swapped.results.map { it.key })
    }

    @Test
    fun failuresAreToldByKind() {
        assertEquals(OnlineProblem.NoKey, SubtitleSearch.problemOf(OnlineFailure.NoKey()))
        assertEquals(OnlineProblem.NoFolder, SubtitleSearch.problemOf(OnlineFailure.NoFolder()))
        assertEquals(OnlineProblem.Server, SubtitleSearch.problemOf(OnlineFailure.Server(500)))
        assertEquals(OnlineProblem.Server, SubtitleSearch.problemOf(RuntimeException()))
    }
}
