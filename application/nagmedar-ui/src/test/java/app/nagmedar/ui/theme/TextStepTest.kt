package app.nagmedar.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop Kit 0.2.0: the text step of tablet and DeX windows and the tighter sizes of a phone's window. */
class TextStepTest {

    private fun NagType.styles(): List<TextStyle> = listOf(display, headline, title, body, bodyStrong, label, labelSmall, caption, mono)

    @Test
    fun everyStyleGrowsAndKeepsItsLineHeightRatio() {
        for (base in listOf(NagType.Pointer, NagType.Touch)) {
            val grown = base.grow(1f)
            assertEquals(1f, grown.step)
            base.styles().zip(grown.styles()).forEach { (before, after) ->
                assertEquals(before.fontSize.value + 1f, after.fontSize.value, 0.001f)
                assertEquals(before.lineHeight.value / before.fontSize.value, after.lineHeight.value / after.fontSize.value, 0.001f)
                assertEquals(before.fontWeight, after.fontWeight)
                assertEquals(before.fontFamily, after.fontFamily)
            }
        }
    }

    @Test
    fun noStepIsTheSameStyles() {
        assertSame(NagType.Touch, NagType.Touch.grow(0f))
    }

    @Test
    fun aScreensOwnSizeTakesTheStep() {
        assertEquals(13f, NagType.Pointer.size(13f).value, 0.001f)
        assertEquals(14f, NagType.Pointer.grow(1f).size(13f).value, 0.001f)
        assertEquals(2f, NagType.Pointer.grow(1f).grow(1f).step)
    }

    @Test
    fun nothingGrowsBelowElevenSp() {
        for (style in NagType.Pointer.grow(1f).styles() + NagType.Touch.grow(1f).styles()) assertTrue(style.fontSize.value >= 11f)
    }

    @Test
    fun aPhonesTouchSizesAreTighterButKeepTheirTargets() {
        val phone = NagDimens.Touch.forCompactWindow()
        assertEquals(64.dp, phone.bottomBarHeight)
        assertEquals(52.dp, phone.rowHeight)
        assertEquals(48.dp, phone.minTarget)
        assertTrue(phone.rowHeight >= phone.minTarget)
        // everything else as on a tablet
        assertEquals(NagDimens.Touch.copy(bottomBarHeight = 64.dp, rowHeight = 52.dp), phone)
    }

    @Test
    fun pointerSizesStayAsTheyAre() {
        assertSame(NagDimens.Pointer, NagDimens.Pointer.forCompactWindow())
    }
}
