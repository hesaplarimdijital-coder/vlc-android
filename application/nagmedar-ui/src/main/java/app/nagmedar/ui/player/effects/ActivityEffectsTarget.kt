package app.nagmedar.ui.player.effects

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.KEY_EQUALIZER_ENABLED
import org.videolan.tools.Settings
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.repository.EqualizerRepository

/**
 * The classic player doing the effects libVLC's Java API has (S-603): the picture's shape through
 * its scale types and the equaliser, a gain on top of it as its preamp.
 */
internal class ActivityEffectsTarget(private val activity: VideoPlayerActivity) : PlayerEffectsTarget {

    private var equalizing: Job? = null

    override fun setAspect(aspect: AspectChoice) {
        val player = activity.service?.mediaplayer ?: return
        player.videoScale = when (aspect) {
            AspectChoice.Auto -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectChoice.Wide -> MediaPlayer.ScaleType.SURFACE_16_9
            AspectChoice.Scope -> MediaPlayer.ScaleType.SURFACE_239_1
            AspectChoice.Classic -> MediaPlayer.ScaleType.SURFACE_4_3
            // cropped to fill: libVLC's "fill" would stretch it
            AspectChoice.Fill -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
        }
    }

    override fun setEqualizer(choice: String?, gainDb: Float) {
        // the player's own equaliser is in VLC's database: read off the main thread, the latest call wins
        equalizing?.cancel()
        equalizing = activity.lifecycleScope.launch {
            val equalizer = withContext(Dispatchers.IO) { equalizer(choice, gainDb) }
            activity.service?.setEqualizer(equalizer)
        }
    }

    private fun equalizer(choice: String?, gainDb: Float): MediaPlayer.Equalizer? {
        val base = when (choice) {
            null -> playersOwn()
            PlayerEffects.EQUALIZER_OFF -> null
            else -> EqualizerPreset.of(choice)?.let { MediaPlayer.Equalizer.createFromPreset(it.ordinal) }
        }
        if (gainDb == 0f) return base
        val equalizer = base ?: MediaPlayer.Equalizer.create().apply { setPreAmp(FLAT_PREAMP_DB) }
        equalizer.setPreAmp((equalizer.getPreAmp() + gainDb).coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB))
        return equalizer
    }

    /** VLC's equaliser setting: its current equaliser when it is on. */
    private fun playersOwn(): MediaPlayer.Equalizer? {
        if (!Settings.getInstance(activity).getBoolean(KEY_EQUALIZER_ENABLED, false)) return null
        return EqualizerRepository.getInstance(activity.applicationContext).getCurrentEqualizer(activity.applicationContext).getEqualizer()
    }

    private companion object {
        /**
         * VLC's equaliser lowers its input by 12 dB and its flat preset raises it back: a flat
         * equaliser at this preamp leaves the sound as it is.
         */
        const val FLAT_PREAMP_DB = 12f
        const val MIN_PREAMP_DB = -20f
        const val MAX_PREAMP_DB = 20f
    }
}
