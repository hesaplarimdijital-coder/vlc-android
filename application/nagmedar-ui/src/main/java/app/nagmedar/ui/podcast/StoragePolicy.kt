package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable

/** S-1103: a downloaded episode as the storage rules see it. */
@Immutable
data class StoredEpisode(
    val id: Long,
    val feedId: Long,
    val bytes: Long,
    val publishedAt: Long,
    val played: Boolean,
    /** When it was marked played, 0 if not or unknown. */
    val playedAt: Long,
)

/** S-1103: why a downloaded episode is taken away. */
enum class CleanupReason {
    /** Played more days ago than Settings › Podcasts keeps played episodes. */
    PlayedLongAgo,

    /** Older than the newest ones its show keeps. */
    BeyondKeepLatest,

    /** The downloads together are over the storage limit. */
    OverLimit,
}

/** S-1103: one download to take away. */
@Immutable
data class Removal(val episodeId: Long, val reason: CleanupReason, val bytes: Long)

/**
 * S-1103: what a cleanup does: the downloads to remove, the bytes held before and after, and whether the limit
 * still stands broken after it (everything left is being played or downloaded just now).
 */
@Immutable
data class CleanupPlan(
    val removals: List<Removal>,
    val usedBytes: Long,
    val limitBytes: Long,
    val overLimit: Boolean,
) {
    val freedBytes: Long get() = removals.sumOf { it.bytes }
    val keptBytes: Long get() = usedBytes - freedBytes

    /** The downloads left fill nine tenths of the limit or more: the warning of Settings › Podcasts. */
    val nearLimit: Boolean get() = limitBytes > 0 && keptBytes * 10 >= limitBytes * 9
}

/**
 * S-1103: the storage rules of downloaded episodes, apart from the files. In this order: played episodes older than
 * the days Settings › Podcasts keeps them; each show's episodes beyond the newest N it keeps; then, while the downloads
 * together are over the limit, played episodes (the oldest first), then unplayed ones (the oldest first). What
 * [protected] names (playing now, being downloaded) is never taken. The plan is only a list: [StorageCleaner] does it.
 */
object StoragePolicy {
    private const val DAY_MS = 86_400_000L

    /**
     * The plan for [stored]. [keepLatest] is each show's N (missing or 0: all kept); [limitBytes] 0 means no limit;
     * [playedAfterDays] 0 means played episodes stay.
     */
    fun plan(
        stored: List<StoredEpisode>,
        keepLatest: Map<Long, Int>,
        limitBytes: Long,
        playedAfterDays: Int,
        now: Long,
        protected: Set<Long> = emptySet(),
    ): CleanupPlan {
        val used = stored.sumOf { it.bytes }
        val removals = LinkedHashMap<Long, Removal>()
        fun remove(episode: StoredEpisode, reason: CleanupReason) {
            if (episode.id !in protected && episode.id !in removals) removals[episode.id] = Removal(episode.id, reason, episode.bytes)
        }
        if (playedAfterDays > 0) {
            stored.filter { it.played && it.playedAt > 0 && now - it.playedAt >= playedAfterDays * DAY_MS }.forEach { remove(it, CleanupReason.PlayedLongAgo) }
        }
        stored.groupBy { it.feedId }.forEach { (feedId, episodes) ->
            val keep = keepLatest[feedId] ?: 0
            if (keep > 0) episodes.sortedByDescending { it.publishedAt }.drop(keep).forEach { remove(it, CleanupReason.BeyondKeepLatest) }
        }
        var kept = used - removals.values.sumOf { it.bytes }
        if (limitBytes > 0 && kept > limitBytes) {
            val order = stored.filter { it.id !in removals && it.id !in protected }
                .sortedWith(compareBy<StoredEpisode> { !it.played }.thenBy { it.publishedAt })
            for (episode in order) {
                if (kept <= limitBytes) break
                remove(episode, CleanupReason.OverLimit)
                kept -= episode.bytes
            }
        }
        return CleanupPlan(removals.values.toList(), used, limitBytes, overLimit = limitBytes > 0 && kept > limitBytes)
    }
}
