package app.nagmedar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.theme.NagTheme

/** Navigation chrome around the current screen; screens show their own title only in [NavKind.Sidebar]. */
val LocalNavKind = compositionLocalOf { NavKind.Sidebar }

/** Navigation entry point handed to screens. */
@Stable
class ShellNavigator(private val navController: NavHostController) {

    /** Switches top-level destination, keeping each destination's own back stack and state. */
    fun navigate(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun back(): Boolean = navController.popBackStack()
}

/**
 * The adaptive shell (S-301): bottom bar on compact windows, a rail on medium and expanded
 * windows, a menu bar with a collapsible sidebar on large and extra-large windows. The
 * navigation graph lives in the content slot of [ShellLayout] and survives width changes;
 * [overlay] is drawn over the current screen (undo bars).
 */
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
) {
    val navigator = remember(navController) { ShellNavigator(navController) }
    val entry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(entry?.destination?.route)
    val navKind = NavKind.forWidth(widthClass)
    val motion = NagTheme.motion
    // The graph builder below must not capture changing values, or NavHost would rebuild the graph.
    val currentScreen by rememberUpdatedState(screen)

    ShellLayout(
        navKind = navKind,
        modifier = modifier.fillMaxSize().background(NagTheme.colors.background),
        topBar = {
            if (navKind == NavKind.Sidebar) {
                MenuBarChrome(
                    menus = menus,
                    search = { SearchField(onClick = onSearch, shortcut = "Ctrl+K", modifier = Modifier.widthIn(min = 220.dp, max = 380.dp)) },
                )
            } else {
                PageTopBar(title = stringResource(current.label), onSearch = onSearch)
            }
        },
        navigation = {
            when (navKind) {
                NavKind.BottomBar -> BottomNavBar(current, navigator::navigate)
                NavKind.Rail -> NavRail(current, navigator::navigate)
                NavKind.Sidebar -> Sidebar(
                    current = current,
                    onSelect = navigator::navigate,
                    collapsed = sidebarCollapsed,
                    onToggleCollapsed = { onSidebarCollapsedChange(!sidebarCollapsed) },
                    badges = badges,
                )
            }
        },
        player = { player(navKind) },
        content = {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalNavKind provides navKind) {
                    NavHost(
                        navController = navController,
                        startDestination = Destination.Home.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { motion.screenEnter() },
                        exitTransition = { motion.screenExit() },
                        popEnterTransition = { motion.screenEnter() },
                        popExitTransition = { motion.screenExit() },
                    ) {
                        Destination.entries.forEach { destination ->
                            composable(destination.route) { currentScreen(destination, navigator) }
                        }
                    }
                }
                // undo bars and other messages float over the screen, above the player bar
                overlay()
            }
        },
    )
}
