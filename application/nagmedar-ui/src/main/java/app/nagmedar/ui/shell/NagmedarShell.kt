package app.nagmedar.ui.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.theme.GlassMode
import app.nagmedar.ui.theme.GlassThickness
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalGlassMode
import app.nagmedar.ui.theme.LocalGlassSource
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glass
import app.nagmedar.ui.theme.glassSource
import app.nagmedar.ui.theme.rememberSystemGlassMode
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/** Navigation chrome around the current screen; screens show their own title only in [NavKind.Sidebar]. */
val LocalNavKind = compositionLocalOf { NavKind.Sidebar }

/** Navigation entry point handed to screens. */
@Stable
class ShellNavigator(private val navController: NavHostController) {
    /** Where back went from, latest last: the mouse's forward button returns there (S-408). */
    private val forwardRoutes = ArrayDeque<String>()

    /** The route on screen. */
    val currentRoute: String? get() = navController.currentDestination?.route

    /** Switches top-level destination, keeping each destination's own back stack and state. */
    fun navigate(destination: Destination) {
        forwardRoutes.clear()
        go(destination)
    }

    private fun go(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun back(): Boolean {
        val from = currentRoute
        val popped = navController.popBackStack()
        if (popped) wentBack(from)
        return popped
    }

    /** A back that happened elsewhere (the system's) left [from]: forward can return to it. */
    fun wentBack(from: String?) {
        if (from != null && from != currentRoute) forwardRoutes.addLast(from)
    }

    /** The mouse's forward button: to where back last went from; false when there is nowhere. */
    fun forward(): Boolean {
        val route = forwardRoutes.removeLastOrNull() ?: return false
        go(Destination.fromRoute(route))
        return true
    }
}

/**
 * The adaptive shell (S-301): bottom bar on compact windows, a rail on medium and expanded
 * windows, a menu bar with a collapsible sidebar on large and extra-large windows; the sidebar's
 * width is dragged and remembered (S-303). The
 * navigation graph lives in the content slot of [ShellLayout] and survives width changes;
 * [overlay] is drawn over the current screen where the bars leave it (undo bars). [contentOnly]:
 * the screen and its overlay take the whole window, the bars stand aside (S-1002: the player over
 * a tablet's window).
 *
 * As Desktop Kit 0.2.0 has it: screens change with Android's fade-through on phones and tablets
 * and a still cross-fade on a desktop (Back plays the change along with the predictive gesture);
 * on a phone the bottom bar and the player strip make way while the content scrolls down, the
 * top bar takes the chrome colour once the content has left its top, and the bottom bar steps
 * aside while the keyboard is up.
 *
 * As Desktop Kit 1.0 has it: on a phone the content fills the window and the bars lie over it as
 * frosted glass ([app.nagmedar.ui.theme.NagGlass]); a screen that lays its lists out under them
 * asks for [rememberShellContentPadding], the others are kept clear of the bars as before. The
 * bars move in the draw phase only. The content area is the glass source of the bars, sheets and
 * menus; the shell provides the window's [MotionTier] (springs on a phone) and [GlassMode].
 * [floatingPlayer] (tablet, DeX): the [player] floats over the content's bottom edge instead of
 * being stacked under it. [glass] false draws every bar, sheet and menu as a solid surface.
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
    overlay: @Composable BoxScope.() -> Unit = {},
    /** The desktop window's caption bar the top bar goes up into (S-306); null for none. */
    caption: CaptionBar? = null,
    contentOnly: Boolean = false,
    floatingPlayer: Boolean = false,
    glass: Boolean = true,
) {
    val navigator = remember(navController) { ShellNavigator(navController) }
    val entry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(entry?.destination?.route)
    val navKind = NavKind.forWidth(widthClass)
    val motion = NagTheme.motion
    val tier = NagMotion.tier(widthClass, motion.reduced)
    val systemGlass = rememberSystemGlassMode()
    val glassMode = if (!glass) GlassMode.Solid else LocalGlassMode.current ?: systemGlass
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val sidebarWidth = rememberPaneWidth(SIDEBAR_PANE, NagTheme.dimens.sidebarWidth)
    // The graph builder below must not capture changing values, or NavHost would rebuild the graph.
    val currentScreen by rememberUpdatedState(screen)
    // read when a transition starts, so a new width class or motion setting applies to the next one
    val currentKind by rememberUpdatedState(navKind)
    val currentMotion by rememberUpdatedState(motion)
    val currentTier by rememberUpdatedState(tier)
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = remember {
        { currentMotion.screenEnter(desktop = currentKind == NavKind.Sidebar, initialScale = screenScale(currentKind), tier = currentTier) }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = remember {
        { currentMotion.screenExit(desktop = currentKind == NavKind.Sidebar, tier = currentTier) }
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
    SideEffect {
        scroll.enabled = navKind == NavKind.BottomBar
        scroll.hideOnScroll = showBottomNav
        scroll.tier = tier
        metrics.underTop = phone
        metrics.underBottom = phone
        metrics.underFloatingPlayer = floating && !contentOnly
        metrics.floatingMarginPx = with(density) { floatingMargin.roundToPx() }
        if (!showBottomNav || navKind != NavKind.BottomBar) metrics.navPx = 0
        overlayHost.onExpandStart = { scroll.showBars() }
        overlayHost.rootView = view.rootView
    }
    // every screen starts with the bars shown and a flat top bar
    LaunchedEffect(current, navKind, showBottomNav, contentOnly) { scroll.reset() }

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
            navigationOffset = { if (navKind == NavKind.BottomBar) scroll.offsetPx.roundToInt() else 0 },
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
                        scrolled = navKind == NavKind.BottomBar && scroll.scrolled,
                        glass = navKind == NavKind.BottomBar,
                        modifier = Modifier.onSizeChanged { metrics.topPx = it.height },
                    )
                }
            },
            navigation = {
                when (navKind) {
                    NavKind.BottomBar -> if (showBottomNav) {
                        BottomNavBar(
                            current,
                            navigator::navigate,
                            Modifier
                                .onSizeChanged {
                                    scroll.maxOffsetPx = it.height.toFloat()
                                    metrics.navPx = it.height
                                }
                                // an expanding player pushes the bar out of the way
                                .graphicsLayer { translationY = overlayHost.expandProgress() * size.height },
                        )
                    }
                    NavKind.Rail -> NavRail(current, navigator::navigate)
                    NavKind.Sidebar -> Sidebar(
                        current = current,
                        onSelect = navigator::navigate,
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
                        .then(
                            if (!floating) Modifier else Modifier
                                .glass(NagGlass.raised(colors, GlassThickness.Thin), shape)
                                .border(1.dp, colors.borderStrong, shape),
                        ),
                ) { player(navKind) }
            },
            content = {
                Box(
                    Modifier
                        .fillMaxSize()
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
                            popEnterTransition = enter,
                            popExitTransition = exit,
                        ) {
                            Destination.entries.forEach { destination ->
                                composable(destination.route) {
                                    ShellScreenFrame(metrics, scroll) { currentScreen(destination, navigator) }
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
            screenOverlayTop = { metrics.topPadPx },
            screenOverlayBottom = { metrics.bottomPadPx },
            overlay = {
                Box(Modifier.fillMaxSize().onGloballyPositioned { overlayHost.coordinates = it }) {
                    overlayHost.layers.forEach { layer -> key(layer) { layer.content() } }
                }
            },
            floatingPlayer = floating,
            floatingMargin = floatingMargin,
        )
    }
}

/** Where a new screen grows from in the fade-through: a phone's, a tablet's (no movement on a desktop). */
private fun screenScale(kind: NavKind): Float = if (kind == NavKind.BottomBar) NagMotion.PHONE_SCALE else NagMotion.TABLET_SCALE
