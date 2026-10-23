package app.nagmedar.ui.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mini player shows a video only while it plays without its player: in the background or cast. */
class VideoInBarsTest {

    @Test
    fun aVideoInTheBackgroundShows() {
        assertTrue(videoInBars(casting = false, playerShown = false, pictureOut = false, background = true))
    }

    @Test
    fun aVideoBeingOpenedOrClosedDoesNot() {
        // loaded, the player not yet in front
        assertFalse(videoInBars(casting = false, playerShown = false, pictureOut = false, background = false))
        // the player in front
        assertFalse(videoInBars(casting = false, playerShown = true, pictureOut = true, background = false))
        // its picture still out while the player goes
        assertFalse(videoInBars(casting = false, playerShown = false, pictureOut = true, background = true))
        assertFalse(videoInBars(casting = false, playerShown = true, pictureOut = false, background = true))
    }

    @Test
    fun aCastVideoAlwaysShows() {
        assertTrue(videoInBars(casting = true, playerShown = true, pictureOut = false, background = false))
        assertTrue(videoInBars(casting = true, playerShown = false, pictureOut = false, background = false))
    }
}
