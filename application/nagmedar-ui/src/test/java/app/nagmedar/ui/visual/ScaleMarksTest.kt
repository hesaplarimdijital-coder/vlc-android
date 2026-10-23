package app.nagmedar.ui.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/** The spectrum's scales on a narrow pane: every n-th number, the first and the last kept, none touching. */
class ScaleMarksTest {

    @Test
    fun everyMarkWhereAllFit() {
        assertEquals(listOf(0, 1, 2, 3), ScaleMarks.spaced(listOf(0f, 100f, 200f, 300f), 40f, 6f))
    }

    @Test
    fun everySecondWhereNeighboursTouch() {
        val centres = List(7) { it * 30f }
        assertEquals(listOf(0, 2, 4, 6), ScaleMarks.spaced(centres, 40f, 6f))
    }

    @Test
    fun theLastStaysAndTheOneBeforeItGoes() {
        assertEquals(listOf(0, 1, 3), ScaleMarks.spaced(listOf(0f, 50f, 100f, 120f), 40f, 6f))
    }

    @Test
    fun onlyTheFirstWhenEvenTwoTouch() {
        assertEquals(listOf(0), ScaleMarks.spaced(listOf(0f, 10f), 40f, 6f))
        assertEquals(listOf(0), ScaleMarks.spaced(listOf(5f), 40f, 6f))
    }

    @Test
    fun aPhonesFrequenciesNeverTouch() {
        // 50 Hz to 10 kHz on the log scale of 25 Hz to 20 kHz, 300 px wide, numbers 44 px wide
        val marks = listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)
        val span = ln(20000f / 25f)
        val centres = marks.map { 300f * ln(it / 25f) / span }
        val shown = ScaleMarks.spaced(centres, 44f, 6f)
        assertEquals(0, shown.first())
        assertEquals(marks.lastIndex, shown.last())
        assertTrue(shown.size in 2 until marks.size)
        shown.zipWithNext().forEach { (a, b) -> assertTrue("$a, $b", centres[b] - centres[a] >= 50f) }
    }
}
