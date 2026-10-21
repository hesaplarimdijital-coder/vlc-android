package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1104: what follows the playing episode asks of the player and the database, look by look. */
class EpisodeTrackerTest {

    private val tracker = EpisodeTracker()
    private val length = 600_000L
    private var now = 1_000_000L

    private fun episode(
        speed: Float = 1f,
        intro: Int = 0,
        outro: Int = 0,
        position: Long = 0,
        played: Boolean = false,
        id: Long = 7,
        feed: Long = 3,
    ) = EpisodeContext(id, feed, speed, intro, outro, position, played, length)

    /** A look [afterMs] after the last one. */
    private fun look(
        context: EpisodeContext? = episode(),
        time: Long = 0,
        playing: Boolean = true,
        rate: Float = 1f,
        location: String? = "https://cdn.example.org/a.mp3",
        afterMs: Long = 1_000,
        lengthMs: Long = length,
    ): List<TrackerCommand> {
        now += afterMs
        return tracker.sample(location, context, time, lengthMs, playing, rate, now)
    }

    @Test
    fun theShowsSpeedIsSetOnceTheEpisodeHasStarted() {
        assertEquals(listOf(TrackerCommand.SetRate(1.5f)), look(episode(speed = 1.5f), time = 800))
        assertTrue(look(episode(speed = 1.5f), time = 1_800, rate = 1.5f).isEmpty())
    }

    @Test
    fun noCommandWhenTheSpeedIsAlreadyRight() {
        assertTrue(look(episode(speed = 1f), time = 500).isEmpty())
    }

    @Test
    fun aMediaThatIsNoEpisodeIsLeftAlone() {
        repeat(5) { assertTrue(look(context = null, time = it * 1_000L).isEmpty()) }
        assertTrue(tracker.end().isEmpty())
    }

    @Test
    fun anEpisodeStartedFromTheBeginningSkipsItsIntro() {
        assertEquals(listOf(TrackerCommand.SeekTo(20_000)), look(episode(intro = 20), time = 300))
    }

    @Test
    fun anEpisodeAlreadyStartedAfterTheIntroIsNotSoughtAgain() {
        assertTrue(look(episode(intro = 20), time = 20_100).isEmpty())
    }

    @Test
    fun anEpisodeStartedFromTheBeginningFindsItsPlace() {
        assertEquals(listOf(TrackerCommand.SeekTo(297_000)), look(episode(position = 300_000), time = 200))
    }

    @Test
    fun theContextArrivingLaterStillApplies() {
        assertTrue(look(context = null, time = 100).isEmpty())
        assertEquals(listOf(TrackerCommand.SetRate(2f)), look(episode(speed = 2f), time = 1_100))
    }

    @Test
    fun thePlaceIsSavedEveryFewSeconds() {
        look(episode(), time = 100)
        assertTrue(look(time = 1_100, afterMs = 1_000).isEmpty())
        assertTrue(look(time = 4_100, afterMs = 3_000).isEmpty())
        assertEquals(listOf(TrackerCommand.SavePosition(7, 6_100, false)), look(time = 6_100, afterMs = 2_000))
        assertTrue(look(time = 7_100, afterMs = 1_000).isEmpty())
    }

    @Test
    fun thePlaceIsSavedWhenItPauses() {
        look(episode(), time = 100)
        look(time = 2_100)
        assertEquals(listOf(TrackerCommand.SavePosition(7, 2_100, false)), look(time = 2_100, playing = false))
        assertTrue(look(time = 2_100, playing = false).isEmpty())
    }

    @Test
    fun thePlaceIsSavedWhenAnotherMediaStarts() {
        look(episode(), time = 100)
        look(time = 90_100)
        val commands = look(context = episode(id = 8), time = 300, location = "https://cdn.example.org/b.mp3")
        assertEquals(listOf(TrackerCommand.SavePosition(7, 90_100, false)), commands)
    }

    @Test
    fun thePlaceIsSavedWhenTheServiceGoes() {
        look(episode(), time = 100)
        look(time = 45_100)
        assertEquals(listOf(TrackerCommand.SavePosition(7, 45_100, false)), tracker.end())
        assertTrue(tracker.end().isEmpty())
    }

    @Test
    fun anEpisodeThatWasNeverStartedSavesNothingWhenLeft() {
        look(context = null, time = 0)
        assertTrue(look(context = null, location = "https://cdn.example.org/b.mp3").isEmpty())
    }

    @Test
    fun theLastFiveShareMarksItPlayedOnce() {
        look(episode(), time = 100)
        look(time = 500_000)
        assertEquals(listOf(TrackerCommand.SavePosition(7, 570_000, true)), look(time = 570_000))
        // nothing more for the same episode until the next save is due
        assertTrue(look(time = 571_000).isEmpty())
    }

    @Test
    fun leavingNearTheEndMarksItPlayed() {
        look(episode(), time = 100)
        look(time = 560_000, afterMs = 1_000)
        assertEquals(listOf(TrackerCommand.SavePosition(7, 560_000, false)), look(time = 560_000, playing = false))
        look(time = 575_000, playing = true)
        val commands = look(context = episode(id = 8), time = 100, location = "https://cdn.example.org/b.mp3")
        // it was already marked played at 575 s; leaving only keeps the place
        assertEquals(listOf(TrackerCommand.SavePosition(7, 575_000, false)), commands)
    }

    @Test
    fun theOutroFinishesTheEpisode() {
        look(episode(outro = 45), time = 100)
        look(episode(outro = 45), time = 400_000)
        val commands = look(episode(outro = 45), time = 556_000)
        assertEquals(listOf(TrackerCommand.SavePosition(7, 556_000, true), TrackerCommand.Finish), commands)
        // once: the service moves on, a second look at the same place does not ask again
        assertTrue(look(episode(outro = 45), time = 557_000).none { it == TrackerCommand.Finish })
    }

    @Test
    fun theOutroIsNotActedOnWhilePaused() {
        look(episode(outro = 45), time = 100)
        assertTrue(look(episode(outro = 45), time = 560_000, playing = false).none { it == TrackerCommand.Finish })
    }

    @Test
    fun aSpeedTheListenerSetsBecomesTheShows() {
        look(episode(speed = 1.5f), time = 100)
        look(time = 20_000, rate = 1.5f, afterMs = 20_000)
        assertEquals(listOf(TrackerCommand.SaveSpeed(3, 2f)), look(time = 21_000, rate = 2f))
        assertTrue(look(time = 22_000, rate = 2f).isEmpty())
    }

    @Test
    fun theReturnToNormalSpeedAtTheStartIsThePlayersAndIsUndone() {
        look(episode(speed = 1.5f), time = 100, rate = 1f)
        assertEquals(listOf(TrackerCommand.SetRate(1.5f)), look(time = 1_100, rate = 1f))
    }

    @Test
    fun theReturnToNormalSpeedLaterIsTheListeners() {
        look(episode(speed = 1.5f), time = 100, rate = 1f)
        look(time = 1_100, rate = 1.5f)
        assertEquals(listOf(TrackerCommand.SaveSpeed(3, 1f)), look(time = 30_000, rate = 1f, afterMs = 29_000).filterIsInstance<TrackerCommand.SaveSpeed>())
    }

    @Test
    fun theReturnToNormalIsUndoneThreeTimesAtMost() {
        look(episode(speed = 1.5f), time = 100, rate = 1f)
        var undone = 0
        repeat(6) { if (look(time = 1_000L + it, rate = 1f, afterMs = 500).isNotEmpty()) undone++ }
        // three undone, then it is taken as the listener's
        assertEquals(4, undone)
    }

    @Test
    fun aResetToZeroAfterTheEndIsNotAPlace() {
        look(episode(), time = 100)
        look(time = 590_000)
        assertTrue(look(time = 0, playing = false).isEmpty())
        val commands = look(context = episode(id = 8), time = 100, location = "https://cdn.example.org/b.mp3")
        assertEquals(1, commands.count { it is TrackerCommand.SavePosition })
        assertEquals(590_000L, (commands.single { it is TrackerCommand.SavePosition } as TrackerCommand.SavePosition).positionMs)
    }

    @Test
    fun nothingHappensBeforeTheLengthIsKnown() {
        assertTrue(look(episode(speed = 2f, intro = 30), time = 0, lengthMs = 0).isEmpty())
        assertEquals(listOf(TrackerCommand.SetRate(2f), TrackerCommand.SeekTo(30_000)), look(episode(speed = 2f, intro = 30), time = 100))
    }
}
