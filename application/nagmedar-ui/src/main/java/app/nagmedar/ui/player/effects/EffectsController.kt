package app.nagmedar.ui.player.effects

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.data.NagmedarDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings

/** Where the effects are kept: per file (S-603, in Nağmedar's database) and the defaults. */
interface EffectsStore {
    /** The effects [uri] remembers, or null when it remembers none. */
    suspend fun load(uri: String): PlayerEffects?

    suspend fun save(uri: String, effects: PlayerEffects)

    /** [uri] remembers no effects any more; its delays and tracks stay. */
    suspend fun forget(uri: String)

    /** The effects of films that remember none ("Make default"), or null. */
    fun defaults(): PlayerEffects?

    fun saveDefaults(effects: PlayerEffects)
}

/** The per-file settings (S-809's media_settings) and the app's preferences. */
class RoomEffectsStore(context: Context) : EffectsStore {
    private val settings = NagmedarDatabase.get(context).mediaSettings()
    private val prefs = Settings.getInstance(context)

    override suspend fun load(uri: String): PlayerEffects? = withContext(Dispatchers.IO) {
        settings.get(uri)?.let(EffectsCodec::fromSettings)
    }

    override suspend fun save(uri: String, effects: PlayerEffects) = withContext(Dispatchers.IO) {
        val columns = EffectsCodec.columns(effects)
        settings.saveEffects(uri, columns.aspectRatio, columns.deinterlace, columns.equalizerPreset, columns.effects, System.currentTimeMillis())
    }

    override suspend fun forget(uri: String) = withContext(Dispatchers.IO) {
        settings.updateEffects(uri, null, null, null, null, System.currentTimeMillis())
    }

    override fun defaults(): PlayerEffects? = EffectsCodec.decodeDefaults(prefs.getString(KEY_DEFAULTS, null))

    override fun saveDefaults(effects: PlayerEffects) {
        prefs.edit().putString(KEY_DEFAULTS, EffectsCodec.encodeDefaults(effects)).apply()
    }

    companion object {
        const val KEY_DEFAULTS = "nagmedar_effects_default"
    }
}

/**
 * The playing film's effects (S-603): what the panel shows and changes, applied at once by the
 * [engine]. A film remembers its effects when the user ticks "Save for this file" and keeps them
 * up to date after that; a film that remembers none starts with the defaults ("Make default").
 */
@Stable
class EffectsController(
    private val engine: EffectsEngine,
    private val store: EffectsStore,
    private val scope: CoroutineScope,
    /** A dragged slider writes once, after it rests this long. */
    private val saveAfterMs: Long = 500,
) {
    var effects by mutableStateOf(PlayerEffects())
        private set

    /** Whether the playing film remembers its effects. */
    var remembered by mutableStateOf(false)
        private set

    /** The playing film. */
    var media by mutableStateOf<String?>(null)
        private set

    val supported: Set<Effect> get() = engine.supported

    /** Of [supported], what libVLC takes as it starts again. */
    val deferred: Set<Effect> get() = engine.deferred

    /** Whether a change waits for libVLC to start again (the video output, its scaler, the picture turned). */
    var waiting by mutableStateOf(false)
        private set

    private var pending: Pair<String, PlayerEffects>? = null
    private var saving: Job? = null

    /** A film started playing: the effects it remembers, else the defaults, else none. */
    fun onMediaStarted(uri: String?) {
        if (uri == media) return
        flush()
        media = uri
        scope.launch {
            val own = uri?.let { store.load(it) }
            // another film may have started meanwhile
            if (media != uri) return@launch
            remembered = own != null
            show(own ?: store.defaults() ?: PlayerEffects(), newFilm = true)
        }
    }

    fun update(change: (PlayerEffects) -> PlayerEffects) {
        val next = change(effects)
        if (next == effects) return
        if (engine.deferred.any { it.differs(effects, next) }) waiting = true
        show(next)
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

    /** "Save for this file": ticked, the film remembers its effects from now on; cleared, it forgets them. */
    fun remember(on: Boolean) {
        val uri = media ?: return
        if (on == remembered) return
        remembered = on
        saving?.cancel()
        pending = null
        val value = effects
        scope.launch { if (on) store.save(uri, value) else store.forget(uri) }
    }

    /** "Make default": films that remember no effects start with these. */
    fun makeDefault() = store.saveDefaults(effects)

    /** The player goes: what is left to save is saved, and the player's next film starts without these effects. */
    fun release() {
        flush()
        engine.apply(PlayerEffects(), release = true)
    }

    private fun show(next: PlayerEffects, newFilm: Boolean = false) {
        effects = next
        engine.apply(next, newFilm)
    }

    /** A save still waiting is done now. */
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
}
