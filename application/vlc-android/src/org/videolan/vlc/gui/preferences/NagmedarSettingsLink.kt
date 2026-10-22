package org.videolan.vlc.gui.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.videolan.resources.NAGMEDAR_SHELL_ACTIVITY
import org.videolan.tools.NAGMEDAR_DESTINATION_ALL_SETTINGS
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import org.videolan.tools.NAGMEDAR_EXTRA_PREFERENCE

/**
 * Nağmedar's "All settings" (VLC's preference pages drawn by Nağmedar), on the page of [key] when
 * one is given: what PreferencesActivity.launchWithPref did. An open Nağmedar window takes it.
 */
fun Context.openNagmedarSettings(key: String? = null) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setClassName(applicationContext, NAGMEDAR_SHELL_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(NAGMEDAR_EXTRA_DESTINATION, NAGMEDAR_DESTINATION_ALL_SETTINGS)
    if (key != null) intent.putExtra(NAGMEDAR_EXTRA_PREFERENCE, key)
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
