package app.nagmedar.ui.player.equalizer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.resources.VLCInstance
import org.videolan.tools.KEY_CURRENT_EQUALIZER_ID
import org.videolan.tools.KEY_EQUALIZER_ENABLED
import org.videolan.tools.Settings
import org.videolan.vlc.mediadb.models.EqualizerBand
import org.videolan.vlc.mediadb.models.EqualizerEntry
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import org.videolan.vlc.repository.EqualizerRepository
import java.util.concurrent.Executors

/**
 * VLC's equalisers where VLC keeps them, so that what the player's window sets survives and the
 * player starts every film with it: its database (EqualizerRepository: libVLC's presets and the
 * user's own) and its settings (the one chosen, on or off), as VLC's own equaliser dialog kept
 * them. Read and written on one thread of their own, in the order asked; a write asked from the
 * window is done even when the window has closed.
 */
internal class VlcEqualizerStore(context: Context) : PlayerEqualizerStore {
    private val app = context.applicationContext
    private val repository get() = EqualizerRepository.getInstance(app)
    private val settings get() = Settings.getInstance(app)

    override suspend fun load(): StoredEqualizers = withContext(thread) {
        // libVLC's natives: its presets and its bands' frequencies
        VLCInstance.getInstance(app)
        var presets = repository.getDefaultEqualizers()
        var own = repository.getCustomEqualizers()
        if (presets.isEmpty() && own.isEmpty()) {
            seed()
            presets = repository.getDefaultEqualizers()
            own = repository.getCustomEqualizers()
        }
        val enabled = settings.getBoolean(KEY_EQUALIZER_ENABLED, false)
        val chosen = settings.getLong(KEY_CURRENT_EQUALIZER_ID, FIRST_ID)
        val all = presets + own
        // the one chosen; when it is gone, VLC's first preset that its settings did not hide, as VLC's repository takes it
        val currentId = all.firstOrNull { it.equalizerEntry.id == chosen }?.equalizerEntry?.id
            ?: presets.filter { !it.equalizerEntry.isDisabled }.minByOrNull { it.equalizerEntry.presetIndex }?.equalizerEntry?.id
            ?: presets.minByOrNull { it.equalizerEntry.presetIndex }?.equalizerEntry?.id
            ?: own.firstOrNull()?.equalizerEntry?.id
            ?: 0L
        if (currentId != chosen && currentId != 0L) settings.edit().putLong(KEY_CURRENT_EQUALIZER_ID, currentId).apply()
        val bands = MediaPlayer.Equalizer.getBandCount()
        StoredEqualizers(
            // the presets VLC's settings hid stay hidden, unless chosen
            equalizers = presets.filter { !it.equalizerEntry.isDisabled || it.equalizerEntry.id == currentId }
                .sortedBy { it.equalizerEntry.presetIndex }
                .map { it.toModel(bands) } +
                own.sortedBy { it.equalizerEntry.id }.map { it.toModel(bands) },
            currentId = currentId,
            enabled = enabled,
            names = all.map { it.equalizerEntry.name }.toSet(),
            frequencies = (0 until bands).map { MediaPlayer.Equalizer.getBandFrequency(it) },
        )
    }

    override suspend fun add(equalizer: VlcEqualizer): Long = withContext(thread) {
        repository.addOrUpdateEqualizerWithBands(app, equalizer.copy(id = 0).toEntity())
    }

    override fun save(equalizer: VlcEqualizer) {
        if (equalizer.id <= 0) return
        later { repository.addOrUpdateEqualizerWithBands(app, equalizer.toEntity()) }
    }

    override fun delete(equalizer: VlcEqualizer) {
        if (!equalizer.own || equalizer.id <= 0) return
        later { repository.delete(equalizer.toEntity().equalizerEntry) }
    }

    override fun setCurrent(id: Long) = later { settings.edit().putLong(KEY_CURRENT_EQUALIZER_ID, id).apply() }

    override fun setEnabled(on: Boolean) = later { settings.edit().putBoolean(KEY_EQUALIZER_ENABLED, on).apply() }

    private fun later(write: () -> Unit) {
        writes.launch { runCatching(write) }
    }

    /** libVLC's presets into VLC's database, as VLC's first start does; for a database that has none yet. */
    private fun seed() {
        val bands = MediaPlayer.Equalizer.getBandCount()
        for (index in 0 until MediaPlayer.Equalizer.getPresetCount()) {
            val preset = MediaPlayer.Equalizer.createFromPreset(index)
            val entry = EqualizerEntry(MediaPlayer.Equalizer.getPresetName(index), preset.preAmp, index)
            repository.addOrUpdateEqualizerWithBands(app, EqualizerWithBands(entry, (0 until bands).map { EqualizerBand(it, preset.getAmp(it)) }))
        }
    }

    companion object {
        /** The id VLC takes for its first preset before one is chosen. */
        private const val FIRST_ID = 1L

        /** The thread of VLC's equalisers: what the player reads of them comes after what was written. */
        internal val thread = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "nagmedar-equalizer").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        /** The writes, outliving the window that asked for them. */
        private val writes = CoroutineScope(SupervisorJob() + thread)
    }
}

/** [this] as libVLC plays it. */
internal fun VlcEqualizer.toLibVlc(): MediaPlayer.Equalizer = MediaPlayer.Equalizer.create().also { equalizer ->
    equalizer.setPreAmp(preampDb)
    bandsDb.forEachIndexed { band, db -> equalizer.setAmp(band, db) }
}

private fun EqualizerWithBands.toModel(bands: Int): VlcEqualizer {
    val values = this.bands.associate { it.index to it.bandValue }
    return VlcEqualizer(
        id = equalizerEntry.id,
        name = equalizerEntry.name,
        presetIndex = equalizerEntry.presetIndex,
        preampDb = equalizerEntry.preamp,
        bandsDb = (0 until maxOf(bands, this.bands.size)).map { values[it] ?: 0f },
    )
}

private fun VlcEqualizer.toEntity(): EqualizerWithBands = EqualizerWithBands(
    EqualizerEntry(name, preampDb, presetIndex, isDisabled = false).apply { id = this@toEntity.id },
    bandsDb.mapIndexed { band, db -> EqualizerBand(band, db) },
)
