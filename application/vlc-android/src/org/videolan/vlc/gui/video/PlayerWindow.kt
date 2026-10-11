/*
 * Nağmedar (S-409, S-601): the video player's window on desktops.
 */
package org.videolan.vlc.gui.video

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import org.videolan.tools.NAGMEDAR_PLAYER_FULLSCREEN

object PlayerWindow {

    /** Whether the player asked the system to fill the screen and has not given it back since. */
    var fullscreenRequested = false
        private set

    /**
     * In a desktop window (DeX, freeform) the player asks the system to fill the screen, as video
     * players do on desktops; the window's own buttons bring it back. Android 14 and later.
     */
    fun askForFullscreen(activity: Activity, settings: SharedPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!activity.isInMultiWindowMode || !settings.getBoolean(NAGMEDAR_PLAYER_FULLSCREEN, true)) return
        activity.requestFullscreenMode(Activity.FULLSCREEN_MODE_REQUEST_ENTER, null)
        fullscreenRequested = true
    }

    /** S-601: whether the window fills the screen at the player's request; its own buttons may have brought it back. */
    fun isFullscreen(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && fullscreenRequested && !activity.isInMultiWindowMode

    /** S-601: whether the window can fill the screen and come back: a desktop window, Android 14 and later. */
    fun canToggleFullscreen(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && (activity.isInMultiWindowMode || isFullscreen(activity))

    /** S-601: the desktop controls' full-screen button and the double click on the video. */
    fun toggleFullscreen(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (activity.isInMultiWindowMode) {
            activity.requestFullscreenMode(Activity.FULLSCREEN_MODE_REQUEST_ENTER, null)
            fullscreenRequested = true
        } else if (fullscreenRequested) {
            activity.requestFullscreenMode(Activity.FULLSCREEN_MODE_REQUEST_EXIT, null)
            fullscreenRequested = false
        }
    }
}
