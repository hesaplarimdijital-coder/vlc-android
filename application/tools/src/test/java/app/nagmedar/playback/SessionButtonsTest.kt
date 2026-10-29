package app.nagmedar.playback

import app.nagmedar.playback.SessionButton.EpisodeBack
import app.nagmedar.playback.SessionButton.EpisodeForward
import app.nagmedar.playback.SessionButton.Forward
import app.nagmedar.playback.SessionButton.Repeat
import app.nagmedar.playback.SessionButton.Rewind
import app.nagmedar.playback.SessionButton.Shuffle
import app.nagmedar.playback.SessionButton.Speed
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.0: the media session's custom actions by what plays: an episode's 15 s back and 30 s forward, the music's own. */
class SessionButtonsTest {

    @Test
    fun anEpisodeHasItsSkipsEverywhere() {
        val skips = listOf(EpisodeBack, EpisodeForward)
        // Android Auto, the system's controls, an older phone: the same two, whatever Settings says
        assertEquals(skips, SessionButtons.of(episode = true, carMode = true, systemControls = true, seekButtons = true, speedButtons = true))
        assertEquals(skips, SessionButtons.of(episode = true, carMode = false, systemControls = true))
        assertEquals(skips, SessionButtons.of(episode = true, carMode = false, systemControls = false))
    }

    @Test
    fun musicInTheCarShufflesAndRepeats() {
        assertEquals(listOf(Shuffle, Repeat), SessionButtons.of(episode = false, carMode = true, systemControls = true))
        assertEquals(listOf(Shuffle, Repeat, Rewind, Forward, Speed), SessionButtons.of(episode = false, carMode = true, systemControls = true, seekButtons = true, speedButtons = true))
        assertEquals(listOf(Shuffle, Repeat, Speed), SessionButtons.of(episode = false, carMode = true, systemControls = false, speedButtons = true))
    }

    @Test
    fun musicOnThePhonesControlsSeeks() {
        assertEquals(listOf(Rewind, Forward), SessionButtons.of(episode = false, carMode = false, systemControls = true, speedButtons = true))
        // before Android 13 the notification draws its own buttons; the session keeps the car's
        assertEquals(listOf(Shuffle, Repeat), SessionButtons.of(episode = false, carMode = false, systemControls = false))
    }

    @Test
    fun anEpisodesSkipsStayInsideIt() {
        assertEquals(45_000L, SessionButtons.episodeSeek(forward = false, timeMs = 60_000, lengthMs = 600_000))
        assertEquals(0L, SessionButtons.episodeSeek(forward = false, timeMs = 9_000, lengthMs = 600_000))
        assertEquals(90_000L, SessionButtons.episodeSeek(forward = true, timeMs = 60_000, lengthMs = 600_000))
        assertEquals(600_000L, SessionButtons.episodeSeek(forward = true, timeMs = 590_000, lengthMs = 600_000))
        // a length not known yet does not hold it back
        assertEquals(40_000L, SessionButtons.episodeSeek(forward = true, timeMs = 10_000, lengthMs = 0))
    }
}
