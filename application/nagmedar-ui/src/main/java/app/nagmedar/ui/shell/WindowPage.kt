package app.nagmedar.ui.shell

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
