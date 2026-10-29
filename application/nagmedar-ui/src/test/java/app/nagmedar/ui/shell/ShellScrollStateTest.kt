package app.nagmedar.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Desktop Kit 0.2.0: a phone's bottom bar and player strip follow the content's scrolling (1.0.1: the top bar does not). */
class ShellScrollStateTest {

    private fun state(max: Float = 64f) = ShellScrollState().apply { maxOffsetPx = max }

    @Test
    fun scrollingDownSlidesTheBarAwayAndUpBringsItBack() {
        val scroll = state()
        scroll.onScrolled(consumedY = -20f)
        assertEquals(20f, scroll.offsetPx)
        scroll.onScrolled(consumedY = -200f)
        assertEquals("no further than its height", 64f, scroll.offsetPx)
        scroll.onScrolled(consumedY = 30f)
        assertEquals(34f, scroll.offsetPx)
        scroll.onScrolled(consumedY = 500f)
        assertEquals(0f, scroll.offsetPx)
    }

    @Test
    fun aPullTheContentCannotTakeMovesNothing() {
        val scroll = state()
        scroll.onScrolled(consumedY = -10f)
        // at its top, a pull down the list could not take: the bar stays where it is, nothing flips
        scroll.onScrolled(consumedY = 0f)
        assertEquals(10f, scroll.offsetPx)
    }

    @Test
    fun withTheKeyboardUpTheBarStays() {
        val scroll = state().apply { hideOnScroll = false }
        scroll.onScrolled(consumedY = -40f)
        assertEquals(0f, scroll.offsetPx)
    }

    @Test
    fun widerWindowsDoNotMove() {
        val scroll = state().apply { enabled = false }
        scroll.onScrolled(consumedY = -40f)
        assertEquals(0f, scroll.offsetPx)
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
        scroll.onScrolled(consumedY = -50f)
        scroll.reset()
        assertEquals(0f, scroll.offsetPx)
    }
}
