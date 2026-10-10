package app.nagmedar.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1407: text is at least 11 sp; targets are at least 24 dp with a pointer and 48 dp on touch. */
class TokensTest {

    private fun NagType.styles(): List<Pair<String, TextStyle>> = listOf(
        "display" to display, "headline" to headline, "title" to title, "body" to body, "bodyStrong" to bodyStrong,
        "label" to label, "labelSmall" to labelSmall, "caption" to caption, "mono" to mono,
    )

    @Test
    fun textIsAtLeast11sp() {
        for ((density, type) in listOf("pointer" to NagType.Pointer, "touch" to NagType.Touch)) {
            for ((name, style) in type.styles()) {
                assertTrue("$density $name is ${style.fontSize}", style.fontSize.value >= 11f)
            }
        }
    }

    @Test
    fun targetsMeetTheirMinimum() {
        assertTrue(NagDimens.Pointer.minTarget >= 24.dp)
        assertTrue(NagDimens.Touch.minTarget >= 48.dp)
        assertTrue(NagDimens.Touch.navItemHeight >= 48.dp)
        assertTrue(NagDimens.Pointer.rowHeight >= NagDimens.Pointer.minTarget)
    }

    @Test
    fun touchIsLargerThanPointer() {
        assertTrue(NagType.Touch.body.fontSize.value > NagType.Pointer.body.fontSize.value)
        assertTrue(NagDimens.Touch.rowHeight > NagDimens.Pointer.rowHeight)
    }
}
