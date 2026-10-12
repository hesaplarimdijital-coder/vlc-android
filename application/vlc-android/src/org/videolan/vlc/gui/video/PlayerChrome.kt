/*
 * Nağmedar (S-601): the desktop control bar over the video player; its panel (S-603); the buttons
 * of picture in picture (S-609).
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
 * The controls a mouse uses over the video: drawn by Nağmedar's new interface, which depends on
 * this module and is therefore found by name. With a mouse they stand for the classic overlay;
 * a finger brings the classic overlay and its gestures back.
 */
interface PlayerChrome {

    /** Adds the controls to [root], over the video and under the classic panels. */
    fun attach(activity: VideoPlayerActivity, root: FrameLayout)

    /** Whether the mouse is in use: the classic overlay then shows these controls instead. */
    val active: Boolean

    /** The player asked for its overlay (a key, a new media): the controls show for a moment. */
    fun show()

    /**
     * A pointer moved, pressed or scrolled over the window, before the views see it: a mouse's
     * makes the controls stand for the overlay, a finger's hands the overlay back to the player.
     */
    fun onMotion(event: MotionEvent)

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
     * S-609: the buttons of picture in picture's window, for a film [playing] or paused: back,
     * play or pause, forward. None leaves the window the system's own.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun pictureInPictureActions(playing: Boolean): List<RemoteAction> = emptyList()

    fun detach()

    companion object {
        private const val TAG = "VLC/PlayerChrome"
        private const val IMPLEMENTATION = "app.nagmedar.ui.player.DesktopPlayerChrome"

        /** The new interface's controls; null when the app is built without it. */
        fun create(): PlayerChrome? = try {
            Class.forName(IMPLEMENTATION).getDeclaredConstructor().newInstance() as PlayerChrome
        } catch (e: ReflectiveOperationException) {
            Log.i(TAG, "no desktop controls: ${e.message}")
            null
        }

        /** Whether [event] comes from a mouse or a touchpad's pointer rather than a finger on the screen or a pen. */
        fun isMouse(event: MotionEvent): Boolean = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            (event.pointerCount > 0 && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE)
    }
}
