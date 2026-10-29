package app.nagmedar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * 1.0.1: what lies over a page keeps it still. An empty layer over a list lets a drag through to
 * it (the user's "scroll yapınca müzikler kayıyor"); with [blockPointerBelow] it does not.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class ModalLayerTest {

    @get:Rule
    val compose = createComposeRule()

    private val list = LazyListState()

    private fun layers(blocks: Boolean) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = list) {
                        items((1..100).toList()) { Text("Parça $it", Modifier.height(48.dp)) }
                    }
                    // an empty state over the list: nothing in it takes a touch of its own
                    Box(Modifier.fillMaxSize().then(if (blocks) Modifier.blockPointerBelow() else Modifier).testTag("over"))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("over").performTouchInput { swipeUp() }
        compose.waitForIdle()
    }

    @Test
    fun anEmptyLayerLetsTheDragThrough() {
        layers(blocks = false)
        assertTrue(list.firstVisibleItemIndex > 0)
    }

    @Test
    fun aBlockingLayerKeepsTheListStill() {
        layers(blocks = true)
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
    }
}
