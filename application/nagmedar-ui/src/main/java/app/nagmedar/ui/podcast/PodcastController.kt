package app.nagmedar.ui.podcast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.data.PodcastFeedSummary
import app.nagmedar.ui.R
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import org.videolan.tools.Notices

/**
 * S-1102: the OPML import that is running or has just finished, kept apart from the screen: leaving the
 * Podcasts screen neither stops the import nor loses its summary, which is there when the screen is back.
 */
class ImportTracker {
    val state = MutableStateFlow<ImportUi?>(null)

    internal var job: Job? = null
}

/**
 * S-1103, S-1104, S-205: what the episode pages need beside the repository: the download queue and its progress, the
 * chapters and transcripts, the player's controls, and Settings › Podcasts. Without them (tests of other things)
 * downloads do nothing, chapters and transcripts are missing, and nothing plays.
 */
class EpisodeServices(
    val downloads: DownloadManager? = null,
    val extras: EpisodeExtras? = null,
    val control: EpisodeControl = NoEpisodeControl,
    val progress: StateFlow<Map<Long, DownloadProgress>> = MutableStateFlow(emptyMap()),
    val settings: () -> PodcastSettings = { PodcastSettings() },
    /** Heard when the subscriptions change from none to some or back: the refresh job is scheduled or cancelled. */
    val onSubscribed: (Boolean) -> Unit = {},
)

/**
 * S-1101, S-1102: what the Podcasts screen is doing: the subscriptions, the search and its results,
 * the podcast page that is open, the "add by address" window and an OPML import in progress. It
 * keeps them as Compose state ([model]) and does the work through the [repository] and [search],
 * on [scope]; the screen only draws [model] and calls back.
 *
 * [undo] (when the shell has an undo bar) brings back a subscription taken away and takes back one
 * just made. What must not stop when the screen is left (the undo, the import, the export, the
 * unsubscribing and the played marks) runs on [background], which outlives it (the app's scope in the app);
 * what only matters while the screen is there (the search, a subscription in progress, the page) runs on [scope],
 * and cancelling it cancels the network call under it.
 */
@Stable
class PodcastController(
    private val repository: PodcastRepository,
    private val search: PodcastSearch,
    private val scope: CoroutineScope,
    private val context: Context,
    private val undo: UndoBarState?,
    private val background: CoroutineScope = scope,
    private val imports: ImportTracker = ImportTracker(),
    private val services: EpisodeServices = EpisodeServices(),
    private val player: EpisodePlayer,
) {
    private val res get() = context.resources

    private var subscriptions by mutableStateOf<List<PodcastFeedSummary>?>(null)
    private var query by mutableStateOf("")
    private var found by mutableStateOf<SearchUi>(SearchUi.Idle)
    private val subscribing = mutableStateMapOf<String, SubscribeUi>()
    private var openId by mutableStateOf<Long?>(null)
    private var episodes by mutableStateOf<List<PodcastEpisode>?>(null)
    private var add by mutableStateOf<AddUi?>(null)
    private var importing by mutableStateOf<ImportUi?>(imports.state.value)
    private var openEpisodeId by mutableStateOf<Long?>(null)
    private var episodeRow by mutableStateOf<PodcastEpisode?>(null)
    private var notes by mutableStateOf<List<NoteBlock>>(emptyList())
    private var chapters by mutableStateOf<ExtraUi<List<Chapter>>>(ExtraUi.Loading)
    private var transcript by mutableStateOf<ExtraUi<Transcript>>(ExtraUi.Loading)
    private var settingsFeedId by mutableStateOf<Long?>(null)
    private var downloadProgress by mutableStateOf<Map<Long, DownloadProgress>>(emptyMap())
    private var listeningState by mutableStateOf<Listening?>(null)
    private var settingsNow by mutableStateOf(services.settings())
    private var episodeJob: Job? = null
    private var extrasJob: Job? = null

    private var searchJob: Job? = null
    private var pageJob: Job? = null
    private var addJob: Job? = null

    val model: PodcastScreenModel
        get() {
            val list = subscriptions
            val page = openId?.let { id -> list?.firstOrNull { it.feed.id == id } }?.let { PodcastPage(it, episodes) }
            val row = episodeRow
            val feed = row?.let { r -> list?.firstOrNull { it.feed.id == r.feedId }?.feed }
            val detail = if (row != null && feed != null) {
                val chapterState = chapters
                val transcriptState = transcript
                EpisodeDetailUi(
                    feed = feed,
                    episode = row,
                    notes = notes,
                    chapters = chapterState,
                    transcript = transcriptState,
                    download = DownloadUi.of(row, downloadProgress[row.id]),
                    listening = listeningState,
                    skipBackSeconds = settingsNow.skipBackSeconds,
                    skipForwardSeconds = settingsNow.skipForwardSeconds,
                    offline = chapterState is ExtraUi.Offline || transcriptState is ExtraUi.Offline,
                )
            } else {
                null
            }
            val shown = settingsFeedId?.let { id -> list?.firstOrNull { it.feed.id == id }?.feed }
            return PodcastScreenModel(list, query, found, subscribing.toMap(), page, add, importing, openEpisodeId, detail, downloadProgress, shown)
        }

    /** The place of the playing episode, read apart from [model] so that only the pages that draw it follow it. */
    fun listening(): Listening? = listeningState

    /** Follows the subscriptions, the downloads and the player in Room and the services until cancelled. */
    suspend fun follow() = coroutineScope {
        launch { imports.state.collect { importing = it } }
        launch { services.progress.collect { downloadProgress = it } }
        launch {
            // the place of the episode whose page is open: a look a few times a second, only while one is
            while (true) {
                val row = episodeRow
                listeningState = row?.let { services.control.listening(it) }
                delay(LISTENING_MS)
            }
        }
        var had: Boolean? = null
        repository.subscriptions().collect { list ->
            subscriptions = list
            val any = list.isNotEmpty()
            if (had != any) {
                had = any
                services.onSubscribed(any)
            }
        }
    }

    // ------------------------------------------------------------ search

    fun onQueryChange(text: String) {
        query = text
        if (text.isBlank()) clearSearch()
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        query = ""
        found = SearchUi.Idle
    }

    /** Searches for what is typed; an address typed there is subscribed to, as the "add" window would. */
    fun runSearch() {
        val term = query.trim()
        if (term.isEmpty()) return
        if (looksLikeAddress(term)) {
            clearSearch()
            submitAddress(term)
            return
        }
        searchJob?.cancel()
        found = SearchUi.Searching
        searchJob = scope.launch {
            found = try {
                val outcome = search.search(term)
                SearchUi.Found(term, outcome.hits, outcome.notice)
            } catch (e: DirectoryFailure) {
                SearchUi.Failed(
                    term,
                    when (e) {
                        is DirectoryFailure.Offline -> SearchFailure.Offline
                        is DirectoryFailure.Busy -> SearchFailure.Busy
                        is DirectoryFailure.Server -> SearchFailure.Server
                    },
                )
            }
        }
    }

    private fun looksLikeAddress(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return ADDRESS_PREFIXES.any { lower.startsWith(it) } && FeedUrl.normalize(text) != null
    }

    // ------------------------------------------------------------ subscribing

    /** One tap on a search result: subscribes; the result says "Abone" when it is done, or why it could not be. */
    fun subscribe(hit: PodcastHit) {
        val key = FeedUrl.key(hit.feedUrl)
        if (subscribing[key] == SubscribeUi.Working) return
        subscribing[key] = SubscribeUi.Working
        scope.launch {
            when (val result = repository.subscribe(hit.feedUrl, hit)) {
                is SubscribeResult.Added -> {
                    subscribing.remove(key)
                    announce(result.feed)
                }
                is SubscribeResult.Already -> {
                    subscribing.remove(key)
                    say(res.getString(R.string.nag_pod_already, result.feed.title))
                    open(result.feed.id)
                }
                is SubscribeResult.Failed -> subscribing[key] = SubscribeUi.Failed(result.failure)
            }
        }
    }

    /** Subscribes to the address typed in the "add" window; the window stays with the reason when it is no feed. */
    fun submitAddress(text: String) {
        addJob?.cancel()
        add = AddUi(text, working = true)
        addJob = scope.launch {
            when (val result = repository.subscribe(text)) {
                is SubscribeResult.Added -> {
                    add = null
                    announce(result.feed)
                    open(result.feed.id)
                }
                is SubscribeResult.Already -> {
                    add = null
                    say(res.getString(R.string.nag_pod_already, result.feed.title))
                    open(result.feed.id)
                }
                is SubscribeResult.Failed -> add = AddUi(text, failure = result.failure)
            }
        }
    }

    fun openAdd() {
        add = AddUi()
    }

    fun dismissAdd() {
        addJob?.cancel()
        addJob = null
        add = null
    }

    /** "X eklendi · Geri al": the undo takes the subscription away again. */
    private fun announce(feed: PodcastFeed) {
        val text = res.getString(R.string.nag_pod_subscribed_to, feed.title)
        if (undo == null) {
            say(text)
        } else {
            undo.show(text, res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) {
                background.launch { repository.unsubscribe(feed.id) }
            }
        }
    }

    /** Takes the subscription away; the undo bar puts it back with its episodes. */
    fun unsubscribe(feedId: Long) {
        background.launch {
            val gone = repository.unsubscribe(feedId) ?: return@launch
            if (openId == feedId) closePage()
            val text = res.getString(R.string.nag_pod_unsubscribed, gone.feed.title)
            if (undo == null) {
                say(text)
            } else {
                undo.show(text, res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) {
                    background.launch { repository.resubscribe(gone) }
                }
            }
        }
    }

    // ------------------------------------------------------------ the podcast page

    fun open(feedId: Long) {
        if (openId == feedId) return
        closeEpisode()
        pageJob?.cancel()
        openId = feedId
        episodes = null
        pageJob = scope.launch { repository.episodes(feedId).collect { episodes = it } }
    }

    /** A search result already subscribed to: its page. */
    fun openSubscribed(hit: PodcastHit) {
        val key = FeedUrl.key(hit.feedUrl)
        subscriptions?.firstOrNull { key in it.feed.addressKeys() }?.let { open(it.feed.id) }
    }

    fun closePage() {
        closeEpisode()
        settingsFeedId = null
        pageJob?.cancel()
        pageJob = null
        openId = null
        episodes = null
    }

    // ------------------------------------------------------------ an episode's page

    /** The page of [episodeId]: its row followed in Room, its show notes read, its chapters and transcript asked for. */
    fun openEpisode(episodeId: Long) {
        if (openEpisodeId == episodeId) return
        episodeJob?.cancel()
        extrasJob?.cancel()
        openEpisodeId = episodeId
        episodeRow = null
        notes = emptyList()
        chapters = ExtraUi.Loading
        transcript = ExtraUi.Loading
        settingsNow = services.settings()
        episodeJob = scope.launch {
            var first = true
            repository.episode(episodeId).collect { row ->
                episodeRow = row
                if (first && row != null) {
                    first = false
                    val base = subscriptions?.firstOrNull { it.feed.id == row.feedId }?.feed?.let { it.link ?: it.feedUrl }
                    notes = withContext(Dispatchers.Default) { ShowNotes.of(row.notesHtml, row.description, base) }
                    loadExtras(row)
                }
            }
        }
    }

    fun closeEpisode() {
        episodeJob?.cancel()
        extrasJob?.cancel()
        episodeJob = null
        extrasJob = null
        openEpisodeId = null
        episodeRow = null
        listeningState = null
    }

    private fun loadExtras(row: PodcastEpisode) {
        val extras = services.extras
        extrasJob?.cancel()
        if (extras == null) {
            chapters = ExtraUi.None
            transcript = ExtraUi.None
            return
        }
        chapters = ExtraUi.Loading
        transcript = ExtraUi.Loading
        extrasJob = scope.launch {
            launch { chapters = extras.chapters(row).toUi() }
            launch { transcript = extras.transcript(row).toUi() }
        }
    }

    private fun <T> ExtraLoad<T>.toUi(): ExtraUi<T> = when (this) {
        is ExtraLoad.Ready -> ExtraUi.Ready(value)
        ExtraLoad.None -> ExtraUi.None
        ExtraLoad.Offline -> ExtraUi.Offline
    }

    /** Asks for the chapters and the transcript again, after the network failed. */
    fun retryExtras() {
        episodeRow?.let(::loadExtras)
    }

    /** Plays or pauses the episode the player has loaded, else starts [episode] where it was left. */
    fun togglePlay(feed: PodcastFeed, episode: PodcastEpisode) {
        if (services.control.listening(episode) != null) services.control.togglePlay() else player.play(feed, episode)
    }

    /** From [ms]: a seek when the player has the episode, else the episode started there. */
    fun playAt(feed: PodcastFeed, episode: PodcastEpisode, ms: Long) {
        val now = services.control.listening(episode)
        if (now == null) {
            // the place is kept first: the playback observer reads it as the place to start from
            scope.launch {
                repository.setPosition(episode.id, ms)
                player.playFrom(feed, episode, ms)
            }
        } else {
            services.control.seekTo(ms)
            if (!now.playing) services.control.togglePlay()
        }
    }

    fun seekTo(ms: Long) = services.control.seekTo(ms)

    fun skip(deltaMs: Long) {
        val row = episodeRow ?: return
        val now = services.control.listening(row) ?: return
        services.control.seekTo((now.positionMs + deltaMs).coerceIn(0L, now.lengthMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }

    fun stepChapter(forward: Boolean) {
        val row = episodeRow ?: return
        val list = (chapters as? ExtraUi.Ready)?.value ?: return
        val now = services.control.listening(row) ?: return
        Chapters.step(list, now.positionMs, forward)?.let { services.control.seekTo(list[it].startMs) }
    }

    fun download(episodeId: Long) {
        background.launch { services.downloads?.queue(listOf(episodeId)) }
    }

    fun cancelDownload(episodeId: Long) {
        background.launch { services.downloads?.cancel(episodeId) }
    }

    fun deleteDownload(episodeId: Long) {
        background.launch {
            val deleted = services.downloads?.delete(episodeId) ?: return@launch
            if (!deleted) say(res.getString(R.string.nag_pod_delete_failed))
        }
    }

    /** The sheet of the system: the episode's name, its show and its audio address. */
    fun share(feed: PodcastFeed, episode: PodcastEpisode) {
        val text = listOf(episode.title, feed.title, episode.audioUrl).joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, episode.title)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: android.content.ActivityNotFoundException) {
            say(res.getString(R.string.nag_pod_share_failed))
        }
    }

    // ------------------------------------------------------------ the show's own settings

    fun showSettings(feedId: Long) {
        settingsFeedId = feedId
    }

    fun dismissShowSettings() {
        settingsFeedId = null
    }

    fun setNotify(feedId: Long, on: Boolean) {
        background.launch { repository.setNotify(feedId, on) }
    }

    fun setAutoDownload(feedId: Long, on: Boolean, count: Int) {
        background.launch { repository.setAutoDownload(feedId, on, count) }
    }

    fun setKeepLatest(feedId: Long, count: Int) {
        background.launch { repository.setKeepLatest(feedId, count) }
    }

    fun setPlayback(feedId: Long, speed: Float, introSeconds: Int, outroSeconds: Int) {
        background.launch { repository.setPlayback(feedId, speed, introSeconds, outroSeconds) }
    }

    fun setVolumeBoost(feedId: Long, boost: Int) {
        background.launch { repository.setVolumeBoost(feedId, boost) }
    }

    fun play(feed: PodcastFeed, episode: PodcastEpisode) = player.play(feed, episode)

    fun setPlayed(episodeId: Long, played: Boolean) {
        background.launch { repository.setPlayed(episodeId, played) }
    }

    // ------------------------------------------------------------ OPML

    /** Reads the OPML file at [uri] and subscribes to its feeds, saying how far it got; leaving the screen does not stop it. */
    fun importFile(uri: Uri) {
        imports.job?.cancel()
        imports.state.value = ImportUi.Reading
        imports.job = background.launch {
            val document = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { Opml.parse(it) } }
            } catch (e: OpmlException) {
                imports.state.value = ImportUi.Unusable(e.problem)
                return@launch
            } catch (e: IOException) {
                imports.state.value = ImportUi.Unusable(null)
                return@launch
            } catch (e: SecurityException) {
                imports.state.value = ImportUi.Unusable(null)
                return@launch
            }
            if (document == null) {
                imports.state.value = ImportUi.Unusable(null)
                return@launch
            }
            imports.state.value = ImportUi.Running(0, document.feeds.size)
            val summary = repository.importFeeds(document.feeds) { done, total -> imports.state.value = ImportUi.Running(done, total) }
            imports.state.value = ImportUi.Done(summary)
        }
    }

    /** Closes the import's window; an import still running stops (what was subscribed to stays). */
    fun dismissImport() {
        imports.job?.cancel()
        imports.job = null
        imports.state.value = null
    }

    /** A short notice in the window in front, whichever thread the work ended on. */
    private fun say(message: String, long: Boolean = false) {
        Notices.show(context, message, long)
    }

    /** Writes the subscriptions as OPML to the file at [uri] and says so; leaving the screen does not cut the file short. */
    fun export(uri: Uri) {
        val count = subscriptions?.size ?: 0
        background.launch {
            val saved = withContext(Dispatchers.IO) {
                val ok = try {
                    val text = repository.exportOpml(res.getString(R.string.nag_pod_opml_title))
                    // "w", not "wt": a document the picker has just made is empty, and not every provider takes the truncating mode
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    // providers also fail with SecurityException, IllegalArgumentException, UnsupportedOperationException
                    false
                } catch (e: IOException) {
                    false
                }
                if (!ok) discard(uri)
                ok
            }
            val message = if (saved) res.getQuantityString(R.plurals.nag_pod_exported, count, count) else res.getString(R.string.nag_pod_export_failed)
            say(message, long = true)
        }
    }

    /** A document the export made and could not fill is taken away again, not left empty in the person's files. */
    private fun discard(uri: Uri) {
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: RuntimeException) {
            // a provider that will not delete it either: nothing more to do
        } catch (e: FileNotFoundException) {
            // gone already
        }
    }

    /** What the screen calls back to. */
    fun actions(onCopy: (String) -> Unit, onOpenLink: (String) -> Unit, onImport: () -> Unit, onExport: () -> Unit) = PodcastActions(
        onQueryChange = ::onQueryChange,
        onSearch = ::runSearch,
        onClearSearch = ::clearSearch,
        onSubscribe = ::subscribe,
        onOpen = ::open,
        onOpenSubscribed = ::openSubscribed,
        onClosePage = ::closePage,
        onUnsubscribe = ::unsubscribe,
        onCopy = onCopy,
        onPlay = ::play,
        onSetPlayed = ::setPlayed,
        onAdd = ::openAdd,
        onAddSubmit = ::submitAddress,
        onAddDismiss = ::dismissAdd,
        onImport = onImport,
        onImportDismiss = ::dismissImport,
        onExport = onExport,
        onOpenLink = onOpenLink,
        onOpenEpisode = ::openEpisode,
        onCloseEpisode = ::closeEpisode,
        onTogglePlay = ::togglePlay,
        onPlayAt = ::playAt,
        onSeekTo = ::seekTo,
        onSkip = ::skip,
        onStepChapter = ::stepChapter,
        onDownload = ::download,
        onCancelDownload = ::cancelDownload,
        onDeleteDownload = ::deleteDownload,
        onShare = ::share,
        onRetryExtras = ::retryExtras,
        onShowSettings = ::showSettings,
        onDismissShowSettings = ::dismissShowSettings,
        onSetNotify = ::setNotify,
        onSetAutoDownload = ::setAutoDownload,
        onSetKeepLatest = ::setKeepLatest,
        onSetPlayback = ::setPlayback,
        onSetVolumeBoost = ::setVolumeBoost,
    )

    private companion object {
        const val LISTENING_MS = 400L
        val ADDRESS_PREFIXES = listOf("http://", "https://", "feed:", "itpc:", "pcast:", "podcast:")
    }
}
