package app.nagmedar.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/**
 * 1.1.3: where a [CollapsingTop]'s header is: how far it has gone up ([offset], px; 0 while all of it shows) of how far
 * it may ([fraction]: 0 shown, 1 folded away). A list scrolled towards its end takes the header up before it moves
 * itself; a list back at its top, pulled on, brings it down again; let go halfway, it goes to the nearer end.
 */
@Stable
class CollapsingTopState {
    var offset by mutableFloatStateOf(0f)
        private set

    /** How far the header may go up (px): its height less what stays of it ([CollapsingTop]'s reserve). */
    internal var limit = 0f

    /** With reduced motion a header let go halfway jumps to the nearer end. */
    internal var instant = false

    /** What a push back up takes back before the header folds: a pull to refresh's disc on its way down. */
    internal val holds = ArrayList<() -> Boolean>()

    /** 0 while the header shows, 1 once it has folded away. */
    val fraction: Float get() = if (limit <= 0f) 0f else (-offset / limit).coerceIn(0f, 1f)

    /** Whether all of the header shows (a pull on the list at its top may then refresh it). */
    val expanded: Boolean get() = offset > -0.5f

    /** The header shown again at once. */
    fun expand() {
        offset = 0f
    }

    private fun moveBy(dy: Float): Float {
        // a header grown shorter meanwhile: from where it is now
        val old = offset.coerceIn(-limit, 0f)
        val new = (old + dy).coerceIn(-limit, 0f)
        offset = new
        return new - old
    }

    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || holds.any { it() }) return Offset.Zero
            return Offset(0f, moveBy(available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            return Offset(0f, moveBy(available.y))
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    /** A header let go halfway, to the nearer end. */
    internal suspend fun settle() {
        val f = fraction
        if (f <= 0f || f >= 1f) return
        val target = if (f < 0.5f) 0f else -limit
        if (instant) {
            offset = target
            return
        }
        animate(offset, target, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { value, _ -> offset = value.coerceIn(-limit, 0f) }
    }
}

/** How long a header let go halfway takes to the nearer end. */
private const val SETTLE_MS = 220

@Composable
fun rememberCollapsingTopState(): CollapsingTopState = remember { CollapsingTopState() }

/** The folding top a list lies under, for what in it works with it ([PullToRefresh] waits for the whole header). */
val LocalCollapsingTop = staticCompositionLocalOf<CollapsingTopState?> { null }

/**
 * 1.1.3: [header] over [pinned] over [content], the header folding away as [content]'s lists scroll towards their end
 * ([state]), so that they have the window's height; [pinned] (a page's tabs, its toolbar, its search field) stays, at
 * the top once the header has gone, or [reserve] under it (a phone's chips over the header's last part).
 */
@Composable
fun CollapsingTop(
    state: CollapsingTopState,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    reserve: Dp = 0.dp,
    pinned: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    state.instant = NagTheme.motion.reduced || LocalMotionTier.current == MotionTier.Reduced
    // the keyboard moving into the folded header (its search field, its buttons) brings it down
    val focusable: @Composable () -> Unit = { Box(Modifier.onFocusChanged { if (it.hasFocus) state.expand() }) { header() } }
    CompositionLocalProvider(LocalCollapsingTop provides state) {
        Layout(
            contents = listOf(focusable, pinned, content),
            modifier = modifier.fillMaxSize().clipToBounds().nestedScroll(state.connection),
        ) { (headers, pins, contents), constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = Constraints.Infinity)
            val h = headers.map { it.measure(loose) }
            val headerHeight = h.maxOfOrNull { it.height } ?: 0
            state.limit = (headerHeight - reserve.roundToPx()).coerceAtLeast(0).toFloat()
            // read while measuring: the lists under it take the room it leaves
            val off = state.offset.coerceIn(-state.limit, 0f).roundToInt()
            val p = pins.map { it.measure(loose) }
            val pinHeight = p.maxOfOrNull { it.height } ?: 0
            val top = headerHeight + off + pinHeight
            val bounded = constraints.hasBoundedHeight
            val rest = if (bounded) (constraints.maxHeight - top).coerceAtLeast(0) else 0
            val c = contents.map { if (bounded) it.measure(Constraints.fixed(constraints.maxWidth, rest)) else it.measure(loose) }
            val height = if (bounded) constraints.maxHeight else top + (c.maxOfOrNull { it.height } ?: 0)
            layout(constraints.maxWidth, height) {
                h.forEach { it.place(0, off) }
                p.forEach { it.place(0, headerHeight + off) }
                c.forEach { it.place(0, top) }
            }
        }
    }
}
