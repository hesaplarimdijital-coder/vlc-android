package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import app.nagmedar.data.PodcastDao
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** S-1103: what a round of refreshing came to. */
@Immutable
data class RefreshReport(
    /** The feeds read. */
    val checked: Int,
    /** The feeds that could not be read (offline, server down, not a feed any more). */
    val failed: Int,
    /** The news among the episodes the round added. */
    val news: Int,
    /** What the notifications say; empty with the notifications off. */
    val notices: NoticePlan,
    /** The episodes queued for download by the shows that download by themselves. */
    val queued: List<Long>,
    /** What the storage rules took away afterwards. */
    val cleanup: CleanupPlan?,
) {
    /** Every feed failed: the network is the likely reason and the work is worth trying again soon. */
    val allFailed: Boolean get() = checked > 0 && failed == checked
}

/**
 * S-1103: the work of the periodic job apart from WorkManager and the notification service: every subscription is read
 * again (the repository sends the stored ETag and Last-Modified, so a feed that did not change costs a 304), the episodes
 * the feeds added are sorted into news, the shows that download by themselves get their newest queued, and the downloads
 * are tidied by the storage rules. The notifications and the download work are the caller's: it gets the [RefreshReport].
 */
class PodcastRefresher(
    private val repository: PodcastRepository,
    private val dao: PodcastDao,
    private val settings: () -> PodcastSettings,
    private val downloads: DownloadManager,
    private val cleaner: StorageCleaner,
    private val protectedNow: () -> Set<Long> = { emptySet() },
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * One round over all subscriptions. [onFeed] hears each feed after it is read, for the screens that show progress.
     * A feed that fails does not stop the others.
     */
    suspend fun run(onFeed: (done: Int, total: Int) -> Unit = { _, _ -> }): RefreshReport {
        val feeds = withContext(io) { dao.feedList() }
        val config = settings()
        val news = ArrayList<Pair<PodcastFeed, List<PodcastEpisode>>>()
        val queue = ArrayList<Long>()
        var failed = 0
        feeds.forEachIndexed { index, feed ->
            when (val result = repository.refresh(feed.id)) {
                is RefreshResult.Updated -> {
                    val fresh = NewEpisodes.fresh(result.episodes, now())
                    if (fresh.isNotEmpty()) news += feed to fresh
                    // the show's settings as they are now: the person may have changed them while the feed was read
                    val current = withContext(io) { dao.feed(feed.id) } ?: feed
                    queue += NewEpisodes.toDownload(current, fresh).map { it.id }
                }
                is RefreshResult.Failed -> failed++
                RefreshResult.NotModified, RefreshResult.Missing -> Unit
            }
            onFeed(index + 1, feeds.size)
        }
        val queued = if (queue.isEmpty()) emptyList() else downloads.queue(queue, auto = true)
        val cleanup = cleaner.clean(protectedNow())
        return RefreshReport(
            checked = feeds.size,
            failed = failed,
            news = news.sumOf { it.second.size },
            notices = NoticePlan.of(news, config.notifications),
            queued = queued,
            cleanup = cleanup,
        )
    }
}
