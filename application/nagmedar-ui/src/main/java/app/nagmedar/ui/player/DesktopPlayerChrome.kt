package app.nagmedar.ui.player

import android.annotation.SuppressLint
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.view.DragEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.LocalTooltipDelay
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutOverrides
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.hasPrecisePointer
import app.nagmedar.ui.input.isMediaOrSubtitleType
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.rememberTechOf
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.library.storageName
import app.nagmedar.ui.player.effects.ActivityEffectsTarget
import app.nagmedar.ui.player.effects.EffectsController
import app.nagmedar.ui.player.effects.EffectsEngine
import app.nagmedar.ui.player.effects.EffectsPanel
import app.nagmedar.ui.player.effects.PendingNativeEffects
import app.nagmedar.ui.player.effects.RoomEffectsStore
import app.nagmedar.ui.player.info.ActivityInfoSource
import app.nagmedar.ui.player.info.FileFacts
import app.nagmedar.ui.player.info.InfoPanel
import app.nagmedar.ui.player.info.InfoRow
import app.nagmedar.ui.player.info.InfoState
import app.nagmedar.ui.player.info.LiveStats
import app.nagmedar.ui.player.info.ReportLabels
import app.nagmedar.ui.player.info.StatsOverlay
import app.nagmedar.ui.player.info.countLabel
import app.nagmedar.ui.player.info.infoReport
import app.nagmedar.ui.player.info.mbpsLabel
import app.nagmedar.ui.player.info.streamRows
import app.nagmedar.ui.player.navigate.BookmarksPanel
import app.nagmedar.ui.player.navigate.JumpDialog
import app.nagmedar.ui.player.navigate.PlayerNavigation
import app.nagmedar.ui.player.navigate.RenameBookmarkDialog
import app.nagmedar.ui.player.panel.PanelHost
import app.nagmedar.ui.player.panel.PanelLayout
import app.nagmedar.ui.player.panel.PanelState
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.panel.PlayerPanel
import app.nagmedar.ui.player.refresh.FrameRateMatcher
import app.nagmedar.ui.player.refresh.RefreshMatch
import app.nagmedar.ui.player.refresh.RefreshSection
import app.nagmedar.ui.player.refresh.cadenceLabel
import app.nagmedar.ui.player.refresh.cadenceText
import app.nagmedar.ui.player.refresh.screenLabel
import app.nagmedar.ui.player.snapshot.SnapshotCard
import app.nagmedar.ui.player.snapshot.SnapshotController
import app.nagmedar.ui.player.snapshot.SnapshotFlash
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.player.snapshot.forgetSnapshotFolder
import app.nagmedar.ui.player.snapshot.keepSnapshotFolder
import app.nagmedar.ui.player.subtitles.EncodingDialog
import app.nagmedar.ui.player.subtitles.PlayerSubtitles
import app.nagmedar.ui.player.subtitles.SubtitleChooser
import app.nagmedar.ui.player.subtitles.SubtitleDropOverlay
import app.nagmedar.ui.player.subtitles.SubtitleEncodings
import app.nagmedar.ui.player.sync.ActivitySyncTarget
import app.nagmedar.ui.player.sync.PendingNativeSubtitles
import app.nagmedar.ui.player.sync.RoomSyncStore
import app.nagmedar.ui.player.sync.SyncController
import app.nagmedar.ui.player.sync.SyncPanel
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.resources.ACTION_REMOTE_PLAYPAUSE
import org.videolan.resources.AndroidDevices
import org.videolan.resources.ACTION_REMOTE_SEEK_BACKWARD
import org.videolan.resources.ACTION_REMOTE_SEEK_FORWARD
import org.videolan.resources.EXTRA_SEEK_DELAY
import org.videolan.resources.SubtitleEncodingHook
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.video.PlayerChrome
import org.videolan.vlc.gui.video.PlayerWindow
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.util.getPendingIntent

/**
 * The new interface's controls over the classic video player (S-601), which finds them by name
 * ([PlayerChrome.create]): a view over the video that a mouse drives and fingers pass through to
 * the classic overlay's gestures; and its panel (S-603), docked beside or under the video, with
 * the effects of the playing film.
 */
class DesktopPlayerChrome : PlayerChrome {
    private val controls = ChromeVisibility()
    private val panel = PanelState()
    private val info = InfoState()
    private val live = LiveStats()
    private var playingUri: String? = null
    private var activity: VideoPlayerActivity? = null
    private var keys: PlayerKeyTarget? = null
    private var frame: View? = null
    private var panelHost: PanelHost? = null
    private var effects: EffectsController? = null
    private var sync: SyncController? = null
    private var snapshots: SnapshotController? = null
    private var navigation: PlayerNavigation? = null
    private var refresh: FrameRateMatcher? = null
    private var subtitles: PlayerSubtitles? = null

    override val active: Boolean get() = controls.active

    // S-612: the screen's refresh rate follows the film as the sync tab's choice says
    override val matchesFrameRate: Boolean get() = true

    override fun attach(activity: VideoPlayerActivity, root: FrameLayout) {
        this.activity = activity
        val classic = ActivityKeyTarget(activity)
        keys = PlayerKeyTarget { id ->
            when (id) {
                ShortcutId.Effects -> panel.toggle(PanelTab.Effects)
                ShortcutId.Synchronization -> panel.toggle(PanelTab.Sync)
                ShortcutId.MediaInfo -> panel.toggle(PanelTab.Info)
                ShortcutId.Snapshot -> snapshots?.take()
                // S-608: the new interface's "Jump to time", bookmarks and their tab
                ShortcutId.JumpToTime -> navigation?.jumping = true
                ShortcutId.AddBookmark -> navigation?.addBookmark()
                ShortcutId.Bookmarks -> panel.toggle(PanelTab.Bookmarks)
                // S-701: the system's picker rather than the classic one
                ShortcutId.AddSubtitleFile -> subtitles?.pick?.invoke()
                // the delays' keys: the sync tab shows (and remembers) where they took the delays
                ShortcutId.AudioDelayDown, ShortcutId.AudioDelayUp, ShortcutId.SubtitleDelayDown, ShortcutId.SubtitleDelayUp -> {
                    val done = classic.perform(id)
                    sync?.refresh()
                    return@PlayerKeyTarget done
                }
                else -> return@PlayerKeyTarget classic.perform(id)
            }
            true
        }
        // the user's keys (S-402), before the first key arrives
        ShortcutRegistry.overrides = ShortcutOverrides.parse(Settings.getInstance(activity).getString(NagmedarPreferences.KEY_SHORTCUTS, null))
        // a desktop with a mouse starts with these controls; a finger later hands the video back
        if (hasPrecisePointer(activity)) controls.activate()
        // the effects (S-603): the classic player's own today, the native ones once libVLC has them
        val effects = EffectsController(
            EffectsEngine(ActivityEffectsTarget(activity), PendingNativeEffects),
            RoomEffectsStore(activity.applicationContext),
            // saves outlive the player's window: the last change is kept when it closes
            MainScope(),
        )
        this.effects = effects
        // the delays and the subtitles' speed (S-604)
        val sync = SyncController(ActivitySyncTarget(activity), PendingNativeSubtitles, RoomSyncStore(activity.applicationContext), MainScope())
        this.sync = sync
        // the film's facts and statistics (S-605)
        val infoSource = ActivityInfoSource(activity)
        // the screenshots (S-606)
        val snapshots = SnapshotController(activity)
        this.snapshots = snapshots
        // "Jump to time", the chapters and the bookmarks (S-608)
        val navigation = PlayerNavigation(activity)
        this.navigation = navigation
        // the screen's refresh rate (S-612)
        val refresh = FrameRateMatcher(activity).also { it.attach() }
        this.refresh = refresh
        // subtitle files: picked, dropped on the video, found beside it (S-701), read in their character set (S-702)
        val subtitles = PlayerSubtitles(activity, MainScope())
        this.subtitles = subtitles
        SubtitleEncodingHook.finder = SubtitleEncodings
        val frame = MouseOnlyFrame(activity, controls)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) frame.setOnDragListener { _, event -> onDrag(activity, subtitles, event) }
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { PlayerChromeContent(activity, controls, panel, frame, infoSource, info, live, snapshots, navigation, subtitles) }
        }
        frame.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // over the video, under the classic panels (delays, tracks, playlist), which keep their clicks
        val video = activity.videoLayout?.let { root.indexOfChild(it) } ?: -1
        root.addView(frame, video + 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        this.frame = frame
        panelHost = PanelHost(activity, root, panel) { layout ->
            PanelContent(activity, controls, panel, effects, sync, PanelInfo(infoSource, info, live, refresh), navigation, refresh, layout)
        }.also { it.attach() }
    }

    override fun show() = controls.show()

    override fun onMotion(event: MotionEvent) {
        if (PlayerChrome.isMouse(event)) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_SCROLL,
                -> {
                    // the mouse takes over from the classic overlay
                    if (!controls.active) activity?.takeIf { it.isShowing }?.overlayDelegate?.hideOverlay(true)
                    controls.onMouseMoved()
                }
            }
        } else if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            controls.onFinger()
        }
    }

    override fun onKey(event: KeyEvent): Boolean {
        // Escape closes the panel before it leaves anything else
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && panel.shown) {
            if (event.action == KeyEvent.ACTION_DOWN) panel.close()
            return true
        }
        return keys?.let { PlayerKeys.handle(event, it) } ?: false
    }

    override fun onPictureInPicture(inPictureInPicture: Boolean) {
        controls.onPictureInPicture(inPictureInPicture)
        // the small window shows the video alone; the panel comes back with the big one
        panel.suspended = inPictureInPicture
    }

    override fun onPlaying() {
        val uri = activity?.service?.currentMediaWrapper?.uri?.toString()
        effects?.onMediaStarted(uri)
        sync?.onMediaStarted(uri)
        // another film: its own minute of statistics
        if (uri != playingUri) live.clear()
        playingUri = uri
        // its bookmarks
        navigation?.onMediaStarted()
        // the screen follows its frame rate (S-612)
        activity?.let { refresh?.apply(RefreshMatch.read(Settings.getInstance(it))) }
        // its subtitles beside it (S-701)
        subtitles?.onMediaStarted()
    }

    /**
     * S-701: files carried over the video from another app or window: while they are, what a drop
     * does shows; dropped, subtitles join the film and a video plays instead.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun onDrag(activity: VideoPlayerActivity, subtitles: PlayerSubtitles, event: DragEvent): Boolean {
        val description = event.clipDescription
        val types = description?.let { d -> (0 until d.mimeTypeCount).map { d.getMimeType(it) } }.orEmpty()
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> types.any(::isMediaOrSubtitleType)
            DragEvent.ACTION_DRAG_ENTERED -> {
                subtitles.dragging = true
                true
            }
            DragEvent.ACTION_DRAG_LOCATION -> true
            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                subtitles.dragging = false
                true
            }
            DragEvent.ACTION_DROP -> {
                subtitles.dragging = false
                val clip = event.clipData ?: return false
                // readable while the player's window lives; a document's access is kept for good where it can be
                activity.requestDragAndDropPermissions(event)
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                subtitles.dropped(uris)
                uris.isNotEmpty()
            }
            else -> false
        }
    }

    /**
     * S-609: picture in picture's window has back and forward ten seconds around play or pause,
     * as the control bar has them; the playback service does them, the window being the system's.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun pictureInPictureActions(playing: Boolean): List<RemoteAction> {
        val activity = activity ?: return emptyList()
        fun action(icon: Int, title: Int, command: String): RemoteAction {
            val intent = Intent(command).setClass(activity, PlaybackService::class.java).putExtra(EXTRA_SEEK_DELAY, PIP_STEP_S)
            val label = activity.getString(title)
            return RemoteAction(Icon.createWithResource(activity, icon), label, label, activity.getPendingIntent(intent))
        }
        return listOf(
            action(R.drawable.nagmedar_pip_back, R.string.nag_sc_back_10s, ACTION_REMOTE_SEEK_BACKWARD),
            if (playing) action(R.drawable.nagmedar_pip_pause, R.string.nag_pause, ACTION_REMOTE_PLAYPAUSE) else action(R.drawable.nagmedar_pip_play, R.string.nag_play, ACTION_REMOTE_PLAYPAUSE),
            action(R.drawable.nagmedar_pip_forward, R.string.nag_sc_forward_10s, ACTION_REMOTE_SEEK_FORWARD),
        )
    }

    override fun detach() {
        effects?.release()
        effects = null
        sync?.release()
        sync = null
        snapshots = null
        navigation?.release()
        navigation = null
        refresh?.release()
        refresh = null
        subtitles = null
        panelHost?.detach()
        panelHost = null
        frame?.setOnDragListener(null)
        frame?.let { (it.parent as? ViewGroup)?.removeView(it) }
        frame = null
        activity = null
        keys = null
    }
}

/** Picture in picture's step back and forward, in seconds: the control bar's. */
private const val PIP_STEP_S = 10L

/**
 * Over the video: a mouse reaches the controls, a finger or a pen passes through to the classic
 * overlay's gestures. The keys stay the player's (Space, the arrows, the shortcuts): nothing in
 * here takes the focus. The cursor hides with the controls when the video fills the screen.
 */
@SuppressLint("ViewConstructor")
internal class MouseOnlyFrame(context: Context, private val controls: ChromeVisibility) : FrameLayout(context) {

    /** Whether the cursor hides with the controls: the video fills the screen. */
    var hidesCursor = false

    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        controls.active && !controls.pictureInPicture && PlayerChrome.isMouse(event) && super.dispatchTouchEvent(event)

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = controls.active && super.dispatchHoverEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = controls.active && super.dispatchGenericMotionEvent(event)

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? =
        if (hidesCursor && controls.cursorHidden) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL) else super.onResolvePointerIcon(event, pointerIndex)

    /** The cursor's look changed without a move: the system asks again. */
    fun cursorChanged() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) pointerIcon = null
    }
}

/**
 * The controls in the app: the player's state twice a second while they show, its commands,
 * frames for the timeline; the statistics over the video when asked (S-605), sampled every second
 * while they or the Info tab show; the screenshots' flash and card (S-606).
 */
@Composable
private fun PlayerChromeContent(
    activity: VideoPlayerActivity,
    visibility: ChromeVisibility,
    panel: PanelState,
    frame: MouseOnlyFrame,
    infoSource: ActivityInfoSource,
    info: InfoState,
    live: LiveStats,
    snapshots: SnapshotController,
    navigation: PlayerNavigation,
    subtitles: PlayerSubtitles,
) {
    val prefs = rememberNagmedarPreferences()
    val source = remember(activity, prefs) { ActivityPlayerSource(activity, panel, snapshots, subtitles, prefs) }
    // the screenshots' folder, chosen with the system's picker: written in, kept across restarts
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) keepSnapshotFolder(activity, prefs, uri)
    }
    // subtitle files (S-701): any file, as senders call .srt files many things; what is not one is said
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> subtitles.picked(uris) }
    SideEffect {
        // a picker over the player is not leaving it: no small window
        source.pickFolder = {
            activity.isShowingDialog = true
            runCatching { folderPicker.launch(null) }
        }
        subtitles.pick = {
            activity.isShowingDialog = true
            runCatching { subtitlePicker.launch(arrayOf("*/*")) }
        }
    }
    var state by remember { mutableStateOf(PlayerChromeState()) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(source, visibility.visible) {
        while (true) {
            state = source.read()
            uri = source.mediaUri()
            frame.hidesCursor = source.fillsScreen()
            if (!visibility.visible) break
            delay(500)
        }
    }
    LaunchedEffect(visibility) {
        snapshotFlow { visibility.cursorHidden }.collect { frame.cursorChanged() }
    }
    // shared with the chapters and the bookmarks; read again for another film
    val previews = remember(uri) { navigation.previewsFor(uri) }
    val sampling = info.overlay || (panel.shown && panel.tab == PanelTab.Info)
    LaunchedEffect(sampling) {
        while (sampling) {
            live.add(infoSource.stats())
            delay(1_000)
        }
    }
    NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = InputMode.Pointer) {
        CompositionLocalProvider(LocalTooltipDelay provides prefs.tooltipDelay.millis) {
            Box(Modifier.fillMaxSize()) {
                SnapshotFlash(snapshots.state.flashes)
                DesktopControls(
                    state.copy(
                        effectsOpen = panel.open && panel.tab == PanelTab.Effects,
                        snapshot = prefs.snapshot,
                        bookmarks = navigation.bookmarks.marks.map { it.timeMs },
                    ),
                    source,
                    visibility,
                    previews,
                )
                if (info.overlay) StatsOverlay(live.last, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 64.dp))
                // above the control bar while it shows: the subtitles found beside the film (S-701), a screenshot's card
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = if (visibility.visible) 120.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SubtitleChooser(subtitles.found, onChoose = subtitles::choose, onDismiss = subtitles::dismiss)
                    SnapshotCard(
                        outcome = snapshots.state.outcome,
                        saving = snapshots.state.saving,
                        onOpen = snapshots::open,
                        onShare = snapshots::share,
                        onChooseFolder = source::chooseSnapshotFolder,
                        onDismiss = snapshots.state::dismiss,
                    )
                }
                if (subtitles.dragging) SubtitleDropOverlay()
            }
            // S-702: the character set of the film's subtitle files
            subtitles.encoding?.let { sample -> EncodingDialog(sample, onApply = subtitles::applyEncoding, onDismiss = subtitles::closeEncoding) }
            // Ctrl+T (S-608): the time and the chapters as they are when it opens
            if (navigation.jumping) {
                val now = remember { navigation.timeMs() }
                val length = remember { navigation.lengthMs() }
                val chapters = remember { navigation.chapters() }
                JumpDialog(now, length, chapters, onJump = navigation::seek, onDismiss = { navigation.jumping = false })
            }
        }
    }
}

/**
 * What the Info tab reads and shows: the film's facts, its statistics, whether they show over the
 * video; the screen's refresh rate and the frames' cadence on it (S-612).
 */
private class PanelInfo(val source: ActivityInfoSource, val state: InfoState, val live: LiveStats, val refresh: FrameRateMatcher)

/** The Info tab in the app (S-605): the film's facts, read again every two seconds while it shows. */
@Composable
private fun InfoContent(info: PanelInfo, navigation: PlayerNavigation, columns: Int) {
    val source = info.source
    var uri by remember { mutableStateOf(source.uri()) }
    var tracks by remember { mutableStateOf(source.tracks()) }
    var decoding by remember { mutableStateOf(source.decoding()) }
    var lengthMs by remember { mutableStateOf(source.lengthMs()) }
    var chapters by remember { mutableStateOf(navigation.chapters()) }
    var nowMs by remember { mutableStateOf(navigation.timeMs()) }
    LaunchedEffect(source) {
        while (true) {
            uri = source.uri()
            tracks = source.tracks()
            decoding = source.decoding()
            lengthMs = source.lengthMs()
            info.refresh.refresh()
            chapters = navigation.chapters()
            nowMs = navigation.timeMs()
            delay(2_000)
        }
    }
    val size by produceState<Long?>(null, uri) { value = withContext(Dispatchers.IO) { source.sizeBytes() } }
    val tech = rememberTechOf(uri)
    val locale = currentLocale()
    val storage = uri?.let { storageName(it) }
    val file = uri?.let {
        FileFacts(
            name = source.fileName() ?: it,
            details = listOfNotNull(
                (tech?.sizeBytes?.takeIf { bytes -> bytes > 0 } ?: size)?.let { bytes -> sizeLabel(bytes, locale) },
                tech?.container?.takeIf { name -> name.isNotEmpty() },
                lengthMs.takeIf { ms -> ms > 0 }?.let(::playerClock),
                storage,
            ).joinToString(" · "),
        )
    }
    val streams = streamRows(tech, tracks, decoding)
    // the video output and the audio decoder are the native side's to tell (S-504, S-506)
    val screen = info.refresh.status
    val rows = listOf(
        InfoRow(stringResource(R.string.nag_info_video_decoder), stringResource(decoding.label)),
        InfoRow(stringResource(R.string.nag_info_video_output), null),
        InfoRow(stringResource(R.string.nag_info_audio_decoder), null),
        InfoRow(stringResource(R.string.nag_info_audio_output), source.audioOutput()),
        // the screen's rate and how the film's frames fall on it (S-612)
        InfoRow(stringResource(R.string.nag_refresh_screen), screenLabel(screen)),
        InfoRow(cadenceLabel(screen.cadence), cadenceText(screen.cadence)),
    )
    val last = info.live.last
    val labels = ReportLabels(
        streams = stringResource(R.string.nag_info_streams),
        decoding = stringResource(R.string.nag_info_decoding),
        live = stringResource(R.string.nag_info_live),
        bitrate = stringResource(R.string.nag_info_report_bitrate, last?.let { mbpsLabel(it.inputMbps, locale) }.orEmpty()),
        frames = stringResource(R.string.nag_info_report_frames, countLabel(last?.lost ?: 0, locale), countLabel(last?.displayed ?: 0, locale)),
    )
    InfoPanel(
        file = file,
        streams = streams,
        decoding = rows,
        live = info.live,
        overlay = info.state.overlay,
        onOverlay = { info.state.overlay = it },
        report = { infoReport(file, streams, rows, info.live.last, labels) },
        columns = columns,
        chapters = chapters,
        nowMs = nowMs,
        previews = remember(uri) { navigation.previewsFor(uri?.let(Uri::parse)) },
        onChapter = { ms ->
            navigation.seek(ms)
            nowMs = ms
        },
    )
}

/**
 * The panel in the app: its sync tab (S-604) and its effects tab (S-603) drive the playing film;
 * its Info tab (S-605) describes it.
 * Dense for a mouse, roomier when a finger last touched the video.
 */
@Composable
private fun PanelContent(
    activity: VideoPlayerActivity,
    visibility: ChromeVisibility,
    panel: PanelState,
    effects: EffectsController,
    sync: SyncController,
    info: PanelInfo,
    navigation: PlayerNavigation,
    refresh: FrameRateMatcher,
    layout: PanelLayout,
) {
    val prefs = rememberNagmedarPreferences()
    NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = if (visibility.active) InputMode.Pointer else InputMode.Touch) {
        CompositionLocalProvider(LocalTooltipDelay provides prefs.tooltipDelay.millis) {
            PlayerPanel(panel, listOf(PanelTab.Sync, PanelTab.Effects, PanelTab.Info, PanelTab.Bookmarks), layout.dock, Modifier.fillMaxSize()) { tab ->
                when (tab) {
                    PanelTab.Sync -> {
                        // the delays are the player's: VLC's own controls and the keys move them too
                        LaunchedEffect(sync, panel.shown) {
                            while (panel.shown) {
                                sync.refresh()
                                delay(500)
                            }
                        }
                        // the screen's rate is read again as the tab opens; its changes come by themselves
                        LaunchedEffect(refresh) { refresh.refresh() }
                        SyncPanel(
                            values = sync.values,
                            remembered = sync.remembered,
                            supportsRate = sync.supportsRate,
                            onAudioDelay = sync::setAudioDelay,
                            onSubtitleDelay = sync::setSubtitleDelay,
                            onRate = sync::setRate,
                            onResetAll = sync::resetAll,
                            onRemember = sync::remember,
                            columns = layout.columns,
                            refresh = {
                                RefreshSection(refresh.status, prefs.refreshMatch) { match ->
                                    prefs.updateRefreshMatch(match)
                                    refresh.apply(match)
                                }
                            },
                        )
                    }
                    PanelTab.Effects -> EffectsPanel(
                        effects = effects.effects,
                        supported = effects.supported,
                        remembered = effects.remembered,
                        onChange = effects::update,
                        onRemember = effects::remember,
                        onMakeDefault = effects::makeDefault,
                        onEqualizerWindow = activity::showEqualizer,
                        columns = layout.columns,
                    )
                    PanelTab.Info -> InfoContent(info, navigation, layout.columns)
                    PanelTab.Bookmarks -> {
                        val uri = remember { mutableStateOf(info.source.uri()) }
                        LaunchedEffect(navigation) {
                            while (true) {
                                uri.value = info.source.uri()
                                delay(2_000)
                            }
                        }
                        BookmarksPanel(
                            marks = navigation.bookmarks.marks,
                            refused = navigation.bookmarks.refused,
                            previews = remember(uri.value) { navigation.previewsFor(uri.value?.let(Uri::parse)) },
                            onAdd = navigation::addBookmark,
                            onSeek = navigation::seek,
                            onRename = { navigation.renaming = it },
                            onRemove = navigation.bookmarks::remove,
                        )
                    }
                    else -> Unit
                }
            }
            navigation.renaming?.let { mark ->
                RenameBookmarkDialog(mark, onRename = { name -> navigation.bookmarks.rename(mark, name) }, onDismiss = { navigation.renaming = null })
            }
        }
    }
}

/** The classic player's state, as the controls show it, and its commands. */
internal class ActivityPlayerSource(
    private val activity: VideoPlayerActivity,
    private val panel: PanelState,
    private val snapshots: SnapshotController,
    private val subtitles: PlayerSubtitles,
    private val prefs: NagmedarPreferences,
) : PlayerChromeActions {

    /** Opens the folder picker: the composition's launcher. */
    var pickFolder: () -> Unit = {}

    fun read(): PlayerChromeState {
        val service = activity.service ?: return PlayerChromeState()
        val manager = service.playlistManager
        val ab = manager.abRepeat.value?.takeIf { manager.abRepeatOn.value == true }
        return PlayerChromeState(
            title = service.title.orEmpty(),
            playing = service.isPlaying,
            timeMs = service.getTime(),
            lengthMs = service.length,
            chapters = service.getChapters(-1)?.map { ChapterMark(it.timeOffset, it.name.orEmpty()) }.orEmpty(),
            abStartMs = ab?.start ?: -1L,
            abStopMs = ab?.stop ?: -1L,
            rate = service.rate,
            audioTracks = service.audioTracks.orEmpty().filter { it.getId() != NO_TRACK }.map { TrackChoice(it.getId(), it.getName()) },
            audioTrack = service.audioTrack.takeIf { it != NO_TRACK },
            subtitleTracks = service.spuTracks.orEmpty().filter { it.getId() != NO_TRACK }.map { TrackChoice(it.getId(), it.getName()) },
            subtitleTrack = service.spuTrack.takeIf { it != NO_TRACK },
            volume = activity.volumePercent,
            maxVolume = activity.maxVolumePercent,
            muted = activity.muted,
            fullscreen = PlayerWindow.isFullscreen(activity),
            canFullscreen = PlayerWindow.canToggleFullscreen(activity),
            hasPrevious = service.hasPrevious(),
            hasNext = service.hasNext(),
            canPictureInPicture = AndroidDevices.pipAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
        )
    }

    fun mediaUri(): Uri? = activity.service?.currentMediaWrapper?.uri

    /** Whether the video fills the screen: a phone's or a tablet's, or a desktop window made full. */
    fun fillsScreen(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !activity.isInMultiWindowMode

    override fun togglePlayPause() = activity.doPlayPause()

    override fun previous() {
        activity.service?.previous(false)
    }

    override fun next() {
        activity.service?.next()
    }

    override fun seekTo(ms: Long) {
        activity.service?.seek(ms, fromUser = true)
    }

    override fun seekBy(deltaMs: Int) = activity.seekBy(deltaMs)

    override fun setRate(rate: Float) {
        activity.service?.setRate(rate, true)
    }

    override fun setAudioTrack(id: String) {
        activity.service?.setAudioTrack(id)
    }

    override fun setSubtitleTrack(id: String) {
        activity.service?.setSpuTrack(id)
    }

    override fun abRepeat() {
        val service = activity.service ?: return
        val manager = service.playlistManager
        val ab = manager.abRepeat.value?.takeIf { manager.abRepeatOn.value == true }
        when {
            ab == null || ab.start < 0 -> {
                if (manager.abRepeatOn.value != true) manager.toggleABRepeat()
                manager.setABRepeatValue(service.currentMediaWrapper, service.getTime())
            }
            ab.stop < 0 -> manager.setABRepeatValue(service.currentMediaWrapper, service.getTime())
            else -> manager.clearABRepeat()
        }
    }

    override fun setVolume(percent: Int) = activity.setVolumePercent(percent)

    override fun toggleMute() = activity.toggleMute()

    override fun toggleFullscreen() = PlayerWindow.toggleFullscreen(activity)

    override fun toggleEffects() = panel.toggle(PanelTab.Effects)

    override fun takeSnapshot() = snapshots.take()

    override fun setSnapshotSettings(settings: SnapshotSettings) {
        // back to Pictures/Nağmedar: the chosen folder's permission goes
        if (settings.folder == null && prefs.snapshot.folder != null) forgetSnapshotFolder(activity, prefs)
        prefs.updateSnapshot(settings)
    }

    override fun chooseSnapshotFolder() = pickFolder()

    // VLC's way in: the system's picture in picture, or its own small window where the device has none
    override fun pictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activity.switchToPopup()
    }

    override fun addSubtitleFile() = subtitles.pick()

    override fun chooseSubtitleEncoding() = subtitles.openEncoding()

    override fun close() = activity.onBackPressedDispatcher.onBackPressed()

    private companion object {
        /** The player's id of "no track". */
        const val NO_TRACK = "-1"
    }
}
