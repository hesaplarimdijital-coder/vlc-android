package app.nagmedar.ui.player.sync

import android.os.SystemClock
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * The classic player's delays (S-604), which VLC keeps in microseconds; setting them through the
 * playback service also keeps them in VLC's own per-media memory, as its delay controls do.
 */
internal class ActivitySyncTarget(private val activity: VideoPlayerActivity) : SyncTarget {

    override fun audioDelayMs(): Long? = activity.service?.audioDelay?.div(US_PER_MS)

    override fun subtitleDelayMs(): Long? = activity.service?.spuDelay?.div(US_PER_MS)

    override fun setAudioDelay(ms: Long) {
        activity.service?.setAudioDelay(ms * US_PER_MS)
    }

    override fun setSubtitleDelay(ms: Long) {
        activity.service?.setSpuDelay(ms * US_PER_MS)
    }

    private companion object {
        const val US_PER_MS = 1_000L
    }
}

/**
 * The sync assistant's moments and delay (S-704): the classic player's time and speed, the
 * device's clock, and the subtitles' delay as the sync tab has it, which remembers it.
 */
internal class ActivitySyncAssist(private val activity: VideoPlayerActivity, private val sync: SyncController) : SyncAssistTarget {

    override fun mark(): SyncMark? {
        val service = activity.service ?: return null
        if (service.currentMediaWrapper == null) return null
        return SyncMark(service.getTime(), SystemClock.elapsedRealtime(), if (service.isPlaying) service.rate else 0f)
    }

    override fun subtitleDelayMs(): Long {
        // the keys and VLC's own controls move it too
        sync.refresh()
        return sync.values.subtitleDelayMs
    }

    override fun setSubtitleDelay(ms: Long) = sync.setSubtitleDelay(ms)

    override fun remembered(): Boolean = sync.remembered

    override fun remember(on: Boolean) = sync.remember(on)
}
