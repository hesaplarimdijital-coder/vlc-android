package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import app.nagmedar.data.DownloadState
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed

/**
 * S-1103: which of the episodes a refresh added are news. A feed that changes the ids of its episodes lists its whole
 * past as new; only what was published lately, and not played, is told and downloaded.
 */
object NewEpisodes {
    /** An episode published longer ago than this is not news. */
    const val FRESH_DAYS = 14
    private const val DAY_MS = 86_400_000L

    /** The news among [added], the newest first: not played, published within [FRESH_DAYS] days of [now] (one with no date counts as published now). */
    fun fresh(added: List<PodcastEpisode>, now: Long): List<PodcastEpisode> =
        added.filter { !it.played && (it.publishedAt <= 0 || now - it.publishedAt <= FRESH_DAYS * DAY_MS) }.sortedByDescending { it.publishedAt }

    /** What [feed] downloads by itself of [fresh]: nothing with the switch off, else the newest [PodcastFeed.autoDownloadCount] not downloaded yet. */
    fun toDownload(feed: PodcastFeed, fresh: List<PodcastEpisode>): List<PodcastEpisode> {
        if (!feed.autoDownload) return emptyList()
        return fresh.filter { it.downloadState == DownloadState.NONE }.sortedByDescending { it.publishedAt }.take(feed.autoDownloadCount.coerceAtLeast(1))
    }
}

/** S-1103: what one show's notification says: how many new episodes, and the titles of the newest. */
@Immutable
data class FeedNotice(val feedId: Long, val feedTitle: String, val count: Int, val titles: List<String>)

/** S-1103: the notifications of a refresh: one per show with news, and a summary over them when there are several shows. */
@Immutable
data class NoticePlan(val feeds: List<FeedNotice>) {
    val total: Int get() = feeds.sumOf { it.count }
    val summary: Boolean get() = feeds.size >= 2
    val isEmpty: Boolean get() = feeds.isEmpty()

    companion object {
        /** The newest titles one notification lists. */
        const val MAX_TITLES = 5

        /**
         * The plan for the news of a refresh ([news]: each show with its fresh episodes). Nothing when [enabled] (Settings ›
         * Podcasts) is off; a show whose notifications are off is left out; shows are in order of title.
         */
        fun of(news: List<Pair<PodcastFeed, List<PodcastEpisode>>>, enabled: Boolean): NoticePlan {
            if (!enabled) return NoticePlan(emptyList())
            return NoticePlan(
                news.filter { (feed, episodes) -> feed.notify && episodes.isNotEmpty() }
                    .map { (feed, episodes) -> FeedNotice(feed.id, feed.title, episodes.size, episodes.sortedByDescending { it.publishedAt }.take(MAX_TITLES).map { it.title }) }
                    .sortedBy { it.feedTitle.lowercase() },
            )
        }
    }
}
