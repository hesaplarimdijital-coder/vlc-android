package app.nagmedar.ui.shell

import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
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
import kotlin.math.abs

/**
 * 1.0.3 (Desktop Kit 2.3): with gesture navigation the Back from another tab's first page follows the
 * finger on a phone: the tab shrinks toward 90 % leaning away from the edge the swipe came from, Müzik
 * is really there behind it, a gesture given up puts the tab back, a whole one goes to Müzik; with
 * reduced motion nothing moves. Three-button navigation's Back (the user's) goes to Müzik as before,
 * the tab kept as it was left; tablets keep their Back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class TabBackGestureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View
    private var navigator: ShellNavigator? = null

    /** The real shell, each page a box named after it; Podcast's counts its taps, kept as its saved state. */
    private fun window(widthClass: WidthClass = WidthClass.Compact, reduced: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch, reduceMotion = reduced) {
                TestShell(widthClass, screen = { destination, nav ->
                    navigator = nav
                    Box(Modifier.fillMaxSize().testTag("screen-${destination.name}")) {
                        if (destination == Destination.Podcasts) {
                            var taps by rememberSaveable { mutableIntStateOf(0) }
                            NagButton("Dokunuş: $taps", { taps++ })
                        }
                    }
                })
            }
        }
        compose.waitForIdle()
    }

    /** What a phone with gesture navigation sends: its side edges take the Back gesture. */
    private fun gestureNavigation() {
        val edge = with(compose.density) { 24.dp.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(edge, 0, edge, 0))
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    private fun podcasts() {
        compose.runOnIdle { navigator?.selectTab(Destination.Podcasts) }
        compose.waitForIdle()
    }

    private fun back(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) = BackEventCompat(40f, 400f, progress, edge)

    private val dispatcher get() = compose.activity.onBackPressedDispatcher

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun width(tag: String): Float = bounds(tag).let { (it.right - it.left).value }

    private fun middle(tag: String): Float = bounds(tag).let { (it.left + it.right).value / 2f }

    @Test
    fun theTabShrinksWithTheFingerAndMuzikIsBehindIt() {
        window()
        gestureNavigation()
        podcasts()
        val full = width("screen-Podcasts")
        val centre = middle("screen-Podcasts")
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.25f))
        }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.5f)) }
        compose.waitForIdle()
        // half way: toward 90 %, leaning away from the left edge, Müzik really there behind it
        val ratio = width("screen-Podcasts") / full
        assertTrue("width half way: $ratio of full", abs(ratio - 0.95f) < 0.02f)
        assertTrue("leans to the right, away from the left edge", middle("screen-Podcasts") > centre + 1f)
        compose.onNodeWithTag("screen-Music").assertExists()
        // given up: back to its full size, still on the tab
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(full, width("screen-Podcasts"), 0.5f)
        // a whole gesture goes back to Müzik
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.6f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("screen-Podcasts").assertDoesNotExist()
        compose.onNodeWithTag("screen-Music").assertExists()
    }

    @Test
    fun fromTheRightEdgeItLeansLeft() {
        window()
        gestureNavigation()
        podcasts()
        val centre = middle("screen-Podcasts")
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(back(0.5f, BackEventCompat.EDGE_RIGHT))
        }
        compose.waitForIdle()
        assertTrue("leans to the left, away from the right edge", middle("screen-Podcasts") < centre - 1f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
    }

    @Test
    fun withReducedMotionNothingFollowsTheFinger() {
        window(reduced = true)
        gestureNavigation()
        podcasts()
        val full = width("screen-Podcasts")
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.5f))
        }
        compose.waitForIdle()
        assertEquals(full, width("screen-Podcasts"), 0.5f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("screen-Music").assertExists()
    }

    /** The user's phone: three-button navigation's Back goes to Müzik as before, and Podcast is as it was left. */
    @Test
    fun withThreeButtonsBackGoesToMuzikAsBefore() {
        window()
        podcasts()
        compose.onNodeWithText("Dokunuş: 0").performClick()
        compose.waitForIdle()
        val full = width("screen-Podcasts")
        // nothing follows a finger here: the shell's Back takes it whole
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.5f))
        }
        compose.waitForIdle()
        assertEquals(full, width("screen-Podcasts"), 0.5f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("screen-Podcasts").assertDoesNotExist()
        compose.onNodeWithTag("screen-Music").assertExists()
        // Podcast again: as it was left
        podcasts()
        compose.onNodeWithText("Dokunuş: 1").assertExists()
    }

    /** A tablet keeps its Back between tabs, gesture navigation or not. */
    @Test
    @Config(qualifiers = "tr-w800dp-h1280dp-mdpi")
    fun aTabletKeepsItsBack() {
        window(WidthClass.Medium)
        gestureNavigation()
        podcasts()
        val full = width("screen-Podcasts")
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.5f))
        }
        compose.waitForIdle()
        assertEquals(full, width("screen-Podcasts"), 0.5f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("screen-Music").assertExists()
    }

    @Test
    fun theRules() {
        // from another tab's first page to Müzik: the tabs' Back
        assertTrue(NavRules.tabBack(Destination.Podcasts, Destination.Music, NavKind.BottomBar))
        assertTrue(NavRules.tabBack(Destination.More, Destination.Music, NavKind.BottomBar))
        // a page's Back, or Müzik's own, is not
        assertFalse(NavRules.tabBack(Destination.Settings, Destination.More, NavKind.BottomBar))
        assertFalse(NavRules.tabBack(Destination.Equalizer, Destination.Music, NavKind.BottomBar))
        // it follows the finger only on a phone with gesture navigation
        assertTrue(NavRules.tabBackFollowsFinger(NavKind.BottomBar, edgeBack = true))
        assertFalse(NavRules.tabBackFollowsFinger(NavKind.BottomBar, edgeBack = false))
        assertFalse(NavRules.tabBackFollowsFinger(NavKind.Rail, edgeBack = true))
        // reduced motion: nothing plays
        assertEquals(EnterTransition.None, tabBackEnter(NagMotion.Reduced, MotionTier.Gesture))
        assertEquals(ExitTransition.None, tabBackExit(NagMotion.Full, MotionTier.Reduced, BackEventCompat.EDGE_LEFT))
        assertTrue(tabBackEnter(NagMotion.Full, MotionTier.Gesture) != EnterTransition.None)
    }
}
