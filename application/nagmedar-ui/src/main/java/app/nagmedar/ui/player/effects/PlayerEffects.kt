package app.nagmedar.ui.player.effects

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.nagmedar.data.MediaSettings
import app.nagmedar.ui.R
import org.json.JSONObject
import kotlin.math.log10

/** One control of the effects panel (S-603): what the player can or cannot do yet ([EffectsEngine.supported]). */
enum class Effect {
    ColorAdjust, Aspect, AutoCrop, Transform, HdrOutput, ToneMapping, Scaler, Deinterlace, Debanding,
    NightMode, Dialogue, Gain, ChannelLayout, Equalizer,
}

/**
 * The picture's colours, as VLC's "adjust" filter takes them: brightness, contrast and saturation
 * from −100 to 100 (0 leaves them), the hue turned by −180° to 180°, gamma from 0.5 to 1.5.
 */
@Immutable
data class ColorAdjust(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val hue: Int = 0,
    val gamma: Float = 1f,
) {
    val isNeutral: Boolean get() = this == ColorAdjust()

    companion object {
        const val MIN = -100
        const val MAX = 100
        const val HUE_MIN = -180
        const val HUE_MAX = 180
        const val GAMMA_MIN = 0.5f
        const val GAMMA_MAX = 1.5f
    }
}

/** The picture's shape: as the file says, a ratio forced on it, or cropped to fill the screen. */
enum class AspectChoice(override val key: String, override val label: Int) : EffectOption {
    Auto("auto", R.string.nag_fx_auto),
    Wide("16:9", R.string.nag_fx_aspect_16_9),
    Scope("2.39:1", R.string.nag_fx_aspect_239),
    Classic("4:3", R.string.nag_fx_aspect_4_3),
    Fill("fill", R.string.nag_fx_aspect_fill),
}

/** The picture turned by quarter turns and mirrored. */
@Immutable
data class VideoTransform(val rotation: Int = 0, val flipHorizontal: Boolean = false, val flipVertical: Boolean = false) {
    val isNeutral: Boolean get() = this == VideoTransform()

    fun rotatedLeft() = copy(rotation = (rotation + 270) % 360)

    fun rotatedRight() = copy(rotation = (rotation + 90) % 360)

    fun flippedHorizontally() = copy(flipHorizontal = !flipHorizontal)

    fun flippedVertically() = copy(flipVertical = !flipVertical)
}

/** An option of one of the panel's lists: kept by [key] in the per-file settings, shown by [label]. */
interface EffectOption {
    val key: String

    @get:StringRes
    val label: Int
}

/** What an HDR film does on this screen: whatever suits it, passed to the screen as HDR, or tone-mapped to SDR. */
enum class HdrOutput(override val key: String, override val label: Int) : EffectOption {
    Auto("auto", R.string.nag_fx_auto),
    Passthrough("passthrough", R.string.nag_fx_hdr_passthrough),
    ToneMap("tonemap", R.string.nag_fx_hdr_tonemap),
}

/** libplacebo's tone-mapping curves; none when the screen takes the HDR as it is. */
enum class ToneMapping(override val key: String, override val label: Int) : EffectOption {
    Auto("auto", R.string.nag_fx_auto),
    Bt2390("bt2390", R.string.nag_fx_tone_bt2390),
    Hable("hable", R.string.nag_fx_tone_hable),
    Reinhard("reinhard", R.string.nag_fx_tone_reinhard),
    Mobius("mobius", R.string.nag_fx_tone_mobius),
}

/** How the picture is scaled to the screen. */
enum class Scaler(override val key: String, override val label: Int) : EffectOption {
    Auto("auto", R.string.nag_fx_auto),
    Bilinear("bilinear", R.string.nag_fx_scaler_bilinear),
    Spline36("spline36", R.string.nag_fx_scaler_spline36),
    EwaLanczos("ewa_lanczos", R.string.nag_fx_scaler_ewa_lanczos),
}

/** VLC's deinterlacing modes. */
enum class Deinterlace(override val key: String, override val label: Int) : EffectOption {
    Auto("auto", R.string.nag_fx_auto),
    Off("off", R.string.nag_fx_off),
    Blend("blend", R.string.nag_fx_deinterlace_blend),
    Bob("bob", R.string.nag_fx_deinterlace_bob),
    Yadif("yadif", R.string.nag_fx_deinterlace_yadif),
    Yadif2x("yadif2x", R.string.nag_fx_deinterlace_yadif2x),
}

/** How the sound's channels reach the speakers: as the device asks, or mixed down to these. */
enum class ChannelLayout(override val key: String, override val label: Int) : EffectOption {
    Auto("auto", R.string.nag_fx_auto),
    Stereo("stereo", R.string.nag_fx_channels_stereo),
    Binaural("binaural", R.string.nag_fx_channels_binaural),
    Surround51("5.1", R.string.nag_fx_channels_51),
    Surround71("7.1", R.string.nag_fx_channels_71),
}

/**
 * VLC's equaliser presets, in libVLC's order (an index into them is the preset libVLC makes), by
 * VLC's own names for them.
 */
enum class EqualizerPreset(val key: String, @StringRes val label: Int) {
    Flat("flat", R.string.nag_eq_flat),
    Classical("classical", R.string.nag_eq_classical),
    Club("club", R.string.nag_eq_club),
    Dance("dance", R.string.nag_eq_dance),
    FullBass("fullbass", R.string.nag_eq_full_bass),
    FullBassTreble("fullbasstreble", R.string.nag_eq_full_bass_treble),
    FullTreble("fulltreble", R.string.nag_eq_full_treble),
    Headphones("headphones", R.string.nag_eq_headphones),
    LargeHall("largehall", R.string.nag_eq_large_hall),
    Live("live", R.string.nag_eq_live),
    Party("party", R.string.nag_eq_party),
    Pop("pop", R.string.nag_eq_pop),
    Reggae("reggae", R.string.nag_eq_reggae),
    Rock("rock", R.string.nag_eq_rock),
    Ska("ska", R.string.nag_eq_ska),
    Soft("soft", R.string.nag_eq_soft),
    SoftRock("softrock", R.string.nag_eq_soft_rock),
    Techno("techno", R.string.nag_eq_techno),
    ;

    companion object {
        fun of(key: String?): EqualizerPreset? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Everything the effects panel sets (S-603). The defaults are the film as it is: nothing is
 * changed, and the equaliser is the player's own ([equalizer] null).
 */
@Immutable
data class PlayerEffects(
    val color: ColorAdjust = ColorAdjust(),
    val aspect: AspectChoice = AspectChoice.Auto,
    val autoCrop: Boolean = false,
    val transform: VideoTransform = VideoTransform(),
    val hdrOutput: HdrOutput = HdrOutput.Auto,
    val toneMapping: ToneMapping = ToneMapping.Auto,
    val scaler: Scaler = Scaler.Auto,
    val deinterlace: Deinterlace = Deinterlace.Auto,
    val debanding: Boolean = false,
    val nightMode: Boolean = false,
    /** The dialogue (the centre channel) raised by 0 to 12 dB. */
    val dialogueDb: Int = 0,
    /** The sound's level, 0 to 200 %, on top of the volume. */
    val gainPercent: Int = 100,
    val channelLayout: ChannelLayout = ChannelLayout.Auto,
    /** [EQUALIZER_OFF], an [EqualizerPreset]'s key, or null for the player's own equaliser. */
    val equalizer: String? = null,
) {
    /** The gain in decibels: 100 % is 0 dB, silence as low as the equaliser goes. */
    val gainDb: Float get() = if (gainPercent <= 0) MIN_GAIN_DB else (20 * log10(gainPercent / 100f)).coerceAtLeast(MIN_GAIN_DB)

    companion object {
        const val EQUALIZER_OFF = "off"
        const val DIALOGUE_MAX_DB = 12
        const val GAIN_MAX_PERCENT = 200
        const val MIN_GAIN_DB = -32f
    }
}

/** The effects in the per-file settings' columns ([MediaSettings]); null: not remembered. */
data class EffectColumns(val aspectRatio: String?, val deinterlace: String?, val equalizerPreset: String?, val effects: String?)

/**
 * The effects in the per-file settings (S-603): the aspect ratio, the deinterlacing and the
 * equaliser in their own columns, the rest as JSON; and the defaults ("Make default") as one JSON.
 * Unknown or missing values read as the film's own.
 */
object EffectsCodec {

    /** The effects remembered in [settings], or null when it remembers none. */
    fun fromSettings(settings: MediaSettings): PlayerEffects? {
        if (settings.effects == null && settings.aspectRatio == null && settings.deinterlace == null && settings.equalizerPreset == null) return null
        val json = settings.effects?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        return read(json).copy(
            aspect = option(AspectChoice.entries, settings.aspectRatio, AspectChoice.Auto),
            deinterlace = option(Deinterlace.entries, settings.deinterlace, Deinterlace.Auto),
            equalizer = equalizer(settings.equalizerPreset),
        )
    }

    /** The columns remembering [effects]. */
    fun columns(effects: PlayerEffects) = EffectColumns(
        aspectRatio = effects.aspect.key,
        deinterlace = effects.deinterlace.key,
        equalizerPreset = effects.equalizer,
        effects = write(effects).toString(),
    )

    /** The defaults as one JSON object, the aspect ratio, the deinterlacing and the equaliser with them. */
    fun encodeDefaults(effects: PlayerEffects): String = write(effects)
        .put(KEY_ASPECT, effects.aspect.key)
        .put(KEY_DEINTERLACE, effects.deinterlace.key)
        .apply { effects.equalizer?.let { put(KEY_EQUALIZER, it) } }
        .toString()

    fun decodeDefaults(text: String?): PlayerEffects? {
        val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return read(json).copy(
            aspect = option(AspectChoice.entries, json.optString(KEY_ASPECT), AspectChoice.Auto),
            deinterlace = option(Deinterlace.entries, json.optString(KEY_DEINTERLACE), Deinterlace.Auto),
            equalizer = equalizer(json.optString(KEY_EQUALIZER)),
        )
    }

    private fun equalizer(key: String?): String? = key?.takeIf { it == PlayerEffects.EQUALIZER_OFF || EqualizerPreset.of(it) != null }

    private fun write(effects: PlayerEffects): JSONObject = JSONObject()
        .put(KEY_VERSION, 1)
        .put("brightness", effects.color.brightness)
        .put("contrast", effects.color.contrast)
        .put("saturation", effects.color.saturation)
        .put("hue", effects.color.hue)
        .put("gamma", effects.color.gamma.toDouble())
        .put("autoCrop", effects.autoCrop)
        .put("rotation", effects.transform.rotation)
        .put("flipH", effects.transform.flipHorizontal)
        .put("flipV", effects.transform.flipVertical)
        .put("hdr", effects.hdrOutput.key)
        .put("toneMapping", effects.toneMapping.key)
        .put("scaler", effects.scaler.key)
        .put("debanding", effects.debanding)
        .put("nightMode", effects.nightMode)
        .put("dialogueDb", effects.dialogueDb)
        .put("gain", effects.gainPercent)
        .put("channels", effects.channelLayout.key)

    private fun read(json: JSONObject): PlayerEffects {
        val neutral = PlayerEffects()
        return PlayerEffects(
            color = ColorAdjust(
                brightness = json.optInt("brightness", 0).coerceIn(ColorAdjust.MIN, ColorAdjust.MAX),
                contrast = json.optInt("contrast", 0).coerceIn(ColorAdjust.MIN, ColorAdjust.MAX),
                saturation = json.optInt("saturation", 0).coerceIn(ColorAdjust.MIN, ColorAdjust.MAX),
                hue = json.optInt("hue", 0).coerceIn(ColorAdjust.HUE_MIN, ColorAdjust.HUE_MAX),
                gamma = json.optDouble("gamma", 1.0).toFloat().takeIf { !it.isNaN() }?.coerceIn(ColorAdjust.GAMMA_MIN, ColorAdjust.GAMMA_MAX) ?: 1f,
            ),
            autoCrop = json.optBoolean("autoCrop", false),
            transform = VideoTransform(
                rotation = json.optInt("rotation", 0).takeIf { it in setOf(0, 90, 180, 270) } ?: 0,
                flipHorizontal = json.optBoolean("flipH", false),
                flipVertical = json.optBoolean("flipV", false),
            ),
            hdrOutput = option(HdrOutput.entries, json.optString("hdr"), neutral.hdrOutput),
            toneMapping = option(ToneMapping.entries, json.optString("toneMapping"), neutral.toneMapping),
            scaler = option(Scaler.entries, json.optString("scaler"), neutral.scaler),
            debanding = json.optBoolean("debanding", false),
            nightMode = json.optBoolean("nightMode", false),
            dialogueDb = json.optInt("dialogueDb", 0).coerceIn(0, PlayerEffects.DIALOGUE_MAX_DB),
            gainPercent = json.optInt("gain", 100).coerceIn(0, PlayerEffects.GAIN_MAX_PERCENT),
            channelLayout = option(ChannelLayout.entries, json.optString("channels"), neutral.channelLayout),
        )
    }

    private fun <T : EffectOption> option(entries: List<T>, key: String?, default: T): T = entries.firstOrNull { it.key == key } ?: default

    private const val KEY_VERSION = "v"
    private const val KEY_ASPECT = "aspect"
    private const val KEY_DEINTERLACE = "deinterlace"
    private const val KEY_EQUALIZER = "equalizer"
}
