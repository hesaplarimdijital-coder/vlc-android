package app.nagmedar.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import app.nagmedar.ui.shell.DetailMotion
import app.nagmedar.ui.shell.ShellScrollState
import app.nagmedar.ui.shell.WidthClass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop Kit 1.0: which motion a window gets, and the bars' settle with it; 1.0.3: the springs' thresholds (Desktop Kit 2.3.0). */
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
        // 1.0.3 (Desktop Kit 2.3.0): all but critically damped at 600, no longer the soft 400 without bounce
        assertEquals(600f, spring.stiffness)
        assertEquals(0.92f, spring.dampingRatio)
        // what a tap opens and closes: Android's standard spatial spring
        val open = NagMotion.open<Float>()
        assertEquals(700f, open.stiffness)
        assertEquals(0.9f, open.dampingRatio)
        assertSame(timed, NagMotion.spatial(MotionTier.Timed, timed))
        assertTrue(NagMotion.spatial(MotionTier.Reduced, timed) is SnapSpec<*>)
        assertTrue(NagMotion.spatial(MotionTier.Reduced, timed, NagMotion.PIXEL) is SnapSpec<*>)
    }

    /**
     * 1.0.3 (Desktop Kit 2.3.0): the gesture spring arrives in time with a threshold for its unit. Without one Compose
     * stops it only 0.01 px from its target, and a bar that looks settled after 300 ms keeps running for a good
     * while more; the old spring (no bounce at 400) ran longer still.
     */
    @Test
    fun theGestureSpringArrivesInTimeWithAThreshold() {
        val travel = 176f // px: the phone's 64 dp bottom bar hidden, on the S24
        fun millis(spec: SpringSpec<Float>): Long =
            spec.vectorize(Float.VectorConverter).getDurationNanos(AnimationVector1D(0f), AnimationVector1D(travel), AnimationVector1D(0f)) / 1_000_000
        val bare = millis(NagMotion.gesture())
        val pixels = millis(NagMotion.gesture(NagMotion.PIXEL))
        val old = millis(SpringSpec(dampingRatio = 1f, stiffness = 400f))
        assertTrue("without a threshold the spring runs $bare ms", bare > 400)
        assertTrue("with half a pixel it is done in $pixels ms", pixels < 350)
        assertTrue("1.0.2's spring ran $old ms", old > 550)
        assertEquals(NagMotion.PIXEL, (NagMotion.spatial(MotionTier.Gesture, tween(300), NagMotion.PIXEL) as SpringSpec<Float>).visibilityThreshold)
        // a fraction (a sheet's way over its 1500 px) is done within a thousandth of it, not a hundredth left to jump
        val fraction = NagMotion.gesture(NagMotion.FRACTION)
        val end = fraction.vectorize(Float.VectorConverter)
        val nanos = end.getDurationNanos(AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f))
        val last = end.getValueFromNanos(nanos - 16_000_000L, AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)).value
        assertEquals("a frame before its end the sheet is within 1.5 px of its place", 1f, last, 0.001f)
        assertTrue("and it arrives within ${nanos / 1_000_000} ms", nanos / 1_000_000 < 400)
    }

    /**
     * 1.0.3: a list's detail on a phone stops its spring a fiftieth of the way from the end ([DetailMotion.ARRIVED]):
     * the frames are a function of the way whose curves flatten at both ends, so less than a tenth of a pixel is left
     * there on the widest phone and the fades are over; it arrives sooner than at Compose's hundredth.
     */
    @Test
    fun aDetailArrivesOnceNothingMoves() {
        val width = 1440
        for (opening in listOf(true, false)) {
            val end = DetailMotion.frame(if (opening) 1f else 0f, opening)
            val near = DetailMotion.frame(if (opening) 1f - DetailMotion.ARRIVED else DetailMotion.ARRIVED, opening)
            assertEquals("the list's slide", end.listShift * width, near.listShift * width, 0.1f)
            assertEquals("the detail's slide", end.detailShift * width, near.detailShift * width, 0.1f)
            assertEquals("the list's fade", end.listAlpha, near.listAlpha, 0.002f)
            assertEquals("the detail's fade", end.detailAlpha, near.detailAlpha, 0.002f)
        }
        fun millis(spec: SpringSpec<Float>): Long =
            spec.vectorize(Float.VectorConverter).getDurationNanos(AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)) / 1_000_000
        val arrived = millis(NagMotion.gesture(DetailMotion.ARRIVED))
        val bare = millis(NagMotion.gesture())
        assertTrue("the detail arrives in $arrived ms, $bare ms at a hundredth", arrived < 240 && bare > arrived)
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
        // 1.0.3 (Desktop Kit 2.3.0): on a phone too the growth from 96 % starts with the new screen's fade, after the old
        // one has gone; on the gesture spring it started 90 ms early, while the screen could not be seen yet
        val withTheFade = tween<Float>(NagMotion.FADE_IN, delayMillis = NagMotion.FADE_OUT, easing = NagMotion.EmphasizedDecelerate)
        assertEquals(
            fadeIn(withTheFade) + scaleIn(withTheFade, initialScale = NagMotion.PHONE_SCALE),
            full.screenEnter(initialScale = NagMotion.PHONE_SCALE, tier = MotionTier.Gesture),
        )
        // the phone's and the tablet's differ in where they grow from only
        assertNotEquals(
            full.screenEnter(initialScale = NagMotion.TABLET_SCALE, tier = MotionTier.Timed),
            full.screenEnter(initialScale = NagMotion.PHONE_SCALE, tier = MotionTier.Gesture),
        )
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
        scroll.onScrolled(consumedY = -64f)
        assertEquals(64f, scroll.offsetPx)
        scroll.showBars()
        assertEquals(0f, scroll.offsetPx)
    }
}
