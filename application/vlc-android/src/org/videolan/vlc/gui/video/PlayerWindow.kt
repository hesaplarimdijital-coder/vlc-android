/*
 * Nağmedar (S-409): the video player's window on desktops.
 */
package org.videolan.vlc.gui.video

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import org.videolan.tools.NAGMEDAR_PLAYER_FULLSCREEN

object PlayerWindow {

    /**
     * In a desktop window (DeX, freeform) the player asks the system to fill the screen, as video
     * players do on desktops; the window's own buttons bring it back. Android 14 and later.
     */
    fun askForFullscreen(activity: Activity, settings: SharedPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!activity.isInMultiWindowMode || !settings.getBoolean(NAGMEDAR_PLAYER_FULLSCREEN, true)) return
        activity.requestFullscreenMode(Activity.FULLSCREEN_MODE_REQUEST_ENTER, null)
    }
}
