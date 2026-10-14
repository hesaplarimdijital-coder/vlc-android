package app.nagmedar.ui.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

/** The stations kept, in memory: what the database does with the directory's stations. */
class FakeLibrary : RadioLibrary {
    private val rows = LinkedHashMap<String, Station>()
    private var next = 1L

    val kept: List<Station> get() = rows.values.toList()

    private fun put(station: Station): Station {
        val known = rows[station.key]
        val row = station.copy(
            id = known?.id ?: next++,
            favourite = known?.favourite ?: station.favourite,
            lastVotedAt = known?.lastVotedAt ?: station.lastVotedAt,
            lastPlayedAt = known?.lastPlayedAt ?: station.lastPlayedAt,
        )
        rows[station.key] = row
        return row
    }

    override suspend fun keep(station: Station): Station = put(station)

    override suspend fun played(station: Station, at: Long): Station = put(station).copy(lastPlayedAt = at).also { rows[it.key] = it }

    override suspend fun voted(station: Station, at: Long, votes: Int): Station =
        put(station).copy(lastVotedAt = at, votes = votes).also { rows[it.key] = it }

    override suspend fun marks(uuids: Collection<String>): Map<String, Station> = rows.values.filter { it.uuid in uuids }.associateBy { it.uuid!! }
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
