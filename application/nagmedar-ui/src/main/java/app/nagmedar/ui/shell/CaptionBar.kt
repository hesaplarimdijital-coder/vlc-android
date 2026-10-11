package app.nagmedar.ui.shell

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The window's caption bar in desktop windowing (S-306). From Android 15 the system draws only the
 * window's buttons (minimise, maximise, close) over a transparent caption, and the app draws the
 * bar: the menu bar and the search field go up into it. [left] and [right] are what the system's
 * buttons take at each end; the rest of the bar still moves the window, but what the app put
 * there to be clicked takes its clicks ([captionTarget]).
 */
@Immutable
data class CaptionBar(val height: Dp, val left: Dp = 0.dp, val right: Dp = 0.dp)

/**
 * The caption bar the app draws in; null when the window has none, or when the system draws the
 * bar itself (before Android 15, DeX's own caption), so that the app stays below it.
 */
@Composable
fun rememberCaptionBar(): CaptionBar? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
    val density = LocalDensity.current
    // read in composition: the bar coming and going recomposes
    val top = WindowInsets.captionBar.getTop(density)
    if (top <= 0) return null
    val view = LocalView.current
    val buttons = runCatching { view.rootWindowInsets?.getBoundingRects(android.view.WindowInsets.Type.captionBar()) }.getOrNull().orEmpty()
    // no buttons drawn over the app: the system draws the whole bar
    if (buttons.isEmpty()) return null
    val width = view.rootView.width
    var left = 0
    var right = 0
    for (button in buttons) {
        if (button.centerX() < width / 2) left = max(left, button.right) else right = max(right, width - button.left)
    }
    return with(density) { CaptionBar(top.toDp(), left.toDp(), right.toDp()) }
}

/** Keeps clear of the system's buttons in the caption bar. */
fun Modifier.captionPadding(caption: CaptionBar?): Modifier =
    if (caption == null) this else absolutePadding(left = caption.left, right = caption.right)

/** Something to click in the caption bar: it takes the clicks the window would take to move. */
fun Modifier.captionTarget(caption: CaptionBar?): Modifier = if (caption == null) this else systemGestureExclusion()
