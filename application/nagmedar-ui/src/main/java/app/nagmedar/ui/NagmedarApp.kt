package app.nagmedar.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.cast.CastPickerDialog
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.MenuBarState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.LocalTooltipDelay
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.components.TrackClickModifiers
import app.nagmedar.ui.components.UndoBarHost
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.rememberMenuBarState
import app.nagmedar.ui.components.rememberUndoBarState
import app.nagmedar.ui.input.ShortcutGroup
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.input.menuBarSpecs
import app.nagmedar.ui.input.rememberInputModeState
import app.nagmedar.ui.input.trackInputMode
import app.nagmedar.ui.library.HomeScreen
import app.nagmedar.ui.library.LibraryAccess
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.MoviesScreen
import app.nagmedar.ui.library.SeriesScreen
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.lyrics.LyricsSession
import app.nagmedar.ui.lyrics.LyricsTrack
import app.nagmedar.ui.lyrics.PlayingLyricsFullScreen
import app.nagmedar.ui.lyrics.delayNumber
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.music.MusicNowPlaying
import app.nagmedar.ui.music.MusicScreen
import app.nagmedar.ui.music.PhonePlayerHost
import app.nagmedar.ui.music.PlaylistsScreen
import app.nagmedar.ui.network.LibVlcDialogHost
import app.nagmedar.ui.network.NetworkScreen
import app.nagmedar.ui.onboarding.FirstRunHost
import app.nagmedar.ui.onboarding.rememberAccessController
import app.nagmedar.ui.onboarding.scanSources
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.PlayerBar
import app.nagmedar.ui.playback.QueuePanelState
import app.nagmedar.ui.playback.QueueSheet
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlayQueue
import app.nagmedar.ui.playback.rememberQueuePanelState
import app.nagmedar.ui.radio.RadioScreen
import app.nagmedar.ui.podcast.Chapters
import app.nagmedar.ui.podcast.PodcastCenter
import app.nagmedar.ui.podcast.PodcastsScreen
import app.nagmedar.ui.screens.MoreScreen
import app.nagmedar.ui.screens.PlaceholderScreen
import app.nagmedar.ui.screens.SETTINGS_SHORTCUTS_ITEM
import app.nagmedar.ui.audio.AbChoice
import app.nagmedar.ui.audio.AbTest
import app.nagmedar.ui.audio.EqualizerScreen
import app.nagmedar.ui.audio.EqualizerSide
import app.nagmedar.ui.audio.MusicEngineAccess
import app.nagmedar.ui.screens.AboutScreen
import app.nagmedar.ui.tvlab.Step
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsHost
import app.nagmedar.ui.player.subtitles.whisper.WhisperHost
import app.nagmedar.ui.tvlab.TvLabScreen
import app.nagmedar.ui.tvlab.TvLabSession
import app.nagmedar.ui.tvlab.percentLabel
import app.nagmedar.ui.screens.SettingsScreen
import app.nagmedar.ui.screens.rememberThirdParties
import app.nagmedar.ui.input.DropPlan
import app.nagmedar.ui.input.DropTargets
import app.nagmedar.ui.input.DroppedFile
import app.nagmedar.ui.input.LocalDropTargets
import app.nagmedar.ui.input.dropTarget
import app.nagmedar.ui.input.mayBePlayable
import app.nagmedar.ui.input.planDrop
import app.nagmedar.ui.search.CommandPalette
import app.nagmedar.ui.search.PaletteItem
import app.nagmedar.ui.search.SearchScreen
import app.nagmedar.ui.search.commandItems
import app.nagmedar.ui.search.mediaItems
import app.nagmedar.ui.search.screenItems
import app.nagmedar.ui.search.settingItems
import app.nagmedar.ui.shell.CaptionBar
import app.nagmedar.ui.shell.rememberCaptionBar
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.DropOverlay
import app.nagmedar.ui.shell.LocalPaneWidths
import app.nagmedar.ui.shell.NagmedarShell
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.sleep.SleepTimerDialog
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.SystemBarsFollowTheme
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.tools.NAGMEDAR_ACTION_CLASSIC
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.SlaveRepository

/**
 * Root of the new interface: theme from the preferences, density from the last input device,
 * the first-run flow until it is done (S-309), then the adaptive shell and the screens of this
 * build. [onOpenFile] starts the system file picker; [onActionsReady] hands the shortcut
 * actions to the activity, which routes key events to them. [requestedDestination] is a route
 * asked for by a launcher shortcut; [onDestinationShown] clears it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NagmedarApp(
    onOpenFile: () -> Unit,
    onActionsReady: (AppActions) -> Unit = {},
    requestedDestination: String? = null,
    onDestinationShown: () -> Unit = {},
) {
    val prefs = rememberNagmedarPreferences()
    val inputMode = rememberInputModeState()
    val mode = inputMode.effective(prefs.density)
    NagmedarTheme(themeMode = prefs.themeMode, accent = prefs.accent, inputMode = mode) {
        // in a desktop window the shell draws the caption bar (S-306); the first run stays below it
        SystemBarsFollowTheme(drawsCaption = prefs.firstRunDone)
        val caption = if (prefs.firstRunDone) rememberCaptionBar() else null
        Box(
            Modifier
                .fillMaxSize()
                .background(NagTheme.colors.background)
                // the test tags as resource ids, for UI Automator and the macrobenchmarks (S-1404)
                .semantics { testTagsAsResourceId = true }
                .trackInputMode(inputMode)
                .windowInsetsPadding(if (caption != null) WindowInsets.safeDrawing.exclude(WindowInsets.captionBar) else WindowInsets.safeDrawing),
        ) {
            if (prefs.firstRunDone) {
                // Ctrl and Shift held with a click select several items (S-405)
                TrackClickModifiers {
                    NagmedarMain(prefs, onOpenFile, onActionsReady, requestedDestination, onDestinationShown, caption)
                }
            } else {
                FirstRunHost(prefs, onDone = {})
            }
        }
    }
}

@Composable
private fun NagmedarMain(
    prefs: NagmedarPreferences,
    onOpenFile: () -> Unit,
    onActionsReady: (AppActions) -> Unit,
    requestedDestination: String?,
    onDestinationShown: () -> Unit,
    caption: CaptionBar?,
) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context.applicationContext) }
    val navController = rememberNavController()
    val navigator = remember(navController) { ShellNavigator(navController) }
    val settingsList = rememberLazyListState()
    val undo = rememberUndoBarState()
    var shortcutListRequests by remember { mutableIntStateOf(0) }
    val menuBar = rememberMenuBarState()
    var paletteOpen by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val screenCommands = remember { ScreenCommands() }
    val dropTargets = remember { DropTargets() }
    val queuePanel = rememberQueuePanelState()
    // S-1002: the phone's music player, over the shell
    var phonePlayer by rememberSaveable { mutableStateOf(false) }
    // S-1007: the sleep timer's window, from the menu bar and the palette
    var sleepTimerOpen by rememberSaveable { mutableStateOf(false) }
    // S-1004: the lyrics over the whole window (Ctrl+Shift+L, the panes' full-screen button)
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    // S-1105: the radio screen's search box asked for by Ctrl+Shift+R
    var radioSearchRequested by remember { mutableStateOf(false) }
    val actions = remember(context, prefs, navigator) {
        AppActions(
            context, prefs, navigator, undo, menuBar, screenCommands, dropTargets, queuePanel, onOpenFile,
            openPalette = { paletteOpen = true },
            showShortcutList = { shortcutListRequests++ },
            openSleepTimer = { sleepTimerOpen = true },
            toggleLyrics = { lyricsOpen = !lyricsOpen },
            openRadioSearch = { radioSearchRequested = true },
            closeLyrics = {
                val open = lyricsOpen
                lyricsOpen = false
                open
            },
        )
    }
    // media access asked for from the library starts the scan of this device's storage
    val access = rememberAccessController(prefs) { scanSources(context, listOf(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) }
    val libraryAccess = LibraryAccess(access.mediaAccess, access.folders, access::requestMedia, access::pickFolder)
    // without media access a folder is picked with the system picker; with it, the medialibrary's folders open
    val addFolder = { if (access.mediaAccess) openClassic(context, SecondaryActivity.STORAGE_BROWSER) else access.pickFolder() }
    LaunchedEffect(actions) { onActionsReady(actions) }
    val motion = NagTheme.motion
    LaunchedEffect(shortcutListRequests) {
        if (shortcutListRequests == 0) return@LaunchedEffect
        if (motion.reduced) settingsList.scrollToItem(SETTINGS_SHORTCUTS_ITEM) else settingsList.animateScrollToItem(SETTINGS_SHORTCUTS_ITEM)
    }
    LaunchedEffect(requestedDestination) {
        val destination = Destination.entries.firstOrNull { it.route == requestedDestination } ?: return@LaunchedEffect
        navigator.navigate(destination)
        onDestinationShown()
    }
    val counts by produceState<Map<Destination, String>>(emptyMap(), access.mediaAccess, access.folders) {
        val c = repository.counts()
        value = mapOf(Destination.Movies to c.videos.toString(), Destination.Music to c.tracks.toString())
    }
    // S-1202: a long analysis goes on while other screens show; the sidebar says how far it is
    val tvLab = remember(context) { TvLabSession.get(context) }
    val tvLabProgress = (tvLab.bitrateStep as? Step.Running)?.let { percentLabel(it.fraction ?: 0f, currentLocale()) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthClass = WidthClass.fromWidth(maxWidth.value)
        val nowPlaying = rememberNowPlaying()
        CompositionLocalProvider(
            LocalLibraryAccess provides libraryAccess,
            LocalPaneWidths provides prefs,
            LocalTooltipDelay provides prefs.tooltipDelay.millis,
            LocalScreenCommands provides screenCommands,
            LocalDropTargets provides dropTargets,
        ) {
            NagmedarShell(
                widthClass = widthClass,
                sidebarCollapsed = prefs.sidebarCollapsed,
                onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
                badges = if (tvLabProgress != null) counts + (Destination.TvLab to tvLabProgress) else counts,
                // the menu bar's field is the palette's; phones and tablets go to the search page (S-311)
                onSearch = {
                    if (NavKind.forWidth(widthClass) == NavKind.Sidebar) actions.perform(ShortcutId.CommandPalette) else navigator.navigate(Destination.Search)
                },
                menus = { MenuBarMenus(menuSpecs(context, actions), menuBar) },
                // media dropped on the player bar join the queue (S-406)
                player = { navKind ->
                    PlayerBar(
                        navKind,
                        nowPlaying,
                        Modifier.dropTarget("player-bar", ::mayBePlayable, RoundedCornerShape(NagTheme.dimens.radiusCard)) { files ->
                            actions.queueFiles(files)
                        },
                        queueOpen = queuePanel.open,
                        onQueue = queuePanel::toggle,
                        // music opens here: on a desktop window the music view with its now playing pane
                        onOpenAudio = {
                            if (navKind == NavKind.Sidebar) {
                                navigator.navigate(Destination.Music)
                            } else {
                                phonePlayer = true
                            }
                        },
                    )
                },
                navController = navController,
                caption = caption,
                overlay = {
                    if (sleepTimerOpen) SleepTimerDialog { sleepTimerOpen = false }
                    // S-1302: the device picker, from the cast buttons, Ctrl+Shift+C and the menus' "Play on …"
                    Casting.request?.let { request -> CastPickerDialog(request, Casting::closePicker) }
                    if (phonePlayer && NavKind.forWidth(widthClass) != NavKind.Sidebar) {
                        PhonePlayerHost(onClose = { phonePlayer = false }, onQueue = queuePanel::toggle, onFullScreenLyrics = { lyricsOpen = true })
                    }
                    if (lyricsOpen) PlayingLyricsFullScreen(onClose = { lyricsOpen = false })
                    // S-708: the subtitle tools, asked for from a film's menu
                    SubtitleToolsHost()
                    // S-709: subtitles from a film's speech, asked for from its menu, and what came of the job
                    WhisperHost(Modifier.align(Alignment.BottomEnd).padding(16.dp))
                    // "Sıradaki" beside the screen, over it on a phone (S-310)
                    if (queuePanel.open) {
                        val queue = rememberPlayQueue()
                        QueueSheet(
                            queue.entries,
                            queue.current,
                            queue,
                            compact = NavKind.forWidth(widthClass) == NavKind.BottomBar,
                            onClose = { queuePanel.close() },
                            modifier = Modifier.align(Alignment.TopEnd),
                            undo = undo,
                        )
                    }
                    UndoBarHost(undo, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    if (dropTargets.incoming && dropTargets.hovered == null) DropOverlay()
                },
                screen = { destination, shellNavigator ->
                    when (destination) {
                        Destination.Home -> HomeScreen(repository, onAddFolder = addFolder, onBrowse = shellNavigator::navigate)
                        Destination.Movies -> MoviesScreen(repository, onAddFolder = addFolder)
                        Destination.Series -> SeriesScreen(repository, onAddFolder = addFolder)
                        // S-803, S-1001: on a desktop window the now playing pane, with the waveform, beside the table
                        Destination.Music -> MusicScreen(repository, onAddFolder = addFolder, nowPlaying = { MusicNowPlaying(onFullScreenLyrics = { lyricsOpen = true }) })
                        // S-804: the medialibrary's playlists, a tab each
                        Destination.Playlists -> PlaylistsScreen(repository, undo = undo, onBrowseMusic = { shellNavigator.navigate(Destination.Music) })
                        // S-1105: the radio directory's search
                        Destination.Radio -> RadioScreen(searchRequested = radioSearchRequested, onSearchHandled = { radioSearchRequested = false }, undo = undo)
                        // S-1101, S-1102: subscriptions, the search of Podcast Index and iTunes, OPML
                        Destination.Podcasts -> PodcastsScreen(undo = undo)
                        Destination.Search -> SearchScreen(repository, searchQuery) { searchQuery = it }
                        Destination.About -> AboutScreen(rememberThirdParties(), onOpenLink = { url -> openLink(context, url) })
                        Destination.TvLab -> TvLabScreen(tvLab)
                        // S-1301: SMB, FTP, SFTP, NFS, UPnP/DLNA and WebDAV through libVLC
                        Destination.Network -> NetworkScreen(undo)
                        Destination.Settings -> SettingsScreen(
                            prefs = prefs,
                            onOpenClassicSettings = { context.startActivity(Intent(context, PreferencesActivity::class.java)) },
                            onOpenAbout = { shellNavigator.navigate(Destination.About) },
                            onShowFirstRun = { prefs.updateFirstRunDone(false) },
                            listState = settingsList,
                            undo = undo,
                            onOpenEqualizer = { shellNavigator.navigate(Destination.Equalizer) },
                        )
                        // S-906, with S-910's signal path and S-912's A/B test beside the chain
                        Destination.Equalizer -> EqualizerScreen(side = { EqualizerSide() })
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
    // S-1301: libVLC's sign-ins, questions and progress that no page answers, while this window is in front
    LibVlcDialogHost()
    if (paletteOpen) {
        // the library's media come when the palette opens; commands, screens and settings at once
        val media by produceState(emptyList<PaletteItem>()) {
            value = mediaItems(context.resources, repository.videos() + repository.tracks()) { item ->
                if (item.type == MediaWrapper.TYPE_VIDEO) PlaybackActions.playVideo(context, item) else PlaybackActions.playList(context, listOf(item), 0)
            }
        }
        val wallpaperAccent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val items = remember(media, actions) {
            commandItems(context.resources, actions::isAvailable, { actions.perform(it) }) +
                screenItems(context.resources, navigator::navigate) +
                settingItems(context.resources, prefs, wallpaperAccent) +
                media
        }
        CommandPalette(
            items = items,
            recent = prefs.recentCommands,
            onRun = { item ->
                prefs.rememberCommand(item.id)
                item.run()
            },
            onDismiss = { paletteOpen = false },
            onSearchAll = { query ->
                searchQuery = query
                navigator.navigate(Destination.Search)
            },
        )
    }
}

/** A web page in the browser (S-209: the source code, the licences); nothing when there is none. */
fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * Opens the classic interface, on a given screen of SecondaryActivity when [fragment] is set.
 * StartActivity is asked for the classic interface explicitly: the launcher's default is this one.
 */
fun openClassic(context: Context, fragment: String?) {
    val intent = if (fragment == null) {
        Intent(context, StartActivity::class.java).setAction(NAGMEDAR_ACTION_CLASSIC)
    } else {
        Intent(context, SecondaryActivity::class.java).putExtra(SecondaryActivity.KEY_FRAGMENT, fragment)
    }
    context.startActivity(intent)
}

/** Menu bar contents, built from the shortcut table so labels and keys match everywhere (S-302). */
internal fun menuSpecs(context: Context, actions: AppActions): List<MenuSpec> =
    menuBarSpecs(context.resources, { actions.perform(it) }, actions::isAvailable) { group ->
        if (group == ShortcutGroup.Tools) {
            listOf(MenuEntry.Separator, MenuEntry.Item(context.getString(R.string.nag_classic_interface), { openClassic(context, null) }))
        } else {
            emptyList()
        }
    }

/** What each shortcut does in the shell; player-only shortcuts are handled by the player. */
class AppActions(
    private val context: Context,
    private val prefs: NagmedarPreferences,
    private val navigator: ShellNavigator,
    private val undo: UndoBarState,
    private val menuBar: MenuBarState,
    private val screenCommands: ScreenCommands,
    /** Where a drag over the window is, and what takes the files (S-406). */
    val dropTargets: DropTargets,
    private val queuePanel: QueuePanelState,
    private val openFile: () -> Unit,
    private val openPalette: () -> Unit,
    private val showShortcutList: () -> Unit,
    /** S-1007: the sleep timer's window. */
    private val openSleepTimer: () -> Unit = {},
    /** S-1004: the lyrics over the whole window, opened or closed; closed, and whether they were open. */
    private val toggleLyrics: () -> Unit = {},
    private val closeLyrics: () -> Boolean = { false },
    /** S-1105: the radio screen's search box takes the keyboard. */
    private val openRadioSearch: () -> Unit = {},
) {
    private var volumeBeforeMute = 100

    /** Alt or F10 (S-302): the keyboard goes to the menu bar, or back from it; false without a menu bar. */
    fun toggleMenuBar(): Boolean = menuBar.onAlt()

    /** The window's keys while the menu bar has the keyboard. */
    fun menuBarKey(keyCode: Int, down: Boolean, rtl: Boolean): Boolean = menuBar.onKey(keyCode, down, rtl)

    /**
     * Files dropped on the window and on no target (S-406): media play in the order dropped,
     * subtitles join the video playing (and are kept with it), else a word on what to do.
     */
    fun dropFiles(files: List<DroppedFile>): Boolean {
        val service = PlaybackService.instance
        val video = service?.currentMediaWrapper?.takeIf { service.isVideoPlaying }
        val res = context.resources
        val message = when (val plan = planDrop(files, video != null)) {
            is DropPlan.Play -> {
                MediaUtils.openList(context, plan.uris.map { MLServiceLocator.getAbstractMediaWrapper(Uri.parse(it)) }, 0)
                res.getQuantityString(R.plurals.nag_drop_playing, plan.uris.size, plan.uris.size)
            }
            is DropPlan.LoadSubtitles -> {
                plan.uris.forEach { uri ->
                    service?.addSubtitleTrack(Uri.parse(uri), true)
                    if (video != null) SlaveRepository.getInstance(context).saveSlave(video.location, IMedia.Slave.Type.Subtitle, 2, uri)
                }
                res.getQuantityString(R.plurals.nag_drop_subtitles_loaded, plan.uris.size, plan.uris.size)
            }
            DropPlan.SubtitlesNeedAVideo -> res.getString(R.string.nag_drop_subtitle_needs_video)
            DropPlan.Nothing -> res.getString(R.string.nag_drop_nothing)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return true
    }

    /** Media dropped on the player bar (S-406): after what plays; false when none would play. */
    fun queueFiles(files: List<DroppedFile>): Boolean {
        val media = files.filter { it.playable }
        if (media.isEmpty()) return false
        MediaUtils.appendMedia(context, media.map { MLServiceLocator.getAbstractMediaWrapper(Uri.parse(it.uri)) })
        Toast.makeText(context, context.resources.getQuantityString(R.plurals.nag_drop_queued, media.size, media.size), Toast.LENGTH_SHORT).show()
        return true
    }

    /** The route on screen, before a back (S-408). */
    fun currentRoute(): String? = navigator.currentRoute

    /** A back left [from]: the forward button can return there. */
    fun wentBack(from: String?) = navigator.wentBack(from)

    /** The mouse's forward button (S-408). */
    fun forward(): Boolean = navigator.forward()

    /** An Esc nothing else used: "Sıradaki" closes (S-310), else the screen lets go of its selection (S-405). */
    fun escape(): Boolean = closeLyrics() || queuePanel.close() || screenCommands.escape()

    fun leaveMenuBar() {
        if (menuBar.hasKeyboard) menuBar.leave()
    }

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
            ShortcutId.CommandPalette -> openPalette()
            ShortcutId.OpenNetwork -> openClassic(context, SecondaryActivity.STREAMS)
            // S-307: another window of the library, on this screen
            ShortcutId.OpenInNewWindow -> {
                val split = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (context as? Activity)?.isInMultiWindowMode == true
                context.startActivity(newWindowIntent(context, navigator.currentRoute, adjacent = split))
            }
            ShortcutId.Undo -> return undo.undoLatest()
            ShortcutId.SelectAll -> return screenCommands.selectAll()
            // S-1301: the network browser's folders
            ShortcutId.FolderUp -> return screenCommands.up()
            ShortcutId.Refresh -> return screenCommands.refresh()
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
            ShortcutId.SleepTimer -> openSleepTimer()
            ShortcutId.Cast -> Casting.openPicker()
            ShortcutId.Lyrics -> toggleLyrics()
            ShortcutId.LyricsEarlier -> shiftLyrics(-LyricsSession.STEP_MS)
            ShortcutId.LyricsLater -> shiftLyrics(LyricsSession.STEP_MS)
            ShortcutId.Settings -> navigator.navigate(Destination.Settings)
            // S-906: in the library the music engine's equaliser; the video player keeps libVLC's
            ShortcutId.Equalizer -> navigator.navigate(Destination.Equalizer)
            // S-912: from anywhere, while the engine plays through a chain that changes the sound
            ShortcutId.AbSwitch -> {
                val path = MusicEngineAccess.signalPath
                val message = if (path == null || !path.dspActive) {
                    R.string.nag_ab_unavailable
                } else {
                    AbTest.state.next()
                    when (AbTest.state.choice) {
                        AbChoice.A -> R.string.nag_ab_now_a
                        AbChoice.B -> R.string.nag_ab_now_b
                        AbChoice.X -> R.string.nag_ab_now_x
                    }
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            ShortcutId.TvLab -> navigator.navigate(Destination.TvLab)
            ShortcutId.RadioSearch -> {
                navigator.navigate(Destination.Radio)
                openRadioSearch()
            }
            // S-1101: the search field of Podcasts has the keyboard
            ShortcutId.PodcastSearch -> {
                navigator.navigate(Destination.Podcasts)
                PodcastCenter.requestSearchFocus()
            }
            // S-1104: the chapters of the podcast episode that plays
            ShortcutId.PodcastNextChapter -> stepPodcastChapter(forward = true)
            ShortcutId.PodcastPreviousChapter -> stepPodcastChapter(forward = false)
            ShortcutId.ToggleSidebar -> prefs.updateSidebarCollapsed(!prefs.sidebarCollapsed)
            ShortcutId.PlayQueue -> queuePanel.toggle()
            ShortcutId.ShortcutList -> {
                navigator.navigate(Destination.Settings)
                showShortcutList()
            }
            else -> return false
        }
        return true
    }

    /** S-1104: to the next (or the previous) chapter of the podcast episode that plays; a toast says when there are none. */
    private fun stepPodcastChapter(forward: Boolean) {
        val chapters = PodcastCenter.playing.value?.chapters.orEmpty()
        val target = PlaybackService.instance?.let { Chapters.step(chapters, it.getTime(), forward) }
        if (target == null) {
            Toast.makeText(context, R.string.nag_pod_chapter_none, Toast.LENGTH_SHORT).show()
        } else {
            PlaybackActions.seekToMs(chapters[target].startMs)
        }
    }

    /** S-1004: the playing track's lyrics [byMs] later (earlier when negative), said in a toast. */
    private fun shiftLyrics(byMs: Long) {
        val media = PlaybackService.instance?.currentMediaWrapper?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }
        val track = media?.let(LyricsTrack::of)
        val session = LyricsSession.get(context)
        // only the lyrics of what plays, as a view of them has them
        val delay = if (track != null && session.load.track == track) session.shift(byMs) else null
        val message = if (delay == null) {
            context.getString(R.string.nag_lyrics_delay_none)
        } else {
            context.getString(R.string.nag_lyrics_delay_now, context.getString(R.string.nag_lyrics_delay_value, delayNumber(delay, context.resources.locale())))
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun seekBy(deltaMs: Long) {
        val service = PlaybackService.instance ?: return
        service.seek((service.getTime() + deltaMs).coerceAtLeast(0L), fromUser = true)
    }

    private companion object {
        /** Global shortcuts whose features arrive in later MVPs; they show a notice instead. */
        val comingLater = emptySet<ShortcutId>()
    }
}
