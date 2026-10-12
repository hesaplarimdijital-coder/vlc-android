package app.nagmedar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-1502: a restore joins a backup's rows to these without losing either's. */
class RestoreMergeTest {

    private val here = TrackStatsEntry("k", rating = 0, playCount = 3, skipCount = 4, firstPlayedAt = 300, lastPlayedAt = 900, updatedAt = 900)
    private val kept = TrackStatsEntry("k", rating = 4, playCount = 7, skipCount = 1, firstPlayedAt = 100, lastPlayedAt = 500, updatedAt = 500)

    @Test
    fun statisticsAreMerged() {
        assertEquals(TrackStatsEntry("k", 4, 7, 4, 100, 900, 900), RestoreMerge.stats(here, kept))
        // a rating given here stays
        assertEquals(2, RestoreMerge.stats(here.copy(rating = 2), kept).rating)
        // restored twice, counted once
        val once = RestoreMerge.stats(here, kept)
        assertEquals(once, RestoreMerge.stats(once, kept))
        // a first play known on one side only
        assertEquals(100L, RestoreMerge.stats(here.copy(firstPlayedAt = 0), kept).firstPlayedAt)
        assertEquals(300L, RestoreMerge.stats(here, kept.copy(firstPlayedAt = 0)).firstPlayedAt)
        // nothing here: the backup's as it is
        assertEquals(kept, RestoreMerge.stats(null, kept))
    }

    @Test
    fun aFilesSettingsAreTheNewerOnes() {
        val mine = MediaSettings("file:///film.mkv", audioDelayMs = 50, updatedAt = 10)
        val backup = MediaSettings("file:///film.mkv", audioDelayMs = -120, updatedAt = 5)
        assertNull(RestoreMerge.mediaSettings(mine, backup))
        assertEquals(backup.copy(updatedAt = 10), RestoreMerge.mediaSettings(mine, backup.copy(updatedAt = 10)))
        assertEquals(backup, RestoreMerge.mediaSettings(null, backup))
    }
}
