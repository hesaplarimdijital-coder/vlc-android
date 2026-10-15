package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.data.PodcastFeedSummary

/** What the search shows. */
sealed interface SearchUi {
    /** Nothing asked: the subscriptions show. */
    data object Idle : SearchUi

    data object Searching : SearchUi

    /** [hits] for [term]; [notice] says when Podcast Index had no part in them. */
    data class Found(val term: String, val hits: List<PodcastHit>, val notice: SearchNotice?) : SearchUi

    data class Failed(val term: String, val reason: SearchFailure) : SearchUi
}

/** Why a search found nothing to show. */
enum class SearchFailure { Offline, Busy, Server }

/** One search result being subscribed to. */
sealed interface SubscribeUi {
    data object Working : SubscribeUi

    data class Failed(val failure: FeedFailure) : SubscribeUi
}

/** The "add by address" window: [working] while the feed is fetched; [failure] when it was not a feed. */
@Immutable
data class AddUi(val input: String = "", val working: Boolean = false, val failure: FeedFailure? = null)

/** An OPML import, from the file being read to the word on how it went. */
sealed interface ImportUi {
    data object Reading : ImportUi

    data class Running(val done: Int, val total: Int) : ImportUi

    data class Done(val summary: ImportSummary) : ImportUi

    /** The file is not a list of podcasts, or could not be read at all ([problem] null). */
    data class Unusable(val problem: OpmlProblem?) : ImportUi
}

/** The podcast page: the subscription and its episodes (null while they are read). */
@Immutable
data class PodcastPage(val summary: PodcastFeedSummary, val episodes: List<PodcastEpisode>?)

/** Everything the Podcasts screen draws. */
@Immutable
data class PodcastScreenModel(
    /** The subscriptions; null while they are read. */
    val subscriptions: List<PodcastFeedSummary>?,
    val query: String = "",
    val search: SearchUi = SearchUi.Idle,
    /** The results being subscribed to or refused, by [FeedUrl.key]. */
    val subscribing: Map<String, SubscribeUi> = emptyMap(),
    val page: PodcastPage? = null,
    val add: AddUi? = null,
    val importing: ImportUi? = null,
) {
    /** The feeds subscribed to, by [FeedUrl.key]: each under the address it is stored at and the ones it was reached by. */
    val subscribedKeys: Set<String> = subscriptions.orEmpty().flatMapTo(HashSet()) { it.feed.addressKeys() }
}

/** What the screen asks of its owner; every one does nothing by default, so that previews and tests give only what they look at. */
class PodcastActions(
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onSubscribe: (PodcastHit) -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onOpenSubscribed: (PodcastHit) -> Unit = {},
    val onClosePage: () -> Unit = {},
    val onUnsubscribe: (Long) -> Unit = {},
    val onCopy: (String) -> Unit = {},
    val onPlay: (PodcastFeed, PodcastEpisode) -> Unit = { _, _ -> },
    val onSetPlayed: (Long, Boolean) -> Unit = { _, _ -> },
    val onAdd: () -> Unit = {},
    val onAddSubmit: (String) -> Unit = {},
    val onAddDismiss: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onImportDismiss: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
)
