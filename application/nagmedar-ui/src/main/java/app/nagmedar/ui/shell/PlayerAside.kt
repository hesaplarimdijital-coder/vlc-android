package app.nagmedar.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 1.1.2 (Nağmedar's design, its tablet page): the pages that show the transport themselves (Müzik's panel of what
 * plays): while one does, the floating player of a tablet's or a DeX window stands aside and the pages keep no room for
 * it. Counted, so a page leaving while another comes does not bring it back too soon.
 */
@Stable
class ShellPlayerAside {
    private var pages by mutableIntStateOf(0)

    /** Whether a page on screen shows the transport itself. */
    val aside: Boolean get() = pages > 0

    internal fun take() {
        pages++
    }

    internal fun give() {
        pages = (pages - 1).coerceAtLeast(0)
    }
}

/** The shell's [ShellPlayerAside]; none outside the shell. */
val LocalShellPlayerAside = staticCompositionLocalOf<ShellPlayerAside?> { null }

/** While [active], this page shows the transport itself: the shell's floating player stands aside. */
@Composable
fun PlayerAside(active: Boolean) {
    val holder = LocalShellPlayerAside.current ?: return
    DisposableEffect(holder, active) {
        if (active) holder.take()
        onDispose { if (active) holder.give() }
    }
}
