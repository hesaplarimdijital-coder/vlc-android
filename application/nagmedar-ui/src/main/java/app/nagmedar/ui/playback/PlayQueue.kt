package app.nagmedar.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService

/** An item of the play queue as the panel shows it. */
@Immutable
data class QueueEntry(
    /** Stays with the item when it moves: its location, and which time it is in the queue. */
    val key: String,
    val title: String,
    /** The artist; empty for a video. */
    val subtitle: String,
    val lengthMs: Long,
    val isVideo: Boolean = false,
    val media: MediaWrapper? = null,
)

/** The queue of [list], each item keyed by its location and the times it came before. */
fun queueEntries(list: List<MediaWrapper>): List<QueueEntry> {
    val seen = HashMap<String, Int>()
    return list.map { media ->
        val location = media.location ?: media.uri?.toString().orEmpty()
        val time = (seen[location] ?: 0) + 1
        seen[location] = time
        QueueEntry(
            key = "$location#$time",
            title = media.title ?: media.fileName ?: location.substringAfterLast('/'),
            subtitle = media.artistName.orEmpty(),
            lengthMs = media.length,
            isVideo = media.type == MediaWrapper.TYPE_VIDEO,
            media = media,
        )
    }
}

/** Changes to a queue, done to the list shown at once and to the playback service. */
interface QueueActions {
    fun play(index: Int)

    fun remove(index: Int)

    /** Moves the item at [from] so that it is at [to] afterwards. */
    fun move(from: Int, to: Int)

    /** Takes away everything after what plays now. */
    fun clearUpcoming()

    /** Puts [removed] back at [index]: the undo of a removal. */
    fun restore(index: Int, removed: List<QueueEntry>)
}

/**
 * The playback service's queue (S-310): what plays now and what comes after it, followed while
 * the panel is open. Removing and moving show at once; the service then confirms.
 */
@Stable
class PlayQueue : QueueActions {
    var entries: List<QueueEntry> by mutableStateOf(emptyList())
        private set

    /** The index of what plays now; -1 with nothing. */
    var current by mutableIntStateOf(-1)
        private set

    private var service: PlaybackService? = null

    internal fun load(service: PlaybackService?) {
        this.service = service
        entries = service?.let { queueEntries(it.media) }.orEmpty()
        current = service?.currentMediaPosition ?: -1
    }

    override fun play(index: Int) {
        service?.playIndex(index)
    }

    override fun remove(index: Int) {
        if (index !in entries.indices) return
        entries = entries.toMutableList().apply { removeAt(index) }
        if (index < current) current--
        service?.remove(index)
    }

    override fun move(from: Int, to: Int) {
        if (from !in entries.indices || to !in entries.indices || from == to) return
        entries = entries.toMutableList().apply { add(to, removeAt(from)) }
        current = when (current) {
            from -> to
            in (from + 1)..to -> current - 1
            in to until from -> current + 1
            else -> current
        }
        // the service's list takes the place before which the item goes
        service?.moveItem(from, if (to > from) to + 1 else to)
    }

    override fun clearUpcoming() {
        val last = entries.lastIndex
        if (current < 0 || last <= current) return
        entries = entries.take(current + 1)
        for (index in last downTo current + 1) service?.remove(index)
    }

    override fun restore(index: Int, removed: List<QueueEntry>) {
        val at = index.coerceIn(0, entries.size)
        entries = entries.toMutableList().apply { addAll(at, removed) }
        // what plays now moves down when items come back before it
        if (current >= at) current += removed.size
        removed.forEachIndexed { offset, entry -> entry.media?.let { service?.insertItem(at + offset, it) } }
    }
}

/**
 * The service's queue, followed while composed. It is read at once, so the first frame has the
 * list: "Sıradaki" never shows itself empty while the queue is being read.
 */
@Composable
fun rememberPlayQueue(): PlayQueue {
    val queue = remember { PlayQueue().apply { load(PlaybackService.serviceFlow.value) } }
    LaunchedEffect(queue) {
        PlaybackService.serviceFlow.collectLatest { service ->
            queue.load(service)
            if (service == null) return@collectLatest
            val callback = object : PlaybackService.Callback {
                // the list or what plays changed
                override fun update() = queue.load(service)

                override fun onMediaEvent(event: IMedia.Event) = Unit

                override fun onMediaPlayerEvent(event: MediaPlayer.Event) = Unit
            }
            service.addCallback(callback)
            try {
                awaitCancellation()
            } finally {
                service.removeCallback(callback)
            }
        }
    }
    return queue
}
