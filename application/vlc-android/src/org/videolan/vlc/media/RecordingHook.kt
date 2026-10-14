package org.videolan.vlc.media

import android.content.Context
import androidx.annotation.DrawableRes
import org.videolan.vlc.PlaybackService

/**
 * Nağmedar's recording (S-607) for the classic players' options (the video player's and the
 * audio player's sheet): kept by the new interface's module, which this one does not see, whose
 * observer of the playback service ([PlaybackObserver]) sets [recorder] while the service lives.
 */
interface PlaybackRecorder {
    /** Whether a recording is being made. */
    val recording: Boolean

    /** The option's name: start recording, or stop it. */
    fun label(context: Context): String

    @get:DrawableRes
    val icon: Int

    /** Starts recording what [service] plays, or stops the recording. */
    fun toggle(service: PlaybackService)
}

object RecordingHook {
    @Volatile
    var recorder: PlaybackRecorder? = null
}
