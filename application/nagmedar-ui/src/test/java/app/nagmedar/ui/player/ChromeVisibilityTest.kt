package app.nagmedar.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-601: the controls hide unless the mouse rests on them or a menu is open; picture in picture
 * has no controls; the cursor hides with them.
 */
class ChromeVisibilityTest {

    @Test
    fun theTimerRunsUnlessTheMouseRestsOnThemOrAMenuIsOpen() {
        val visibility = ChromeVisibility()
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
        val visibility = ChromeVisibility()
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
        visibility.onMouseMoved()
        assertFalse(visibility.cursorHidden)
        visibility.hide()
        assertTrue(visibility.cursorHidden)
    }
}
