package app.nagmedar.ui.radio.record

import app.nagmedar.ui.radio.record.StorageLimits.Companion.GB
import app.nagmedar.ui.radio.record.StorageLimits.Companion.MB
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** S-1107: a recording session over fakes: the stream, the storage and the library are the test's; the state machine is the real one. */
class RecordingSessionTest {

    private val station = RecordingStation("Radyo", "https://yayin.example/radyo", uuid = "r1")
    private val frame = 417
    private val metaInt = frame * 50
    private val audio = Streams.mp3(0, 300)
    private val titles = mapOf(0 to "Artist A - Song A", 2 to "Artist B - Song B", 4 to "Artist C - Song C")
    private val stream = Streams.icy(audio, metaInt) { titles[it] }

    private val storage = MemoryStorage()
    private val library = MemoryLibrary()
    private val clock = ManualClock()
    private val opener = FakeOpener()
    private var settings = RecordingSettings(minSeconds = 1)

    private fun session(endAt: Long? = null, id: String = "s1") = RecordingSession(
        request = RecordingRequest(id, station, endAt),
        opener = opener,
        storage = storage,
        library = library,
        settings = { settings },
        clock = clock::invoke,
        zone = ZoneId.of("Europe/Istanbul"),
        io = Dispatchers.IO,
        pause = { delay(2) },
    )

    private fun mp3(source: StreamSource, metaInt: Int = this.metaInt, type: String = "audio/mpeg") = OpenedStream(type, metaInt, source)

    private fun chunks(bytes: ByteArray, size: Int = 3_000) = bytes.toList().chunked(size).map { it.toByteArray() }

    @Test
    fun theTitlesBecomeTracksInTheLibrary() = runBlocking {
        lateinit var session: RecordingSession
        opener.then { mp3(ScriptedSource(chunks(stream), onEnd = { session.stop() })) }
        session = session()
        val end = session.run()
        assertEquals(SessionEnd.UserStopped, end)
        assertEquals(listOf("Artist A - Song A.mp3", "Artist B - Song B.mp3", "Artist C - Song C.mp3"), library.tracks.map { it.fileName })
        assertEquals(listOf(false, true, false), library.tracks.map { it.complete })
        val b = library.tracks[1]
        assertEquals("Artist B", b.artist)
        assertEquals("Song B", b.title)
        assertEquals("r1", b.station.uuid)
        assertEquals("s1", b.sessionId)
        assertEquals("mp3", b.format)
        val state = session.state.value
        assertEquals(Phase.Done, state.phase)
        assertEquals(3, state.tracksSaved)
        assertEquals(AudioFormat.Mp3, state.format)
        assertEquals(true, state.splits)
        assertEquals(300L * frame, state.bytes)
        // the request carried what the stream sends: the player's connection is not this one, and the metadata was asked for by the opener
        assertEquals(listOf("https://yayin.example/radyo"), opener.asked.toList())
    }

    @Test
    fun aStreamWithoutTitlesIsRecordedWholeAndSaysItCannotSplit() = runBlocking {
        lateinit var session: RecordingSession
        opener.then { mp3(ScriptedSource(chunks(audio), onEnd = { session.stop() }), metaInt = 0) }
        session = session()
        session.run()
        assertEquals(1, library.tracks.size)
        assertEquals(false, session.state.value.splits)
    }

    @Test
    fun theCurrentTrackIsInTheStateWhileItIsRecorded() = runBlocking {
        val source = LiveSource()
        opener.then { mp3(source) }
        val session = session()
        val job = async { session.run() }
        // Song A's title is heard after the first 50 frames
        source.push(stream.copyOfRange(0, (metaInt + 1 + 16 * 3) + frame * 3))
        withTimeout(5_000) { while (session.state.value.track?.title != "Song A") delay(5) }
        assertEquals(Phase.Recording, session.state.value.phase)
        assertEquals(TrackTag("Artist A", "Song A"), session.state.value.track)
        session.stop()
        assertEquals(SessionEnd.UserStopped, job.await())
        assertTrue(source.closed)
    }

    @Test
    fun theRecordingStopsWhenItsTimeIsUp() = runBlocking {
        settings = RecordingSettings(minSeconds = 0)
        val endAt = clock.now + 5_000
        var reads = 0
        opener.then {
            mp3(object : StreamSource {
                override fun read(buffer: ByteArray): Int {
                    if (++reads > 100_000) return -1
                    // each read takes a second on the clock
                    clock.now += 1_000
                    Thread.sleep(1)
                    val frames = Streams.mp3(reads % 200, 3)
                    System.arraycopy(frames, 0, buffer, 0, frames.size)
                    return frames.size
                }

                override fun close() = Unit
            }, metaInt = 0)
        }
        val end = withTimeout(10_000) { session(endAt).run() }
        assertEquals(SessionEnd.TimeUp, end)
        assertEquals(1, library.tracks.size)
    }

    @Test
    fun theEndCanBeMovedLater() = runBlocking {
        val source = LiveSource()
        opener.then { mp3(source, metaInt = 0) }
        val session = session(endAt = clock.now + 5_000)
        val job = async { session.run() }
        session.extend(clock.now + 60_000)
        clock.now += 10_000
        delay(100)
        assertTrue(job.isActive)
        assertEquals(clock.now - 10_000 + 60_000, session.state.value.endAt)
        clock.now += 60_000
        assertEquals(SessionEnd.TimeUp, withTimeout(10_000) { job.await() })
    }

    @Test
    fun aStreamThatDropsIsOpenedAgainAndTheTracksGoOnInNewFiles() = runBlocking {
        lateinit var session: RecordingSession
        // two intervals with their blocks, and five frames of the third
        val first = stream.copyOf(2 * metaInt + 33 + 1 + frame * 5)
        opener.then { mp3(ScriptedSource(chunks(first), failAfter = true)) }
        opener.then { mp3(ScriptedSource(chunks(stream), onEnd = { session.stop() })) }
        session = session()
        val end = session.run()
        assertEquals(SessionEnd.UserStopped, end)
        assertEquals(2, opener.asked.size)
        // the first connection's track was cut short; the second opened its own files
        assertEquals("Artist A - Song A.mp3", library.tracks[0].fileName)
        assertFalse(library.tracks[0].complete)
        assertEquals("Artist A - Song A (2).mp3", library.tracks[1].fileName)
        assertEquals(1, session.state.value.reconnects)
    }

    @Test
    fun aStreamThatNeverAnswersEndsTheRecordingAfterAFewTries() = runBlocking {
        val session = session()
        val end = withTimeout(20_000) { session.run() }
        assertEquals(SessionEnd.Failed(null), end)
        assertEquals(RecordingSession.MAX_FAILURES, opener.asked.size)
        assertTrue(library.tracks.isEmpty())
    }

    @Test
    fun aRefusalSaysWhy() = runBlocking {
        repeat(RecordingSession.MAX_FAILURES) { opener.then { throw StreamRefused(503) } }
        val end = withTimeout(20_000) { session().run() }
        assertEquals(SessionEnd.Failed(503), end)
    }

    @Test
    fun aPlaylistOfSegmentsIsNotRecorded() = runBlocking {
        opener.then { OpenedStream("application/vnd.apple.mpegurl", 0, ScriptedSource(listOf("#EXTM3U\n".toByteArray()))) }
        assertEquals(SessionEnd.Unsupported(UnsupportedStream.Segmented), session().run())
        // a server that calls it audio
        opener.then { OpenedStream("audio/mpeg", 0, ScriptedSource(listOf("#EXTM3U\n#EXT-X-VERSION:3\n".toByteArray()))) }
        assertEquals(SessionEnd.Unsupported(UnsupportedStream.Segmented), session(id = "s2").run())
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun somethingThatIsNotAudioIsNotRecorded() = runBlocking {
        opener.then { OpenedStream("text/html", 0, ScriptedSource(listOf("<html>not a stream</html>".toByteArray()))) }
        assertEquals(SessionEnd.Unsupported(UnsupportedStream.Format), session().run())
    }

    @Test
    fun aFolderThatCannotBeWrittenToEndsTheRecordingBeforeAnyFile() = runBlocking {
        storage.writable = false
        opener.then { mp3(ScriptedSource(chunks(stream))) }
        assertEquals(SessionEnd.NoFolder, session().run())
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun aDiskThatFillsUpEndsItAndKeepsWhatWasWritten() = runBlocking {
        lateinit var session: RecordingSession
        opener.then { mp3(ScriptedSource(chunks(stream), onEnd = { session.stop() })) }
        session = session()
        storage.failWrites = true
        assertEquals(SessionEnd.StorageLow, session.run())
    }

    @Test
    fun tooLittleRoomStopsItAtOnce() = runBlocking {
        storage.free = 100 * MB
        opener.then { mp3(ScriptedSource(chunks(stream))) }
        assertEquals(SessionEnd.StorageLow, session().run())
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun theTotalLimitStopsItToo() = runBlocking {
        library.add(track(11 * GB))
        storage.free = 50 * GB
        opener.then { mp3(ScriptedSource(chunks(stream))) }
        assertEquals(SessionEnd.LimitReached, session().run())
    }

    @Test
    fun orDeletesTheOldestWhenThatIsTheChoice() = runBlocking {
        settings = settings.copy(limits = StorageLimits(action = LimitAction.PruneOldest))
        library.add(track(11 * GB))
        library.pruneFreed = 3 * GB
        storage.free = 50 * GB
        lateinit var session: RecordingSession
        opener.then { mp3(ScriptedSource(chunks(stream), onEnd = { session.stop() })) }
        session = session()
        assertEquals(SessionEnd.UserStopped, session.run())
        // the recording's own tracks are never the ones that go
        assertEquals(setOf("s1"), library.pruned.first().second)
        assertEquals((11 * GB - (10 * GB * 0.9).toLong()), library.pruned.first().first)
    }

    @Test
    fun pruningThatFreesNothingStopsIt() = runBlocking {
        settings = settings.copy(limits = StorageLimits(action = LimitAction.PruneOldest))
        library.add(track(11 * GB))
        storage.free = 50 * GB
        opener.then { mp3(ScriptedSource(chunks(stream))) }
        assertEquals(SessionEnd.LimitReached, session().run())
    }

    @Test
    fun roomRunningShortIsAWarningWhileItRecords() = runBlocking {
        storage.free = 800 * MB
        val source = LiveSource()
        opener.then { mp3(source) }
        val session = session()
        val job = async { session.run() }
        source.push(stream.copyOf(metaInt + 1 + 16 * 3 + frame * 3))
        withTimeout(5_000) { while (session.state.value.warning == null || session.state.value.phase != Phase.Recording) delay(5) }
        assertEquals(SpaceIssue.LowSpace, session.state.value.warning)
        assertEquals(Phase.Recording, session.state.value.phase)
        session.stop()
        job.await()
        assertEquals(null, session.state.value.warning)
    }

    @Test
    fun cancellingItFinishesTheFilesAndKeepsTheTracks() = runBlocking {
        val source = LiveSource()
        opener.then { mp3(source) }
        val session = session()
        val done = CompletableDeferred<Throwable?>()
        val job = launch(Dispatchers.IO) {
            try {
                session.run()
                done.complete(null)
            } catch (e: CancellationException) {
                done.complete(e)
                throw e
            }
        }
        source.push(stream.copyOf(2 * metaInt + 33 + 1 + frame * 60))
        // Song A's file and Song B's
        withTimeout(5_000) { while (storage.files.size < 2) delay(5) }
        job.cancel()
        withTimeout(5_000) { assertTrue(done.await() is CancellationException) }
        assertTrue(source.closed)
        assertTrue(storage.files.all { it.closed })
        assertTrue(library.tracks.isNotEmpty())
        assertEquals(Phase.Done, session.state.value.phase)
    }

    private fun track(size: Long) = RecordedTrack(
        sessionId = "old", station = station, sessionStartedAt = 1, startedAt = 1, fileUri = "mem://old", fileName = "old.mp3",
        artist = null, title = null, durationMs = 1, sizeBytes = size, complete = true, format = "mp3",
    )
}
