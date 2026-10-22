package app.nagmedar.ui.components

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A table's sort stays on its column when the window's width takes columns away or brings them back. */
class ColumnSortTest {

    private val medium = listOf("Title", "Year", "Imdb", "Duration")
    private val expanded = listOf("Title", "Year", "Imdb", "Tomatoes", "Video", "Audio", "Duration")
    private val compact = listOf("Title", "Duration")

    @Test
    fun theSortFollowsItsColumnAcrossWidths() {
        // sorted by the length in a medium window: its fourth column
        val sort = ColumnSort.of(TableSort(3, ascending = false), medium)!!
        assertEquals(ColumnSort("Duration", ascending = false), sort)
        // wider, the fourth column is Rotten Tomatoes: the sort is still the length's, now seventh
        assertEquals(TableSort(6, ascending = false), sort.among(expanded))
        assertEquals(TableSort(1, ascending = false), sort.among(compact))
    }

    @Test
    fun aColumnNotShownSortsNothingUntilItIsBack() {
        val sort = ColumnSort("Tomatoes", ascending = true)
        assertNull(sort.among(medium))
        assertEquals(TableSort(3, ascending = true), sort.among(expanded))
        assertNull(ColumnSort.of(TableSort(9, ascending = true), medium))
    }

    @Test
    fun theSortIsKeptByItsColumnsId() {
        val scope = SaverScope { true }
        val saver = ColumnSort.StateSaver
        val saved = with(saver) { scope.save(ColumnSort("Duration", ascending = false)) }
        assertEquals("-Duration", saved)
        assertEquals(ColumnSort("Duration", ascending = false), saver.restore(saved!!))
        assertEquals(ColumnSort("Year", ascending = true), saver.restore("+Year"))
        assertNull(with(saver) { scope.save(null) })
        assertNull(saver.restore("Duration"))
    }
}
