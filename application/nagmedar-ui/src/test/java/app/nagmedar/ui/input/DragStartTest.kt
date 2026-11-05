package app.nagmedar.ui.input

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.screenshots.ShellScreenshotTest
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagPalette
import app.nagmedar.ui.theme.NagType
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.3 (Desktop Kit 2.3's K6, as in Samsung's My Files): a track is dragged out of a row only after
 * the pointer rested on it for the touch and hold delay and then moved; a quick press and move, a
 * slow click, a finger, another button start nothing. The drag's card says what goes and, for
 * several files, how many in the accent colour.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w600dp-h400dp-mdpi-keysexposed-qwerty")
class DragStartTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** How many drags started: the held drag found, as fileDragSource would start the platform's. */
    private var started = 0
    private var clicks = 0

    private fun source() {
        compose.setContent {
            NagmedarTheme(inputMode = InputMode.Pointer) {
                Box(
                    Modifier
                        .size(200.dp)
                        .testTag("row")
                        .pointerInput(Unit) { awaitEachGesture { if (awaitHeldDrag() != null) started++ } }
                        .clickable { clicks++ },
                )
            }
        }
        compose.waitForIdle()
        // time moves only when the test moves it
        compose.mainClock.autoAdvance = false
    }

    private fun row() = compose.onNodeWithTag("row")

    @Test
    fun aQuickPressAndMoveStartsNoDrag() {
        source()
        row().performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(80f, 0f))
            moveBy(Offset(80f, 0f))
            release()
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertEquals(0, started)
    }

    @Test
    fun aMouseHeldStillThenMovedStartsTheDrag() {
        source()
        row().performMouseInput {
            moveTo(center)
            press()
        }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        row().performMouseInput { moveBy(Offset(1f, 0f)) }
        compose.waitForIdle()
        assertEquals("not before the delay", 0, started)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals("held still: not until it moves", 0, started)
        row().performMouseInput { moveBy(Offset(30f, 0f)) }
        compose.waitForIdle()
        assertEquals(1, started)
        row().performMouseInput { release() }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals("the drag is no click", 0, clicks)
    }

    @Test
    fun aSlowClickStaysAClick() {
        source()
        row().performMouseInput {
            moveTo(center)
            press()
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        row().performMouseInput { release() }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals("no drag", 0, started)
        assertEquals("the click ran", 1, clicks)
    }

    @Test
    fun theButtonThatOpensMenusDragsNothing() {
        source()
        row().performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        row().performMouseInput {
            moveBy(Offset(40f, 0f))
            release(MouseButton.Secondary)
        }
        compose.waitForIdle()
        assertEquals(0, started)
    }

    @Test
    fun aFingerKeepsItsLongPressForTheMenu() {
        source()
        row().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        row().performTouchInput {
            moveBy(Offset(40f, 0f))
            up()
        }
        compose.waitForIdle()
        assertEquals(0, started)
    }

    private val density = Density(2f)
    private val fonts by lazy { createFontFamilyResolver(ApplicationProvider.getApplicationContext()) }

    private fun card(label: String, count: Int, dark: Boolean = true, direction: LayoutDirection = LayoutDirection.Ltr) = DragCard(
        label, count, if (dark) NagPalette.dark(Accent.Blue) else NagPalette.light(Accent.Blue), NagType.Pointer, density, fonts, direction,
    )

    @Test
    fun theCardStandsAboveThePointer() {
        val one = card("Neva Kâr", 1)
        assertFalse("one file: no count", one.counted)
        assertTrue(card("3 parça", 3).counted)
        // the pointer is under the card's middle, clear of it
        assertEquals(one.width / 2, one.pointerX)
        assertTrue("below the card: ${one.pointerY} > ${one.cardBottom}", one.pointerY > one.cardBottom)
        assertTrue(one.pointerY < one.height)
        // a long title ends in "…" instead of widening the card past its limit
        val long = card("Buhûrîzâde Mustafa Itrî — Neva Kâr (Sabah Ezanından Önce, Yeniden Kayıt)", 1)
        assertTrue("${long.width}", long.width <= with(density) { (240.dp + 2 * 14.dp + 2 * 12.dp).roundToPx() } + 1)
    }

    @Test
    fun severalFilesShowTheirCountInTheAccentColour() {
        val colors = NagPalette.dark(Accent.Blue)
        val many = picture(card("12 parça", 12))
        val accent = colors.accent.toArgb()
        // the badge is on the card's end corner
        assertTrue("the badge's accent at the end corner", many.any(accent, fromX = many.width * 3 / 4))
        assertFalse("one file: no accent", picture(card("Neva Kâr", 1)).any(accent, fromX = 0))
        // right to left, the corner on the left
        val rtl = picture(card("12 parça", 12, direction = LayoutDirection.Rtl))
        assertTrue(rtl.any(accent, fromX = 0, toX = rtl.width / 4))
        assertNotEquals(colors.surfaceRaised.toArgb(), accent)
    }

    @Test
    fun cardsScreenshot() {
        listOf(true, false).forEach { dark ->
            val theme = if (dark) "dark" else "light"
            ShellScreenshotTest.save("drag-card-one-$theme", picture(card("Neva Kâr", 1, dark)))
            ShellScreenshotTest.save("drag-card-many-$theme", picture(card("12 parça", 12, dark)))
        }
    }

    private fun picture(card: DragCard): Bitmap {
        val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
        card.draw(Canvas(bitmap))
        return bitmap
    }

    private fun Bitmap.any(color: Int, fromX: Int, toX: Int = width): Boolean {
        for (x in fromX until toX) for (y in 0 until height) if (getPixel(x, y) == color) return true
        return false
    }
}
