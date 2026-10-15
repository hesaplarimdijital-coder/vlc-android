package org.videolan.vlc.util

import androidx.fragment.app.FragmentActivity

/**
 * Nağmedar (S-1302): the new interface's device picker, which the classic video player's cast
 * button opens in place of VLC's renderer list; the picker looks for devices only while it is
 * open, so the button shows whether or not any were found yet.
 */
object CastPickerHook {
    @Volatile
    var picker: ((FragmentActivity) -> Unit)? = null
}
