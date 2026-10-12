package app.nagmedar.ui.player

import app.nagmedar.ui.player.effects.ChannelLayout
import app.nagmedar.ui.player.effects.ColorAdjust
import app.nagmedar.ui.player.effects.Deinterlace
import org.videolan.libvlc.MediaPlayer
import java.lang.reflect.Method

/**
 * Nağmedar's additions to libVLC's Java API (libvlcjni/nagmedar, S-503 … S-508), found as the app
 * runs: the published libVLC the app is built with has none of them, the dev build with Nağmedar's
 * own libVLC (the native CI job's) has them all. What is missing does nothing, and says so.
 */
object LibVlcExtensions {
    private val FLOAT = java.lang.Float.TYPE
    private val BOOLEAN = java.lang.Boolean.TYPE
    private val INT = Integer.TYPE

    private fun method(name: String, vararg types: Class<*>): Method? =
        runCatching { MediaPlayer::class.java.getMethod(name, *types) }.getOrNull()

    private val setSpuTextScale = method("setSpuTextScale", FLOAT)
    private val nextFrame = method("nextFrame")
    private val setVideoAdjust = method("setVideoAdjust", BOOLEAN, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT)
    private val setDeinterlace = method("setDeinterlace", INT, String::class.java)
    private val getAudioOutputDevices = method("getAudioOutputDevices")
    private val setAudioMixMode = method("setAudioMixMode", INT)
    private val setAudioFilters = method("setAudioFilters", String::class.java)
    private val setAudioFilterOption = method("setAudioFilterOption", String::class.java, FLOAT)
    private val setCropBorder = method("setCropBorder", INT, INT, INT, INT)
    private val setCropRatio = method("setCropRatio", INT, INT)

    /** The subtitles' size changes while a film plays (S-503, S-705). */
    val hasTextScale: Boolean get() = setSpuTextScale != null

    /** The next frame, exactly (S-504). */
    val hasNextFrame: Boolean get() = nextFrame != null

    /** The picture's colours (S-505). */
    val hasVideoAdjust: Boolean get() = setVideoAdjust != null

    /** Deinterlacing's mode (S-505). */
    val hasDeinterlace: Boolean get() = setDeinterlace != null

    /** The audio output's devices (S-508). */
    val hasAudioDevices: Boolean get() = getAudioOutputDevices != null

    /** The sound mixed down or up to a channel layout (S-505, S-508). */
    val hasMixMode: Boolean get() = setAudioMixMode != null

    /** Audio filters beside the equaliser: the compressor and the dialogue boost (S-505). */
    val hasAudioFilters: Boolean get() = setAudioFilters != null && setAudioFilterOption != null

    /** The picture cropped by pixels (S-505's auto crop). */
    val hasCrop: Boolean get() = setCropBorder != null && setCropRatio != null

    /** [scale]: 1 is libVLC's normal size, from 0,1 to 5. */
    fun setSpuTextScale(player: MediaPlayer, scale: Float): Boolean = call(setSpuTextScale, player, scale.coerceIn(MIN_SCALE, MAX_SCALE))

    fun nextFrame(player: MediaPlayer): Boolean = call(nextFrame, player)

    /** The panel's colours as libVLC's adjust filter takes them; neutral ones turn it off. */
    fun setVideoAdjust(player: MediaPlayer, adjust: ColorAdjust): Boolean {
        val (contrast, brightness, saturation) = adjustFactors(adjust)
        return call(setVideoAdjust, player, !adjust.isNeutral, contrast, brightness, adjust.hue.toFloat(), saturation, adjust.gamma)
    }

    fun setDeinterlace(player: MediaPlayer, mode: Deinterlace): Boolean {
        val (state, filter) = deinterlaceOf(mode)
        return call(setDeinterlace, player, state, filter)
    }

    /** The devices libVLC can play through: their ids and names; none without the addition. */
    fun audioOutputDevices(player: MediaPlayer): List<Pair<String, String>> {
        val flat = runCatching { getAudioOutputDevices?.invoke(player) as? Array<*> }.getOrNull() ?: return emptyList()
        return flat.toList().chunked(2).mapNotNull { pair ->
            val id = pair.getOrNull(0) as? String ?: return@mapNotNull null
            id to (pair.getOrNull(1) as? String).orEmpty()
        }
    }

    fun setChannelLayout(player: MediaPlayer, layout: ChannelLayout): Boolean = call(setAudioMixMode, player, mixModeOf(layout))

    /** The audio filters, "compressor:nagdialogue"; empty for none. The equaliser stays. */
    fun setAudioFilters(player: MediaPlayer, chain: String): Boolean = call(setAudioFilters, player, chain)

    /** A filter's option, changed while the film plays. */
    fun setAudioFilterOption(player: MediaPlayer, name: String, value: Float): Boolean = call(setAudioFilterOption, player, name, value)

    /** The picture cropped by this many of its pixels on each side; all zero shows it whole. */
    fun setCropBorder(player: MediaPlayer, left: Int, right: Int, top: Int, bottom: Int): Boolean =
        call(setCropBorder, player, left.coerceAtLeast(0), right.coerceAtLeast(0), top.coerceAtLeast(0), bottom.coerceAtLeast(0))

    fun clearCrop(player: MediaPlayer): Boolean = call(setCropRatio, player, 0, 0)

    /** −100…100 as libVLC's 0…2 for contrast, brightness and saturation, 1 leaving them. */
    fun adjustFactors(adjust: ColorAdjust): Triple<Float, Float, Float> =
        Triple(1f + adjust.contrast / 100f, 1f + adjust.brightness / 100f, 1f + adjust.saturation / 100f)

    /** libVLC's deinterlacing: −1 automatic, 0 off, 1 on with its filter. */
    fun deinterlaceOf(mode: Deinterlace): Pair<Int, String?> = when (mode) {
        Deinterlace.Auto -> -1 to null
        Deinterlace.Off -> 0 to null
        else -> 1 to mode.key
    }

    /** libVLC's mix modes: as the film has it, stereo, binaural, 5.1, 7.1. */
    fun mixModeOf(layout: ChannelLayout): Int = when (layout) {
        ChannelLayout.Auto -> MIX_UNSET
        ChannelLayout.Stereo -> MIX_STEREO
        ChannelLayout.Binaural -> MIX_BINAURAL
        ChannelLayout.Surround51 -> MIX_5_1
        ChannelLayout.Surround71 -> MIX_7_1
    }

    private fun call(method: Method?, player: MediaPlayer, vararg args: Any?): Boolean {
        if (method == null || player.isReleased) return false
        return runCatching { method.invoke(player, *args) }.map { it != false }.getOrDefault(false)
    }

    private const val MIN_SCALE = 0.1f
    private const val MAX_SCALE = 5f
    private const val MIX_UNSET = 0
    private const val MIX_STEREO = 1
    private const val MIX_BINAURAL = 2
    private const val MIX_5_1 = 4
    private const val MIX_7_1 = 5
}
