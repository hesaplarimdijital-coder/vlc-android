package app.nagmedar.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import app.nagmedar.ui.player.panel.PanelHost
import app.nagmedar.ui.player.panel.PanelLayout
import app.nagmedar.ui.player.panel.PanelState
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.panel.PlayerPanel
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
import org.videolan.tools.Settings
import org.videolan.vlc.gui.video.PlayerChrome
import org.videolan.vlc.gui.video.PlayerWindow
import org.videolan.vlc.gui.video.VideoPlayerActivity

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

    override val active: Boolean get() = controls.active

    override fun attach(activity: VideoPlayerActivity, root: FrameLayout) {
        this.activity = activity
        val classic = ActivityKeyTarget(activity)
        keys = PlayerKeyTarget { id ->
            when (id) {
                ShortcutId.Effects -> panel.toggle(PanelTab.Effects)
                ShortcutId.Synchronization -> panel.toggle(PanelTab.Sync)
                ShortcutId.MediaInfo -> panel.toggle(PanelTab.Info)
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
        val frame = MouseOnlyFrame(activity, controls)
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { PlayerChromeContent(activity, controls, panel, frame, infoSource, info, live) }
        }
        frame.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // over the video, under the classic panels (delays, tracks, playlist), which keep their clicks
        val video = activity.videoLayout?.let { root.indexOfChild(it) } ?: -1
        root.addView(frame, video + 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        this.frame = frame
        panelHost = PanelHost(activity, root, panel) { layout ->
            PanelContent(activity, controls, panel, effects, sync, PanelInfo(infoSource, info, live), layout)
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
    }

    override fun detach() {
        effects?.release()
        effects = null
        sync?.release()
        sync = null
        panelHost?.detach()
        panelHost = null
        frame?.let { (it.parent as? ViewGroup)?.removeView(it) }
        frame = null
        activity = null
        keys = null
    }
}

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
 * while they or the Info tab show.
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
) {
    val prefs = rememberNagmedarPreferences()
    val source = remember(activity) { ActivityPlayerSource(activity, panel) }
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
    val previews = remember(uri) { uri?.let { RetrieverFramePreviews(activity.applicationContext, it) } }
    DisposableEffect(previews) {
        onDispose { previews?.close() }
    }
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
                DesktopControls(state.copy(effectsOpen = panel.open && panel.tab == PanelTab.Effects), source, visibility, previews)
                if (info.overlay) StatsOverlay(live.last, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 64.dp))
            }
        }
    }
}

/** What the Info tab reads and shows: the film's facts, its statistics, whether they show over the video. */
private class PanelInfo(val source: ActivityInfoSource, val state: InfoState, val live: LiveStats)

/** The Info tab in the app (S-605): the film's facts, read again every two seconds while it shows. */
@Composable
private fun InfoContent(info: PanelInfo, columns: Int) {
    val source = info.source
    var uri by remember { mutableStateOf(source.uri()) }
    var tracks by remember { mutableStateOf(source.tracks()) }
    var decoding by remember { mutableStateOf(source.decoding()) }
    var lengthMs by remember { mutableStateOf(source.lengthMs()) }
    LaunchedEffect(source) {
        while (true) {
            uri = source.uri()
            tracks = source.tracks()
            decoding = source.decoding()
            lengthMs = source.lengthMs()
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
    val rows = listOf(
        InfoRow(stringResource(R.string.nag_info_video_decoder), stringResource(decoding.label)),
        InfoRow(stringResource(R.string.nag_info_video_output), null),
        InfoRow(stringResource(R.string.nag_info_audio_decoder), null),
        InfoRow(stringResource(R.string.nag_info_audio_output), source.audioOutput()),
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
    layout: PanelLayout,
) {
    val prefs = rememberNagmedarPreferences()
    NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = if (visibility.active) InputMode.Pointer else InputMode.Touch) {
        CompositionLocalProvider(LocalTooltipDelay provides prefs.tooltipDelay.millis) {
            PlayerPanel(panel, listOf(PanelTab.Sync, PanelTab.Effects, PanelTab.Info), layout.dock, Modifier.fillMaxSize()) { tab ->
                when (tab) {
                    PanelTab.Sync -> {
                        // the delays are the player's: VLC's own controls and the keys move them too
                        LaunchedEffect(sync, panel.shown) {
                            while (panel.shown) {
                                sync.refresh()
                                delay(500)
                            }
                        }
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
                    PanelTab.Info -> InfoContent(info, layout.columns)
                    else -> Unit
                }
            }
        }
    }
}

/** The classic player's state, as the controls show it, and its commands. */
internal class ActivityPlayerSource(private val activity: VideoPlayerActivity, private val panel: PanelState) : PlayerChromeActions {

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

    override fun close() = activity.onBackPressedDispatcher.onBackPressed()

    private companion object {
        /** The player's id of "no track". */
        const val NO_TRACK = "-1"
    }
}
