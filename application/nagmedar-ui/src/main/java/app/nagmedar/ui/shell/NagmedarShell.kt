package app.nagmedar.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.components.BackSource
import app.nagmedar.ui.components.rememberBackSwipes
import app.nagmedar.ui.components.coveredByLayer
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
 * a tablet's window). 1.0.2: on tablet and DeX a [WindowPage] in the overlay (the big player) has
 * the whole window from its first frame, below the [caption] bar, and once it covers it the bars
 * stand aside as with [contentOnly].
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
 *
 * 1.0.3 (Desktop Kit 2.0): the shell owns the system's navigation bar at the window's bottom (the
 * three buttons or the gesture handle; the root leaves it, [LocalWindowRootInsets]): on a phone the
 * bottom bar's colour continues behind the buttons and stays there, a rule over it, when the bars
 * slide away; on tablets and DeX the chrome colour lies there; with the keyboard up there is none.
 * The screens' bottom padding includes it on a phone, full pages too. Tab and TalkBack go top bar,
 * navigation, content, player, a phone's bottom bar last. [chromeGlass]: the phone's chrome, by the
 * user's decision of 6 Nov the bottom one on one glass ([ChromeGlass.Bottom]), which is off while the
 * expanded player or a page covers the window: nothing is blurred under their colour.
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
    /** 1.0.3: the phone's chrome on glass, the user's decision of 6 Nov (B); a debuggable build's walk compares the others. */
    chromeGlass: ChromeGlass = ChromeGlass.Product,
) {
    val entry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(entry?.destination?.route)
    val navKind = NavKind.forWidth(widthClass)
    val motion = NagTheme.motion
    // 1.0.8 (Desktop Kit 2.8.0): a phone on its side keeps the phone's motion and its player under the content
    val short = LocalHeightClass.current == HeightClass.Compact
    val tier = NagMotion.tier(widthClass, motion.reduced, LocalHeightClass.current)
    val systemGlass = rememberSystemGlassMode()
    val glassMode = if (!glass) GlassMode.Solid else LocalGlassMode.current ?: systemGlass
    // 1.0.3: the phone's bars on glass (the user's decision; solid in the product) while menus and sheets stay as [glass] says
    val chromeMode = if (chromeGlass == ChromeGlass.Solid) GlassMode.Solid else LocalGlassMode.current ?: systemGlass
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
    // 1.0.3 (Desktop Kit 2.4.0): which page lies under the one moving over it, and which tab the Back gesture is
    // leaving, told to the pages ([ShellPage]: the one sinks and darkens, the other rounds its corners). Written
    // as navigation asks for the transitions: it keeps the pages' z-order itself
    val pages = remember(navigator) { ShellPages() }
    val reducedNow: () -> Boolean = { currentMotion.reduced || currentTier == MotionTier.Reduced }
    // 1.0.3: a pop between tabs (Back from another tab's first page, the Müzik tab chosen) fades through as a tab
    // chosen does; every other pop is a page's Back
    val tabPop: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Boolean = remember(navigator) {
        {
            NavRules.tabPop(
                Destination.fromRoute(initialState.destination.route),
                Destination.fromRoute(targetState.destination.route),
                currentKind,
                navigator.moveFrom(initialState.id),
            )
        }
    }
    val pushed: () -> Boolean = remember(navigator) { { paged() && navigator.lastMove == NavMove.Open } }
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = remember(navigator) {
        {
            if (pushed()) {
                pages.over(initialState.id)
                currentMotion.pageEnter(currentTier, currentRtl)
            } else {
                pages.tabs()
                currentMotion.screenEnter(desktop = currentKind == NavKind.Sidebar, initialScale = screenScale(currentKind), tier = currentTier)
            }
        }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = remember(navigator) {
        {
            // the page under stays where it is, whole, until the page over it has arrived: its surface sinks and darkens it
            if (pushed()) {
                pages.over(initialState.id)
                if (reducedNow()) ExitTransition.None else ExitTransition.KeepUntilTransitionsFinished
            } else {
                pages.tabs()
                currentMotion.screenExit(desktop = currentKind == NavKind.Sidebar, tier = currentTier)
            }
        }
    }
    // a page's Back: the page leaves over the end edge, the one under it, in its place, rises out of the scrim
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = remember(navigator) {
        {
            if (paged() && !tabPop(this)) {
                pages.over(targetState.id)
                EnterTransition.None
            } else {
                pages.tabs()
                currentMotion.screenEnter(desktop = currentKind == NavKind.Sidebar, initialScale = screenScale(currentKind), tier = currentTier)
            }
        }
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = remember(navigator) {
        {
            if (paged() && !tabPop(this)) {
                pages.over(targetState.id)
                currentMotion.pagePopExit(currentTier, currentRtl)
            } else {
                pages.tabs()
                currentMotion.screenExit(desktop = currentKind == NavKind.Sidebar, tier = currentTier)
            }
        }
    }
    // 1.0.3: the predictive Back gesture seeks the same pops with the finger, as navigation 2.8 did; 2.10 would
    // otherwise play its own (the page shrinking to 70 %). Between the phone's tabs (Desktop Kit 2.3) its own:
    // the tab left shrinks toward 90 % with the finger, leaning away from the edge the swipe came from, and
    // Müzik rises behind it from 94 % ([tabBackEnter], [tabBackExit]). The Back button is no swipe (Desktop Kit
    // 2.4.0): since Android 16 a press of the three buttons' Back or of a hardware Back key starts a predictive
    // Back too, with no edge ([BackEventCompat.EDGE_NONE]), which navigation plays through on the release: it
    // gets the plain pops, the tabs' fade-through and the pages' slide (1.0.4: [BackSource], the details' and
    // the player's rule too)
    // 1.0.3: gesture navigation, whose Back is a swipe from the window's side edges (the system keeps them for it);
    // three-button navigation has no such edges ([rememberBackSwipes]: 1.0.4, the details' and the player's rule too)
    val edgeBack by rememberBackSwipes()
    val currentEdgeBack by rememberUpdatedState(edgeBack)
    val tabBack: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> Boolean = remember(navigator) {
        { edge ->
            BackSource.finger(edge, currentEdgeBack) && currentKind == NavKind.BottomBar && NavRules.tabBack(
                Destination.fromRoute(initialState.destination.route),
                Destination.fromRoute(targetState.destination.route),
                currentKind,
            )
        }
    }
    val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition =
        remember(popEnter, tabBack) {
            { edge ->
                if (tabBack(this, edge)) {
                    pages.gesture(initialState.id)
                    tabBackEnter(currentMotion, currentTier)
                } else {
                    popEnter(this)
                }
            }
        }
    val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition =
        remember(popExit, tabBack) {
            { edge ->
                if (tabBack(this, edge)) {
                    pages.gesture(initialState.id)
                    tabBackExit(currentMotion, currentTier, edge)
                } else {
                    popExit(this)
                }
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
    val floating = floatingPlayer && navKind != NavKind.BottomBar && !short
    val floatingMargin = 12.dp
    val phone = navKind == NavKind.BottomBar && !contentOnly
    // 1.0.3: the system's navigation bar at the window's bottom, which the root leaves to the shell (read while measuring)
    val rootInsets = LocalWindowRootInsets.current
    val systemBottom: () -> Int = remember(rootInsets, density) { { rootInsets?.navigationBottom?.getBottom(density) ?: 0 } }
    // the phone's one bottom glass, the user's decision (B): behind the mini player, the bottom bar and the band; thick, so
    // that the card's grey artist line and times keep 4.5:1 over a white cover
    val colors = NagTheme.colors
    val onBottomGlass = chromeGlass != ChromeGlass.Solid && navKind == NavKind.BottomBar && !contentOnly
    // not drawn while the expanded player or a page covers the window (as what it lies on): nothing is blurred under their
    // colour. A layer's alpha, not the glass taken away: the blur keeps its source and is right again from the first frame
    val glassSurface: @Composable () -> Modifier = {
        Modifier
            .graphicsLayer { alpha = if (overlayHost.expandProgress() >= 1f || overlayHost.windowCovered) 0f else 1f }
            .glass(NagGlass.chrome(colors, GlassThickness.Thick), source = glassSource, mode = chromeMode)
    }
    val bottomGlass: (@Composable () -> Modifier)? = if (onBottomGlass) glassSurface else null
    // 1.0.2: a page over a tablet's or a desktop's whole window (the big player) covers it: the bars stand aside
    val chromeAway = contentOnly || (navKind != NavKind.BottomBar && overlayHost.windowCovered)
    // the tab lit: the one whose pages are on screen (none on the search page)
    val selected = NavRules.highlighted(current, navigator::inStack, navKind)
    // Settings and its pages on a phone: the bottom bar and the mini player slide away, the page takes their room
    val barsHidden = NavRules.hidesBars(current, navKind)
    val hidden = animateFloatAsState(
        if (barsHidden) 1f else 0f,
        NagMotion.spatial(tier, tween(NagMotion.LONG, easing = NagMotion.Emphasized), NagMotion.FRACTION),
        label = "barsHidden",
    )
    // how far the bottom bar and the player strip are down: scrolled away, or away for a page of their own
    val bottomOffset: () -> Float = remember(scroll, metrics, hidden) {
        { maxOf(scroll.offsetPx, hidden.value * (metrics.navPx + metrics.playerPx)) }
    }
    // the phone's player covers the window: only its end, not every frame of the drag, re-measures the overlays;
    // as does a page of the screen's overlay over the whole window, once it is open (1.0.1: Sıradaki on a phone)
    val playerOverState = remember(overlayHost) { derivedStateOf { overlayHost.expandProgress() >= 1f || overlayHost.windowCovered } }
    val playerOver by playerOverState
    // what is a tab in this window, known before any effect asks the navigator for a page (a launcher shortcut's);
    // a plain field, so writing it here recomposes nothing
    navigator.navKind = navKind
    SideEffect {
        scroll.enabled = navKind == NavKind.BottomBar
        scroll.hideOnScroll = showBottomNav
        scroll.tier = tier
        metrics.underTop = phone
        metrics.underBottom = phone
        metrics.barsHidden = barsHidden
        metrics.underFloatingPlayer = floating && !chromeAway
        metrics.floatingMarginPx = with(density) { floatingMargin.roundToPx() }
        if (!showBottomNav || navKind != NavKind.BottomBar) metrics.navPx = 0
        metrics.systemBottomPx = systemBottom
        overlayHost.systemBottomPx = systemBottom
        overlayHost.navigationOffset = { if (navKind == NavKind.BottomBar) bottomOffset() else 0f }
        overlayHost.rootView = view.rootView
    }
    // every screen starts with the bars shown
    LaunchedEffect(current, navKind, showBottomNav, chromeAway) { scroll.reset() }
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
            contentOnly = chromeAway,
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
                        // the kit's top bar (C of the user's glass decision): glass while content passes under it
                        glass = if (chromeGlass == ChromeGlass.Kit && navKind == NavKind.BottomBar) chromeMode else null,
                        scrolled = { scroll.scrolled },
                    )
                }
            },
            navigation = {
                when (navKind) {
                    NavKind.BottomBar -> if (showBottomNav) {
                        BottomNavBar(
                            selected,
                            navigator::selectTab,
                            modifier = Modifier
                                .onSizeChanged {
                                    scroll.maxOffsetPx = it.height.toFloat()
                                    metrics.navPx = it.height
                                }
                                // an expanding player pushes the bar out of the way; once it covers the window the bar is not drawn
                                .graphicsLayer {
                                    translationY = overlayHost.expandProgress() * size.height
                                    alpha = if (playerOver) 0f else 1f
                                }
                                .then(if (playerOver || barsHidden) Modifier.clearAndSetSemantics {} else Modifier)
                                .coveredByLayer(playerOver),
                            onGlass = onBottomGlass,
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
                Box(
                    Modifier
                        .onSizeChanged { metrics.playerPx = it.height }
                        .underPlayer(playerOver)
                        .then(if (barsHidden && navKind == NavKind.BottomBar) Modifier.clearAndSetSemantics {} else Modifier)
                        // a solid card with its border, whatever passes beneath (1.0.1)
                        .then(if (floating) Modifier.floatingPlayerCard() else Modifier),
                ) {
                    CompositionLocalProvider(
                        LocalFloatingPlayer provides floating,
                        LocalOnBottomGlass provides onBottomGlass,
                        LocalBottomGlassSurface provides bottomGlass,
                    ) { player(navKind) }
                }
            },
            content = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .underPlayer(playerOver)
                        // what the glass blurs: menus and sheets ([glass]) or the phone's bars on glass ([chromeGlass])
                        .glassSource(glassSource, if (glassMode != GlassMode.Solid) glassMode else chromeMode)
                        .background(NagTheme.colors.background)
                        .nestedScroll(scroll.connection),
                ) {
                    CompositionLocalProvider(LocalNavKind provides navKind, LocalWidthClass provides widthClass, LocalShellScroll provides scroll) {
                        NavHost(
                            navController = navController,
                            startDestination = Destination.Music.route,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = enter,
                            exitTransition = exit,
                            popEnterTransition = popEnter,
                            popExitTransition = popExit,
                            predictivePopEnterTransition = predictivePopEnter,
                            predictivePopExitTransition = predictivePopExit,
                        ) {
                            Destination.entries.forEach { destination ->
                                composable(destination.route) { pageEntry ->
                                    // another tab's first page: Back goes to Müzik as it was left. First in the page, so
                                    // what the page opens (a detail, a selection) takes Back before it; not while the
                                    // phone's player, whose handler is older, is open over it. 1.0.3: nor on a phone with
                                    // gesture navigation, whose Back gesture the navigation plays with the finger
                                    if (destination != Destination.Music) {
                                        BackHandler(
                                            enabled = NavRules.backToStart(destination, currentKind) && !playerOverState.value &&
                                                !NavRules.tabBackFollowsFinger(currentKind, currentEdgeBack),
                                        ) {
                                            navigator.backToStart()
                                        }
                                    }
                                    ShellPage(
                                        transition,
                                        paged = paged(),
                                        under = pages.under == pageEntry.id,
                                        leaving = pages.leaving == pageEntry.id,
                                        tier = currentTier,
                                    ) {
                                        ShellScreenFrame(metrics, scroll, bars = !NavRules.hidesBars(destination, currentKind)) {
                                            currentScreen(destination, navigator)
                                        }
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
            // over the phone's expanded player, the screen's overlays take the whole window; with a page over the whole
            // window too, from its first frame (1.0.1)
            screenOverlayTop = { if (playerOver || overlayHost.windowPageShown) 0 else metrics.topPadPx },
            screenOverlayBottom = { if (playerOver || overlayHost.windowPageShown) 0 else metrics.bottomPadPx },
            // 1.0.2: on tablet and DeX the same page has the whole window, under a desktop window's caption bar
            screenOverlayWindow = { overlayHost.windowPageShown },
            windowTop = caption?.height ?: 0.dp,
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
            // 1.0.3: the system's navigation bar, the chrome continued behind it; none with the keyboard up
            systemBottom = systemBottom,
            systemBand = {
                SystemBand(
                    navKind,
                    // the rule over the band shows as far as the bars have slid away beneath it
                    hidden = { if (metrics.navPx > 0) bottomOffset() / metrics.navPx else if (barsHidden) 1f else 0f },
                    onGlass = onBottomGlass,
                )
            },
            bottomGlass = bottomGlass,
        )
    }
}

/**
 * The floating player's card (Desktop Kit 1.0, solid since 1.0.1): the surface, the border and
 * the corners of the tablet's and the desktop's floating bar. 1.0.2: the phone's bar is the same
 * card ([app.nagmedar.ui.playback.CompactTransportBar]). 1.0.3: on the phone's bottom glass (the
 * user's glass decision, [LocalOnBottomGlass]) only its border and corners: the glass is its surface.
 */
@Composable
fun Modifier.floatingPlayerCard(): Modifier {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(floatingPlayerCorner)
    val fill = if (LocalOnBottomGlass.current) this.clip(shape) else this.clip(shape).background(colors.surfaceRaised, shape)
    return fill.border(1.dp, colors.borderStrong, shape)
}

/** The floating player card's corners ([floatingPlayerCard]); 1.0.3: the phone's full player grows out of this card. */
internal val floatingPlayerCorner: Dp
    @Composable get() = NagTheme.dimens.radiusCard.coerceAtLeast(12.dp)

/**
 * What the phone's expanded player covers (1.0), while [covered]: not drawn (no overdraw, no blur
 * under the opaque player on every frame of its seek bar or visualiser) and not read out, as
 * [DetailTransition]'s covered list. The layer is always there, so the player's first move down
 * only turns it visible again, without recording anything anew. 1.0.3: nor focused by a hardware
 * keyboard's Tab, nor given the screen's keys (Ctrl+A) ([coveredByLayer]).
 */
private fun Modifier.underPlayer(covered: Boolean): Modifier =
    graphicsLayer { alpha = if (covered) 0f else 1f }.then(if (covered) Modifier.clearAndSetSemantics {} else Modifier).coveredByLayer(covered)

/** Where a new screen grows from in the fade-through: a phone's, a tablet's (no movement on a desktop). */
private fun screenScale(kind: NavKind): Float = if (kind == NavKind.BottomBar) NagMotion.PHONE_SCALE else NagMotion.TABLET_SCALE

/** How long the phone's Back gesture between tabs is seeked over (the finger's part and the way out once let go). */
private const val TAB_BACK_MILLIS = 300

/** Where the tab left by the Back gesture shrinks toward ([tabBackExit]) and where Müzik rises from behind it ([tabBackEnter]). */
internal const val TAB_BACK_SHRINK = 0.9f
internal const val TAB_BACK_BEHIND = 0.94f

/**
 * 1.0.3 (Desktop Kit 2.3): the phone's predictive Back gesture from another tab's first page, Müzik's side: it
 * rises from [TAB_BACK_BEHIND] and brightens behind the tab being left. Navigation seeks it with the finger, plays
 * the rest once let go and runs it back on a cancel; with reduced motion nothing moves.
 */
internal fun tabBackEnter(motion: NagMotion, tier: MotionTier): EnterTransition =
    if (motion.reduced || tier == MotionTier.Reduced) {
        EnterTransition.None
    } else {
        scaleIn(tween(TAB_BACK_MILLIS, easing = NagMotion.Standard), initialScale = TAB_BACK_BEHIND) +
            fadeIn(tween(TAB_BACK_MILLIS, easing = NagMotion.Standard))
    }

/**
 * The tab being left by that gesture: with the finger it shrinks toward [TAB_BACK_SHRINK] and leans a twentieth of
 * its width away from the edge the swipe came from ([swipeEdge]), whole until the last tenth of the way, where it
 * fades; as a detail does under the same gesture. With reduced motion nothing moves.
 */
internal fun tabBackExit(motion: NagMotion, tier: MotionTier, swipeEdge: Int): ExitTransition {
    if (motion.reduced || tier == MotionTier.Reduced) return ExitTransition.None
    val away = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
    val lastTenth = Easing { fraction -> ((fraction - 0.9f) / 0.1f).coerceIn(0f, 1f) }
    return scaleOut(tween(TAB_BACK_MILLIS, easing = LinearEasing), targetScale = TAB_BACK_SHRINK) +
        slideOutHorizontally(tween(TAB_BACK_MILLIS, easing = LinearEasing)) { width -> away * width / 20 } +
        fadeOut(tween(TAB_BACK_MILLIS, easing = lastTenth))
}
