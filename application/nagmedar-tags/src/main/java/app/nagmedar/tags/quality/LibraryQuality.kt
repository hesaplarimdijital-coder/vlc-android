/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.4.0: from Metamatik (core/domain/src/main/kotlin/com/bobbyesp/metadator/core/domain/quality/LibraryQuality.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.quality

import app.nagmedar.tags.LibraryFile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** What the library knows of a song's audio: whether it is worth checking, and what was found. */
sealed interface TrackQuality {
    /** A lossy format, or one the check does not read: there is nothing to find. */
    data object NotApplicable : TrackQuality

    /** A lossless file not checked yet. */
    data object Unchecked : TrackQuality

    /** Being checked now. */
    data object Checking : TrackQuality

    data class Checked(val verdict: LosslessVerdict) : TrackQuality

    /** None of its audio could be decoded. */
    data object Unreadable : TrackQuality

    /** Whether the audio has been through a lossy encoder, surely or most likely. */
    val isFake: Boolean
        get() =
            this is Checked &&
                (verdict == LosslessVerdict.Transcoded ||
                    verdict == LosslessVerdict.ProbablyTranscoded)
}

/** Whether this song's audio is one the lossless check reads: FLAC and WAV. */
val LibraryFile.isLosslessCandidate: Boolean
    get() =
        fileExtension in LosslessExtensions ||
            mimeType?.lowercase()?.let { mime -> LosslessMimeTypes.any { mime.endsWith(it) } } ==
                true

private val LosslessExtensions = setOf("flac", "wav", "wave")
private val LosslessMimeTypes = setOf("/flac", "/x-flac", "/wav", "/x-wav", "/wave")

/**
 * A verdict as it is kept: with what identified the file when it was made, so that a file that
 * changed since is checked again. [verdict] is null for a file that could not be read.
 */
data class StoredVerdict(
    val uri: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    val verdict: LosslessVerdict?,
)

/**
 * Where verdicts are kept between runs of the app: a library of FLAC files takes minutes to check,
 * and each file only needs it once.
 */
interface QualityStore {
    suspend fun load(): List<StoredVerdict>

    suspend fun save(verdict: StoredVerdict)
}

/** How far the check of the library has got. */
data class LibraryQualityState(
    /** Every song of the library the scanner was given, by its URI. */
    val qualities: Map<String, TrackQuality> = emptyMap(),
    /** Lossless songs checked, of [total]. */
    val checked: Int = 0,
    val total: Int = 0,
) {
    val isRunning: Boolean
        get() = checked < total

    fun of(track: LibraryFile): TrackQuality =
        qualities[track.ref.uri]
            ?: if (track.isLosslessCandidate) TrackQuality.Unchecked else TrackQuality.NotApplicable

    /**
     * Nağmedar 1.4.0: the same for a song known by its file's URI only (the library's tables): what the URI's name ends
     * in says whether it is checked.
     */
    fun of(uri: String): TrackQuality =
        qualities[uri]
            ?: if (uri.substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase() in LosslessExtensions) TrackQuality.Unchecked
            else TrackQuality.NotApplicable
}

/**
 * Checks every lossless song of the library in the background, one at a time, and remembers what it
 * found, so that each file is checked once, and again only once it has changed. What the library
 * lists as fake lossless comes from here; nothing leaves the device.
 *
 * It works through the library it was last [submit]ted, in that order, and starts over from it when
 * a new one comes: a song added to the library is checked too, one removed is not.
 *
 * @param scope where the work runs: the app's, since it outlives any screen
 */
class LibraryQualityScanner(
    private val check: CheckLosslessUseCase,
    private val store: QualityStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val default: CoroutineDispatcher = Dispatchers.Default,
) {
    private val library = MutableStateFlow<List<LibraryFile>?>(null)
    private val _state = MutableStateFlow(LibraryQualityState())
    val state: StateFlow<LibraryQualityState> = _state.asStateFlow()

    /** What is known by URI: the verdicts read from the store, then those made since. */
    private val known = mutableMapOf<String, StoredVerdict>()
    private var worker: Job? = null

    /** Takes the library as it is now. Starts the work the first time. */
    fun submit(tracks: List<LibraryFile>) {
        library.value = tracks
        synchronized(this) {
            if (worker == null) worker = scope.launch(default) { work() }
        }
    }

    /**
     * Records a verdict made elsewhere (the editor checks the song it opens), so that the library
     * says the same without checking it again.
     */
    fun record(track: LibraryFile, verdict: LosslessVerdict?) {
        scope.launch(default) { keep(track, verdict) }
    }

    private suspend fun work() {
        val stored = runCatching { withContext(io) { store.load() } }.getOrNull()
        // A verdict recorded meanwhile is newer than the one the store had.
        synchronized(known) { stored?.forEach { if (it.uri !in known) known[it.uri] = it } }
        while (true) {
            val tracks = library.value ?: library.first { it != null }!!
            publish(tracks, checking = null)
            val next = tracks.firstOrNull { it.isLosslessCandidate && verdictOf(it) == null }
            if (next == null) {
                // All checked: wait for a library that differs.
                library.first { it != null && it !== tracks }
                continue
            }
            publish(tracks, checking = next)
            val verdict =
                try {
                    (check(next.ref) as? LosslessCheckResult.Checked)?.report?.verdict
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // One file the decoder chokes on must not stop the rest from being checked.
                    null
                }
            keep(next, verdict)
            // The check is heavy; whatever else wants the processor gets it between two.
            yield()
        }
    }

    private suspend fun keep(track: LibraryFile, verdict: LosslessVerdict?) {
        val stored =
            StoredVerdict(
                uri = track.ref.uri,
                sizeBytes = track.sizeBytes,
                modifiedEpochSeconds = track.dateModifiedEpochSeconds,
                verdict = verdict,
            )
        synchronized(known) { known[stored.uri] = stored }
        runCatching { withContext(io) { store.save(stored) } }
        library.value?.let { publish(it, checking = null) }
    }

    /** The verdict kept for [track], unless its file changed since: null when it needs checking. */
    private fun verdictOf(track: LibraryFile): StoredVerdict? {
        val stored = synchronized(known) { known[track.ref.uri] } ?: return null
        return stored.takeIf {
            it.sizeBytes == track.sizeBytes &&
                it.modifiedEpochSeconds == track.dateModifiedEpochSeconds
        }
    }

    private fun publish(tracks: List<LibraryFile>, checking: LibraryFile?) {
        var total = 0
        var checked = 0
        val qualities = HashMap<String, TrackQuality>(tracks.size)
        for (track in tracks) {
            val quality =
                if (!track.isLosslessCandidate) TrackQuality.NotApplicable
                else {
                    total++
                    val stored = verdictOf(track)
                    when {
                        stored != null -> {
                            checked++
                            stored.verdict?.let { TrackQuality.Checked(it) }
                                ?: TrackQuality.Unreadable
                        }
                        track.ref == checking?.ref -> TrackQuality.Checking
                        else -> TrackQuality.Unchecked
                    }
                }
            qualities[track.ref.uri] = quality
        }
        _state.update { LibraryQualityState(qualities, checked = checked, total = total) }
    }
}
