package app.nagmedar.ui.shell

import androidx.compose.runtime.Stable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** How the shell came to the page on screen, which picks the transition that plays (1.0.1). */
enum class NavMove {
    /** Another tab of the bar, the rail or the sidebar: fade-through, each tab with its own pages. */
    Tab,

    /** The tab on screen chosen again: back to its first page, fade-through. */
    Reselect,

    /** A page opened from a page: it slides in from the end edge over the one it leaves. */
    Open,

    /** Back to the page under it: the opening played in reverse. */
    Back,
}

/**
 * 1.0.1: the shell's navigation rules, free of Compose so they can be tested on their own.
 *
 * Each tab (Müzik · Podcast · Radyo · ⋯ on a phone; the rail's and the sidebar's entries) keeps a
 * stack of its own pages: a page opened from a page is pushed on the stack of the tab it was opened
 * in, so Back and the top bar's arrow return to the page that opened it. Back from another tab's
 * first page goes to Müzik, the start, as it was left; Back on Müzik's first page leaves the app.
 */
object NavRules {
    /** The tabs of [kind]'s navigation chrome: what the bottom bar, the rail or the sidebar switch between. */
    fun tabs(kind: NavKind): List<Destination> = when (kind) {
        NavKind.BottomBar -> Destination.bottomBar
        NavKind.Rail -> Destination.rail + Destination.railFooter
        NavKind.Sidebar -> Destination.sidebar
    }

    fun isTab(destination: Destination, kind: NavKind): Boolean = destination in tabs(kind)

    /**
     * The tab whose stack holds [current], the page on screen; [inStack] says whether a page is in
     * the back stack under it. Only one tab's first page is ever above Müzik's (a tab change goes
     * back to Müzik first), so the first one found is the one.
     */
    fun tabOf(current: Destination, inStack: (Destination) -> Boolean, kind: NavKind): Destination = when {
        isTab(current, kind) -> current
        else -> tabs(kind).firstOrNull { it != Destination.Music && inStack(it) } ?: Destination.Music
    }

    /** The tab lit in the chrome: the one whose stack the page is in; none on the search page, which belongs to no tab. */
    fun highlighted(current: Destination, inStack: (Destination) -> Boolean, kind: NavKind): Destination? =
        if (current == Destination.Search) null else tabOf(current, inStack, kind)

    /** What asking for [target] does from [current]: a tab change, the tab on screen again, or a page opened. */
    fun moveFor(target: Destination, current: Destination, inStack: (Destination) -> Boolean, kind: NavKind): NavMove = when {
        !isTab(target, kind) -> NavMove.Open
        target == tabOf(current, inStack, kind) -> NavMove.Reselect
        else -> NavMove.Tab
    }

    /** Back on [current] goes to Müzik, the start, rather than out of the tab: another tab's first page. */
    fun backToStart(current: Destination, kind: NavKind): Boolean = current != Destination.Music && isTab(current, kind)

    /** The top bar's back arrow (phone and rail): on every page that is not a tab's first page. */
    fun showsBack(current: Destination, kind: NavKind): Boolean = kind != NavKind.Sidebar && !isTab(current, kind)

    /** Settings and the pages under it: on a phone each is a page of its own, without the bottom bar and the mini player. */
    val fullPages: Set<Destination> = setOf(
        Destination.Settings, Destination.About, Destination.Guide, Destination.ShortcutCard, Destination.AllSettings,
    )

    /** The phone's bottom bar and mini player stand aside for [current]. */
    fun hidesBars(current: Destination, kind: NavKind): Boolean = kind == NavKind.BottomBar && current in fullPages
}

/**
 * Navigation entry point handed to screens (1.0.1): [selectTab] for the bottom bar, the rail and
 * the sidebar, [open] for what a page opens, [navigate] for whatever asks for a destination without
 * knowing which it is (the palette, the shortcuts, a launcher shortcut). The shell tells it the
 * window's [navKind], which says what is a tab.
 */
@Stable
class ShellNavigator(private val navController: NavHostController) {
    /** Where back went from, latest last: the mouse's forward button returns there (S-408). */
    private val forwardRoutes = ArrayDeque<String>()

    /** The navigation chrome of the window; set by the shell. */
    var navKind: NavKind = NavKind.BottomBar

    /** How the last change was made; the shell's transitions read it. */
    var lastMove: NavMove = NavMove.Tab
        private set

    /** The route on screen. */
    val currentRoute: String? get() = navController.currentDestination?.route

    private val current: Destination get() = Destination.fromRoute(currentRoute)

    /** [destination] is in the back stack (on screen or under it). */
    fun inStack(destination: Destination): Boolean =
        try {
            navController.getBackStackEntry(destination.route)
            true
        } catch (e: IllegalArgumentException) {
            false
        }

    /** The tab whose stack the page on screen is in. */
    val tab: Destination get() = NavRules.tabOf(current, ::inStack, navKind)

    /** A tab when [destination] is one in this window, a page opened from the one on screen otherwise. */
    fun navigate(destination: Destination) {
        forwardRoutes.clear()
        go(destination)
    }

    /**
     * A tab of the bar, the rail or the sidebar: each keeps its pages; the tab on screen goes back
     * to its first page (Android's reselect). Not a tab in this window: [open].
     */
    fun selectTab(destination: Destination) {
        forwardRoutes.clear()
        if (NavRules.isTab(destination, navKind)) switchTab(destination) else openPage(destination)
    }

    /** A page opened from the page on screen, on its tab's stack; a tab is selected instead. */
    fun open(destination: Destination) {
        forwardRoutes.clear()
        go(destination)
    }

    private fun go(destination: Destination) {
        if (NavRules.isTab(destination, navKind)) switchTab(destination) else openPage(destination)
    }

    private fun switchTab(destination: Destination) {
        if (destination == tab) {
            if (currentRoute != destination.route) {
                lastMove = NavMove.Reselect
                navController.popBackStack(destination.route, inclusive = false)
            }
            return
        }
        lastMove = NavMove.Tab
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    private fun openPage(destination: Destination) {
        if (currentRoute == destination.route) return
        if (inStack(destination)) {
            // opened before on this stack (the guide and the card of keys lead to each other): back to it
            lastMove = NavMove.Back
            navController.popBackStack(destination.route, inclusive = false)
            return
        }
        lastMove = NavMove.Open
        navController.navigate(destination.route) { launchSingleTop = true }
    }

    /**
     * Back within the app: the page under this one, or Müzik from another tab's first page; false
     * on Müzik's first page, where Back leaves the app.
     */
    fun back(): Boolean {
        val from = currentRoute
        val went = when {
            NavRules.backToStart(current, navKind) -> {
                switchTab(Destination.Music)
                true
            }
            navController.previousBackStackEntry == null -> false
            else -> {
                lastMove = NavMove.Back
                navController.popBackStack()
            }
        }
        if (went) wentBack(from)
        return went
    }

    /** The system's Back on another tab's first page: to Müzik as it was left (the activity remembers it for forward). */
    internal fun backToStart() {
        switchTab(Destination.Music)
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
