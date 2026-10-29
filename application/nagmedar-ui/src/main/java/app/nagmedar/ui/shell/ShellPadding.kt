package app.nagmedar.ui.shell

import android.view.View
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/**
 * Sizes of the chrome lying over the content, measured by the shell (Desktop Kit 1.0). On a phone
 * the top bar, the player strip and the bottom bar cover the content's edges; on tablet and DeX
 * only a floating player does. 1.0.1: a phone's full page (Settings and its pages) has no bottom
 * bar and no player strip ([barsHidden] while one is on screen).
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

    /** The page on screen is a full page: the phone's bottom bar and player strip are away. */
    var barsHidden by mutableStateOf(false)

    /** Content lies under a floating player (tablet, DeX); its margin is added. */
    var underFloatingPlayer by mutableStateOf(false)
    var floatingMarginPx by mutableIntStateOf(0)

    val topPadPx: Int get() = if (underTop) topPx else 0

    /** What covers the bottom of the page on screen. */
    val bottomPadPx: Int get() = bottomPadPx(bars = !barsHidden)

    /** What covers the bottom of a page that has the phone's bottom bar and player strip ([bars]) or not. */
    fun bottomPadPx(bars: Boolean): Int = when {
        underBottom -> if (bars) navPx + playerPx else 0
        underFloatingPlayer && playerPx > 0 -> playerPx + 2 * floatingMarginPx
        else -> 0
    }
}

/**
 * The covered edges as padding; read while measuring, so a change re-measures without recomposing.
 * [bars]: those of a page with or without the phone's bottom bar; null, those of the page on screen.
 */
private class ShellPaddingValues(private val metrics: ShellChromeMetrics, private val density: Density, private val bars: Boolean?) : PaddingValues {
    private val bottomPx: Int get() = if (bars == null) metrics.bottomPadPx else metrics.bottomPadPx(bars)
    override fun calculateTopPadding(): Dp = with(density) { metrics.topPadPx.toDp() }
    override fun calculateBottomPadding(): Dp = with(density) { bottomPx.toDp() }
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun toString() = "ShellPaddingValues(top=${metrics.topPadPx}px, bottom=${bottomPx}px)"
}

internal fun shellPaddingValues(metrics: ShellChromeMetrics, density: Density, bars: Boolean? = null): PaddingValues =
    ShellPaddingValues(metrics, density, bars)

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
 * clear of the bars while the rest scrolls beneath the bars; a header above the list takes its
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
 * [LocalShellTopPadding] too and scrolls beneath the top bar. On tablet and DeX the
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
 * Wraps one screen of the shell, on an opaque background (a page opened slides over the one under
 * it). A screen that never asks for [rememberShellContentPadding] is kept clear of the bars, as in
 * 0.2.0: padded by the covered edges, and the bottom padding shrinks while the bars slide away (the
 * only case where scrolling re-measures). [bars]: the screen has the phone's bottom bar and player
 * strip; a full page (1.0.1) has none and reaches down to the window's edge. The screen's
 * [LocalShellContentPadding] says the same.
 */
@Composable
internal fun ShellScreenFrame(metrics: ShellChromeMetrics, scroll: ShellScrollState, bars: Boolean, content: @Composable () -> Unit) {
    val screen = remember { ShellScreenState() }
    val density = LocalDensity.current
    val padding = remember(metrics, density, bars) { shellPaddingValues(metrics, density, bars) }
    CompositionLocalProvider(LocalShellScreen provides screen, LocalShellContentPadding provides padding) {
        Box(
            Modifier
                .fillMaxSize()
                .background(NagTheme.colors.background)
                .layout { measurable, constraints ->
                    if (screen.handlers > 0) {
                        val placeable = measurable.measure(constraints)
                        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    val top = metrics.topPadPx
                    val hidden = if (metrics.underBottom && bars) scroll.offsetPx.roundToInt().coerceIn(0, metrics.navPx) else 0
                    val bottom = (metrics.bottomPadPx(bars) - hidden).coerceAtLeast(0)
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
 * the sheets ([app.nagmedar.ui.components.GlassBottomSheet]) register layers here.
 */
@Stable
internal class ShellOverlayHost {
    /**
     * One thing drawn over the shell; the later ones over the earlier ones. [PLAYER] layers lie
     * under the screen's own overlays (the lyrics, "Sıradaki" and the notices opened from the
     * player come over it), [SHEET] layers over everything.
     */
    class Layer(val z: Int, val content: @Composable () -> Unit)

    val layers = mutableStateListOf<Layer>()

    /** Where the overlay lies in the window; set by the shell. */
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)

    /** The root view of the shell's window: a sheet opened in a dialog or a pop-up of it is not drawn here. */
    var rootView: View? = null

    /** 0 collapsed … 1 expanded; the shell slides the bottom bar away with it. Read while drawing. */
    var expandProgress: () -> Float by mutableStateOf({ 0f })

    /** Called when the player starts to expand: the shell brings the bars back first. */
    var onExpandStart: () -> Unit = {}

    /** How far the bars have slid down now (px); the player measures where its strip rests without it. */
    var navigationOffset: () -> Float = { 0f }

    /**
     * 1.0.1: the pages of the screen's overlay that take the whole window ([WindowPage]), each with
     * whether it covers it now (open and at rest).
     */
    val windowPages = mutableStateMapOf<Any, Boolean>()

    /** A [WindowPage] is shown: the screen's overlays take the whole window, bars included. */
    val windowPageShown: Boolean get() = windowPages.isNotEmpty()

    /** A [WindowPage] covers the window: the bars and the screen under it are neither drawn nor read out. */
    val windowCovered: Boolean get() = windowPages.containsValue(true)

    companion object {
        /** The expanded player: over the bars, under the screen's overlays. */
        const val PLAYER = 0

        /** The glass sheets: over everything. */
        const val SHEET = 1
    }
}

internal val LocalShellOverlayHost = staticCompositionLocalOf<ShellOverlayHost?> { null }
