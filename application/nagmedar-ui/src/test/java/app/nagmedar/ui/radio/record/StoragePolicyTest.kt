package app.nagmedar.ui.radio.record

import app.nagmedar.ui.radio.record.StorageLimits.Companion.GB
import app.nagmedar.ui.radio.record.StorageLimits.Companion.MB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1107: the storage limit: when to warn, stop or delete the oldest. */
class StoragePolicyTest {

    private val stop = StorageLimits(minFreeBytes = 500 * MB, maxTotalBytes = 10 * GB, action = LimitAction.Stop)
    private val prune = stop.copy(action = LimitAction.PruneOldest)

    @Test
    fun plentyOfRoomIsFine() {
        assertEquals(StorageVerdict.Ok, StoragePolicy.assess(20 * GB, 1 * GB, stop))
    }

    @Test
    fun roomNotKnownLeavesTheTotalToJudge() {
        assertEquals(StorageVerdict.Ok, StoragePolicy.assess(-1, 1 * GB, stop))
        assertEquals(StorageVerdict.Stop(SpaceIssue.LimitReached), StoragePolicy.assess(-1, 10 * GB, stop))
    }

    @Test
    fun underTwiceTheMinimumIsAWarning() {
        assertEquals(StorageVerdict.Warn(SpaceIssue.LowSpace), StoragePolicy.assess(900 * MB, 0, stop))
        assertEquals(StorageVerdict.Ok, StoragePolicy.assess(1001 * MB, 0, stop))
    }

    @Test
    fun eightyPercentOfTheLimitIsAWarning() {
        assertEquals(StorageVerdict.Warn(SpaceIssue.LimitReached), StoragePolicy.assess(50 * GB, 8 * GB + MB, stop))
        assertEquals(StorageVerdict.Ok, StoragePolicy.assess(50 * GB, 7 * GB, stop))
    }

    @Test
    fun atTheMinimumRecordingStops() {
        assertEquals(StorageVerdict.Stop(SpaceIssue.LowSpace), StoragePolicy.assess(500 * MB, 0, stop))
        assertEquals(StorageVerdict.Stop(SpaceIssue.LowSpace), StoragePolicy.assess(10 * MB, 0, stop))
    }

    @Test
    fun atTheTotalRecordingStops() {
        assertEquals(StorageVerdict.Stop(SpaceIssue.LimitReached), StoragePolicy.assess(50 * GB, 10 * GB, stop))
    }

    @Test
    fun noTotalLimitMeansNone() {
        val unlimited = stop.copy(maxTotalBytes = 0)
        assertEquals(StorageVerdict.Ok, StoragePolicy.assess(50 * GB, 900 * GB, unlimited))
    }

    @Test
    fun pruningAsksForWhatIsOverAndALittleMore() {
        val total = StoragePolicy.assess(50 * GB, 11 * GB, prune) as StorageVerdict.Prune
        assertEquals(SpaceIssue.LimitReached, total.issue)
        // down to nine tenths of the limit
        assertEquals(11 * GB - (10 * GB * 0.9).toLong(), total.bytes)
        val space = StoragePolicy.assess(400 * MB, 0, prune) as StorageVerdict.Prune
        assertEquals(SpaceIssue.LowSpace, space.issue)
        assertEquals(100 * MB + 125 * MB, space.bytes)
    }

    @Test
    fun spaceCountsBeforeTheTotal() {
        assertEquals(StorageVerdict.Stop(SpaceIssue.LowSpace), StoragePolicy.assess(100 * MB, 11 * GB, stop))
    }

    @Test
    fun theOldestTracksGoFirstAndTheSessionBeingRecordedStays() {
        val tracks = listOf(
            PruneCandidate(1, "old", 100, 40),
            PruneCandidate(2, "old", 200, 40),
            PruneCandidate(3, "live", 50, 500),
            PruneCandidate(4, "new", 300, 40),
        )
        assertEquals(listOf(1L, 2L), StoragePolicy.prune(tracks, 70, setOf("live")).map { it.id })
        assertEquals(listOf(3L), StoragePolicy.prune(tracks, 70).map { it.id })
        assertEquals(listOf(1L, 2L, 4L), StoragePolicy.prune(tracks, 1_000, setOf("live")).map { it.id })
        assertTrue(StoragePolicy.prune(emptyList(), 10).isEmpty())
    }
}
