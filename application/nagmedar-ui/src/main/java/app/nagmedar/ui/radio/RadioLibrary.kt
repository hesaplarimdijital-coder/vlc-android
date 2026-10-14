package app.nagmedar.ui.radio

import android.content.Context
import android.database.sqlite.SQLiteException
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S-1105: the stations the app keeps (the rows of the radio table) and what the user did with
 * them: a play, a vote. The Room database in the app, a fake in the tests.
 */
interface RadioLibrary {
    /** Keeps [station] (updating the directory's data of a known one, not the user's marks) and returns it as kept. */
    suspend fun keep(station: Station): Station

    /** [station] started playing at [at]: it is kept and comes first under "recent". */
    suspend fun played(station: Station, at: Long): Station

    /** The user voted for [station] at [at]; the directory now counts [votes]. */
    suspend fun voted(station: Station, at: Long, votes: Int): Station

    /** The kept stations among these directory ids, by directory id: their marks (favourite, plays, vote). */
    suspend fun marks(uuids: Collection<String>): Map<String, Station>
}

/** [RadioLibrary] in the Nağmedar database. */
class DatabaseRadioLibrary(context: Context) : RadioLibrary {
    private val appContext = context.applicationContext
    private val dao get() = NagmedarDatabase.get(appContext).radio()

    override suspend fun keep(station: Station): Station = withContext(Dispatchers.IO) {
        val id = dao.keep(station.toEntity())
        dao.byId(id)?.toStation() ?: station
    }

    override suspend fun played(station: Station, at: Long): Station = withContext(Dispatchers.IO) {
        val id = dao.keep(station.toEntity())
        dao.markPlayed(id, at)
        dao.byId(id)?.toStation() ?: station
    }

    override suspend fun voted(station: Station, at: Long, votes: Int): Station = withContext(Dispatchers.IO) {
        val id = dao.keep(station.toEntity())
        dao.markVoted(id, at, votes)
        dao.byId(id)?.toStation() ?: station
    }

    override suspend fun marks(uuids: Collection<String>): Map<String, Station> = withContext(Dispatchers.IO) {
        if (uuids.isEmpty()) return@withContext emptyMap()
        try {
            uuids.chunked(CHUNK).flatMap { dao.byUuids(it) }.mapNotNull { row -> row.toStation().takeIf { it.uuid != null } }.associateBy { it.uuid!! }
        } catch (e: SQLiteException) {
            // the marks are an extra: a list without them still plays
            emptyMap()
        }
    }

    private companion object {
        /** SQLite takes a limited number of variables in a query. */
        const val CHUNK = 500
    }
}

/** The station as a database row: tags in one comma-separated text. */
internal fun Station.toEntity(): RadioStation = RadioStation(
    id = id,
    stationUuid = uuid,
    name = name,
    streamUrl = streamUrl,
    homepage = homepage,
    faviconUrl = logoUrl,
    countryCode = countryCode,
    tags = tags.joinToString(",").ifEmpty { null },
    codec = codec,
    bitrate = bitrate,
    favorite = favourite,
    lastPlayedAt = lastPlayedAt,
    country = country,
    language = language,
    votes = votes,
    lastVotedAt = lastVotedAt,
)

internal fun RadioStation.toStation(): Station = Station(
    uuid = stationUuid,
    name = name,
    streamUrl = streamUrl,
    id = id,
    logoUrl = faviconUrl,
    homepage = homepage,
    countryCode = countryCode,
    country = country,
    language = language,
    tags = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    codec = codec,
    bitrate = bitrate,
    votes = votes,
    favourite = favorite,
    lastVotedAt = lastVotedAt,
    lastPlayedAt = lastPlayedAt,
)
