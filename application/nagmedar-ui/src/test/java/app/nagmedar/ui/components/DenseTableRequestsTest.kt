package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, DeskMail's request 2 and the design audit's table findings): a cell that is a button of its
 * own, the sort read out, an empty table that says why, rows in bold, a check box for each row in a finger's select mode.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1000dp-h700dp-mdpi")
class DenseTableRequestsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Episode(val id: Int, val show: String, val title: String, val fresh: Boolean)

    private var episodes by mutableStateOf(
        listOf(
            Episode(1, "Açık Bilim", "Kara delikler ve ışık", fresh = true),
            Episode(2, "Kayıp Kitaplar", "Bir yazarın son mektubu", fresh = false),
        ),
    )
    private val opened = mutableListOf<Int>()
    private val marked = mutableListOf<Int>()
    private var sort by mutableStateOf<TableSort?>(TableSort(1, ascending = true))

    private fun table(selection: MultiSelection? = null, inputMode: InputMode = InputMode.Pointer) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                DenseTable(
                    columns = listOf(
                        TableColumn(
                            "İşaret", ColumnWidth.Fixed(40.dp), sortable = false,
                            content = { episode, _ -> Icon(NagIcons.Check, contentDescription = if (episode.fresh) "Yeni" else "Dinlendi") },
                            onCellClick = { episode -> marked += episode.id },
                            cellClickLabel = { "İşareti değiştir" },
                        ) { if (it.fresh) "yeni" else "" },
                        TableColumn("Program", ColumnWidth.Weight(1f)) { it.show },
                        TableColumn("Bölüm", ColumnWidth.Weight(2f), ellipsis = TextOverflow.MiddleEllipsis) { it.title },
                    ),
                    rows = episodes,
                    key = { it.id },
                    onRowClick = { episode, _ -> opened += episode.id },
                    sort = sort,
                    onSortChange = { sort = it },
                    rowEmphasis = { it.fresh },
                    emptyContent = { Text("Bu listede bölüm yok") },
                    selection = selection,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aCellButtonDoesNotOpenTheRow() {
        table()
        compose.onNodeWithContentDescription("Yeni").performClick()
        assertEquals(listOf(1), marked)
        assertEquals(emptyList<Int>(), opened)
        compose.onNodeWithText("Kara delikler ve ışık").performClick()
        assertEquals(listOf(1), opened)
    }

    @Test
    fun theSortedColumnSaysWhichWayAndTheTitlesAreHeadings() {
        table()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Artan sırada")).assertExists()
        compose.onNodeWithText("Program").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Azalan sırada")).assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Bölüm")))).assertExists()
    }

    @Test
    fun anEmptyTableSaysWhy() {
        episodes = emptyList()
        table()
        compose.onNodeWithText("Bu listede bölüm yok").assertExists()
    }

    /** A finger selecting: a check box on every row and "Tümünü seç" in the header, with three states. */
    @Test
    fun theSelectModeHasCheckBoxes() {
        val selection = MultiSelection()
        selection.startSelecting(1)
        table(selection, InputMode.Touch)
        val all = compose.onNodeWithContentDescription("Tümünü seç")
        all.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.Indeterminate))
        all.performClick()
        compose.waitForIdle()
        assertEquals(setOf<Any>(1, 2), selection.keys)
        compose.onAllNodesWithContentDescription("Tümünü seç").assertCountEquals(1)
        all.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.On))
    }

    /**
     * 1.0.8: another order starts the rows at the top. Keyed rows kept the first one in view where the new order put it:
     * forty rows at their top, sorted the other way, showed their end (the walk's workstation pass, "By size").
     */
    @Test
    fun anotherOrderStartsTheRowsAtTheTop() {
        val state = LazyListState()
        var order by mutableStateOf(true)
        val rows = (1..40).map { Episode(it, "Program", "Bölüm %02d".format(it), fresh = false) }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                DenseTable(
                    columns = listOf(TableColumn("Bölüm", ColumnWidth.Weight(1f)) { it.title }),
                    rows = if (order) rows else rows.reversed(),
                    key = { it.id },
                    onRowClick = { _, _ -> },
                    state = state,
                    sort = TableSort(0, ascending = order),
                )
            }
        }
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        compose.onNodeWithText("Bölüm 01").assertExists()
        order = false
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        compose.onNodeWithText("Bölüm 40").assertExists()
        compose.onNodeWithText("Bölüm 01").assertDoesNotExist()
    }
}
