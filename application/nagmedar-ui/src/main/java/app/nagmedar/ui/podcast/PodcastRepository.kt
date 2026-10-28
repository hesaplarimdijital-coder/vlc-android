package app.nagmedar.ui.podcast

import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.Immutable
import app.nagmedar.data.PodcastDao
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.data.PodcastFeedSummary
import app.nagmedar.ui.playback.PlaybackRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Every address key the feed answers to: the one it is stored under and the ones it was reached by before it moved. */
internal fun PodcastFeed.addressKeys(): List<String> = listOf(FeedUrl.key(feedUrl)) + aliasKeys.split('\n').filter { it.isNotEmpty() }

private fun List<PodcastFeed>.byAddressKey(): Map<String, PodcastFeed> {
    val map = HashMap<String, PodcastFeed>()
    forEach { feed -> feed.addressKeys().forEach { map.putIfAbsent(it, feed) } }
    return map
}

/** What subscribing to one feed came to. */
sealed interface SubscribeResult {
    /** Subscribed, with [episodes] episodes read. */
    data class Added(val feed: PodcastFeed, val episodes: Int) : SubscribeResult

    /** Already a subscription (the same feed under another spelling of its address counts). */
    data class Already(val feed: PodcastFeed) : SubscribeResult

    /** Not subscribed, and nothing saved: [failure] says why. */
    data class Failed(val failure: FeedFailure) : SubscribeResult
}

/** A feed an OPML import could not subscribe to. */
@Immutable
data class ImportFailure(val title: String, val feedUrl: String, val failure: FeedFailure)

/** What an OPML import came to: feeds subscribed, feeds that already were, feeds that could not be. */
@Immutable
data class ImportSummary(val added: Int, val already: Int, val failed: List<ImportFailure>) {
    val total: Int get() = added + already + failed.size

    /** Every feed failed because the network was out of reach. */
    val offline: Boolean get() = failed.isNotEmpty() && failed.all { it.failure is FeedFailure.Offline }
}

/** A subscription taken away, whole, so that it can be put back (the undo bar). */
class UnsubscribedFeed(val feed: PodcastFeed, val episodes: List<PodcastEpisode>)

/** What refreshing a subscription came to. */
sealed interface RefreshResult {
    /** [added] episodes that the feed has and the subscription did not: [episodes], as stored. */
    data class Updated(val added: Int, val episodes: List<PodcastEpisode> = emptyList()) : RefreshResult

    data object NotModified : RefreshResult

    data class Failed(val failure: FeedFailure) : RefreshResult

    /** The subscription is gone. */
    data object Missing : RefreshResult
}

/**
 * S-1101, S-1102: the subscriptions. Fetches a feed, reads it and keeps it in Room: one place that
 * turns a fetch into rows, for subscribing, for an OPML import and for a refresh. An invalid feed
 * (not XML, not RSS or Atom, no episode with an enclosure) is a [SubscribeResult.Failed] and
 * nothing is saved. A feed that moved for good (301, 308) is stored under its new address, and the
 * address it was asked for stays an alias, so that the search result that gave it is still told as subscribed;
 * a move from https to http is followed but not kept.
 *
 * Room is the one source of subscriptions, deliberately: the medialibrary has a `MlService` and a
 * `Subscription` of its own (PODCAST), but mirroring subscriptions there would give two sources
 * that can disagree. Its feed reader would parse every feed a second time with VLC's own and none
 * of Podcasting 2.0's files, its media would turn up in the video and music libraries and their
 * counts, its refresh answers with a boolean (no moved feeds, no validators, no per-feed errors),
 * and it only exists once the medialibrary is initialised, which no unit test can do. Downloads,
 * refresh schedules and notifications (S-1103) are Nağmedar's own on top of Room.
 */
class PodcastRepository(
    private val dao: PodcastDao,
    private val source: FeedSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Podcast Index's id of the show at a feed address, for the shows added by their address; null when unknown. */
    private val indexId: suspend (feedUrl: String) -> Long? = { null },
) {
    /** The subscriptions by title, with their episodes counted. */
    fun subscriptions(): Flow<List<PodcastFeedSummary>> = dao.summaries()

    fun feed(feedId: Long): Flow<PodcastFeed?> = dao.observeFeed(feedId)

    /** The feed's episodes, the newest first. */
    fun episodes(feedId: Long): Flow<List<PodcastEpisode>> = dao.episodes(feedId)

    /**
     * Subscribes to the feed at [input], an address as typed or pasted. [hit] is the search result it
     * came from, whose cover, author and Podcast Index id fill what the feed leaves out; a feed added
     * by its address asks Podcast Index for its id too unless [lookupIndex] is false (an OPML import
     * of hundreds would queue behind the service's pace; those shows have no id until they are found by a search).
     * Never throws but for cancellation, and a cancelled subscription saves nothing.
     */
    suspend fun subscribe(input: String, hit: PodcastHit? = null, lookupIndex: Boolean = true): SubscribeResult {
        val candidates = FeedUrl.candidates(input)
        if (candidates.isEmpty()) return SubscribeResult.Failed(FeedFailure.BadAddress())
        val known = withContext(io) { dao.feedList() }.byAddressKey()
        candidates.firstNotNullOfOrNull { known[FeedUrl.key(it)] }?.let { return SubscribeResult.Already(it) }
        var failure: FeedFailure? = null
        for (url in candidates) {
            try {
                val fetch = source.fetch(url) as? FeedFetch.Fresh ?: continue
                return store(fetch, url, hit, known, lookupIndex)
            } catch (e: FeedFailure) {
                failure = failure ?: e
                // a page that is no feed is none over http either; an address that did not answer may over the other scheme
                if (e is FeedFailure.Invalid) break
            }
        }
        return SubscribeResult.Failed(failure ?: FeedFailure.Invalid(InvalidReason.Empty))
    }

    private suspend fun store(fetch: FeedFetch.Fresh, requested: String, hit: PodcastHit?, known: Map<String, PodcastFeed>, lookupIndex: Boolean): SubscribeResult {
        val address = fetch.permanentUrl ?: requested
        known[FeedUrl.key(address)]?.let { return SubscribeResult.Already(withAlias(it, requested)) }
        val parsed = fetch.feed
        val at = now()
        val index = hit?.podcastIndexId ?: if (lookupIndex) lookupIndexId(address) else null
        // the address the person (or the search result) used stays an alias of the one the feed moved to
        val alias = FeedUrl.key(requested).takeIf { it != FeedUrl.key(address) }.orEmpty()
        val feed = PodcastFeed(
            feedUrl = address,
            title = parsed.title.ifBlank { hit?.title ?: hostOf(address) },
            author = parsed.author ?: hit?.author,
            description = parsed.description ?: hit?.description,
            imageUrl = parsed.imageUrl ?: hit?.imageUrl,
            podcastIndexId = index,
            etag = fetch.etag,
            lastModified = fetch.lastModified,
            refreshedAt = at,
            subscribedAt = at,
            link = parsed.link,
            podcastGuid = parsed.podcastGuid,
            fundingUrl = parsed.fundingUrl,
            fundingMessage = parsed.fundingMessage,
            aliasKeys = alias,
            language = parsed.language,
        )
        // a subscription cancelled while the feed was being read saves nothing
        currentCoroutineContext().ensureActive()
        return withContext(io) {
            try {
                val id = dao.subscribeWith(feed, parsed.episodes.map { it.toEntity(0, at) })
                SubscribeResult.Added(feed.copy(id = id), parsed.episodes.size)
            } catch (e: SQLiteConstraintException) {
                // the same address was subscribed to meanwhile
                dao.feedByUrl(address)?.let { SubscribeResult.Already(it) } ?: SubscribeResult.Failed(FeedFailure.Offline(e))
            }
        }
    }

    /** [feed], also known by the address [requested] when that is another one: the next tap on it is known without a fetch. */
    private suspend fun withAlias(feed: PodcastFeed, requested: String): PodcastFeed {
        val key = FeedUrl.key(requested)
        if (key in feed.addressKeys()) return feed
        val updated = feed.copy(aliasKeys = (feed.addressKeys().drop(1) + key).joinToString("\n"))
        withContext(io) { dao.updateFeed(updated) }
        return updated
    }

    private suspend fun lookupIndexId(address: String): Long? = try {
        indexId(address)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Takes the subscription away with its episodes; null when it is gone already. */
    suspend fun unsubscribe(feedId: Long): UnsubscribedFeed? = withContext(io) {
        val feed = dao.feed(feedId) ?: return@withContext null
        val episodes = dao.episodeList(feedId)
        dao.unsubscribe(feed)
        UnsubscribedFeed(feed, episodes)
    }

    /** Puts [gone] back with its episodes and their playback state; the new feed's id, or null when its address was subscribed to meanwhile. */
    suspend fun resubscribe(gone: UnsubscribedFeed): Long? = withContext(io) {
        try {
            dao.subscribeWith(gone.feed.copy(id = 0), gone.episodes.map { it.copy(id = 0) })
        } catch (e: SQLiteConstraintException) {
            null
        }
    }

    suspend fun setPlayed(episodeId: Long, played: Boolean) = withContext(io) { dao.setPlayed(episodeId, played) }

    /** S-205: one episode, followed; null once it is gone. */
    fun episode(episodeId: Long): Flow<PodcastEpisode?> = dao.observeEpisode(episodeId)

    /** S-1104: the place of an episode, set to where it is about to be played from. */
    suspend fun setPosition(episodeId: Long, positionMs: Long) = withContext(io) { dao.savePositionOnly(episodeId, positionMs.coerceAtLeast(0L)) }

    /** S-1103: whether [feedId]'s new episodes are told in a notification. */
    suspend fun setNotify(feedId: Long, on: Boolean) = withContext(io) { dao.setNotify(feedId, on) }

    /** S-1103: [feedId]'s automatic download: off, or the newest [count] of each refresh's new episodes. */
    suspend fun setAutoDownload(feedId: Long, on: Boolean, count: Int) = withContext(io) { dao.setAutoDownload(feedId, on, count.coerceIn(1, MAX_AUTO)) }

    /** S-1103: how many downloaded episodes of [feedId] are kept; 0 keeps all. */
    suspend fun setKeepLatest(feedId: Long, count: Int) = withContext(io) { dao.setKeepLatest(feedId, count.coerceAtLeast(0)) }

    /** S-1104: how [feedId]'s episodes play: [speed] within 0.5 to 3, seconds skipped at the start and before the end. */
    suspend fun setPlayback(feedId: Long, speed: Float, introSeconds: Int, outroSeconds: Int) = withContext(io) {
        dao.setPlayback(feedId, speed.coerceIn(PlaybackRules.MIN_SPEED, PlaybackRules.MAX_SPEED), introSeconds.coerceIn(0, PlaybackRules.MAX_SKIP_S), outroSeconds.coerceIn(0, PlaybackRules.MAX_SKIP_S))
    }

    /** S-1104: [feedId]'s volume boost ([VolumeBoost]): off, light or strong. */
    suspend fun setVolumeBoost(feedId: Long, boost: Int) = withContext(io) { dao.setVolumeBoost(feedId, VolumeBoost.clamp(boost)) }

    /** The language Whisper hears the show in: a code, "auto", or null for the feed's ([TranscriptLanguage]). */
    suspend fun setTranscriptLanguage(feedId: Long, language: String?) = withContext(io) { dao.setTranscriptLanguage(feedId, language) }

    /**
     * S-1102: subscribes to each of [feeds], [PARALLEL] at a time; [progress] hears how many are done of how many.
     * Feeds already subscribed to are counted, not fetched. Never throws but for cancellation.
     */
    suspend fun importFeeds(feeds: List<OpmlFeed>, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportSummary {
        val known = withContext(io) { dao.feedList() }.byAddressKey().keys
        val (old, fresh) = feeds.distinctBy { FeedUrl.key(it.feedUrl) }.partition { FeedUrl.key(it.feedUrl) in known }
        val total = feeds.distinctBy { FeedUrl.key(it.feedUrl) }.size
        val done = AtomicInteger(old.size)
        progress(done.get(), total)
        val gate = Semaphore(PARALLEL)
        val results = coroutineScope {
            fresh.map { feed ->
                async {
                    val result = gate.withPermit { subscribe(feed.feedUrl, lookupIndex = false) }
                    progress(done.incrementAndGet(), total)
                    feed to result
                }
            }.awaitAll()
        }
        var added = 0
        var already = old.size
        val failed = ArrayList<ImportFailure>()
        results.forEach { (feed, result) ->
            when (result) {
                is SubscribeResult.Added -> added++
                is SubscribeResult.Already -> already++
                is SubscribeResult.Failed -> failed += ImportFailure(feed.title ?: hostOf(feed.feedUrl), feed.feedUrl, result.failure)
            }
        }
        return ImportSummary(added, already, failed)
    }

    /** S-1102: the subscriptions as an OPML 2.0 file named [title]. */
    suspend fun exportOpml(title: String): String = withContext(io) {
        Opml.write(dao.feedList().map { OpmlFeed(it.feedUrl, it.title, it.link) }, title, now())
    }

    /**
     * Reads the feed again with the validators kept (a 304 costs nothing), stores the address it moved
     * to, takes what the feed now says of the show, and adds the episodes it did not have: those are
     * the new ones. The schedule, the notifications and the downloads around it are [PodcastRefresher]'s.
     */
    suspend fun refresh(feedId: Long): RefreshResult {
        val feed = withContext(io) { dao.feed(feedId) } ?: return RefreshResult.Missing
        val fetch = try {
            source.fetch(feed.feedUrl, feed.etag, feed.lastModified)
        } catch (e: FeedFailure) {
            return RefreshResult.Failed(e)
        }
        val at = now()
        return withContext(io) {
            val others = dao.feedList().filter { it.id != feed.id }.byAddressKey()
            // the address it moved to, unless that is the same by key or another subscription answers to it
            val moved = fetch.permanentUrl?.takeIf { FeedUrl.key(it) != FeedUrl.key(feed.feedUrl) && FeedUrl.key(it) !in others }
            val home = if (moved == null) feed else feed.copy(feedUrl = moved, aliasKeys = (feed.addressKeys() - FeedUrl.key(moved)).joinToString("\n"))
            try {
                refreshed(home, fetch, at)
            } catch (e: SQLiteConstraintException) {
                // the new address was taken meanwhile: the feed stays where it was
                try {
                    refreshed(feed, fetch, at)
                } catch (again: SQLiteConstraintException) {
                    RefreshResult.Failed(FeedFailure.Offline(again))
                }
            }
        }
    }

    private fun refreshed(base: PodcastFeed, fetch: FeedFetch, at: Long): RefreshResult = when (fetch) {
        is FeedFetch.NotModified -> {
            dao.updateFeed(base.copy(refreshedAt = at))
            RefreshResult.NotModified
        }
        is FeedFetch.Fresh -> {
            val parsed = fetch.feed
            val added = dao.refreshFeed(
                base.copy(
                    title = parsed.title.ifBlank { base.title },
                    author = parsed.author ?: base.author,
                    description = parsed.description ?: base.description,
                    imageUrl = parsed.imageUrl ?: base.imageUrl,
                    etag = fetch.etag,
                    lastModified = fetch.lastModified,
                    refreshedAt = at,
                    link = parsed.link ?: base.link,
                    podcastGuid = parsed.podcastGuid ?: base.podcastGuid,
                    fundingUrl = parsed.fundingUrl ?: base.fundingUrl,
                    fundingMessage = parsed.fundingMessage ?: base.fundingMessage,
                    language = parsed.language ?: base.language,
                ),
                parsed.episodes.map { it.toEntity(base.id, at) },
            )
            RefreshResult.Updated(added.size, added)
        }
    }

    private fun hostOf(url: String): String = FeedUrl.key(url).substringBefore('/').substringBefore('?')

    private fun ParsedEpisode.toEntity(feedId: Long, addedAt: Long) = PodcastEpisode(
        feedId = feedId,
        guid = guid,
        title = title,
        audioUrl = audioUrl,
        mimeType = mimeType,
        durationMs = durationMs,
        publishedAt = publishedAt,
        description = description,
        addedAt = addedAt,
        sizeBytes = sizeBytes,
        imageUrl = imageUrl,
        season = season,
        episodeNumber = episodeNumber,
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcriptUrl,
        transcriptType = transcriptType,
        notesHtml = notesHtml,
    )

    companion object {
        /** Feeds fetched at once by an import: quick enough for hundreds, gentle on the hosts. */
        const val PARALLEL = 4

        /** The most episodes a show downloads by itself at once. */
        const val MAX_AUTO = 20
    }
}
