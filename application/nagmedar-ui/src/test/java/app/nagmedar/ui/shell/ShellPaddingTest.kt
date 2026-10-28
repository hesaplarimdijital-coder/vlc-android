package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Desktop Kit 1.0: on a phone the content lies under the bars. A screen that asks for the shell's
 * padding gets the whole window and the bars' heights; one that does not is kept clear of the bars
 * as before. Hiding the bars while scrolling moves them in the draw phase only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ShellPaddingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shell(widthClass: WidthClass = WidthClass.Compact, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, screen = { _, _ -> content() })
            }
        }
        compose.waitForIdle()
    }

    private fun near(expected: Dp, actual: Dp, label: String) =
        assertTrue("$label: expected $expected, was $actual", abs(expected.value - actual.value) < 1f)

    @Test
    fun aScreenUnderTheBarsGetsTheWindowAndThePadding() {
        var padding: PaddingValues? = null
        shell {
            padding = rememberShellContentPadding()
            Box(Modifier.fillMaxSize().testTag("screen"))
        }
        val root = compose.onRoot().getBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        near(root.top, screen.top, "screen top")
        near(root.bottom, screen.bottom, "screen bottom")
        val values = padding!!
        // the top bar's height on top; the mini player's card (with its margins) and the bottom bar below
        val miniTop = compose.onNodeWithTag("mini-player").getBoundsInRoot().top
        assertTrue("top padding ${values.calculateTopPadding()}", values.calculateTopPadding() >= 48.dp)
        near(root.bottom - miniTop + 6.dp, values.calculateBottomPadding(), "bottom padding")
        assertEquals(0.dp, values.calculateLeftPadding(LayoutDirection.Ltr))
    }

    @Test
    fun screensThatDoNotAskStayClearOfTheBars() {
        var padding: PaddingValues? = null
        shell {
            // read without asking: the shell keeps such a screen clear of the bars
            padding = LocalShellContentPadding.current
            Box(Modifier.fillMaxSize().testTag("screen")) { Text("eski ekran") }
        }
        val root = compose.onRoot().getBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        val values = padding!!
        assertTrue(values.calculateBottomPadding() > 100.dp)
        near(root.top + values.calculateTopPadding(), screen.top, "screen top")
        near(root.bottom - values.calculateBottomPadding(), screen.bottom, "screen bottom")
    }

    @Test
    fun aTabletIsNotCovered() {
        var padding: PaddingValues? = null
        shell(WidthClass.Medium) {
            padding = rememberShellContentPadding()
            Box(Modifier.fillMaxSize())
        }
        assertEquals(0.dp, padding!!.calculateTopPadding())
        assertEquals(0.dp, padding!!.calculateBottomPadding())
    }

    @Test
    fun scrollingMovesTheBarsWithoutMeasuringTheScreen() {
        var measures = 0
        shell {
            val padding = rememberShellContentPadding()
            Box(
                Modifier.fillMaxSize().layout { measurable, constraints ->
                    measures++
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            ) {
                LazyColumn(Modifier.fillMaxSize().testTag("tracks"), contentPadding = padding) {
                    items((1..80).toList()) { Text("Parça $it") }
                }
            }
        }
        val before = measures
        val barTop = compose.onNodeWithText("Radyo").getBoundsInRoot().top
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = bottom - 300f, endY = top + 300f, durationMillis = 600) }
        compose.waitForIdle()
        val movedTop = compose.onNodeWithText("Radyo").getBoundsInRoot().top
        assertTrue("the bottom bar slid down: $barTop → $movedTop", movedTop > barTop + 20.dp)
        assertEquals("the screen was measured again while scrolling", before, measures)
    }
}
