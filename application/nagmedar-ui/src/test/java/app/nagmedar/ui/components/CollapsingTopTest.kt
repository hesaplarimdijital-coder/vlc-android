package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.1.3: a page's top folding away as its lists scroll (a tablet's band and masthead, Radio's and Podcasts'): the header
 * goes up before the list moves, the pinned row stays at the top, the list takes the room; back at the list's top a pull
 * brings it down, and a pull to refresh under it waits for all of it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class CollapsingTopTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var top: CollapsingTopState
    private lateinit var list: LazyListState
    private var refreshing by mutableStateOf(false)
    private var refreshes = 0

    private fun page(refresh: Boolean = false) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                top = rememberCollapsingTopState()
                CollapsingTop(
                    top,
                    header = { Box(Modifier.fillMaxWidth().height(200.dp).testTag("header")) },
                    pinned = { Box(Modifier.fillMaxWidth().height(48.dp).testTag("tabs")) },
                ) {
                    list = rememberLazyListState()
                    val rows = @Composable {
                        LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) {
                            items(40) { i -> Text("İstasyon $i", Modifier.fillMaxWidth().height(56.dp)) }
                        }
                    }
                    if (refresh) PullToRefresh(refreshing, onRefresh = { refreshes++; refreshing = true }) { rows() } else rows()
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tabsTop() = compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot().top.value

    @Test
    fun theHeaderFoldsAwayFirstAndComesBackAtTheListsTop() {
        page()
        assertEquals(0f, top.fraction, 0.001f)
        assertEquals(200f, tabsTop(), 0.5f)
        // a short push folds part of it; the list has not moved
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = centerY + 60f, endY = centerY - 60f, durationMillis = 800) }
        compose.waitForIdle()
        assertTrue(top.fraction > 0f)
        // a long one folds all of it: the tabs at the top, the list under them to the window's bottom
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 800) }
        compose.waitForIdle()
        assertEquals(1f, top.fraction, 0.001f)
        assertEquals(0f, tabsTop(), 0.5f)
        val list = compose.onNodeWithTag("list").getUnclippedBoundsInRoot()
        assertEquals(48f, list.top.value, 0.5f)
        assertEquals(891f, list.bottom.value, 1f)
        // pulled down past the list's top, it comes back whole
        repeat(3) { compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 800) } }
        compose.waitForIdle()
        assertEquals(0f, top.fraction, 0.001f)
        assertEquals(200f, tabsTop(), 0.5f)
    }

    @Test
    fun letGoHalfwayItGoesToTheNearerEnd() {
        page()
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = centerY + 70f, endY = centerY - 70f, durationMillis = 1500) }
        compose.waitForIdle()
        // folded most of the way or not much: either end, never halfway
        assertTrue(top.fraction == 0f || top.fraction == 1f)
    }

    @Test
    fun underAFoldedHeaderAPullBringsItDownBeforeItRefreshes() {
        page(refresh = true)
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 800) }
        compose.waitForIdle()
        assertEquals(1f, top.fraction, 0.001f)
        // the list back at its top by itself (not by a finger): the header stays folded
        compose.runOnIdle { runBlocking { list.scrollToItem(0) } }
        compose.waitForIdle()
        assertEquals(1f, top.fraction, 0.001f)
        // a pull goes to the header first: nothing is read again
        repeat(2) { compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 130f, durationMillis = 800) } }
        compose.waitForIdle()
        assertEquals(0, refreshes)
        // with all of it shown, a long pull refreshes
        repeat(2) { compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 400) } }
        compose.waitForIdle()
        assertEquals(0f, top.fraction, 0.001f)
        assertEquals(1, refreshes)
    }
}
