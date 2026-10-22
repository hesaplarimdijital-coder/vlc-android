package app.nagmedar.ui.player.equalizer

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One of VLC's equalisers, as VLC keeps them (EqualizerRepository): one of libVLC's presets
 * ([presetIndex], in libVLC's order) or the user's own, its preamp and its bands in dB. [id] 0:
 * not kept yet.
 */
@Immutable
data class VlcEqualizer(
    val id: Long,
    /** VLC's name for it: a preset's English name, or the user's own. */
    val name: String,
    /** libVLC's preset index; [OWN] for the user's own. */
    val presetIndex: Int,
    val preampDb: Float,
    val bandsDb: List<Float>,
) {
    /** The user's own: it can be changed, renamed and deleted; libVLC's presets stay as they are. */
    val own: Boolean get() = presetIndex < 0

    companion object {
        /** VLC's preset index of the user's own equalisers. */
        const val OWN = -1
    }
}

/** What VLC keeps of its equaliser: its equalisers, the one chosen, whether it is on. */
@Immutable
data class StoredEqualizers(
    /** libVLC's presets that VLC's settings did not hide, in libVLC's order, then the user's own. */
    val equalizers: List<VlcEqualizer>,
    val currentId: Long,
    val enabled: Boolean,
    /** Every name VLC has, its hidden presets' too: a name is VLC's key for an equaliser. */
    val names: Set<String>,
    /** libVLC's bands: their centres, in Hz. */
    val frequencies: List<Float>,
)

/** Where VLC keeps its equalisers: its database and its settings, which its player reads as a film starts. */
interface PlayerEqualizerStore {
    suspend fun load(): StoredEqualizers

    /** [equalizer] kept as a new one of the user's; its id. */
    suspend fun add(equalizer: VlcEqualizer): Long

    /** [equalizer], kept already, as it is now: after what was asked before, done even when the window has closed. */
    fun save(equalizer: VlcEqualizer)

    fun delete(equalizer: VlcEqualizer)

    fun setCurrent(id: Long)

    fun setEnabled(on: Boolean)
}

/** Where the player's equaliser window sends VLC's setting: the playing film, and what that film has of its own. */
interface PlayerEqualizerTarget {
    /**
     * The film's own equaliser from the effects tab ("off" or one of VLC's presets, by its key);
     * null while it follows the player's setting. Compose state where the film has effects.
     */
    val filmChoice: String?

    /** The film follows the player's setting again. */
    fun followPlayer()

    /** The player's setting is [equalizer] now (null: off): a film that follows it hears it at once. */
    fun apply(equalizer: VlcEqualizer?)
}

/** The player's equaliser window. */
@Immutable
data class PlayerEqualizerState(
    val loading: Boolean = true,
    /** VLC's equalisers could not be read. */
    val failed: Boolean = false,
    /** VLC's equaliser is on. */
    val enabled: Boolean = false,
    val equalizers: List<VlcEqualizer> = emptyList(),
    val current: VlcEqualizer? = null,
    val frequencies: List<Float> = emptyList(),
    /** The name typed for the user's own equaliser, as it is typed. */
    val name: String = "",
    /** The name typed is blank or another equaliser's: it was not taken. */
    val nameTaken: Boolean = false,
    /** A copy of a preset is being made. */
    val copying: Boolean = false,
) {
    val presets: List<VlcEqualizer> get() = equalizers.filter { !it.own }

    val own: List<VlcEqualizer> get() = equalizers.filter { it.own }

    /** The bands and the preamp can be moved: the user's own equaliser, while the equaliser is on. */
    val editable: Boolean get() = enabled && current?.own == true
}

/**
 * The player's equaliser: VLC's, which libVLC plays films, radio, podcasts and streams through
 * (the music engine has its own DSP chain). It is on or off, one of libVLC's presets or one of
 * the user's own, with its preamp and its bands. Every change is heard at once
 * ([PlayerEqualizerTarget.apply]) and kept where VLC keeps it ([PlayerEqualizerStore]), so the
 * next film starts with it. libVLC's presets stay as they are: [copy] makes one the user's own
 * to change, as VLC's own dialog did. Compose state.
 */
@Stable
class PlayerEqualizerController(
    private val store: PlayerEqualizerStore,
    private val target: PlayerEqualizerTarget,
    private val scope: CoroutineScope,
    /** A copy's name, in the interface's language: "Rock kopyası". */
    private val copyName: (VlcEqualizer) -> String,
) {
    var state by mutableStateOf(PlayerEqualizerState())
        private set

    /** VLC's names, its hidden presets' too: a new name is none of them. */
    private var names: Set<String> = emptySet()

    /** VLC's equalisers read, as the window opens. */
    fun load() {
        scope.launch {
            val stored = try {
                store.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (stored == null) {
                state = PlayerEqualizerState(loading = false, failed = true)
                return@launch
            }
            names = stored.names
            val current = stored.equalizers.firstOrNull { it.id == stored.currentId } ?: stored.equalizers.firstOrNull()
            state = PlayerEqualizerState(
                loading = false,
                enabled = stored.enabled,
                equalizers = stored.equalizers,
                current = current,
                frequencies = stored.frequencies,
                name = current?.name.orEmpty(),
            )
        }
    }

    fun setEnabled(on: Boolean) {
        val current = state
        if (current.loading || current.failed || on == current.enabled) return
        state = current.copy(enabled = on)
        store.setEnabled(on)
        hear()
    }

    /** The equaliser [id] chosen; the equaliser goes on with it. */
    fun choose(id: Long) {
        val current = state
        val chosen = current.equalizers.firstOrNull { it.id == id } ?: return
        state = current.copy(current = chosen, enabled = true, name = chosen.name, nameTaken = false)
        store.setCurrent(id)
        if (!current.enabled) store.setEnabled(true)
        hear()
    }

    fun setBand(index: Int, db: Float) = edit { eq ->
        if (index !in eq.bandsDb.indices) eq else eq.copy(bandsDb = eq.bandsDb.mapIndexed { i, value -> if (i == index) db else value })
    }

    fun setPreamp(db: Float) = edit { it.copy(preampDb = db) }

    /** The equaliser chosen made the user's own, to change: a copy of it, chosen, the equaliser on. */
    fun copy() {
        val current = state
        val source = current.current ?: return
        if (current.copying) return
        val name = uniqueName(copyName(source).trim().ifEmpty { source.name })
        val copy = source.copy(id = 0, name = name, presetIndex = VlcEqualizer.OWN)
        names = names + name
        state = current.copy(copying = true)
        scope.launch {
            val id = try {
                store.add(copy)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0L
            }
            if (id <= 0) {
                names = names - name
                state = state.copy(copying = false)
                return@launch
            }
            val made = copy.copy(id = id)
            state = state.copy(copying = false, equalizers = state.equalizers + made, current = made, enabled = true, name = name, nameTaken = false)
            store.setCurrent(id)
            store.setEnabled(true)
            hear()
        }
    }

    /** The name typed for the user's own equaliser: it is its name once it is not blank and no other equaliser's. */
    fun rename(name: String) {
        val current = state
        val equalizer = current.current?.takeIf { it.own } ?: return
        val trimmed = name.trim()
        val free = trimmed.isNotEmpty() && (trimmed == equalizer.name || trimmed !in names)
        state = current.copy(name = name, nameTaken = !free)
        if (!free || trimmed == equalizer.name) return
        val renamed = equalizer.copy(name = trimmed)
        names = names - equalizer.name + trimmed
        state = state.copy(current = renamed, equalizers = state.equalizers.map { if (it.id == renamed.id) renamed else it })
        store.save(renamed)
    }

    /** The user's own equaliser deleted; libVLC's first preset takes its place. */
    fun delete() {
        val current = state
        val equalizer = current.current?.takeIf { it.own } ?: return
        val left = current.equalizers.filter { it.id != equalizer.id }
        val next = left.firstOrNull { !it.own } ?: left.firstOrNull()
        names = names - equalizer.name
        state = current.copy(equalizers = left, current = next, name = next?.name.orEmpty(), nameTaken = false)
        store.delete(equalizer)
        if (next != null) store.setCurrent(next.id)
        hear()
    }

    /** The film's own equaliser from the effects tab left: it plays with the player's setting again. */
    fun followPlayer() {
        target.followPlayer()
        if (!state.loading && !state.failed) hear()
    }

    private fun edit(change: (VlcEqualizer) -> VlcEqualizer) {
        val current = state
        val equalizer = current.current ?: return
        if (!current.editable) return
        val next = change(equalizer)
        if (next == equalizer) return
        state = current.copy(current = next, equalizers = current.equalizers.map { if (it.id == next.id) next else it })
        store.save(next)
        hear()
    }

    /** What the film hears: the equaliser chosen while it is on, none while it is off. */
    private fun hear() {
        val current = state
        target.apply(if (current.enabled) current.current else null)
    }

    /** [base], or "[base] 2", "[base] 3"… when an equaliser has that name already. */
    private fun uniqueName(base: String): String {
        if (base !in names) return base
        var n = 2
        while ("$base $n" in names) n++
        return "$base $n"
    }
}
