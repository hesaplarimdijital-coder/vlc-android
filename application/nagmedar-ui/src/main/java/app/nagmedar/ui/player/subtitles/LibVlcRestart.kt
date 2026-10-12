package app.nagmedar.ui.player.subtitles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.resources.VLCInstance
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.media.PlaylistManager

/**
 * libVLC started again with its options as they are now (S-705), as the classic settings do it
 * when they change VLC's: the subtitles' look reaches the next film. Done once nothing plays, a
 * moment after the player closed; while something does, at the next close.
 */
object LibVlcRestart {
    /** Whether libVLC's options changed since it started. */
    @Volatile
    var pending = false

    fun whenIdle(scope: CoroutineScope = MainScope()) {
        if (!pending) return
        scope.launch {
            delay(SETTLE_MS)
            if (!pending || PlaylistManager.playingState.value == true || PlaylistManager.hasMedia()) return@launch
            pending = false
            if (runCatching { VLCInstance.restart() }.isFailure) {
                pending = true
                return@launch
            }
            // the playback service's player made again, from the new libVLC
            restartMediaPlayer()
        }
    }

    /** The player's window closing stops its film first. */
    private const val SETTLE_MS = 1_500L
}
