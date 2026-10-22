package app.nagmedar.ui.radio.record

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.ConcurrentLinkedQueue

/** Where a recording is. */
enum class Phase { Connecting, Recording, Reconnecting, Done }

/**
 * S-1107: what a recording is doing, for the notification and the Recordings tab. [track] is the
 * title being recorded now (null: the stream names none), begun at [trackStartedAt]; [bytes] is
 * what was written to files; [splits] says whether the stream sends titles to cut tracks by (null
 * until known); [warning] is set while room runs short; [end] once the recording is over.
 */
data class SessionState(
    val phase: Phase = Phase.Connecting,
    val startedAt: Long = 0,
    val endAt: Long? = null,
    val track: TrackTag? = null,
    val trackStartedAt: Long = 0,
    val tracksSaved: Int = 0,
    val bytes: Long = 0,
    val format: AudioFormat? = null,
    val splits: Boolean? = null,
    val warning: SpaceIssue? = null,
    val reconnects: Int = 0,
    val end: SessionEnd? = null,
)

/**
 * S-1107: records one station's stream to files, on a connection of its own (the player is never
 * touched: listening goes on as it was). The stream is opened with `Icy-MetaData: 1` by the
 * [opener]; [IcyDemuxer] takes the metadata out, a [Framer] cuts the audio into frames, and a
 * [TrackRecorder] writes a file per title. A stream that drops is opened again (up to
 * [MAX_FAILURES] times in a row, waiting longer each time) and its tracks go on in new files.
 * Room is checked as [StoragePolicy] says. [run] returns why the recording ended; [stop] ends it
 * at once, [extend] moves its end. Everything written is closed and kept however it ends.
 */
class RecordingSession(
    val request: RecordingRequest,
    private val opener: StreamOpener,
    private val storage: TrackStorage,
    private val library: SessionLibrary,
    private val settings: () -> RecordingSettings,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutable = MutableStateFlow(SessionState(endAt = request.endAt))

    /** The recording as it is now. */
    val state: StateFlow<SessionState> = mutable

    @Volatile
    private var endAt: Long? = request.endAt

    @Volatile
    private var ending: SessionEnd? = null

    @Volatile
    private var source: StreamSource? = null

    private val unsaved = ConcurrentLinkedQueue<SavedTrack>()

    /** Bytes written by the connections before this one. */
    private var writtenBefore = 0L

    /** Ends the recording as the user's stop. */
    fun stop() = end(SessionEnd.UserStopped)

    /** The recording ends at [until] (ms; null: when stopped) instead, if that is later. */
    fun extend(until: Long?) {
        val current = endAt ?: return
        val later = if (until == null) null else maxOf(current, until)
        endAt = later
        mutable.update { it.copy(endAt = later) }
    }

    private fun end(reason: SessionEnd) {
        if (ending == null) ending = reason
        // a read that waits on the network is ended by closing its stream
        runCatching { source?.close() }
    }

    /** Records until it ends; returns why. Cancelling it finishes the files and ends as a stop. */
    suspend fun run(): SessionEnd = withContext(io) {
        mutable.update { it.copy(startedAt = clock()) }
        val reason = try {
            coroutineScope {
                val watcher = launch { watch() }
                // cancelled, the recording closes its stream: a read that waits on the network ends
                val closer = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        runCatching { source?.close() }
                    }
                }
                try {
                    record()
                } finally {
                    watcher.cancel()
                    closer.cancel()
                }
            }
        } catch (e: CancellationException) {
            mutable.update { it.copy(phase = Phase.Done, end = SessionEnd.UserStopped, warning = null) }
            throw e
        }
        mutable.update { it.copy(phase = Phase.Done, end = reason, warning = null) }
        reason
    }

    /** Ends the recording when its time is up. */
    private suspend fun watch() {
        while (true) {
            val until = endAt
            if (until == null) {
                pause(IDLE_WATCH_MS)
                continue
            }
            val wait = until - clock()
            if (wait <= 0) {
                end(SessionEnd.TimeUp)
                return
            }
            pause(minOf(wait, IDLE_WATCH_MS))
        }
    }

    private suspend fun record(): SessionEnd {
        var failures = 0
        var rounds = 0
        var refused: Int? = null
        while (true) {
            ending?.let { return it }
            mutable.update { it.copy(phase = if (rounds == 0) Phase.Connecting else Phase.Reconnecting, reconnects = rounds) }
            val connected = clock()
            try {
                pump()
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamUnsupported) {
                return SessionEnd.Unsupported(e.reason)
            } catch (e: FolderTrouble) {
                return if (storage.writable()) SessionEnd.StorageLow else SessionEnd.NoFolder
            } catch (e: SpaceTrouble) {
                return e.end
            } catch (e: StreamRefused) {
                refused = e.status
            } catch (e: IOException) {
                refused = null
            }
            ending?.let { return it }
            // cancelled while the stream was read: no new try
            currentCoroutineContext().ensureActive()
            // a connection that lasted is not a failure to count against the next one
            failures = if (clock() - connected >= STABLE_MS) 1 else failures + 1
            if (failures >= MAX_FAILURES) return SessionEnd.Failed(refused)
            rounds++
            waitFor(minOf(BACKOFF_MS shl (failures - 1), MAX_BACKOFF_MS))
        }
    }

    /** Waits [ms], ending early when the recording is ended. */
    private suspend fun waitFor(ms: Long) {
        var left = ms
        while (left > 0 && ending == null) {
            val step = minOf(left, 500L)
            pause(step)
            left -= step
        }
    }

    /** One connection: opens the stream and writes it until it ends or breaks. */
    private suspend fun pump() {
        val opened = opener.open(request.station.streamUrl)
        opened.playlist?.let { return pumpHls(it) }
        val stream = opened.source
        source = stream
        var recorder: TrackRecorder? = null
        try {
            if (ending != null) return
            val buffer = ByteArray(BUFFER)
            var count = readOrDrop(stream, buffer)
            if (count <= 0) throw IOException("the stream gave nothing")
            if (AudioFormat.isSegmented(opened.contentType) || startsPlaylist(buffer, count)) throw StreamUnsupported(UnsupportedStream.Segmented)
            val format = AudioFormat.detect(opened.contentType, buffer, count) ?: throw StreamUnsupported(UnsupportedStream.Format)
            if (!storage.writable()) throw FolderTrouble()
            space(null)?.let { throw SpaceTrouble(it) }
            val splits = format.isOgg || opened.metaInt > 0
            val rec = TrackRecorder(
                station = request.station,
                format = format,
                storage = storage,
                settings = settings(),
                splits = splits,
                listener = listener(),
                clock = clock,
                zone = zone,
                holdBytes = (opened.metaInt * 2L).coerceIn(MIN_HOLD, MAX_HOLD),
            )
            recorder = rec
            val framer: Framer = when (format) {
                AudioFormat.Mp3 -> MpegFramer()
                AudioFormat.Aac -> AdtsFramer()
                AudioFormat.Ogg, AudioFormat.Opus -> OggFramer()
                // only an HLS stream is kept as these
                AudioFormat.Ts, AudioFormat.M4a, AudioFormat.Mp4 -> throw StreamUnsupported(UnsupportedStream.Format)
            }
            val units = { unit: MediaUnit -> rec.unit(unit) }
            val demux = IcyDemuxer(
                opened.metaInt,
                object : IcyDemuxer.Listener {
                    override fun audio(data: ByteArray, offset: Int, length: Int) = framer.feed(data, offset, length, units)

                    override fun metadata(block: ByteArray, length: Int, audioOffset: Long) {
                        StreamTitles.title(StreamTitles.decode(block, length))?.let { rec.title(audioOffset, it) }
                    }
                },
            )
            mutable.update { it.copy(phase = Phase.Recording, format = format, splits = splits) }
            var checked = clock()
            var shown = checked
            while (true) {
                try {
                    demux.feed(buffer, 0, count)
                } catch (e: IOException) {
                    throw FolderTrouble()
                }
                flush()
                val now = clock()
                if (now - shown >= SHOW_MS) {
                    shown = now
                    publish(writtenBefore + rec.bytes)
                }
                if (now - checked >= CHECK_MS) {
                    checked = now
                    space(rec)?.let { throw SpaceTrouble(it) }
                }
                count = readOrDrop(stream, buffer)
                if (count < 0 || ending != null) break
            }
            framer.finish(units)
        } finally {
            closeUp(recorder, stream)
        }
    }

    /**
     * One HLS connection: the playlist is read by an [HlsReader], whose units go to one file (an HLS
     * stream names no titles). The files are checked for room after each segment.
     */
    private suspend fun pumpHls(start: HlsStart) {
        val stopper = object : StreamSource {
            override fun read(buffer: ByteArray): Int = -1

            override fun close() = start.client.cancel()
        }
        source = stopper
        var recorder: TrackRecorder? = null
        try {
            if (ending != null) return
            if (!storage.writable()) throw FolderTrouble()
            space(null)?.let { throw SpaceTrouble(it) }
            var checked = clock()
            var shown = checked
            val reader = HlsReader(start.client, clock, pause) { ending != null }
            reader.run(
                start.url,
                start.text,
                object : HlsReader.Output {
                    override fun format(format: AudioFormat) {
                        recorder = TrackRecorder(
                            station = request.station,
                            format = format,
                            storage = storage,
                            settings = settings(),
                            splits = false,
                            listener = listener(),
                            clock = clock,
                            zone = zone,
                        )
                        mutable.update { it.copy(phase = Phase.Recording, format = format, splits = false) }
                    }

                    override fun unit(unit: MediaUnit) {
                        try {
                            recorder?.unit(unit)
                        } catch (e: IOException) {
                            throw FolderTrouble()
                        }
                    }

                    override suspend fun segment() {
                        flush()
                        val now = clock()
                        val rec = recorder ?: return
                        if (now - shown >= SHOW_MS) {
                            shown = now
                            publish(writtenBefore + rec.bytes)
                        }
                        if (now - checked >= CHECK_MS) {
                            checked = now
                            space(rec)?.let { throw SpaceTrouble(it) }
                        }
                    }
                },
            )
        } finally {
            closeUp(recorder, stopper)
        }
    }

    /** A connection is over: its file is finished and kept, what it saved goes into the library, its stream is closed. */
    private suspend fun closeUp(recorder: TrackRecorder?, stream: StreamSource) {
        withContext(NonCancellable) {
            if (recorder != null) {
                recorder.finish()
                writtenBefore += recorder.bytes
                publish(writtenBefore)
            }
            flush()
            source = null
            runCatching { stream.close() }
        }
    }

    /** A read; -1 at the end of the stream or when the recording is ended; a failed read is a [StreamDropped]. */
    private fun readOrDrop(stream: StreamSource, buffer: ByteArray): Int = try {
        stream.read(buffer)
    } catch (e: IOException) {
        if (ending != null) -1 else throw StreamDropped(e)
    }

    private fun startsPlaylist(buffer: ByteArray, count: Int): Boolean =
        count >= 7 && String(buffer, 0, 7, Charsets.ISO_8859_1).equals("#EXTM3U", ignoreCase = true)

    private fun listener() = object : TrackListener {
        override fun started(tag: TrackTag?) {
            mutable.update { it.copy(track = tag, trackStartedAt = clock()) }
        }

        override fun saved(track: SavedTrack) {
            unsaved.add(track)
        }

        override fun dropped(tag: TrackTag?) = Unit
    }

    private fun publish(bytes: Long) {
        mutable.update { it.copy(bytes = bytes) }
    }

    /** The tracks finished since the last call go into the library. */
    private suspend fun flush() {
        while (true) {
            val track = unsaved.poll() ?: return
            val started = mutable.value.startedAt
            library.add(
                RecordedTrack(
                    sessionId = request.sessionId,
                    station = request.station,
                    sessionStartedAt = started,
                    startedAt = track.startedAt,
                    fileUri = track.fileUri,
                    fileName = track.fileName,
                    artist = track.tag?.artist,
                    title = track.tag?.title,
                    durationMs = track.durationMs,
                    sizeBytes = track.sizeBytes,
                    complete = track.complete,
                    format = track.format.extension,
                ),
            )
            mutable.update { it.copy(tracksSaved = it.tracksSaved + 1) }
        }
    }

    /**
     * Looks at the room left. Returns the end to give the recording when it must stop; deletes the
     * oldest recordings when that is the user's choice; sets the warning.
     */
    private suspend fun space(recorder: TrackRecorder?): SessionEnd? {
        val limits = settings().limits
        val free = recorder?.freeBytes()?.takeIf { it >= 0 } ?: storage.freeBytes()
        val used = library.usedBytes() + (recorder?.openSize ?: 0L)
        return when (val verdict = StoragePolicy.assess(free, used, limits)) {
            StorageVerdict.Ok -> {
                mutable.update { it.copy(warning = null) }
                null
            }
            is StorageVerdict.Warn -> {
                mutable.update { it.copy(warning = verdict.issue) }
                null
            }
            is StorageVerdict.Stop -> stopFor(verdict.issue)
            is StorageVerdict.Prune -> {
                val freed = library.prune(verdict.bytes, setOf(request.sessionId))
                if (freed <= 0) stopFor(verdict.issue) else null
            }
        }
    }

    private fun stopFor(issue: SpaceIssue): SessionEnd {
        val reason = if (issue == SpaceIssue.LowSpace) SessionEnd.StorageLow else SessionEnd.LimitReached
        end(reason)
        return reason
    }

    /** The server's stream broke (a read failed). */
    private class StreamDropped(cause: IOException) : IOException("the stream broke", cause)

    /** The folder cannot be written to, or a file could not be. */
    private class FolderTrouble : IOException("storage")

    /** Room is short and the recording must stop. */
    private class SpaceTrouble(val end: SessionEnd) : IOException("space")

    companion object {
        /** Streams that fail this many times in a row (never lasting [STABLE_MS]) end the recording. */
        const val MAX_FAILURES = 5

        private const val BUFFER = 16 * 1024
        private const val CHECK_MS = 5_000L
        private const val SHOW_MS = 1_000L
        private const val STABLE_MS = 30_000L
        private const val BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 16_000L
        private const val IDLE_WATCH_MS = 30_000L
        private const val MIN_HOLD = 64L * 1024L
        private const val MAX_HOLD = 4L * 1024L * 1024L
    }
}
