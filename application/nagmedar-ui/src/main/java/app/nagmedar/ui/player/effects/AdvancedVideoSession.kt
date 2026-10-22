package app.nagmedar.ui.player.effects

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.nagmedar.ui.player.subtitles.LibVlcRestart
import org.videolan.resources.NagmedarVlcOptions

/** Where the advanced effects' options go (S-610): libVLC's next start. */
interface VideoOptionsTarget {
    fun set(options: VideoFilterOptions)

    /** The options libVLC started with: what shows now. */
    fun started(): VideoFilterOptions
}

/**
 * The advanced video effects of the session (S-610): what the panel shows and changes. A change
 * becomes libVLC's options at once and waits for libVLC to start again ([waiting]): the next film
 * after the player closes, or "Apply now".
 */
@Stable
class AdvancedVideoController(private val target: VideoOptionsTarget) {
    var video by mutableStateOf(AdvancedVideo())
        private set

    /** Whether what is set is not what libVLC shows yet. */
    var waiting by mutableStateOf(false)
        private set

    /** The playing film's height in pixels: the text's size is a share of it. */
    var pictureHeight = AdvancedVideoOptions.DEFAULT_HEIGHT
        private set

    fun update(change: (AdvancedVideo) -> AdvancedVideo) {
        val next = change(video)
        if (next == video) return
        video = next
        push()
    }

    /** A film plays, [height] pixels high: a text over it is sized for it. */
    fun onPicture(height: Int) {
        if (height <= 0 || height == pictureHeight) return
        pictureHeight = height
        if (video.text.enabled) push() else refresh()
    }

    /** libVLC may have started again: what waits is read again. */
    fun refresh() {
        waiting = AdvancedVideoOptions.build(video, pictureHeight) != target.started()
    }

    private fun push() {
        val options = AdvancedVideoOptions.build(video, pictureHeight)
        target.set(options)
        waiting = options != target.started()
    }
}

/** libVLC's options ([NagmedarVlcOptions.sessionVideo]); a change makes libVLC start again once it is idle. */
object LibVlcVideoOptions : VideoOptionsTarget {
    override fun set(options: VideoFilterOptions) {
        val session = NagmedarVlcOptions.SessionVideo(options.before, options.after, options.subSources, options.options, options.cpu)
        if (session == NagmedarVlcOptions.sessionVideo) return
        NagmedarVlcOptions.sessionVideo = session
        if (session != NagmedarVlcOptions.startedVideo) LibVlcRestart.pending = true
    }

    override fun started(): VideoFilterOptions = NagmedarVlcOptions.startedVideo.let { VideoFilterOptions(it.before, it.after, it.subSources, it.options, it.cpu) }
}

/** The session's advanced effects: one for the app, kept as long as it runs, whichever player window shows them. */
object AdvancedVideoSession {
    // made in the global snapshot: the object is first touched inside a player window's composition
    val controller = Snapshot.global { AdvancedVideoController(LibVlcVideoOptions) }
}
