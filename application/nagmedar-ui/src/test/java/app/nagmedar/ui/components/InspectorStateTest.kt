package app.nagmedar.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop Kit 1.0: the inspector's width within its bounds, hidden and shown, and what it keeps. */
class InspectorStateTest {

    private val kept = mutableListOf<Pair<Float, Boolean>>()

    private fun state(width: Float = 340f, collapsed: Boolean = false) = InspectorState(width, collapsed) { w, c -> kept += w to c }

    @Test
    fun theDividerMovesWithinTheBounds() {
        val state = state()
        state.resizeBy(40f, 260.dp, 560.dp)
        assertEquals(380f, state.widthDp)
        state.resizeBy(-500f, 260.dp, 560.dp)
        assertEquals(260f, state.widthDp)
        state.resizeBy(1000f, 260.dp, 560.dp)
        assertEquals(560f, state.widthDp)
        // a narrower window: the most it may take now
        state.resizeBy(0f, 260.dp, 400.dp)
        assertEquals(400f, state.widthDp)
        state.resizeTo(300f, 260.dp, 560.dp)
        assertEquals(300f, state.widthDp)
        // moving is not keeping: the drag's end keeps
        assertTrue(kept.isEmpty())
        state.commit()
        assertEquals(listOf(300f to false), kept)
    }

    @Test
    fun hidingAndShowingIsKeptAtOnce() {
        val state = state(collapsed = true)
        state.toggle()
        assertEquals(false, state.collapsed)
        state.toggle()
        assertEquals(true, state.collapsed)
        assertEquals(listOf(340f to false, 340f to true), kept)
    }

    @Test
    fun aStoredWidthOutsideTheBoundsComesBackWithin() {
        val state = state(width = 900f)
        state.resizeBy(-16f, 260.dp, 560.dp)
        assertEquals(544f, state.widthDp)
    }
}
