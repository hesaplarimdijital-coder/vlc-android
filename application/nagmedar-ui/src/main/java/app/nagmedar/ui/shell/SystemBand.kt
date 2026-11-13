package app.nagmedar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/**
 * 1.0.3 (Desktop Kit 2.0): the insets of the app's window root. The root pads the system's bars, the
 * display cutout and the on-screen keyboard ([padding]); while the shell is shown it leaves the
 * navigation bar at the window's bottom (three buttons or the gesture handle; 1.0.9: or whatever the
 * system lays over that edge taller than it, [navigationBottom]) to the shell, which draws its chrome
 * behind it ([SystemBand]) and keeps its own controls above it.
 * Outside the shell (the first run) the root pads that bar too.
 */
@Immutable
class WindowRootInsets internal constructor(
    /** What the root pads: the window's safe drawing area, less what is left to the shell. */
    val padding: WindowInsets,
    /**
     * What the system lays over the window's bottom edge (the navigation bar, or a taller cutout or system bar there),
     * less the keyboard over it, left to the shell; zero when the root pads it.
     */
    val navigationBottom: WindowInsets,
)

/**
 * The root's insets: with [belowCaption] the content starts below a desktop window's caption bar
 * that the app does not draw in; with [shellDrawsNavigation] the bottom navigation bar is the shell's.
 * Made in composition, not while measuring, so that the shell's first frame already has the bar
 * (Desktop Kit 2.1's first launch: a bar-high gap under the shell until the window was laid out again).
 */
@Composable
fun rememberWindowRootInsets(belowCaption: Boolean, shellDrawsNavigation: Boolean): WindowRootInsets {
    val safe = WindowInsets.safeDrawing
    val captionBar = WindowInsets.captionBar
    val base = if (belowCaption) safe.exclude(captionBar) else safe
    // 1.0.9 (Desktop Kit 2.9.0): everything the system lays over the window's bottom edge, less the keyboard over it.
    // The navigation bar alone left the root a strip of its own background under the shell's chrome wherever a device
    // reports a cutout or another system bar there taller than the navigation bar; the band now covers the tallest
    val navigationBottom = safe.only(WindowInsetsSides.Bottom).exclude(WindowInsets.ime)
    return remember(base, navigationBottom, shellDrawsNavigation) {
        WindowRootInsets(
            padding = if (shellDrawsNavigation) RootPadding(base, navigationBottom) else base,
            navigationBottom = if (shellDrawsNavigation) navigationBottom else NoInsets,
        )
    }
}

/** The root's insets, for the shell (the band, the bottom padding) and the full-window players' backdrops; null outside the app's root. */
val LocalWindowRootInsets = staticCompositionLocalOf<WindowRootInsets?> { null }

/**
 * The app's window root (1.0.3): pads the window's insets ([rememberWindowRootInsets]) around
 * [content]; with [shell] the navigation bar at the window's bottom is left to the shell inside
 * ([LocalWindowRootInsets]), without it (the first run) it is padded as well.
 */
@Composable
fun WindowRoot(shell: Boolean, belowCaption: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val insets = rememberWindowRootInsets(belowCaption = belowCaption, shellDrawsNavigation = shell)
    Box(modifier.windowInsetsPadding(insets.padding)) {
        if (shell) CompositionLocalProvider(LocalWindowRootInsets provides insets, content = content) else content()
    }
}

/** [safe] without the [navigationBottom] the shell draws behind. */
private class RootPadding(private val safe: WindowInsets, private val navigationBottom: WindowInsets) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = safe.getLeft(density, layoutDirection)
    override fun getTop(density: Density) = safe.getTop(density)
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = safe.getRight(density, layoutDirection)
    override fun getBottom(density: Density) = (safe.getBottom(density) - navigationBottom.getBottom(density)).coerceAtLeast(0)

    override fun equals(other: Any?) = other is RootPadding && other.safe == safe && other.navigationBottom == navigationBottom

    override fun hashCode() = 31 * safe.hashCode() + navigationBottom.hashCode()

    override fun toString() = "RootPadding($safe - $navigationBottom)"
}

private val NoInsets = WindowInsets(0, 0, 0, 0)

/**
 * Only a bottom of [px] pixels, read while measuring: what the shell's bars and band keep from the
 * screen's overlay at the window's bottom, consumed there so that a page in it which pads the
 * navigation bar (`navigationBarsPadding()`) does so only when it reaches the window's bottom.
 */
internal class BottomInsets(private val px: () -> Int) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getTop(density: Density) = 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getBottom(density: Density) = px().coerceAtLeast(0)
}

/**
 * What lies behind the system's navigation bar at the window's bottom (Desktop Kit 2.0): on a
 * phone the bottom bar's surface, continued behind the buttons and staying there when the bars
 * slide away beneath it, a rule along its top as far as they are away ([hidden], 0 … 1, read while
 * drawing); with the shell's bottom glass ([onGlass]) the glass lies behind it and it draws the rule
 * alone. On tablets and DeX the chrome colour, continuing the rail or the sidebar.
 */
@Composable
internal fun SystemBand(navKind: NavKind, hidden: () -> Float, onGlass: Boolean) {
    val colors = NagTheme.colors
    if (navKind == NavKind.BottomBar) {
        val rule = colors.border
        Box(
            Modifier
                .fillMaxSize()
                .then(if (onGlass) Modifier else Modifier.background(colors.chrome))
                .drawBehind {
                    val away = hidden().coerceIn(0f, 1f)
                    if (away > 0f) drawRect(rule.copy(alpha = rule.alpha * away), size = Size(size.width, 1.dp.toPx()))
                },
        )
    } else {
        Box(Modifier.fillMaxSize().background(colors.chrome))
    }
}
