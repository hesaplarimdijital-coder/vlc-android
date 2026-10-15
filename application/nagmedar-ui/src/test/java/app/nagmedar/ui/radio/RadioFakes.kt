package app.nagmedar.ui.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList

/** S-1105: stations for the tests and the screenshots, as the directory lists them. */
object RadioSamples {
    val jazz = Station(
        uuid = "a1-caz", name = "Gece Cazı", streamUrl = "https://yayin.example/caz.mp3", logoUrl = "https://logo.example/caz.png",
        countryCode = "TR", country = "Turkey", language = "turkish", tags = listOf("jazz", "gece", "enstrümantal"),
        codec = "MP3", bitrate = 128, votes = 18_402,
    )
    val news = Station(
        uuid = "b2-haber", name = "Haber Hattı", streamUrl = "https://yayin.example/haber.aac",
        countryCode = "TR", country = "Turkey", language = "turkish", tags = listOf("haber", "konuşma"),
        codec = "AAC", bitrate = 64, votes = 2_310,
    )
    val classical = Station(
        uuid = "c3-klasik", name = "Klasik Sabah", streamUrl = "https://yayin.example/klasik.ogg", logoUrl = "https://logo.example/klasik.png",
        countryCode = "DE", country = "Germany", language = "german", tags = listOf("classical", "baroque"),
        codec = "OGG", bitrate = 256, votes = 970,
    )
    val oneVote = Station(
        uuid = "d4-yeni", name = "Yeni Dalga", streamUrl = "https://yayin.example/yeni.mp3",
        countryCode = "FR", country = "France", language = "french", tags = emptyList(), codec = "MP3", bitrate = 0, votes = 1,
    )
    val all = listOf(jazz, news, classical, oneVote)
}

/** The directory without a network: answers set by the test, calls noted. */
class FakeDirectory : RadioDirectory {
    val searches = CopyOnWriteArrayList<StationQuery>()
    val clicks = CopyOnWriteArrayList<String>()
    val votes = CopyOnWriteArrayList<String>()

    var onSearch: suspend (StationQuery) -> List<Station> = { emptyList() }
    var onClick: suspend (String) -> StationClick = { StationClick(null, null) }
    var onVote: suspend (String) -> VoteResult = { VoteResult(true, "voted for station successfully") }
    var countriesList: List<DirectoryCountry> = emptyList()
    var languagesList: List<DirectoryLanguage> = emptyList()
    var tagsList: List<DirectoryTag> = emptyList()
    var optionsFailure: RadioFailure? = null

    override suspend fun search(query: StationQuery): List<Station> {
        searches += query
        return onSearch(query)
    }

    override suspend fun countries(): List<DirectoryCountry> = optionsFailure?.let { throw it } ?: countriesList

    override suspend fun languages(): List<DirectoryLanguage> = optionsFailure?.let { throw it } ?: languagesList

    override suspend fun tags(): List<DirectoryTag> = optionsFailure?.let { throw it } ?: tagsList

    override suspend fun click(uuid: String): StationClick {
        clicks += uuid
        return onClick(uuid)
    }

    override suspend fun vote(uuid: String): VoteResult {
        votes += uuid
        return onVote(uuid)
    }
}

/** The stations kept, in memory: what the database does with the directory's stations and the user's own. */
class FakeLibrary : RadioLibrary {
    private val rows = LinkedHashMap<String, Station>()
    private val changes = MutableStateFlow(0)
    private var next = 1L

    val kept: List<Station> get() = rows.values.toList()

    /** When set, every call that reads or writes the database throws it (a full disk, a corrupt file). */
    @Volatile
    var failWith: RuntimeException? = null

    private fun check() {
        failWith?.let { throw it }
    }

    private fun changed() {
        changes.value++
    }

    private fun put(station: Station): Station {
        val known = rows[station.key]
        val row = station.copy(
            id = known?.id ?: next++,
            favourite = known?.favourite ?: station.favourite,
            lastVotedAt = known?.lastVotedAt ?: station.lastVotedAt,
            lastPlayedAt = known?.lastPlayedAt ?: station.lastPlayedAt,
        )
        rows[station.key] = row
        changed()
        return row
    }

    private fun set(station: Station): Station {
        rows[station.key] = station
        changed()
        return station
    }

    private fun prune() {
        rows.values.removeAll { !it.isUser && !it.favourite && it.lastPlayedAt == 0L && it.lastVotedAt == 0L }
        changed()
    }

    override suspend fun keep(station: Station): Station {
        check()
        return put(station)
    }

    override suspend fun played(station: Station, at: Long): Station {
        check()
        return set(put(station).copy(lastPlayedAt = at))
    }

    override suspend fun voted(station: Station, at: Long, votes: Int): Station {
        check()
        return set(put(station).copy(lastVotedAt = at, votes = votes))
    }

    override suspend fun marks(uuids: Collection<String>): Map<String, Station> {
        check()
        return rows.values.filter { it.uuid in uuids }.associateBy { it.uuid!! }
    }

    override fun favourites(): Flow<List<Station>> = changes.map { rows.values.filter { it.favourite }.sortedBy { it.name.lowercase() } }

    override fun recent(limit: Int): Flow<List<Station>> =
        changes.map { rows.values.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.take(limit) }

    override fun imported(): Flow<List<Station>> = changes.map { rows.values.filter { it.isUser }.sortedBy { it.name.lowercase() } }

    override suspend fun favourite(station: Station, on: Boolean): Station {
        check()
        val row = set(put(station).copy(favourite = on))
        if (!on) prune()
        return rows[row.key] ?: row.copy(id = 0)
    }

    override suspend fun forget(station: Station) {
        rows[station.key]?.let { set(it.copy(lastPlayedAt = 0)) }
        prune()
    }

    override suspend fun remove(station: Station) {
        rows.remove(station.key)
        changed()
    }

    override suspend fun restore(station: Station) {
        set(station.copy(id = next++))
    }

    override suspend fun import(stations: List<PlaylistStation>): ImportCounts {
        check()
        var added = 0
        var present = 0
        stations.forEach {
            val station = Station(uuid = null, name = it.name, streamUrl = it.url, logoUrl = it.logoUrl, tags = listOfNotNull(it.group))
            if (station.key in rows) present++ else added++
            put(station)
        }
        return ImportCounts(added, present)
    }
}

/** Where a station plays in the tests: noted. */
class RecordingSink : StationSink {
    val played = mutableListOf<StationStream>()

    override fun play(stream: StationStream) {
        played += stream
    }
}

/** Where the player's clicks run in the tests: on the spot, so what a start does has been done when it returns. */
fun inlineClicks() = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
