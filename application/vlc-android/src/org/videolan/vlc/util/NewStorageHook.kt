package org.videolan.vlc.util

import android.content.Context

/**
 * Nağmedar: a drive the medialibrary had not seen was mounted (a USB disk, an SD card). The new
 * interface says so (in its window, else in a notification) and leads to the library's folders,
 * instead of VLC's DialogActivity ("scan it?") started over whatever is on screen.
 */
interface NewStorageHandler {
    /** [path] is the drive's root; any thread. */
    fun newStorage(context: Context, path: String)
}

object NewStorageHook {
    @Volatile
    var handler: NewStorageHandler? = null
}
