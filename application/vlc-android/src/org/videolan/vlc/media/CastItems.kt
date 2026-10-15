/*
 * Nağmedar (S-1302): what casting needs to know of an item.
 */
package org.videolan.vlc.media

import android.net.Uri
import app.nagmedar.audio.MusicFormats
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.playback.CastItem

/** [uri] as casting sees it: a tracker module (S-911) or a DSD file, a CUE sheet's track by its file (S-808); null for none. */
fun castItemOf(uri: Uri?): CastItem? {
    if (uri == null) return null
    val file = CueUri.file(uri)
    return CastItem(tracker = MusicFormats.isTracker(file), dsd = MusicFormats.isDsd(file))
}
