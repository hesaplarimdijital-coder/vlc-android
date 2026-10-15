package app.nagmedar.ui.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-1008: Last.fm's rule: longer than 30 s, half of it or four minutes heard; "now playing" when a track starts. */
class ScrobbleTrackerTest {

    private val events = mutableListOf<String>()
    private var now = 1_800_000_000_000L
    private val tracker = ScrobbleTracker(
        onStarted = { events += "now ${it.title}" },
        onScrobble = { track, startedAt -> events += "scrobble ${track.title} @$startedAt" },
        clock = { now },
    )

    private fun track(title: String) = ScrobbleTrack("file:///m/$title.flac", "Ada Karan", title, "Gece Mavisi", null, 180)

    /** [title] played from [from] to [to] ms, a sample a second, the clock with it. */
    private fun play(title: String, length: Long, from: Long, to: Long, playing: Boolean = true) {
        var time = from
        while (time <= to) {
            tracker.sample(track(title), time, length, playing)
            time += 1_000
            now += 1_000
        }
    }

    @Test
    fun halfOfATrackIsScrobbledOnceAndItsStartIsToldFirst() {
        play("a", 180_000, 0, 88_000)
        assertEquals(listOf("now a"), events)
        play("a", 180_000, 89_000, 180_000)
        assertEquals(2, events.size)
        assertEquals("now a", events[0])
        assertEquals("scrobble a @1800000000", events[1])
    }

    @Test
    fun fourMinutesOfALongTrackAreEnough() {
        play("long", 20 * 60_000L, 0, 239_000)
        assertEquals(listOf("now long"), events)
        play("long", 20 * 60_000L, 240_000, 241_000)
        assertEquals(2, events.size)
    }

    @Test
    fun aTrackOfThirtySecondsOrLessIsNeitherToldNorScrobbled() {
        play("short", 30_000, 0, 30_000)
        assertEquals(emptyList<String>(), events)
        // a little longer is
        play("longer", 31_000, 0, 16_000)
        assertEquals(2, events.size)
        assertEquals("now longer", events[0])
    }

    @Test
    fun leftForAnotherTrackBeforeHalfIsNotScrobbled() {
        play("a", 180_000, 0, 60_000)
        play("b", 180_000, 0, 5_000)
        assertEquals(listOf("now a", "now b"), events)
    }

    @Test
    fun aPauseIsNotListening() {
        play("a", 180_000, 0, 40_000)
        // paused for ten minutes: the position stays
        repeat(600) {
            tracker.sample(track("a"), 40_000, 180_000, playing = false)
            now += 1_000
        }
        play("a", 180_000, 41_000, 80_000)
        assertEquals(listOf("now a"), events)
        play("a", 180_000, 81_000, 90_000)
        assertEquals(2, events.size)
    }

    @Test
    fun aSeekIsNotListening() {
        play("a", 180_000, 0, 10_000)
        // to the end: the rest of the track is not heard
        play("a", 180_000, 170_000, 179_000)
        assertEquals(listOf("now a"), events)
    }

    @Test
    fun aTrackThatStartsAgainIsAnotherPlayWhetherOrNotTheFirstCounted() {
        play("a", 100_000, 0, 50_000)
        assertEquals(listOf("now a", "scrobble a @1800000000"), events)
        // repeat one: from the beginning again
        events.clear()
        play("a", 100_000, 0, 50_000)
        assertEquals(2, events.size)
        assertEquals("now a", events[0])
        assertEquals("scrobble a @1800000051", events[1])
        // not counted yet and back to the start: a play of its own, the first part is not added
        events.clear()
        tracker.sample(track("b"), 0, 100_000, true)
        play("b", 100_000, 1_000, 30_000)
        play("b", 100_000, 0, 30_000)
        assertEquals(listOf("now b", "now b"), events)
    }

    @Test
    fun nothingIsToldWhilePausedAtTheStartOrBeforeTheLengthIsKnown() {
        tracker.sample(track("a"), 0, 180_000, playing = false)
        assertEquals(emptyList<String>(), events)
        tracker.sample(track("a"), 1_000, 180_000, playing = true)
        assertEquals(listOf("now a"), events)
        // the length is not known yet
        tracker.sample(track("b"), 0, 0, playing = true)
        tracker.sample(track("b"), 1_000, 0, playing = true)
        assertEquals(listOf("now a"), events)
        tracker.sample(track("b"), 2_000, 180_000, playing = true)
        assertEquals(listOf("now a", "now b"), events)
    }

    @Test
    fun whatIsNotScrobbledIsLeftAlone() {
        play("a", 180_000, 0, 20_000)
        tracker.sample(null, 0, 0, playing = false)
        assertEquals(listOf("now a"), events)
        // and the same track afterwards is a new play
        play("a", 180_000, 0, 5_000)
        assertEquals(listOf("now a", "now a"), events)
    }

    @Test
    fun theStartIsWhenTheTrackBeganNotWhenItCounted() {
        now = 1_800_000_500_000L
        // resumed 100 s into the track
        play("a", 400_000, 100_000, 300_000)
        assertEquals("scrobble a @1800000400", events.last())
    }

    @Test
    fun aTrackIsBuiltOfTheTagsOrNot() {
        val built = ScrobbleTrack.of("file:///m/a.flac", " Ada Karan ", " Sabaha Kadar ", "", " Ada ", 241_600)!!
        assertEquals("Ada Karan", built.artist)
        assertEquals("Sabaha Kadar", built.title)
        assertNull(built.album)
        assertEquals("Ada", built.albumArtist)
        assertEquals(242, built.durationSec)
        assertNull(ScrobbleTrack.of("file:///m/a.flac", null, "Title", null, null, 1_000))
        assertNull(ScrobbleTrack.of("file:///m/a.flac", "Artist", "  ", null, null, 1_000))
    }
}
