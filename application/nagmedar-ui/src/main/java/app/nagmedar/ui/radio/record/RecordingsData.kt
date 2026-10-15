package app.nagmedar.ui.radio.record

import kotlinx.coroutines.flow.Flow

/** What the Recordings tab keeps and reads: the real database in the app, fakes in the tests. */
interface RecordingsData {
    /** The tracks kept (not those in the bin), newest session first; follows every change. */
    fun tracks(): Flow<List<RecordedTrack>>

    /** The recordings scheduled; follows every change. */
    fun schedules(): Flow<List<RecordingSchedule>>

    /** What the tracks take, bytes. */
    fun used(): Flow<Long>

    suspend fun trash(ids: List<Long>)

    suspend fun restore(ids: List<Long>)

    /** Deletes the files of what has been in the bin for [graceMs]; how many. */
    suspend fun purge(graceMs: Long): Int

    /** The ids of [tracks] whose files are gone. */
    suspend fun missing(tracks: List<RecordedTrack>): List<Long>

    /** Takes the rows of [ids] out of the list (their files are gone). */
    suspend fun forget(ids: List<Long>)

    /** The track [id] under the file name [name]; the track as it is, or null when the storage refused. */
    suspend fun rename(id: Long, name: String): RecordedTrack?

    suspend fun save(schedule: RecordingSchedule): RecordingSchedule

    suspend fun enable(id: Long, enabled: Boolean)

    suspend fun deleteSchedule(id: Long)
}
