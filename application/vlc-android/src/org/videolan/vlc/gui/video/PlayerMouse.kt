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
 * settings); the middle button plays and pauses, mutes, or does nothing.
 */
object PlayerMouse {

    /** Seconds a notch of the wheel seeks. */
    private const val SEEK_PER_NOTCH = 10_000

    fun handle(activity: VideoPlayerActivity, event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE) && !event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) return false
        val settings = Settings.getInstance(activity)
        return when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> {
                val notches = event.getAxisValue(MotionEvent.AXIS_VSCROLL).roundToInt()
                if (notches == 0) return false
                val ctrl = event.metaState and KeyEvent.META_CTRL_ON != 0
                val seeks = settings.getBoolean(NAGMEDAR_WHEEL_SEEKS, false) != ctrl
                if (seeks) activity.wheelSeek(notches * SEEK_PER_NOTCH) else activity.wheelVolume(notches)
                true
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (event.actionButton != MotionEvent.BUTTON_TERTIARY) return false
                when (settings.getString(NAGMEDAR_MIDDLE_CLICK, NAGMEDAR_MIDDLE_PLAY_PAUSE)) {
                    NAGMEDAR_MIDDLE_MUTE -> activity.toggleMuteFromMouse()
                    NAGMEDAR_MIDDLE_NONE -> return false
                    else -> activity.doPlayPause()
                }
                true
            }
            else -> false
        }
    }
}
