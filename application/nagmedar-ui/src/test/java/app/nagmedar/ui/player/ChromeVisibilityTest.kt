package app.nagmedar.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-601: the controls stand for the classic overlay once a mouse is in use; a finger hands the
 * video back to the classic overlay and its gestures; picture in picture has no controls; the
 * cursor hides with them.
 */
class ChromeVisibilityTest {

    @Test
    fun withoutAMouseThePlayerAsksTheClassicOverlay() {
        val visibility = ChromeVisibility()
        visibility.show()
        assertFalse("a finger's player: the classic overlay shows", visibility.visible)
        visibility.onMouseMoved()
        assertTrue(visibility.active)
        assertTrue(visibility.visible)
        assertEquals(1, visibility.moves)
    }

    @Test
    fun aFingerHandsTheVideoBack() {
        val visibility = ChromeVisibility(active = true)
        visibility.show()
        visibility.menuOpen = true
        visibility.onFinger()
        assertFalse(visibility.active)
        assertFalse(visibility.visible)
        assertFalse(visibility.menuOpen)
        // until the mouse moves again
        visibility.show()
        assertFalse(visibility.visible)
    }

    @Test
    fun theTimerRunsUnlessTheMouseRestsOnThemOrAMenuIsOpen() {
        val visibility = ChromeVisibility(active = true)
        visibility.show()
        assertTrue(visibility.hiding)
        visibility.overBars = true
        assertFalse(visibility.hiding)
        visibility.overBars = false
        visibility.menuOpen = true
        assertFalse(visibility.hiding)
        visibility.menuOpen = false
        visibility.hide()
        assertFalse(visibility.hiding)
    }

    @Test
    fun pictureInPictureHasNoControls() {
        val visibility = ChromeVisibility(active = true)
        visibility.show()
        visibility.onPictureInPicture(true)
        assertFalse(visibility.visible)
        visibility.onMouseMoved()
        assertFalse(visibility.visible)
        assertFalse("no cursor to hide in picture in picture", visibility.cursorHidden)
        visibility.onPictureInPicture(false)
        visibility.onMouseMoved()
        assertTrue(visibility.visible)
    }

    @Test
    fun theCursorHidesWithThem() {
        val visibility = ChromeVisibility()
        assertFalse(visibility.cursorHidden)
        visibility.onMouseMoved()
        assertFalse(visibility.cursorHidden)
        visibility.hide()
        assertTrue(visibility.cursorHidden)
    }
}
