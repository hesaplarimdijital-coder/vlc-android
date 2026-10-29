package app.nagmedar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
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
 * 1.0: the music table's column titles on a tablet: a long press in their band opens the column chooser or nothing,
 * never the menu of the row passing beneath the glass header; a fixed column is as wide as its title.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class DenseTableHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private val rowMenus = mutableListOf<String>()
    private val headerMenus = mutableListOf<Int>()

    private val tracks = (1..40).map { "Profile track $it" }

    private fun table(durationWidth: Int = 68) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                Box(Modifier.size(900.dp, 600.dp)) {
                    DenseTable(
                        columns = listOf(
                            TableColumn<String>("Title", ColumnWidth.Weight(1.6f)) { it },
                            TableColumn("Artist", ColumnWidth.Weight(1f)) { "Nağmedar QA" },
                            TableColumn("Duration", ColumnWidth.Fixed(durationWidth.dp), alignEnd = true, mono = true) { "0:20" },
                        ),
                        rows = tracks,
                        key = { it },
                        onRowClick = { _, _ -> },
                        // scrolled: rows pass beneath the header
                        state = LazyListState(firstVisibleItemIndex = 6),
                        sort = TableSort(2, ascending = true),
                        onSortChange = {},
                        rowMenu = { item, _ ->
                            rowMenus += item
                            listOf(MenuEntry.Item("Add to queue", {}))
                        },
                        rowModifier = { Modifier.testTag("row-$it") },
                        headerMenu = { column ->
                            headerMenus += column
                            listOf(MenuEntry.Item("Album artist", {}, checked = false))
                        },
                        glassHeader = true,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aLongPressBesideTheTitlesIsNoRowsPress() {
        table()
        val title = compose.onNodeWithText("Title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // the table's edge, at the titles' height: no title there, but a row beneath the glass
        val rows = tracks.mapNotNull { track ->
            runCatching { compose.onNodeWithTag("row-$track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }.getOrNull()?.let { track to it }
        }
        val beneath = rows.firstOrNull { (_, bounds) -> bounds.top < title.bottom && bounds.bottom > title.top }
        assertTrue("a row lies beneath the titles $title: ${rows.take(8)}", beneath != null)
        val point = Offset(1f, (maxOf(title.top, beneath!!.second.top) + minOf(title.bottom, beneath.second.bottom)) / 2)
        compose.onRoot().performTouchInput { longClick(point) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), rowMenus)
    }

    @Test
    fun aLongPressOnATitleOpensTheColumnChooser() {
        table()
        compose.onNodeWithText("Artist", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(listOf(1), headerMenus.distinct())
        assertEquals(emptyList<String>(), rowMenus)
    }

    @Test
    fun aFixedColumnIsAsWideAsItsTitle() {
        // the sort's arrow beside it too, as the music table's 68 dp did not have room for
        table(durationWidth = 40)
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText("Duration", useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        // not "Durati…": one line, not cut short
        assertEquals(1, layout.lineCount)
        assertFalse("Duration is read whole", layout.isLineEllipsized(0))
    }
}
