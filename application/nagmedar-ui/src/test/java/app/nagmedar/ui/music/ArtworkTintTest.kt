package app.nagmedar.ui.music

import androidx.compose.ui.graphics.Color
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.NagPalette
import app.nagmedar.ui.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1002: the artwork's colour behind the phone's player, and the text kept readable over it. */
class ArtworkTintTest {

    private fun picture(vararg parts: Pair<Int, Int>): IntArray = parts.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    @Test
    fun theLivelyColourWinsOverGreys() {
        // a grey picture with a red sign on a tenth of it
        val found = dominantColor(picture(0xFF808080.toInt() to 900, 0xFFC0392B.toInt() to 100))
        assertEquals(0xFFC0392B.toInt(), found)
    }

    @Test
    fun aPictureOfGreysHasNoColour() {
        assertNull(dominantColor(picture(0xFF202020.toInt() to 500, 0xFFE0E0E0.toInt() to 500)))
        // too little colour to count
        assertNull(dominantColor(picture(0xFF808080.toInt() to 990, 0xFF2E86C1.toInt() to 10)))
        assertNull(dominantColor(IntArray(0)))
    }

    @Test
    fun theHueMostOfThePictureSharesWins() {
        val found = dominantColor(picture(0xFF2E86C1.toInt() to 600, 0xFF3498DB.toInt() to 100, 0xFFE67E22.toInt() to 300))
        val color = Color(found!!)
        assertTrue("blue: $color", color.blue > color.red && color.blue > color.green)
    }

    @Test
    fun theBackdropKeepsTheTextReadable() {
        val artworks = listOf(Color(0xFFC0392B), Color(0xFFF1C40F), Color(0xFF2E86C1), Color(0xFF27AE60), Color(0xFF8E44AD), Color(0xFF111111), Color(0xFFFAFAFA))
        for (colors in listOf(NagPalette.light(Accent.Blue), NagPalette.dark(Accent.Blue), NagPalette.oled(Accent.Coral))) {
            for (artwork in artworks) {
                val top = backdropTop(colors, artwork)
                for (text in listOf(colors.text, colors.textSecondary, colors.textTertiary)) {
                    assertTrue("$text on $top (artwork $artwork)", contrast(text, top) >= 4.5)
                }
            }
        }
    }

    @Test
    fun theBackdropIsTinted() {
        // a lively artwork shows: the top is not the plain background in any theme
        for (colors in listOf(NagPalette.light(Accent.Blue), NagPalette.dark(Accent.Blue), NagPalette.oled(Accent.Blue))) {
            assertNotEquals(colors.background, backdropTop(colors, Color(0xFFC0392B)))
        }
    }
}
