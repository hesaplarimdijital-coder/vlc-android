package app.nagmedar.ui.audio

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import app.nagmedar.audio.SignalPath
import app.nagmedar.ui.R
import kotlinx.coroutines.delay
import org.videolan.tools.NAGMEDAR_OUTPUT_AAUDIO
import org.videolan.tools.NAGMEDAR_OUTPUT_BIT_PERFECT
import org.videolan.tools.NAGMEDAR_OUTPUT_USB
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.MusicEngineController
import java.text.NumberFormat
import java.util.Locale

/**
 * The music engine (S-901) as the screens reach it: through the playback service, which owns
 * it. Nothing here starts the service; without it there is no engine to ask.
 */
object MusicEngineAccess {

    /** The service's music engine controller, while the service runs. */
    val controller: MusicEngineController?
        get() = runCatching { PlaybackService.instance?.let { it.playlistManager.musicEngine } }.getOrNull()

    /** Whether the music engine plays what plays now (libVLC plays the rest). */
    val active: Boolean
        get() = runCatching { PlaybackService.instance?.let { it.playlistManager.engine === it.playlistManager.musicEngine } }.getOrNull() == true

    /** The signal path while the engine plays; null otherwise. */
    val signalPath: SignalPath? get() = if (active) controller?.signalPathModel else null
}

/** The signal path, refreshed every [intervalMs] while shown; null while the engine does not play. */
@Composable
fun rememberSignalPath(intervalMs: Long = 500): SignalPath? = produceState<SignalPath?>(null) {
    while (true) {
        value = MusicEngineAccess.signalPath
        delay(intervalMs)
    }
}.value

internal fun Resources.locale(): Locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()

/** "44,1 kHz" in Turkish, "44.1 kHz" in English. */
internal fun kiloHertz(rate: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(rate / 1000.0) + " kHz"

/** "+2,5 dB", "−3 dB", "0 dB": a gain with its sign, in the interface's language. */
internal fun decibels(db: Double, locale: Locale, fraction: Int = 1): String {
    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = fraction; minimumFractionDigits = 0 }
    val rounded = Math.round(db * Math.pow(10.0, fraction.toDouble())) / Math.pow(10.0, fraction.toDouble())
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> "−"
        else -> ""
    }
    return sign + format.format(kotlin.math.abs(rounded)) + " dB"
}

/** The output's name as the settings call it. */
@Composable
internal fun outputLabel(name: String): String = when (name) {
    NAGMEDAR_OUTPUT_BIT_PERFECT -> stringResource(R.string.nag_output_bit_perfect)
    NAGMEDAR_OUTPUT_USB -> stringResource(R.string.nag_output_usb)
    NAGMEDAR_OUTPUT_AAUDIO -> stringResource(R.string.nag_output_aaudio)
    else -> stringResource(R.string.nag_output_auto)
}

/** The engine's sink by the output's name: the pull sink's only owner in the app is the bit-perfect AudioTrack output. */
@Composable
internal fun sinkLabel(sink: String): String = outputLabel(
    when (sink) {
        "usb" -> NAGMEDAR_OUTPUT_USB
        "pull" -> NAGMEDAR_OUTPUT_BIT_PERFECT
        else -> NAGMEDAR_OUTPUT_AAUDIO
    },
)

/** Why the samples do not reach the output as they are, in a few words. */
@Composable
internal fun reasonLabel(reason: SignalPath.Reason): String = stringResource(
    when (reason) {
        SignalPath.Reason.None -> R.string.nag_sp_bit_perfect
        SignalPath.Reason.Idle -> R.string.nag_sp_reason_idle
        SignalPath.Reason.DsdToPcm -> R.string.nag_sp_reason_dsd_pcm
        SignalPath.Reason.Rendered -> R.string.nag_sp_reason_rendered
        SignalPath.Reason.Lossy -> R.string.nag_sp_reason_lossy
        SignalPath.Reason.Volume -> R.string.nag_sp_reason_volume
        SignalPath.Reason.ReplayGain -> R.string.nag_sp_reason_replaygain
        SignalPath.Reason.Tempo -> R.string.nag_sp_reason_tempo
        SignalPath.Reason.Balance -> R.string.nag_sp_reason_balance
        SignalPath.Reason.Dsp -> R.string.nag_sp_reason_dsp
        SignalPath.Reason.Depth -> R.string.nag_sp_reason_depth
        SignalPath.Reason.Output -> R.string.nag_sp_reason_output
        SignalPath.Reason.Crossfade -> R.string.nag_sp_reason_crossfade
    },
)
