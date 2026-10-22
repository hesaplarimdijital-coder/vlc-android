package app.nagmedar.ui.radio.record

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The folder recordings go in, as it is now. */
sealed interface FolderState {
    /** The app's own music folder. */
    data object Default : FolderState

    /** A folder chosen, written in; [label] is its path on its drive. */
    data class Chosen(val label: String) : FolderState

    /** The folder chosen is gone (a card pulled out, the folder deleted). */
    data class Missing(val label: String) : FolderState

    /** The folder chosen is no longer ours to write in. */
    data class NoPermission(val label: String) : FolderState

    val usable: Boolean get() = this is Default || this is Chosen
}

/** What the tab needs of the device. */
interface RecordingEnvironment {
    /** Room left where recordings go, bytes; negative when not known. */
    fun freeBytes(): Long

    fun folder(): FolderState

    /** Whether the system lets alarms ring at the minute. */
    fun exactAlarms(): Boolean

    /** [schedule] was saved: its alarm is set at its next start. */
    fun scheduleSaved(schedule: RecordingSchedule)

    /** The schedule [id] was deleted: its alarm is taken off. */
    fun scheduleRemoved(id: Long)

    /** Starts the runs that should be on and were not started (the alarm came late, or the device was off). */
    suspend fun catchUp()
}

/** Plays recorded tracks in the normal player. */
fun interface TrackPlayer {
    fun play(tracks: List<RecordedTrack>, index: Int)
}

/** A station's recordings: its sessions, the newest first. */
data class StationGroup(val station: RecordingStation, val sessions: List<SessionGroup>) {
    val key: String get() = station.key
    val tracks: Int get() = sessions.sumOf { it.tracks.size }
    val bytes: Long get() = sessions.sumOf { it.bytes }
}

/** One recording (its tracks in the order they were recorded). */
data class SessionGroup(val sessionId: String, val station: RecordingStation, val startedAt: Long, val tracks: List<RecordedTrack>) {
    val bytes: Long get() = tracks.sumOf { it.sizeBytes }
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
    val partial: Int get() = tracks.count { !it.complete }
}

/** How full the recordings' room is. */
data class StorageUsage(val usedBytes: Long, val freeBytes: Long, val limits: StorageLimits, val verdict: StorageVerdict) {
    /** The bar's fill, 0..1: the share of the limit, else of the room recordings could take. */
    val fraction: Float
        get() {
            val whole = if (limits.maxTotalBytes > 0) limits.maxTotalBytes else if (freeBytes >= 0) usedBytes + freeBytes else 0L
            return if (whole <= 0) 0f else (usedBytes.toFloat() / whole).coerceIn(0f, 1f)
        }

    /** What the bar is a share of, bytes; 0 when unknown. */
    val wholeBytes: Long get() = if (limits.maxTotalBytes > 0) limits.maxTotalBytes else if (freeBytes >= 0) usedBytes + freeBytes else 0L
}

/** A schedule as the list shows it; [lastRun] is the latest run that came of it (none yet: null). */
data class ScheduleRow(
    val schedule: RecordingSchedule,
    val next: RecordingWindow?,
    val running: Boolean,
    val conflicts: Int,
    val lastRun: ScheduleRun? = null,
)

/** What a word to the user says. */
sealed interface RecordingsNotice {
    /** [count] tracks were deleted ([RecordingsController.undo] brings them back). */
    data class Deleted(val ids: List<Long>, val name: String?) : RecordingsNotice {
        val count: Int get() = ids.size
    }

    /** The partial tracks of a recording were deleted. */
    data class PartialDeleted(val ids: List<Long>) : RecordingsNotice {
        val count: Int get() = ids.size
    }

    data class Renamed(val name: String) : RecordingsNotice

    data object RenameFailed : RecordingsNotice

    /** A schedule was deleted; [RecordingsController.undo] saves it again. */
    data class ScheduleDeleted(val schedule: RecordingSchedule) : RecordingsNotice

    /** A recording asked for did not begin, or went into one running. */
    data class Recording(val notice: RecordingNotice) : RecordingsNotice

    /** The recording of a station began. */
    data class Started(val station: String) : RecordingsNotice

    /** The recording is only partial tracks or has none. */
    data object NothingPartial : RecordingsNotice

    /** [count] tracks whose files are gone were taken out of the list. */
    data class Gone(val count: Int) : RecordingsNotice

    data object Failed : RecordingsNotice
}

/** A recording being scheduled, in the editor. [station] is null until one is chosen. */
data class ScheduleDraft(
    val id: Long = 0,
    val station: RecordingStation? = null,
    val hour: Int = 7,
    val minute: Int = 0,
    val durationMinutes: Int = 60,
    val repeat: Repeat = Repeat.Once,
    val weekday: Int = 1,
    val enabled: Boolean = true,
    val createdAt: Long = 0,
) {
    /** The hour the recording ends at. */
    val endHour: Int get() = ((hour * 60 + minute + durationMinutes) / 60) % 24

    val endMinute: Int get() = (hour * 60 + minute + durationMinutes) % 60

    /** The draft ending at [h]:[m] (the next time that is: after midnight when it is earlier), at most [RecordingSchedule.MAX_MINUTES] long. */
    fun endingAt(h: Int, m: Int): ScheduleDraft {
        val gap = ((h * 60 + m) - (hour * 60 + minute) + 24 * 60) % (24 * 60)
        return copy(durationMinutes = gap.coerceIn(1, RecordingSchedule.MAX_MINUTES))
    }

    val ready: Boolean get() = station != null

    /** The schedule this draft makes at [now]: a one-off is for the next time its hour comes. */
    fun toSchedule(now: Long, zone: ZoneId): RecordingSchedule? {
        val on = station ?: return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val at = ZonedDateTime.of(today, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()
        val date: LocalDate = if (repeat == Repeat.Once && at <= now) today.plusDays(1) else today
        val made = RecordingSchedule.of(on, date, hour, minute, durationMinutes, repeat, zone, if (createdAt > 0) createdAt else now, id, enabled)
        return if (repeat == Repeat.Weekly) made.copy(weekday = weekday) else made
    }

    companion object {
        fun of(schedule: RecordingSchedule) = ScheduleDraft(
            id = schedule.id,
            station = schedule.station,
            hour = schedule.hour,
            minute = schedule.minute,
            durationMinutes = schedule.durationMinutes,
            repeat = schedule.repeat,
            weekday = schedule.weekday,
            enabled = schedule.enabled,
            createdAt = schedule.createdAt,
        )
    }
}

/** What the editor says about a draft against the schedules there are. */
data class ScheduleCheck(val conflicts: List<RecordingSchedule>, val tooMany: Boolean)

/**
 * S-1107, S-207: what the Recordings tab does: the recordings going on, the schedules, the tracks
 * grouped by station and recording, the room used; playing, renaming, deleting (which can be taken
 * back) and sharing tracks; scheduling. The screen reads its state; nothing here needs the screen,
 * so the tests drive it.
 */
@Stable
class RecordingsController(
    private val data: RecordingsData,
    private val control: RecordingControl,
    private val settings: () -> RecordingSettings,
    private val environment: RecordingEnvironment,
    private val player: TrackPlayer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val maxParallel: Int = RecordingHub.MAX_PARALLEL,
) {
    /** The recordings going on. */
    var live by mutableStateOf<List<LiveRecording>>(emptyList())
        private set

    /** The tracks kept; null until the database has answered. */
    var tracks by mutableStateOf<List<RecordedTrack>?>(null)
        private set

    var groups by mutableStateOf<List<StationGroup>>(emptyList())
        private set

    var schedules by mutableStateOf<List<ScheduleRow>>(emptyList())
        private set

    /** The ids of the tracks whose files are gone. */
    var missing by mutableStateOf<Set<Long>>(emptySet())
        private set

    var storage by mutableStateOf<StorageUsage?>(null)
        private set

    var folder by mutableStateOf<FolderState>(FolderState.Default)
        private set

    /** Whether alarms may ring at the minute (the schedules need it to start on time). */
    var exactAlarms by mutableStateOf(true)
        private set

    /** The keys of the stations and recordings shown open. */
    var open by mutableStateOf<Set<String>>(emptySet())
        private set

    var notice by mutableStateOf<RecordingsNotice?>(null)
        private set

    /** The recording being scheduled; null when the editor is closed. */
    var editor by mutableStateOf<ScheduleDraft?>(null)
        private set

    /** The track being renamed. */
    var renaming by mutableStateOf<RecordedTrack?>(null)
        private set

    /** How many stations are recorded at once. */
    val maxRecordings: Int get() = maxParallel

    private var used = 0L
    private var free = -1L
    private var scheduleList by mutableStateOf(emptyList<RecordingSchedule>())
    private var runList by mutableStateOf(emptyList<ScheduleRun>())
    private var opened = false
    private var openedOnce = false
    private var clockNow by mutableLongStateOf(clock())

    /** The time the screen tells "next" and "today" by. */
    val now: Long get() = clockNow

    init {
        scope.launch {
            data.tracks().collect { list ->
                tracks = list
                regroup(list)
                scope.launch { checkFiles(list) }
            }
        }
        scope.launch {
            data.schedules().collect { list ->
                scheduleList = list
                rows()
            }
        }
        scope.launch {
            data.runs().collect { list ->
                runList = list
                rows()
            }
        }
        scope.launch {
            data.used().collect {
                used = it
                usage()
            }
        }
        scope.launch {
            control.live.collect {
                live = it
                rows()
                usage()
            }
        }
        scope.launch {
            control.notices.collect { notice = RecordingsNotice.Recording(it) }
        }
    }

    /** Runs [block]; a failure that is not the caller's to handle (the database, a provider) is said, not crashed on. */
    private fun guarded(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = RecordingsNotice.Failed
            }
        }
    }

    // --- the screen's state

    /** The tab was shown: files deleted a while ago go for good, the runs that are on begin, the state is read. */
    fun opened() {
        if (!opened) {
            opened = true
            guarded {
                data.purge(RecordingStore.UNDO_MS)
                environment.catchUp()
            }
        }
        refresh()
    }

    /** Reads the room left, the folder and the alarm permission again; the screen does it now and then. */
    fun refresh() {
        scope.launch {
            val (room, place, exact) = withContext(work) { Triple(environment.freeBytes(), environment.folder(), environment.exactAlarms()) }
            free = room
            folder = place
            exactAlarms = exact
            clockNow = clock()
            rows()
            usage()
        }
    }

    private fun usage() {
        val limits = settings().limits
        storage = StorageUsage(used, free, limits, StoragePolicy.assess(free, used, limits))
    }

    private fun regroup(list: List<RecordedTrack>) {
        groups = list.groupBy { it.station.key }.map { (_, mine) ->
            val sessions = mine.groupBy { it.sessionId }.map { (id, tracks) ->
                val sorted = tracks.sortedBy { it.startedAt }
                SessionGroup(id, sorted.first().station, sorted.first().sessionStartedAt, sorted)
            }.sortedByDescending { it.startedAt }
            StationGroup(sessions.first().station, sessions)
        }.sortedByDescending { it.sessions.first().startedAt }
        if (!openedOnce && groups.isNotEmpty()) {
            openedOnce = true
            val first = groups.first()
            open = open + stationKey(first) + sessionKey(first.sessions.first())
        }
    }

    private suspend fun checkFiles(list: List<RecordedTrack>) {
        missing = withContext(work) { data.missing(list).toSet() }
    }

    private fun rows() {
        val at = clockNow
        val z = zone()
        schedules = scheduleList.map { schedule ->
            ScheduleRow(
                schedule = schedule,
                next = ScheduleMath.next(schedule, at, z),
                running = control.live.value.any { it.request.scheduleId == schedule.id },
                conflicts = ScheduleMath.conflicts(schedule, scheduleList, at, zone = z).size,
                // the runs come latest first
                lastRun = runList.firstOrNull { it.scheduleId == schedule.id },
            )
        }
    }

    // --- opening and closing

    fun toggle(key: String) {
        open = if (key in open) open - key else open + key
    }

    fun noticed() {
        notice = null
    }

    // --- recording

    /** Whether the station [key] is being recorded now. */
    fun recording(key: String): Boolean = live.any { it.request.station.key == key }

    /** Records [station] until stopped. */
    fun record(station: RecordingStation) {
        if (!folder.usable) {
            refresh()
        }
        control.start(station)
        notice = RecordingsNotice.Started(station.name)
    }

    fun stop(sessionId: String) = control.stop(sessionId)

    fun stopAll() = control.stopAll()

    // --- tracks

    /** Plays [session]'s tracks from [track] (all of them in order), in the normal player. */
    fun play(session: SessionGroup, track: RecordedTrack) {
        val playable = session.tracks.filter { it.id !in missing }
        val index = playable.indexOfFirst { it.id == track.id }
        if (index >= 0) player.play(playable, index)
    }

    /** Plays the whole recording from its first track. */
    fun playAll(session: SessionGroup) {
        val playable = session.tracks.filter { it.id !in missing }
        if (playable.isNotEmpty()) player.play(playable, 0)
    }

    fun askRename(track: RecordedTrack?) {
        renaming = track
    }

    /** The file of [track] is called [base] (the extension stays). */
    fun rename(track: RecordedTrack, base: String) {
        val extension = track.fileName.substringAfterLast('.', "")
        val clean = TrackNames.fit(TrackNames.sanitize(base), TrackNames.MAX_BYTES - extension.length - 1)
        if (clean.isEmpty()) {
            notice = RecordingsNotice.RenameFailed
            return
        }
        val name = if (extension.isEmpty()) clean else "$clean.$extension"
        if (name == track.fileName) return
        guarded {
            val renamed = data.rename(track.id, name)
            notice = if (renamed == null) RecordingsNotice.RenameFailed else RecordingsNotice.Renamed(renamed.fileName)
        }
    }

    /** Deletes [tracks]; [undo] brings them back until the time to undo is over. */
    fun delete(tracks: List<RecordedTrack>) {
        if (tracks.isEmpty()) return
        val ids = tracks.map { it.id }
        guarded {
            data.trash(ids)
            notice = RecordingsNotice.Deleted(ids, tracks.singleOrNull()?.displayTitle)
            purgeLater()
        }
    }

    /** Keeps only the complete tracks of [session]: the partial ones are deleted. */
    fun keepComplete(session: SessionGroup) {
        val partial = session.tracks.filter { !it.complete }
        if (partial.isEmpty()) {
            notice = RecordingsNotice.NothingPartial
            return
        }
        val ids = partial.map { it.id }
        guarded {
            data.trash(ids)
            notice = RecordingsNotice.PartialDeleted(ids)
            purgeLater()
        }
    }

    /** Takes the tracks whose files are gone out of the list. */
    fun forgetMissing() {
        val ids = missing.toList()
        if (ids.isEmpty()) return
        guarded {
            data.forget(ids)
            notice = RecordingsNotice.Gone(ids.size)
        }
    }

    private fun purgeLater() {
        scope.launch {
            delay(RecordingStore.UNDO_MS)
            try {
                data.purge(RecordingStore.UNDO_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the next time the tab is shown tries again
            }
        }
    }

    /** Takes back what [notice] says was done. */
    fun undo(notice: RecordingsNotice) {
        when (notice) {
            is RecordingsNotice.Deleted -> guarded { data.restore(notice.ids) }
            is RecordingsNotice.PartialDeleted -> guarded { data.restore(notice.ids) }
            is RecordingsNotice.ScheduleDeleted -> guarded {
                val saved = data.save(notice.schedule.copy(id = 0))
                environment.scheduleSaved(saved)
            }
            else -> Unit
        }
    }

    // --- schedules

    /** The editor opens on a new schedule for [station] (none chosen yet: null). */
    fun schedule(station: RecordingStation? = null) {
        val at = Instant.ofEpochMilli(clock()).atZone(zone())
        // the next quarter of an hour after the next full one is a plain time to offer
        val start = at.plusHours(1).withMinute(0)
        editor = ScheduleDraft(station = station, hour = start.hour, minute = 0)
    }

    fun edit(schedule: RecordingSchedule) {
        editor = ScheduleDraft.of(schedule)
    }

    fun change(draft: ScheduleDraft) {
        editor = draft
    }

    fun closeEditor() {
        editor = null
    }

    /** What [draft] meets among the schedules there are. */
    fun check(draft: ScheduleDraft): ScheduleCheck {
        val made = draft.toSchedule(clock(), zone()) ?: return ScheduleCheck(emptyList(), false)
        val at = clock()
        val others = scheduleList.filter { it.id != made.id || made.id == 0L }
        val z = zone()
        val conflicts = ScheduleMath.conflicts(made, others, at, zone = z)
        val peak = ScheduleMath.peak(others + made, at, zone = z)
        return ScheduleCheck(conflicts, peak > maxParallel)
    }

    /** Keeps the schedule being edited and sets its alarm. */
    fun saveSchedule() {
        val draft = editor ?: return
        val made = draft.toSchedule(clock(), zone()) ?: return
        editor = null
        guarded {
            val saved = data.save(made)
            environment.scheduleSaved(saved)
        }
    }

    fun deleteSchedule(schedule: RecordingSchedule) {
        guarded {
            data.deleteSchedule(schedule.id)
            environment.scheduleRemoved(schedule.id)
            notice = RecordingsNotice.ScheduleDeleted(schedule)
        }
    }

    fun enable(schedule: RecordingSchedule, on: Boolean) {
        guarded {
            data.enable(schedule.id, on)
            environment.scheduleSaved(schedule.copy(enabled = on))
        }
    }

    companion object {
        fun stationKey(group: StationGroup) = "station:${group.key}"

        fun sessionKey(session: SessionGroup) = "session:${session.sessionId}"
    }
}
