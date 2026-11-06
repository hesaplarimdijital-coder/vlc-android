package app.nagmedar.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import app.nagmedar.ui.R
import app.nagmedar.ui.components.blockInputBelow
import app.nagmedar.ui.components.rememberSheetDrag
import app.nagmedar.ui.components.sheetDrag
import app.nagmedar.ui.shell.DetailBackHandler
import app.nagmedar.ui.shell.WindowPage
import app.nagmedar.ui.shell.rememberDetailMotion
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/**
 * 1.0.2: the big player over a tablet's and a desktop's window (S-1002's player over a rail's window,
 * now the sidebar's too), opened by the bar's button, its artwork and words. It comes up from the
 * bar and goes down again; it closes by Back, the predictive Back gesture (it shrinks with the finger,
 * as a detail does), a pull down and the player's own ⌄; Esc is the window's
 * ([app.nagmedar.ui.AppActions.escape]). While it is open nothing under it takes a touch, a scroll or
 * a key, [onKey] has the keys nothing in it used, and once it is up and still the shell stops drawing
 * what is under it and its bars stand aside ([WindowPage]).
 *
 * 1.0.3: pulled down as the sheets are ([rememberSheetDrag]): from anywhere on it that does not move
 * by a drag of its own (the seek bar, the artwork's sideways swipe keep theirs), and from the lyrics
 * at their top, which scroll back first; it follows the finger and closes past a third of its height
 * or flung down, else it springs back.
 */
@Composable
fun WindowPlayer(
    open: Boolean,
    onClose: () -> Unit,
    onKey: (KeyEvent) -> Boolean = { false },
    player: @Composable (modifier: Modifier) -> Unit,
) {
    val motion = NagTheme.motion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestClose by rememberUpdatedState(onClose)
    val latestKey by rememberUpdatedState(onKey)
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = open
    // kept composed while it goes down
    AnimatedVisibility(shown, enter = motion.sheetEnter(), exit = motion.sheetExit()) {
        // the predictive Back gesture shrinks it as it does a detail; let go, it goes down from there
        val back = rememberDetailMotion(open = true)
        val drag = rememberSheetDrag { latestClose() }
        // opened again while still going down
        LaunchedEffect(open) { if (open) drag.reset() }
        // up and at rest, nothing moving it: what is under it may go
        val covers by remember(back, drag) { derivedStateOf { shown.isIdle && shown.currentState && back.covered && !drag.pulled } }
        WindowPage(covers = covers)
        player(
            Modifier
                .graphicsLayer { translationY = drag.offset }
                .graphicsLayer { with(back) { detailLayer(size.width.roundToInt(), if (rtl) -1f else 1f) } }
                .onKeyEvent { latestKey(it) }
                .blockInputBelow(stringResource(R.string.nag_now_playing))
                // what in it does not take a drag of its own, and its lyrics at their top, pull it down
                .sheetDrag(drag),
        )
        // after the player's own: this one goes first; the player's windows opened later go before it
        DetailBackHandler(enabled = open, state = back, onBack = { latestClose() })
    }
}
