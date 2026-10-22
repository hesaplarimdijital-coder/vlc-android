package app.nagmedar.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The timelines move on between the readings a pixel at a time, as often as the playing media moves them one. */
class ProgressGlideTest {

    @Test
    fun aPixelAtATime() {
        // a four-minute track on the desktop pane's 1200 px: a pixel every 200 ms
        assertEquals(200L, glideStepMs(240_000, 1f, 1200f))
        // twice as fast, twice as often
        assertEquals(100L, glideStepMs(240_000, 2f, 1200f))
        // a short clip on a wide timeline: some fifty steps a second at the most
        assertEquals(25L, glideStepMs(30_000, 1f, 1200f))
        assertEquals(GLIDE_MIN_STEP_MS, glideStepMs(10_000, 1f, 2000f))
    }

    @Test
    fun whereAReadingMovesItLessThanAPixelTheReadingsAlone() {
        // the same track on a phone's 300 px bar: a pixel takes 800 ms, longer than a reading's interval
        assertNull(glideStepMs(240_000, 1f, 300f))
        // a film anywhere
        assertNull(glideStepMs(7_200_000, 1f, 2400f))
        // nothing to carry on
        assertNull(glideStepMs(0, 1f, 1200f))
        assertNull(glideStepMs(240_000, 0f, 1200f))
        assertNull(glideStepMs(240_000, 1f, 0f))
    }
}
