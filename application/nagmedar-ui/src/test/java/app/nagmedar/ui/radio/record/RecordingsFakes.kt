package app.nagmedar.ui.radio.record

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** S-1107: the database of the Recordings tab, in memory. [gone] are the addresses of files that do not exist. */
class FakeRecordingsData(
    tracks: List<RecordedTrack> = emptyList(),
    schedules: List<RecordingSchedule> = emptyList(),
    var gone: Set<String> = emptySet(),
) : RecordingsData {
    val all = MutableStateFlow(tracks)
    val plans = MutableStateFlow(schedules)
    var renameFails = false
    var failNextTrash = false
    val purged = ArrayList<Long>()
    private var nextId = 1000L

    override fun tracks(): Flow<List<RecordedTrack>> = all.map { list -> list.filter { it.trashedAt == 0L } }

    override fun schedules(): Flow<List<RecordingSchedule>> = plans

    override fun used(): Flow<Long> = all.map { list -> list.filter { it.trashedAt == 0L }.sumOf { it.sizeBytes } }

    override suspend fun trash(ids: List<Long>) {
        if (failNextTrash) {
            failNextTrash = false
            throw IllegalStateException("the database is shut")
        }
        all.value = all.value.map { if (it.id in ids) it.copy(trashedAt = 1) else it }
    }

    override suspend fun restore(ids: List<Long>) {
        all.value = all.value.map { if (it.id in ids) it.copy(trashedAt = 0) else it }
    }

    override suspend fun purge(graceMs: Long): Int {
        val bin = all.value.filter { it.trashedAt != 0L }
        purged += bin.map { it.id }
        all.value = all.value - bin.toSet()
        return bin.size
    }

    override suspend fun missing(tracks: List<RecordedTrack>): List<Long> = tracks.filter { it.fileUri in gone }.map { it.id }

    override suspend fun forget(ids: List<Long>) {
        all.value = all.value.filter { it.id !in ids }
    }

    override suspend fun rename(id: Long, name: String): RecordedTrack? {
        if (renameFails) return null
        val track = all.value.firstOrNull { it.id == id } ?: return null
        val renamed = track.copy(fileName = name)
        all.value = all.value.map { if (it.id == id) renamed else it }
        return renamed
    }

    override suspend fun save(schedule: RecordingSchedule): RecordingSchedule {
        val saved = if (schedule.id == 0L) schedule.copy(id = nextId++) else schedule
        plans.value = plans.value.filter { it.id != saved.id } + saved
        return saved
    }

    override suspend fun enable(id: Long, enabled: Boolean) {
        plans.value = plans.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun deleteSchedule(id: Long) {
        plans.value = plans.value.filter { it.id != id }
    }
}

/** The recordings going on, driven by the test. */
class FakeControl : RecordingControl {
    override val live = MutableStateFlow<List<LiveRecording>>(emptyList())
    override val notices = MutableSharedFlow<RecordingNotice>(extraBufferCapacity = 8)
    val started = ArrayList<RecordingStation>()
    val stopped = ArrayList<String>()
    var stoppedAll = 0

    override fun recording(station: RecordingStation): Boolean = live.value.any { it.request.station.key == station.key }

    override fun start(station: RecordingStation, endAt: Long?, scheduleId: Long?) {
        started += station
    }

    override fun stop(sessionId: String) {
        stopped += sessionId
    }

    override fun stopAll() {
        stoppedAll++
    }
}

class FakeEnvironment : RecordingEnvironment {
    var free = 40L * StorageLimits.GB
    var place: FolderState = FolderState.Default
    var exact = true
    val saved = ArrayList<RecordingSchedule>()
    val removed = ArrayList<Long>()
    var caughtUp = 0

    override fun freeBytes() = free

    override fun folder() = place

    override fun exactAlarms() = exact

    override fun scheduleSaved(schedule: RecordingSchedule) {
        saved += schedule
    }

    override fun scheduleRemoved(id: Long) {
        removed += id
    }

    override suspend fun catchUp() {
        caughtUp++
    }
}

class FakeTrackPlayer : TrackPlayer {
    val plays = ArrayList<Pair<List<RecordedTrack>, Int>>()

    override fun play(tracks: List<RecordedTrack>, index: Int) {
        plays += tracks to index
    }
}

/** Stations and tracks the Recordings tests and screenshots share. */
object RecordingSamples {
    val sabah = RecordingStation("Sabah Seansı", "https://yayin.example/sabah", uuid = "e5-sabah")
    val caz = RecordingStation("Gece Cazı", "https://yayin.example/caz.mp3", uuid = "e5-caz")
    val haber = RecordingStation("Haber Hattı", "https://yayin.example/haber")

    fun track(
        id: Long,
        station: RecordingStation,
        session: String,
        sessionAt: Long,
        offsetMin: Int,
        artist: String?,
        title: String?,
        minutes: Int = 4,
        complete: Boolean = true,
        format: String = "mp3",
        sizeMb: Int = minutes * 2,
        name: String? = null,
    ) = RecordedTrack(
        id = id,
        sessionId = session,
        station = station,
        sessionStartedAt = sessionAt,
        startedAt = sessionAt + offsetMin * 60_000L,
        fileUri = "file:///music/Radio/${station.name}/$id.$format",
        fileName = (name ?: if (artist != null) "$artist - $title" else title.orEmpty()) + ".$format",
        artist = artist,
        title = title,
        durationMs = minutes * 60_000L,
        sizeBytes = sizeMb * 1024L * 1024L,
        complete = complete,
        format = format,
    )
}
