package app.nagmedar.ui.radio

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** S-1105: the search as the screen has it: what it asks, pages, failures, and only the latest answer counts. */
@RunWith(AndroidJUnit4::class)
class RadioSearchTest {

    private val directory = FakeDirectory()
    private val library = FakeLibrary()

    /** Runs what it is given at once, on the test's thread: no debounce to wait for. */
    private fun newSearch(debounceMs: Long = 0, scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())) =
        RadioSearch(directory, library, scope, debounceMs)

    private fun page(from: Int, count: Int) = (from until from + count).map {
        Station(uuid = "u$it", name = "Radyo $it", streamUrl = "https://yayin.example/$it")
    }

    @Test
    fun theFirstSearchIsTheBestStationsAndIsMadeOnce() {
        directory.onSearch = { RadioSamples.all }
        val search = newSearch()
        search.start()
        search.start()
        assertEquals(listOf(StationQuery()), directory.searches.toList())
        assertEquals(RadioSamples.all.map { it.uuid }, search.stations.map { it.uuid })
        assertTrue(search.searched)
        assertFalse(search.loading)
        assertFalse(search.more)
    }

    @Test
    fun whatIsTypedChosenAndOrderedIsWhatIsAsked() {
        val search = newSearch()
        search.type("gece caz")
        search.filter(StationFilters(countryCode = "TR", tag = "jazz"))
        search.filter(StationFilters(countryCode = "TR", tag = "jazz", codec = "MP3", minBitrate = 128))
        search.sort(StationOrder.Name)
        val last = directory.searches.last()
        assertEquals("gece caz", last.text)
        assertEquals(StationFilters("TR", null, "jazz", "MP3", 128), last.filters)
        assertEquals(StationOrder.Name, last.order)
        assertEquals(0, last.offset)
        assertEquals(StationQuery.PAGE, last.limit)
        assertEquals(4, directory.searches.size)
        // the same filters again are not another search
        search.filter(StationFilters("TR", null, "jazz", "MP3", 128))
        search.sort(StationOrder.Name)
        assertEquals(4, directory.searches.size)
        assertEquals(4, search.filters.count)
    }

    @Test
    fun typingSearchesWhenTheTypingPauses() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val search = newSearch(debounceMs = 80, scope = scope)
        search.type("g")
        search.type("ge")
        search.type("gece")
        withTimeout(5_000) { while (directory.searches.isEmpty()) delay(10) }
        delay(300)
        assertEquals(listOf("gece"), directory.searches.map { it.text })
        scope.cancel()
    }

    @Test
    fun anotherPageFollowsTheSameSearchWithoutRepeats() {
        directory.onSearch = { query -> if (query.offset == 0) page(0, 50) else page(45, 10) }
        val search = newSearch()
        search.type("radyo")
        assertTrue(search.more)
        search.loadMore()
        assertEquals(listOf(0, 50), directory.searches.map { it.offset })
        assertEquals("radyo", directory.searches.last().text)
        // five of the ten were listed already
        assertEquals(55, search.stations.size)
        assertEquals(55, search.stations.map { it.key }.toSet().size)
        assertFalse(search.more)
        search.loadMore()
        assertEquals(2, directory.searches.size)
    }

    @Test
    fun noStationsIsNotAFailure() {
        val search = newSearch()
        search.start()
        assertTrue(search.searched)
        assertNull(search.failure)
        assertTrue(search.stations.isEmpty())
    }

    @Test
    fun aFailureIsKeptUntilTryingAgainWorks() {
        directory.onSearch = { throw RadioFailure.Offline() }
        val search = newSearch()
        search.start()
        assertTrue(search.failure is RadioFailure.Offline)
        assertTrue(search.searched)
        assertFalse(search.loading)
        directory.onSearch = { RadioSamples.all }
        search.retry()
        assertNull(search.failure)
        assertEquals(4, search.stations.size)
    }

    @Test
    fun aPageThatFailsKeepsTheStationsListed() {
        directory.onSearch = { query -> if (query.offset == 0) page(0, 50) else throw RadioFailure.Server(503) }
        val search = newSearch()
        search.start()
        search.loadMore()
        assertEquals(50, search.stations.size)
        assertEquals(503, (search.failure as RadioFailure.Server).status)
        directory.onSearch = { page(50, 5) }
        search.retry()
        assertNull(search.failure)
        assertEquals(55, search.stations.size)
    }

    @Test
    fun onlyTheLatestSearchCounts() = runBlocking {
        val slow = CompletableDeferred<Unit>()
        directory.onSearch = { query ->
            if (query.text == "eski") {
                // an answer that arrives late, whatever happened to the question
                withContext(NonCancellable) { slow.await() }
                listOf(RadioSamples.news)
            } else {
                listOf(RadioSamples.jazz)
            }
        }
        val search = newSearch()
        search.type("eski")
        assertTrue(search.loading)
        search.type("yeni")
        assertEquals(listOf("a1-caz"), search.stations.map { it.uuid })
        slow.complete(Unit)
        assertEquals(listOf("a1-caz"), search.stations.map { it.uuid })
        assertFalse(search.loading)
    }

    @Test
    fun whatTheUserDidWithAStationShowsInTheList() {
        runBlocking { library.played(RadioSamples.jazz.copy(favourite = true), 1234L) }
        directory.onSearch = { listOf(RadioSamples.jazz, RadioSamples.news) }
        val search = newSearch()
        search.start()
        val jazz = search.stations[0]
        assertTrue(jazz.favourite)
        assertEquals(1234L, jazz.lastPlayedAt)
        assertTrue(jazz.id > 0)
        // a station never kept is as the directory has it
        assertFalse(search.stations[1].favourite)
        assertEquals(0L, search.stations[1].id)
    }

    @Test
    fun replacingAStationChangesOnlyItsRow() {
        directory.onSearch = { RadioSamples.all }
        val search = newSearch()
        search.start()
        val before = search.stations
        search.replace(RadioSamples.news.copy(votes = 2311, lastVotedAt = 99))
        assertEquals(2311, search.stations[1].votes)
        assertSame(before[0], search.stations[0])
        assertSame(before[2], search.stations[2])
    }

    @Test
    fun theListsForTheFiltersAreFetchedOnce() {
        directory.countriesList = listOf(DirectoryCountry("Turkey", "TR", 800))
        directory.languagesList = listOf(DirectoryLanguage("turkish", "tr", 700))
        directory.tagsList = listOf(DirectoryTag("jazz", 400))
        val search = newSearch()
        assertEquals(OptionsPhase.Idle, search.options.phase)
        search.loadOptions()
        assertEquals(OptionsPhase.Ready, search.options.phase)
        assertEquals("TR", search.options.countries.single().code)
        assertEquals("jazz", search.options.tags.single().name)
        directory.countriesList = emptyList()
        search.loadOptions()
        assertEquals(1, search.options.countries.size)
    }

    @Test
    fun listsThatFailCanBeAskedForAgain() {
        directory.optionsFailure = RadioFailure.Offline()
        val search = newSearch()
        search.loadOptions()
        assertEquals(OptionsPhase.Failed, search.options.phase)
        directory.optionsFailure = null
        directory.tagsList = listOf(DirectoryTag("haber", 20))
        search.loadOptions()
        assertEquals(OptionsPhase.Ready, search.options.phase)
    }
}
