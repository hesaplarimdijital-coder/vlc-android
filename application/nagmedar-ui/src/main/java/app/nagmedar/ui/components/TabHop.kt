package app.nagmedar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * 1.0.8 (Desktop Kit 2.8.0): how Tab leaves a collection that is one stop of its own ([DenseTable], a cover grid) at
 * once, from wherever in it the focus is. Left to itself, Tab would look for the next stop among the rows: none takes the
 * focus (one stop), so a lazy list would lay out every row after the ones in view, one by one, before giving up (a freeze
 * in a long library), and the focusable things in the rows (a rating's stars, a row's "⋯") would each be a stop.
 *
 * Instead the focus goes to a spot just past the collection ([after], or [before] it with Shift+Tab) and moves on from
 * there, so the rows are never searched. The spots take the focus only during that move ([HopSpot]).
 */
@Stable
internal class TabHop {
    val before = FocusRequester()
    val after = FocusRequester()

    /** Whether the spots take the focus: only while the focus hops over the collection. */
    var hopping = false

    /** Whether one of the spots has the focus (the move found nothing past it). */
    val atSpot: Boolean get() = beforeFocused || afterFocused
    internal var beforeFocused = false
    internal var afterFocused = false

    /** Whether the focus is somewhere in the collection's rows or cells. */
    var inside = false

    /**
     * Tab or Shift+Tab while the focus is [inside]: the focus leaves the collection, and true. When there is nowhere to
     * go, [stay] puts it back in the collection (by default the way it came: into the collection from the spot).
     */
    fun handle(event: KeyEvent, focusManager: FocusManager, stay: (() -> Unit)? = null): Boolean {
        if (event.type != KeyEventType.KeyDown || event.key != Key.Tab || !inside) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        val forward = !event.isShiftPressed
        hopping = true
        try {
            runCatching { (if (forward) after else before).requestFocus() }
            focusManager.moveFocus(if (forward) FocusDirection.Next else FocusDirection.Previous)
        } finally {
            hopping = false
        }
        if (atSpot) {
            if (stay != null) stay() else focusManager.moveFocus(if (afterFocused) FocusDirection.Previous else FocusDirection.Next)
        }
        return true
    }
}

/** One of a [TabHop]'s spots ([TabHop.before] or [TabHop.after]): no size, nothing to see or read, a focus target only while the focus hops. */
@Composable
internal fun HopSpot(hop: TabHop, requester: FocusRequester) {
    Box(
        Modifier
            .focusRequester(requester)
            .onFocusChanged { if (requester === hop.before) hop.beforeFocused = it.isFocused else hop.afterFocused = it.isFocused }
            .focusProperties { canFocus = hop.hopping }
            .focusTarget(),
    )
}
