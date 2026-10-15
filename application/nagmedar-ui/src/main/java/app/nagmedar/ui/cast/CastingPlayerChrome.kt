package app.nagmedar.ui.cast

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutOverrides
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.hasPrecisePointer
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.player.ActivityKeyTarget
import app.nagmedar.ui.player.PlayerKeyTarget
import app.nagmedar.ui.player.PlayerKeys
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import org.videolan.tools.Settings
import org.videolan.vlc.gui.video.PlayerChrome
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * S-1302: the video player's controls while a renderer plays the film, found by name
 * ([PlayerChrome.createCasting]): the classic player draws no picture then, and these stand for
 * its remote-control overlay, for a mouse, the keys of the shortcut table and fingers alike.
 */
class CastingPlayerChrome : PlayerChrome {
    private var view: View? = null
    private var keys: PlayerKeyTarget? = null

    override val active: Boolean get() = view != null

    override fun attach(activity: VideoPlayerActivity, root: FrameLayout) {
        val classic = ActivityKeyTarget(activity)
        keys = PlayerKeyTarget { id ->
            when (id) {
                ShortcutId.Cast -> showCastPicker(activity)
                else -> return@PlayerKeyTarget classic.perform(id)
            }
            true
        }
        // the user's keys (S-402), before the first key arrives
        ShortcutRegistry.overrides = ShortcutOverrides.parse(Settings.getInstance(activity).getString(NagmedarPreferences.KEY_SHORTCUTS, null))
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { CastingChromeContent(activity) }
        }
        root.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view = content
    }

    override fun show() = Unit

    override fun onMotion(event: MotionEvent) = Unit

    override fun onKey(event: KeyEvent): Boolean = keys?.let { PlayerKeys.handle(event, it) } ?: false

    override fun onPictureInPicture(inPictureInPicture: Boolean) = Unit

    override fun onPlaying() = Unit

    override fun detach() {
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        view = null
        keys = null
    }
}

/** The casting controls on the playback service, read twice a second; nothing once casting ended (the player is made again then). */
@Composable
private fun CastingChromeContent(activity: VideoPlayerActivity) {
    val prefs = rememberNagmedarPreferences()
    val device = rememberCastDevice() ?: return
    var state by remember { mutableStateOf<CastingScreenState?>(null) }
    LaunchedEffect(device) {
        while (true) {
            val service = activity.service
            state = service?.let {
                CastingScreenState(
                    device = device,
                    title = it.title.orEmpty(),
                    playing = it.isPlaying,
                    timeMs = it.getTime(),
                    lengthMs = it.length,
                    hasPrevious = it.hasPrevious(),
                    hasNext = it.hasNext(),
                )
            }
            delay(500)
        }
    }
    val actions = remember(activity) { ActivityCastingActions(activity) }
    val mode = if (hasPrecisePointer(activity)) InputMode.Pointer else InputMode.Touch
    NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = mode) {
        // every touch is the remote's: none reaches the classic player's gestures under it
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
            state?.let { CastingScreen(it, actions) }
        }
    }
}

/** The casting controls on the classic player and its playback service. */
private class ActivityCastingActions(private val activity: VideoPlayerActivity) : CastingActions {
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

    override fun choose() = showCastPicker(activity)

    // the player is made again for this device's screen, where the film goes on
    override fun stopCasting() = Casting.stop()

    override fun close() = activity.onBackPressedDispatcher.onBackPressed()
}
