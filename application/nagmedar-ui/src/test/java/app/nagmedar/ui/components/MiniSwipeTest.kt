package app.nagmedar.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Desktop Kit 1.0: the phone's mini player skips by a sideways swipe past its threshold or by a fling. */
class MiniSwipeTest {

    private fun decide(offset: Float, velocity: Float = 0f, prev: Boolean = true, next: Boolean = true, rtl: Boolean = false) =
        MiniSwipe.decide(offset, velocity, threshold = 100f, flingVelocity = 1000f, canPrevious = prev, canNext = next, rtl = rtl)

    @Test
    fun aSwipeCommitsPastTheThresholdOrOnAFling() {
        assertEquals(MiniSwipeAction.None, decide(0f))
        assertEquals(MiniSwipeAction.None, decide(-60f))
        assertEquals(MiniSwipeAction.Next, decide(-100f))
        assertEquals(MiniSwipeAction.Previous, decide(120f))
        // a quick flick commits early, but not against its own direction or from almost nothing
        assertEquals(MiniSwipeAction.Next, decide(-40f, velocity = -1500f))
        assertEquals(MiniSwipeAction.None, decide(-40f, velocity = 1500f))
        assertEquals(MiniSwipeAction.None, decide(-10f, velocity = -1500f))
    }

    @Test
    fun noHandlerNoSkipAndRightToLeftMirrors() {
        // a station or an episode: no handlers, the strip only springs back
        assertEquals(MiniSwipeAction.None, decide(-150f, next = false))
        assertEquals(MiniSwipeAction.None, decide(150f, prev = false))
        assertEquals(MiniSwipeAction.Next, decide(150f, rtl = true))
        assertEquals(MiniSwipeAction.Previous, decide(-150f, rtl = true))
    }

    @Test
    fun theStripResistsTowardsADirectionWithoutHandler() {
        assertEquals(-90f, MiniSwipe.resist(-90f, canPrevious = true, canNext = true))
        assertEquals(-30f, MiniSwipe.resist(-90f, canPrevious = true, canNext = false))
        assertEquals(30f, MiniSwipe.resist(90f, canPrevious = false, canNext = true))
        // right-to-left: leftwards goes to the previous track
        assertEquals(-30f, MiniSwipe.resist(-90f, canPrevious = false, canNext = true, rtl = true))
    }
}
