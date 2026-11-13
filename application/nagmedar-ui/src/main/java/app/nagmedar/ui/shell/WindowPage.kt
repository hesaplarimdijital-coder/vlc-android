package app.nagmedar.ui.shell

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * 1.0.1: while this is composed, a page of the screen's overlay takes the whole window (on a
 * phone the overlay otherwise lies between the top bar and the bottom bar): the shell lays the
 * overlay over the bars and the mini player from the page's first frame, so the page comes up
 * whole and the undo bars and notices over it sit at the window's bottom. While [covers] (the
 * page is open and at rest, no Back gesture moving it) the bars and the screen under it are
 * neither drawn nor read out, as under the expanded player. Outside the shell it does nothing.
 */
@Composable
fun WindowPage(covers: Boolean) {
    val host = LocalShellOverlayHost.current ?: return
    val key = remember { Any() }
    // written after the composition and before the layout of the same frame: the overlay is measured whole at once
    SideEffect { if (host.windowPages[key] != covers) host.windowPages[key] = covers }
    DisposableEffect(host, key) { onDispose { host.windowPages.remove(key) } }
}

/**
 * 1.0.9 (Desktop Kit 2.9.0's full-screen tasks, its `HideBottomNavigation`): a page of the screen's overlay that is a
 * task over the whole window (full-screen lyrics, the visualiser, the lyrics editor with its own buttons at the bottom)
 * takes the whole window on a phone also when no expanded player lies under it, as when a shortcut opened it over the
 * mini player: the bottom bar and the mini player stand aside under it, as under Sıradaki, and no tab is tapped
 * half-way through the task. The page covers the window once [shown] has come in, until it starts to go; without an
 * animation ([shown] null) at once. On tablets and DeX nothing changes.
 */
@Composable
fun PhoneWindowPage(shown: AnimatedVisibilityScope?) {
    if (LocalNavKind.current != NavKind.BottomBar) return
    val covers = shown == null || shown.transition.let { it.currentState == EnterExitState.Visible && it.targetState == EnterExitState.Visible }
    WindowPage(covers = covers)
}
