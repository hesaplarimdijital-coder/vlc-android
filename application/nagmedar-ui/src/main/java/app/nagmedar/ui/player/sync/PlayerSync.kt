package app.nagmedar.ui.player.sync

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.data.MediaSettings
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The subtitles' speed (S-604): as they are, or converted between the frame rates they were timed
 * for and the video plays at. [factor] stretches the subtitles' times: PAL subtitles (25 fps) on
 * a film (23,976 fps) come 1,0427 times later.
 */
enum class SubtitleRate(val key: String, val factor: Double, @StringRes val label: Int, @StringRes val hint: Int) {
    Same("1", 1.0, R.string.nag_sync_rate_same, R.string.nag_sync_rate_same_hint),
    FilmToPal("23.976>25", 23.976 / 25, R.string.nag_sync_rate_film_to_pal, R.string.nag_sync_rate_film_to_pal_hint),
    PalToFilm("25>23.976", 25 / 23.976, R.string.nag_sync_rate_pal_to_film, R.string.nag_sync_rate_pal_to_film_hint),
    ;

    companion object {
        fun of(key: String?): SubtitleRate? = entries.firstOrNull { it.key == key }
    }
}

/** What the sync panel sets (S-604): the delays in milliseconds (positive: later) and the subtitles' speed. */
@Immutable
data class SyncValues(
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
    val subtitleRate: SubtitleRate = SubtitleRate.Same,
)

/** The classic player's delays, through libVLC's Java API; null while there is no player. */
interface SyncTarget {
    fun audioDelayMs(): Long?

    fun subtitleDelayMs(): Long?

    fun setAudioDelay(ms: Long)

    fun setSubtitleDelay(ms: Long)
}

/**
 * The native side of the subtitles' speed (VLC's associated subtitle frame rate, through the JNI
 * work of S-503/S-505); this build's libVLC does not have it ([PendingNativeSubtitles]).
 */
interface NativeSubtitles {
    val supportsRate: Boolean

    fun setSubtitleRate(factor: Double)
}

object PendingNativeSubtitles : NativeSubtitles {
    override val supportsRate: Boolean = false

    override fun setSubtitleRate(factor: Double) = Unit
}

/** Where a film's delays are remembered: the per-file settings (S-809). */
interface SyncStore {
    /** What [uri] remembers, or null when it remembers none. */
    suspend fun load(uri: String): SyncValues?

    suspend fun save(uri: String, values: SyncValues)

    /** [uri] remembers no delays any more; its effects stay. */
    suspend fun forget(uri: String)
}

object SyncCodec {
    fun fromSettings(settings: MediaSettings): SyncValues? {
        if (settings.audioDelayMs == null && settings.subtitleDelayMs == null && settings.subtitleRate == null) return null
        return SyncValues(
            audioDelayMs = settings.audioDelayMs ?: 0,
            subtitleDelayMs = settings.subtitleDelayMs ?: 0,
            subtitleRate = SubtitleRate.of(settings.subtitleRate) ?: SubtitleRate.Same,
        )
    }
}

class RoomSyncStore(context: Context) : SyncStore {
    private val settings = NagmedarDatabase.get(context).mediaSettings()

    override suspend fun load(uri: String): SyncValues? = withContext(Dispatchers.IO) {
        settings.get(uri)?.let(SyncCodec::fromSettings)
    }

    override suspend fun save(uri: String, values: SyncValues) = withContext(Dispatchers.IO) {
        settings.saveSync(uri, values.audioDelayMs, values.subtitleDelayMs, values.subtitleRate.key, System.currentTimeMillis())
    }

    override suspend fun forget(uri: String) = withContext(Dispatchers.IO) {
        settings.updateSync(uri, null, null, null, System.currentTimeMillis())
    }
}

/**
 * The playing film's delays and subtitle speed (S-604): what the sync panel shows and changes.
 * The delays are the player's: VLC puts back its own as a film starts and the keys (J/K, G/H)
 * change them, so the panel reads them back ([refresh]). A film remembers them when the user
 * ticks "Remember for this file"; they go back over VLC's when it plays again.
 */
@Stable
class SyncController(
    private val target: SyncTarget,
    private val native: NativeSubtitles,
    private val store: SyncStore,
    private val scope: CoroutineScope,
    /** Clicks in a row write once, after they rest this long. */
    private val saveAfterMs: Long = 500,
    /** VLC puts back its own delays a moment after a film starts; a remembered film's go back over them then. */
    private val settleMs: Long = 1_000,
) {
    var values by mutableStateOf(SyncValues())
        private set

    /** Whether the playing film remembers its delays. */
    var remembered by mutableStateOf(false)
        private set

    var media by mutableStateOf<String?>(null)
        private set

    val supportsRate: Boolean get() = native.supportsRate

    private var pending: Pair<String, SyncValues>? = null
    private var saving: Job? = null

    /** A film started playing (or went on): its remembered delays go to the player, else the player's are read. */
    fun onMediaStarted(uri: String?) {
        if (uri == media) {
            refresh()
            return
        }
        flush()
        media = uri
        scope.launch {
            val own = uri?.let { store.load(it) }
            if (media != uri) return@launch
            remembered = own != null
            if (own != null) {
                put(own)
                if (settleMs > 0) delay(settleMs)
                // VLC put its own back meanwhile: ours again
                if (media == uri && (target.audioDelayMs()?.let { it != values.audioDelayMs } == true || target.subtitleDelayMs()?.let { it != values.subtitleDelayMs } == true)) {
                    put(values)
                }
            } else {
                if (values.subtitleRate != SubtitleRate.Same && native.supportsRate) native.setSubtitleRate(SubtitleRate.Same.factor)
                values = SyncValues(target.audioDelayMs() ?: 0, target.subtitleDelayMs() ?: 0)
            }
        }
    }

    /** The player's delays as they are now, after a key or VLC's own controls changed them. */
    fun refresh() {
        val audio = target.audioDelayMs() ?: return
        val subtitles = target.subtitleDelayMs() ?: return
        update(values.copy(audioDelayMs = audio, subtitleDelayMs = subtitles))
    }

    fun setAudioDelay(ms: Long) {
        target.setAudioDelay(ms)
        update(values.copy(audioDelayMs = ms))
    }

    fun setSubtitleDelay(ms: Long) {
        target.setSubtitleDelay(ms)
        update(values.copy(subtitleDelayMs = ms))
    }

    /** The audio delay moved by [deltaMs] from where the player has it. */
    fun nudgeAudio(deltaMs: Long) = setAudioDelay((target.audioDelayMs() ?: values.audioDelayMs) + deltaMs)

    fun nudgeSubtitles(deltaMs: Long) = setSubtitleDelay((target.subtitleDelayMs() ?: values.subtitleDelayMs) + deltaMs)

    fun setRate(rate: SubtitleRate) {
        if (!native.supportsRate) return
        native.setSubtitleRate(rate.factor)
        update(values.copy(subtitleRate = rate))
    }

    /** "Reset all delays": both delays back to 0 and the subtitles at their own speed. */
    fun resetAll() {
        target.setAudioDelay(0)
        target.setSubtitleDelay(0)
        if (values.subtitleRate != SubtitleRate.Same && native.supportsRate) native.setSubtitleRate(SubtitleRate.Same.factor)
        update(SyncValues())
    }

    /** "Remember for this file": ticked, the film remembers its delays from now on; cleared, it forgets them. */
    fun remember(on: Boolean) {
        val uri = media ?: return
        if (on == remembered) return
        remembered = on
        saving?.cancel()
        pending = null
        val value = values
        scope.launch { if (on) store.save(uri, value) else store.forget(uri) }
    }

    /** The player goes: what is left to save is saved. */
    fun release() = flush()

    private fun put(next: SyncValues) {
        target.setAudioDelay(next.audioDelayMs)
        target.setSubtitleDelay(next.subtitleDelayMs)
        if (native.supportsRate) native.setSubtitleRate(next.subtitleRate.factor)
        values = next
    }

    private fun update(next: SyncValues) {
        if (next == values) return
        values = next
        val uri = media
        if (remembered && uri != null) {
            pending = uri to next
            saving?.cancel()
            saving = scope.launch {
                if (saveAfterMs > 0) delay(saveAfterMs)
                save()
            }
        }
    }

    private fun flush() {
        if (pending == null) return
        saving?.cancel()
        scope.launch { save() }
    }

    private suspend fun save() {
        val (uri, value) = pending ?: return
        pending = null
        store.save(uri, value)
    }

    companion object {
        /** The panel's steps, as the mockup has them: 10 ms for fine work, 50 ms as VLC's keys do. */
        const val SMALL_STEP_MS = 10L
        const val STEP_MS = 50L
    }
}
