/*
 * Nağmedar (S-702): the character set a film's subtitle files are read in.
 */
package org.videolan.resources

import android.content.Context
import android.net.Uri

/**
 * Finds the character set of a film's subtitle files before the film starts: the user's choice
 * for the film, or what its subtitle files are written in (a Turkish SRT in Windows-1254).
 * Nağmedar's new interface sets it; VLC's own setting stands while it is unset or finds none.
 */
interface SubtitleEncodingFinder {
    /**
     * The VLC name of the character set ("Windows-1254") for the film [media] (the playlist's
     * URI), its file at [location] (a local path where there is one) and the subtitle files kept
     * with it ([kept]); null leaves VLC's setting.
     */
    suspend fun find(context: Context, media: Uri, location: Uri, kept: List<String>): String?
}

object SubtitleEncodingHook {
    @Volatile
    var finder: SubtitleEncodingFinder? = null
}
