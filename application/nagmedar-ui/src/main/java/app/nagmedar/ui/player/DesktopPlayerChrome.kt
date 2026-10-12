package app.nagmedar.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.nagmedar.ui.components.LocalTooltipDelay
import app.nagmedar.ui.input.hasPrecisePointer
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import org.videolan.vlc.gui.video.PlayerChrome
import org.videolan.vlc.gui.video.PlayerWindow
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * The new interface's controls over the classic video player (S-601), which finds them by name
 * ([PlayerChrome.create]): a view over the video that a mouse drives and fingers pass through to
 * the classic overlay's gestures.
 */
class DesktopPlayerChrome : PlayerChrome {
    private val controls = ChromeVisibility()
    private var activity: VideoPlayerActivity? = null
    private var frame: View? = null

    override val active: Boolean get() = controls.active

    override fun attach(activity: VideoPlayerActivity, root: FrameLayout) {
        this.activity = activity
        // a desktop with a mouse starts with these controls; a finger later hands the video back
        if (hasPrecisePointer(activity)) controls.activate()
        val frame = MouseOnlyFrame(activity, controls)
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { PlayerChromeContent(activity, controls, frame) }
        }
        frame.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // over the video, under the classic panels (delays, tracks, playlist), which keep their clicks
        val video = activity.videoLayout?.let { root.indexOfChild(it) } ?: -1
        root.addView(frame, video + 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        this.frame = frame
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

    override fun onPictureInPicture(inPictureInPicture: Boolean) = controls.onPictureInPicture(inPictureInPicture)

    override fun detach() {
        frame?.let { (it.parent as? ViewGroup)?.removeView(it) }
        frame = null
        activity = null
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

/** The controls in the app: the player's state twice a second while they show, its commands, frames for the timeline. */
@Composable
private fun PlayerChromeContent(activity: VideoPlayerActivity, visibility: ChromeVisibility, frame: MouseOnlyFrame) {
    val prefs = rememberNagmedarPreferences()
    val source = remember(activity) { ActivityPlayerSource(activity) }
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
    NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = InputMode.Pointer) {
        CompositionLocalProvider(LocalTooltipDelay provides prefs.tooltipDelay.millis) {
            DesktopControls(state, source, visibility, previews)
        }
    }
}

/** The classic player's state, as the controls show it, and its commands. */
internal class ActivityPlayerSource(private val activity: VideoPlayerActivity) : PlayerChromeActions {

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

    override fun close() = activity.onBackPressedDispatcher.onBackPressed()

    private companion object {
        /** The player's id of "no track". */
        const val NO_TRACK = "-1"
    }
}
