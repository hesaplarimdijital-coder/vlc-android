package app.nagmedar.ui.player.record

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

/** What records (S-607): libVLC's record of what the playback service plays. */
interface RecordingEngine {
    /** Whether what plays can be recorded: libVLC plays it (not the music engine). */
    val available: Boolean

    /** Whether it plays now: a pause does not count in the recording's time. */
    val playing: Boolean

    /** What plays, for the file's name. */
    fun title(): String?

    /** libVLC records into [directory]; false when it was not asked. */
    fun start(directory: String): Boolean

    fun stop(): Boolean
}

/**
 * Where recordings are made and kept (S-607). libVLC writes into a path, so a recording is made
 * in a folder of its own in the app's storage, and copied into the chosen folder (or the
 * default one) once it stops.
 */
interface RecordingStorage {
    /** A new, empty folder for one recording; null when there is no storage to write in. */
    fun newFolder(): File?

    /** The bytes free where [folder] is. */
    fun freeBytes(folder: File): Long

    /** The bytes written into [folder] so far. */
    fun bytes(folder: File): Long

    /** The file libVLC wrote: [path], when it said it, else what is in [folder]; null for nothing written. */
    fun written(folder: File, path: String?): File?

    /** Copies [file] into the recordings' folder as [name], telling [progress] the bytes copied of the total. */
    @Throws(RecordingException::class)
    suspend fun keep(file: File, name: String, durationMs: Long, progress: (Long, Long) -> Unit): KeptRecording

    /** [folder] and what is in it go. */
    fun discard(folder: File)

    /** Folders of recordings an earlier run left (the app was closed while one was made or kept). */
    fun leftovers(): List<File> = emptyList()
}

/**
 * The recordings (S-607): one at a time, started and stopped from the player (Shift+R, its
 * menus) or the playback service's options. libVLC says when it records and, when it stops, the
 * file it wrote (RecordChanged); a recording that does not start within [START_TIMEOUT_MS] is
 * one VLC cannot make of this input. The media ending, or the storage running low, stops it; what
 * was written is kept. [tick] moves the clock on while something happens; [state] is Compose
 * state, read by the indicator over the video.
 */
@Stable
class Recorder(
    private val engine: RecordingEngine,
    private val storage: RecordingStorage,
    private val scope: CoroutineScope,
    /** What names a recording of something without a title: the app. */
    private val fallbackName: String,
    /** Where the storage's work is done: off the main thread. */
    private val io: CoroutineContext = Dispatchers.IO,
    /** Each outcome as it comes, for those who show no card. */
    private val onOutcome: (RecordingOutcome) -> Unit = {},
) {
    private sealed interface Phase {
        data object Idle : Phase

        /** Its folder is being made. */
        data object Preparing : Phase

        data class Starting(val folder: File, val sinceMs: Long) : Phase

        data class Recording(val folder: File, val startedAtMs: Long, val title: String?, val elapsedMs: Long, val lastTickMs: Long, val bytes: Long) : Phase

        data class Finishing(val recording: Recording, val sinceMs: Long, val forSpace: Boolean) : Phase

        data class Saving(val total: Long) : Phase
    }

    private var phase: Phase = Phase.Idle
        set(value) {
            field = value
            status = statusOf(value)
        }

    /** What the indicator shows. */
    var status: RecordingStatus by mutableStateOf(RecordingStatus.Off)
        private set

    /** The last recording's outcome, for the card over the video; null once it is dismissed. */
    var outcome: RecordingOutcome? by mutableStateOf(null)
        private set

    fun dismiss() {
        outcome = null
    }

    private fun tell(value: RecordingOutcome) {
        outcome = value
        onOutcome(value)
    }

    /** A recording that could not be kept, still in the app's storage: kept again by [retry]. */
    private var unkept: Unkept? = null

    private class Unkept(val folder: File, val file: File, val name: String, val durationMs: Long, val forSpace: Boolean)

    /** The recording's date and what it records, for its file's name. */
    private var startedAt = 0L
    private var title: String? = null

    /** A measure of the folder is under way: the next tick does not start another. */
    private var measuring = false

    /** Counts the starts: a start's work, back from the storage, goes on only while it is the latest. */
    private var attempt = 0

    /** Whether a recording is starting or being made: the player's options say "Stop recording". Compose state. */
    val recording: Boolean get() = status.let { it == RecordingStatus.Starting || it is RecordingStatus.On }

    /** Whether something happens that [tick] moves on. */
    val busy: Boolean get() = phase !is Phase.Idle

    /** Shift+R: starts a recording, or stops the one being made. [nowMs] the clock, [wallMs] the date it bears. */
    fun toggle(nowMs: Long, wallMs: Long) {
        when (phase) {
            is Phase.Idle -> start(nowMs, wallMs)
            is Phase.Preparing, is Phase.Starting, is Phase.Recording -> stop(nowMs)
            // still being written or kept: the next one waits
            is Phase.Finishing, is Phase.Saving -> Unit
        }
    }

    fun start(nowMs: Long, wallMs: Long) {
        if (phase !is Phase.Idle) return
        if (!engine.available) return fail(RecordingFailure.NotSupported)
        phase = Phase.Preparing
        val mine = ++attempt
        scope.launch {
            val (folder, failure) = withContext(io) {
                val made = storage.newFolder() ?: return@withContext null to RecordingFailure.Write
                if (storage.freeBytes(made) < MIN_FREE_BYTES) {
                    storage.discard(made)
                    null to RecordingFailure.NoSpace
                } else {
                    made to null
                }
            }
            // stopped meanwhile, perhaps started again
            if (mine != attempt || phase != Phase.Preparing) {
                folder?.let(::discard)
                return@launch
            }
            if (folder == null) {
                phase = Phase.Idle
                return@launch fail(failure ?: RecordingFailure.Write)
            }
            if (!engine.start(folder.path)) {
                discard(folder)
                phase = Phase.Idle
                return@launch fail(RecordingFailure.NotSupported)
            }
            outcome = null
            startedAt = wallMs
            title = engine.title()
            phase = Phase.Starting(folder, nowMs)
        }
    }

    /** Stops the recording; what it wrote is kept once libVLC says it stopped. */
    fun stop(nowMs: Long) = stop(nowMs, forSpace = false)

    private fun stop(nowMs: Long, forSpace: Boolean) {
        when (val current = phase) {
            is Phase.Preparing -> phase = Phase.Idle
            is Phase.Starting -> {
                engine.stop()
                discard(current.folder)
                phase = Phase.Idle
            }
            is Phase.Recording -> {
                engine.stop()
                phase = Phase.Finishing(current, nowMs, forSpace)
            }
            else -> Unit
        }
    }

    /** libVLC's RecordChanged: it records, or it stopped and wrote [path]. */
    fun onRecordChanged(recording: Boolean, path: String?, nowMs: Long) {
        val current = phase
        if (recording) {
            if (current is Phase.Starting) phase = Phase.Recording(current.folder, startedAt, title, 0L, nowMs, 0L)
            return
        }
        when (current) {
            is Phase.Recording -> finish(Phase.Finishing(current, nowMs, forSpace = false), path)
            is Phase.Finishing -> finish(current, path)
            is Phase.Starting -> {
                discard(current.folder)
                phase = Phase.Idle
                tell(RecordingOutcome.Failed(RecordingFailure.NotSupported))
            }
            else -> Unit
        }
    }

    /** What plays ended, stopped or changed: libVLC stops recording it; a recording not yet started never will. */
    fun onPlaybackEnded(nowMs: Long) {
        when (val current = phase) {
            is Phase.Starting -> {
                engine.stop()
                discard(current.folder)
                phase = Phase.Idle
                tell(RecordingOutcome.Failed(RecordingFailure.NotSupported))
            }
            is Phase.Recording -> phase = Phase.Finishing(current, nowMs, forSpace = false)
            else -> Unit
        }
    }

    /** The clock: the time recorded while it plays, the bytes written, the storage left, what waits too long. */
    fun tick(nowMs: Long) {
        when (val current = phase) {
            is Phase.Starting -> if (nowMs - current.sinceMs >= START_TIMEOUT_MS) {
                engine.stop()
                discard(current.folder)
                phase = Phase.Idle
                tell(RecordingOutcome.Failed(RecordingFailure.NotSupported))
            }
            is Phase.Recording -> {
                if (measuring) return
                measuring = true
                val playing = engine.playing
                scope.launch {
                    val (bytes, free) = try {
                        withContext(io) { storage.bytes(current.folder) to storage.freeBytes(current.folder) }
                    } finally {
                        measuring = false
                    }
                    // the same recording, still going
                    val now = phase as? Phase.Recording ?: return@launch
                    if (now.folder != current.folder) return@launch
                    val elapsed = now.elapsedMs + if (playing) (nowMs - now.lastTickMs).coerceAtLeast(0L) else 0L
                    phase = now.copy(elapsedMs = elapsed, lastTickMs = nowMs, bytes = bytes)
                    if (free < LOW_FREE_BYTES) stop(nowMs, forSpace = true)
                }
            }
            is Phase.Finishing -> if (nowMs - current.sinceMs >= FINISH_TIMEOUT_MS) finish(current, null)
            else -> Unit
        }
    }

    /** Keeps again the recording that could not be kept, in the folder chosen now. */
    fun retry() {
        val left = unkept ?: return
        if (phase !is Phase.Idle) return
        unkept = null
        outcome = null
        keep(left)
    }

    /**
     * The recordings an earlier run left are kept, one after the other, without a card: those
     * whose folders were made before [beforeMs] (they are named by the time they were made), never
     * one this run makes meanwhile.
     */
    fun recoverLeftovers(beforeMs: Long) {
        scope.launch {
            val folders = withContext(io) { storage.leftovers() }.filter { folder -> folder.name.toLongOrNull()?.let { it < beforeMs } ?: false }
            for (folder in folders) {
                val file = withContext(io) { storage.written(folder, null) }
                if (file == null) {
                    discard(folder)
                    continue
                }
                try {
                    storage.keep(file, file.name, 0L) { _, _ -> }
                    discard(folder)
                } catch (e: RecordingException) {
                    // left where it is for the next run
                }
            }
        }
    }

    private fun finish(finishing: Phase.Finishing, path: String?) {
        val recording = finishing.recording
        phase = Phase.Saving(recording.bytes)
        scope.launch {
            val file = withContext(io) { storage.written(recording.folder, path)?.takeIf { it.length() > 0 } }
            if (file == null) {
                discard(recording.folder)
                phase = Phase.Idle
                tell(RecordingOutcome.Failed(if (finishing.forSpace) RecordingFailure.NoSpace else RecordingFailure.Empty))
                return@launch
            }
            val name = recordingFileName(recording.title, recording.startedAtMs, file.extension, fallbackName)
            keep(Unkept(recording.folder, file, name, recording.elapsedMs, finishing.forSpace))
        }
    }

    private fun keep(left: Unkept) {
        phase = Phase.Saving(0L)
        scope.launch {
            val total = withContext(io) { left.file.length() }
            if (phase is Phase.Saving) phase = Phase.Saving(total)
            val result = try {
                // called from the copy's thread: only the indicator follows it, on the scope's thread
                val kept = storage.keep(left.file, left.name, left.durationMs) { copied, all ->
                    scope.launch { if (phase is Phase.Saving) status = RecordingStatus.Saving(copied, all) }
                }
                withContext(io) { storage.discard(left.folder) }
                RecordingOutcome.Saved(kept.uri, left.name, kept.folder, kept.tree, kept.mime, total, left.durationMs, left.forSpace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecordingException) {
                unkept = left
                RecordingOutcome.Failed(e.reason, canRetry = e.reason != RecordingFailure.NotSupported)
            } finally {
                phase = Phase.Idle
            }
            tell(result)
        }
    }

    /** [folder] and what is in it go, off the main thread. */
    private fun discard(folder: File) {
        scope.launch { withContext(io) { storage.discard(folder) } }
    }

    private fun fail(reason: RecordingFailure) = tell(RecordingOutcome.Failed(reason))

    private fun statusOf(phase: Phase): RecordingStatus = when (phase) {
        Phase.Idle -> RecordingStatus.Off
        Phase.Preparing, is Phase.Starting -> RecordingStatus.Starting
        is Phase.Recording -> RecordingStatus.On(phase.elapsedMs, phase.bytes)
        is Phase.Finishing -> RecordingStatus.Saving(0L, phase.recording.bytes)
        is Phase.Saving -> RecordingStatus.Saving(0L, phase.total)
    }

    companion object {
        /** libVLC says it records within this; else it cannot record this input. */
        const val START_TIMEOUT_MS = 5_000L

        /** libVLC says it stopped within this; else what is in the folder is taken. */
        const val FINISH_TIMEOUT_MS = 5_000L

        /** No recording starts with less free. */
        const val MIN_FREE_BYTES = 256L * 1024 * 1024

        /** A recording stops with less free, and is kept. */
        const val LOW_FREE_BYTES = 64L * 1024 * 1024

        /** How often [tick] is called while [busy]. */
        const val TICK_MS = 500L
    }
}
