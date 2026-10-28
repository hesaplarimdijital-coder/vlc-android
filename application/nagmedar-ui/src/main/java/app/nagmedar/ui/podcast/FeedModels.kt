package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import java.io.IOException

/** S-1101: a feed as its XML told it: the show, and the episodes that have something to play. */
@Immutable
data class ParsedFeed(
    val title: String,
    val author: String? = null,
    /** The show's description as plain text. */
    val description: String? = null,
    val imageUrl: String? = null,
    /** The show's web page. */
    val link: String? = null,
    /** Podcasting 2.0's `podcast:guid`. */
    val podcastGuid: String? = null,
    /** Podcasting 2.0's `podcast:funding`: its address and its words. */
    val fundingUrl: String? = null,
    val fundingMessage: String? = null,
    /** The show's language as a tag ("tr", "en-us"): RSS's `language` (iTunes reads the same), Dublin Core's, Atom's `xml:lang`. */
    val language: String? = null,
    /** Newest first. */
    val episodes: List<ParsedEpisode>,
    /** Items left out: no enclosure, an enclosure that is no address, or a guid already listed. */
    val skipped: Int = 0,
)

/** S-1101: one episode of a feed. */
@Immutable
data class ParsedEpisode(
    /** The feed's own id of the episode; its audio address when the feed gives none. */
    val guid: String,
    val title: String,
    val audioUrl: String,
    val mimeType: String? = null,
    /** The enclosure's length as the feed says; 0 when it does not. */
    val sizeBytes: Long = 0,
    /** 0 when the feed gives none. */
    val durationMs: Long = 0,
    /** Epoch milliseconds; 0 when the feed gives no date, or one that cannot be read. */
    val publishedAt: Long = 0,
    val description: String? = null,
    val imageUrl: String? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    /** Podcasting 2.0's `podcast:chapters` file. */
    val chaptersUrl: String? = null,
    /** Podcasting 2.0's `podcast:transcript` file and its media type. */
    val transcriptUrl: String? = null,
    val transcriptType: String? = null,
    /** S-205: the show notes with their markup, as the feed wrote them; null when the feed's text had none. */
    val notesHtml: String? = null,
)

/** Why an address is not a feed. */
enum class InvalidReason {
    /** Not XML at all: a web page's text, JSON, an image. */
    NotXml,

    /** XML, but not an RSS or Atom feed: a web page in XHTML, a sitemap, an OPML. */
    NotFeed,

    /** A feed, but nothing in it to listen to: no item has an audio or video enclosure. */
    NoEpisodes,

    /** Nothing came back. */
    Empty,
}

/** Why a feed could not be read or fetched. Nothing is saved for any of them. */
sealed class FeedFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** What came back is not a podcast feed. */
    class Invalid(val reason: InvalidReason, cause: Throwable? = null) : FeedFailure("invalid: $reason", cause)

    /** The text typed is not an address of a web page. */
    class BadAddress : FeedFailure("bad address")

    /** No network, or the server could not be reached. */
    class Offline(cause: Throwable? = null) : FeedFailure("offline", cause)

    /** The server answered with [status]: 404 and 410 for a feed that is gone. */
    class Server(val status: Int) : FeedFailure("status $status")
}
