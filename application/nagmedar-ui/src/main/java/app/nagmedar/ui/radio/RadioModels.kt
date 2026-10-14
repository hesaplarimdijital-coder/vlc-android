package app.nagmedar.ui.radio

import androidx.compose.runtime.Immutable

/**
 * S-1105: an internet radio station as the screens list it: from the Radio Browser directory
 * ([uuid] set) or imported from a playlist file (S-1106, no [uuid]). [id] is the database row, 0
 * while the station is not kept.
 */
@Immutable
data class Station(
    val uuid: String?,
    val name: String,
    val streamUrl: String,
    val id: Long = 0,
    val logoUrl: String? = null,
    val homepage: String? = null,
    val countryCode: String? = null,
    val country: String? = null,
    val language: String? = null,
    val tags: List<String> = emptyList(),
    val codec: String? = null,
    val bitrate: Int = 0,
    val votes: Int = 0,
    val favourite: Boolean = false,
    val lastVotedAt: Long = 0,
    val lastPlayedAt: Long = 0,
) {
    /** A key that tells the station apart in lists. */
    val key: String get() = uuid ?: "url:$streamUrl"

    /** Whether the station is the user's own (imported), not the directory's. */
    val isUser: Boolean get() = uuid == null
}

/** How the directory orders a search. */
enum class StationOrder(val param: String, val reverse: Boolean) {
    Votes("votes", true),
    Popularity("clickcount", true),
    Name("name", false),
    Bitrate("bitrate", true),
}

/** What narrows a search; null and 0 narrow nothing. */
@Immutable
data class StationFilters(
    /** A country's ISO 3166-1 code. */
    val countryCode: String? = null,
    /** A language's name as the directory has it ("turkish"). */
    val language: String? = null,
    val tag: String? = null,
    /** "MP3", "AAC", … */
    val codec: String? = null,
    /** The lowest bitrate wanted, in kbit/s. */
    val minBitrate: Int = 0,
) {
    /** How many filters are on. */
    val count: Int
        get() = listOf(countryCode != null, language != null, tag != null, codec != null, minBitrate > 0).count { it }
}

/** A search of the directory: the words in the name, the filters, the order, and which page. */
@Immutable
data class StationQuery(
    val text: String = "",
    val filters: StationFilters = StationFilters(),
    val order: StationOrder = StationOrder.Votes,
    val offset: Int = 0,
    val limit: Int = PAGE,
) {
    companion object {
        /** Stations asked for at a time. */
        const val PAGE = 50
    }
}

/** A country of the directory. */
@Immutable
data class DirectoryCountry(val name: String, val code: String, val stations: Int)

/** A language of the directory: [name] as the directory spells it, [code] its ISO 639 code when it has one. */
@Immutable
data class DirectoryLanguage(val name: String, val code: String?, val stations: Int)

/** A tag of the directory. */
@Immutable
data class DirectoryTag(val name: String, val stations: Int)

/** The directory's answer to a click: the address the station plays from now. */
@Immutable
data class StationClick(val streamUrl: String?, val name: String?)

/** The directory's answer to a vote: [message] is its own words. */
@Immutable
data class VoteResult(val ok: Boolean, val message: String)

/** Why the directory could not answer. */
sealed class RadioFailure(message: String, cause: Throwable? = null) : java.io.IOException(message, cause) {
    /** No server could be reached, or none was found. */
    class Offline(cause: Throwable? = null) : RadioFailure("offline", cause)

    /** The directory asked to wait (429): [waitMs] more. */
    class Busy(val waitMs: Long = 0) : RadioFailure("busy")

    /** Another answer: its HTTP status, 200 for one that could not be read. */
    class Server(val status: Int) : RadioFailure("status $status")
}

/** S-1105: the Radio Browser directory: a real client in the app, a fake in the tests. Every call throws [RadioFailure]. */
interface RadioDirectory {
    suspend fun search(query: StationQuery): List<Station>

    suspend fun countries(): List<DirectoryCountry>

    suspend fun languages(): List<DirectoryLanguage>

    suspend fun tags(): List<DirectoryTag>

    /** Reports that a station starts playing (the directory counts it) and returns the address it plays from. */
    suspend fun click(uuid: String): StationClick

    /** Votes for a station. */
    suspend fun vote(uuid: String): VoteResult
}
