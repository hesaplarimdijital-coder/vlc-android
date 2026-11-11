package app.nagmedar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.3: the phone's table with a leading item (the shelf of the tracks played last) that gets its content
 * after the rows are on screen, as it does when the kept list is drawn before the history has been read. A
 * list still resting at its top shows the item over the column titles; a list the user has scrolled stays
 * where it is; an item that is there from the start is simply the first one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w390dp-h844dp-hdpi")
class DenseTableLeadingTest {

    @get:Rule
    val compose = createComposeRule()

    private val tracks = (1..60).map { "Parça $it" }

    private var present by mutableStateOf(false)

    private val list = LazyListState()

    private lateinit var scope: CoroutineScope

    private fun table() {
        compose.setContent {
            scope = rememberCoroutineScope()
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize()) {
                    DenseTable(
                        columns = listOf(TableColumn<String>("Başlık", ColumnWidth.Weight(1f)) { it }),
                        rows = tracks,
                        key = { it },
                        onRowClick = { _, _ -> },
                        state = list,
                        leading = { if (present) Box(Modifier.fillMaxWidth().height(200.dp).testTag("shelf")) { Text("Son çalınan müzik") } },
                        leadingPresent = { present },
                    )
                }
            }
        }
    }

    @Test
    fun aLeadingItemThatComesAfterTheRowsIsShownOverTheTitles() {
        table()
        compose.waitForIdle()
        compose.onNodeWithText("Parça 1").assertIsDisplayed()
        // the history has been read: the shelf has its tracks
        present = true
        compose.waitForIdle()
        compose.onNodeWithTag("shelf").assertIsDisplayed()
        compose.onNodeWithText("Son çalınan müzik").assertIsDisplayed()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
        // the titles and the rows come under it
        val shelf = compose.onNodeWithTag("shelf").getUnclippedBoundsInRoot()
        val titles = compose.onNodeWithText("Başlık").getUnclippedBoundsInRoot()
        val first = compose.onNodeWithText("Parça 1").getUnclippedBoundsInRoot()
        assertTrue("titles $titles under the shelf $shelf", titles.top >= shelf.bottom)
        assertTrue("first row $first under the titles $titles", first.top >= titles.bottom)
    }

    @Test
    fun aListTheUserHasScrolledStaysWhereItIs() {
        table()
        compose.waitForIdle()
        compose.runOnIdle { scope.launch { list.scrollToItem(20) } }
        compose.waitForIdle()
        val index = list.firstVisibleItemIndex
        assertTrue("scrolled to $index", index >= 20)
        present = true
        compose.waitForIdle()
        assertEquals(index, list.firstVisibleItemIndex)
        compose.onNodeWithTag("shelf").assertDoesNotExist()
    }

    @Test
    fun aLeadingItemThereFromTheStartIsTheFirstItem() {
        present = true
        table()
        compose.waitForIdle()
        compose.onNodeWithTag("shelf").assertIsDisplayed()
        assertEquals(0, list.firstVisibleItemIndex)
        compose.onNodeWithText("Parça 1").assertIsDisplayed()
    }
}
