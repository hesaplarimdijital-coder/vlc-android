/*
 * Nağmedar (S-601): the desktop control bar over the video player; its panel (S-603); the buttons
 * of picture in picture (S-609); the refresh rate's matching (S-612); casting's controls (S-1302);
 * the only controls of the player, for fingers, pens, mice and keys alike.
 */
package org.videolan.vlc.gui.video

import android.app.RemoteAction
import android.os.Build
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.RequiresApi

/**
 * The player's controls over the video: drawn by Nağmedar's new interface, which depends on this
 * module and is therefore found by name. Once attached they are the player's only controls: the
 * classic overlay, its gestures, its option sheets and its dialogs are never shown; what the
 * player would show there it hands to them instead ([request], [showInfo], [showLevel]…).
 */
interface PlayerChrome {

    /** What the player asks of the controls where the classic overlay had a view of its own. */
    enum class Request {
        /** The audio and subtitle tracks (V, the classic tracks button). */
        Tracks,

        /** The player's options (D-pad up, the classic "advanced options"). */
        Options,
        Equalizer,
        JumpToTime,

        /** A subtitle file to add, from the system's picker. */
        PickSubtitles,
        Bookmarks,
        AddBookmark,

        /** The screen locked against touches, or unlocked. */
        Lock,

        /** Picture in picture is turned off for the app in Android's settings. */
        PictureInPictureRefused,
    }

    /** The levels the gestures, the wheel and the keys change. */
    enum class Level { Volume, Brightness }

    /** Adds the controls to [root], over the video and under the classic panels. */
    fun attach(activity: VideoPlayerActivity, root: FrameLayout)

    /** Whether the controls are attached: they stand for the classic overlay, which then never shows. */
    val active: Boolean

    /** The player asked for its overlay (a key, a new media): the controls show for a moment. */
    fun show()

    /**
     * A pointer moved, pressed or scrolled over the window, before the views see it: a mouse's
     * brings the desktop layout of the controls, a finger's or a pen's their touch layout.
     */
    fun onMotion(event: MotionEvent)

    /**
     * Nağmedar: whether the window's point ([x], [y], in pixels from its corner, as the window's
     * events give it) is on the controls' own surfaces (their panel, a bar, a card) rather than on
     * the video. The mouse's wheel there is theirs and scrolls them ([PlayerMouse]); over the
     * video it sets the volume.
     */
    fun isOverControls(x: Float, y: Float): Boolean = false

    /**
     * S-602: a key reached the player: the new interface's shortcut table (VLC's keys, or the
     * user's) answers it first. False leaves it to the classic keys.
     */
    fun onKey(event: KeyEvent): Boolean

    /** The player went into or out of picture in picture, where there are no controls. */
    fun onPictureInPicture(inPictureInPicture: Boolean)

    /**
     * S-603: a media started or went on playing: a new film brings the effects it remembers
     * (or the defaults) to the player.
     */
    fun onPlaying()

    /**
     * S-612: whether these controls make the screen's refresh rate follow the film, as their own
     * choice says (always, when seamless, never); the classic "Match display frame rate" then
     * stands aside.
     */
    val matchesFrameRate: Boolean get() = false

    /**
     * S-609: the buttons of picture in picture's window, for a film [playing] or paused: back,
     * play or pause, forward. None leaves the window the system's own.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun pictureInPictureActions(playing: Boolean): List<RemoteAction> = emptyList()

    /** Nağmedar: [request] answered by the controls; false when they have nothing for it. */
    fun request(request: Request): Boolean = false

    /** Nağmedar: [text] said over the video for [durationMs], where the classic overlay wrote it. */
    fun showInfo(text: String, durationMs: Int): Boolean = false

    /** Nağmedar: the volume or the brightness, in percent, as a key, the wheel or a gesture set it. */
    fun showLevel(level: Level, percent: Int): Boolean = false

    /** Nağmedar: the subtitles' delay ([subtitles]) or the audio's, now [delayUs], as the keys set it. */
    fun showDelay(subtitles: Boolean, delayUs: Long): Boolean = false

    /** Nağmedar: a seek by [deltaMs] (the keys, the ±10 s), now at [timeMs] of [lengthMs]. */
    fun showSeek(deltaMs: Int, timeMs: Long, lengthMs: Long): Boolean = false

    /** Nağmedar: the media is loading or buffering: the controls show it instead of the classic cone. */
    fun showLoading(loading: Boolean): Boolean = false

    /**
     * Nağmedar: whether the video [title] goes on where it stopped. [answer] gets true to resume,
     * false to start again, null to leave the player; [always] for every video until the player
     * closes, as the classic dialog's "do the same for all" box. False leaves it to the classic dialog.
     */
    fun askResume(title: String, answer: (resume: Boolean?, always: Boolean) -> Unit): Boolean = false

    /** The question of [askResume] was answered elsewhere or went. */
    fun closeResume() = Unit

    /** Back: true when the controls took it (a sheet closed, the lock said so). */
    fun onBack(): Boolean = false

    fun detach()

    companion object {
        private const val TAG = "VLC/PlayerChrome"
        private const val IMPLEMENTATION = "app.nagmedar.ui.player.DesktopPlayerChrome"
        private const val CASTING = "app.nagmedar.ui.cast.CastingPlayerChrome"

        /** The new interface's controls; null when the app is built without it. */
        fun create(): PlayerChrome? = named(IMPLEMENTATION)

        /**
         * S-1302: the new interface's controls while a renderer plays the film (the local picture
         * is not drawn): where it plays, the transport, the picker, casting stopped; for mouse,
         * keys and fingers alike. Null when the app is built without them.
         */
        fun createCasting(): PlayerChrome? = named(CASTING)

        private fun named(name: String): PlayerChrome? = try {
            Class.forName(name).getDeclaredConstructor().newInstance() as PlayerChrome
        } catch (e: ReflectiveOperationException) {
            Log.i(TAG, "no desktop controls: ${e.message}")
            null
        }

        /** Whether [event] comes from a mouse or a touchpad's pointer rather than a finger on the screen or a pen. */
        fun isMouse(event: MotionEvent): Boolean = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            (event.pointerCount > 0 && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE)
    }
}
