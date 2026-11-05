package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.2: a phone's sheet (a track's "⋯") goes down with a drag anywhere on it, not only on its
 * handle (the user's report): past a third of its height it closes, short of it it springs back,
 * a long sheet scrolled down scrolls back to its top first; a tap still runs a row, a drag never
 * does, and a sideways drag stays with what it began on (a slider), the sheet where it is.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class SheetDragTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var dismisses = 0
    private val ran = mutableListOf<String>()

    private fun sheet(labels: List<String> = listOf("Oynat", "Sıradaki olarak çal", "Sıraya ekle", "Çalma listesine ekle…")) {
        val groups = listOf(ActionGroup(null, labels.map { label -> MenuEntry.Item(label, { ran += label }) }))
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { _, _ ->
                    Box(Modifier.fillMaxSize()) {
                        MoreSheet(visible = open, onDismiss = {
                            open = false
                            dismisses++
                        }, groups = groups, title = "Sabaha Kadar")
                    }
                })
            }
        }
        compose.waitForIdle()
    }

    private fun sheetTop(): Float = compose.onNodeWithTag("sheet").getBoundsInRoot().top.value

    private fun sheetHeight(): Float = compose.onNodeWithTag("sheet").getBoundsInRoot().let { (it.bottom - it.top).value }

    @Test
    fun aDragDownOnARowClosesItPastAThird() {
        sheet()
        val height = sheetHeight()
        // slow (no fling): only the distance decides
        compose.onNodeWithText("Oynat").performTouchInput {
            swipe(center, center + Offset(0f, height * 0.5f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertEquals(1, dismisses)
        assertFalse(open)
        // the row the drag began on did not run
        assertEquals(emptyList<String>(), ran)
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }

    @Test
    fun aShortDragSpringsBack() {
        sheet()
        val top = sheetTop()
        val height = sheetHeight()
        compose.onNodeWithText("Sıradaki olarak çal").performTouchInput {
            swipe(center, center + Offset(0f, height * 0.15f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertTrue(open)
        assertEquals(0, dismisses)
        assertEquals(emptyList<String>(), ran)
        assertEquals(top, sheetTop(), 0.5f)
    }

    @Test
    fun aFlingDownClosesIt() {
        // a tall sheet: the fling's short way is well under a third of it
        sheet((1..8).map { "Parça $it" })
        assertTrue(sheetHeight() > 400f)
        compose.onNodeWithText("Parça 2").performTouchInput {
            swipe(center, center + Offset(0f, 100f * density), durationMillis = 70)
        }
        compose.waitForIdle()
        assertEquals(1, dismisses)
        assertEquals(emptyList<String>(), ran)
    }

    @Test
    fun aScrolledSheetScrollsBackFirst() {
        sheet((1..30).map { "Parça $it" })
        val top = sheetTop()
        val first = compose.onNodeWithText("Parça 1").getUnclippedBoundsInRoot().top.value
        // scrolled down
        compose.onNodeWithText("Parça 6").performTouchInput {
            swipe(center, center - Offset(0f, 150f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        val scrolled = compose.onNodeWithText("Parça 1").getUnclippedBoundsInRoot().top.value
        assertTrue("scrolled: $first → $scrolled", scrolled < first - 100f)
        // a pull shorter than the scroll: the rows come back down, the sheet stays where it is
        compose.onNodeWithText("Parça 6").performTouchInput {
            swipe(center, center + Offset(0f, 80f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertTrue(open)
        assertEquals(top, sheetTop(), 0.5f)
        val back = compose.onNodeWithText("Parça 1").getUnclippedBoundsInRoot().top.value
        assertTrue("scrolled back: $scrolled → $back", back > scrolled + 50f)
        // a long pull: the rest of the way to the top, then the sheet goes down and closes
        compose.onNodeWithText("Parça 4").performTouchInput {
            swipe(center, center + Offset(0f, 600f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertFalse(open)
        assertEquals(emptyList<String>(), ran)
    }

    @Test
    fun aTapStillRunsTheRow() {
        sheet()
        compose.onNodeWithText("Sıraya ekle").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Sıraya ekle"), ran)
        assertFalse(open)
    }

    @Test
    fun aSidewaysDragStaysWithWhatItBeganOn() {
        // a slider-like strip in a sheet: it gets the sideways drag, the sheet stays where it is
        var moved = 0f
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { _, _ ->
                    Box(Modifier.fillMaxSize()) {
                        GlassBottomSheet(visible = open, onDismiss = { open = false }, label = "Ekolayzer") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .testTag("strip")
                                    .draggable(rememberDraggableState { moved += it }, Orientation.Horizontal),
                            )
                        }
                    }
                })
            }
        }
        compose.waitForIdle()
        val top = sheetTop()
        compose.onNodeWithTag("strip").performTouchInput {
            swipe(centerRight - Offset(20f, 0f), centerLeft + Offset(20f, 30f), durationMillis = 400)
        }
        compose.waitForIdle()
        assertTrue("moved $moved", moved < -100f)
        assertTrue(open)
        assertEquals(top, sheetTop(), 0.5f)
    }

    @Test
    fun letGoRules() {
        val fling = 1200f
        // past a third of a 900 px sheet, or flung down
        assertTrue(SheetDragRules.closes(offset = 301f, height = 900, velocity = 0f, flingClosePx = fling))
        assertFalse(SheetDragRules.closes(offset = 299f, height = 900, velocity = 0f, flingClosePx = fling))
        assertTrue(SheetDragRules.closes(offset = 20f, height = 900, velocity = 1500f, flingClosePx = fling))
        // flung back up, or never pulled: it stays
        assertFalse(SheetDragRules.closes(offset = 299f, height = 900, velocity = -3000f, flingClosePx = fling))
        assertFalse(SheetDragRules.closes(offset = 0f, height = 900, velocity = 5000f, flingClosePx = fling))
        // not measured yet: only a fling
        assertFalse(SheetDragRules.closes(offset = 500f, height = 0, velocity = 0f, flingClosePx = fling))
    }
}
