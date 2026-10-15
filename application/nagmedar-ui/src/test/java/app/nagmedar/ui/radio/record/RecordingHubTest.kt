package app.nagmedar.ui.radio.record

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** S-1107: recordings side by side; one station is never recorded twice; the fourth is refused. */
class RecordingHubTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val storage = MemoryStorage()
    private val library = MemoryLibrary()
    private val clock = ManualClock()
    private val sources = HashMap<String, LiveSource>()

    private val opener = StreamOpener { url ->
        val source = LiveSource()
        sources[url] = source
        OpenedStream("audio/mpeg", 0, source)
    }

    private val hub = RecordingHub(
        create = { request ->
            RecordingSession(request, opener, storage, library, { RecordingSettings(minSeconds = 1) }, clock::invoke, ZoneId.of("Europe/Istanbul"), Dispatchers.IO, { delay(2) })
        },
        scope = scope,
    )

    @After
    fun stop() {
        scope.cancel()
    }

    private fun request(name: String, end: Long? = null) =
        RecordingRequest("session-$name", RecordingStation(name, "https://yayin.example/$name", uuid = name), end)

    private suspend fun waitFor(count: Int) = withTimeout(5_000) { while (hub.live.value.size != count) delay(5) }

    @Test
    fun stationsAreRecordedSideBySide() = runBlocking {
        assertEquals(StartResult.Started, hub.start(request("a")))
        assertEquals(StartResult.Started, hub.start(request("b")))
        waitFor(2)
        assertEquals(listOf("session-a", "session-b"), hub.live.value.map { it.sessionId })
        assertTrue(hub.recording("a"))
        hub.stopAll()
        waitFor(0)
    }

    @Test
    fun theSameStationAskedAgainIsOneRecordingWithTheLaterEnd() = runBlocking {
        hub.start(request("a", end = clock.now + 60_000))
        val again = hub.start(request("a", end = clock.now + 600_000))
        assertEquals(StartResult.Merged("session-a"), again)
        assertEquals(1, hub.live.value.size)
        assertEquals(clock.now + 600_000, hub.live.value.single().state.endAt)
        // an earlier end does not shorten it
        hub.start(request("a", end = clock.now + 1_000))
        assertEquals(clock.now + 600_000, hub.live.value.single().state.endAt)
        // and none at all means until stopped
        hub.start(request("a", end = null))
        assertEquals(null, hub.live.value.single().state.endAt)
        hub.stopAll()
        waitFor(0)
    }

    @Test
    fun theFourthStationIsRefused() = runBlocking {
        listOf("a", "b", "c").forEach { assertEquals(StartResult.Started, hub.start(request(it))) }
        assertEquals(StartResult.Full, hub.start(request("d")))
        waitFor(3)
        // one ending makes room
        hub.stop("session-b")
        waitFor(2)
        assertEquals(StartResult.Started, hub.start(request("d")))
        hub.stopAll()
        waitFor(0)
    }

    @Test
    fun anEndedRecordingSaysWhy() = runBlocking {
        hub.start(request("a"))
        waitFor(1)
        val first = async(start = CoroutineStart.UNDISPATCHED) { hub.ends.first() }
        hub.stop("session-a")
        val ended = withTimeout(5_000) { first.await() }
        assertEquals("session-a", ended.request.sessionId)
        assertEquals(SessionEnd.UserStopped, ended.end)
        waitFor(0)
        assertTrue(!hub.recording("a"))
    }
}
