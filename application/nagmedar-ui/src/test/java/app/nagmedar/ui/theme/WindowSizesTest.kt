package app.nagmedar.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Desktop Kit 0.2.0 in the theme: on tablet and DeX windows every text style is one sp larger, on a
 * phone's window the touch sizes are a little tighter (a 64 dp bottom bar, 52 dp rows), targets
 * stay 48 dp, and nothing else adds a step (DeX gets +1, not +2).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowSizesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun read(input: InputMode): Triple<NagType, NagDimens, Boolean> {
        var read: Triple<NagType, NagDimens, Boolean>? = null
        compose.setContent { NagmedarTheme(inputMode = input) { read = Triple(NagTheme.type, NagTheme.dimens, NagTheme.largeWindow) } }
        compose.waitForIdle()
        return read!!
    }

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun aPhoneKeepsItsTextAndTightensItsSizes() {
        val (type, dimens, large) = read(InputMode.Touch)
        assertFalse(large)
        assertEquals(NagType.Touch, type)
        assertEquals(64.dp, dimens.bottomBarHeight)
        assertEquals(52.dp, dimens.rowHeight)
        assertEquals(48.dp, dimens.minTarget)
    }

    @Test @Config(qualifiers = "w1000dp-h800dp")
    fun aTabletReadsOneSpLarger() {
        val (type, dimens, large) = read(InputMode.Touch)
        assertTrue(large)
        assertEquals(1f, type.step)
        assertEquals(NagType.Touch.body.fontSize.value + 1f, type.body.fontSize.value)
        assertEquals(NagDimens.Touch, dimens)
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-keysexposed-qwerty")
    fun deXGetsOneStepNotTwo() {
        val (type, dimens, _) = read(InputMode.Pointer)
        assertEquals(1f, type.step)
        assertEquals(NagType.Pointer.body.fontSize.value + 1f, type.body.fontSize.value)
        assertEquals(NagType.Pointer.caption.fontSize.value + 1f, type.caption.fontSize.value)
        assertEquals(NagDimens.Pointer, dimens)
    }
}
