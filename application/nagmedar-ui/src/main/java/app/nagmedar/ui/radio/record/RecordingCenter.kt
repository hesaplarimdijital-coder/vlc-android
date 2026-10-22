package app.nagmedar.ui.radio.record

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A word for the user about a recording that was asked for. */
sealed interface RecordingNotice {
    /** [maxParallel] recordings are going on: this one is not made. */
    data class Full(val station: String, val maxParallel: Int) : RecordingNotice

    /** The station is being recorded already; the running recording now lasts until the later end. */
    data class Merged(val station: String) : RecordingNotice

    /** The recording cannot run: the system would not let a service start. */
    data class CannotStart(val station: String) : RecordingNotice
}

/** Tells the user what happened to a recording when the app is not in front of them (notifications in the app). */
interface RecordingAlerts {
    /** A recording ended; [ended] says why and what it saved. */
    fun ended(ended: EndedRecording)

    /** A recording a schedule asked for could not be made. */
    fun refused(notice: RecordingNotice)
}

/** What the Recordings tab asks of the recordings going on. */
interface RecordingControl {
    /** The recordings going on. */
    val live: StateFlow<List<LiveRecording>>

    /** What came of the recordings asked for, for the screen. */
    val notices: SharedFlow<RecordingNotice>

    /** Whether [station] is being recorded now. */
    fun recording(station: RecordingStation): Boolean

    /** Records [station] until [endAt] (ms; null: until stopped); [scheduleId] says which schedule asked. */
    fun start(station: RecordingStation, endAt: Long? = null, scheduleId: Long? = null)

    fun stop(sessionId: String)

    fun stopAll()
}

/**
 * S-1107: the app's radio recordings: asking for one (resolving the address the station plays
 * from, as the player does), stopping them, and telling the user how they ended. [hold] keeps the
 * app alive while recordings go (a foreground service) and answers false where the system
 * refuses; [live] and [starting] are what the screens and the service show. [runs] is where what
 * came of each run of a schedule is kept.
 */
class RecordingCenter(
    val hub: RecordingHub,
    private val scope: CoroutineScope,
    private val resolve: suspend (RecordingStation) -> String,
    private val hold: () -> Boolean,
    private val alerts: RecordingAlerts,
    private val runs: ScheduleRunLog = ScheduleRunLog.None,
) : RecordingControl {
    private val pending = MutableStateFlow(0)
    private val noticeFlow = MutableSharedFlow<RecordingNotice>(extraBufferCapacity = 8)

    override val live: StateFlow<List<LiveRecording>> get() = hub.live

    /** How many recordings are being started (their address is being found). */
    val starting: StateFlow<Int> = pending

    override val notices: SharedFlow<RecordingNotice> = noticeFlow

    init {
        scope.launch {
            hub.ends.collect { ended ->
                alerts.ended(ended)
                keep { runs.ended(ended.request.sessionId, RunStatus.of(ended.end)) }
            }
        }
    }

    /** The log is a record, not a part of recording: a failure of it never stops one. */
    private suspend fun keep(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the next restart finds a run left Running and takes it for interrupted: a word wrong, nothing lost
        }
    }

    override fun recording(station: RecordingStation): Boolean = hub.recording(station.key)

    override fun start(station: RecordingStation, endAt: Long?, scheduleId: Long?) {
        begin(station, endAt, scheduleId, null)
    }

    /**
     * Records [station] for the run [window] of the schedule [scheduleId]; false when the system would
     * not let the service start (the run is not begun, and is tried again when the app is in front).
     */
    fun startRun(station: RecordingStation, window: RecordingWindow, scheduleId: Long): Boolean = begin(station, window.end, scheduleId, window.start)

    private fun begin(station: RecordingStation, endAt: Long?, scheduleId: Long?, windowStart: Long?): Boolean {
        pending.update { it + 1 }
        if (!hold()) {
            pending.update { it - 1 }
            refuse(RecordingNotice.CannotStart(station.name), scheduleId != null)
            return false
        }
        scope.launch {
            try {
                val address = try {
                    resolve(station).takeIf { it.isNotBlank() } ?: station.streamUrl
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // the directory not answering never keeps a station from being recorded
                    station.streamUrl
                }
                val request = RecordingRequest(UUID.randomUUID().toString(), station.copy(streamUrl = address), endAt, scheduleId, windowStart)
                // the run is on before the recording is: one that ends at once still finds its row
                val run = if (scheduleId != null && windowStart != null && endAt != null) Triple(scheduleId, windowStart, endAt) else null
                if (run != null) keep { runs.began(run.first, run.second, run.third, request.sessionId) }
                when (val result = hub.start(request)) {
                    StartResult.Started -> Unit
                    is StartResult.Merged -> {
                        if (run != null) keep { runs.began(run.first, run.second, run.third, result.sessionId) }
                        noticeFlow.tryEmit(RecordingNotice.Merged(station.name))
                    }
                    StartResult.Full -> {
                        if (run != null) keep { runs.failed(run.first, run.second, run.third) }
                        refuse(RecordingNotice.Full(station.name, hub.maxParallel), scheduleId != null)
                    }
                }
            } finally {
                pending.update { it - 1 }
            }
        }
        return true
    }

    /** A refusal is said on screen; one of a schedule, which nobody is waiting for, is a notification too. */
    private fun refuse(notice: RecordingNotice, scheduled: Boolean) {
        noticeFlow.tryEmit(notice)
        if (scheduled) alerts.refused(notice)
    }

    override fun stop(sessionId: String) = hub.stop(sessionId)

    override fun stopAll() = hub.stopAll()
}
