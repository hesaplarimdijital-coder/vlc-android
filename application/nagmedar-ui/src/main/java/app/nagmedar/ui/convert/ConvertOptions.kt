package app.nagmedar.ui.convert

import android.content.SharedPreferences
import app.nagmedar.audio.convert.ConvertCodec
import app.nagmedar.audio.convert.ConvertSettings
import java.util.Locale

/**
 * S-1010: the converter's formats and qualities. Opus at 128, 160 or 192 kbps (VBR); MP3 at
 * LAME's best VBR quality (V0, about 245 kbps) or 320 kbps; AAC at 256 kbps, where FFmpeg's
 * encoder is at its best.
 */
enum class ConvertPreset(val codec: ConvertCodec, val bitrate: Int, val vbrQuality: Int = 0) {
    Opus128(ConvertCodec.Opus, 128_000),
    Opus160(ConvertCodec.Opus, 160_000),
    Opus192(ConvertCodec.Opus, 192_000),
    Mp3V0(ConvertCodec.Mp3, 0, vbrQuality = 0),
    Mp3Cbr320(ConvertCodec.Mp3, 320_000),
    Aac256(ConvertCodec.Aac, 256_000),
    ;

    /** The name it is kept by. */
    val key: String get() = name.lowercase(Locale.ROOT)

    /** kbps, or null for a VBR quality. */
    val kbps: Int? get() = if (bitrate > 0) bitrate / 1000 else null

    companion object {
        val DEFAULT = Opus160

        fun of(key: String?): ConvertPreset = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** The output's sample rate: the source's (where the codec has it), or one asked for. Opus is always 48 kHz. */
enum class ConvertRate(val hz: Int) {
    Source(0),
    Hz44100(44_100),
    Hz48000(48_000),
    ;

    companion object {
        fun of(key: String?): ConvertRate = entries.firstOrNull { it.name == key } ?: Source
    }
}

/** How the new files are named: "Artist/Album/NN Title.ext" in folders, or "Artist - Title.ext" side by side. */
enum class ConvertNaming {
    Folders,
    Flat,
    ;

    companion object {
        fun of(key: String?): ConvertNaming = entries.firstOrNull { it.name == key } ?: Folders
    }
}

/** S-1010: what the converter is asked for: format and quality, rate, naming, whether files there are left alone, where. */
data class ConvertOptions(
    val preset: ConvertPreset = ConvertPreset.DEFAULT,
    val rate: ConvertRate = ConvertRate.Source,
    val naming: ConvertNaming = ConvertNaming.Folders,
    val skipExisting: Boolean = true,
    /** The destination: a document tree the system's picker gave; empty until one is chosen. */
    val folder: String = "",
) {
    /** The engine's settings: Opus takes no rate (it is always 48 kHz). */
    fun settings(): ConvertSettings =
        ConvertSettings(preset.codec, preset.bitrate, preset.vbrQuality, if (preset.codec == ConvertCodec.Opus) 0 else rate.hz)

    /** Whether the rate is the user's to choose for this format. */
    val rateApplies: Boolean get() = preset.codec != ConvertCodec.Opus

    /** The choices as the next conversion starts from them. */
    fun write(editor: SharedPreferences.Editor): SharedPreferences.Editor = editor
        .putString(KEY_PRESET, preset.key)
        .putString(KEY_RATE, rate.name)
        .putString(KEY_NAMING, naming.name)
        .putBoolean(KEY_SKIP, skipExisting)
        .putString(KEY_FOLDER, folder)

    companion object {
        const val KEY_PRESET = "nagmedar_convert_preset"
        const val KEY_RATE = "nagmedar_convert_rate"
        const val KEY_NAMING = "nagmedar_convert_naming"
        const val KEY_SKIP = "nagmedar_convert_skip_existing"

        /** This device's: a folder permission (a backup leaves it out). */
        const val KEY_FOLDER = "nagmedar_convert_folder"

        /** The last conversion's choices; the defaults before any. */
        fun read(prefs: SharedPreferences): ConvertOptions = ConvertOptions(
            preset = ConvertPreset.of(prefs.getString(KEY_PRESET, null)),
            rate = ConvertRate.of(prefs.getString(KEY_RATE, null)),
            naming = ConvertNaming.of(prefs.getString(KEY_NAMING, null)),
            skipExisting = prefs.getBoolean(KEY_SKIP, true),
            folder = prefs.getString(KEY_FOLDER, null).orEmpty(),
        )
    }
}
