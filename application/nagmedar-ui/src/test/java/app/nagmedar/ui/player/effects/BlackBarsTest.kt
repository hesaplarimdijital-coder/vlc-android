package app.nagmedar.ui.player.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S-505: the black bars of a wide film found in small copies of the picture: dark rows at the top
 * and the bottom, a little noise allowed; a frame that is nearly all dark tells nothing; several
 * frames agree on the least; the bars become the picture's pixels, a thin one left.
 */
class BlackBarsTest {

    private val width = 8
    private val height = 20

    /** A frame of [height] rows, the first [top] and the last [bottom] black, the rest at [grey]. */
    private fun frame(top: Int, bottom: Int, grey: Int = 120, noise: (Int, Int) -> Int? = { _, _ -> null }): IntArray =
        IntArray(width * height) { i ->
            val row = i / width
            val x = i % width
            noise(row, x) ?: if (row < top || row >= height - bottom) 4 else grey
        }

    @Test
    fun theDarkRowsAtTheEdgesAreTheBars() {
        assertEquals(BlackBars(3, 2), BlackBarsFinder.find(frame(3, 2), width, height))
        assertEquals(BlackBars(0, 0), BlackBarsFinder.find(frame(0, 0), width, height))
        // a bar's row keeps one bright pixel: a logo, noise
        assertEquals(BlackBars(3, 2), BlackBarsFinder.find(frame(3, 2) { row, x -> if (row == 1 && x == 4) 200 else null }, width, height))
        // two bright pixels in a row of eight are no bar
        assertEquals(BlackBars(1, 2), BlackBarsFinder.find(frame(3, 2) { row, x -> if (row == 1 && x >= 3 && x <= 4) 200 else null }, width, height))
        // a dark picture is not a bar
        assertEquals(BlackBars(0, 0), BlackBarsFinder.find(frame(0, 0, grey = BlackBarsFinder.BLACK + 1), width, height))
    }

    @Test
    fun aDarkFrameTellsNothing() {
        assertNull(BlackBarsFinder.find(frame(9, 9), width, height))
        assertNull(BlackBarsFinder.find(frame(20, 0), width, height))
        assertNull(BlackBarsFinder.find(IntArray(3), width, height))
    }

    @Test
    fun framesAgreeOnTheLeast() {
        assertNull(BlackBarsFinder.agree(emptyList()))
        assertEquals(BlackBars(2, 1), BlackBarsFinder.agree(listOf(BlackBars(3, 1), BlackBars(2, 4), BlackBars(5, 2))))
    }

    @Test
    fun theBarsBecomeThePicturesPixels() {
        // 20 rows of a 1080-row picture are 54 pixels each
        assertEquals(BlackBars(162, 108), BlackBarsFinder.scale(BlackBars(3, 2), 20, 1080))
        // a bar thinner than an encoder's black line is left
        assertEquals(BlackBars(0, 0), BlackBarsFinder.scale(BlackBars(1, 1), 216, 1080))
        assertEquals(BlackBars(0, 0), BlackBarsFinder.scale(BlackBars(1, 0), 1080, 1080))
        assertEquals(BlackBars(0, 0), BlackBarsFinder.scale(BlackBars(3, 2), 0, 1080))
    }

    @Test
    fun brightnessIsTheLuma() {
        val luma = BlackBarsFinder.luma(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt()))
        assertEquals(listOf(255, 0, 76, 149), luma.toList())
    }
}
