package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagDimens
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagType
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's finding 10): a phone on its side is wide like a tablet but short; it stays
 * a phone: its text, its sizes (a top bar 8 dp lower) and its springs. The class comes from the window's configuration,
 * so the on-screen keyboard does not make a phone short.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w780dp-h360dp")
class HeightClassTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var height: HeightClass? = null
    private var large: Boolean? = null
    private var tier: MotionTier? = null
    private var body: TextUnit = TextUnit.Unspecified
    private var topBar: Dp = Dp.Unspecified

    private fun window() {
        compose.setContent {
            NagmedarTheme(inputMode = InputMode.Touch) {
                height = LocalHeightClass.current
                large = NagTheme.largeWindow
                tier = LocalMotionTier.current
                body = NagTheme.type.body.fontSize
                topBar = NagTheme.dimens.topBarHeight
                Text("ekran")
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun thresholds() {
        assertEquals(HeightClass.Compact, HeightClass.fromHeight(360f))
        assertEquals(HeightClass.Compact, HeightClass.fromHeight(479.9f))
        assertEquals(HeightClass.Medium, HeightClass.fromHeight(480f))
        assertEquals(HeightClass.Expanded, HeightClass.fromHeight(900f))
    }

    @Test
    fun aShortWindowKeepsThePhonesTextSizesAndMotion() {
        window()
        assertEquals(HeightClass.Compact, height)
        assertFalse("no tablet's text on a phone on its side", large == true)
        assertEquals(NagType.forMode(InputMode.Touch).body.fontSize, body)
        assertEquals(MotionTier.Gesture, tier)
        assertEquals("a top bar 8 dp lower", NagDimens.forMode(InputMode.Touch).topBarHeight - 8.dp, topBar)
        assertEquals(MotionTier.Gesture, app.nagmedar.ui.theme.NagMotion.tier(WidthClass.Expanded, reduced = false, heightClass = HeightClass.Compact))
    }

    /** A tablet of the same width, taller: one step larger, the tablet's timings. */
    @Test
    @Config(qualifiers = "tr-w780dp-h600dp")
    fun aTallerWindowOfTheSameWidthIsATablets() {
        window()
        assertEquals(HeightClass.Medium, height)
        assertTrue(large == true)
        assertTrue("$body > the phone's", body.value > NagType.forMode(InputMode.Touch).body.fontSize.value)
        assertEquals(MotionTier.Timed, tier)
        assertEquals(NagDimens.forMode(InputMode.Touch).topBarHeight, topBar)
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp")
    fun theKeyboardDoesNotMakeAPhoneShort() {
        var seen: HeightClass? = null
        compose.setContent {
            // what the window keeps of its height while the on-screen keyboard is up (adjustResize)
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                NagmedarTheme {
                    seen = LocalHeightClass.current
                    Text("ekran")
                }
            }
        }
        compose.waitForIdle()
        assertEquals("the window's height, not what the keyboard leaves of it", HeightClass.Medium, seen)
    }
}
