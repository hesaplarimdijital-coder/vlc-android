package app.nagmedar.ui.radio

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1106: the lists the user keeps (favourites, recent, imported): how a station gets in and out of them, and what is said. */
@RunWith(AndroidJUnit4::class)
class RadioListsTest {

    private val directory = FakeDirectory()
    private val library = FakeLibrary()
    private val sink = RecordingSink()
    private val onAir = MutableStateFlow<RadioNow?>(null)
    private var now = 1_760_000_000_000L

    private fun controller(): RadioController {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val controller = RadioController(directory, library, RadioPlayer(directory, library, sink, { now }, clickScope = inlineClicks()), scope, { now }, debounceMs = 0, playingNow = onAir, work = Dispatchers.Unconfined)
        directory.onSearch = { listOf(RadioSamples.jazz, RadioSamples.news) }
        controller.search.start()
        return controller
    }

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/radio/$name")) { "no test resource $name" }.use { it.readBytes() }

    @Test
    fun theListsStartUnknownThenEmptyAndTheSearchIsShown() {
        val controller = controller()
        assertEquals(RadioTab.Search, controller.tab)
        assertEquals(emptyList<Station>(), controller.favourites)
        assertEquals(emptyList<Station>(), controller.recent)
        assertEquals(emptyList<Station>(), controller.imported)
        controller.select(RadioTab.Recent)
        assertEquals(RadioTab.Recent, controller.tab)
    }

    @Test
    fun aFavouriteIsKeptAndTheStarGoesBothWays() {
        val controller = controller()
        controller.toggleFavourite(controller.search.stations.first())
        assertEquals(listOf("Gece Cazı"), controller.favourites?.map { it.name })
        assertTrue(controller.search.stations.first().favourite)

        controller.toggleFavourite(controller.search.stations.first())
        assertEquals(emptyList<Station>(), controller.favourites)
        assertFalse(controller.search.stations.first().favourite)
        // a station nobody keeps is not kept
        assertEquals(emptyList<Station>(), library.kept)
    }

    @Test
    fun aStationPlayedIsRecentWithTheLatestFirst() {
        val controller = controller()
        controller.play(controller.search.stations[0])
        now += 1_000
        controller.play(controller.search.stations[1])
        assertEquals(listOf("Haber Hattı", "Gece Cazı"), controller.recent?.map { it.name })
    }

    @Test
    fun takingAStationOutOfRecentForgetsItAndUndoBringsItBack() {
        val controller = controller()
        controller.play(controller.search.stations.first())
        controller.select(RadioTab.Recent)
        val station = controller.recent!!.single()

        controller.remove(station)
        assertEquals(emptyList<Station>(), controller.recent)
        assertEquals(RadioNotice.Removed(station, RadioTab.Recent), controller.notice)

        controller.undo(controller.notice as RadioNotice.Removed)
        assertEquals(listOf("Gece Cazı"), controller.recent?.map { it.name })
    }

    @Test
    fun forgettingARecentStationKeepsItsStar() {
        val controller = controller()
        controller.toggleFavourite(controller.search.stations.first())
        controller.play(controller.search.stations.first())
        controller.select(RadioTab.Recent)
        controller.remove(controller.recent!!.single())
        assertEquals(emptyList<Station>(), controller.recent)
        assertEquals(listOf("Gece Cazı"), controller.favourites?.map { it.name })
    }

    @Test
    fun takingAStationOutOfFavouritesIsUnstarringIt() {
        val controller = controller()
        controller.toggleFavourite(controller.search.stations.first())
        controller.select(RadioTab.Favourites)
        controller.remove(controller.favourites!!.single())
        assertEquals(emptyList<Station>(), controller.favourites)
        assertEquals(RadioTab.Favourites, (controller.notice as RadioNotice.Removed).from)
        controller.undo(controller.notice as RadioNotice.Removed)
        assertEquals(listOf("Gece Cazı"), controller.favourites?.map { it.name })
    }

    @Test
    fun aFileOfStationsIsImportedAndShown() {
        val controller = controller()
        controller.import(resource("stations.m3u8"), "stations.m3u8")
        assertEquals(RadioTab.Imported, controller.tab)
        assertEquals(listOf("Nağmedar Test FM (AAC)", "Nağmedar Test FM (MP3)"), controller.imported?.map { it.name }?.sorted())
        assertEquals(RadioNotice.Imported(2, 0), controller.notice)
        // imported stations have no directory id
        assertTrue(controller.imported!!.all { it.uuid == null })
        assertEquals("https://example.org/logo.png", controller.imported!!.first { it.name.endsWith("(AAC)") }.logoUrl)
    }

    @Test
    fun theSameFileAgainAddsNothing() {
        val controller = controller()
        controller.import(resource("stations.m3u8"), "stations.m3u8")
        controller.import(resource("stations.m3u8"), "stations.m3u8")
        assertEquals(2, controller.imported?.size)
        // nothing new was added, and it is said so
        assertEquals(RadioNotice.Imported(0, 0, 2), controller.notice)
    }

    @Test
    fun aFileWithSomeStationsKnownCountsTheNewAndThePresent() {
        val controller = controller()
        controller.import(resource("station.pls"), "station.pls")
        controller.import(resource("stations.m3u8"), "stations.m3u8")
        // the playlist's AAC entry is the station of the PLS: one new, one present
        assertEquals(2, controller.imported?.size)
        assertEquals(RadioNotice.Imported(1, 0, 1), controller.notice)
        controller.import(resource("stations.m3u8"), "stations.m3u8")
        assertEquals(RadioNotice.Imported(0, 0, 2), controller.notice)
    }

    @Test
    fun aDatabaseThatFailsIsSaidAndDoesNotCrash() {
        val controller = controller()
        val station = controller.search.stations.first()
        library.failWith = IllegalStateException("disk I/O error")
        controller.toggleFavourite(station)
        assertEquals(RadioNotice.Failed, controller.notice)
        controller.noticed()
        controller.play(station)
        // the station started all the same
        assertEquals(1, sink.played.size)
        assertEquals(RadioNotice.Failed, controller.notice)
        controller.noticed()
        controller.import(resource("stations.m3u8"), "stations.m3u8")
        assertEquals(RadioNotice.Failed, controller.notice)
        controller.noticed()
        // and it works again when the database does
        library.failWith = null
        controller.toggleFavourite(station)
        assertNull(controller.notice)
        assertEquals(listOf("Gece Cazı"), controller.favourites?.map { it.name })
    }

    @Test
    fun theListStaysWhenTheMarksCannotBeRead() {
        library.failWith = IllegalStateException("disk I/O error")
        val controller = controller()
        assertEquals(2, controller.search.stations.size)
        assertNull(controller.search.failure)
    }

    @Test
    fun anImportedStationIsRemovedAndPutBack() {
        val controller = controller()
        controller.import(resource("station.pls"), "station.pls")
        val station = controller.imported!!.single()
        controller.remove(station)
        assertEquals(emptyList<Station>(), controller.imported)
        assertEquals(RadioNotice.Removed(station, RadioTab.Imported), controller.notice)
        controller.undo(controller.notice as RadioNotice.Removed)
        assertEquals(listOf("Nağmedar Test FM"), controller.imported?.map { it.name })
    }

    @Test
    fun aFileThatIsNoGoodSaysWhyAndKeepsTheTab() {
        val controller = controller()
        controller.import(resource("not-a-playlist.txt"), "not-a-playlist.txt")
        assertEquals(RadioNotice.ImportFailed(ImportProblem.NotAPlaylist), controller.notice)
        controller.import(resource("empty.m3u8"), "empty.m3u8")
        assertEquals(RadioNotice.ImportFailed(ImportProblem.Empty), controller.notice)
        controller.import(ByteArray(StationPlaylists.MAX_BYTES + 1) { '#'.code.toByte() }, "dev.m3u8")
        assertEquals(RadioNotice.ImportFailed(ImportProblem.TooLarge), controller.notice)
        controller.importUnreadable()
        assertEquals(RadioNotice.ImportFailed(ImportProblem.Unreadable), controller.notice)
        assertEquals(RadioTab.Search, controller.tab)
        assertEquals(emptyList<Station>(), controller.imported)
        controller.noticed()
        assertNull(controller.notice)
    }

    @Test
    fun skippedEntriesAreCounted() {
        val controller = controller()
        controller.import(resource("mixed.m3u8"), "mixed.m3u8")
        assertEquals(RadioNotice.Imported(2, 6), controller.notice)
    }

    @Test
    fun whatPlaysIsShownInTheController() {
        val controller = controller()
        assertNull(controller.now)
        val display = app.nagmedar.playback.RadioMetadata.display("Gece Cazı", "Miles Davis - So What")
        onAir.value = RadioNow(RadioSamples.jazz, display, playing = true)
        assertEquals("So What", controller.now?.display?.title)
        onAir.value = null
        assertNull(controller.now)
    }
}
