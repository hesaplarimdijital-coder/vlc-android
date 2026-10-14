package app.nagmedar.ui.radio

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** S-1105: a station of the directory is reported as a click every time it starts, and plays from the address the directory gives. */
@RunWith(AndroidJUnit4::class)
class RadioPlayerTest {

    private val directory = FakeDirectory()
    private val library = FakeLibrary()
    private val sink = RecordingSink()
    private var now = 10_000L

    private fun player(clickWaitMs: Long = 3_000) = RadioPlayer(directory, library, sink, { now }, clickWaitMs, inlineClicks())

    private val jazz = RadioSamples.jazz

    @Test
    fun everyStartIsAClick() = runBlocking {
        directory.onClick = { StationClick("https://cdn.example/live/$it", null) }
        val player = player()
        player.start(jazz)
        player.start(jazz)
        player.start(RadioSamples.news)
        assertEquals(listOf("a1-caz", "a1-caz", "b2-haber"), directory.clicks.toList())
        assertEquals(3, sink.played.size)
    }

    @Test
    fun theAddressTheClickGivesIsTheOneThatPlays() = runBlocking {
        directory.onClick = { StationClick("https://cdn.example/live/$it", "Gece Cazı") }
        player().start(jazz)
        assertEquals("https://cdn.example/live/a1-caz", sink.played.single().url)
        // and it is the one kept
        assertEquals("https://cdn.example/live/a1-caz", library.kept.single().streamUrl)
    }

    @Test
    fun theKeptAddressPlaysWhenTheDirectoryFails() = runBlocking {
        directory.onClick = { throw RadioFailure.Offline() }
        player().start(jazz)
        assertEquals(jazz.streamUrl, sink.played.single().url)
    }

    @Test
    fun theKeptAddressPlaysWhenTheClickIsSlow() = runBlocking {
        directory.onClick = {
            delay(5_000)
            StationClick("https://cdn.example/late", null)
        }
        player(clickWaitMs = 40).start(jazz)
        assertEquals(jazz.streamUrl, sink.played.single().url)
        // the click was still made
        assertEquals(listOf("a1-caz"), directory.clicks.toList())
    }

    @Test
    fun aSlowClickIsNotCancelledWhenThePlayerStopsWaiting() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Boolean>()
        directory.onClick = {
            release.await()
            finished.complete(true)
            StationClick("https://cdn.example/late", null)
        }
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        RadioPlayer(directory, library, sink, { now }, 30, scope).start(jazz)
        // the wait is over and the kept address plays, but the click is still in the air
        assertEquals(jazz.streamUrl, sink.played.single().url)
        assertFalse(finished.isCompleted)
        release.complete(Unit)
        assertTrue(finished.await())
    }

    @Test
    fun aClickThatThrowsAnythingStillLetsTheStationPlay() = runBlocking {
        directory.onClick = { throw IllegalStateException("garbage") }
        player().start(jazz)
        assertEquals(jazz.streamUrl, sink.played.single().url)
    }

    @Test
    fun theStationPlaysEvenWhenTheDatabaseFails() = runBlocking {
        val broken = object : RadioLibrary by library {
            override suspend fun played(station: Station, at: Long): Station = throw IllegalStateException("disk I/O error")
        }
        try {
            RadioPlayer(directory, broken, sink, { now }, clickScope = inlineClicks()).start(jazz)
            fail("the failure was swallowed")
        } catch (e: IllegalStateException) {
            // said to the user, after the station started
        }
        assertEquals(jazz.streamUrl, sink.played.single().url)
        assertEquals("a1-caz", RadioSession.playing.value?.station?.uuid)
    }

    @Test
    fun aClickWithoutAnAddressLeavesTheKeptOne() = runBlocking {
        directory.onClick = { StationClick("  ", null) }
        player().start(jazz)
        assertEquals(jazz.streamUrl, sink.played.single().url)
        directory.onClick = { StationClick(null, null) }
        player().start(jazz)
        assertEquals(jazz.streamUrl, sink.played.last().url)
    }

    @Test
    fun aStationOfTheUsersIsNotReported() = runBlocking {
        val own = Station(uuid = null, name = "Benim Radyom", streamUrl = "http://ev.example:8000/radyo")
        player().start(own)
        assertEquals(emptyList<String>(), directory.clicks.toList())
        assertEquals("http://ev.example:8000/radyo", sink.played.single().url)
        assertNull(library.kept.single().uuid)
    }

    @Test
    fun theStationIsKeptAndNotedAsPlayed() = runBlocking {
        val player = player()
        val first = player.start(jazz)
        assertEquals(10_000L, first.lastPlayedAt)
        assertEquals(1, library.kept.size)
        now += 60_000
        player.start(jazz)
        assertEquals(1, library.kept.size)
        assertEquals(70_000L, library.kept.single().lastPlayedAt)
    }

    @Test
    fun theSessionKnowsWhatPlays() = runBlocking {
        directory.onClick = { StationClick("https://cdn.example/live", null) }
        player().start(RadioSamples.classical)
        val playing = RadioSession.playing.value!!
        assertEquals("c3-klasik", playing.station.uuid)
        assertEquals("https://cdn.example/live", playing.url)
    }
}
