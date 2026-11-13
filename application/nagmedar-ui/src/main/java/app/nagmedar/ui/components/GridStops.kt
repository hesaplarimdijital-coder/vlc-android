package app.nagmedar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import app.nagmedar.ui.input.TabNavigation

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's high finding 2): a lazy grid of covers as one stop of the Tab key, as a
 * table is: Tab reaches the cover last focused, else the selected one, when it is in view, else the first cover in view;
 * the next Tab leaves the grid at once ([TabHop]: the covers are never searched one by one); the arrows move among the
 * covers. Made by [rememberGridStops]: the grid takes [grid], each cover [cell] around its click, a cover's own buttons (its
 * "⋯") [buttons], and [Around] holds the grid between the spots Tab goes on from.
 */
@Stable
class GridStops internal constructor(private val state: LazyGridState, private val focusManager: FocusManager) {
    internal val hop = TabHop()
    private var focused: Any? = null
    private var last: Any? = null
    internal var keys: Set<Any> = emptySet()
    internal var selected: Any? = null

    /** The one cover Tab reaches; read when the focus moves, not composed. */
    internal fun rovingKey(): Any? {
        val visible = state.layoutInfo.visibleItemsInfo
        val active = focused ?: last ?: selected
        return if (active != null && active in keys && visible.any { it.key == active }) active else visible.firstOrNull { it.key in keys }?.key
    }

    /** On the cover of [key] (its key in the grid), around its click. */
    fun cell(key: Any): Modifier = Modifier
        .focusProperties { canFocus = !TabNavigation.active || key == rovingKey() }
        .onFocusChanged { state ->
            if (state.isFocused) {
                focused = key
                last = key
            } else if (focused == key) {
                focused = null
            }
        }

    /** On a cover's own buttons: reached from the cover (and with the mouse), not by Tab. */
    val buttons: Modifier = Modifier.focusProperties { canFocus = !TabNavigation.active }

    /** On the grid itself: Tab from anywhere in it leaves it. */
    val grid: Modifier = Modifier
        .onFocusChanged { hop.inside = it.hasFocus }
        .onPreviewKeyEvent { hop.handle(it, focusManager) }
}

/** [GridStops] for the grid of [state] whose covers have [keys] (as the grid's item keys), [selected] the one selected. */
@Composable
fun rememberGridStops(state: LazyGridState, keys: List<Any>, selected: Any? = null): GridStops {
    val focusManager = LocalFocusManager.current
    val stops = remember(state, focusManager) { GridStops(state, focusManager) }
    stops.keys = remember(keys) { keys.toHashSet() }
    stops.selected = selected
    return stops
}

/** The grid between the two spots Tab goes on from, past the covers. */
@Composable
fun GridStops.Around(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        HopSpot(hop, hop.before)
        content()
        HopSpot(hop, hop.after)
    }
}
