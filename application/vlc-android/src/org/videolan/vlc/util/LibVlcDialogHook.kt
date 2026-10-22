package org.videolan.vlc.util

import android.os.Handler
import android.os.Looper
import org.videolan.libvlc.Dialog

/**
 * Nağmedar (S-1301): libVLC's requests (a sign-in, a question, a progress) seen by the new
 * interface first: its network browser answers the sign-in of a folder it reads or of a saved
 * server, shows a folder's question on its page, and its window shows the rest while it is in
 * front, or says in a notification that one waits. Only a TV's classic interface shows what it
 * does not take.
 */
interface LibVlcDialogHandler {
    /** True when [dialog] (a login, a question or a progress) is taken here. Main thread. */
    fun display(dialog: Dialog): Boolean

    /** libVLC took [dialog] back; true when it was one taken here. Main thread. */
    fun canceled(dialog: Dialog): Boolean

    /** [dialog]'s progress moved; true when it is one taken here. Main thread. */
    fun progress(dialog: Dialog.ProgressDialog): Boolean
}

object LibVlcDialogHook {
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** The requests that came before the handler was installed (it is, a moment after libVLC's callbacks). Main thread. */
    private val waiting = ArrayList<Dialog>()

    @Volatile
    var handler: LibVlcDialogHandler? = null
        set(value) {
            field = value
            // what came before it, on the main thread where libVLC's requests come
            if (value != null) main.post(::handOver)
        }

    /** [dialog] shown by the handler, or kept for it until it is installed; false when the handler does not take it. Main thread. */
    fun display(dialog: Dialog): Boolean {
        val handler = handler
        if (handler == null) {
            waiting += dialog
            return true
        }
        return handler.display(dialog)
    }

    /** libVLC took [dialog] back: one still kept is forgotten; true when it was one taken here. Main thread. */
    fun canceled(dialog: Dialog): Boolean = waiting.remove(dialog) || handler?.canceled(dialog) == true

    /** [dialog]'s progress moved; true when it is one taken here (a kept one shows where it is when handed over). Main thread. */
    fun progress(dialog: Dialog.ProgressDialog): Boolean = dialog in waiting || handler?.progress(dialog) == true

    /** The kept requests to the handler; one it does not take is dismissed, never shown in VLC's window. */
    private fun handOver() {
        val handler = handler ?: return
        val kept = waiting.toList()
        waiting.clear()
        kept.forEach { dialog -> if (!handler.display(dialog)) dialog.dismiss() }
    }
}
