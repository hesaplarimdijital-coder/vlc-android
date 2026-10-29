package app.nagmedar.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
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
 * 1.0.1: "Sıradaki" on a phone is a page of its own over the whole window: the bars and the mini
 * player are under it, a drag on its empty state does not scroll the list behind it (the user's
 * report), Back and its arrow close it, and "Temizle" is there only while something is to come.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi-nokeys")
class QueuePageTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val behind = LazyListState()
    private var open by mutableStateOf(true)
    private var closes = 0

    private fun page(queue: FakeQueue) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                TestShell(
                    WidthClass.Compact,
                    queueOpen = open,
                    // the music list under it, long enough to scroll
                    screen = { _, _ ->
                        LazyColumn(Modifier.fillMaxSize().testTag("behind"), state = behind) {
                            items((1..100).toList()) { Text("Parça $it", Modifier.height(48.dp)) }
                        }
                    },
                    overlay = {
                        QueuePage(open, onClose = {
                            open = false
                            closes++
                        }) { pageModifier ->
                            QueuePanel(queue.entries, queue.current, queue, { open = false; closes++ }, pageModifier, background = NagTheme.colors.background, page = true)
                        }
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theEmptyPageCoversTheWholeWindow() {
        page(FakeQueue(emptyList(), current = -1))
        val window = compose.onRoot().getBoundsInRoot()
        val bounds = compose.onNodeWithTag("queue-page").getBoundsInRoot()
        assertEquals(window, bounds)
        compose.onNodeWithText("Sırada başka bir şey yok").assertExists()
        // nothing to clear: no "Temizle" at all
        compose.onNodeWithTag("queue-clear").assertDoesNotExist()
        // the bars under it are neither drawn nor read out
        compose.onNodeWithText("Radyo").assertDoesNotExist()
    }

    @Test
    fun aDragOnTheEmptyPageDoesNotScrollTheListBehind() {
        page(FakeQueue(emptyList(), current = -1))
        compose.onNodeWithTag("queue-page").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(0, behind.firstVisibleItemIndex)
        assertEquals(0, behind.firstVisibleItemScrollOffset)
    }

    @Test
    fun theListBehindScrollsOnceThePageHasGone() {
        page(FakeQueue(emptyList(), current = -1))
        compose.onNodeWithTag("queue-back").performClick()
        compose.waitForIdle()
        assertFalse(open)
        compose.onNodeWithTag("queue-page").assertDoesNotExist()
        compose.onNodeWithTag("behind").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(behind.firstVisibleItemIndex > 0)
    }

    @Test
    fun backClosesIt() {
        page(FakeQueue(FakeQueue.sample, current = 0))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, closes)
        compose.onNodeWithTag("queue-page").assertDoesNotExist()
    }

    @Test
    fun somethingToComeCanBeCleared() {
        val queue = FakeQueue(FakeQueue.sample, current = 0)
        page(queue)
        compose.onNodeWithTag("queue-clear").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar"), queue.titles)
        compose.onNodeWithTag("queue-clear").assertDoesNotExist()
    }

    @Test
    fun theClearButtonsRule() {
        assertEquals(ClearButton.Enabled, clearShown(upcoming = 3, page = true))
        assertEquals(ClearButton.Enabled, clearShown(upcoming = 1, page = false))
        assertEquals(ClearButton.Hidden, clearShown(upcoming = 0, page = true))
        // the panel beside a tablet's screen keeps it in its place
        assertEquals(ClearButton.Disabled, clearShown(upcoming = 0, page = false))
    }
}
