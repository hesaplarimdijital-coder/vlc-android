package app.nagmedar.ui.player.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A film's 23,976 frames a second (24000/1001), as libVLC reads them. */
internal const val FILM_FPS = 24000.0 / 1001

/** A TV's modes at 2160p over HDMI. */
internal val tvRates = listOf(23.976, 24.0, 25.0, 29.97, 50.0, 59.94, 60.0)

/**
 * S-612: how a film's frames fall on a screen's refreshes: evenly on a whole multiple, a frame out
 * of step every few seconds just off one, three and two on 60 Hz, frames unseen on a slower
 * screen; the screen's rate that takes a film best.
 */
class RefreshTest {

    @Test
    fun aFilmOnATabletsPanelRepeatsAFrameEveryEightSeconds() {
        val cadence = cadenceOf(FILM_FPS, 120.0) as Cadence.Drift
        assertEquals(8.34, cadence.seconds, 0.01)
        assertFalse(cadence.dropped)
    }

    @Test
    fun aWholeMultipleShowsEveryFrameAlike() {
        // a display's rate is a float: 23,976 Hz is 23.976025
        assertEquals(Cadence.Even(1), cadenceOf(FILM_FPS, 23.976025))
        assertEquals(Cadence.Even(5), cadenceOf(FILM_FPS, 119.88))
        assertEquals(Cadence.Even(2), cadenceOf(25.0, 50.0))
        assertEquals(Cadence.Even(1), cadenceOf(60000.0 / 1001, 59.94006))
        assertEquals(Cadence.Even(4), cadenceOf(30.0, 120.00001))
    }

    @Test
    fun sixtyHertzShowsAFilmThreeAndTwo() {
        val cadence = cadenceOf(FILM_FPS, 60.0) as Cadence.Uneven
        assertEquals(11.93, cadence.perSecond, 0.01)
        assertFalse(cadence.dropped)
        assertEquals(10.0, (cadenceOf(25.0, 60.0) as Cadence.Uneven).perSecond, 0.001)
        assertEquals(5.9, (cadenceOf(FILM_FPS, 90.0) as Cadence.Uneven).perSecond, 0.01)
    }

    @Test
    fun aRateJustOffTheFilmsDriftsSlowly() {
        // a 24 fps film on a TV at 23,976 Hz: a frame unseen every 42 s
        val tv = cadenceOf(24.0, 23.976) as Cadence.Drift
        assertEquals(41.67, tv.seconds, 0.01)
        assertTrue(tv.dropped)
        // 29,97 fps on 60 Hz: a frame twice every 17 s
        val video = cadenceOf(30000.0 / 1001, 60.0) as Cadence.Drift
        assertEquals(16.68, video.seconds, 0.01)
        assertFalse(video.dropped)
    }

    @Test
    fun aSlowerScreenLeavesFramesUnseen() {
        val cadence = cadenceOf(60000.0 / 1001, 50.0) as Cadence.Uneven
        assertEquals(9.94, cadence.perSecond, 0.01)
        assertTrue(cadence.dropped)
        assertEquals(36.0, (cadenceOf(60.0, 24.0) as Cadence.Uneven).perSecond, 0.001)
    }

    @Test
    fun unknownRatesTellNothing() {
        assertNull(cadenceOf(0.0, 60.0))
        assertNull(cadenceOf(FILM_FPS, 0.0))
        assertNull(cadenceOf(Double.NaN, 60.0))
        assertNull(RefreshStatus().cadence)
    }

    @Test
    fun theBestRateTakesTheFilmEvenlyAtItsLowest() {
        assertEquals(23.976, bestRate(FILM_FPS, tvRates)!!, 0.001)
        assertEquals(25.0, bestRate(25.0, tvRates)!!, 0.001)
        assertEquals(24.0, bestRate(24.0, tvRates)!!, 0.001)
        assertEquals(59.94, bestRate(60000.0 / 1001, tvRates)!!, 0.001)
        // a panel without an even rate: the fewest frames out of step
        assertEquals(120.0, bestRate(FILM_FPS, listOf(60.0, 90.0, 120.0))!!, 0.001)
        assertEquals(60.0, bestRate(30.0, listOf(60.0, 90.0, 120.0))!!, 0.001)
        assertNull(bestRate(FILM_FPS, emptyList()))
        assertNull(bestRate(0.0, tvRates))
    }

    @Test
    fun whetherTheScreenHasARateForTheFilm() {
        assertFalse(RefreshStatus(fps = FILM_FPS, hz = 120.0, rates = listOf(60.0, 120.0)).hasEvenRate)
        assertTrue(RefreshStatus(fps = FILM_FPS, hz = 60.0, rates = tvRates).hasEvenRate)
        // either unknown: nothing to say
        assertTrue(RefreshStatus(fps = 0.0, hz = 60.0, rates = listOf(60.0)).hasEvenRate)
        assertTrue(RefreshStatus(fps = FILM_FPS, hz = 60.0).hasEvenRate)
    }

    @Test
    fun theChoicesAreKeptByTheirKeys() {
        assertEquals(RefreshMatch.entries, RefreshMatch.entries.map { RefreshMatch.of(it.key) })
        assertNull(RefreshMatch.of("sometimes"))
        assertEquals(RefreshMatch.Seamless, RefreshMatch.Default)
    }
}
