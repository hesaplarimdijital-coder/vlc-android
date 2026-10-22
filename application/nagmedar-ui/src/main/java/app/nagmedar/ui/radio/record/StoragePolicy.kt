package app.nagmedar.ui.radio.record

/** What recording does at the space limit. */
enum class LimitAction {
    /** Stops, and says so. */
    Stop,

    /** Deletes the oldest recordings to make room. */
    PruneOldest,
}

/**
 * S-1107: how much room recording may take. [minFreeBytes] is what the device must keep free;
 * [maxTotalBytes] is how much all recordings together may take (0: no limit); [action] is what
 * happens at a limit.
 */
data class StorageLimits(
    val minFreeBytes: Long = DEFAULT_MIN_FREE,
    val maxTotalBytes: Long = DEFAULT_MAX_TOTAL,
    val action: LimitAction = LimitAction.Stop,
) {
    companion object {
        /** Sizes are counted in powers of 1000, as the rest of the app and the system's storage screens count them. */
        const val MB = 1_000_000L
        const val GB = 1_000L * MB
        const val DEFAULT_MIN_FREE = 500 * MB
        const val DEFAULT_MAX_TOTAL = 10 * GB

        /** Offered for the room the device keeps free, in MB. */
        val MIN_FREE_MB = listOf(100, 250, 500, 1000, 2000)

        /** Offered for the room all recordings may take, in GB (0: no limit). */
        val MAX_TOTAL_GB = listOf(1, 2, 5, 10, 20, 50, 0)
    }
}

/** Why room is short, for the banner and the notification. */
enum class SpaceIssue { LowSpace, LimitReached }

/** S-1107: what the room left says about recording. */
sealed interface StorageVerdict {
    /** Plenty of room. */
    data object Ok : StorageVerdict

    /** Room is running short; recording goes on. */
    data class Warn(val issue: SpaceIssue) : StorageVerdict

    /** At the limit, and [StorageLimits.action] is to stop. */
    data class Stop(val issue: SpaceIssue) : StorageVerdict

    /** At the limit, and [StorageLimits.action] is to delete the oldest: [bytes] have to go. */
    data class Prune(val issue: SpaceIssue, val bytes: Long) : StorageVerdict
}

/**
 * S-1107: the storage limit rules. The device must keep [StorageLimits.minFreeBytes] free and all
 * recordings together may take [StorageLimits.maxTotalBytes]. A warning comes first: when free
 * space is under twice the minimum, or recordings take 80 % of the limit. Pruning goes below the
 * limit by a tenth, so that it does not run again at the next track.
 */
object StoragePolicy {

    /** Recordings take this share of the limit before the warning. */
    private const val WARN_SHARE = 0.8

    /** Pruning leaves recordings at this share of the limit. */
    private const val PRUNE_TO = 0.9

    /**
     * The verdict for [freeBytes] left on the device (negative: not known) and [usedBytes] taken by
     * recordings. Space beats the total when both are short.
     */
    fun assess(freeBytes: Long, usedBytes: Long, limits: StorageLimits): StorageVerdict {
        val known = freeBytes >= 0
        val max = limits.maxTotalBytes
        if (known && freeBytes <= limits.minFreeBytes) return atLimit(SpaceIssue.LowSpace, limits.minFreeBytes - freeBytes + limits.minFreeBytes / 4, limits)
        if (max > 0 && usedBytes >= max) return atLimit(SpaceIssue.LimitReached, usedBytes - (max * PRUNE_TO).toLong(), limits)
        if (known && freeBytes < limits.minFreeBytes * 2) return StorageVerdict.Warn(SpaceIssue.LowSpace)
        if (max > 0 && usedBytes >= (max * WARN_SHARE).toLong()) return StorageVerdict.Warn(SpaceIssue.LimitReached)
        return StorageVerdict.Ok
    }

    private fun atLimit(issue: SpaceIssue, bytes: Long, limits: StorageLimits): StorageVerdict =
        if (limits.action == LimitAction.PruneOldest) StorageVerdict.Prune(issue, bytes.coerceAtLeast(1)) else StorageVerdict.Stop(issue)

    /**
     * The tracks to delete (the oldest first, by [PruneCandidate.at]) to free [bytes]; fewer when
     * there are not enough. Tracks of a session still recording are never candidates.
     */
    fun prune(candidates: List<PruneCandidate>, bytes: Long, keepSession: Set<String> = emptySet()): List<PruneCandidate> {
        var freed = 0L
        val chosen = ArrayList<PruneCandidate>()
        for (c in candidates.filter { it.sessionId !in keepSession }.sortedBy { it.at }) {
            if (freed >= bytes) break
            chosen += c
            freed += c.sizeBytes
        }
        return chosen
    }
}

/** A recorded track as pruning sees it. */
data class PruneCandidate(val id: Long, val sessionId: String, val at: Long, val sizeBytes: Long)
