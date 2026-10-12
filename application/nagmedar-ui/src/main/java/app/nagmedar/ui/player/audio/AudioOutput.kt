package app.nagmedar.ui.player.audio

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.R
import org.videolan.tools.KEY_NAG_AUDIO_OUTPUT
import org.videolan.tools.KEY_NAG_PASSTHROUGH_CODECS

/**
 * How the sound leaves the device (S-508): as libVLC decides from what is plugged in, always
 * stereo, multichannel PCM, the film's own bitstream when the device says it takes it, or the
 * bitstream of the codecs chosen, whatever the device says (an AVR behind an adapter).
 */
enum class AudioOutputMode(val key: String, @StringRes val label: Int) {
    Auto("auto", R.string.nag_fx_auto),
    Stereo("stereo", R.string.nag_out_stereo),
    Multichannel("pcm", R.string.nag_out_multichannel),
    Passthrough("passthrough", R.string.nag_out_passthrough),
    Forced("forced", R.string.nag_out_forced),
    ;

    companion object {
        fun of(key: String?): AudioOutputMode = entries.firstOrNull { it.key == key } ?: Auto
    }
}

/** The codecs a device can take as a bitstream, by Android's encodings (AudioFormat.ENCODING_*). */
enum class PassthroughCodec(val key: String, val label: String, val encoding: Int) {
    Ac3("ac3", "AC-3", ENCODING_AC3),
    Eac3("eac3", "E-AC-3", ENCODING_E_AC3),
    Dts("dts", "DTS", ENCODING_DTS),
    DtsHd("dtshd", "DTS-HD", ENCODING_DTS_HD),
    TrueHd("truehd", "TrueHD", ENCODING_DOLBY_TRUEHD),
    ;

    companion object {
        fun of(encoding: Int): PassthroughCodec? = entries.firstOrNull { it.encoding == encoding }

        /** "ac3,eac3" as codecs; unknown names skipped. */
        fun parse(keys: String?): Set<PassthroughCodec> =
            keys.orEmpty().split(',').mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }.toSet()

        fun encode(codecs: Set<PassthroughCodec>): String = entries.filter { it in codecs }.joinToString(",") { it.key }
    }
}

/** What a sink is. */
enum class AudioSinkKind(@StringRes val label: Int) {
    Hdmi(R.string.nag_out_sink_hdmi),
    Usb(R.string.nag_out_sink_usb),
    Bluetooth(R.string.nag_out_sink_bluetooth),
    Headphones(R.string.nag_out_sink_headphones),
    Speaker(R.string.nag_out_sink_speaker),
    Other(R.string.nag_out_sink_other),
}

/** A device the sound can go to, as Android lists it, and the codecs it says it takes as a bitstream. */
@Immutable
data class AudioSink(val name: String, val kind: AudioSinkKind, val codecs: Set<PassthroughCodec>)

/** The playing player's audio output, and the devices Android lists. */
interface AudioOutputTarget {
    /** The sound sent as [mode] says, the bitstream of [codecs] when forced; false when the player refuses. */
    fun apply(mode: AudioOutputMode, codecs: Set<PassthroughCodec>): Boolean

    /** Whether something plugged in takes a bitstream, as libVLC's output heard. */
    fun canPassthrough(): Boolean

    fun sinks(): List<AudioSink>
}

/** The output in the settings, for every film. */
class PrefsAudioOutputStore(private val prefs: SharedPreferences) {
    fun mode(): AudioOutputMode = AudioOutputMode.of(prefs.getString(KEY_NAG_AUDIO_OUTPUT, null))

    fun codecs(): Set<PassthroughCodec> = PassthroughCodec.parse(prefs.getString(KEY_NAG_PASSTHROUGH_CODECS, null))

    fun save(mode: AudioOutputMode, codecs: Set<PassthroughCodec>) {
        prefs.edit()
            .putString(KEY_NAG_AUDIO_OUTPUT, mode.key)
            .putString(KEY_NAG_PASSTHROUGH_CODECS, PassthroughCodec.encode(codecs))
            .apply()
    }
}

/**
 * The audio output (S-508): chosen in the effects tab's audio section, kept in the settings,
 * applied to the playing player at once and again as a film starts (the player keeps it, but a
 * new player starts from libVLC's own). The devices listed are read again whenever the tab shows.
 */
@Stable
class AudioOutputController(private val target: AudioOutputTarget, private val store: PrefsAudioOutputStore) {
    var mode by mutableStateOf(store.mode())
        private set

    var codecs by mutableStateOf(store.codecs())
        private set

    var sinks by mutableStateOf<List<AudioSink>>(emptyList())
        private set

    /** Whether the device plugged in takes a bitstream. */
    var canPassthrough by mutableStateOf(false)
        private set

    /** Whether the player refused the last choice (its output cannot send a bitstream). */
    var refused by mutableStateOf(false)
        private set

    fun choose(next: AudioOutputMode) {
        if (next == mode) return
        mode = next
        if (next == AudioOutputMode.Forced && codecs.isEmpty()) codecs = DEFAULT_FORCED
        store.save(mode, codecs)
        apply()
    }

    fun toggle(codec: PassthroughCodec) {
        codecs = if (codec in codecs) codecs - codec else codecs + codec
        store.save(mode, codecs)
        if (mode == AudioOutputMode.Forced) apply()
    }

    /** The devices and whether they take a bitstream, read again. */
    fun refresh() {
        sinks = target.sinks()
        canPassthrough = target.canPassthrough()
    }

    /** A film started: the player takes the output chosen (a new player would start from libVLC's own). */
    fun onMediaStarted() {
        if (mode != AudioOutputMode.Auto) apply()
    }

    private fun apply() {
        refused = !target.apply(mode, if (mode == AudioOutputMode.Forced) codecs else emptySet())
    }

    companion object {
        /** What is forced first: the codecs every AVR takes. */
        val DEFAULT_FORCED: Set<PassthroughCodec> = setOf(PassthroughCodec.Ac3, PassthroughCodec.Eac3, PassthroughCodec.Dts)
    }
}

/** AudioFormat.ENCODING_*, as libVLC's Java side knows them (TrueHD has no name before API 28). */
private const val ENCODING_AC3 = 5
private const val ENCODING_E_AC3 = 6
private const val ENCODING_DTS = 7
private const val ENCODING_DTS_HD = 8
private const val ENCODING_DOLBY_TRUEHD = 14
