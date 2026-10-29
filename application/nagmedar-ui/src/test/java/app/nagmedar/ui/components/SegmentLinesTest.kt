package app.nagmedar.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.0.1: a segmented choice too wide for its row goes on more lines, in order, and never cuts an option. */
class SegmentLinesTest {

    @Test
    fun optionsThatFitStayOnOneLine() {
        assertEquals(listOf(0..3), segmentLines(listOf(80, 70, 90, 60), maxWidth = 306, gap = 2))
    }

    @Test
    fun theOutputRowOfAPhoneWraps() {
        // "Otomatik · AAudio · Bit-perfect · USB DAC" at a phone's width: the last one on a line of its own
        assertEquals(listOf(0..2, 3..3), segmentLines(listOf(96, 80, 104, 92), maxWidth = 300, gap = 2))
        // at 320 dp with the largest font, two by two
        assertEquals(listOf(0..1, 2..3), segmentLines(listOf(120, 100, 130, 110), maxWidth = 240, gap = 2))
    }

    @Test
    fun anOptionWiderThanTheRowHasALineOfItsOwn() {
        assertEquals(listOf(0..0, 1..1, 2..2), segmentLines(listOf(50, 400, 50), maxWidth = 300, gap = 2))
    }

    @Test
    fun nothingToLayOut() {
        assertEquals(emptyList<IntRange>(), segmentLines(emptyList(), maxWidth = 300, gap = 2))
    }
}
