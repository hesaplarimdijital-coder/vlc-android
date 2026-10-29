package app.nagmedar.ui.shell

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.theme.GlassMode
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalGlassMode
import app.nagmedar.ui.theme.LocalGlassSource
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glassSource
import app.nagmedar.ui.theme.rememberSystemGlassMode
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/** Navigation chrome around the current screen; screens show their own title only in [NavKind.Sidebar]. */
val LocalNavKind = compositionLocalOf { NavKind.Sidebar }

/**
 * Desktop Kit 1.0: the player bar lies in the shell's floating bar (tablet, DeX), which draws the
 * surface (a solid card since 1.0.1): the bar's content draws no background or top rule of its own.
 */
val LocalFloatingPlayer = staticCompositionLocalOf { false }

/**
 * The adaptive shell (S-301): bottom bar on compact windows, a rail on medium and expanded
 * windows, a menu bar with a collapsible sidebar on large and extra-large windows; the sidebar's
 * width is dragged and remembered (S-303). The
 * navigation graph lives in the content slot of [ShellLayout] and survives width changes;
 * [overlay] is drawn over the current screen where the bars leave it (undo bars). [contentOnly]:
 * the screen and its overlay take the whole window, the bars stand aside (S-1002: the player over
 * a tablet's window).
 *
 * As Desktop Kit 0.2.0 has it: tabs change with Android's fade-through on phones and tablets and a
 * still cross-fade on a desktop; on a phone the bottom bar and the player strip make way while the
 * content scrolls down, and the bottom bar steps aside while the keyboard is up.
 *
 * 1.0.1: each tab keeps a stack of pages ([ShellNavigator]). A page opened from a page slides in
 * from the end edge over it, and Back (the key, the predictive gesture, the top bar's arrow) plays
 * that in reverse; Back on another tab's first page goes to Müzik. The phone's "⋯" is a tab, the
 * "Daha fazla" page. Settings and the pages under it are pages of their own on a phone: the bottom
 * bar and the mini player slide away for them ([NavRules.hidesBars]). Every bar is one solid
 * colour whatever passes beneath it.
 *
 * As Desktop Kit 1.0 has it: on a phone the content fills the window and the bars lie over it; a
 * screen that lays its lists out under them asks for [rememberShellContentPadding], the others are
 * kept clear of the bars as before. The bars move in the draw phase only. The shell provides the
 * window's [MotionTier] (springs on a phone) and [GlassMode]: [glass] true lets menus and sheets be
 * frosted glass over the content (the kit's [app.nagmedar.ui.theme.NagGlass]); false, the default
 * since 1.0.1, draws them as solid surfaces. [floatingPlayer] (tablet, DeX): the [player] floats
 * over the content's bottom edge instead of being stacked under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NagmedarShell(
    widthClass: WidthClass,
    sidebarCollapsed: Boolean,
    onSidebarCollapsedChange: (Boolean) -> Unit,
    badges: NavBadges,
    onSearch: () -> Unit,
    menus: @Composable RowScope.() -> Unit,
    player: @Composable (NavKind) -> Unit,
    screen: @Composable (Destination, ShellNavigator) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    /** The navigator of [navController] the app's actions use too; the shell tells it the window's [NavKind]. */
    navigator: ShellNavigator = remember(navController) { ShellNavigator(navController) },
    overlay: @Composable BoxScope.() -> Unit = {},
    /** The desktop window's caption bar the top bar goes up into (S-306); null for none. */
    caption: CaptionBar? = null,
    contentOnly: Boolean = false,
    floatingPlayer: Boolean = false,
    glass: Boolean = false,
) {
    val entry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(entry?.destination?.route)
    val navKind = NavKind.forWidth(widthClass)
    val motion = NagTheme.motion
    val tier = NagMotion.tier(widthClass, motion.reduced)
    val systemGlass = rememberSystemGlassMode()
    val glassMode = if (!glass) GlassMode.Solid else LocalGlassMode.current ?: systemGlass
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sidebarWidth = rememberPaneWidth(SIDEBAR_PANE, NagTheme.dimens.sidebarWidth)
    // the graph builder below must not capture changing values, or NavHost would rebuild the graph
    val currentScreen by rememberUpdatedState(screen)
    // read when a transition starts, so a new width class or motion setting applies to the next one
    val currentKind by rememberUpdatedState(navKind)
    val currentMotion by rememberUpdatedState(motion)
    val currentTier by rememberUpdatedState(tier)
    val currentRtl by rememberUpdatedState(rtl)
    // a page opened from a page slides in over it and Back slides it away; tabs fade through; a desktop cross-fades still
    val paged: () -> Boolean = remember(navigator) { { currentKind != NavKind.Sidebar } }
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = remember(navigator) {
        {
            if (paged() && navigator.lastMove == NavMove.Open) currentMotion.pageEnter(currentTier, currentRtl)
            else currentMotion.screenEnter(desktop = currentKind == NavKind.Sidebar, initialScale = screenScale(currentKind), tier = currentTier)
        }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = remember(navigator) {
        {
            if (paged() && navigator.lastMove == NavMove.Open) currentMotion.pageExit(currentTier, currentRtl)
            else currentMotion.screenExit(desktop = currentKind == NavKind.Sidebar, tier = currentTier)
        }
    }
    // every pop is Back from a page (the key, the gesture, the arrow) but a tab chosen again, which goes to its first page
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = remember(navigator) {
        {
            if (paged() && navigator.lastMove != NavMove.Reselect) currentMotion.pagePopEnter(currentTier, currentRtl)
            else currentMotion.screenEnter(desktop = currentKind == NavKind.Sidebar, initialScale = screenScale(currentKind), tier = currentTier)
        }
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = remember(navigator) {
        {
            if (paged() && navigator.lastMove != NavMove.Reselect) currentMotion.pagePopExit(currentTier, currentRtl)
            else currentMotion.screenExit(desktop = currentKind == NavKind.Sidebar, tier = currentTier)
        }
    }
    // the bottom bar is not laid out while the keyboard is up: the content reaches down to it. The
    // keyboard must also take room, as isImeVisible reads true until the first insets arrive
    val imeVisible = WindowInsets.isImeVisible
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val view = LocalView.current
    val imeTakesRoom by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val showBottomNav = !(imeVisible && imeTakesRoom)
    val scroll = remember { ShellScrollState() }
    val metrics = remember { ShellChromeMetrics() }
    val overlayHost = remember { ShellOverlayHost() }
    val glassSource = remember { HazeState() }
    val padding = remember(metrics, density) { shellPaddingValues(metrics, density) }
    val floating = floatingPlayer && navKind != NavKind.BottomBar
    val floatingMargin = 12.dp
    val phone = navKind == NavKind.BottomBar && !contentOnly
    // the tab lit: the one whose pages are on screen (none on the search page)
    val selected = NavRules.highlighted(current, navigator::inStack, navKind)
    // Settings and its pages on a phone: the bottom bar and the mini player slide away, the page takes their room
    val barsHidden = NavRules.hidesBars(current, navKind)
    val hidden = animateFloatAsState(
        if (barsHidden) 1f else 0f,
        NagMotion.spatial(tier, tween(NagMotion.LONG, easing = NagMotion.Emphasized)),
        label = "barsHidden",
    )
    // how far the bottom bar and the player strip are down: scrolled away, or away for a page of their own
    val bottomOffset: () -> Float = remember(scroll, metrics, hidden) {
        { maxOf(scroll.offsetPx, hidden.value * (metrics.navPx + metrics.playerPx)) }
    }
    // the phone's player covers the window: only its end, not every frame of the drag, re-measures the overlays
    val playerOverState = remember(overlayHost) { derivedStateOf { overlayHost.expandProgress() >= 1f } }
    val playerOver by playerOverState
    SideEffect {
        navigator.navKind = navKind
        scroll.enabled = navKind == NavKind.BottomBar
        scroll.hideOnScroll = showBottomNav
        scroll.tier = tier
        metrics.underTop = phone
        metrics.underBottom = phone
        metrics.barsHidden = barsHidden
        metrics.underFloatingPlayer = floating && !contentOnly
        metrics.floatingMarginPx = with(density) { floatingMargin.roundToPx() }
        if (!showBottomNav || navKind != NavKind.BottomBar) metrics.navPx = 0
        overlayHost.onExpandStart = { scroll.showBars() }
        overlayHost.navigationOffset = { if (navKind == NavKind.BottomBar) bottomOffset() else 0f }
        overlayHost.rootView = view.rootView
    }
    // every screen starts with the bars shown
    LaunchedEffect(current, navKind, showBottomNav, contentOnly) { scroll.reset() }
    // the top bar's arrow does what Back does: a detail open in the page closes first, as with the key
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack: () -> Unit = {
        val from = navigator.currentRoute
        if (backDispatcher != null) {
            backDispatcher.onBackPressed()
            navigator.wentBack(from)
        } else {
            navigator.back()
        }
    }

    CompositionLocalProvider(
        LocalMotionTier provides tier,
        LocalGlassMode provides glassMode,
        LocalGlassSource provides glassSource,
        LocalShellOverlayHost provides overlayHost,
        LocalShellContentPadding provides padding,
    ) {
        ShellLayout(
            navKind = navKind,
            modifier = modifier.fillMaxSize().background(NagTheme.colors.background),
            contentOnly = contentOnly,
            navigationOffset = { if (navKind == NavKind.BottomBar) bottomOffset().roundToInt() else 0 },
            topBar = {
                if (navKind == NavKind.Sidebar) {
                    // the menus are a pointer's and a keyboard's (S-302); by touch the bar keeps the search
                    MenuBarChrome(
                        menus = if (pointer) menus else ({}),
                        search = { SearchField(onClick = onSearch, shortcut = "Ctrl+K", modifier = Modifier.widthIn(min = 220.dp, max = 380.dp)) },
                        caption = caption,
                    )
                } else {
                    PageTopBar(
                        title = stringResource(current.label),
                        onSearch = onSearch.takeIf { current != Destination.Search },
                        caption = caption,
                        onBack = onBack.takeIf { NavRules.showsBack(current, navKind) },
                        modifier = Modifier.onSizeChanged { metrics.topPx = it.height }.underPlayer(playerOver),
                    )
                }
            },
            navigation = {
                when (navKind) {
                    NavKind.BottomBar -> if (showBottomNav) {
                        BottomNavBar(
                            selected,
                            navigator::selectTab,
                            Modifier
                                .onSizeChanged {
                                    scroll.maxOffsetPx = it.height.toFloat()
                                    metrics.navPx = it.height
                                }
                                // an expanding player pushes the bar out of the way; once it covers the window the bar is not drawn
                                .graphicsLayer {
                                    translationY = overlayHost.expandProgress() * size.height
                                    alpha = if (playerOver) 0f else 1f
                                }
                                .then(if (playerOver || barsHidden) Modifier.clearAndSetSemantics {} else Modifier),
                        )
                    }
                    NavKind.Rail -> NavRail(selected, navigator::selectTab)
                    NavKind.Sidebar -> Sidebar(
                        selected = selected,
                        onSelect = navigator::selectTab,
                        collapsed = sidebarCollapsed,
                        onToggleCollapsed = { onSidebarCollapsedChange(!sidebarCollapsed) },
                        badges = badges,
                        width = sidebarWidth,
                    )
                }
            },
            player = {
                val shape = RoundedCornerShape(NagTheme.dimens.radiusCard.coerceAtLeast(12.dp))
                val colors = NagTheme.colors
                Box(
                    Modifier
                        .onSizeChanged { metrics.playerPx = it.height }
                        .underPlayer(playerOver)
                        .then(if (barsHidden && navKind == NavKind.BottomBar) Modifier.clearAndSetSemantics {} else Modifier)
                        .then(
                            // a solid card with its border, whatever passes beneath (1.0.1)
                            if (!floating) Modifier else Modifier
                                .clip(shape)
                                .background(colors.surfaceRaised, shape)
                                .border(1.dp, colors.borderStrong, shape),
                        ),
                ) {
                    CompositionLocalProvider(LocalFloatingPlayer provides floating) { player(navKind) }
                }
            },
            content = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .underPlayer(playerOver)
                        .glassSource(glassSource, glassMode)
                        .background(NagTheme.colors.background)
                        .nestedScroll(scroll.connection),
                ) {
                    CompositionLocalProvider(LocalNavKind provides navKind, LocalWidthClass provides widthClass) {
                        NavHost(
                            navController = navController,
                            startDestination = Destination.Music.route,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = enter,
                            exitTransition = exit,
                            popEnterTransition = popEnter,
                            popExitTransition = popExit,
                        ) {
                            Destination.entries.forEach { destination ->
                                composable(destination.route) {
                                    // another tab's first page: Back goes to Müzik as it was left. First in the page, so
                                    // what the page opens (a detail, a selection) takes Back before it; not while the
                                    // phone's player, whose handler is older, is open over it
                                    if (destination != Destination.Music) {
                                        BackHandler(enabled = NavRules.backToStart(destination, currentKind) && !playerOverState.value) {
                                            navigator.backToStart()
                                        }
                                    }
                                    ShellScreenFrame(metrics, scroll, bars = !NavRules.hidesBars(destination, currentKind)) {
                                        currentScreen(destination, navigator)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            // undo bars and other messages float over the screen, above the player bar
            screenOverlay = {
                CompositionLocalProvider(LocalNavKind provides navKind, LocalWidthClass provides widthClass) {
                    Box(Modifier.fillMaxSize()) { overlay() }
                }
            },
            // over the phone's expanded player, the screen's overlays take the whole window
            screenOverlayTop = { if (playerOver) 0 else metrics.topPadPx },
            screenOverlayBottom = { if (playerOver) 0 else metrics.bottomPadPx },
            playerLayer = {
                Box(Modifier.fillMaxSize().onGloballyPositioned { overlayHost.coordinates = it }) {
                    overlayHost.layers.forEach { layer -> if (layer.z == ShellOverlayHost.PLAYER) key(layer) { layer.content() } }
                }
            },
            overlay = {
                Box(Modifier.fillMaxSize()) {
                    overlayHost.layers.forEach { layer -> if (layer.z != ShellOverlayHost.PLAYER) key(layer) { layer.content() } }
                }
            },
            floatingPlayer = floating,
            floatingMargin = floatingMargin,
            // the transport bar of a desktop window needs the room of its three parts; a tablet's mini player less
            floatingMaxWidth = if (navKind == NavKind.Sidebar) 1180.dp else 760.dp,
        )
    }
}

/**
 * What the phone's expanded player covers (1.0), while [covered]: not drawn (no overdraw, no blur
 * under the opaque player on every frame of its seek bar or visualiser) and not read out, as
 * [DetailTransition]'s covered list. The layer is always there, so the player's first move down
 * only turns it visible again, without recording anything anew.
 */
private fun Modifier.underPlayer(covered: Boolean): Modifier =
    graphicsLayer { alpha = if (covered) 0f else 1f }.then(if (covered) Modifier.clearAndSetSemantics {} else Modifier)

/** Where a new screen grows from in the fade-through: a phone's, a tablet's (no movement on a desktop). */
private fun screenScale(kind: NavKind): Float = if (kind == NavKind.BottomBar) NagMotion.PHONE_SCALE else NagMotion.TABLET_SCALE
