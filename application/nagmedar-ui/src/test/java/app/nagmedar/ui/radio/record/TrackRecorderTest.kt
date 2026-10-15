package app.nagmedar.ui.radio.record

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** S-1107: a stream is cut into a file per title, at frame boundaries, and the files are named and tagged. */
class TrackRecorderTest {

    private class Run(val storage: MemoryStorage) {
        val saved = ArrayList<SavedTrack>()
        val started = ArrayList<TrackTag?>()
        val dropped = ArrayList<TrackTag?>()
    }

    private val station = RecordingStation("Radyo", "https://yayin.example/radyo", uuid = "r1")
    private val frame = 417
    private val metaInt = frame * 50

    private fun run(
        stream: ByteArray,
        metaInt: Int,
        format: AudioFormat = AudioFormat.Mp3,
        settings: RecordingSettings = RecordingSettings(minSeconds = 1),
        chunk: Int = 1_000,
        holdBytes: Long = TrackRecorder.DEFAULT_HOLD_BYTES,
        storage: MemoryStorage = MemoryStorage(),
    ): Run {
        val run = Run(storage)
        var time = 0L
        val recorder = TrackRecorder(
            station = station,
            format = format,
            storage = storage,
            settings = settings,
            splits = format.isOgg || metaInt > 0,
            listener = object : TrackListener {
                override fun started(tag: TrackTag?) {
                    run.started += tag
                }

                override fun saved(track: SavedTrack) {
                    run.saved += track
                }

                override fun dropped(tag: TrackTag?) {
                    run.dropped += tag
                }
            },
            clock = { 1_792_063_805_000L + (++time) * 1000 },
            zone = ZoneId.of("Europe/Istanbul"),
            holdBytes = holdBytes,
        )
        val framer: Framer = when (format) {
            AudioFormat.Mp3 -> MpegFramer()
            AudioFormat.Aac -> AdtsFramer()
            else -> OggFramer()
        }
        val units = { unit: MediaUnit -> recorder.unit(unit) }
        val demux = IcyDemuxer(
            metaInt,
            object : IcyDemuxer.Listener {
                override fun audio(data: ByteArray, offset: Int, length: Int) = framer.feed(data, offset, length, units)

                override fun metadata(block: ByteArray, length: Int, audioOffset: Long) {
                    StreamTitles.title(StreamTitles.decode(block, length))?.let { recorder.title(audioOffset, it) }
                }
            },
        )
        var i = 0
        while (i < stream.size) {
            val n = minOf(chunk, stream.size - i)
            demux.feed(stream, i, n)
            i += n
        }
        framer.finish(units)
        recorder.finish()
        return run
    }

    /** 300 frames; titles at the 3rd and 5th metadata blocks (after 150 and 250 frames). */
    private val audio = Streams.mp3(0, 300)

    private val titles = mapOf(0 to "Artist A - Song A", 2 to "Artist B - Song B", 4 to "Artist C - Song C")

    private fun body(file: MemoryStorage.File): ByteArray {
        val bytes = file.bytes.toByteArray()
        // past the ID3 tag
        val size = (0..3).fold(0) { acc, k -> (acc shl 7) or bytes[6 + k].toInt() }
        return bytes.copyOfRange(10 + size, bytes.size)
    }

    private fun slice(from: Int, to: Int) = audio.copyOfRange(from * frame, to * frame)

    @Test
    fun aFileIsMadePerTitleAndCutAtTheFrameTheTitleCameAt() {
        val run = run(Streams.icy(audio, metaInt) { titles[it] }, metaInt)
        assertEquals(listOf("Artist A - Song A.mp3", "Artist B - Song B.mp3", "Artist C - Song C.mp3"), run.storage.kept.map { it.name })
        assertTrue(run.storage.kept.all { it.folder == "Radyo" && it.closed && it.mime == "audio/mpeg" })
        assertArrayEquals(slice(0, 150), body(run.storage.kept[0]))
        assertArrayEquals(slice(150, 250), body(run.storage.kept[1]))
        assertArrayEquals(slice(250, 300), body(run.storage.kept[2]))
        assertEquals(3, run.saved.size)
        assertEquals(TrackTag("Artist B", "Song B"), run.saved[1].tag)
    }

    @Test
    fun theFilesAreTheStreamsOwnBytesBehindATag() {
        val run = run(Streams.icy(audio, metaInt) { titles[it] }, metaInt)
        val file = run.storage.kept[1].bytes.toByteArray()
        assertEquals("ID3", String(file, 0, 3))
        val tagSize = (0..3).fold(0) { acc, k -> (acc shl 7) or file[6 + k].toInt() }
        // no ICY block left in the audio: the frames follow one another exactly
        val units = ArrayList<MediaUnit>()
        MpegFramer().apply {
            feed(file, 10 + tagSize, file.size - 10 - tagSize) { units += it }
            finish { units += it }
        }
        assertEquals(100, units.size)
        assertEquals(100L * frame, (file.size - 10 - tagSize).toLong())
        val text = String(file, 0, 10 + tagSize, Charsets.UTF_8)
        assertTrue(text.contains("Song B"))
        assertTrue(text.contains("Artist B"))
        assertTrue(text.contains("Radyo"))
        assertTrue(text.contains("2026-10-15"))
    }

    @Test
    fun theFirstAndTheLastTrackArePartialAndTheOnesBetweenAreComplete() {
        val run = run(Streams.icy(audio, metaInt) { titles[it] }, metaInt)
        assertEquals(listOf(false, true, false), run.saved.map { it.complete })
        assertEquals(listOf(AudioFormat.Mp3), run.saved.map { it.format }.distinct())
    }

    @Test
    fun aTrackKnowsItsLengthAndSize() {
        val run = run(Streams.icy(audio, metaInt) { titles[it] }, metaInt)
        val b = run.saved[1]
        assertEquals(100 * 26_122L / 1000, b.durationMs)
        assertEquals(run.storage.kept[1].bytes.size().toLong(), b.sizeBytes)
        assertEquals("mem://Radyo/Artist B - Song B.mp3", b.fileUri)
    }

    @Test
    fun theSameTitleAgainIsNotACut() {
        val run = run(Streams.icy(audio, metaInt) { if (it % 2 == 0) "Artist A - Song A" else "ARTIST  A - song a" }, metaInt)
        assertEquals(1, run.saved.size)
    }

    @Test
    fun noChangeBlocksAreNotCuts() {
        val run = run(Streams.icy(audio, metaInt) { if (it == 0) "Artist A - Song A" else null }, metaInt)
        assertEquals(1, run.saved.size)
        assertArrayEquals(audio, body(run.storage.kept[0]))
    }

    @Test
    fun theSameSongTwiceGetsANumber() {
        val run = run(Streams.icy(audio, metaInt) { mapOf(0 to "A - S", 2 to "B - S", 4 to "A - S")[it] }, metaInt)
        assertEquals(listOf("A - S.mp3", "B - S.mp3", "A - S (2).mp3"), run.storage.kept.map { it.name })
    }

    @Test
    fun aTitleThatIsTheStationsNameGivesAFileNamedByTheTime() {
        val run = run(Streams.icy(audio, metaInt) { mapOf(0 to "Radyo", 2 to "Artist B - Song B")[it] }, metaInt)
        assertEquals("Radyo - 2026-10-15 14.30.0", run.storage.kept[0].name.substring(0, 26))
        assertEquals("Artist B - Song B.mp3", run.storage.kept[1].name)
        assertNull(run.saved[0].tag)
    }

    @Test
    fun aStreamWithoutTitlesIsOneFileAndSaysSo() {
        val run = run(audio, 0)
        assertEquals(1, run.saved.size)
        assertArrayEquals(audio, body(run.storage.kept[0]))
        assertFalse(run.saved[0].complete)
    }

    @Test
    fun theAudioBeforeTheFirstTitleIsThatTitlesTrack() {
        // the first title arrives after 50 frames; they are in its file, not in one of their own
        val run = run(Streams.icy(audio, metaInt) { if (it == 0) "Artist A - Song A" else null }, metaInt)
        assertEquals(1, run.storage.kept.size)
        assertEquals("Artist A - Song A.mp3", run.storage.kept[0].name)
        assertEquals(listOf<TrackTag?>(TrackTag("Artist A", "Song A")), run.started)
    }

    @Test
    fun aFirstTitleThatNeverComesOpensTheFirstTrackAnyway() {
        val run = run(Streams.icy(audio, metaInt) { if (it == 3) "Artist A - Song A" else null }, metaInt, holdBytes = frame * 10L)
        // the first 200 frames are a track of their own, unnamed; the title cuts at its frame
        assertEquals(2, run.storage.kept.size)
        assertTrue(run.storage.kept[0].name.startsWith("Radyo - 2026-10-15"))
        assertEquals("Artist A - Song A.mp3", run.storage.kept[1].name)
        assertArrayEquals(slice(200, 300), body(run.storage.kept[1]))
    }

    @Test
    fun aTrackShorterThanTheLimitIsThrownAway() {
        // B is 100 frames = 2.6 s
        val run = run(Streams.icy(audio, metaInt) { titles[it] }, metaInt, settings = RecordingSettings(minSeconds = 5))
        assertTrue(run.storage.kept.isEmpty())
        assertTrue(run.storage.files.all { it.deleted })
        assertEquals(3, run.dropped.size)
        assertTrue(run.saved.isEmpty())
    }

    @Test
    fun orKeptAndMarkedPartial() {
        val run = run(Streams.icy(audio, metaInt) { titles[it] }, metaInt, settings = RecordingSettings(minSeconds = 3, keepShort = true))
        assertEquals(3, run.saved.size)
        // B is 2.6 s: shorter than three seconds, so not complete though a title change began and ended it
        assertEquals(listOf(false, false, false), run.saved.map { it.complete })
    }

    @Test
    fun onlyTheShortOnesAreDroppedWhenSomeAreLongEnough() {
        val long = Streams.mp3(0, 500)
        val run = run(Streams.icy(long, metaInt) { mapOf(0 to "A - A", 4 to "B - B", 6 to "C - C")[it] }, metaInt, settings = RecordingSettings(minSeconds = 3))
        // A: 250 frames = 6.5 s; B: 100 frames = 2.6 s; C: 150 frames = 3.9 s
        assertEquals(listOf("A - A.mp3", "C - C.mp3"), run.storage.kept.map { it.name })
        assertEquals(listOf<TrackTag?>(TrackTag("B", "B")), run.dropped)
    }

    @Test
    fun paddingGivesTheNextTrackTheEndOfTheLastAndTheLastTheStartOfTheNext() {
        val settings = RecordingSettings(minSeconds = 1, padBeforeSeconds = 1, padAfterSeconds = 1)
        val run = run(Streams.icy(audio, metaInt) { titles[it] }, metaInt, settings = settings)
        // a second of 26.122 ms frames is 39 frames
        val a = body(run.storage.kept[0]).size / frame
        val b = body(run.storage.kept[1]).size / frame
        assertEquals(150 + 39, a)
        assertEquals(39 + 100 + 39, b)
        // B begins with the last frames of A
        assertArrayEquals(slice(150 - 39, 150), body(run.storage.kept[1]).copyOfRange(0, 39 * frame))
        assertArrayEquals(slice(150, 250), body(run.storage.kept[1]).copyOfRange(39 * frame, 139 * frame))
        // and A goes on with the first of B
        assertArrayEquals(slice(150, 189), body(run.storage.kept[0]).copyOfRange(150 * frame, a * frame))
    }

    @Test
    fun theFilesAreTheSameWhateverTheReadsAre() {
        val stream = Streams.icy(audio, metaInt) { titles[it] }
        val whole = run(stream, metaInt, chunk = stream.size)
        for (chunk in listOf(1, 13, 417, 4_099)) {
            val split = run(stream, metaInt, chunk = chunk)
            assertEquals(whole.storage.kept.map { it.name }, split.storage.kept.map { it.name })
            whole.storage.kept.zip(split.storage.kept).forEach { (a, b) -> assertArrayEquals("chunk $chunk", body(a), body(b)) }
        }
    }

    @Test
    fun aacIsCutAndTaggedToo() {
        val adts = Streams.adts(0, 300)
        val frameSize = 300
        val interval = frameSize * 50
        val run = run(Streams.icy(adts, interval) { titles[it] }, interval, format = AudioFormat.Aac)
        assertEquals(listOf("Artist A - Song A.aac", "Artist B - Song B.aac", "Artist C - Song C.aac"), run.storage.kept.map { it.name })
        assertEquals("audio/aac", run.storage.kept[0].mime)
        assertEquals("ID3", String(run.storage.kept[0].bytes.toByteArray(), 0, 3))
    }

    @Test
    fun anOggChainIsCutWhereTheStationChainsAndNamedByItsComments() {
        val one = Streams.vorbisStream(1, "ARTIST=Biri", "TITLE=Bir", audioPages = 60)
        val two = Streams.vorbisStream(2, "ARTIST=Öteki", "TITLE=İki", audioPages = 60)
        val run = run(one + two, 0, format = AudioFormat.Ogg, settings = RecordingSettings(minSeconds = 1))
        assertEquals(listOf("Biri - Bir.ogg", "Öteki - İki.ogg"), run.storage.kept.map { it.name })
        assertArrayEquals(one, run.storage.kept[0].bytes.toByteArray())
        assertArrayEquals(two, run.storage.kept[1].bytes.toByteArray())
        assertEquals("audio/ogg", run.storage.kept[0].mime)
        assertEquals(listOf(false, false), run.saved.map { it.complete })
    }

    @Test
    fun aWriteThatFailsReachesTheCaller() {
        val storage = MemoryStorage().apply { failWrites = true }
        val failure = runCatching { run(Streams.icy(audio, metaInt) { titles[it] }, metaInt, storage = storage) }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
    }
}
