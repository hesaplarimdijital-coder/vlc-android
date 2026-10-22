package app.nagmedar.ui.radio.record

import app.nagmedar.data.RadioRecording
import app.nagmedar.data.RadioRecordingDao
import app.nagmedar.data.RadioScheduleEntry
import app.nagmedar.data.RadioScheduleRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * S-1107: the tracks recorded and the recordings scheduled, kept in the database, with the files
 * the tracks are. A deletion has two steps so that it can be taken back: [trash] hides the tracks,
 * [purge] deletes their files and rows once the time to undo has passed.
 */
class RecordingStore(
    private val dao: RadioRecordingDao,
    private val files: TrackFiles,
    private val clock: () -> Long = System::currentTimeMillis,
) : SessionLibrary, RecordingsData, ScheduleRunLog {

    // --- the session's library

    override suspend fun add(track: RecordedTrack): Long = withContext(Dispatchers.IO) { dao.add(track.toRow()) }

    override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { dao.usedBytes() }

    override suspend fun prune(bytes: Long, keep: Set<String>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (row in dao.oldestFirst()) {
            if (freed >= bytes) break
            if (row.sessionId in keep) continue
            // a file that cannot be deleted stays listed: it still takes its room
            if (!files.delete(row.fileUri)) continue
            dao.delete(listOf(row.id))
            freed += row.sizeBytes
        }
        freed
    }

    // --- the screen's

    /** The tracks kept, newest session first; follows every change. */
    override fun tracks(): Flow<List<RecordedTrack>> = dao.tracks().map { rows -> rows.map { it.toTrack() } }

    /** What the tracks take, bytes; follows every change. */
    override fun used(): Flow<Long> = dao.used()

    /** The tracks of [ids], hidden for now; [restore] shows them again. */
    override suspend fun trash(ids: List<Long>) = withContext(Dispatchers.IO) { dao.trash(ids, clock()) }

    override suspend fun restore(ids: List<Long>) = withContext(Dispatchers.IO) { dao.trash(ids, 0) }

    /**
     * Deletes the files of the tracks in the bin that have been there for [graceMs] (all of them
     * with 0) and their rows; returns how many were deleted. A file that cannot be deleted
     * stays in the bin.
     */
    override suspend fun purge(graceMs: Long): Int = withContext(Dispatchers.IO) {
        val before = clock() - graceMs
        var deleted = 0
        for (row in dao.trashed()) {
            if (row.trashedAt > before) continue
            if (files.delete(row.fileUri)) {
                dao.delete(listOf(row.id))
                deleted++
            }
        }
        deleted
    }

    /** Whether the files of the tracks listed still exist; the ids of those that do not. */
    override suspend fun missing(tracks: List<RecordedTrack>): List<Long> = withContext(Dispatchers.IO) {
        tracks.filter { !files.exists(it.fileUri) }.map { it.id }
    }

    /** Takes the rows of tracks whose files are gone out of the list (the user deleted them elsewhere). */
    override suspend fun forget(ids: List<Long>) = withContext(Dispatchers.IO) { dao.delete(ids) }

    /** The track [id] named [name] (with its extension); the track as it is now, or null when the storage refused. */
    override suspend fun rename(id: Long, name: String): RecordedTrack? = withContext(Dispatchers.IO) {
        val row = dao.track(id) ?: return@withContext null
        val renamed = files.rename(row.fileUri, name) ?: return@withContext null
        dao.rename(id, renamed.uri, renamed.name)
        dao.track(id)?.toTrack()
    }

    // --- schedules

    override fun schedules(): Flow<List<RecordingSchedule>> = dao.schedules().map { rows -> rows.map { it.toSchedule() } }

    suspend fun scheduleList(): List<RecordingSchedule> = withContext(Dispatchers.IO) { dao.scheduleList().map { it.toSchedule() } }

    suspend fun schedule(id: Long): RecordingSchedule? = withContext(Dispatchers.IO) { dao.schedule(id)?.toSchedule() }

    /**
     * Saves [schedule] (a new one when its id is 0); returns it with its id. Its runs are looked over
     * from now on: what passed before it was saved is nobody's to have missed.
     */
    override suspend fun save(schedule: RecordingSchedule): RecordingSchedule = withContext(Dispatchers.IO) {
        val now = clock()
        // an edit keeps the start last made: the run on now is not begun a second time
        val started = if (schedule.id != 0L) maxOf(schedule.lastStartedAt, dao.schedule(schedule.id)?.lastStartedAt ?: 0L) else schedule.lastStartedAt
        val stamped = schedule.copy(
            createdAt = if (schedule.createdAt == 0L) now else schedule.createdAt,
            checkedAt = maxOf(schedule.checkedAt, now),
            lastStartedAt = started,
        )
        stamped.copy(id = dao.keepSchedule(stamped.toRow()))
    }

    /** Turns a schedule on or off; one turned on is looked over from now (its time off is not a run missed). */
    override suspend fun enable(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.enableSchedule(id, enabled)
        if (enabled) dao.markScheduleChecked(id, clock())
    }

    suspend fun markStarted(id: Long, at: Long) = withContext(Dispatchers.IO) { dao.markScheduleStarted(id, at) }

    suspend fun markChecked(id: Long, at: Long) = withContext(Dispatchers.IO) { dao.markScheduleChecked(id, at) }

    override suspend fun deleteSchedule(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteSchedule(id)
        dao.deleteRuns(id)
    }

    // --- what came of the runs

    /** The runs kept, the latest window first; follows every change. */
    override fun runs(): Flow<List<ScheduleRun>> = dao.runs().map { rows -> rows.map { it.toRun() } }

    /** Runs a restart cut off whose window is not over at [now]. */
    suspend fun resumable(now: Long): List<ScheduleRun> = withContext(Dispatchers.IO) { dao.resumable(now).map { it.toRun() } }

    /** Nothing is recording now, so a run that is Running was cut off: it is Interrupted. */
    suspend fun interruptRunning() = withContext(Dispatchers.IO) { dao.interruptRunning(clock()) }

    /** The window of [scheduleId] from [windowStart] passed with nothing recorded. */
    suspend fun missed(scheduleId: Long, windowStart: Long, windowEnd: Long) =
        withContext(Dispatchers.IO) { dao.keepMissed(scheduleId, windowStart, windowEnd, clock()) }

    override suspend fun began(scheduleId: Long, windowStart: Long, windowEnd: Long, sessionId: String) =
        withContext(Dispatchers.IO) { dao.keepRun(scheduleId, windowStart, windowEnd, RunStatus.Running.name, sessionId, clock()) }

    override suspend fun failed(scheduleId: Long, windowStart: Long, windowEnd: Long) =
        withContext(Dispatchers.IO) { dao.keepRun(scheduleId, windowStart, windowEnd, RunStatus.Failed.name, null, clock()) }

    override suspend fun ended(sessionId: String, status: RunStatus) = withContext(Dispatchers.IO) { dao.endRunsOf(sessionId, status.name, clock()) }

    companion object {
        /** How long a deletion can be taken back (the undo bar's time, and some more). */
        const val UNDO_MS = 15_000L
    }
}

private fun RecordedTrack.toRow() = RadioRecording(
    id = id,
    sessionId = sessionId,
    stationKey = station.key,
    stationName = station.name,
    streamUrl = station.streamUrl,
    stationUuid = station.uuid,
    logoUrl = station.logoUrl,
    fileUri = fileUri,
    fileName = fileName,
    artist = artist,
    title = title,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    complete = complete,
    format = format,
    startedAt = startedAt,
    sessionStartedAt = sessionStartedAt,
    trashedAt = trashedAt,
)

private fun RadioRecording.toTrack() = RecordedTrack(
    id = id,
    sessionId = sessionId,
    station = RecordingStation(stationName, streamUrl, stationUuid, logoUrl),
    sessionStartedAt = sessionStartedAt,
    startedAt = startedAt,
    fileUri = fileUri,
    fileName = fileName,
    artist = artist,
    title = title,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    complete = complete,
    format = format,
    trashedAt = trashedAt,
)

private fun RecordingSchedule.toRow() = RadioScheduleEntry(
    id = id,
    stationKey = station.key,
    stationName = station.name,
    streamUrl = station.streamUrl,
    stationUuid = station.uuid,
    logoUrl = station.logoUrl,
    startAt = startAt,
    hour = hour,
    minute = minute,
    durationMinutes = durationMinutes,
    repeat = repeat.name,
    weekday = weekday,
    enabled = enabled,
    lastStartedAt = lastStartedAt,
    createdAt = createdAt,
    checkedAt = checkedAt,
)

private fun RadioScheduleRun.toRun() = ScheduleRun(
    scheduleId = scheduleId,
    windowStart = windowStart,
    windowEnd = windowEnd,
    status = RunStatus.of(status),
    interruptions = interruptions,
    sessionId = sessionId,
)

private fun RadioScheduleEntry.toSchedule() = RecordingSchedule(
    id = id,
    station = RecordingStation(stationName, streamUrl, stationUuid, logoUrl),
    startAt = startAt,
    hour = hour,
    minute = minute,
    durationMinutes = durationMinutes,
    repeat = Repeat.entries.firstOrNull { it.name == repeat } ?: Repeat.Once,
    weekday = weekday.coerceIn(1, 7),
    enabled = enabled,
    lastStartedAt = lastStartedAt,
    createdAt = createdAt,
    checkedAt = checkedAt,
)
