package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.rememberShellContentPadding
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.0.8 (Desktop Kit 2.8.0, DeskMail's request 14): a list pulled down is read again, with a finger only. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class PullToRefreshTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var refreshing by mutableStateOf(false)
    private var refreshes = 0

    private fun list(inputMode: InputMode) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                PullToRefresh(refreshing = refreshing, onRefresh = { refreshes++; refreshing = true }) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(30) { i -> Text("Bölüm $i", Modifier.fillMaxWidth().height(56.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aLongPullRefreshesAndTheDiscStaysWhileItDoes() {
        list(InputMode.Touch)
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(1, refreshes)
        compose.onNodeWithContentDescription("Yenileniyor").assertExists()
        refreshing = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Yenileniyor").assertDoesNotExist()
        compose.onNodeWithTag("refresh-disc").assertDoesNotExist()
    }

    @Test
    fun aShortPullDoesNot() {
        list(InputMode.Touch)
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 110f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(0, refreshes)
        compose.onNodeWithTag("refresh-disc").assertDoesNotExist()
    }

    @Test
    fun theMouseAndTheKeyboardLeaveItAlone() {
        list(InputMode.Pointer)
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(0, refreshes)
    }

    @Test
    fun inTheShellTheDiscComesOutUnderTheBarOrAtTheListsOwnTop() {
        var topPad = 0.dp
        var header by mutableStateOf(false)
        refreshing = true
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { _, _ ->
                    val padding = rememberShellContentPadding()
                    topPad = padding.calculateTopPadding()
                    val rows: @Composable () -> Unit = {
                        LazyColumn(Modifier.fillMaxSize()) { items(30) { i -> Text("Bölüm $i", Modifier.fillMaxWidth().height(56.dp)) } }
                    }
                    if (!header) {
                        // the list lies under the top bar
                        PullToRefresh(refreshing, onRefresh = {}, modifier = Modifier.testTag("box"), content = rows)
                    } else {
                        // a field above the list, below the bar
                        Column(Modifier.fillMaxSize().padding(top = topPad)) {
                            Box(Modifier.fillMaxWidth().height(48.dp))
                            PullToRefresh(refreshing, onRefresh = {}, modifier = Modifier.testTag("box"), content = rows)
                        }
                    }
                })
            }
        }
        compose.waitForIdle()
        val disc = { compose.onNodeWithContentDescription("Yenileniyor").fetchSemanticsNode().boundsInRoot }
        val box = { compose.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot }
        // at rest the disc is 20 dp below where it comes out: three quarters of the 80 dp threshold, less its own 40 dp
        assertTrue("a bar above: $topPad", topPad > 0.dp)
        assertEquals(box().top + topPad.value + 20f, disc().top, 1f)
        header = true
        compose.waitForIdle()
        assertEquals("at the list's top, not a bar's height lower", box().top + 20f, disc().top, 1f)
    }

    @Test
    fun withReducedMotionTheDiscJumpsToItsPlace() {
        // a refresh under way as the list comes: the disc comes down by itself
        refreshing = true
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Reduced) {
                    PullToRefresh(refreshing = refreshing, onRefresh = {}, modifier = Modifier.testTag("box")) {
                        LazyColumn(Modifier.fillMaxSize()) { items(30) { i -> Text("Bölüm $i", Modifier.fillMaxWidth().height(56.dp)) } }
                    }
                }
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val top = compose.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot.top
        // a spring would still be on its way three frames in
        assertEquals("at rest at once", top + 20f, compose.onNodeWithContentDescription("Yenileniyor").fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
