/*
 * Nağmedar (S-408): the mouse in the video player.
 */
package org.videolan.vlc.gui.video

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.videolan.tools.NAGMEDAR_MIDDLE_CLICK
import org.videolan.tools.NAGMEDAR_MIDDLE_MUTE
import org.videolan.tools.NAGMEDAR_MIDDLE_NONE
import org.videolan.tools.NAGMEDAR_MIDDLE_PLAY_PAUSE
import org.videolan.tools.NAGMEDAR_WHEEL_SEEKS
import org.videolan.tools.Settings
import kotlin.math.roundToInt

/**
 * The wheel sets the volume and Ctrl+wheel seeks (or the other way round, as set in Nağmedar's
 * settings) while the pointer is over the video: over the controls' own surfaces (their panel, a
 * bar, a card, [PlayerChrome.isOverControls]) the wheel is theirs, it goes on to the views and
 * scrolls them. The middle button plays and pauses, mutes, or does nothing.
 */
object PlayerMouse {

    /** What the mouse drives: the video player, or a test's. */
    interface Target {
        /** Nağmedar's setting: the wheel seeks and Ctrl+wheel sets the volume. */
        val wheelSeeks: Boolean

        /** The middle button's setting: [NAGMEDAR_MIDDLE_PLAY_PAUSE], [NAGMEDAR_MIDDLE_MUTE] or [NAGMEDAR_MIDDLE_NONE]. */
        val middleClick: String

        /** Whether the window's point is on the controls' own surfaces rather than on the video. */
        fun isOverControls(x: Float, y: Float): Boolean

        fun wheelVolume(notches: Int)

        fun wheelSeek(deltaMs: Int)

        fun playPause()

        fun toggleMute()
    }

    /** Milliseconds a notch of the wheel seeks. */
    private const val SEEK_PER_NOTCH = 10_000

    fun handle(activity: VideoPlayerActivity, event: MotionEvent): Boolean {
        // the pointer's moves come here too: only a turn of the wheel or a button reads the settings
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_SCROLL && action != MotionEvent.ACTION_BUTTON_PRESS) return false
        return handle(ActivityMouse(activity), event)
    }

    /** True when the event was the player's; false leaves it to the views (the controls' surfaces among them). */
    fun handle(target: Target, event: MotionEvent): Boolean {
        if (!isMouse(event)) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> {
                val notches = event.getAxisValue(MotionEvent.AXIS_VSCROLL).roundToInt()
                if (notches == 0 || target.isOverControls(event.x, event.y)) return false
                val ctrl = event.metaState and KeyEvent.META_CTRL_ON != 0
                if (target.wheelSeeks != ctrl) target.wheelSeek(notches * SEEK_PER_NOTCH) else target.wheelVolume(notches)
                true
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (event.actionButton != MotionEvent.BUTTON_TERTIARY) return false
                when (target.middleClick) {
                    NAGMEDAR_MIDDLE_MUTE -> target.toggleMute()
                    NAGMEDAR_MIDDLE_NONE -> return false
                    else -> target.playPause()
                }
                true
            }
            else -> false
        }
    }

    private fun isMouse(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
}

/** The video player as the mouse drives it: its settings as they are when the event comes. */
private class ActivityMouse(private val activity: VideoPlayerActivity) : PlayerMouse.Target {
    private val settings = Settings.getInstance(activity)

    override val wheelSeeks: Boolean get() = settings.getBoolean(NAGMEDAR_WHEEL_SEEKS, false)

    override val middleClick: String get() = settings.getString(NAGMEDAR_MIDDLE_CLICK, null) ?: NAGMEDAR_MIDDLE_PLAY_PAUSE

    override fun isOverControls(x: Float, y: Float): Boolean = activity.chrome?.isOverControls(x, y) == true

    override fun wheelVolume(notches: Int) = activity.wheelVolume(notches)

    override fun wheelSeek(deltaMs: Int) = activity.wheelSeek(deltaMs)

    override fun playPause() = activity.doPlayPause()

    override fun toggleMute() {
        activity.toggleMuteFromMouse()
    }
}
