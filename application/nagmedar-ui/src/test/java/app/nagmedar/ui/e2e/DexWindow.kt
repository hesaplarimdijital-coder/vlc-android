package app.nagmedar.ui.e2e

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.AppActions
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.SamplePlayerBar
import app.nagmedar.ui.SampleScreen
import app.nagmedar.ui.WindowKeys
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.components.rememberMenuBarState
import app.nagmedar.ui.components.rememberUndoBarState
import app.nagmedar.ui.input.DropTargets
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.menuSpecs
import app.nagmedar.ui.playback.QueuePanelState
import app.nagmedar.ui.search.CommandPalette
import app.nagmedar.ui.search.commandItems
import app.nagmedar.ui.search.screenItems
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.NagmedarShell
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.NagTheme

/**
 * S-1403: a DeX window as NagmedarActivity and NagmedarMain build it: the real shell with its menu
 * bar and command palette, the window's keys (WindowKeys, before the views and after them) and the
 * app's actions (AppActions). The library's screens are the samples (the medialibrary is native).
 * The system's file pickers answer at once: a file to open is [picked], a place to save [saved].
 */
class DexWindow {
    var picked: Uri? = null
    var saved: Uri? = null

    /** The pickers asked for, by their contract's name: "OpenDocument", "CreateDocument". */
    val asked = mutableListOf<String>()

    lateinit var actions: AppActions
        private set
    lateinit var navController: NavHostController
        private set
    lateinit var inputModes: InputModeManager
        private set

    /** The screen on show. */
    val destination: Destination get() = Destination.fromRoute(navController.currentDestination?.route)

    private val pickers = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                asked += contract::class.java.simpleName
                dispatchResult(requestCode, if (contract is ActivityResultContracts.OpenDocument) picked else saved)
            }
        }
    }

    @Composable
    fun Content() {
        val context = LocalContext.current
        val res = context.resources
        inputModes = LocalInputModeManager.current
        val prefs = remember { NagmedarPreferences(context.getSharedPreferences("nagmedar-e2e", Context.MODE_PRIVATE)) }
        val nav = rememberNavController().also { navController = it }
        val navigator = remember(nav) { ShellNavigator(nav) }
        val undo = rememberUndoBarState()
        val menuBar = rememberMenuBarState()
        val screenCommands = remember { ScreenCommands() }
        var paletteOpen by remember { mutableStateOf(false) }
        val appActions = remember {
            AppActions(context, prefs, navigator, undo, menuBar, screenCommands, DropTargets(), QueuePanelState(), {}, { paletteOpen = true }, {})
        }.also { actions = it }
        // the activity's goBack: within the app, never out of it
        val keys = remember { WindowKeys({ appActions }, { nav.previousBackStackEntry != null && nav.popBackStack() }) }
        val window = remember { FocusRequester() }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickers, LocalScreenCommands provides screenCommands) {
            // the window: it sees every key first, as the activity's dispatchKeyEvent, and those no view used last
            Box(
                Modifier
                    .fillMaxSize()
                    .background(NagTheme.colors.background)
                    .onPreviewKeyEvent { keys.beforeViews(it.nativeKeyEvent, rtl = false) }
                    .onKeyEvent { it.type == KeyEventType.KeyDown && keys.afterViews(it.nativeKeyEvent) }
                    .focusRequester(window)
                    .focusable(),
            ) {
                NagmedarShell(
                    widthClass = WidthClass.Large,
                    sidebarCollapsed = false,
                    onSidebarCollapsedChange = {},
                    badges = emptyMap(),
                    onSearch = { appActions.perform(ShortcutId.CommandPalette) },
                    menus = { MenuBarMenus(menuSpecs(context, appActions), menuBar) },
                    player = { kind -> SamplePlayerBar(kind) },
                    navController = nav,
                    screen = { destination, _ -> SampleScreen(destination) },
                )
            }
            if (paletteOpen) {
                CommandPalette(
                    items = commandItems(res, appActions::isAvailable) { appActions.perform(it) } + screenItems(res, navigator::navigate),
                    recent = emptyList(),
                    onRun = { it.run() },
                    onDismiss = { paletteOpen = false },
                )
            }
        }
        LaunchedEffect(Unit) { window.requestFocus() }
    }
}
