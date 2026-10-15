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
 * refuses; [live] and [starting] are what the screens and the service show.
 */
class RecordingCenter(
    val hub: RecordingHub,
    private val scope: CoroutineScope,
    private val resolve: suspend (RecordingStation) -> String,
    private val hold: () -> Boolean,
    private val alerts: RecordingAlerts,
) : RecordingControl {
    private val pending = MutableStateFlow(0)
    private val noticeFlow = MutableSharedFlow<RecordingNotice>(extraBufferCapacity = 8)

    override val live: StateFlow<List<LiveRecording>> get() = hub.live

    /** How many recordings are being started (their address is being found). */
    val starting: StateFlow<Int> = pending

    override val notices: SharedFlow<RecordingNotice> = noticeFlow

    init {
        scope.launch { hub.ends.collect { alerts.ended(it) } }
    }

    override fun recording(station: RecordingStation): Boolean = hub.recording(station.key)

    override fun start(station: RecordingStation, endAt: Long?, scheduleId: Long?) {
        pending.update { it + 1 }
        if (!hold()) {
            pending.update { it - 1 }
            refuse(RecordingNotice.CannotStart(station.name), scheduleId != null)
            return
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
                val request = RecordingRequest(UUID.randomUUID().toString(), station.copy(streamUrl = address), endAt, scheduleId)
                when (hub.start(request)) {
                    StartResult.Started -> Unit
                    is StartResult.Merged -> noticeFlow.tryEmit(RecordingNotice.Merged(station.name))
                    StartResult.Full -> refuse(RecordingNotice.Full(station.name, hub.maxParallel), scheduleId != null)
                }
            } finally {
                pending.update { it - 1 }
            }
        }
    }

    /** A refusal is said on screen; one of a schedule, which nobody is waiting for, is a notification too. */
    private fun refuse(notice: RecordingNotice, scheduled: Boolean) {
        noticeFlow.tryEmit(notice)
        if (scheduled) alerts.refused(notice)
    }

    override fun stop(sessionId: String) = hub.stop(sessionId)

    override fun stopAll() = hub.stopAll()
}
