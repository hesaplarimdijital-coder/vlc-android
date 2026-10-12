package app.nagmedar.ui.player.refresh

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.R
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.vlc.gui.video.VideoPlayerActivity
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * S-612: the screen's refresh rate follows the playing film as the user chose. From Android 11
 * the video's surface tells the system the film's frame rate and the system picks the screen's
 * mode: only seamlessly, or, from Android 12 and asked "always", even through a moment of black
 * (a TV over HDMI going to 23,976 Hz). Where the surface cannot say it (before Android 11, a
 * TextureView, Android 11's "always"), the window asks for the screen's mode of the same size
 * whose rate takes the film best. [status] tells the panel what the screen does now.
 */
@Stable
internal class FrameRateMatcher(private val activity: VideoPlayerActivity) {

    var status by mutableStateOf(RefreshStatus())
        private set

    private var choice = RefreshMatch.Default
    private val handler = Handler(Looper.getMainLooper())
    private val displays: DisplayManager? = activity.getSystemService(DisplayManager::class.java)

    /** The surface told the film's rate, so that "Off" takes it back. */
    private var voted: Surface? = null

    /** The video's view whose surface is watched: a new surface is told the film's rate again. */
    private var watched: SurfaceView? = null

    /** Until when the screen's switch may pause the film (a TV over HDMI), which then goes on. */
    private var resumeUntil = 0L

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != display()?.displayId) return
            read(status.fps.takeIf { it > 0 })
            if (SystemClock.uptimeMillis() < resumeUntil) {
                resumeUntil = 0
                handler.postDelayed({ resume() }, SWITCH_SETTLE_MS)
            }
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            handler.post { apply(choice) }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (voted === holder.surface) voted = null
        }
    }

    fun attach() {
        displays?.registerDisplayListener(displayListener, handler)
    }

    fun release() {
        displays?.unregisterDisplayListener(displayListener)
        watched?.holder?.removeCallback(surfaceCallback)
        watched = null
        voted = null
        handler.removeCallbacksAndMessages(null)
    }

    /** The film's rate and the screen's, read again for the panel. */
    fun refresh() = read(videoFps())

    /** A film started or went on, or the choice changed: the screen follows the film as [match] says. */
    fun apply(match: RefreshMatch) {
        choice = match
        val fps = videoFps()
        read(fps)
        val display = display() ?: return
        val surface = videoSurface()
        // the surface tells the film's rate; from Android 12 also whether the switch may show black
        val told = fps?.takeIf {
            surface != null && when (match) {
                RefreshMatch.Off -> false
                RefreshMatch.Seamless -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                RefreshMatch.Always -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            }
        }
        if (surface != null) {
            if (told != null) tell(surface, told, match) else if (voted != null) withdraw(surface)
        }
        // else the window asks for a mode: always any of the screen's, or, from Android 12, one it switches to seamlessly
        val mode = when {
            fps == null || match == RefreshMatch.Off || told != null -> null
            match == RefreshMatch.Always -> bestMode(display, fps, seamlessOnly = false)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> bestMode(display, fps, seamlessOnly = true)
            else -> null
        }
        askMode(mode?.modeId ?: 0)
        // a switch that may show black may pause the film too: it goes on once the screen has switched
        resumeUntil = if (match == RefreshMatch.Always && activity.service?.isPlaying == true) SystemClock.uptimeMillis() + SWITCH_WAIT_MS else 0
    }

    private fun tell(surface: Surface, fps: Double, match: RefreshMatch) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val strategy = if (match == RefreshMatch.Always) Surface.CHANGE_FRAME_RATE_ALWAYS else Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, strategy)
                voted = surface
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11 switches only seamlessly
                surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                voted = surface
            }
        } catch (e: RuntimeException) {
            // a surface going away as it is told
            Log.w(TAG, "the video's surface did not take $fps fps", e)
        }
    }

    private fun withdraw(surface: Surface) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        } catch (e: RuntimeException) {
            Log.w(TAG, "the video's surface kept its frame rate", e)
        }
        voted = null
    }

    /** The screen's mode of the current size whose rate takes [fps] best; [seamlessOnly], among those it switches to without black. */
    private fun bestMode(display: Display, fps: Double, seamlessOnly: Boolean): Display.Mode? {
        val current = display.mode
        val seamless = if (seamlessOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) current.alternativeRefreshRates.map { it.toDouble() } else emptyList()
        val modes = display.supportedModes.filter { mode ->
            mode.physicalWidth == current.physicalWidth && mode.physicalHeight == current.physicalHeight &&
                (!seamlessOnly || mode.modeId == current.modeId || seamless.any { abs(it - mode.refreshRate) < RATE_TOLERANCE })
        }
        val rate = bestRate(fps, modes.map { it.refreshRate.toDouble() }) ?: return null
        return modes.filter { abs(it.refreshRate - rate) < RATE_TOLERANCE }.minByOrNull { if (it.modeId == current.modeId) 0 else 1 }
    }

    /** The window's wish for the screen's mode; 0 leaves it to the system. */
    private fun askMode(modeId: Int) {
        val window = activity.window ?: return
        val params = window.attributes
        if (params.preferredDisplayModeId == modeId) return
        params.preferredDisplayModeId = modeId
        window.attributes = params
    }

    private fun resume() {
        val service = activity.service ?: return
        if (!service.isPlaying && service.hasMedia()) service.play()
    }

    private fun read(fps: Double?) {
        val display = display() ?: return
        val mode = display.mode
        val rates = display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate.toDouble() }
            .distinctBy { (it * 100).roundToLong() }
            .sorted()
        status = RefreshStatus(
            fps = fps ?: 0.0,
            hz = mode.refreshRate.toDouble(),
            screen = when {
                display.displayId != Display.DEFAULT_DISPLAY -> ScreenKind.External
                // the display's, not the window's: a tablet's small window is still on a tablet
                activity.applicationContext.resources.configuration.smallestScreenWidthDp >= TABLET_DP -> ScreenKind.Tablet
                else -> ScreenKind.Phone
            },
            screenName = display.name,
            rates = rates,
            system = systemMatch(),
        )
    }

    private fun systemMatch(): SystemMatch =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (displays?.matchContentFrameRateUserPreference) {
                DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER -> SystemMatch.Never
                DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY -> SystemMatch.Seamless
                DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS -> SystemMatch.Always
                else -> SystemMatch.Unknown
            }
        } else {
            SystemMatch.Unknown
        }

    @Suppress("DEPRECATION")
    private fun display(): Display? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay
    }.getOrNull()

    /** The playing film's frame rate, as libVLC reads it from the file; null without a video or a rate. */
    private fun videoFps(): Double? {
        val player = activity.service?.mediaplayer ?: return null
        val track = runCatching { player.getSelectedTrack(IMedia.Track.Type.Video) as? IMedia.VideoTrack }.getOrNull() ?: return null
        return if (track.frameRateNum > 0 && track.frameRateDen > 0) track.frameRateNum.toDouble() / track.frameRateDen else null
    }

    /** The surface VLC draws the film on, while it shows; null with a TextureView. */
    private fun videoSurface(): Surface? {
        val view = activity.videoLayout?.findViewById<SurfaceView>(R.id.surface_video)?.takeIf { it.isShown }
        if (view !== watched) {
            watched?.holder?.removeCallback(surfaceCallback)
            view?.holder?.addCallback(surfaceCallback)
            watched = view
        }
        return view?.holder?.surface?.takeIf { it.isValid }
    }

    private companion object {
        const val TAG = "Nagmedar/Refresh"

        /** Rates this close are the same: the screens' rates are floats. */
        const val RATE_TOLERANCE = 0.01

        /** A switch the screen makes within this long of the wish is the wish's. */
        const val SWITCH_WAIT_MS = 10_000L

        /** After a switch, the film goes on once the screen has settled (VLC's own wait). */
        const val SWITCH_SETTLE_MS = 2_000L

        /** A display this wide at its narrowest is a tablet's. */
        const val TABLET_DP = 600
    }
}
