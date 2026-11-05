package app.nagmedar.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private const val SLOT_TOP = "top"
private const val SLOT_NAV = "nav"
private const val SLOT_CONTENT = "content"
private const val SLOT_PLAYER = "player"
private const val SLOT_PLAYER_LAYER = "playerLayer"
private const val SLOT_SCREEN_OVERLAY = "screenOverlay"
private const val SLOT_OVERLAY = "overlay"
private const val SLOT_BAND = "band"
private const val SLOT_GLASS = "glass"

/** The test tag of the phone's one bottom glass (1.0.3, off by default: [ChromeGlass]). */
const val BOTTOM_GLASS_TAG = "bottom-glass"

// 1.0.3 (Desktop Kit 2.0): drawn in this order whatever the order they are placed in (which is the Tab key's): the
// content, the phone's bottom glass, the chrome, the system band over the sliding bars, the phone's expanded player,
// the screen's overlays, the sheets
private const val Z_GLASS = 0.5f
private const val Z_CHROME = 1f
private const val Z_BAND = 2f
private const val Z_PLAYER_LAYER = 3f
private const val Z_SCREEN_OVERLAY = 4f
private const val Z_OVERLAY = 5f

/** A slot TalkBack reads as a whole, at [index] among the shell's slots (Desktop Kit 2.0). */
private fun Modifier.readingOrder(index: Float): Modifier = semantics {
    isTraversalGroup = true
    traversalIndex = index
}

/**
 * Places the shell's slots for a [NavKind]. The content slot is always the same child of the
 * same layout, so when the window is resized across width classes only the chrome around it
 * changes: the navigation back stack, scroll positions and text fields keep their state.
 *
 * - BottomBar (Desktop Kit 1.0): the content fills the whole window and the bars lie over it: the
 *   top bar along the top, the bottom navigation along the bottom and the player strip just above
 *   it. The shell tells the screens how much of them is covered ([rememberShellContentPadding]), so
 *   lists pad their ends and scroll beneath the bars. [navigationOffset] slides the navigation and
 *   the player that many pixels down, cut where the system's band begins; it is read while drawing
 *   (a layer translation), so hiding the bars while the content scrolls neither measures nor
 *   recomposes anything.
 * - Rail: rail on the start edge; top bar, content and player stacked beside it.
 * - Sidebar: menu bar across the top, player across the bottom, sidebar and content between.
 *
 * With [floatingPlayer] (Rail and Sidebar) the player is not stacked under the content but floats
 * over its bottom edge, [floatingMargin] from the edges and at most [floatingMaxWidth] wide.
 * [playerLayer] covers the whole window over the chrome (the phone's expanded player); [screenOverlay]
 * lies over that where the bars leave the content ([screenOverlayTop] and [screenOverlayBottom]
 * pixels in from the edges, read while measuring): the lyrics, "Sıradaki", undo bars and notices.
 * [overlay] covers the whole window above everything else (the sheets).
 *
 * [contentOnly]: the content and the overlays take the whole window and the chrome is not placed
 * (S-1002: the player over a tablet's window); the chrome keeps its state for when it comes back.
 * 1.0.2: with Rail and Sidebar, while [screenOverlayWindow] the screen's overlay takes the whole
 * window with the chrome still placed under it (a page over the window coming up); in both cases
 * it starts [windowTop] down, below a desktop window's caption bar, which stays the window's.
 *
 * 1.0.3 (Desktop Kit 2.0): [systemBottom] is the height (px, read while measuring) of the system's
 * navigation bar at the window's bottom, the three buttons or the gesture handle, which the shell
 * draws behind: the chrome stays above it and [systemBand] fills it across the whole width (on a
 * phone the bottom bar's surface continues behind the buttons, on tablets the chrome colour). The
 * content of a phone still fills the window; what lies over the whole window (the expanded player,
 * a window page, the sheets) reaches behind the buttons and keeps its controls above them, the
 * screen's overlay between the bars has the system's bottom taken off ([BottomInsets]). The Tab key
 * and TalkBack go through the slots in reading order: the top bar (menu bar), the navigation, the
 * content, the player; on a phone the bottom navigation comes last. Drawing stays as it was: the
 * content, the chrome over it, the band over the sliding bars, the overlays over everything.
 *
 * [bottomGlass] (1.0.3, the phone's glass for the user's decision, off by default: [ChromeGlass]):
 * one surface behind the player strip, the bottom navigation and the band, drawn by the layout from
 * the strip's top to the window's bottom and moved with the bars by placement ([navigationOffset]),
 * so that a blur blurs what is behind it where it is now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShellLayout(
    navKind: NavKind,
    topBar: @Composable () -> Unit,
    navigation: @Composable () -> Unit,
    player: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentOnly: Boolean = false,
    navigationOffset: () -> Int = { 0 },
    playerLayer: @Composable () -> Unit = {},
    screenOverlay: @Composable () -> Unit = {},
    screenOverlayTop: () -> Int = { 0 },
    screenOverlayBottom: () -> Int = { 0 },
    overlay: @Composable () -> Unit = {},
    floatingPlayer: Boolean = false,
    floatingMargin: Dp = 12.dp,
    floatingMaxWidth: Dp = 760.dp,
    screenOverlayWindow: () -> Boolean = { false },
    windowTop: Dp = 0.dp,
    systemBottom: () -> Int = { 0 },
    systemBand: @Composable () -> Unit = {},
    bottomGlass: (@Composable () -> Modifier)? = null,
) {
    val slides = navKind == NavKind.BottomBar
    val glass = if (slides && !contentOnly) bottomGlass else null
    // the navigation's height, from the last measure: the player strip above it is cut where the band begins
    val navHeight = remember { IntArray(1) }
    // where the screen's overlay ends above the window's bottom (the bars, the band), from the last measure: a page in it
    // that pads the navigation bar does so only when it reaches the window's bottom
    val overlayAbove = remember { IntArray(1) }
    val overlayInsets = remember { BottomInsets { overlayAbove[0] } }
    val latestSystem by rememberUpdatedState(systemBottom)
    // the navigation sits last in a phone's reading order, first beside the content elsewhere
    val navOrder = if (slides) 3f else 0.5f
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId(SLOT_CONTENT).readingOrder(1f)) { content() }
            Box(Modifier.layoutId(SLOT_TOP).readingOrder(0f)) { topBar() }
            if (glass != null) {
                Box(Modifier.layoutId(SLOT_GLASS)) {
                    // placed, not translated in a layer: the blur reads where it is placed
                    Box(Modifier.fillMaxSize().offset { IntOffset(0, navigationOffset()) }.testTag(BOTTOM_GLASS_TAG).then(glass()))
                }
            }
            Box(
                Modifier
                    .layoutId(SLOT_NAV)
                    .readingOrder(navOrder)
                    .graphicsLayer { slideDown(if (slides) navigationOffset().toFloat() else 0f, below = 0) },
            ) { navigation() }
            Box(
                Modifier
                    .layoutId(SLOT_PLAYER)
                    .readingOrder(2f)
                    .graphicsLayer { slideDown(if (slides) navigationOffset().toFloat() else 0f, below = navHeight[0]) },
            ) { player() }
            Box(Modifier.layoutId(SLOT_BAND)) { systemBand() }
            Box(Modifier.layoutId(SLOT_PLAYER_LAYER).readingOrder(4f)) { playerLayer() }
            Box(Modifier.layoutId(SLOT_SCREEN_OVERLAY).readingOrder(5f).consumeWindowInsets(overlayInsets)) { screenOverlay() }
            Box(Modifier.layoutId(SLOT_OVERLAY).readingOrder(6f)) { overlay() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val fullHeight = constraints.maxHeight
        val topM = measurables.first { it.layoutId == SLOT_TOP }
        val navM = measurables.first { it.layoutId == SLOT_NAV }
        val playerM = measurables.first { it.layoutId == SLOT_PLAYER }
        val contentM = measurables.first { it.layoutId == SLOT_CONTENT }
        val screenOverlayM = measurables.first { it.layoutId == SLOT_SCREEN_OVERLAY }
        val overlay = measurables.first { it.layoutId == SLOT_OVERLAY }.measure(Constraints.fixed(width, fullHeight))
        val playerLayer = measurables.first { it.layoutId == SLOT_PLAYER_LAYER }.measure(Constraints.fixed(width, fullHeight))
        // the system's navigation bar at the window's bottom: the band fills it, everything else of the chrome stays above it
        val system = if (contentOnly) 0 else latestSystem().coerceIn(0, fullHeight)
        val band = measurables.first { it.layoutId == SLOT_BAND }.measure(Constraints.fixed(width, system))
        val height = fullHeight - system
        val margin = floatingMargin.roundToPx()
        // the screen's overlay over the whole window, below the caption bar, down to the window's bottom (behind the band)
        val wholeTop = windowTop.roundToPx().coerceIn(0, fullHeight)
        val whole = {
            overlayAbove[0] = 0
            screenOverlayM.measure(Constraints.fixed(width, fullHeight - wholeTop))
        }
        // the screen's overlay [above] px over the window's bottom: the system's bottom is not its own then
        val overlayOf = { w: Int, h: Int, above: Int ->
            overlayAbove[0] = above
            screenOverlayM.measure(Constraints.fixed(w, h.coerceAtLeast(0)))
        }

        if (contentOnly) {
            val content = contentM.measure(Constraints.fixed(width, fullHeight))
            val over = whole()
            return@Layout layout(width, fullHeight) {
                content.placeRelative(0, 0)
                playerLayer.placeRelative(0, 0, Z_PLAYER_LAYER)
                over.placeRelative(0, wholeTop, Z_SCREEN_OVERLAY)
                overlay.placeRelative(0, 0, Z_OVERLAY)
            }
        }
        val window = navKind != NavKind.BottomBar && screenOverlayWindow()
        when (navKind) {
            NavKind.BottomBar -> {
                val top = topM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val nav = navM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                navHeight[0] = nav.height
                val player = playerM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                // the content never changes size while the bars move: they are drawn over it
                val content = contentM.measure(Constraints.fixed(width, fullHeight))
                val overTop = screenOverlayTop().coerceIn(0, fullHeight)
                val overBottom = screenOverlayBottom().coerceIn(0, fullHeight - overTop)
                val over = overlayOf(width, fullHeight - overTop - overBottom, overBottom)
                // the one glass: from the strip's top to the window's bottom
                val glassTop = (height - nav.height - player.height).coerceAtLeast(0)
                val glassPlaceable = measurables.firstOrNull { it.layoutId == SLOT_GLASS }?.measure(Constraints.fixed(width, fullHeight - glassTop))
                layout(width, fullHeight) {
                    // placed in reading order (the Tab key's), drawn by depth
                    top.placeRelative(0, 0, Z_CHROME)
                    content.placeRelative(0, 0)
                    glassPlaceable?.placeRelative(0, glassTop, Z_GLASS)
                    player.placeRelative(0, height - nav.height - player.height, Z_CHROME)
                    nav.placeRelative(0, height - nav.height, Z_CHROME)
                    band.placeRelative(0, height, Z_BAND)
                    playerLayer.placeRelative(0, 0, Z_PLAYER_LAYER)
                    over.placeRelative(0, overTop, Z_SCREEN_OVERLAY)
                    overlay.placeRelative(0, 0, Z_OVERLAY)
                }
            }

            NavKind.Rail -> {
                val nav = navM.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height))
                val rest = (width - nav.width).coerceAtLeast(0)
                val top = topM.measure(Constraints(minWidth = rest, maxWidth = rest, maxHeight = height))
                if (floatingPlayer) {
                    val playerWidth = (rest - 2 * margin).coerceAtMost(floatingMaxWidth.roundToPx()).coerceAtLeast(0)
                    val player = playerM.measure(Constraints(minWidth = playerWidth, maxWidth = playerWidth, maxHeight = height))
                    val contentHeight = (height - top.height).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(rest, contentHeight))
                    val overBottom = screenOverlayBottom()
                    val over = if (window) whole() else overlayOf(rest, contentHeight - overBottom, overBottom + system)
                    layout(width, fullHeight) {
                        top.placeRelative(nav.width, 0, Z_CHROME)
                        nav.placeRelative(0, 0, Z_CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(nav.width + (rest - playerWidth) / 2, height - margin - player.height, Z_CHROME)
                        band.placeRelative(0, height, Z_BAND)
                        playerLayer.placeRelative(0, 0, Z_PLAYER_LAYER)
                        if (window) over.placeRelative(0, wholeTop, Z_SCREEN_OVERLAY) else over.placeRelative(nav.width, top.height, Z_SCREEN_OVERLAY)
                        overlay.placeRelative(0, 0, Z_OVERLAY)
                    }
                } else {
                    val player = playerM.measure(Constraints(minWidth = rest, maxWidth = rest, maxHeight = height))
                    val contentHeight = (height - top.height - player.height).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(rest, contentHeight))
                    val over = if (window) whole() else overlayOf(rest, contentHeight, player.height + system)
                    layout(width, fullHeight) {
                        top.placeRelative(nav.width, 0, Z_CHROME)
                        nav.placeRelative(0, 0, Z_CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(nav.width, height - player.height, Z_CHROME)
                        band.placeRelative(0, height, Z_BAND)
                        playerLayer.placeRelative(0, 0, Z_PLAYER_LAYER)
                        if (window) over.placeRelative(0, wholeTop, Z_SCREEN_OVERLAY) else over.placeRelative(nav.width, top.height, Z_SCREEN_OVERLAY)
                        overlay.placeRelative(0, 0, Z_OVERLAY)
                    }
                }
            }

            NavKind.Sidebar -> {
                val top = topM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                if (floatingPlayer) {
                    val middle = (height - top.height).coerceAtLeast(0)
                    val nav = navM.measure(Constraints(maxWidth = width, minHeight = middle, maxHeight = middle))
                    val contentWidth = (width - nav.width).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(contentWidth, middle))
                    val playerWidth = (contentWidth - 2 * margin).coerceAtMost(floatingMaxWidth.roundToPx()).coerceAtLeast(0)
                    val player = playerM.measure(Constraints(minWidth = playerWidth, maxWidth = playerWidth, maxHeight = height))
                    val overBottom = screenOverlayBottom()
                    val over = if (window) whole() else overlayOf(contentWidth, middle - overBottom, overBottom + system)
                    layout(width, fullHeight) {
                        top.placeRelative(0, 0, Z_CHROME)
                        nav.placeRelative(0, top.height, Z_CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(nav.width + (contentWidth - playerWidth) / 2, height - margin - player.height, Z_CHROME)
                        band.placeRelative(0, height, Z_BAND)
                        playerLayer.placeRelative(0, 0, Z_PLAYER_LAYER)
                        if (window) over.placeRelative(0, wholeTop, Z_SCREEN_OVERLAY) else over.placeRelative(nav.width, top.height, Z_SCREEN_OVERLAY)
                        overlay.placeRelative(0, 0, Z_OVERLAY)
                    }
                } else {
                    val player = playerM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                    val middle = (height - top.height - player.height).coerceAtLeast(0)
                    val nav = navM.measure(Constraints(maxWidth = width, minHeight = middle, maxHeight = middle))
                    val contentWidth = (width - nav.width).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(contentWidth, middle))
                    val over = if (window) whole() else overlayOf(contentWidth, middle, player.height + system)
                    layout(width, fullHeight) {
                        top.placeRelative(0, 0, Z_CHROME)
                        nav.placeRelative(0, top.height, Z_CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(0, height - player.height, Z_CHROME)
                        band.placeRelative(0, height, Z_BAND)
                        playerLayer.placeRelative(0, 0, Z_PLAYER_LAYER)
                        if (window) over.placeRelative(0, wholeTop, Z_SCREEN_OVERLAY) else over.placeRelative(nav.width, top.height, Z_SCREEN_OVERLAY)
                        overlay.placeRelative(0, 0, Z_OVERLAY)
                    }
                }
            }
        }
    }
}

/**
 * A bar slid [offset] px down, cut [below] px under its bottom (1.0.1): what slides out goes under
 * the system's navigation, not over it. 1.0.3: the bars rest on the system's band, so the cut is
 * where the band begins: the band (or the glass behind it) is what is seen there.
 */
private fun GraphicsLayerScope.slideDown(offset: Float, below: Int) {
    translationY = offset
    val visible = size.height + below - offset
    clip = offset > 0f && visible < size.height
    if (clip) shape = TopPart(visible.coerceAtLeast(0f))
}

/** The top [height] px of a layer. */
private class TopPart(private val height: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, size.width, height.coerceAtMost(size.height)))
}
