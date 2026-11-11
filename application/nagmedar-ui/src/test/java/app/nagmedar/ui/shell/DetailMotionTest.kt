package app.nagmedar.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Desktop Kit 0.2.0's list → detail transition (the shared axis), frame by frame; 1.0.3: the phone's Back gesture
 * in depth, and (Desktop Kit 2.4.0) no frame with both screens part transparent.
 */
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

    /** 1.0.3 (Desktop Kit 2.4.0): the fades come in turn; they used to overlap for 50 to 100 ms, one screen's text over the other's. */
    @Test
    fun theSharedAxisNeverShowsBothScreensPartTransparent() {
        for (opening in listOf(true, false)) {
            for (step in 0..300) {
                val frame = DetailMotion.frame(step / 300f, opening)
                val listPart = frame.listAlpha > 0.001f && frame.listAlpha < 0.999f
                val detailPart = frame.detailAlpha > 0.001f && frame.detailAlpha < 0.999f
                assertTrue("both part transparent at $step ms (opening $opening): $frame", !(listPart && detailPart))
                // nor one fading over the other still whole: the one going away is gone before the other appears
                assertTrue("both drawn at $step ms (opening $opening): $frame", frame.listAlpha < 0.001f || frame.detailAlpha < 0.001f)
            }
        }
        // the screen going away is gone after the first third, the other comes over the rest
        assertEquals(0f, DetailMotion.frame(1f / 3f, opening = true).listAlpha, 0.001f)
        assertEquals(0f, DetailMotion.frame(1f / 3f, opening = true).detailAlpha, 0.001f)
        assertEquals(0f, DetailMotion.frame(2f / 3f, opening = false).detailAlpha, 0.001f)
        assertEquals(0f, DetailMotion.frame(2f / 3f, opening = false).listAlpha, 0.001f)
    }

    @Test
    fun theBackGestureShrinksToNinetyPercent() {
        assertEquals(0.9f, DetailMotion.BACK_SCALE, 0.0001f)
        assertEquals(28, DetailMotion.BACK_CORNER_DP)
        assertEquals(24, DetailMotion.BACK_LEAN_DP)
    }

    @Test
    fun aPhonesBackGestureHasTheListDarkBehindAndTheDetailWholeUntilLetGo() {
        // the finger's first touch: the detail as it was, the list behind it at 94 %, dark under all of its scrim
        val start = DetailDepth.gestureFrame(0f)
        assertEquals(1f, start.detailScale, 0.0001f)
        assertEquals(DetailDepth.GESTURE_LIST_SCALE, start.listScale, 0.0001f)
        assertEquals(1f, start.listScrim, 0.0001f)
        // as far as the finger seeks: shrunk to 90 %, leaning and rounded the whole way, not yet on its way out, the
        // list still dark behind it (the shrunk detail stands out against it)
        val held = DetailDepth.gestureFrame(DetailDepth.GESTURE_END)
        assertEquals(DetailMotion.BACK_SCALE, held.detailScale, 0.0001f)
        assertEquals(1f, held.lean, 0.0001f)
        assertEquals(0f, held.out, 0.0001f)
        assertEquals(1f, held.listScrim, 0.0001f)
        // half of the way out: half out of the window, the scrim half lifted (evenly: the commit's curve shapes it in time)
        val half = DetailDepth.gestureFrame(DetailDepth.GESTURE_END + (1f - DetailDepth.GESTURE_END) / 2f)
        assertEquals(0.5f, half.out, 0.001f)
        assertEquals(0.5f, half.listScrim, 0.001f)
        // the end of the way out: all of its width away, the list in place and light
        val end = DetailDepth.gestureFrame(1f)
        assertEquals(1f, end.out, 0.0001f)
        assertEquals(1f, end.listScale, 0.0001f)
        assertEquals(0f, end.listScrim, 0.0001f)
    }

    @Test
    fun aPhonesBackGestureMovesOneWayOnly() {
        var last = DetailDepth.gestureFrame(0f)
        for (step in 1..40) {
            val frame = DetailDepth.gestureFrame(step / 40f)
            assertTrue("the detail shrinks", frame.detailScale <= last.detailScale)
            assertTrue("the detail leans on", frame.lean >= last.lean)
            assertTrue("the detail goes on its way out", frame.out >= last.out)
            assertTrue("the list rises, its scrim lifting", frame.listScale >= last.listScale && frame.listScrim <= last.listScrim)
            last = frame
        }
    }

    @Test
    fun aPhonesDetailComesOverAListSunkToNinetyFiveUnderSeventyPercentOfTheScrim() {
        assertEquals(0.95f, DetailDepth.LIST_SCALE, 0.0001f)
        assertEquals(0.7f, DetailDepth.SCRIM, 0.0001f)
        assertEquals(0.85f, DetailDepth.GESTURE_END, 0.0001f)
        assertEquals(220, DetailDepth.COMMIT_MS)
        // with a card: gone in 90 ms, then there over 210
        assertEquals(90, DetailDepth.FADE_OUT_MS)
        assertEquals(210, DetailDepth.FADE_IN_MS)
    }
}
