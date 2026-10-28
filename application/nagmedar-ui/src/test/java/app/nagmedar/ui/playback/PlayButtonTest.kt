package app.nagmedar.ui.playback

import app.nagmedar.ui.podcast.Listening
import org.junit.Assert.assertEquals
import org.junit.Test

/** S-205: the one rule of an episode's play button and of the place its page shows. */
class PlayButtonTest {

    private val length = 37 * 60_000L + 4_000

    private fun button(savedMs: Long, played: Boolean = false, now: Listening? = null, starting: Boolean = false) =
        PlaybackRules.playButton(savedMs, played, now, starting)

    @Test
    fun nothingSavedPlays() {
        assertEquals(PlayButton.Play, button(0))
        assertEquals(PlayButton.Play, button(3_000))
    }

    @Test
    fun aSavedPlaceResumesThere() {
        assertEquals(PlayButton.Resume(43_000), button(43_000))
    }

    @Test
    fun theSameSavedPlaceWhenThePlayerHasTheEpisodeLoadedButNotStarted() {
        // the playlist brought back after a restart: the player says 0 until it plays
        assertEquals(PlayButton.Resume(43_000), button(43_000, now = Listening(0, length, playing = false)))
        assertEquals(PlayButton.Resume(43_000), button(43_000, now = null))
    }

    @Test
    fun aPausedEpisodeResumesWhereThePlayerHasIt() {
        assertEquals(PlayButton.Resume(600_000), button(43_000, now = Listening(600_000, length, playing = false)))
    }

    @Test
    fun aPlayingEpisodePauses() {
        assertEquals(PlayButton.Pause, button(43_000, now = Listening(50_000, length, playing = true)))
    }

    @Test
    fun anEpisodeBeingOpenedWaitsAsAPauseWhateverThePlayerSays() {
        assertEquals(PlayButton.Starting, button(43_000, now = null, starting = true))
        assertEquals(PlayButton.Starting, button(43_000, now = Listening(0, 0, playing = false), starting = true))
        assertEquals(PlayButton.Starting, button(43_000, now = Listening(0, length, playing = true), starting = true))
    }

    @Test
    fun aPlayedEpisodePlaysAgainUnlessThePlayerIsInIt() {
        assertEquals(PlayButton.Play, button(2_200_000, played = true))
        assertEquals(PlayButton.Play, button(2_200_000, played = true, now = Listening(0, length, playing = false)))
        assertEquals(PlayButton.Resume(120_000), button(2_200_000, played = true, now = Listening(120_000, length, playing = false)))
    }

    @Test
    fun aPlaceInTheLastSecondsPlaysAgain() {
        assertEquals(PlayButton.Play, PlaybackRules.playButton(length - 3_000, played = false, now = null, durationMs = length))
        assertEquals(PlayButton.Play, PlaybackRules.playButton(length - 5_000, played = false, now = null, durationMs = length))
        assertEquals(PlayButton.Play, PlaybackRules.playButton(length - 3_000, played = false, now = Listening(0, length, playing = false), durationMs = length))
        assertEquals(PlayButton.Resume(length - 6_000), PlaybackRules.playButton(length - 6_000, played = false, now = null, durationMs = length))
        // without a known length the place is resumed as it is
        assertEquals(PlayButton.Resume(length - 3_000), button(length - 3_000))
        assertEquals(0L, PlaybackRules.shownPositionMs(length - 2_000, played = false, now = null, durationMs = length))
        assertEquals(length - 6_000, PlaybackRules.shownPositionMs(length - 6_000, played = false, now = null, durationMs = length))
        // the player in the episode: its place, wherever that is
        assertEquals(PlayButton.Resume(length - 3_000), PlaybackRules.playButton(43_000, played = false, now = Listening(length - 3_000, length, playing = false), durationMs = length))
    }

    @Test
    fun theTimelineStartsAtTheSavedPlaceUntilThePlayerIsThere() {
        assertEquals(43_000L, PlaybackRules.shownPositionMs(43_000, played = false, now = null))
        assertEquals(43_000L, PlaybackRules.shownPositionMs(43_000, played = false, now = Listening(0, length, playing = false)))
        // opening: the player says 0 a moment, the timeline stays at the place
        assertEquals(43_000L, PlaybackRules.shownPositionMs(43_000, played = false, now = Listening(0, length, playing = true), starting = true))
        assertEquals(41_000L, PlaybackRules.shownPositionMs(43_000, played = false, now = Listening(41_000, length, playing = true)))
        // played from the start on purpose: the player's place
        assertEquals(2_000L, PlaybackRules.shownPositionMs(43_000, played = false, now = Listening(2_000, length, playing = true)))
        assertEquals(0L, PlaybackRules.shownPositionMs(2_200_000, played = true, now = null))
    }
}
