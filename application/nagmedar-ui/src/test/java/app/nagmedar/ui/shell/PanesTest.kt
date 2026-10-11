package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-303: details beside the list from expanded windows up, over it below; the pane's width is
 * dragged, set with the keys or a screen reader, kept in the store and fitted to the window; the
 * list keeps its place under the details; the sidebar's width works the same way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Store(vararg saved: Pair<String, Float>) : PaneWidths {
        val saved = mutableMapOf(*saved)

        override fun paneWidth(key: String): Float? = saved[key]

        override fun savePaneWidth(key: String, widthDp: Float) {
            saved[key] = widthDp
        }
    }

    private var detail by mutableStateOf<String?>(null)
    private val list = LazyListState()
    private lateinit var inputModeManager: InputModeManager

    private fun panes(store: PaneWidths = Store(), input: InputMode = InputMode.Pointer, rtl: Boolean = false) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                inputModeManager = LocalInputModeManager.current
                CompositionLocalProvider(
                    LocalPaneWidths provides store,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    ListDetailPanes(
                        detail = detail,
                        onCloseDetail = { detail = null },
                        list = {
                            LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) {
                                items(100) { i ->
                                    Text("Satır $i", Modifier.fillMaxWidth().height(40.dp).clickable { detail = "Satır $i" })
                                }
                            }
                        },
                        detailPane = { key, fullScreen ->
                            Column(Modifier.fillMaxSize().testTag(if (fullScreen) "full" else "pane")) {
                                Text("Ayrıntı: $key")
                                NagButton("Kapat", { detail = null }, modifier = Modifier.testTag("inside"))
                            }
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun width(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().let { it.right - it.left }

    private fun assertWidth(expected: Dp, actual: Dp) = assertTrue("expected $expected, was $actual", abs(expected.value - actual.value) < 1f)

    private val divider get() = compose.onNodeWithContentDescription("Ayrıntılar bölmesinin genişliği")

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun besideTheListInLargeWindows() {
        panes()
        compose.onNodeWithTag("pane").assertDoesNotExist()
        compose.onNodeWithText("Satır 3").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ayrıntı: Satır 3").assertExists()
        compose.onNodeWithTag("list").assertExists()
        assertWidth(336.dp, width("pane"))
        // list, divider, pane
        assertWidth((1280 - 336 - 8).dp, width("list"))
        divider.assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun besideTheListInExpandedWindows() {
        panes(input = InputMode.Touch)
        detail = "Satır 1"
        compose.waitForIdle()
        compose.onNodeWithTag("pane").assertExists()
        compose.onNodeWithTag("list").assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun keysAndScreenReadersSetTheWidthAndItIsKept() {
        val store = Store()
        panes(store)
        detail = "Satır 1"
        compose.waitForIdle()
        divider.requestFocus()
        // the pane is at the end: moving its edge towards the start widens it
        divider.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        assertWidth(352.dp, width("pane"))
        assertEquals(352f, store.saved[INSPECTOR_PANE])
        divider.performKeyInput { pressKey(Key.DirectionRight) }
        divider.performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertWidth(320.dp, width("pane"))
        divider.performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        compose.waitForIdle()
        assertWidth(500.dp, width("pane"))
        assertEquals(500f, store.saved[INSPECTOR_PANE])
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun draggingTheEdgeResizesAndSavesOnRelease() {
        val store = Store()
        panes(store)
        detail = "Satır 1"
        compose.waitForIdle()
        divider.performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-10.dp.toPx(), 0f)) }
        }
        compose.waitForIdle()
        // 100 dp to the left, less the touch slop; nothing saved while the finger is down
        val dragged = width("pane")
        assertTrue("$dragged", dragged > 400.dp && dragged < 437.dp)
        assertNull(store.saved[INSPECTOR_PANE])
        divider.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(dragged.value, store.saved[INSPECTOR_PANE]!!, 1f)
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun theRememberedWidthComesBackAndFitsTheWindow() {
        panes(Store(INSPECTOR_PANE to 420f))
        detail = "Satır 1"
        compose.waitForIdle()
        assertWidth(420.dp, width("pane"))
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun aWidthTooLargeForTheWindowLeavesRoomForTheList() {
        panes(Store(INSPECTOR_PANE to 2000f))
        detail = "Satır 1"
        compose.waitForIdle()
        assertWidth(360.dp, width("list"))
        assertWidth((1280 - 360 - 8).dp, width("pane"))
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun rightToLeftThePaneIsOnTheLeft() {
        val store = Store()
        panes(store, rtl = true)
        detail = "Satır 1"
        compose.waitForIdle()
        assertEquals(0f, compose.onNodeWithTag("pane").getBoundsInRoot().left.value, 0.5f)
        divider.requestFocus()
        // the edge moves to the right, away from the pane: wider
        divider.performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertWidth(352.dp, width("pane"))
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun escapeClosesTheDetails() {
        panes()
        detail = "Satır 1"
        compose.waitForIdle()
        // buttons take the focus from the keyboard
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithTag("inside").requestFocus()
        compose.onNodeWithTag("inside").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertNull(detail)
        compose.onNodeWithTag("pane").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun overTheListOnPhonesAndBackReturnsToItsPlace() {
        panes(input = InputMode.Touch)
        compose.runOnIdle { list.requestScrollToItem(50) }
        compose.waitForIdle()
        compose.onNodeWithText("Satır 51").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("full").assertExists()
        assertWidth(411.dp, width("full"))
        // covered: neither drawn nor read out
        compose.onNodeWithTag("list").assertDoesNotExist()
        divider.assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertNull(detail)
        compose.onNodeWithTag("full").assertDoesNotExist()
        compose.onNodeWithText("Satır 51").assertExists()
        assertEquals(50, list.firstVisibleItemIndex)
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun overTheListInMediumWindows() {
        panes(input = InputMode.Touch)
        detail = "Satır 2"
        compose.waitForIdle()
        compose.onNodeWithTag("full").assertExists()
        compose.onNodeWithTag("list").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun theListKeepsItsPlaceAcrossWidthClasses() {
        var widthClass by mutableStateOf(WidthClass.Large)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides widthClass) {
                    ListDetailPanes(
                        detail = detail,
                        onCloseDetail = { detail = null },
                        list = {
                            LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) {
                                items(100) { i -> Text("Satır $i", Modifier.fillMaxWidth().height(40.dp)) }
                            }
                        },
                        detailPane = { key, fullScreen -> Text("Ayrıntı: $key", Modifier.testTag(if (fullScreen) "full" else "pane")) },
                    )
                }
            }
        }
        compose.runOnIdle { list.requestScrollToItem(30) }
        detail = "Satır 30"
        compose.waitForIdle()
        compose.onNodeWithTag("pane").assertExists()
        for (next in listOf(WidthClass.Compact, WidthClass.Medium, WidthClass.Expanded, WidthClass.ExtraLarge)) {
            widthClass = next
            compose.waitForIdle()
            compose.onNodeWithTag(if (next >= WidthClass.Expanded) "pane" else "full").assertExists()
        }
        detail = null
        compose.waitForIdle()
        assertEquals(30, list.firstVisibleItemIndex)
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun theSidebarIsDraggedToAKeptWidth() {
        val store = Store(SIDEBAR_PANE to 300f)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalPaneWidths provides store) {
                    TestShell(WidthClass.Large, startDestination = Destination.Music)
                }
            }
        }
        compose.waitForIdle()
        val sidebar = compose.onNodeWithContentDescription("Kenar çubuğunun genişliği")
        sidebar.requestFocus()
        sidebar.performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertEquals(316f, store.saved[SIDEBAR_PANE])
        sidebar.performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        compose.waitForIdle()
        // within 200–360 dp
        assertEquals(360f, store.saved[SIDEBAR_PANE])
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun aCollapsedSidebarCannotBeDragged() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                TestShell(WidthClass.Large, startDestination = Destination.Music, sidebarCollapsed = true)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Kenar çubuğunun genişliği").assertDoesNotExist()
    }
}
