package app.nagmedar.ui.components

import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection

/**
 * 1.0.4: whose Back a predictive Back is. Since Android 16 the Back button of the three-button navigation
 * starts one as a swipe does, but with no edge ([BackEventCompat.EDGE_NONE]) and with a progress the system
 * runs by itself, on a spring from 0 to 1, for as long as the button is down (a tap's tenth of a second takes
 * it past the half); a keyboard's Back key starts one with no progress at all. Neither is a finger: what a
 * swipe shrinks, rounds and leans (a detail, the phone's player, a tab) stays whole under a button, and the
 * release plays what a tap on the page's own arrow plays.
 *
 * 1.0.3 knew this for the tabs alone (Desktop Kit 2.4.0). A detail and the player followed the button's
 * progress as a finger's: with three buttons, Back from a podcast's episode, a show, an album, a playlist or
 * the player shrank the page into a rounded card before it left, as 1.0.2 did.
 *
 * And a finger's Back needs an edge to come from: only gesture navigation keeps the window's side edges for
 * it ([rememberBackSwipes]). With three buttons there is no such swipe, so no predictive Back is a finger's
 * there, whatever edge a system's button names for its own.
 */
internal object BackSource {
    /**
     * A swipe from an edge of the screen: [swipeEdge] is [BackEventCompat.EDGE_LEFT] or [BackEventCompat.EDGE_RIGHT],
     * on a system whose Back is such a swipe ([edgeSwipes], [rememberBackSwipes]).
     */
    fun finger(swipeEdge: Int, edgeSwipes: Boolean): Boolean = edgeSwipes && swipeEdge != BackEventCompat.EDGE_NONE
}

/**
 * Whether the system's Back is a swipe from the window's side edges (gesture navigation): it keeps them for the
 * gesture ([WindowInsets.systemGestures]). Three-button navigation has no such edges.
 */
@Composable
internal fun rememberBackSwipes(): State<Boolean> {
    // EXPERIMENT swipes=const: no window insets read here
    if (app.nagmedar.ui.shell.DepthExperiment.swipesConst) return remember { androidx.compose.runtime.mutableStateOf(false) }
    val gestures = WindowInsets.systemGestures
    val density = LocalDensity.current
    return remember(gestures, density) {
        derivedStateOf { gestures.getLeft(density, LayoutDirection.Ltr) > 0 || gestures.getRight(density, LayoutDirection.Ltr) > 0 }
    }
}
