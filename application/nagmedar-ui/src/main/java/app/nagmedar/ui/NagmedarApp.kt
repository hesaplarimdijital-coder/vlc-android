package app.nagmedar.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.components.UndoBarHost
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.rememberUndoBarState
import app.nagmedar.ui.input.ShortcutGroup
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.input.rememberInputModeState
import app.nagmedar.ui.input.trackInputMode
import app.nagmedar.ui.library.HomeScreen
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.MoviesScreen
import app.nagmedar.ui.library.MusicScreen
import app.nagmedar.ui.playback.MiniPlayer
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.TransportBar
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.screens.MoreScreen
import app.nagmedar.ui.screens.PlaceholderScreen
import app.nagmedar.ui.screens.SETTINGS_SHORTCUTS_ITEM
import app.nagmedar.ui.screens.SettingsScreen
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.NagmedarShell
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.LibrariesActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.preferences.PreferencesActivity

/**
 * Root of the new interface: theme from the preferences, density from the last input device,
 * the adaptive shell and the screens of this build. [onOpenFile] starts the system file picker;
 * [onActionsReady] hands the shortcut actions to the activity, which routes key events to them.
 */
@Composable
fun NagmedarApp(onOpenFile: () -> Unit, onActionsReady: (AppActions) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = rememberNagmedarPreferences()
    val inputMode = rememberInputModeState()
    val mode = inputMode.effective(prefs.density)
    val repository = remember { MediaRepository(context.applicationContext) }
    val navController = rememberNavController()
    val navigator = remember(navController) { ShellNavigator(navController) }
    val settingsList = rememberLazyListState()
    val undo = rememberUndoBarState()
    var shortcutListRequests by remember { mutableIntStateOf(0) }
    val actions = remember(context, prefs, navigator) {
        AppActions(context, prefs, navigator, undo, onOpenFile) { shortcutListRequests++ }
    }
    val addFolder = { openClassic(context, SecondaryActivity.STORAGE_BROWSER) }
    LaunchedEffect(actions) { onActionsReady(actions) }
    LaunchedEffect(shortcutListRequests) {
        if (shortcutListRequests > 0) settingsList.animateScrollToItem(SETTINGS_SHORTCUTS_ITEM)
    }
    val counts by produceState<Map<Destination, String>>(emptyMap()) {
        val c = repository.counts()
        value = mapOf(Destination.Movies to c.videos.toString(), Destination.Music to c.tracks.toString())
    }

    NagmedarTheme(themeMode = prefs.themeMode, accent = prefs.accent, inputMode = mode) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(NagTheme.colors.background)
                .trackInputMode(inputMode)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val widthClass = WidthClass.fromWidth(maxWidth.value)
            val nowPlaying = rememberNowPlaying()
            NagmedarShell(
                widthClass = widthClass,
                sidebarCollapsed = prefs.sidebarCollapsed,
                onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
                badges = counts,
                onSearch = { actions.perform(ShortcutId.CommandPalette) },
                menus = { MenuBarMenus(menuSpecs(context, actions)) },
                player = { navKind ->
                    if (navKind == NavKind.Sidebar) TransportBar(nowPlaying) else MiniPlayer(nowPlaying)
                },
                navController = navController,
                overlay = {
                    UndoBarHost(undo, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                },
                screen = { destination, shellNavigator ->
                    when (destination) {
                        Destination.Home -> HomeScreen(repository, onAddFolder = addFolder, onBrowse = shellNavigator::navigate)
                        Destination.Movies -> MoviesScreen(repository, onAddFolder = addFolder)
                        Destination.Music -> MusicScreen(repository, onAddFolder = addFolder)
                        Destination.Settings -> SettingsScreen(
                            prefs = prefs,
                            onOpenClassicSettings = { context.startActivity(Intent(context, PreferencesActivity::class.java)) },
                            onOpenLicences = { context.startActivity(Intent(context, LibrariesActivity::class.java)) },
                            listState = settingsList,
                        )
                        Destination.More -> MoreScreen(
                            destinations = Destination.overflow(
                                when (NavKind.forWidth(widthClass)) {
                                    NavKind.BottomBar -> Destination.bottomBar
                                    NavKind.Rail -> Destination.rail + Destination.railFooter
                                    NavKind.Sidebar -> Destination.sidebar
                                },
                            ),
                            onSelect = shellNavigator::navigate,
                        )
                        Destination.Streams -> PlaceholderScreen(destination) { openClassic(context, SecondaryActivity.STREAMS) }
                        Destination.Device -> PlaceholderScreen(destination) { openClassic(context, SecondaryActivity.FILE_BROWSER) }
                        else -> PlaceholderScreen(destination) { openClassic(context, null) }
                    }
                },
            )
        }
    }
}

/** Opens the classic interface, on a given screen of SecondaryActivity when [fragment] is set. */
fun openClassic(context: Context, fragment: String?) {
    val intent = if (fragment == null) {
        Intent(context, StartActivity::class.java)
    } else {
        Intent(context, SecondaryActivity::class.java).putExtra(SecondaryActivity.KEY_FRAGMENT, fragment)
    }
    context.startActivity(intent)
}

/** Menu bar contents, built from the shortcut table so labels and keys match everywhere. */
private fun menuSpecs(context: Context, actions: AppActions): List<MenuSpec> {
    val res = context.resources
    return ShortcutGroup.entries.map { group ->
        MenuSpec(res.getString(group.title)) {
            ShortcutRegistry.defaults.filter { it.group == group }.map { shortcut ->
                MenuEntry.Item(
                    label = res.getString(shortcut.label),
                    onClick = { actions.perform(shortcut.id) },
                    shortcut = shortcut.chord.label(res),
                    enabled = actions.isAvailable(shortcut.id),
                )
            } + if (group == ShortcutGroup.Tools) listOf(
                MenuEntry.Separator,
                MenuEntry.Item(res.getString(R.string.nag_classic_interface), { openClassic(context, null) }),
            ) else emptyList()
        }
    }
}

/** What each shortcut does in the shell; player-only shortcuts are handled by the player. */
class AppActions(
    private val context: Context,
    private val prefs: NagmedarPreferences,
    private val navigator: ShellNavigator,
    private val undo: UndoBarState,
    private val openFile: () -> Unit,
    private val showShortcutList: () -> Unit,
) {
    private var volumeBeforeMute = 100

    fun isAvailable(id: ShortcutId): Boolean = ShortcutRegistry[id].scope == ShortcutScope.Global && id !in comingLater

    fun perform(id: ShortcutId): Boolean {
        if (!isAvailable(id)) {
            if (ShortcutRegistry[id].scope == ShortcutScope.Global) {
                Toast.makeText(context, R.string.nag_unavailable_mvp, Toast.LENGTH_SHORT).show()
                return true
            }
            return false
        }
        val service = PlaybackService.instance
        when (id) {
            ShortcutId.OpenFile -> openFile()
            ShortcutId.OpenNetwork -> openClassic(context, SecondaryActivity.STREAMS)
            ShortcutId.Undo -> return undo.undoLatest()
            ShortcutId.PlayPause -> PlaybackActions.togglePlayPause()
            ShortcutId.Stop -> service?.stop()
            ShortcutId.Previous -> PlaybackActions.previous()
            ShortcutId.Next -> PlaybackActions.next()
            ShortcutId.JumpBackShort -> seekBy(-3_000)
            ShortcutId.JumpForwardShort -> seekBy(3_000)
            ShortcutId.JumpBack -> seekBy(-10_000)
            ShortcutId.JumpForward -> seekBy(10_000)
            ShortcutId.JumpBackLong -> seekBy(-60_000)
            ShortcutId.JumpForwardLong -> seekBy(60_000)
            ShortcutId.Slower -> service?.let { it.setRate((it.rate - 0.25f).coerceAtLeast(0.25f), true) }
            ShortcutId.Faster -> service?.let { it.setRate((it.rate + 0.25f).coerceAtMost(4f), true) }
            ShortcutId.NormalSpeed -> service?.setRate(1f, true)
            ShortcutId.Repeat -> service?.let {
                it.repeatType = when (it.repeatType) {
                    android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL
                    android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE
                    else -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE
                }
            }
            ShortcutId.Shuffle -> service?.shuffle()
            ShortcutId.VolumeUp -> service?.let { it.setVolume((it.volume + 5).coerceAtMost(200)) }
            ShortcutId.VolumeDown -> service?.let { it.setVolume((it.volume - 5).coerceAtLeast(0)) }
            ShortcutId.Mute -> service?.let {
                if (it.volume > 0) {
                    volumeBeforeMute = it.volume
                    it.setVolume(0)
                } else {
                    it.setVolume(volumeBeforeMute)
                }
            }
            ShortcutId.Settings -> navigator.navigate(Destination.Settings)
            ShortcutId.ToggleSidebar -> prefs.updateSidebarCollapsed(!prefs.sidebarCollapsed)
            ShortcutId.PlayQueue -> PlaybackActions.openPlayer(context)
            ShortcutId.ShortcutList -> {
                navigator.navigate(Destination.Settings)
                showShortcutList()
            }
            else -> return false
        }
        return true
    }

    private fun seekBy(deltaMs: Long) {
        val service = PlaybackService.instance ?: return
        service.seek((service.getTime() + deltaMs).coerceAtLeast(0L), fromUser = true)
    }

    private companion object {
        /** Global shortcuts whose features arrive in later MVPs; they show a notice instead. */
        val comingLater = setOf(ShortcutId.CommandPalette, ShortcutId.OpenInNewWindow, ShortcutId.MediaInfo, ShortcutId.Effects)
    }
}
