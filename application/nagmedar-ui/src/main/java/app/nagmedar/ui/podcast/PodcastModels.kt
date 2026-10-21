package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import app.nagmedar.data.DownloadState
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

/** S-1103: how an episode's download stands, for its buttons and rows. */
sealed interface DownloadUi {
    data object None : DownloadUi

    /** Waiting for the work to start (a network, a battery that is not low). */
    data object Queued : DownloadUi

    /** Being downloaded; [progress] null before the first bytes. */
    data class Running(val progress: DownloadProgress?) : DownloadUi

    data class Done(val bytes: Long) : DownloadUi

    /** The last try failed; the button says "Tekrar dene". */
    data object Failed : DownloadUi

    companion object {
        fun of(episode: PodcastEpisode, running: DownloadProgress?): DownloadUi = when (episode.downloadState) {
            DownloadState.QUEUED -> Queued
            DownloadState.RUNNING -> Running(running)
            DownloadState.DONE -> Done(episode.downloadBytes)
            DownloadState.FAILED -> Failed
            else -> None
        }
    }
}

/** S-1104, S-205: what an episode's chapters or transcript are: being read, there, missing, or out of reach for want of a network. */
sealed interface ExtraUi<out T> {
    data object Loading : ExtraUi<Nothing>

    data class Ready<T>(val value: T) : ExtraUi<T>

    data object None : ExtraUi<Nothing>

    data object Offline : ExtraUi<Nothing>
}

/** S-205: the episode that plays and where it is: what the detail screen's timeline, chapters and transcript follow. */
@Immutable
data class Listening(val positionMs: Long, val lengthMs: Long, val playing: Boolean)

/** S-205: everything an episode's page draws. [listening] is null unless the player has this very episode loaded. */
@Immutable
data class EpisodeDetailUi(
    val feed: PodcastFeed,
    val episode: PodcastEpisode,
    /** The show notes after sanitising; empty for an episode with none. */
    val notes: List<NoteBlock>,
    val chapters: ExtraUi<List<Chapter>>,
    val transcript: ExtraUi<Transcript>,
    val download: DownloadUi,
    val listening: Listening? = null,
    /** The seconds the skip back and skip forward buttons go. */
    val skipBackSeconds: Int = 15,
    val skipForwardSeconds: Int = 30,
    /** The device has no network: what is not downloaded cannot be played or read. */
    val offline: Boolean = false,
)

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
    /** S-205: the episode whose page is open (null for none); [episode] is its page, null while it is read. */
    val episodeId: Long? = null,
    val episode: EpisodeDetailUi? = null,
    /** S-1103: the downloads that are running, by episode id. */
    val downloads: Map<Long, DownloadProgress> = emptyMap(),
    /** S-1103, S-1104: the show whose settings window is open. */
    val showSettings: PodcastFeed? = null,
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
    /** S-205: the episode's page, and back from it. */
    val onOpenEpisode: (Long) -> Unit = {},
    val onCloseEpisode: () -> Unit = {},
    /** S-205: play or pause the episode that is loaded, else start it. */
    val onTogglePlay: (PodcastFeed, PodcastEpisode) -> Unit = { _, _ -> },
    /** S-205: play the episode from [ms] (a chapter, a line of the transcript). */
    val onPlayAt: (PodcastFeed, PodcastEpisode, Long) -> Unit = { _, _, _ -> },
    /** S-205: the timeline's seek, to [ms] of the episode that plays. */
    val onSeekTo: (Long) -> Unit = {},
    /** S-1104: skip by the given milliseconds (negative back), and to the next or previous chapter. */
    val onSkip: (Long) -> Unit = {},
    val onStepChapter: (Boolean) -> Unit = {},
    /** S-1103: download, stop a download, delete a downloaded file. */
    val onDownload: (Long) -> Unit = {},
    val onCancelDownload: (Long) -> Unit = {},
    val onDeleteDownload: (Long) -> Unit = {},
    val onShare: (PodcastFeed, PodcastEpisode) -> Unit = { _, _ -> },
    /** S-205: the chapters or the transcript asked for again after the network failed. */
    val onRetryExtras: () -> Unit = {},
    /** S-1103, S-1104: the show's settings window, and what it changes. */
    val onShowSettings: (Long) -> Unit = {},
    val onDismissShowSettings: () -> Unit = {},
    val onSetNotify: (Long, Boolean) -> Unit = { _, _ -> },
    val onSetAutoDownload: (Long, Boolean, Int) -> Unit = { _, _, _ -> },
    val onSetKeepLatest: (Long, Int) -> Unit = { _, _ -> },
    val onSetPlayback: (Long, Float, Int, Int) -> Unit = { _, _, _, _ -> },
)
