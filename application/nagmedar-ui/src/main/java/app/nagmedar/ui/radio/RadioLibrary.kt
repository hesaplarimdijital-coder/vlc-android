package app.nagmedar.ui.radio

import android.content.Context
import android.database.sqlite.SQLiteException
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    /** The favourite stations by name; follows every change. */
    fun favourites(): Flow<List<Station>>

    /** The stations played, the latest first, at most [limit]; follows every change. */
    fun recent(limit: Int = RECENT_LIMIT): Flow<List<Station>>

    /** The stations imported from playlist files, by name; follows every change. */
    fun imported(): Flow<List<Station>>

    /** [station] is a favourite, or no longer is; returns it as kept. */
    suspend fun favourite(station: Station, on: Boolean): Station

    /** [station] leaves "recent": it is no longer marked as played, and kept only if something else keeps it. */
    suspend fun forget(station: Station)

    /** The imported [station] is taken out. */
    suspend fun remove(station: Station)

    /** [station] is put back as it was (what "undo" does after [forget] or [remove]). */
    suspend fun restore(station: Station)

    /** Keeps the stations a playlist file names as the user's own; one already kept is updated and counted as present, not as added. */
    suspend fun import(stations: List<PlaylistStation>): ImportCounts

    companion object {
        /** How many played stations "recent" lists. */
        const val RECENT_LIMIT = 50
    }
}

/** What an import did: stations [added] to the kept ones, and stations that were already [present]. */
data class ImportCounts(val added: Int, val present: Int)

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

    override fun favourites(): Flow<List<Station>> = rows(dao.favorites())

    override fun recent(limit: Int): Flow<List<Station>> = rows(dao.recent(limit))

    override fun imported(): Flow<List<Station>> = rows(dao.imported())

    override suspend fun favourite(station: Station, on: Boolean): Station = withContext(Dispatchers.IO) {
        val id = dao.keep(station.toEntity())
        dao.setFavorite(id, on)
        if (!on) dao.pruneUnused()
        dao.byId(id)?.toStation() ?: station.copy(id = 0, favourite = on)
    }

    override suspend fun forget(station: Station) = withContext(Dispatchers.IO) {
        if (station.id != 0L) {
            dao.forgetPlayed(station.id)
            dao.pruneUnused()
        }
    }

    override suspend fun remove(station: Station) = withContext(Dispatchers.IO) {
        dao.byId(station.id)?.let(dao::delete) ?: Unit
    }

    override suspend fun restore(station: Station) = withContext(Dispatchers.IO) {
        val id = dao.keep(station.toEntity().copy(id = 0))
        if (station.favourite) dao.setFavorite(id, true)
        if (station.lastPlayedAt > 0) dao.markPlayed(id, station.lastPlayedAt)
    }

    override suspend fun import(stations: List<PlaylistStation>): ImportCounts = withContext(Dispatchers.IO) {
        val radio = dao
        var added = 0
        var present = 0
        NagmedarDatabase.get(appContext).runInTransaction(
            Runnable {
                // a transaction that is retried counts again from nothing
                added = 0
                present = 0
                stations.forEach { station ->
                    val row = station.toEntity()
                    if (radio.importedByUrl(row.streamUrl) == null) added++ else present++
                    radio.keep(row)
                }
            },
        )
        ImportCounts(added, present)
    }

    /** The table's rows as stations; a database that cannot be read lists none. */
    private fun rows(source: Flow<List<RadioStation>>): Flow<List<Station>> =
        source.map { list -> list.map { it.toStation() } }.catch { emit(emptyList()) }.flowOn(Dispatchers.IO)

    private companion object {
        /** SQLite takes a limited number of variables in a query. */
        const val CHUNK = 500
    }
}

/** An imported station as a database row: no directory id, the group as its one tag. */
internal fun PlaylistStation.toEntity(): RadioStation = RadioStation(
    name = name,
    streamUrl = url,
    faviconUrl = logoUrl,
    tags = group,
)

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
