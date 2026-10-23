package app.nagmedar.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop Kit 0.2.0: a phone's bottom bar and player strip follow the content's scrolling, the top bar its position. */
class ShellScrollStateTest {

    private fun state(max: Float = 64f) = ShellScrollState().apply { maxOffsetPx = max }

    @Test
    fun scrollingDownSlidesTheBarAwayAndUpBringsItBack() {
        val scroll = state()
        scroll.onScrolled(consumedY = -20f, availableY = 0f)
        assertEquals(20f, scroll.offsetPx)
        assertTrue("the content left its top", scroll.scrolled)
        scroll.onScrolled(consumedY = -200f, availableY = 0f)
        assertEquals("no further than its height", 64f, scroll.offsetPx)
        scroll.onScrolled(consumedY = 30f, availableY = 0f)
        assertEquals(34f, scroll.offsetPx)
        scroll.onScrolled(consumedY = 500f, availableY = 0f)
        assertEquals(0f, scroll.offsetPx)
    }

    @Test
    fun aPullAtTheTopFlattensTheTopBar() {
        val scroll = state()
        scroll.onScrolled(consumedY = -10f, availableY = 0f)
        assertTrue(scroll.scrolled)
        scroll.onScrolled(consumedY = 10f, availableY = 0f)
        assertTrue("still away from the top until it cannot go further", scroll.scrolled)
        scroll.onScrolled(consumedY = 0f, availableY = 15f)
        assertFalse(scroll.scrolled)
    }

    @Test
    fun withTheKeyboardUpTheBarStaysButTheTopBarFollows() {
        val scroll = state().apply { hideOnScroll = false }
        scroll.onScrolled(consumedY = -40f, availableY = 0f)
        assertEquals(0f, scroll.offsetPx)
        assertTrue(scroll.scrolled)
    }

    @Test
    fun widerWindowsDoNotMove() {
        val scroll = state().apply { enabled = false }
        scroll.onScrolled(consumedY = -40f, availableY = 0f)
        assertEquals(0f, scroll.offsetPx)
        assertFalse(scroll.scrolled)
    }

    @Test
    fun aBarLeftHalfWaySettlesTheNearerWay() {
        assertEquals(64f, ShellScrollState.settleTarget(40f, 64f))
        assertEquals(0f, ShellScrollState.settleTarget(20f, 64f))
        assertNull("shown already", ShellScrollState.settleTarget(0f, 64f))
        assertNull("hidden already", ShellScrollState.settleTarget(64f, 64f))
    }

    @Test
    fun aNewScreenStartsWithTheBarsShown() {
        val scroll = state()
        scroll.onScrolled(consumedY = -50f, availableY = 0f)
        scroll.reset()
        assertEquals(0f, scroll.offsetPx)
        assertFalse(scroll.scrolled)
    }
}
