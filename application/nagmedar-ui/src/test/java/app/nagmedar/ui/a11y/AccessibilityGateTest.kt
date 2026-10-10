package app.nagmedar.ui.a11y

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1407 accessibility gate, same criteria as the design QA:
 * - every clickable is at least 24 dp with a pointer and 48 dp on touch;
 * - every clickable has a name (text or content description) for TalkBack;
 * - the keyboard reaches the controls with Tab.
 * Text size (>= 11 sp) and contrast (>= 4.5:1) are checked on the tokens (TokensTest, PaletteContrastTest).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityGateTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(widthClass: WidthClass, input: InputMode, start: Destination) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                TestShell(widthClass, startDestination = start)
            }
        }
        compose.waitForIdle()
    }

    private fun clickables(): List<SemanticsNode> = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    private fun SemanticsNode.label(): String =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: ""

    private fun checkTargets(min: Dp) {
        val density = compose.density
        val small = clickables().filter { node ->
            with(density) { node.size.width.toDp() < min - 0.5.dp || node.size.height.toDp() < min - 0.5.dp }
        }
        assertTrue(
            small.joinToString("\n") { node -> with(density) { "'${node.label()}' ${node.size.width.toDp()} x ${node.size.height.toDp()}" } },
            small.isEmpty(),
        )
    }

    private fun checkNames() {
        val unnamed = clickables().filter { it.label().isBlank() }
        assertTrue("${unnamed.size} clickables without a name: ${unnamed.map { it.id }}", unnamed.isEmpty())
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun pointerTargetsAndNames() {
        render(WidthClass.Large, InputMode.Pointer, Destination.Movies)
        checkTargets(24.dp)
        checkNames()
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun touchTargetsAndNamesCompact() {
        render(WidthClass.Compact, InputMode.Touch, Destination.Movies)
        checkTargets(48.dp)
        checkNames()
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun touchTargetsAndNamesRail() {
        render(WidthClass.Expanded, InputMode.Touch, Destination.TvLab)
        checkTargets(48.dp)
        checkNames()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun tabMovesFocusThroughControls() {
        render(WidthClass.Large, InputMode.Pointer, Destination.Movies)
        val seen = mutableSetOf<Int>()
        repeat(6) {
            compose.onRoot().performKeyInput { pressKey(Key.Tab) }
            compose.waitForIdle()
            val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
            assertTrue("nothing focused after Tab", focused.isNotEmpty())
            seen += focused.first().id
        }
        assertTrue("Tab did not move focus: $seen", seen.size >= 3)
    }
}
