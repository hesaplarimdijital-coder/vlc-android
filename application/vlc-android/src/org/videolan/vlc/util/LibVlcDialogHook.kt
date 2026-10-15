package org.videolan.vlc.util

import org.videolan.libvlc.Dialog

/**
 * Nağmedar (S-1301): libVLC's requests (a sign-in, a question, a progress) seen by the new
 * interface first: its network browser answers the sign-in of a folder it reads or of a saved
 * server, shows a folder's question on its page, and its window shows the rest while it is in
 * front. What it does not take goes to the classic interface's windows, as before.
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
    @Volatile
    var handler: LibVlcDialogHandler? = null
}
