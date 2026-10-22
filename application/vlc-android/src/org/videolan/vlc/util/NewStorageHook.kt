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
    /** The drives mounted before the handler was installed (a start by the drive's own broadcast). */
    private val waiting = ArrayList<Pair<Context, String>>()

    @Volatile
    private var installed: NewStorageHandler? = null

    /** Installed when the app starts; the drives that came before it are said then. */
    var handler: NewStorageHandler?
        get() = installed
        set(value) {
            val kept = synchronized(waiting) {
                installed = value
                if (value == null) emptyList() else waiting.toList().also { waiting.clear() }
            }
            kept.forEach { (context, path) -> value?.newStorage(context, path) }
        }

    /** The new drive at [path] said by the handler, or kept for it until it is installed; any thread. */
    fun newStorage(context: Context, path: String) {
        val handler = synchronized(waiting) {
            installed.also { if (it == null) waiting += context.applicationContext to path }
        } ?: return
        handler.newStorage(context, path)
    }
}
