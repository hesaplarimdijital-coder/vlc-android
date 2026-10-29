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
        // "Otomatik · AAudio · Bit-perfect · USB DAC" at a phone's width: two by two, not three and one
        assertEquals(listOf(0..1, 2..3), segmentLines(listOf(96, 80, 104, 92), maxWidth = 300, gap = 2))
        assertEquals(listOf(0..1, 2..3), segmentLines(listOf(120, 100, 130, 110), maxWidth = 250, gap = 2))
        // two by two would not fit: as many on each line as fit
        assertEquals(listOf(0..1, 2..2, 3..3), segmentLines(listOf(120, 100, 130, 110), maxWidth = 240, gap = 2))
        // three options on two lines: two, then one
        assertEquals(listOf(0..1, 2..2), segmentLines(listOf(90, 120, 110), maxWidth = 260, gap = 2))
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
