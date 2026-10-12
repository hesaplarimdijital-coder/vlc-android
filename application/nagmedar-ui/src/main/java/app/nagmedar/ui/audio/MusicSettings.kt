package app.nagmedar.ui.audio

import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.MusicEngine
import app.nagmedar.audio.SignalPath
import app.nagmedar.audio.UsbOutput
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import org.videolan.tools.NAGMEDAR_AUDIO_BALANCE
import org.videolan.tools.NAGMEDAR_AUDIO_MONO
import org.videolan.tools.NAGMEDAR_AUDIO_PITCH
import org.videolan.tools.NAGMEDAR_CROSSFADE_CURVE
import org.videolan.tools.NAGMEDAR_CROSSFADE_EQUAL_POWER
import org.videolan.tools.NAGMEDAR_CROSSFADE_LINEAR
import org.videolan.tools.NAGMEDAR_CROSSFADE_MS
import org.videolan.tools.NAGMEDAR_CROSSFADE_SKIP_ALBUM
import org.videolan.tools.NAGMEDAR_DSD_DOP
import org.videolan.tools.NAGMEDAR_DSD_MODE
import org.videolan.tools.NAGMEDAR_DSD_PCM
import org.videolan.tools.NAGMEDAR_MUSIC_ENGINE
import org.videolan.tools.NAGMEDAR_MUSIC_OUTPUT
import org.videolan.tools.NAGMEDAR_OUTPUT_AAUDIO
import org.videolan.tools.NAGMEDAR_OUTPUT_AUTO
import org.videolan.tools.NAGMEDAR_OUTPUT_BIT_PERFECT
import org.videolan.tools.NAGMEDAR_OUTPUT_USB
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_ALBUM
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_CLIP
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_MODE
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_OFF
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_PREAMP
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_TRACK
import org.videolan.tools.Settings
import org.videolan.vlc.media.ReplayGainScan
import kotlin.math.abs
import kotlin.math.roundToInt

/** A USB DAC as the output row shows it (S-903). */
@Immutable
data class UsbDac(val name: String, val permitted: Boolean, val device: UsbDevice? = null)

/** The USB audio devices attached, looked at again every two seconds while shown, and after [refresh] changes. */
@Composable
fun rememberUsbDacs(refresh: Int): List<UsbDac> {
    val context = LocalContext.current
    return produceState(emptyList<UsbDac>(), refresh) {
        while (true) {
            value = UsbOutput.devices(context).map { UsbDac(UsbOutput.name(it), UsbOutput.hasPermission(context, it), it) }
            delay(2_000)
        }
    }.value
}

/**
 * Settings › Music (S-901 and the engine's stories): the engine, its output (S-902, S-903),
 * the crossfade (S-905), ReplayGain and the loudness scan (S-907), DSD (S-908), and pitch,
 * balance and mono (S-909). [usbDacs], [signalPath] and [scan] are the live state; the
 * screenshot tests give their own.
 */
@Composable
internal fun MusicSection(
    engineAvailable: Boolean = MusicEngine.available,
    settings: SharedPreferences? = null,
    usbDacs: List<UsbDac>? = null,
    signalPath: SignalPath? = rememberSignalPath(),
    outputDevice: String? = MusicEngineAccess.controller?.outputDevice,
    scan: ReplayGainScan.Progress? = null,
    onOpenEqualizer: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val vlcSettings = settings ?: remember(context) { Settings.getInstance(context) }
    var nagmedarEngine by remember { mutableStateOf(vlcSettings.getBoolean(NAGMEDAR_MUSIC_ENGINE, true)) }
    SettingsSection(stringResource(R.string.nag_settings_music)) {
        SettingRow(stringResource(R.string.nag_settings_music_engine), subtitle = stringResource(R.string.nag_settings_music_engine_hint)) {
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.nag_music_engine_nagmedar), false to stringResource(R.string.nag_music_engine_libvlc)),
                selected = nagmedarEngine && engineAvailable,
                onSelect = { enabled ->
                    if (enabled && !engineAvailable) return@SegmentedChoice
                    nagmedarEngine = enabled
                    vlcSettings.edit().putBoolean(NAGMEDAR_MUSIC_ENGINE, enabled).apply()
                },
            )
        }
        if (!engineAvailable) {
            Text(stringResource(R.string.nag_settings_music_engine_missing), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning)
        }
        // the rest is the engine's: with libVLC playing the music it has no say
        if (engineAvailable && nagmedarEngine) {
            OutputRows(vlcSettings, usbDacs, signalPath, outputDevice)
            CrossfadeRows(vlcSettings)
            ReplayGainRows(vlcSettings, scan)
            DsdRow(vlcSettings)
            SoundRows(vlcSettings)
            if (onOpenEqualizer != null) {
                Spacer(Modifier.height(NagTheme.dimens.gapS))
                NagButton(stringResource(R.string.nag_eq_open), onOpenEqualizer)
            }
        }
    }
}

// --------------------------------------------------------------- S-902, S-903

@Composable
private fun OutputRows(settings: SharedPreferences, usbDacs: List<UsbDac>?, signalPath: SignalPath?, outputDevice: String?) {
    val context = LocalContext.current
    var output by remember { mutableStateOf(settings.getString(NAGMEDAR_MUSIC_OUTPUT, NAGMEDAR_OUTPUT_AUTO) ?: NAGMEDAR_OUTPUT_AUTO) }
    var refresh by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    SettingRow(stringResource(R.string.nag_settings_output), subtitle = stringResource(R.string.nag_settings_output_hint)) {
        SegmentedChoice(
            options = listOf(NAGMEDAR_OUTPUT_AUTO, NAGMEDAR_OUTPUT_AAUDIO, NAGMEDAR_OUTPUT_BIT_PERFECT, NAGMEDAR_OUTPUT_USB).map { it to outputLabel(it) },
            selected = output,
            onSelect = { chosen ->
                output = chosen
                denied = false
                settings.edit().putString(NAGMEDAR_MUSIC_OUTPUT, chosen).apply()
            },
        )
    }
    if (output == NAGMEDAR_OUTPUT_USB) {
        val dacs = usbDacs ?: rememberUsbDacs(refresh)
        val dac = dacs.firstOrNull()
        val colors = NagTheme.colors
        when {
            dac == null -> Text(stringResource(R.string.nag_output_usb_none), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            dac.permitted -> Text(stringResource(R.string.nag_output_usb_ready, dac.name), style = NagTheme.type.labelSmall, color = colors.success)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
                Text(
                    stringResource(if (denied) R.string.nag_output_usb_denied else R.string.nag_output_usb_ask, dac.name),
                    style = NagTheme.type.labelSmall,
                    color = if (denied) colors.warning else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                NagButton(stringResource(R.string.nag_output_usb_allow), {
                    val device = dac.device ?: return@NagButton
                    UsbOutput.requestPermission(context, device) { granted ->
                        denied = !granted
                        refresh++
                        // the engine takes the DAC as soon as it may
                        if (granted) MusicEngineAccess.controller?.selectOutput()
                    }
                })
            }
        }
    }
    if (signalPath != null) OutputNow(signalPath, outputDevice)
}

/** "Now: Bit-perfect · Topping D90 · 96 kHz · 32 bit", and why it is not bit-perfect when it is not. */
@Composable
private fun OutputNow(path: SignalPath, device: String?) {
    val context = LocalContext.current
    val output = path.output ?: return
    val locale = context.resources.locale()
    val parts = buildList {
        add(sinkLabel(output.sink))
        device?.let(::add)
        add(kiloHertz(output.rate, locale))
        add("${output.bits} bit")
    }
    val colors = NagTheme.colors
    Column(Modifier.padding(top = NagTheme.dimens.gapXs)) {
        Text(stringResource(R.string.nag_output_now, parts.joinToString(" · ")), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        Text(
            if (path.bitPerfect) stringResource(R.string.nag_sp_bit_perfect) else stringResource(R.string.nag_sp_not_bit_perfect, reasonLabel(path.reason)),
            style = NagTheme.type.labelSmall,
            color = if (path.bitPerfect) colors.success else colors.textTertiary,
        )
    }
}

// ---------------------------------------------------------------------- S-905

@Composable
private fun CrossfadeRows(settings: SharedPreferences) {
    var crossfadeMs by remember { mutableStateOf(settings.getInt(NAGMEDAR_CROSSFADE_MS, 0)) }
    var curve by remember { mutableStateOf(settings.getString(NAGMEDAR_CROSSFADE_CURVE, NAGMEDAR_CROSSFADE_EQUAL_POWER) ?: NAGMEDAR_CROSSFADE_EQUAL_POWER) }
    var skipAlbum by remember { mutableStateOf(settings.getBoolean(NAGMEDAR_CROSSFADE_SKIP_ALBUM, true)) }
    SettingRow(stringResource(R.string.nag_settings_crossfade), subtitle = stringResource(R.string.nag_settings_crossfade_hint)) {
        SegmentedChoice(
            options = listOf(0 to stringResource(R.string.nag_crossfade_off)) + CROSSFADE_STEPS_MS.map { it to stringResource(R.string.nag_crossfade_seconds, it / 1000) },
            selected = CROSSFADE_STEPS_MS.firstOrNull { it == crossfadeMs } ?: 0,
            onSelect = { ms ->
                crossfadeMs = ms
                settings.edit().putInt(NAGMEDAR_CROSSFADE_MS, ms).apply()
            },
        )
    }
    if (crossfadeMs > 0) {
        SettingRow(stringResource(R.string.nag_settings_crossfade_curve)) {
            SegmentedChoice(
                options = listOf(
                    NAGMEDAR_CROSSFADE_EQUAL_POWER to stringResource(R.string.nag_crossfade_equal_power),
                    NAGMEDAR_CROSSFADE_LINEAR to stringResource(R.string.nag_crossfade_linear),
                ),
                selected = curve,
                onSelect = { chosen ->
                    curve = chosen
                    settings.edit().putString(NAGMEDAR_CROSSFADE_CURVE, chosen).apply()
                },
            )
        }
        SettingRow(stringResource(R.string.nag_settings_crossfade_album), subtitle = stringResource(R.string.nag_settings_crossfade_album_hint)) {
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.nag_crossfade_album_gapless), false to stringResource(R.string.nag_crossfade_album_fade)),
                selected = skipAlbum,
                onSelect = { skip ->
                    skipAlbum = skip
                    settings.edit().putBoolean(NAGMEDAR_CROSSFADE_SKIP_ALBUM, skip).apply()
                },
            )
        }
    }
}

/** S-905: the crossfade lengths offered, in milliseconds. */
private val CROSSFADE_STEPS_MS = listOf(2_000, 4_000, 8_000)

// ---------------------------------------------------------------------- S-907

/** The ReplayGain preamps offered, dB. */
private val PREAMP_STEPS_DB = listOf(-6f, -3f, 0f, 3f, 6f)

@Composable
private fun ReplayGainRows(settings: SharedPreferences, scanOverride: ReplayGainScan.Progress?) {
    val context = LocalContext.current
    val locale = context.resources.locale()
    var mode by remember { mutableStateOf(settings.getString(NAGMEDAR_REPLAYGAIN_MODE, NAGMEDAR_REPLAYGAIN_OFF) ?: NAGMEDAR_REPLAYGAIN_OFF) }
    var preamp by remember { mutableStateOf(settings.getFloat(NAGMEDAR_REPLAYGAIN_PREAMP, 0f)) }
    var clip by remember { mutableStateOf(settings.getBoolean(NAGMEDAR_REPLAYGAIN_CLIP, true)) }
    SettingRow(stringResource(R.string.nag_settings_replaygain), subtitle = stringResource(R.string.nag_settings_replaygain_hint)) {
        SegmentedChoice(
            options = listOf(
                NAGMEDAR_REPLAYGAIN_OFF to stringResource(R.string.nag_replaygain_off),
                NAGMEDAR_REPLAYGAIN_TRACK to stringResource(R.string.nag_replaygain_track),
                NAGMEDAR_REPLAYGAIN_ALBUM to stringResource(R.string.nag_replaygain_album),
            ),
            selected = mode,
            onSelect = { chosen ->
                mode = chosen
                settings.edit().putString(NAGMEDAR_REPLAYGAIN_MODE, chosen).apply()
            },
        )
    }
    if (mode != NAGMEDAR_REPLAYGAIN_OFF) {
        SettingRow(stringResource(R.string.nag_settings_replaygain_preamp)) {
            SegmentedChoice(
                options = PREAMP_STEPS_DB.map { it to decibels(it.toDouble(), locale, fraction = 0) },
                selected = PREAMP_STEPS_DB.minByOrNull { abs(it - preamp) } ?: 0f,
                onSelect = { db ->
                    preamp = db
                    settings.edit().putFloat(NAGMEDAR_REPLAYGAIN_PREAMP, db).apply()
                },
            )
        }
        SettingRow(stringResource(R.string.nag_settings_replaygain_clip), subtitle = stringResource(R.string.nag_settings_replaygain_clip_hint)) {
            NagSwitch(checked = clip, onCheckedChange = { on ->
                clip = on
                settings.edit().putBoolean(NAGMEDAR_REPLAYGAIN_CLIP, on).apply()
            }, label = stringResource(R.string.nag_settings_replaygain_clip))
        }
    }
    LoudnessScanRow(scanOverride)
}

@Composable
private fun LoudnessScanRow(scanOverride: ReplayGainScan.Progress?) {
    val context = LocalContext.current
    val live by ReplayGainScan.progress.collectAsState()
    val scan = scanOverride ?: live
    var redo by remember { mutableStateOf(false) }
    val colors = NagTheme.colors
    SettingRow(stringResource(R.string.nag_rg_scan), subtitle = stringResource(R.string.nag_rg_scan_hint)) {
        if (scan.running) {
            NagButton(stringResource(R.string.nag_rg_scan_stop), { ReplayGainScan.cancel() })
        } else {
            NagButton(stringResource(R.string.nag_rg_scan_start), { ReplayGainScan.start(context, redo) }, primary = true)
        }
    }
    if (!scan.running) {
        NagCheckbox(checked = redo, onCheckedChange = { redo = it }, label = stringResource(R.string.nag_rg_scan_redo))
    }
    when {
        scan.running -> Text(
            stringResource(R.string.nag_rg_scan_progress, (scan.albumsDone + 1).coerceAtMost(scan.albums.coerceAtLeast(1)), scan.albums, scan.current.orEmpty()),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
        )
        scan.finished -> {
            val result = stringResource(R.string.nag_rg_scan_result, scan.tracksDone, scan.tracksSkipped, scan.tracksFailed)
            Text(
                if (scan.cancelled) stringResource(R.string.nag_rg_scan_stopped, result) else result,
                style = NagTheme.type.labelSmall,
                color = if (scan.tracksFailed > 0) colors.warning else colors.textSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------- S-908

@Composable
private fun DsdRow(settings: SharedPreferences) {
    var dsd by remember { mutableStateOf(settings.getString(NAGMEDAR_DSD_MODE, NAGMEDAR_DSD_PCM) ?: NAGMEDAR_DSD_PCM) }
    SettingRow(stringResource(R.string.nag_settings_dsd), subtitle = stringResource(R.string.nag_settings_dsd_hint)) {
        SegmentedChoice(
            options = listOf(NAGMEDAR_DSD_PCM to stringResource(R.string.nag_dsd_pcm), NAGMEDAR_DSD_DOP to stringResource(R.string.nag_dsd_dop)),
            selected = dsd,
            onSelect = { chosen ->
                dsd = chosen
                settings.edit().putString(NAGMEDAR_DSD_MODE, chosen).apply()
            },
        )
    }
}

// ---------------------------------------------------------------------- S-909

/** The pitch the slider covers, semitones each way (the engine takes two octaves). */
private const val PITCH_RANGE = 12

@Composable
private fun SoundRows(settings: SharedPreferences) {
    var pitch by remember { mutableStateOf(settings.getFloat(NAGMEDAR_AUDIO_PITCH, 0f).roundToInt().coerceIn(-PITCH_RANGE, PITCH_RANGE)) }
    var balance by remember { mutableStateOf((settings.getFloat(NAGMEDAR_AUDIO_BALANCE, 0f) * 10).roundToInt().coerceIn(-10, 10)) }
    var mono by remember { mutableStateOf(settings.getBoolean(NAGMEDAR_AUDIO_MONO, false)) }
    val colors = NagTheme.colors
    SettingRow(stringResource(R.string.nag_settings_pitch), subtitle = stringResource(R.string.nag_settings_pitch_hint)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NagSlider(
                value = (pitch + PITCH_RANGE) / (2f * PITCH_RANGE),
                onValueChange = { fraction ->
                    val semitones = (fraction * 2 * PITCH_RANGE - PITCH_RANGE).roundToInt()
                    if (semitones != pitch) {
                        pitch = semitones
                        settings.edit().putFloat(NAGMEDAR_AUDIO_PITCH, semitones.toFloat()).apply()
                    }
                },
                keyStep = 1f / (2 * PITCH_RANGE),
                wheelStep = 1f / (2 * PITCH_RANGE),
                modifier = Modifier.width(200.dp),
            )
            Spacer(Modifier.width(NagTheme.dimens.gapS))
            val signed = when {
                pitch > 0 -> "+$pitch"
                pitch < 0 -> "−${-pitch}"
                else -> "0"
            }
            Text(
                pluralStringResource(R.plurals.nag_pitch_semitones, abs(pitch), signed),
                style = NagTheme.type.mono,
                color = colors.textSecondary,
                modifier = Modifier.width(110.dp),
            )
        }
    }
    SettingRow(stringResource(R.string.nag_settings_balance)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NagSlider(
                value = (balance + 10) / 20f,
                onValueChange = { fraction ->
                    val step = (fraction * 20 - 10).roundToInt()
                    if (step != balance) {
                        balance = step
                        settings.edit().putFloat(NAGMEDAR_AUDIO_BALANCE, step / 10f).apply()
                    }
                },
                keyStep = 1f / 20,
                wheelStep = 1f / 20,
                modifier = Modifier.width(200.dp),
            )
            Spacer(Modifier.width(NagTheme.dimens.gapS))
            Text(
                when {
                    balance < 0 -> stringResource(R.string.nag_balance_left, -balance * 10)
                    balance > 0 -> stringResource(R.string.nag_balance_right, balance * 10)
                    else -> stringResource(R.string.nag_balance_centre)
                },
                style = NagTheme.type.mono,
                color = colors.textSecondary,
                modifier = Modifier.width(110.dp),
            )
        }
    }
    SettingRow(stringResource(R.string.nag_settings_mono)) {
        NagSwitch(checked = mono, onCheckedChange = { on ->
            mono = on
            settings.edit().putBoolean(NAGMEDAR_AUDIO_MONO, on).apply()
        }, label = stringResource(R.string.nag_settings_mono_switch))
    }
    if (pitch != 0 || balance != 0) {
        NagButton(stringResource(R.string.nag_settings_sound_reset), {
            pitch = 0
            balance = 0
            settings.edit().putFloat(NAGMEDAR_AUDIO_PITCH, 0f).putFloat(NAGMEDAR_AUDIO_BALANCE, 0f).apply()
        })
    }
}
