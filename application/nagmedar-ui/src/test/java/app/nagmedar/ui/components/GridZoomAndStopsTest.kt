package app.nagmedar.ui.components

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.TabNavigation
import app.nagmedar.ui.input.noteFocusKeys
import app.nagmedar.ui.shell.LocalPaneWidths
import app.nagmedar.ui.shell.PaneWidths
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * 1.0.8 (Desktop Kit 2.8.0): a grid of covers (the podcasts', the playlists') is one stop of the Tab key, as a table is,
 * and its covers are as large as the user makes them: Ctrl with the wheel, a pinch, Ctrl with + and − (by the character
 * typed), Ctrl+0 back; the size chosen is kept with the app's preferences.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1000dp-h700dp-mdpi-keysexposed-qwerty")
class GridZoomAndStopsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val albums = (1..40).map { "Albüm $it" }
    private lateinit var zoom: GridZoom
    private lateinit var state: LazyGridState
    private lateinit var view: View
    private lateinit var inputModes: InputModeManager

    /** A store of sizes as the app's preferences are one. */
    private class Store : PaneWidths {
        val grids = mutableMapOf<String, Float>()

        override fun paneWidth(key: String): Float? = null

        override fun savePaneWidth(key: String, widthDp: Float) = Unit

        override fun gridCellWidth(key: String): Float? = grids[key]

        override fun saveGridCellWidth(key: String, widthDp: Float?) {
            if (widthDp == null) grids.remove(key) else grids[key] = widthDp
        }
    }

    @Before
    fun forgetKeys() = TabNavigation.reset()

    /** A button, a grid of covers as the app makes one, a button. */
    private fun grid(inputMode: InputMode = InputMode.Pointer, selected: String? = null, store: Store? = null) {
        state = LazyGridState()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            inputModes = LocalInputModeManager.current
            NagmedarTheme(inputMode = inputMode) {
                CompositionLocalProvider(LocalPaneWidths provides (store ?: LocalPaneWidths.current)) {
                    zoom = rememberGridZoom(136.dp, key = store?.let { "albums" })
                    val stops = rememberGridStops(state, albums, selected)
                    Column(Modifier.noteFocusKeys()) {
                        NagButton("Önce", {})
                        stops.Around(Modifier.weight(1f)) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(zoom.cellWidth),
                                state = state,
                                modifier = Modifier.fillMaxSize().testTag("grid").then(stops.grid).gridZoom(zoom),
                            ) {
                                items(albums, key = { it }) { title ->
                                    val interaction = remember { MutableInteractionSource() }
                                    Box(
                                        Modifier.then(stops.cell(title)).aspectRatio(1f).nagClickable(interaction, {}),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(title) }
                                }
                            }
                        }
                        NagButton("Sonra", {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun columns() = state.layoutInfo.visibleItemsInfo.maxOf { it.column } + 1

    private fun keyboard() {
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        compose.waitForIdle()
    }

    private fun press(keyCode: Int, shift: Boolean = false) {
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        compose.runOnUiThread {
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
        compose.waitForIdle()
    }

    private fun focusedText(): String? = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
    }

    /** A mouse wheel turned [notches] away from the user (negative: toward), over [at]. */
    private fun wheel(at: Offset, notches: Float, ctrl: Boolean) {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = at.x
            y = at.y
            setAxisValue(MotionEvent.AXIS_VSCROLL, notches)
        }
        val now = SystemClock.uptimeMillis()
        val meta = if (ctrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords),
            meta, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        compose.runOnUiThread { view.dispatchGenericMotionEvent(event) }
        event.recycle()
        compose.waitForIdle()
    }

    @Test
    fun theGridIsOneTabStop() {
        grid(selected = "Albüm 6")
        keyboard()
        compose.onNodeWithText("Önce").requestFocus()
        press(KeyEvent.KEYCODE_TAB)
        assertEquals("Tab comes in at the selected cover", "Albüm 6", focusedText())
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("the arrows move among the covers", "Albüm 7", focusedText())
        press(KeyEvent.KEYCODE_TAB)
        assertEquals("Tab leaves the grid at once", "Sonra", focusedText())
        press(KeyEvent.KEYCODE_TAB, shift = true)
        assertEquals("and comes back to the cover it left", "Albüm 7", focusedText())
        press(KeyEvent.KEYCODE_TAB, shift = true)
        assertEquals("Önce", focusedText())
    }

    @Test
    fun ctrlAndTheWheelMakeTheCoversLargerAndSmaller() {
        grid()
        val before = columns()
        val center = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot.center
        wheel(center, 1f, ctrl = true)
        assertTrue("larger: ${zoom.cellWidth}", zoom.cellWidth > 136.dp)
        assertTrue("fewer columns: ${columns()} < $before", columns() < before)
        assertEquals("the grid did not scroll", 0, state.firstVisibleItemIndex)
        wheel(center, -1f, ctrl = true)
        wheel(center, -1f, ctrl = true)
        assertTrue("smaller: ${zoom.cellWidth}", zoom.cellWidth < 136.dp)
        // without Ctrl the wheel scrolls, as ever
        val width = zoom.cellWidth
        wheel(center, -3f, ctrl = false)
        assertEquals(width, zoom.cellWidth)
    }

    @Test
    fun twoFingersSpreadTheCovers() {
        grid(InputMode.Touch)
        compose.onNodeWithTag("grid").performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(75f, 0f), center + Offset(50f, 0f), center + Offset(75f, 0f))
        }
        compose.waitForIdle()
        val width = zoom.cellWidth.value
        assertTrue("half as wide again: $width", width in 196f..212f)
        assertEquals("a whole multiple of 4 dp once the fingers are up", 0f, width % 4f)
    }

    @Test
    fun ctrlPlusMinusAndZeroOnTheKeyboardByTheCharacterTyped() {
        grid()
        keyboard()
        val cover = compose.onNodeWithText("Albüm 1")
        cover.requestFocus()
        cover.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Minus) } }
        compose.waitForIdle()
        assertEquals(108.dp, zoom.cellWidth)
        cover.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Zero) } }
        compose.waitForIdle()
        assertEquals(136.dp, zoom.cellWidth)
        // this machine's keyboard is a US one: Shift+− types "_", Shift+= types "+"
        cover.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Minus) } } }
        compose.waitForIdle()
        assertEquals("an underscore is not a minus", 136.dp, zoom.cellWidth)
        cover.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Equals) } } }
        compose.waitForIdle()
        assertEquals("a plus, as typed", 172.dp, zoom.cellWidth)
        cover.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.NumPadSubtract) } }
        compose.waitForIdle()
        assertEquals("the keypad's minus", 136.dp, zoom.cellWidth)
    }

    @Test
    fun aChosenSizeOutlivesANewDefault() {
        var default by mutableStateOf(136.dp)
        compose.setContent {
            NagmedarTheme {
                zoom = rememberGridZoom(default)
            }
        }
        compose.runOnIdle { default = 152.dp }
        compose.runOnIdle { assertEquals("nothing chosen: the default", 152.dp, zoom.cellWidth) }
        compose.runOnIdle { zoom.zoomIn() }
        compose.runOnIdle { assertEquals(192.dp, zoom.cellWidth) }
        compose.runOnIdle { default = 136.dp }
        compose.runOnIdle { assertEquals("the size the user chose stays", 192.dp, zoom.cellWidth) }
        compose.runOnIdle { zoom.reset() }
        compose.runOnIdle { assertEquals("and Ctrl+0 goes back to the default of now", 136.dp, zoom.cellWidth) }
    }

    @Test
    fun theChosenSizeIsKept() {
        val store = Store()
        grid(store = store)
        compose.runOnIdle { zoom.zoomIn() }
        assertEquals(172f, store.grids["albums"])
        // back to the default: nothing is kept, and the grid follows the default again
        compose.runOnIdle { zoom.reset() }
        assertNull(store.grids["albums"])
    }
}
