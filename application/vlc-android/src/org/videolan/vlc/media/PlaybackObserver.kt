package org.videolan.vlc.media

import android.util.Log
import org.videolan.vlc.PlaybackService

/**
 * Something of Nağmedar's that follows the playback service for as long as it lives, whatever
 * started it (the new interface, the classic one, the alarm, a widget): S-808's play counts,
 * S-1009's album covers, S-607's recordings, S-613's latency profiles.
 * The implementations live in modules this one does not see, so they are found by name, as
 * [org.videolan.vlc.gui.video.PlayerChrome] is (their keep rules are the app's).
 */
interface PlaybackObserver {
    /** The service is created; called on the main thread. */
    fun attach(service: PlaybackService)

    /** The service is going; called on the main thread. */
    fun detach()

    companion object {
        private const val TAG = "VLC/PlaybackObserver"
        private val IMPLEMENTATIONS = listOf(
            "app.nagmedar.ui.stats.PlayStatsRecorder",
            "app.nagmedar.ui.covers.CoverObserver",
            "app.nagmedar.ui.player.record.RecordingObserver",
            "app.nagmedar.ui.audio.latency.LatencyObserver",
        )

        /** The observers the app is built with; none without the new interface. */
        fun create(): List<PlaybackObserver> = IMPLEMENTATIONS.mapNotNull { name ->
            try {
                Class.forName(name).getDeclaredConstructor().newInstance() as PlaybackObserver
            } catch (e: ReflectiveOperationException) {
                Log.i(TAG, "no $name: ${e.message}")
                null
            }
        }
    }
}
