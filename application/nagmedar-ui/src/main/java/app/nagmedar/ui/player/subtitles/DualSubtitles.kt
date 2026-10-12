package app.nagmedar.ui.player.subtitles

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.data.NagmedarDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.vlc.gui.video.VideoPlayerActivity
import java.util.Locale

/** A subtitle track as the subtitles' tab lists it: its id, its name, its language as the file tells it ("tur"). */
@Immutable
data class SubtitleTrackChoice(val id: String, val name: String, val language: String = "")

/**
 * The film's two subtitle tracks (S-703): the primary, and the secondary shown with it (VLC 4
 * shows two at once); the secondary is kept when it is turned off, to come back with one key.
 */
@Immutable
data class DualSubtitles(
    val tracks: List<SubtitleTrackChoice> = emptyList(),
    val primary: String? = null,
    val secondary: String? = null,
    val secondaryOn: Boolean = false,
) {
    val primaryTrack: SubtitleTrackChoice? get() = tracks.firstOrNull { it.id == primary }
    val secondaryTrack: SubtitleTrackChoice? get() = tracks.firstOrNull { it.id == secondary }
}

/** The player's subtitle tracks and which of them show, through libVLC (VLC 4 selects more than one of a kind). */
interface SubtitleTrackTarget {
    fun tracks(): List<SubtitleTrackChoice>

    /** The tracks showing, in the player's order. */
    fun selected(): List<String>

    /** Shows [ids], the first as the primary; none turns the subtitles off. */
    fun select(ids: List<String>)
}

/** Where a film's secondary track is kept: the per-file settings (S-809). */
interface SecondaryStore {
    suspend fun load(uri: String): String?

    suspend fun save(uri: String, track: String?)
}

class RoomSecondaryStore(context: Context) : SecondaryStore {
    private val settings = NagmedarDatabase.get(context).mediaSettings()

    override suspend fun load(uri: String): String? = withContext(Dispatchers.IO) { settings.get(uri)?.secondarySubtitleTrack }

    override suspend fun save(uri: String, track: String?) = withContext(Dispatchers.IO) {
        settings.saveSecondarySubtitle(uri, track, System.currentTimeMillis())
    }
}

/**
 * The film's primary and secondary subtitles (S-703): chosen in the subtitles' tab, the secondary
 * turned on and off with Alt+V; read back from the player, whose own keys (V) change them too. A
 * film keeps its secondary track: it shows again as the film plays again.
 */
@Stable
class DualSubtitlesController(
    private val target: SubtitleTrackTarget,
    private val store: SecondaryStore,
    private val scope: CoroutineScope,
    /** The tracks come a moment after a film starts: they are waited for this long, a step at a time. */
    private val waitStepMs: Long = 250,
    private val waitSteps: Int = 12,
) {
    var state by mutableStateOf(DualSubtitles())
        private set

    private var media: String? = null

    /** The player's tracks and which show, after its keys or a new film changed them. */
    fun refresh() {
        val tracks = target.tracks()
        val selected = target.selected().filter { id -> tracks.any { it.id == id } }
        val current = state
        state = when {
            selected.isEmpty() -> current.copy(tracks = tracks, primary = null, secondaryOn = false)
            selected.size == 1 -> current.copy(tracks = tracks, primary = selected[0], secondaryOn = false)
            else -> {
                // ours is the primary when it shows; else the player's first
                val primary = current.primary?.takeIf { it in selected } ?: selected[0]
                current.copy(tracks = tracks, primary = primary, secondary = selected.first { it != primary }, secondaryOn = true)
            }
        }
    }

    fun setPrimary(id: String?) {
        refresh()
        val current = state
        if (id == null) {
            target.select(emptyList())
            state = current.copy(primary = null, secondaryOn = false)
            return
        }
        // the secondary made primary is no longer the secondary
        val secondary = current.secondary?.takeIf { it != id }
        val on = current.secondaryOn && secondary != null
        target.select(listOfNotNull(id, secondary.takeIf { on }))
        state = current.copy(primary = id, secondary = secondary, secondaryOn = on)
    }

    fun setSecondary(id: String?) {
        refresh()
        val current = state
        val primary = current.primary
        if (id == null || primary == null || id == primary) {
            if (primary != null) target.select(listOf(primary))
            state = current.copy(secondary = id?.takeIf { it != primary } ?: current.secondary, secondaryOn = false)
        } else {
            target.select(listOf(primary, id))
            state = current.copy(secondary = id, secondaryOn = true)
        }
        keep()
    }

    /** Alt+V: the secondary on or off; on the first time, the first track in another language. */
    fun toggleSecondary(): Boolean {
        refresh()
        val current = state
        val primary = current.primary ?: return false
        if (current.secondaryOn) {
            target.select(listOf(primary))
            state = current.copy(secondaryOn = false)
        } else {
            val language = current.primaryTrack?.language.orEmpty()
            val secondary = current.secondary?.takeIf { id -> id != primary && current.tracks.any { it.id == id } }
                ?: current.tracks.firstOrNull { it.id != primary && it.language.isNotEmpty() && !it.language.equals(language, ignoreCase = true) }?.id
                ?: current.tracks.firstOrNull { it.id != primary }?.id
                ?: return false
            target.select(listOf(primary, secondary))
            state = current.copy(secondary = secondary, secondaryOn = true)
        }
        keep()
        return state.secondaryOn
    }

    /** A film started or went on: the secondary it keeps shows again once its tracks are there. */
    fun onMediaStarted(uri: String?) {
        if (uri == media) {
            refresh()
            return
        }
        media = uri
        state = DualSubtitles()
        if (uri == null) return
        scope.launch {
            val kept = store.load(uri) ?: return@launch refresh()
            repeat(waitSteps) {
                if (media != uri) return@launch
                refresh()
                val current = state
                if (current.tracks.any { it.id == kept } && current.primary != null) {
                    if (current.primary != kept && !(current.secondaryOn && current.secondary == kept)) {
                        target.select(listOf(current.primary, kept))
                        state = current.copy(secondary = kept, secondaryOn = true)
                    }
                    return@launch
                }
                delay(waitStepMs)
            }
        }
    }

    /** The film keeps its secondary while it shows; turned off, it forgets it. */
    private fun keep() {
        val uri = media ?: return
        val track = state.secondary.takeIf { state.secondaryOn }
        scope.launch { store.save(uri, track) }
    }
}

/** The tracks of the classic player's libVLC: the subtitles VLC lists, and which of them show. */
internal class ActivitySubtitleTracks(private val activity: VideoPlayerActivity) : SubtitleTrackTarget {
    override fun tracks(): List<SubtitleTrackChoice> {
        val player = activity.service?.mediaplayer ?: return emptyList()
        return runCatching { player.getTracks(IMedia.Track.Type.Text) }.getOrNull().orEmpty().map { track ->
            SubtitleTrackChoice(track.id, track.name?.takeIf { it.isNotBlank() } ?: track.description.orEmpty(), track.language.orEmpty())
        }
    }

    override fun selected(): List<String> {
        val player = activity.service?.mediaplayer ?: return emptyList()
        return runCatching { player.getSelectedTracks(IMedia.Track.Type.Text) }.getOrNull().orEmpty().map { it.id }
    }

    override fun select(ids: List<String>) {
        val player = activity.service?.mediaplayer ?: return
        runCatching {
            if (ids.isEmpty()) player.unselectTrackType(IMedia.Track.Type.Text) else player.selectTracks(IMedia.Track.Type.Text, ids.joinToString(","))
        }
    }
}

/** "TR", "EN": a track's [language] as the bar's chip writes two of them ("TR + EN"); its [name] without one. */
fun shortLanguage(language: String, name: String): String =
    languageOf(language)?.uppercase(Locale.ROOT) ?: language.take(3).uppercase(Locale.ROOT).ifEmpty { name }
