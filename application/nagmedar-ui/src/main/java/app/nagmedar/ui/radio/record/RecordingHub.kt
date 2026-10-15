package app.nagmedar.ui.radio.record

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A recording that is going on: what was asked for and how it is doing. */
data class LiveRecording(val request: RecordingRequest, val state: SessionState) {
    val sessionId: String get() = request.sessionId
}

/** A recording that is over, and why. */
data class EndedRecording(val request: RecordingRequest, val end: SessionEnd, val state: SessionState)

/** What asking the hub for a recording came to. */
sealed interface StartResult {
    /** A recording began. */
    data object Started : StartResult

    /** The station is already being recorded: that recording now runs until the later of the two ends. */
    data class Merged(val sessionId: String) : StartResult

    /** [RecordingHub.maxParallel] recordings are going on: this one is not made. */
    data object Full : StartResult
}

/**
 * S-1107: the recordings going on in the app. Stations are recorded side by side, each on its own
 * connection, up to [maxParallel]; asking for a station already being recorded does not start a
 * second recording of it but moves the running one's end to the later of the two (two schedules
 * that overlap on one station make one long recording). Past [maxParallel] the request is
 * refused ([StartResult.Full]) and the caller says so. [live] is the recordings now; [ends] says
 * why each one ended.
 */
class RecordingHub(
    private val create: (RecordingRequest) -> RecordingSession,
    private val scope: CoroutineScope,
    val maxParallel: Int = MAX_PARALLEL,
) {
    private class Entry(val session: RecordingSession, var job: Job? = null, var watcher: Job? = null)

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private val liveState = MutableStateFlow<List<LiveRecording>>(emptyList())
    private val endings = MutableSharedFlow<EndedRecording>(extraBufferCapacity = 16)

    /** The recordings going on, the oldest first. */
    val live: StateFlow<List<LiveRecording>> = liveState

    /** Each recording that ends, with why. */
    val ends: SharedFlow<EndedRecording> = endings

    /** Whether [stationKey]'s station is being recorded now. */
    fun recording(stationKey: String): Boolean = synchronized(lock) { entries.values.any { it.session.request.station.key == stationKey } }

    fun start(request: RecordingRequest): StartResult = synchronized(lock) {
        entries.values.firstOrNull { it.session.request.station.key == request.station.key }?.let {
            it.session.extend(request.endAt)
            refresh()
            return StartResult.Merged(it.session.request.sessionId)
        }
        if (entries.size >= maxParallel) return StartResult.Full
        val session = create(request)
        val entry = Entry(session)
        entries[request.sessionId] = entry
        entry.watcher = scope.launch { session.state.collect { refresh() } }
        entry.job = scope.launch {
            val end = try {
                session.run()
            } catch (e: CancellationException) {
                SessionEnd.UserStopped
            } catch (e: Exception) {
                SessionEnd.Failed()
            }
            val last = session.state.value
            synchronized(lock) { entries.remove(request.sessionId) }
            entry.watcher?.cancel()
            refresh()
            endings.tryEmit(EndedRecording(request, end, last))
        }
        refresh()
        StartResult.Started
    }

    /** Stops the recording [sessionId]. */
    fun stop(sessionId: String) {
        synchronized(lock) { entries[sessionId] }?.session?.stop()
    }

    fun stopAll() {
        synchronized(lock) { entries.values.toList() }.forEach { it.session.stop() }
    }

    private fun refresh() {
        liveState.value = synchronized(lock) { entries.values.map { LiveRecording(it.session.request, it.session.state.value) } }
    }

    companion object {
        /** Stations recorded at once. */
        const val MAX_PARALLEL = 3
    }
}
