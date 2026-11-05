package app.nagmedar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import kotlinx.coroutines.delay

/**
 * 1.0.3 (Desktop Kit 2.3.0): the arrival of a list's rows on a phone. The first [ROWS] rows on screen
 * when the list appears fade in and rise [RISE_DP] dp into place one after another ([STAGGER_MS] apart,
 * each over [DURATION_MS]); rows that come later (scrolled into view, added) are simply there. Wider
 * windows and reduced motion ("Animasyonları kaldır"): nothing moves.
 *
 * One is taken with [rememberListAppear] where the list is, and each row is marked with [listAppear]:
 *
 * ```
 * val appear = rememberListAppear(view)
 * LazyColumn { itemsIndexed(rows) { index, row -> Row(Modifier.listAppear(appear, index)) { … } } }
 * ```
 *
 * The keys of [rememberListAppear] say when the list is new (another way into the library, another
 * album's tracks): its rows then arrive in turn again. A list kept composed under its detail keeps
 * its arrival, so it does not arrive again when the detail closes.
 */
@Stable
class ListAppear internal constructor(internal val enabled: Boolean) {
    /** Set once the first rows' window has passed: rows composed after it are simply there. */
    internal var settled = false

    companion object {
        /** How many rows arrive in turn; the rest are there at once. */
        const val ROWS = 8

        /** The delay between one row's arrival and the next's. */
        const val STAGGER_MS = 15

        /** Each row's fade and rise. */
        const val DURATION_MS = 180

        /** How far a row rises into place. */
        const val RISE_DP = 12

        /** How long after the list appears a row still counts as one of the first. */
        internal const val WINDOW_MS = 120L
    }
}

/** The [ListAppear] of a list; a new one when [keys] change (see [ListAppear]). */
@Composable
fun rememberListAppear(vararg keys: Any?): ListAppear {
    val enabled = LocalMotionTier.current == MotionTier.Gesture
    val appear = remember(enabled, *keys) { ListAppear(enabled) }
    LaunchedEffect(appear) {
        delay(ListAppear.WINDOW_MS)
        appear.settled = true
    }
    return appear
}

/**
 * Marks the [index]th row of a list taken with [rememberListAppear]: one of the first [ListAppear.ROWS]
 * rows on screen when the list appears fades in and rises into place in its turn; any other row is
 * simply there. Put it first in the row's modifiers; it only draws differently (a layer, gone once the
 * row has arrived, so a row at rest is drawn as before), so the list is laid out once, as at rest.
 */
@Composable
fun Modifier.listAppear(appear: ListAppear, index: Int): Modifier {
    if (!appear.enabled || index >= ListAppear.ROWS) return this
    // a row composed once the window has passed starts, and stays, in place
    val progress = remember(appear) { Animatable(if (appear.settled) 1f else 0f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            delay(index * ListAppear.STAGGER_MS.toLong())
            progress.animateTo(1f, tween(ListAppear.DURATION_MS, easing = NagMotion.StandardDecelerate))
        }
    }
    val arrived by remember(progress) { derivedStateOf { progress.value >= 1f } }
    if (arrived) return this
    return this.graphicsLayer {
        val value = progress.value
        alpha = value
        translationY = (1f - value) * ListAppear.RISE_DP.dp.toPx()
    }
}
