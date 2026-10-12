package app.nagmedar.ui.player.sync

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
