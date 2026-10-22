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
import app.nagmedar.ui.player.PlayerAppWindows
import app.nagmedar.ui.player.PlayerAppWindowsHost
import app.nagmedar.ui.player.PlayerKeys
import app.nagmedar.ui.player.OsdLevel
import app.nagmedar.ui.player.PlayerOsd
import app.nagmedar.ui.player.PlayerOsdHost
import app.nagmedar.ui.player.ResumePrompt
import app.nagmedar.ui.player.equalizer.PlayerEqualizerTarget
import app.nagmedar.ui.player.equalizer.VlcEqualizer
import app.nagmedar.ui.player.equalizer.toLibVlc
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
 * its remote-control overlay, for a mouse, the keys of the shortcut table and fingers alike. What
 * the player would say or ask in the classic overlay it says here (the OSD, the resume question);
 * none of its classic sheets or dialogs open.
 */
class CastingPlayerChrome : PlayerChrome {
    private var view: View? = null
    private var keys: PlayerKeyTarget? = null
    private val osd = PlayerOsd()
    private var resume by mutableStateOf<CastResumeQuestion?>(null)
    private var activity: VideoPlayerActivity? = null

    /** The app's windows over the casting controls: the guide, the shortcut card, the palette, the sleep timer. */
    private val appWindows = PlayerAppWindows()

    override val active: Boolean get() = view != null

    override fun attach(activity: VideoPlayerActivity, root: FrameLayout) {
        val classic = ActivityKeyTarget(activity)
        keys = PlayerKeyTarget { id ->
            when (id) {
                ShortcutId.Cast -> showCastPicker(activity)
                ShortcutId.UserGuide, ShortcutId.ShortcutList, ShortcutId.CommandPalette, ShortcutId.SleepTimer, ShortcutId.Settings, ShortcutId.Equalizer ->
                    return@PlayerKeyTarget appWindows.perform(activity, id)
                else -> return@PlayerKeyTarget classic.perform(id)
            }
            true
        }
        // the user's keys (S-402), before the first key arrives
        ShortcutRegistry.overrides = ShortcutOverrides.parse(Settings.getInstance(activity).getString(NagmedarPreferences.KEY_SHORTCUTS, null))
        val equalizer = CastingEqualizerTarget(activity)
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                CastingChromeContent(activity, osd)
                resume?.let { question ->
                    val prefs = rememberNagmedarPreferences()
                    val mode = if (hasPrecisePointer(activity)) InputMode.Pointer else InputMode.Touch
                    NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = mode) {
                        ResumePrompt(question.title) { answer, always ->
                            resume = null
                            question.answer(answer, always)
                        }
                    }
                }
                PlayerAppWindowsHost(activity, appWindows, rememberNagmedarPreferences(), equalizer) { id -> keys?.perform(id) }
            }
        }
        root.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view = content
        this.activity = activity
    }

    // the classic sheets and dialogs have nothing to do while casting; the equaliser is the app's
    override fun request(request: PlayerChrome.Request): Boolean {
        val activity = activity ?: return false
        if (request == PlayerChrome.Request.Equalizer) appWindows.perform(activity, ShortcutId.Equalizer)
        return true
    }

    override fun showInfo(text: String, durationMs: Int): Boolean {
        osd.say(text, durationMs.toLong().coerceAtLeast(MIN_SAID_MS))
        return true
    }

    override fun showLevel(level: PlayerChrome.Level, percent: Int): Boolean {
        osd.level(if (level == PlayerChrome.Level.Volume) OsdLevel.Volume else OsdLevel.Brightness, percent, if (level == PlayerChrome.Level.Volume) activity?.maxVolumePercent ?: 100 else 100)
        return true
    }

    override fun showSeek(deltaMs: Int, timeMs: Long, lengthMs: Long): Boolean {
        osd.seeked(deltaMs, timeMs, lengthMs, System.currentTimeMillis())
        return true
    }

    override fun showLoading(loading: Boolean): Boolean {
        osd.loading = loading
        return true
    }

    override fun showDelay(subtitles: Boolean, delayUs: Long): Boolean = true

    override fun askResume(title: String, answer: (resume: Boolean?, always: Boolean) -> Unit): Boolean {
        resume = CastResumeQuestion(title, answer)
        return true
    }

    override fun closeResume() {
        resume = null
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
        resume = null
        activity = null
    }
}

/** The resume question and where its answer goes. */
private class CastResumeQuestion(val title: String, val answer: (resume: Boolean?, always: Boolean) -> Unit)

/** VLC's equaliser window while casting: the player has no effects then, the setting goes to libVLC as it is. */
private class CastingEqualizerTarget(private val activity: VideoPlayerActivity) : PlayerEqualizerTarget {
    override val filmChoice: String? = null

    override fun followPlayer() = Unit

    override fun apply(equalizer: VlcEqualizer?) {
        activity.service?.setEqualizer(equalizer?.toLibVlc())
    }
}

/** The OSD's shortest line. */
private const val MIN_SAID_MS = 600L

/** The casting controls on the playback service, read twice a second; nothing once casting ended (the player is made again then). */
@Composable
private fun CastingChromeContent(activity: VideoPlayerActivity, osd: PlayerOsd) {
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
            PlayerOsdHost(osd)
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
