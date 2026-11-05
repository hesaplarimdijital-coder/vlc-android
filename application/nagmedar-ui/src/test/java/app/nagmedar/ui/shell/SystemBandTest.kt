package app.nagmedar.ui.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.screenshots.ShellScreenshotTest
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagPalette
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.SystemBarsFollowTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 1.0.3 (Desktop Kit 2.0's three-button navigation): the shell's chrome continues behind the Back,
 * Home and Recents buttons instead of leaving a strip of window background with the system's scrim
 * on it; the bars' controls stay above the buttons; a screen's bottom padding includes them; the
 * keyboard leaves no band; tablets get the chrome colour there; the first launch has no gap; the
 * buttons take the theme's colour; Tab and TalkBack read the bottom bar last. The window is the
 * app's own root ([WindowRoot]) around the real shell, the system's insets dispatched as a phone
 * with three-button navigation sends them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemBandTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View
    private var shown by mutableStateOf(true)

    private fun px(dp: Dp): Int = with(compose.density) { dp.roundToPx() }

    /** What the system sends: the status bar, the three-button navigation bar and, if open, the keyboard. */
    private fun systemBars(status: Dp, navigation: Dp, keyboard: Int = 0) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(status), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(navigation)))
            .setVisible(WindowInsetsCompat.Type.navigationBars(), navigation > 0.dp)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
            .setVisible(WindowInsetsCompat.Type.ime(), keyboard > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    /** The app's root (the shell's or, with [shell] false, the first run's) around the real shell, as NagmedarApp has it. */
    private fun app(
        widthClass: WidthClass = WidthClass.Compact,
        theme: ThemeMode = ThemeMode.Dark,
        shell: Boolean = true,
        fontScale: Float = 1f,
        screen: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            NagmedarTheme(themeMode = theme, inputMode = InputMode.Touch) {
                SystemBarsFollowTheme()
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    WindowRoot(shell = shell, belowCaption = false, modifier = Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                        if (shell && shown) {
                            TestShell(widthClass, screen = { d, n -> if (screen == null) app.nagmedar.ui.SampleScreen(d, n) else screen() })
                        } else {
                            Box(Modifier.fillMaxSize().testTag("first-run"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun window(name: String? = null): Bitmap {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        if (name != null) ShellScreenshotTest.save(name, bitmap)
        return bitmap
    }

    private fun near(expected: Dp, actual: Dp, label: String) =
        assertTrue("$label: expected $expected, was $actual", abs(expected.value - actual.value) < 1.5f)

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneBarReachesBehindTheButtons() {
        app()
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        // the tabs stay above the buttons, the bar resting right on them
        val tab = compose.onNodeWithText("Radyo").getBoundsInRoot()
        assertTrue("tab ends at ${tab.bottom}, buttons start at ${root.bottom - 48.dp}", tab.bottom <= root.bottom - 48.dp)
        val bitmap = window()
        val chrome = NagPalette.dark(Accent.Blue).chrome.toArgb()
        // behind the buttons: the bar's own colour, as on the bar just above them
        assertEquals("behind the buttons", chrome, bitmap.getPixel(bitmap.width / 2, bitmap.height - px(12.dp)))
        assertEquals("the bar over them", chrome, bitmap.getPixel(bitmap.width / 4, bitmap.height - px(48.dp) - px(4.dp)))
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun aScreenUnderTheBarsIsPaddedForTheButtonsToo() {
        var padding: PaddingValues? = null
        app(screen = {
            padding = rememberShellContentPadding()
            Box(Modifier.fillMaxSize().testTag("screen"))
        })
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        // the screen fills the window down to its bottom, behind the bars and the band
        near(root.bottom, screen.bottom, "screen bottom")
        // its padding reaches from the mini player's card (with its margin) to the window's bottom
        val miniTop = compose.onNodeWithTag("mini-player").getBoundsInRoot().top
        near(root.bottom - miniTop + 6.dp, padding!!.calculateBottomPadding(), "bottom padding")
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun aScreenThatDoesNotAskEndsAboveTheBars() {
        app(screen = { Box(Modifier.fillMaxSize().testTag("screen")) })
        systemBars(status = 24.dp, navigation = 48.dp)
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        val miniTop = compose.onNodeWithTag("mini-player").getBoundsInRoot().top
        near(miniTop - 6.dp, screen.bottom, "screen bottom")
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun theKeyboardLeavesNoBand() {
        app()
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        val keyboardPx = 600
        systemBars(status = 24.dp, navigation = 48.dp, keyboard = keyboardPx)
        compose.onNodeWithText("Radyo").assertDoesNotExist()
        val keyboardTop: Dp = root.bottom - with(compose.density) { keyboardPx.toDp() }
        // the mini player sits right on the keyboard: no band for the buttons it covers
        val mini = compose.onNodeWithTag("mini-player").getBoundsInRoot()
        assertTrue("mini player ends at ${mini.bottom}, keyboard at $keyboardTop", abs((keyboardTop - mini.bottom - 6.dp).value) < 1.5f)
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun tabletChromeFillsTheNavigationBar() {
        app(WidthClass.Medium)
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        // the rail's player ends above the buttons
        val player = compose.onNodeWithTag("mini-player").getBoundsInRoot()
        assertTrue("player ends at ${player.bottom}", player.bottom <= root.bottom - 48.dp)
        val bitmap = window()
        assertEquals(NagPalette.dark(Accent.Blue).chrome.toArgb(), bitmap.getPixel(bitmap.width / 2, bitmap.height - px(12.dp)))
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun theFirstRunStaysAboveTheButtons() {
        app(shell = false)
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        val firstRun = compose.onNodeWithTag("first-run").getBoundsInRoot()
        near(root.bottom - 48.dp, firstRun.bottom, "first run bottom")
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun theShellSitsOnTheButtonsAtFirstLaunch() {
        shown = false
        app()
        systemBars(status = 24.dp, navigation = 48.dp)
        // the shell comes once the window already has its navigation bar, as on the app's first launch
        shown = true
        compose.waitForIdle()
        val first = compose.onNodeWithText("Radyo").getBoundsInRoot()
        // the window laid out again with the same bar
        systemBars(status = 24.dp, navigation = 0.dp)
        systemBars(status = 24.dp, navigation = 48.dp)
        val later = compose.onNodeWithText("Radyo").getBoundsInRoot()
        assertEquals("the bottom bar moved once the window was laid out again", later.bottom.value, first.bottom.value, 0.5f)
        val root = compose.onRoot().getBoundsInRoot()
        assertTrue("no gap under the bar: ${first.bottom} vs ${root.bottom - 48.dp}", first.bottom <= root.bottom - 48.dp)
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun buttonsFollowTheLightTheme() {
        app(theme = ThemeMode.Light)
        val window = compose.activity.window
        compose.runOnIdle {
            assertFalse("no system scrim over the app's chrome", window.isNavigationBarContrastEnforced)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertTrue("dark buttons on the light theme", controller.isAppearanceLightNavigationBars)
            assertTrue("dark status icons on the light theme", controller.isAppearanceLightStatusBars)
        }
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun lightButtonsOnTheDarkTheme() {
        app(theme = ThemeMode.Dark)
        val window = compose.activity.window
        compose.runOnIdle {
            assertFalse(window.isNavigationBarContrastEnforced)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertFalse(controller.isAppearanceLightNavigationBars)
            assertFalse(controller.isAppearanceLightStatusBars)
        }
    }

    /** Desktop Kit 2.0's reading order: the top bar, the content, the player, the phone's bottom bar last. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun talkBackReadsTheBottomBarLast() {
        app()
        val order = { node: androidx.compose.ui.semantics.SemanticsNode -> traversalIndexOf(node) }
        val search = order(compose.onNodeWithContentDescriptionOrText("Ara ya da komut çalıştır"))
        val content = order(compose.onNodeWithText("Gece Yolcusu", useUnmergedTree = true).fetchSemanticsNode())
        val player = order(compose.onNodeWithTag("mini-player", useUnmergedTree = true).fetchSemanticsNode())
        val tab = order(compose.onNodeWithText("Radyo", useUnmergedTree = true).fetchSemanticsNode())
        assertNotNull(search)
        assertTrue("top bar $search before content $content", search!! < content!!)
        assertTrue("content $content before the player $player", content < player!!)
        assertTrue("the player $player before the bottom bar $tab", player < tab!!)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithContentDescriptionOrText(label: String): androidx.compose.ui.semantics.SemanticsNode =
        onAllNodes(
            androidx.compose.ui.test.hasContentDescription(label).or(androidx.compose.ui.test.hasText(label)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().first()

    /** The traversal index of the shell's slot [node] lies in: the outermost traversal group above it (a list is one too). */
    private fun traversalIndexOf(node: androidx.compose.ui.semantics.SemanticsNode): Float? {
        var current: androidx.compose.ui.semantics.SemanticsNode? = node
        var index: Float? = null
        while (current != null) {
            if (current.config.getOrElse(SemanticsProperties.IsTraversalGroup) { false }) {
                index = current.config.getOrElse(SemanticsProperties.TraversalIndex) { 0f }
            }
            current = current.parent
        }
        return index
    }

    /** The bars on the buttons for the screenshot references: dark, light and large text. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun threeButtonDark() {
        app()
        systemBars(status = 24.dp, navigation = 48.dp)
        window("compact-dark-three-button-navigation")
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun threeButtonLight() {
        app(theme = ThemeMode.Light)
        systemBars(status = 24.dp, navigation = 48.dp)
        window("compact-light-three-button-navigation")
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun threeButtonLargeText() {
        app(theme = ThemeMode.Light, fontScale = 2f)
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        // the bar grew for the labels and still rests on the buttons
        val tab = compose.onNodeWithText("Radyo").getBoundsInRoot()
        assertTrue("tab ends at ${tab.bottom}", tab.bottom <= root.bottom - 48.dp)
        window("compact-large-text-three-button-navigation")
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun tabletThreeButtonDark() {
        app(WidthClass.Medium)
        systemBars(status = 24.dp, navigation = 48.dp)
        window("medium-dark-three-button-navigation")
    }
}
