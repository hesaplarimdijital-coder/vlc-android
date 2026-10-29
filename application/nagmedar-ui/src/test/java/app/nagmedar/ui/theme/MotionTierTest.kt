package app.nagmedar.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import app.nagmedar.ui.shell.ShellScrollState
import app.nagmedar.ui.shell.WidthClass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop Kit 1.0: which motion a window gets, and the bars' settle with it. */
class MotionTierTest {

    @Test
    fun phonesGetSpringsWiderWindowsKeepTheTimedCurves() {
        assertEquals(MotionTier.Gesture, NagMotion.tier(WidthClass.Compact))
        for (wide in listOf(WidthClass.Medium, WidthClass.Expanded, WidthClass.Large, WidthClass.ExtraLarge)) {
            assertEquals(wide.name, MotionTier.Timed, NagMotion.tier(wide))
        }
        for (any in WidthClass.entries) {
            assertEquals(any.name, MotionTier.Reduced, NagMotion.tier(any, reduced = true))
        }
    }

    @Test
    fun spatialSpecFollowsTheTier() {
        val timed = tween<Float>(300)
        val spring = NagMotion.spatial(MotionTier.Gesture, timed)
        assertTrue(spring is SpringSpec<*>)
        spring as SpringSpec<Float>
        assertEquals(Spring.StiffnessMediumLow, spring.stiffness)
        assertEquals(Spring.DampingRatioNoBouncy, spring.dampingRatio)
        assertSame(timed, NagMotion.spatial(MotionTier.Timed, timed))
        assertTrue(NagMotion.spatial(MotionTier.Reduced, timed) is SnapSpec<*>)
    }

    @Test
    fun fadesStayTimedAndSnapWithReducedMotion() {
        val fade = tween<Float>(150)
        assertSame(fade, NagMotion.fade(MotionTier.Gesture, fade))
        assertSame(fade, NagMotion.fade(MotionTier.Timed, fade))
        assertTrue(NagMotion.fade(MotionTier.Reduced, fade) is SnapSpec<*>)
    }

    @Test
    fun screenTransitionsByTier() {
        val full = NagMotion.Full
        for (desktop in listOf(false, true)) {
            assertEquals(EnterTransition.None, full.screenEnter(desktop, tier = MotionTier.Reduced))
            assertEquals(ExitTransition.None, full.screenExit(desktop, tier = MotionTier.Reduced))
        }
        assertNotEquals(EnterTransition.None, full.screenEnter(initialScale = NagMotion.PHONE_SCALE, tier = MotionTier.Gesture))
        assertNotEquals(EnterTransition.None, full.screenEnter(tier = MotionTier.Timed))
        assertNotEquals(EnterTransition.None, full.screenEnter(desktop = true, tier = MotionTier.Timed))
        // the phone's and the tablet's differ: a spring against the timed curve
        assertNotEquals(full.screenEnter(tier = MotionTier.Timed), full.screenEnter(tier = MotionTier.Gesture))
    }

    @Test
    fun reducedMotionSettlesAtOnce() = runBlocking {
        val scroll = ShellScrollState()
        scroll.maxOffsetPx = 100f
        scroll.tier = MotionTier.Reduced
        // a drag that hid the bar 70 px: past half way, it finishes hiding without a frame clock
        scroll.connection.onPostScroll(Offset(0f, -70f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(70f, scroll.offsetPx)
        scroll.settle()
        assertEquals(100f, scroll.offsetPx)
    }

    @Test
    fun anExpandingPlayerBringsTheBarsBack() {
        val scroll = ShellScrollState()
        scroll.maxOffsetPx = 64f
        scroll.onScrolled(consumedY = -64f, availableY = 0f)
        assertEquals(64f, scroll.offsetPx)
        scroll.showBars()
        assertEquals(0f, scroll.offsetPx)
    }
}
