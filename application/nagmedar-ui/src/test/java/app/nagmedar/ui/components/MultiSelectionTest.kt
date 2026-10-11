package app.nagmedar.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-405: clicks, Ctrl and Shift as on desktops, all, a band, and what a filter takes away. */
class MultiSelectionTest {

    private val order = listOf("a", "b", "c", "d", "e")

    @Test
    fun aClickTakesOneCtrlAddsOrRemoves() {
        val selection = MultiSelection()
        selection.click("b", order)
        assertEquals(setOf("b"), selection.keys)
        selection.click("d", order, ctrl = true)
        assertEquals(setOf("b", "d"), selection.keys)
        selection.click("b", order, ctrl = true)
        assertEquals(setOf("d"), selection.keys)
        selection.click("a", order)
        assertEquals(setOf("a"), selection.keys)
    }

    @Test
    fun shiftTakesTheRangeFromTheLastClick() {
        val selection = MultiSelection()
        selection.click("b", order)
        selection.click("d", order, shift = true)
        assertEquals(setOf("b", "c", "d"), selection.keys)
        // backwards from the same anchor, the range replaces the last one
        selection.click("a", order, shift = true)
        assertEquals(setOf("a", "b"), selection.keys)
        // Ctrl+Shift adds a range to what is selected
        selection.click("e", order, ctrl = true)
        selection.click("d", order, ctrl = true, shift = true)
        assertEquals(setOf("a", "b", "d", "e"), selection.keys)
    }

    @Test
    fun allTheBandAndWhatIsNoLongerListed() {
        val selection = MultiSelection()
        selection.selectAll(order)
        assertEquals(order.toSet(), selection.keys)
        selection.retain(listOf("a", "c"))
        assertEquals(setOf("a", "c"), selection.keys)
        selection.select(setOf("d"), base = selection.keys)
        assertEquals(setOf("a", "c", "d"), selection.keys)
        selection.select(setOf("e"))
        assertEquals(setOf("e"), selection.keys)
        selection.clear()
        assertTrue(selection.keys.isEmpty())
        assertEquals(null, selection.anchor)
    }
}
