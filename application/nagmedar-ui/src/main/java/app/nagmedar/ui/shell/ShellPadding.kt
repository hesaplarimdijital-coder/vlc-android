package app.nagmedar.ui.shell

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Sizes of the chrome lying over the content, measured by the shell (Desktop Kit 1.0). On a phone
 * the top bar, the player strip and the bottom bar cover the content's edges; on tablet and DeX
 * only a floating player does.
 */
@Stable
internal class ShellChromeMetrics {
    var topPx by mutableIntStateOf(0)
    var navPx by mutableIntStateOf(0)
    var playerPx by mutableIntStateOf(0)

    /** Content lies under the top bar (phone). */
    var underTop by mutableStateOf(false)

    /** Content lies under the bottom bar and the player strip (phone). */
    var underBottom by mutableStateOf(false)

    /** Content lies under a floating player (tablet, DeX); its margin is added. */
    var underFloatingPlayer by mutableStateOf(false)
    var floatingMarginPx by mutableIntStateOf(0)

    val topPadPx: Int get() = if (underTop) topPx else 0

    val bottomPadPx: Int
        get() = when {
            underBottom -> navPx + playerPx
            underFloatingPlayer && playerPx > 0 -> playerPx + 2 * floatingMarginPx
            else -> 0
        }
}

/** The covered edges as padding; read while measuring, so a change re-measures without recomposing. */
private class ShellPaddingValues(private val metrics: ShellChromeMetrics, private val density: Density) : PaddingValues {
    override fun calculateTopPadding(): Dp = with(density) { metrics.topPadPx.toDp() }
    override fun calculateBottomPadding(): Dp = with(density) { metrics.bottomPadPx.toDp() }
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun toString() = "ShellPaddingValues(top=${metrics.topPadPx}px, bottom=${metrics.bottomPadPx}px)"
}

internal fun shellPaddingValues(metrics: ShellChromeMetrics, density: Density): PaddingValues = ShellPaddingValues(metrics, density)

/**
 * How much of the screen the shell's chrome covers: on a phone the top bar (top) and the bottom
 * bar with the player strip (bottom); on tablet and DeX the floating player, if any. Zero outside
 * the shell.
 */
val LocalShellContentPadding = staticCompositionLocalOf<PaddingValues> { PaddingValues(0.dp) }

/** One screen of the shell; counts the calls that place the content under the bars themselves. */
@Stable
internal class ShellScreenState {
    var handlers by mutableIntStateOf(0)
}

internal val LocalShellScreen = staticCompositionLocalOf<ShellScreenState?> { null }

/**
 * Desktop Kit 1.0: the padding a screen needs so nothing stays hidden under the phone's bars, for
 * a screen that lays its content out under them: pass it to a list
 * (`LazyColumn(contentPadding = padding + PaddingValues(…))`) so the first and last items can rest
 * clear of the bars while the rest scrolls beneath the glass; a header above the list takes its
 * top ([topOnly]).
 *
 * Calling this tells the shell that the screen handles the padding itself. Screens that never
 * call it are padded by the shell as before and look the same; only those screens re-measure
 * while the bars slide.
 */
@Composable
fun rememberShellContentPadding(): PaddingValues {
    val screen = LocalShellScreen.current
    DisposableEffect(screen) {
        screen?.let { it.handlers++ }
        onDispose { screen?.let { it.handlers-- } }
    }
    return LocalShellContentPadding.current
}

/**
 * The bottom of the shell's padding, for the lists (and the states in the lists' place) of a
 * screen in [UnderShellBars]: `contentPadding = PaddingValues(…) + LocalShellBottomPadding.current`.
 * Zero elsewhere, so a list outside such a screen is never padded twice.
 */
val LocalShellBottomPadding = staticCompositionLocalOf<PaddingValues> { PaddingValues(0.dp) }

/**
 * The top of the shell's padding, for the list of a screen in [UnderShellBars] with `listOnTop`:
 * the list that is the screen's first thing scrolls beneath the top bar as well. Zero elsewhere.
 */
val LocalShellTopPadding = staticCompositionLocalOf<PaddingValues> { PaddingValues(0.dp) }

/**
 * A whole screen laid out under the phone's bars (Desktop Kit 1.0): it asks for the shell's
 * padding ([rememberShellContentPadding]), keeps its top (the header, the toolbar) below the top
 * bar and hands the bottom to its lists through [LocalShellBottomPadding], so they scroll
 * beneath the bottom bar and the player strip and their last rows rest clear of them. A screen
 * that is one list from the top ([listOnTop]: Settings, Search, More) gets the top through
 * [LocalShellTopPadding] too and scrolls beneath the top bar's glass. On tablet and DeX the
 * padding is zero and nothing changes.
 */
@Composable
fun UnderShellBars(listOnTop: Boolean = false, content: @Composable () -> Unit) {
    val padding = rememberShellContentPadding()
    val top = remember(padding) { padding.topOnly() }
    val bottom = remember(padding) { padding.bottomOnly() }
    val none = remember { PaddingValues(0.dp) }
    Box(Modifier.fillMaxSize().then(if (listOnTop) Modifier else Modifier.padding(top)), propagateMinConstraints = true) {
        CompositionLocalProvider(
            LocalShellBottomPadding provides bottom,
            LocalShellTopPadding provides if (listOnTop) top else none,
            content = content,
        )
    }
}

/** [rememberShellContentPadding] as padding: for a screen that does not scroll. */
@Composable
fun Modifier.shellContentPadding(): Modifier = padding(rememberShellContentPadding())

/** [this] padding plus [other], side by side; for `contentPadding = shellPadding + PaddingValues(8.dp)`. */
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding() = this@plus.calculateTopPadding() + other.calculateTopPadding()
    override fun calculateBottomPadding() = this@plus.calculateBottomPadding() + other.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        this@plus.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        this@plus.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)
}

/** Only the top of [this] (still read while measuring): for the header above a list that takes the bottom. */
fun PaddingValues.topOnly(): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding() = this@topOnly.calculateTopPadding()
    override fun calculateBottomPadding() = 0.dp
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

/** Only the bottom of [this] (still read while measuring): for a list under a header that took the top. */
fun PaddingValues.bottomOnly(): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding() = 0.dp
    override fun calculateBottomPadding() = this@bottomOnly.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

/**
 * Wraps one screen of the shell. A screen that never asks for [rememberShellContentPadding] is
 * kept clear of the bars, as in 0.2.0: padded by the covered edges, and the bottom padding shrinks
 * while the bars slide away (the only case where scrolling re-measures).
 */
@Composable
internal fun ShellScreenFrame(metrics: ShellChromeMetrics, scroll: ShellScrollState, content: @Composable () -> Unit) {
    val screen = remember { ShellScreenState() }
    CompositionLocalProvider(LocalShellScreen provides screen) {
        Box(
            Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    if (screen.handlers > 0) {
                        val placeable = measurable.measure(constraints)
                        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    val top = metrics.topPadPx
                    val hidden = if (metrics.underBottom) scroll.offsetPx.roundToInt().coerceIn(0, metrics.navPx) else 0
                    val bottom = (metrics.bottomPadPx - hidden).coerceAtLeast(0)
                    val maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - top - bottom).coerceAtLeast(0) else constraints.maxHeight
                    val inner = constraints.copy(minHeight = constraints.minHeight.coerceAtMost(maxHeight), maxHeight = maxHeight)
                    val placeable = measurable.measure(inner)
                    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height + top + bottom
                    layout(placeable.width, height) { placeable.place(0, top) }
                },
        ) { content() }
    }
}

/**
 * A layer over the whole shell, for what must cover the bars as well: the expanded player and
 * the glass sheets ([app.nagmedar.ui.components.GlassBottomSheet]) register layers here.
 */
@Stable
internal class ShellOverlayHost {
    /** One thing drawn in the overlay; the later ones over the earlier ones. */
    class Layer(val content: @Composable () -> Unit)

    val layers = mutableStateListOf<Layer>()

    /** Where the overlay lies in the window; set by the shell. */
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)

    /** The root view of the shell's window: a sheet opened in a dialog or a pop-up of it is not drawn here. */
    var rootView: View? = null

    /** 0 collapsed … 1 expanded; the shell slides the bottom bar away with it. Read while drawing. */
    var expandProgress: () -> Float by mutableStateOf({ 0f })

    /** Called when the player starts to expand: the shell brings the bars back first. */
    var onExpandStart: () -> Unit = {}
}

internal val LocalShellOverlayHost = staticCompositionLocalOf<ShellOverlayHost?> { null }
