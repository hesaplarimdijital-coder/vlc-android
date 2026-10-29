package app.nagmedar.data

import kotlin.math.max
import kotlin.math.min

/** S-1502: what a restore makes of the rows here and a backup's, so that nothing is lost. */
object RestoreMerge {
    /**
     * A track's statistics: the more plays and skips (a backup restored twice counts once), the
     * earlier first play and the later last one; a rating given here stays, else the backup's.
     */
    fun stats(here: TrackStatsEntry?, kept: TrackStatsEntry): TrackStatsEntry {
        if (here == null) return kept
        return TrackStatsEntry(
            trackKey = here.trackKey,
            rating = if (here.rating > 0) here.rating else kept.rating,
            playCount = max(here.playCount, kept.playCount),
            skipCount = max(here.skipCount, kept.skipCount),
            firstPlayedAt = earliest(here.firstPlayedAt, kept.firstPlayedAt),
            lastPlayedAt = max(here.lastPlayedAt, kept.lastPlayedAt),
            updatedAt = max(here.updatedAt, kept.updatedAt),
        )
    }

    /** The earlier of two times, 0 being none. */
    private fun earliest(a: Long, b: Long): Long = when {
        a <= 0L -> b
        b <= 0L -> a
        else -> min(a, b)
    }
}
