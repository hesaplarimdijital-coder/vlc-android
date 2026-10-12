package app.nagmedar.ui.player.effects

import android.content.SharedPreferences
import app.nagmedar.ui.player.LibVlcExtensions
import app.nagmedar.ui.player.subtitles.LibVlcRestart
import org.videolan.libvlc.MediaPlayer
import org.videolan.resources.NagmedarVlcOptions
import org.videolan.tools.KEY_NAG_SCALER
import org.videolan.tools.KEY_NAG_TONE_MAPPING
import org.videolan.tools.KEY_NAG_TRANSFORM
import org.videolan.tools.KEY_OPENGL

/** The picture's black bars found and cut away (S-505's auto crop): on or off, for the playing film. */
fun interface AutoCropTarget {
    fun setAutoCrop(enabled: Boolean)
}

/**
 * The effects libVLC does (S-505, S-506), through Nağmedar's additions to it
 * ([LibVlcExtensions]) and its options. While a film plays: the colours (the adjust filter),
 * deinterlacing, the channel layout (the mix mode), night mode (the compressor), the dialogue
 * boost (the nagdialogue filter) and the auto crop. As libVLC starts again ([LibVlcRestart]):
 * the video output and its tone mapping and scaler, and the picture turned, which are VLC's
 * settings ([NagmedarVlcOptions]). What this build's libVLC lacks is left out of [supported].
 */
class LibVlcEffects(
    private val player: () -> MediaPlayer?,
    private val prefs: SharedPreferences,
    private val autoCrop: AutoCropTarget? = null,
) : NativeEffects {

    override val supported: Set<Effect> = buildSet {
        if (LibVlcExtensions.hasVideoAdjust) add(Effect.ColorAdjust)
        if (LibVlcExtensions.hasDeinterlace) add(Effect.Deinterlace)
        if (LibVlcExtensions.hasMixMode) add(Effect.ChannelLayout)
        if (LibVlcExtensions.hasAudioFilters) {
            add(Effect.NightMode)
            add(Effect.Dialogue)
        }
        if (LibVlcExtensions.hasCrop && autoCrop != null) add(Effect.AutoCrop)
        addAll(DEFERRED)
    }

    override val deferred: Set<Effect> = DEFERRED

    private var night = false
    private var dialogueDb = 0

    override fun setColorAdjust(adjust: ColorAdjust) {
        player()?.let { LibVlcExtensions.setVideoAdjust(it, adjust) }
    }

    override fun setAutoCrop(enabled: Boolean) {
        autoCrop?.setAutoCrop(enabled)
    }

    override fun setTransform(transform: VideoTransform) = keep(KEY_NAG_TRANSFORM, vlcTransform(transform))

    override fun setHdr(output: HdrOutput, toneMapping: ToneMapping, scaler: Scaler) {
        keep(KEY_OPENGL, vout(output).toString())
        keep(KEY_NAG_TONE_MAPPING, toneMapping.key)
        keep(KEY_NAG_SCALER, scaler.key)
    }

    override fun setDeinterlace(mode: Deinterlace) {
        player()?.let { LibVlcExtensions.setDeinterlace(it, mode) }
    }

    override fun setDebanding(enabled: Boolean) = Unit

    override fun setNightMode(enabled: Boolean) {
        night = enabled
        applyAudioFilters()
    }

    override fun setDialogueBoost(db: Int) {
        dialogueDb = db.coerceIn(0, PlayerEffects.DIALOGUE_MAX_DB)
        applyAudioFilters()
    }

    override fun setChannelLayout(layout: ChannelLayout) {
        player()?.let { LibVlcExtensions.setChannelLayout(it, layout) }
    }

    /** The compressor and the dialogue boost, their options set before they open with them. */
    private fun applyAudioFilters() {
        val player = player() ?: return
        if (night) NIGHT_MODE.forEach { (name, value) -> LibVlcExtensions.setAudioFilterOption(player, name, value) }
        if (dialogueDb > 0) LibVlcExtensions.setAudioFilterOption(player, DIALOGUE_GAIN, dialogueDb.toFloat())
        LibVlcExtensions.setAudioFilters(player, audioFilterChain(night, dialogueDb))
    }

    /** A VLC setting for libVLC's next start; it starts again when it is idle, when the setting is new. */
    private fun keep(key: String, value: String?) {
        if (prefs.getString(key, null) == value) return
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        LibVlcRestart.pending = true
    }

    companion object {
        /** What libVLC takes as it starts. */
        val DEFERRED: Set<Effect> = setOf(Effect.HdrOutput, Effect.ToneMapping, Effect.Scaler, Effect.Transform)

        const val DIALOGUE_GAIN = "nagdialogue-gain"

        /** Night mode: the loud brought down by 4 to 1 above −24 dB, the quiet raised by 8 dB. */
        val NIGHT_MODE: List<Pair<String, Float>> = listOf(
            "compressor-rms-peak" to 0.2f,
            "compressor-attack" to 25f,
            "compressor-release" to 300f,
            "compressor-threshold" to -24f,
            "compressor-ratio" to 4f,
            "compressor-knee" to 5f,
            "compressor-makeup-gain" to 8f,
        )

        /** "compressor:nagdialogue": the filters on, in the order the sound passes them; empty for none. */
        fun audioFilterChain(nightMode: Boolean, dialogueDb: Int): String =
            listOfNotNull("compressor".takeIf { nightMode }, "nagdialogue".takeIf { dialogueDb > 0 }).joinToString(":")

        /** VLC's "opengl" setting for an HDR output choice: its own choice, Android's output (HDR passes), OpenGL (tone mapped). */
        fun vout(output: HdrOutput): Int = when (output) {
            HdrOutput.Auto -> NagmedarVlcOptions.VOUT_AUTO
            HdrOutput.Passthrough -> NagmedarVlcOptions.VOUT_ANDROID
            HdrOutput.ToneMap -> NagmedarVlcOptions.VOUT_OPENGL
        }

        /**
         * VLC's transform filter's type for the picture turned clockwise by [VideoTransform.rotation]
         * and then mirrored; null for the picture as it is. The eight ways a picture can lie are
         * told apart by where they send the x and y axes.
         */
        fun vlcTransform(transform: VideoTransform): String? {
            // the axes after the turn: (x, y) → columns of the matrix, y down
            var ax = 1 to 0
            var ay = 0 to 1
            repeat((transform.rotation / 90).mod(4)) {
                // a quarter turn clockwise, y down: (x, y) → (−y, x)
                val (xx, xy) = ax
                val (yx, yy) = ay
                ax = -xy to xx
                ay = -yy to yx
            }
            if (transform.flipHorizontal) {
                ax = -ax.first to ax.second
                ay = -ay.first to ay.second
            }
            if (transform.flipVertical) {
                ax = ax.first to -ax.second
                ay = ay.first to -ay.second
            }
            return when (ax to ay) {
                (1 to 0) to (0 to 1) -> null
                (0 to 1) to (-1 to 0) -> "90"
                (-1 to 0) to (0 to -1) -> "180"
                (0 to -1) to (1 to 0) -> "270"
                (-1 to 0) to (0 to 1) -> "hflip"
                (1 to 0) to (0 to -1) -> "vflip"
                (0 to 1) to (1 to 0) -> "transpose"
                else -> "antitranspose"
            }
        }
    }
}
