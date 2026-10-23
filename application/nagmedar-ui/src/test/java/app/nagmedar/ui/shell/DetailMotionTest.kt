package app.nagmedar.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop Kit 0.2.0's list → detail transition (the shared axis), frame by frame. */
class DetailMotionTest {

    private fun assertFrame(expected: DetailFrame, actual: DetailFrame) {
        assertEquals("list shift", expected.listShift, actual.listShift, 0.001f)
        assertEquals("list alpha", expected.listAlpha, actual.listAlpha, 0.001f)
        assertEquals("detail shift", expected.detailShift, actual.detailShift, 0.001f)
        assertEquals("detail alpha", expected.detailAlpha, actual.detailAlpha, 0.001f)
    }

    private val list = DetailFrame(listShift = 0f, listAlpha = 1f, detailShift = DetailMotion.SHIFT, detailAlpha = 0f)
    private val detail = DetailFrame(listShift = -DetailMotion.SHIFT, listAlpha = 0f, detailShift = 0f, detailAlpha = 1f)

    @Test
    fun bothWaysStartAndEndWhereTheOtherLeftOff() {
        assertFrame(list, DetailMotion.frame(0f, opening = true))
        assertFrame(detail, DetailMotion.frame(1f, opening = true))
        assertFrame(detail, DetailMotion.frame(1f, opening = false))
        assertFrame(list, DetailMotion.frame(0f, opening = false))
    }

    @Test
    fun openingTheDetailComesFromTheEndAndTheListGoesTheOtherWay() {
        var last = DetailMotion.frame(0f, opening = true)
        for (step in 1..30) {
            val frame = DetailMotion.frame(step / 30f, opening = true)
            assertTrue("the detail moves in", frame.detailShift <= last.detailShift)
            assertTrue("the list moves out", frame.listShift <= last.listShift)
            assertTrue("the detail fades in", frame.detailAlpha >= last.detailAlpha)
            assertTrue("the list fades out", frame.listAlpha <= last.listAlpha)
            last = frame
        }
        // the old one is gone quickly; the new one is there before the move ends (emphasized decelerate)
        assertEquals(0f, DetailMotion.frame(0.4f, opening = true).listAlpha, 0.001f)
        assertTrue(DetailMotion.frame(0.5f, opening = true).detailShift < DetailMotion.SHIFT * 0.2f)
    }

    @Test
    fun closingGoesBackTheWayItCame() {
        var last = DetailMotion.frame(1f, opening = false)
        for (step in 29 downTo 0) {
            val frame = DetailMotion.frame(step / 30f, opening = false)
            assertTrue("the detail moves back to the end", frame.detailShift >= last.detailShift)
            assertTrue("the list comes back", frame.listShift >= last.listShift)
            assertTrue("the detail fades out", frame.detailAlpha <= last.detailAlpha)
            assertTrue("the list fades in", frame.listAlpha >= last.listAlpha)
            last = frame
        }
    }

    @Test
    fun theBackGestureShrinksToNinetyPercent() {
        assertEquals(0.9f, DetailMotion.BACK_SCALE, 0.0001f)
        assertEquals(28, DetailMotion.BACK_CORNER_DP)
        assertEquals(24, DetailMotion.BACK_LEAN_DP)
    }
}
