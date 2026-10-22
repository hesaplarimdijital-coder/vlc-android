package org.videolan.vlc.media

/**
 * Nağmedar's podcast episodes (S-1104) follow their show's own resume rule (where they were left, or after the show's
 * intro), set by the player that starts them: VLC's classic "resume playback" setting (ask, always, never) does not
 * apply to them, and its dialog never shows before one. Kept by the new interface's module, which this one does not
 * see, whose observer of the playback service ([PlaybackObserver]) sets [owner] while the service lives.
 */
fun interface ResumeOwner {
    /** Whether the media at [location] is an episode of a subscribed show. Called off the main thread. */
    fun ownsResume(location: String): Boolean
}

object ResumeHook {
    @Volatile
    var owner: ResumeOwner? = null
}
