package app.nagmedar.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.cast.CastPickerDialog
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.MenuBarState
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoticeHost
import app.nagmedar.ui.components.PlayerValue
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.LocalTooltipDelay
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.components.TrackClickModifiers
import app.nagmedar.ui.components.UndoBarHost
import app.nagmedar.ui.components.blockInputBelow
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.rememberExpandablePlayerState
import app.nagmedar.ui.components.rememberMenuBarState
import app.nagmedar.ui.components.rememberUndoBarState
import app.nagmedar.ui.device.DeviceRequests
import app.nagmedar.ui.device.DeviceScreen
import app.nagmedar.ui.downloads.DownloadsScreen
import app.nagmedar.ui.guide.GuideScreen
import app.nagmedar.ui.guide.ShortcutCardScreen
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.input.menuBarSpecs
import app.nagmedar.ui.input.rememberInputModeState
import app.nagmedar.ui.input.trackInputMode
import app.nagmedar.ui.library.LibraryAccess
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.MediaInfoHost
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.SectionSourcesDialog
import app.nagmedar.ui.lyrics.LyricsSession
import app.nagmedar.ui.lyrics.LyricsTrack
import app.nagmedar.ui.lyrics.PlayingLyricsFullScreen
import app.nagmedar.ui.lyrics.editor.LyricsEditorRequest
import app.nagmedar.ui.lyrics.editor.PlayingLyricsEditor
import app.nagmedar.ui.lyrics.delayNumber
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.music.MusicLinks
import app.nagmedar.ui.music.rememberMusicInspector
import app.nagmedar.ui.music.MusicScreen
import app.nagmedar.ui.music.PhonePlayerHost
import app.nagmedar.ui.music.PlaylistsScreen
import app.nagmedar.ui.music.WindowPlayer
import app.nagmedar.ui.network.LibVlcDialogHost
import app.nagmedar.ui.network.NetworkScreen
import app.nagmedar.ui.onboarding.FirstRunHost
import app.nagmedar.ui.onboarding.rememberAccessController
import app.nagmedar.ui.onboarding.scanSources
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.PlayerBar
import app.nagmedar.ui.playback.QueuePage
import app.nagmedar.ui.playback.QueuePanelState
import app.nagmedar.ui.playback.QueueSheet
import app.nagmedar.ui.playback.ResumePromptHost
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlayQueue
import app.nagmedar.ui.playback.rememberQueuePanelState
import app.nagmedar.ui.radio.RadioCenter
import app.nagmedar.ui.remoteaccess.RemoteAccessHost
import app.nagmedar.ui.radio.RadioScreen
import app.nagmedar.ui.podcast.ChapterStep
import app.nagmedar.ui.podcast.ChapterSteps
import app.nagmedar.ui.podcast.PodcastCenter
import app.nagmedar.ui.podcast.PodcastsScreen
import app.nagmedar.ui.radio.record.RadioRecordings
import app.nagmedar.ui.radio.record.toRecording
import app.nagmedar.ui.screens.MoreAction
import app.nagmedar.ui.screens.MoreActionGroup
import app.nagmedar.ui.screens.MoreScreen
import app.nagmedar.ui.screens.SettingsItem
import app.nagmedar.ui.screens.SettingsReveal
import app.nagmedar.ui.screens.scrollToSetting
import app.nagmedar.ui.screens.settingsItem
import app.nagmedar.ui.audio.AbChoice
import app.nagmedar.ui.audio.AbTest
import app.nagmedar.ui.audio.EqualizerScreen
import app.nagmedar.ui.audio.EqualizerSide
import app.nagmedar.ui.audio.MusicEngineAccess
import app.nagmedar.ui.screens.AboutScreen
import app.nagmedar.ui.screens.SettingsScreen
import app.nagmedar.ui.prefs.AllSettingsScreen
import app.nagmedar.ui.prefs.NagmedarLink
import app.nagmedar.ui.prefs.preferencePaletteItems
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
import app.nagmedar.ui.shell.NavRules
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.ShellRequests
import app.nagmedar.ui.shell.UnderShellBars
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.streams.StreamsCenter
import app.nagmedar.ui.streams.StreamsScreen
import app.nagmedar.ui.sleep.SleepTimerDialog
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.SystemBarsFollowTheme
import app.nagmedar.ui.visual.PlayingVisualizerFullScreen
import app.nagmedar.ui.widget.PlayerWidgetMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.tools.NAGMEDAR_DESTINATION_NOW_PLAYING
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.MediaUtils
import org.videolan.tools.Notices

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
    // S-1002, 1.0.2: the big music player over a tablet's and a desktop's window (the rail's and the sidebar's layouts)
    var windowPlayer by rememberSaveable { mutableStateOf(false) }
    // Desktop Kit 1.0: on a phone the mini player grows into it
    val expandingPlayer = rememberExpandablePlayerState()
    val scope = rememberCoroutineScope()
    // S-1007: the sleep timer's window, from the menu bar and the palette
    var sleepTimerOpen by rememberSaveable { mutableStateOf(false) }
    // S-1004: the lyrics over the whole window (Ctrl+Shift+L, the panes' full-screen button)
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    // S-611: the visualiser over the whole window (Ctrl+Shift+V, the panes' full-screen button)
    var visualizerOpen by rememberSaveable { mutableStateOf(false) }
    // S-1105: the radio screen's search box asked for by Ctrl+Shift+R
    var radioSearchRequested by remember { mutableStateOf(false) }
    // S-207: the radio screen's Recordings tab asked for by Alt+Shift+R
    var radioRecordingsRequested by remember { mutableStateOf(false) }
    // the folders of a section, asked for from its header or its empty state
    var sourcesFor by rememberSaveable { mutableStateOf<LibrarySection?>(null) }
    val actions = remember(context, prefs, navigator) {
        AppActions(
            context, prefs, navigator, undo, menuBar, screenCommands, dropTargets, queuePanel, onOpenFile,
            openPalette = { paletteOpen = true },
            showShortcutList = { shortcutListRequests++ },
            openSleepTimer = { sleepTimerOpen = true },
            toggleLyrics = { lyricsOpen = !lyricsOpen },
            toggleVisualizer = { visualizerOpen = !visualizerOpen },
            openRadioSearch = { radioSearchRequested = true },
            openRadioRecordings = { radioRecordingsRequested = true },
            closeLyrics = {
                val open = lyricsOpen
                lyricsOpen = false
                open
            },
            closeVisualizer = {
                val open = visualizerOpen
                visualizerOpen = false
                open
            },
            // 1.0.2: Esc closes the big player (and the phone's, opened with a keyboard)
            closePlayer = {
                when {
                    windowPlayer -> {
                        windowPlayer = false
                        true
                    }
                    expandingPlayer.currentValue == PlayerValue.Expanded -> {
                        scope.launch { expandingPlayer.collapse() }
                        true
                    }
                    else -> false
                }
            },
        )
    }
    // media access asked for from the library starts the scan of this device's storage
    val access = rememberAccessController(prefs) { scanSources(context, listOf(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) }
    // the sections' picked trees count as picked folders; a change of any section's folders reloads the screens
    val sources = prefs.librarySources
    val libraryAccess = LibraryAccess(access.mediaAccess, access.folders + sources.trees, access::requestMedia, access::pickFolder, sources)
    // "Klasör ekle" for every section's folders: Settings › Library
    var libraryListRequests by remember { mutableIntStateOf(0) }
    val addFolder: () -> Unit = {
        navigator.navigate(Destination.Settings)
        libraryListRequests++
    }
    LaunchedEffect(actions) { onActionsReady(actions) }
    val motion = NagTheme.motion
    // 1.0.1: a section asked for is shown even where Settings would leave it out (the shortcuts on a phone)
    val settingsReveal = remember { SettingsReveal() }
    LaunchedEffect(shortcutListRequests) {
        if (shortcutListRequests == 0) return@LaunchedEffect
        settingsList.scrollToSetting(SettingsItem.Shortcuts, settingsReveal, motion.reduced)
    }
    LaunchedEffect(libraryListRequests) {
        if (libraryListRequests == 0) return@LaunchedEffect
        settingsList.scrollToSetting(SettingsItem.Library, settingsReveal, motion.reduced)
    }
    // "All settings"' lines that Nağmedar's own sections have: Settings, at that section
    var settingsTarget by remember { mutableStateOf(SettingsItem.Header) }
    var settingsItemRequests by remember { mutableIntStateOf(0) }
    val openSettingsAt: (SettingsItem) -> Unit = { target ->
        navigator.navigate(Destination.Settings)
        settingsTarget = target
        settingsItemRequests++
    }
    LaunchedEffect(settingsItemRequests) {
        if (settingsItemRequests == 0) return@LaunchedEffect
        settingsList.scrollToSetting(settingsTarget, settingsReveal, motion.reduced)
    }
    // a preference asked for from outside (the player's metered connection question): its page in "All settings"
    LaunchedEffect(ShellRequests.preference) {
        if (ShellRequests.preference != null) navigator.navigate(Destination.AllSettings)
    }
    // the playback notification and the players outside the shell ask for what plays now
    var nowPlayingRequested by remember { mutableStateOf(false) }
    // the system's search asks for the search page with its words
    LaunchedEffect(ShellRequests.search) {
        val query = ShellRequests.search ?: return@LaunchedEffect
        searchQuery = query
        navigator.open(Destination.Search)
        ShellRequests.search = null
    }
    // VLC's widgets are gone: once, a word on Nağmedar's
    LaunchedEffect(Unit) { PlayerWidgetMigration.noticeIfNeeded(context) }
    // a new drive's notice and notification ask for the library's folders
    LaunchedEffect(ShellRequests.librarySources) {
        if (!ShellRequests.librarySources) return@LaunchedEffect
        addFolder()
        ShellRequests.librarySources = false
    }
    // a media's "Dosyada göster": This device opens at its folder (and takes the request)
    val deviceReveal = DeviceRequests.reveal
    LaunchedEffect(deviceReveal) {
        if (deviceReveal != null) navigator.navigate(Destination.Device)
    }
    LaunchedEffect(requestedDestination) {
        if (requestedDestination == NAGMEDAR_DESTINATION_NOW_PLAYING) {
            nowPlayingRequested = true
            onDestinationShown()
            return@LaunchedEffect
        }
        val destination = Destination.entries.firstOrNull { it.route == requestedDestination } ?: return@LaunchedEffect
        navigator.navigate(destination)
        onDestinationShown()
    }
    // what each section shows by its folders (Settings › Library)
    val counts by produceState<Map<Destination, String>>(emptyMap(), access.mediaAccess, access.folders, sources) {
        val c = repository.sectionCounts()
        value = mapOf(Destination.Music to c.tracks.toString())
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthClass = WidthClass.fromWidth(maxWidth.value)
        val nowPlaying = rememberNowPlaying()
        val openNowPlaying: (NavKind) -> Unit = { navKind ->
            val page = PlaybackActions.nowPlayingDestination()
            if (page == Destination.Music) {
                // on a phone the mini player grown; 1.0.2: on a tablet and a desktop window the big player over the window
                when (navKind) {
                    NavKind.BottomBar -> scope.launch { expandingPlayer.expand() }
                    NavKind.Rail, NavKind.Sidebar -> windowPlayer = true
                }
            } else if (page != null) {
                navigator.navigate(page)
            }
        }
        val closePlayer: () -> Unit = {
            windowPlayer = false
            scope.launch { expandingPlayer.collapse() }
        }
        // a player's artist or album link: the music view takes it (the phone's player closes for it)
        val musicLink = MusicLinks.pending
        LaunchedEffect(musicLink) {
            if (musicLink == null) return@LaunchedEffect
            closePlayer()
            navigator.navigate(Destination.Music)
        }
        // nothing plays any more (stopped from the notification, the queue's end): the phone's player goes back to the
        // strip, so that the next track starts on the mini player with the bottom bar shown, not expanded at once
        val nothingPlays = nowPlaying.media == null
        LaunchedEffect(nothingPlays) {
            if (nothingPlays) expandingPlayer.snapTo(PlayerValue.Collapsed)
        }
        // turned sideways (a phone's window becomes a rail's) or back: the open player stays open in the other form
        val navKindNow = NavKind.forWidth(widthClass)
        LaunchedEffect(navKindNow) {
            when {
                navKindNow != NavKind.BottomBar && expandingPlayer.currentValue == PlayerValue.Expanded -> {
                    expandingPlayer.snapTo(PlayerValue.Collapsed)
                    windowPlayer = true
                }
                navKindNow == NavKind.BottomBar && windowPlayer -> {
                    windowPlayer = false
                    expandingPlayer.snapTo(PlayerValue.Expanded)
                }
            }
        }
        LaunchedEffect(nowPlayingRequested) {
            if (nowPlayingRequested) {
                openNowPlaying(NavKind.forWidth(widthClass))
                nowPlayingRequested = false
            }
        }
        // S-1002, 1.0.2: the big player of a tablet and a desktop window; it has the whole window (below a desktop
        // window's caption bar) and the bars stand aside once it covers it (the shell's WindowPage)
        val windowPlayerShown = windowPlayer && navKindNow != NavKind.BottomBar
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
                badges = counts,
                // the menu bar's field is the palette's; phones and tablets open the search page (S-311)
                onSearch = {
                    if (NavKind.forWidth(widthClass) == NavKind.Sidebar) actions.perform(ShortcutId.CommandPalette) else navigator.open(Destination.Search)
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
                        // what is not a video opens here: music in the player (1.0.2: the bar's button closes the big
                        // player again), radio, podcasts, streams on their pages
                        onOpenAudio = { if (windowPlayerShown) windowPlayer = false else openNowPlaying(navKind) },
                        // Desktop Kit 1.0: on a phone the mini player grows into the player itself
                        phonePlayer = expandingPlayer,
                        fullPlayer = {
                            PhonePlayerHost(
                                onClose = closePlayer,
                                onQueue = queuePanel::toggle,
                                onFullScreenLyrics = { lyricsOpen = true },
                                onFullScreenVisualizer = { visualizerOpen = true },
                                // the equaliser's page is the shell's: the player goes down for it, the page opens over
                                // the one under the player, in its tab
                                onEqualizer = {
                                    closePlayer()
                                    navigator.open(Destination.Equalizer)
                                },
                                fullWindow = caption == null,
                            )
                        },
                    )
                },
                navController = navController,
                navigator = navigator,
                caption = caption,
                // Desktop Kit 1.0: on tablet and DeX the player bar floats over the content (a solid card since 1.0.1)
                floatingPlayer = true,
                // 1.0.1: bars, menus and sheets are solid surfaces; nothing changes colour with what passes beneath
                glass = false,
                overlay = {
                    if (sleepTimerOpen) SleepTimerDialog { sleepTimerOpen = false }
                    sourcesFor?.let { section -> SectionSourcesDialog(section, prefs) { sourcesFor = null } }
                    // S-1302: the device picker, from the cast buttons, Ctrl+Shift+C and the menus' "Play on …"
                    Casting.request?.let { request -> CastPickerDialog(request, Casting::closePicker) }
                    val motion = NagTheme.motion
                    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    // 1.0.2: the big player of a tablet and a desktop window, from the bar; ← and → seek in it
                    WindowPlayer(windowPlayerShown, onClose = { windowPlayer = false }, onKey = { event -> playerKey(event, actions) }) { playerModifier, drop ->
                        PhonePlayerHost(
                            onClose = { windowPlayer = false },
                            onQueue = queuePanel::toggle,
                            modifier = playerModifier,
                            onFullScreenLyrics = { lyricsOpen = true },
                            onFullScreenVisualizer = { visualizerOpen = true },
                            // the equaliser's page is the shell's: the player closes for it
                            onEqualizer = {
                                windowPlayer = false
                                navigator.open(Destination.Equalizer)
                            },
                            // as while it shows, also while it goes down
                            fullWindow = caption == null,
                            drop = drop,
                            captionAbove = caption?.height ?: 0.dp,
                        )
                    }
                    AnimatedVisibility(lyricsOpen, enter = motion.detailEnter(rtl), exit = motion.detailExit(rtl)) {
                        PlayingLyricsFullScreen(onClose = { lyricsOpen = false }, modifier = Modifier.blockInputBelow())
                    }
                    // a media's information, asked for from a menu without a details pane (music, search)
                    MediaInfoHost()
                    // remote access's addresses, how it works, and the one-time code a new browser asks for
                    RemoteAccessHost()
                    // S-1005: the lyrics editor, over the lyrics
                    if (LyricsEditorRequest.open) PlayingLyricsEditor(onClose = { LyricsEditorRequest.open = false }, modifier = Modifier.blockInputBelow())
                    AnimatedVisibility(visualizerOpen, enter = motion.detailEnter(rtl), exit = motion.detailExit(rtl)) {
                        PlayingVisualizerFullScreen(onClose = { visualizerOpen = false }, modifier = Modifier.blockInputBelow())
                    }
                    // "Sıradaki" beside the screen (S-310); on a phone a page of its own over the whole window (1.0.1), which
                    // comes up and goes down as a sheet
                    val compactQueue = NavKind.forWidth(widthClass) == NavKind.BottomBar
                    if (compactQueue) {
                        QueuePage(queuePanel.open, onClose = { queuePanel.close() }, undo = undo)
                    } else {
                        AnimatedVisibility(queuePanel.open, modifier = Modifier.align(Alignment.TopEnd), enter = EnterTransition.None, exit = ExitTransition.None) {
                            val queue = rememberPlayQueue()
                            QueueSheet(
                                queue.entries,
                                queue.current,
                                queue,
                                compact = false,
                                onClose = { queuePanel.close() },
                                undo = undo,
                            )
                        }
                    }
                    // the app's notices (what the library, the service and the screens say), over the undo bars
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NoticeHost()
                        UndoBarHost(undo)
                    }
                    if (dropTargets.incoming && dropTargets.hovered == null) DropOverlay()
                },
                screen = { destination, shellNavigator ->
                    val page: @Composable () -> Unit = {
                        when (destination) {
                            // S-803, S-1001, Desktop Kit 1.0: on tablet and DeX windows the inspector beside the table (what plays
                            // with its waveform, the signal path, the lyrics, the queue)
                            Destination.Music -> MusicScreen(
                                repository,
                                onAddFolder = { sourcesFor = LibrarySection.Music },
                                inspector = rememberMusicInspector(
                                    onFullScreenLyrics = { lyricsOpen = true },
                                    onFullScreenVisualizer = { visualizerOpen = true },
                                    undo = undo,
                                ),
                            )
                            // S-804: the medialibrary's playlists, a tab each
                            Destination.Playlists -> PlaylistsScreen(repository, undo = undo, onBrowseMusic = { shellNavigator.navigate(Destination.Music) })
                            // S-1105: the radio directory's search
                            Destination.Radio -> RadioScreen(
                                searchRequested = radioSearchRequested,
                                onSearchHandled = { radioSearchRequested = false },
                                recordingsRequested = radioRecordingsRequested,
                                onRecordingsHandled = { radioRecordingsRequested = false },
                                undo = undo,
                            )
                            // S-1101, S-1102: subscriptions, the search of Podcast Index and iTunes, OPML
                            Destination.Podcasts -> PodcastsScreen(undo = undo)
                            Destination.Search -> SearchScreen(repository, searchQuery) { searchQuery = it }
                            Destination.About -> AboutScreen(rememberThirdParties(), onOpenLink = { url -> openLink(context, url) })
                            // S-1301: SMB, FTP, SFTP, NFS, UPnP/DLNA and WebDAV through libVLC
                            Destination.Network -> NetworkScreen(undo)
                            Destination.Settings -> SettingsScreen(
                                prefs = prefs,
                                onOpenAllSettings = { shellNavigator.open(Destination.AllSettings) },
                                onOpenAbout = { shellNavigator.open(Destination.About) },
                                onShowFirstRun = { prefs.updateFirstRunDone(false) },
                                listState = settingsList,
                                undo = undo,
                                onOpenEqualizer = { shellNavigator.open(Destination.Equalizer) },
                                onOpenGuide = { shellNavigator.open(Destination.Guide) },
                                onOpenShortcutCard = { shellNavigator.open(Destination.ShortcutCard) },
                                reveal = settingsReveal,
                            )
                            // VLC's preference pages, drawn as Nağmedar's
                            Destination.AllSettings -> AllSettingsScreen(
                                onLink = { link ->
                                    when (link) {
                                        NagmedarLink.Equalizer -> shellNavigator.open(Destination.Equalizer)
                                        NagmedarLink.About -> shellNavigator.open(Destination.About)
                                        // Library, Appearance, Remote access, Backup: that section of Settings
                                        else -> link.settingsItem?.let(openSettingsAt)
                                    }
                                },
                                onClose = { if (!shellNavigator.back()) shellNavigator.navigate(Destination.Settings) },
                                requestedKey = ShellRequests.preference,
                                onRequestHandled = { ShellRequests.preference = null },
                            )
                            // S-906, with S-910's signal path and S-912's A/B test beside the chain
                            Destination.Equalizer -> EqualizerScreen(side = { EqualizerSide() })
                            // S-1504: the guide and the card of keys, each leading to the other
                            Destination.Guide -> GuideScreen(onOpenShortcutCard = { shellNavigator.open(Destination.ShortcutCard) })
                            Destination.ShortcutCard -> ShortcutCardScreen(
                                onEdit = actions::editShortcuts,
                                onOpenGuide = { shellNavigator.open(Destination.Guide) },
                            )
                            // 1.0.1: the phone's "⋯" tab, "Daha fazla" (and the rail's): the pages the chrome leaves out,
                            // each opened over it, then what playback has of its own
                            Destination.More -> MoreScreen(
                                destinations = Destination.overflow(NavRules.tabs(NavKind.forWidth(widthClass))),
                                onSelect = shellNavigator::open,
                                actions = listOf(
                                    MoreActionGroup(
                                        stringResource(R.string.nag_menu_playback),
                                        listOf(
                                            MoreAction(stringResource(Destination.Equalizer.label), Destination.Equalizer.icon, { shellNavigator.open(Destination.Equalizer) }, opensPage = true),
                                            MoreAction(stringResource(R.string.nag_sc_sleep_timer), NagIcons.Sleep, { sleepTimerOpen = true }),
                                            MoreAction(stringResource(R.string.nag_queue_title), NagIcons.Queue, queuePanel::toggle),
                                        ),
                                    ),
                                ),
                            )
                            // the network streams opened by their address, and those played before
                            Destination.Streams -> StreamsScreen(undo)
                            // this device's storage, its drives and the picked folders, browsed
                            Destination.Device -> DeviceScreen(prefs)
                            // the podcast episodes and the subtitle tools' models downloaded, coming down or failed
                            Destination.Downloads -> DownloadsScreen()
                        }
                    }
                    // Desktop Kit 1.0: on a phone these pages lie under the bars, their lists scrolling beneath the glass
                    if (destination in UnderBars) UnderShellBars(listOnTop = destination in ListOnTop, content = page) else page()
                },
            )
        }
    }
    // S-1301: libVLC's sign-ins, questions and progress that no page answers, while this window is in front
    LibVlcDialogHost()
    // VLC's "Resume playback: Ask" for music, radio and streams: their question, which nothing answered here
    ResumePromptHost()
    if (paletteOpen) {
        // the library's media come when the palette opens; commands, screens and settings at once
        val media by produceState(emptyList<PaletteItem>()) {
            value = mediaItems(context.resources, repository.tracks()) { item -> PlaybackActions.playList(context, listOf(item), 0) }
        }
        // every line of "All settings", read from its XML away from the main thread
        val preferences by produceState(emptyList<PaletteItem>()) {
            value = withContext(Dispatchers.Default) {
                preferencePaletteItems(context) { key -> ShellRequests.preference = key }
            }
        }
        val wallpaperAccent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val items = remember(media, preferences, actions) {
            commandItems(context.resources, actions::isAvailable, { actions.perform(it) }) +
                screenItems(context.resources, navigator::navigate) +
                settingItems(context.resources, prefs, wallpaperAccent) +
                preferences +
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
                navigator.open(Destination.Search)
            },
        )
    }
}

/**
 * 1.0.2: the big player's own keys: ← and → seek five seconds back and on (with a modifier they are the window's
 * shortcuts). Space plays and pauses, Esc closes it, as everywhere ([AppActions]).
 */
private fun playerKey(event: KeyEvent, actions: AppActions): Boolean {
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed) return false
    return when (event.key) {
        Key.DirectionLeft -> actions.seekBy(-PLAYER_KEY_STEP_MS)
        Key.DirectionRight -> actions.seekBy(PLAYER_KEY_STEP_MS)
        else -> false
    }
}

/** How far ← and → seek in the big player: as far as the seek bar's own keys. */
private const val PLAYER_KEY_STEP_MS = 5_000L

/** The pages laid out under a phone's bars (Desktop Kit 1.0); the others are kept clear of them by the shell. */
private val UnderBars = setOf(
    Destination.Music, Destination.Podcasts, Destination.Radio, Destination.Playlists, Destination.Device, Destination.Network,
    Destination.Streams, Destination.Downloads, Destination.Settings, Destination.Search, Destination.More,
)

/** Of [UnderBars], the pages that are one list from the top: they scroll beneath the top bar too. */
private val ListOnTop = setOf(Destination.Settings, Destination.Search, Destination.More)

/** A web page in the browser (S-209: the source code, the licences); nothing when there is none. */
fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Menu bar contents, built from the shortcut table so labels and keys match everywhere (S-302). */
internal fun menuSpecs(context: Context, actions: AppActions): List<MenuSpec> =
    menuBarSpecs(context.resources, { actions.perform(it) }, actions::isAvailable)

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
    /** S-611: the visualiser over the whole window, opened or closed; closed, and whether it was open. */
    private val toggleVisualizer: () -> Unit = {},
    private val closeVisualizer: () -> Boolean = { false },
    /** S-1105: the radio screen's search box takes the keyboard. */
    private val openRadioSearch: () -> Unit = {},
    /** S-207: the radio screen's Recordings tab. */
    private val openRadioRecordings: () -> Unit = {},
    /** 1.0.2: the big player (or the phone's full player) closed; whether it was open. */
    private val closePlayer: () -> Boolean = { false },
) {
    private var volumeBeforeMute = 100

    /** Alt or F10 (S-302): the keyboard goes to the menu bar, or back from it; false without a menu bar. */
    fun toggleMenuBar(): Boolean = menuBar.onAlt()

    /** The window's keys while the menu bar has the keyboard. */
    fun menuBarKey(keyCode: Int, down: Boolean, rtl: Boolean): Boolean = menuBar.onKey(keyCode, down, rtl)

    /**
     * Files dropped on the window and on no target (S-406): media play in the order dropped (a
     * video is heard), else a word on what to do.
     */
    fun dropFiles(files: List<DroppedFile>): Boolean {
        val res = context.resources
        val message = when (val plan = planDrop(files)) {
            is DropPlan.Play -> {
                MediaUtils.openList(context, plan.uris.map(::heard), 0)
                res.getQuantityString(R.plurals.nag_drop_playing, plan.uris.size, plan.uris.size)
            }
            DropPlan.Nothing -> res.getString(R.string.nag_drop_nothing)
        }
        Notices.show(context, message)
        return true
    }

    /** Media dropped on the player bar (S-406): after what plays; false when none would play. */
    fun queueFiles(files: List<DroppedFile>): Boolean {
        val media = files.filter { it.playable }
        if (media.isEmpty()) return false
        MediaUtils.appendMedia(context, media.map { heard(it.uri) })
        Notices.show(context, context.resources.getQuantityString(R.plurals.nag_drop_queued, media.size, media.size))
        return true
    }

    /** A dropped file as the service plays it: sound alone, a video's too (Nağmedar plays no picture). */
    private fun heard(uri: String): MediaWrapper =
        MLServiceLocator.getAbstractMediaWrapper(Uri.parse(uri)).apply { addFlags(MediaWrapper.MEDIA_FORCE_AUDIO) }

    /** The shortcut editor in Settings (S-402), scrolled to: the card of keys leads here (S-1504). */
    fun editShortcuts() {
        navigator.navigate(Destination.Settings)
        showShortcutList()
    }

    /** The route on screen, before a back (S-408). */
    fun currentRoute(): String? = navigator.currentRoute

    /** A back left [from]: the forward button can return there. */
    fun wentBack(from: String?) = navigator.wentBack(from)

    /** The mouse's forward button (S-408). */
    fun forward(): Boolean = navigator.forward()

    /**
     * An Esc nothing else used: what lies over the player closes, then "Sıradaki" (S-310), then the player itself (1.0.2),
     * else the screen lets go of its selection (S-405).
     */
    fun escape(): Boolean = closeVisualizer() || closeLyrics() || queuePanel.close() || closePlayer() || screenCommands.escape()

    fun leaveMenuBar() {
        if (menuBar.hasKeyboard) menuBar.leave()
    }

    fun isAvailable(id: ShortcutId): Boolean = ShortcutRegistry[id].scope == ShortcutScope.Global && id !in comingLater

    fun perform(id: ShortcutId): Boolean {
        if (!isAvailable(id)) {
            if (ShortcutRegistry[id].scope == ShortcutScope.Global) {
                Notices.show(context, R.string.nag_unavailable_mvp)
                return true
            }
            return false
        }
        val service = PlaybackService.instance
        when (id) {
            ShortcutId.OpenFile -> openFile()
            ShortcutId.CommandPalette -> openPalette()
            // the Streams page, its address field taking the keyboard
            ShortcutId.OpenNetwork -> {
                navigator.navigate(Destination.Streams)
                StreamsCenter.requestFocus()
            }
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
            // the user's volume, without a podcast show's boost over it: kept for the next start
            ShortcutId.VolumeUp -> service?.let { it.setUserVolume((it.userVolume + 5).coerceAtMost(200)) }
            ShortcutId.VolumeDown -> service?.let { it.setUserVolume((it.userVolume - 5).coerceAtLeast(0)) }
            ShortcutId.Mute -> service?.let {
                if (it.userVolume > 0) {
                    volumeBeforeMute = it.userVolume
                    it.setUserVolume(0)
                } else {
                    it.setUserVolume(volumeBeforeMute)
                }
            }
            ShortcutId.SleepTimer -> openSleepTimer()
            ShortcutId.Cast -> Casting.openPicker()
            ShortcutId.Lyrics -> toggleLyrics()
            ShortcutId.Visualizer -> toggleVisualizer()
            ShortcutId.LyricsEarlier -> shiftLyrics(-LyricsSession.STEP_MS)
            ShortcutId.LyricsLater -> shiftLyrics(LyricsSession.STEP_MS)
            ShortcutId.Settings -> navigator.navigate(Destination.Settings)
            // S-906: the music engine's equaliser
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
                Notices.show(context, message)
            }
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
            // S-1107: the station on the air recorded, or its recording stopped
            ShortcutId.RadioRecord -> toggleRadioRecording()
            ShortcutId.RadioRecordings -> {
                navigator.navigate(Destination.Radio)
                openRadioRecordings()
            }
            ShortcutId.ToggleSidebar -> prefs.updateSidebarCollapsed(!prefs.sidebarCollapsed)
            ShortcutId.PlayQueue -> queuePanel.toggle()
            // Desktop Kit 1.0: the music view's inspector, where the window shows one
            ShortcutId.ToggleInspector -> return screenCommands.toggleInspector()
            // S-1504: the card of keys; its button leads to the editor in Settings
            ShortcutId.ShortcutList -> navigator.navigate(Destination.ShortcutCard)
            ShortcutId.UserGuide -> navigator.navigate(Destination.Guide)
            else -> return false
        }
        return true
    }

    /** S-1104: to the next (or the previous) chapter of the podcast episode that plays; a toast says where there is none to go to. */
    private fun stepPodcastChapter(forward: Boolean) {
        when (val step = ChapterSteps.step(forward)) {
            ChapterStep.NoChapters -> Notices.show(context, R.string.nag_pod_chapter_none)
            ChapterStep.AtEnd -> Notices.show(context, R.string.nag_pod_chapter_last)
            is ChapterStep.Went -> Notices.show(context, ChapterSteps.title(context.resources, step.index, step.chapter))
        }
    }

    /** S-1107: records the station on the air; if its recording is going on, stops it. Said in a toast. */
    private fun toggleRadioRecording() {
        val station = RadioCenter.controller(context).now?.station
        val message = if (station == null) {
            context.getString(R.string.nag_radio_rec_none_playing)
        } else {
            val center = RadioRecordings.center(context)
            val recording = station.toRecording()
            val live = center.live.value.firstOrNull { it.request.station.key == recording.key }
            if (live != null) {
                center.stop(live.sessionId)
                context.getString(R.string.nag_radio_rec_stopped, station.name)
            } else {
                center.start(recording)
                context.getString(R.string.nag_radio_rec_started, station.name)
            }
        }
        Notices.show(context, message)
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
        Notices.show(context, message)
    }

    /** What plays [deltaMs] on (back when negative); false when nothing plays. */
    fun seekBy(deltaMs: Long): Boolean {
        val service = PlaybackService.instance ?: return false
        service.seek((service.getTime() + deltaMs).coerceAtLeast(0L), fromUser = true)
        return true
    }

    private companion object {
        /** Global shortcuts whose features arrive in later MVPs; they show a notice instead. */
        val comingLater = emptySet<ShortcutId>()
    }
}
