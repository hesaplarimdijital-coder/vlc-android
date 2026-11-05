package app.nagmedar.library

import app.nagmedar.library.MediaStamp.VolumeCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.0.3: an ordinary start looks over the music folders only when the media index changed or the last look is old. */
class LibraryRecheckTest {

    private val day = LibraryRecheck.MAX_AGE_MS
    private val stamp = MediaStamp.of(listOf(VolumeCount("external_primary", 640, 9_120)))

    @Test
    fun anUnchangedIndexLookedAtTodayIsTrusted() {
        assertFalse(LibraryRecheck.needed(stamp, stamp, lastAt = 1_000_000, now = 1_000_000 + day / 2))
    }

    @Test
    fun aNewOrRemovedFileMovesTheStamp() {
        val added = MediaStamp.of(listOf(VolumeCount("external_primary", 641, 9_133)))
        val removed = MediaStamp.of(listOf(VolumeCount("external_primary", 639, 9_120)))
        val retagged = MediaStamp.of(listOf(VolumeCount("external_primary", 640, 9_140)))
        for (now in listOf(added, removed, retagged)) {
            assertTrue(LibraryRecheck.needed(now, stamp, lastAt = 1_000_000, now = 1_000_100))
        }
    }

    @Test
    fun anOldLookIsMadeAgain() {
        assertTrue(LibraryRecheck.needed(stamp, stamp, lastAt = 1_000_000, now = 1_000_000 + day))
    }

    @Test
    fun noFinishedLookOrNoIndexMeansALook() {
        assertTrue(LibraryRecheck.needed(stamp, null, lastAt = 0, now = 5))
        assertTrue(LibraryRecheck.needed(stamp, stamp, lastAt = 0, now = 5))
        assertTrue(LibraryRecheck.needed(null, stamp, lastAt = 1_000_000, now = 1_000_100))
    }

    @Test
    fun aClockSetBackCountsAsOld() {
        assertTrue(LibraryRecheck.needed(stamp, stamp, lastAt = 2_000_000, now = 1_000_000))
    }

    @Test
    fun theStampDoesNotDependOnTheVolumesOrder() {
        val sd = VolumeCount("1a2b-3c4d", 12, 40)
        val primary = VolumeCount("external_primary", 640, 9_120)
        assertEquals(MediaStamp.of(listOf(sd, primary)), MediaStamp.of(listOf(primary, sd)))
        assertEquals("1a2b-3c4d:12:40;external_primary:640:9120", MediaStamp.of(listOf(primary, sd)))
    }
}
