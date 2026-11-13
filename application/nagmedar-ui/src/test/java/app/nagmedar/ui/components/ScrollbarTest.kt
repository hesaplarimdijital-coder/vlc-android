package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
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
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's high finding 3): a mouse has a scrollbar to drag along a long table
 * (nothing showed how long a list was, nor let a mouse go to its end at once); a finger scrolls the rows.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w900dp-h600dp-mdpi")
class ScrollbarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val names = List(200) { "Parça %03d".format(it + 1) }
    private val clicked = mutableListOf<String>()
    private lateinit var state: LazyListState

    private fun table(inputMode: InputMode) {
        state = LazyListState()
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                Column {
                    DenseTable(
                        columns = listOf(TableColumn<String>("Ad", ColumnWidth.Weight(1f)) { it }),
                        rows = names,
                        key = { it },
                        onRowClick = { row, _ -> clicked += row },
                        state = state,
                        glassHeader = true,
                        modifier = Modifier.fillMaxWidth().height(400.dp).testTag("table"),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aMouseDragsTheThumbToTheEndAndNoRowIsClicked() {
        table(InputMode.Pointer)
        compose.onNodeWithTag("table").performMouseInput {
            // the thumb starts under the header, along the end edge
            moveTo(Offset(width - 6f, 60f))
            press()
            moveTo(Offset(width - 6f, height / 2f))
            moveTo(Offset(width - 6f, height - 2f))
            release()
        }
        compose.waitForIdle()
        assertTrue("the end came into view: ${state.firstVisibleItemIndex}", state.layoutInfo.visibleItemsInfo.last().index == names.lastIndex)
        assertEquals("the press was the scrollbar's", emptyList<String>(), clicked)
    }

    @Test
    fun aPressOnTheTrackJumpsThere() {
        table(InputMode.Pointer)
        compose.onNodeWithTag("table").performMouseInput {
            moveTo(Offset(width - 6f, height * 0.75f))
            press()
            release()
        }
        compose.waitForIdle()
        val first = state.firstVisibleItemIndex
        assertTrue("the view went toward the end: $first", first in 100 until names.size)
        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun aFingerAtTheEdgeTapsTheRow() {
        table(InputMode.Touch)
        compose.onNodeWithTag("table").performTouchInput { click(Offset(width - 6f, 100f)) }
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(1, clicked.size)
    }
}
