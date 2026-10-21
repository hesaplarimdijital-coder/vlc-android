package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1104: where an episode starts and when it is played. */
class PlaybackRulesTest {

    private val hour = 3_600_000L

    @Test
    fun anEpisodeNeverPlayedStartsAfterTheIntro() {
        assertEquals(0L, PlaybackRules.startMs(0, played = false, durationMs = hour, introSeconds = 0, outroSeconds = 0))
        assertEquals(20_000L, PlaybackRules.startMs(0, played = false, durationMs = hour, introSeconds = 20, outroSeconds = 0))
    }

    @Test
    fun aPlaceIsResumedAFewSecondsEarlier() {
        assertEquals(597_000L, PlaybackRules.startMs(600_000, played = false, durationMs = hour, introSeconds = 20, outroSeconds = 0))
    }

    @Test
    fun aPlaceNearTheStartIsNoPlace() {
        assertEquals(20_000L, PlaybackRules.startMs(4_000, played = false, durationMs = hour, introSeconds = 20, outroSeconds = 0))
    }

    @Test
    fun aPlaceBeforeTheIntroEndsGoesToTheIntrosEnd() {
        assertEquals(20_000L, PlaybackRules.startMs(10_000, played = false, durationMs = hour, introSeconds = 20, outroSeconds = 0))
    }

    @Test
    fun aPlayedEpisodeStartsAgain() {
        assertEquals(15_000L, PlaybackRules.startMs(1_800_000, played = true, durationMs = hour, introSeconds = 15, outroSeconds = 0))
    }

    @Test
    fun anEpisodeLeftInItsLastStretchStartsAgain() {
        assertEquals(0L, PlaybackRules.startMs(hour - 20_000, played = false, durationMs = hour, introSeconds = 0, outroSeconds = 0))
        // ten minutes: its last 5 % are 30 s, a 60 s outro starts earlier
        assertEquals(547_000L, PlaybackRules.startMs(550_000, played = false, durationMs = 600_000, introSeconds = 0, outroSeconds = 0))
        assertEquals(0L, PlaybackRules.startMs(550_000, played = false, durationMs = 600_000, introSeconds = 0, outroSeconds = 60))
    }

    @Test
    fun anIntroLongerThanHalfTheEpisodeIsNotSkipped() {
        assertEquals(0L, PlaybackRules.startMs(0, played = false, durationMs = 100_000, introSeconds = 90, outroSeconds = 0))
    }

    @Test
    fun theLastFiveShareIsPlayed() {
        assertFalse(PlaybackRules.isPlayed(94 * hour / 100, hour, 0))
        assertTrue(PlaybackRules.isPlayed(95 * hour / 100, hour, 0))
        assertTrue(PlaybackRules.isPlayed(hour, hour, 0))
        assertFalse(PlaybackRules.isPlayed(10_000, 0, 0))
    }

    @Test
    fun theOutroIsPlayedToo() {
        // ten minutes: its last 5 % are 30 s, a 60 s outro starts earlier
        assertFalse(PlaybackRules.isPlayed(530_000, 600_000, 60))
        assertTrue(PlaybackRules.isPlayed(540_000, 600_000, 60))
        assertFalse(PlaybackRules.isPlayed(540_000, 600_000, 0))
    }

    @Test
    fun theOutroBeginsItsLengthBeforeTheEnd() {
        assertEquals(hour - 45_000, PlaybackRules.outroStartMs(hour, 45))
        assertNull(PlaybackRules.outroStartMs(hour, 0))
        assertNull(PlaybackRules.outroStartMs(0, 45))
        // longer than half the episode: not an outro of this one
        assertNull(PlaybackRules.outroStartMs(60_000, 45))
    }

    @Test
    fun speedsStayBetweenHalfAndThree() {
        assertEquals(0.5f, PlaybackRules.clampSpeed(0.1f), 0f)
        assertEquals(3f, PlaybackRules.clampSpeed(5f), 0f)
        assertEquals(1.25f, PlaybackRules.clampSpeed(1.25f), 0f)
        assertEquals(1.25f, PlaybackRules.fasterThan(1f), 0f)
        assertEquals(3f, PlaybackRules.fasterThan(3f), 0f)
        assertEquals(0.75f, PlaybackRules.slowerThan(1f), 0f)
        assertEquals(0.5f, PlaybackRules.slowerThan(0.5f), 0f)
        assertTrue(PlaybackRules.sameSpeed(1.0f, 1.0004f))
    }
}
