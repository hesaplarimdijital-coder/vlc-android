package app.nagmedar.ui.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.AbSide
import app.nagmedar.audio.ReplayGainMode
import app.nagmedar.audio.SignalPath
import app.nagmedar.ui.R
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One stage of the path as the panel shows it; [changes]: it changes the samples. */
@Immutable
internal data class PathRow(val label: String, val value: String, val changes: Boolean)

/**
 * S-910: the signal path, from the file to the output: each stage with its format or setting, a
 * dot lit where the stage changes the samples, the bit-perfect badge and, when it is not, the
 * first reason why. [blind]: a blind test runs, so the side that plays (S-912) stays unsaid.
 */
@Composable
fun SignalPathCard(path: SignalPath?, outputDevice: String?, modifier: Modifier = Modifier, blind: Boolean = false) {
    val colors = NagTheme.colors
    val playing = path?.source != null
    val badge: @Composable () -> Unit = {
        if (path?.bitPerfect == true) {
            ToneBadge(stringResource(R.string.nag_sp_bit_perfect), StateTone.Success)
        } else {
            ToneBadge(stringResource(R.string.nag_sp_badge_not), StateTone.Warning)
        }
    }
    EqCard(
        stringResource(R.string.nag_sp_title), modifier,
        hint = stringResource(R.string.nag_sp_hint),
        trailing = if (playing) badge else null,
    ) {
        if (path == null || !playing) {
            Text(stringResource(R.string.nag_sp_idle), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        } else {
            PathStages(signalPathRows(path, outputDevice, blind))
            if (!path.bitPerfect) {
                Text(stringResource(R.string.nag_sp_because, reasonLabel(path.reason)), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
            if (blind) Text(stringResource(R.string.nag_sp_hidden), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
    }
}

/** The stages, one under the other on a line from the source to the output. */
@Composable
private fun PathStages(rows: List<PathRow>) {
    val colors = NagTheme.colors
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, row ->
            val first = i == 0
            val last = i == rows.lastIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT)
                    .drawBehind {
                        val x = 6.dp.toPx()
                        val cy = ROW_HEIGHT.toPx() / 2
                        val radius = 4.dp.toPx()
                        drawLine(colors.borderStrong, Offset(x, if (first) cy else 0f), Offset(x, if (last) cy else size.height), strokeWidth = 2.dp.toPx())
                        if (row.changes) {
                            drawCircle(colors.accent, radius, Offset(x, cy))
                        } else {
                            drawCircle(colors.surface, radius, Offset(x, cy))
                            drawCircle(colors.borderStrong, radius, Offset(x, cy), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    .padding(start = 20.dp)
                    // a stage is read as one: "Source, FLAC · 24 bit · 96 kHz · stereo"
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    row.label,
                    style = NagTheme.type.label,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(0.4f).padding(vertical = 4.dp),
                )
                Text(
                    row.value,
                    style = NagTheme.type.label,
                    color = if (row.changes) colors.text else colors.textSecondary,
                    modifier = Modifier.weight(0.6f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** A row of the path: the QA's 26 px, short labels (QA-06). */
private val ROW_HEIGHT = 26.dp

/** The engine's stages in its order: the file, its decoding, the gains and the chain, the output. */
@Composable
internal fun signalPathRows(path: SignalPath, outputDevice: String?, blind: Boolean): List<PathRow> {
    val locale = LocalContext.current.resources.locale()
    val src = path.source ?: return emptyList()
    val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    return buildList {
        // the file
        val dsd = src.dsdName
        val source = buildList {
            if (dsd != null) {
                add(formatName(src.codec, src.container))
                add(dsd)
            } else {
                add(formatName(src.codec, src.container))
                when {
                    src.bits == 0 -> if (src.bitrate > 0) add(stringResource(R.string.nag_sp_kbps, (src.bitrate / 1000).toInt()))
                    src.isFloat -> add(stringResource(R.string.nag_sp_float, src.bits))
                    else -> add(stringResource(R.string.nag_sp_bits, src.bits))
                }
                add(kiloHertz(src.rate, locale))
            }
            add(channelsText(src.channels))
        }
        add(PathRow(stringResource(R.string.nag_sp_source), source.joinToString(SEPARATOR), changes = false))
        // its decoding: to the engine's doubles, DoP words as they are, DSD to PCM
        val decoded = when {
            src.dop -> stringResource(R.string.nag_sp_decoded_dop)
            dsd != null -> stringResource(R.string.nag_sp_decoded_dsd, dsd, kiloHertz(src.rate, locale))
            else -> stringResource(R.string.nag_sp_decoded, formatName(src.codec, src.container))
        }
        add(PathRow(stringResource(R.string.nag_sp_decoder), decoded, changes = dsd != null && !src.dop))
        // S-907: each track's gain
        val rg = path.replayGain
        if (rg.mode != ReplayGainMode.Off) {
            val parts = buildList {
                add(stringResource(if (rg.mode == ReplayGainMode.Album) R.string.nag_replaygain_album else R.string.nag_replaygain_track))
                add(decibels(rg.gainDb, locale))
                if (!rg.tagged) add(stringResource(R.string.nag_sp_rg_untagged))
                if (rg.clippingPrevented) add(stringResource(R.string.nag_sp_rg_clip))
            }
            add(PathRow(stringResource(R.string.nag_settings_replaygain), parts.joinToString(SEPARATOR), rg.active))
        }
        // S-905: the tracks' overlap
        if (path.crossfadeMs > 0) {
            add(PathRow(stringResource(R.string.nag_settings_crossfade), stringResource(R.string.nag_crossfade_seconds, (path.crossfadeMs + 500) / 1000), changes = true))
        }
        // S-909
        if (path.tempo != 1.0 || path.pitch != 0.0) {
            val parts = buildList {
                if (path.tempo != 1.0) add(stringResource(R.string.nag_sp_speed_value, number.format(path.tempo)))
                if (path.pitch != 0.0) {
                    val semitones = abs(path.pitch)
                    val signed = (if (path.pitch > 0) "+" else "−") + number.format(semitones)
                    add(pluralStringResource(R.plurals.nag_pitch_semitones, semitones.roundToInt(), signed))
                }
            }
            add(PathRow(stringResource(R.string.nag_sp_speed), parts.joinToString(SEPARATOR), changes = true))
        }
        if (path.balance != 0.0 || path.mono) {
            val parts = buildList {
                val percent = (abs(path.balance) * 100).roundToInt()
                if (path.balance < 0) add(stringResource(R.string.nag_balance_left, percent))
                if (path.balance > 0) add(stringResource(R.string.nag_balance_right, percent))
                if (path.mono) add(stringResource(R.string.nag_sp_mono))
            }
            add(PathRow(stringResource(R.string.nag_settings_balance), parts.joinToString(SEPARATOR), changes = true))
        }
        // S-906: the chain, stage by stage; bypassed whole, or for the A/B test's side B
        val dspLabel = stringResource(R.string.nag_sp_dsp)
        when {
            path.dsp.any { it.name == "bypass" } -> add(PathRow(dspLabel, stringResource(R.string.nag_sp_bypassed), changes = false))
            path.dspActive && path.abSide == AbSide.B && !blind ->
                add(PathRow(dspLabel, stringResource(R.string.nag_sp_ab_b, decibels(path.abGainDb, locale)), changes = true))
            path.dspActive -> path.dsp.forEach { stage -> dspRow(stage, locale)?.let(::add) }
        }
        // the volume, when not at full
        if (path.volume != 1.0) {
            add(PathRow(stringResource(R.string.nag_sp_volume), NumberFormat.getPercentInstance(locale).format(path.volume), changes = true))
        }
        // the output
        path.output?.let { out ->
            val parts = buildList {
                add(sinkLabel(out.sink))
                outputDevice?.let(::add)
                add(if (out.format.startsWith("f")) stringResource(R.string.nag_sp_float, out.bits) else stringResource(R.string.nag_sp_bits, out.bits))
                add(kiloHertz(out.rate, locale))
                add(channelsText(out.channels))
                add(stringResource(if (out.exclusive) R.string.nag_sp_direct else R.string.nag_sp_mixed))
            }
            add(PathRow(stringResource(R.string.nag_sp_output), parts.joinToString(SEPARATOR), changes = false))
        }
    }
}

/** A stage of the chain as compiled; null for one the panel does not know. */
@Composable
private fun dspRow(stage: SignalPath.DspStage, locale: Locale): PathRow? {
    fun bands(count: Int?) = count?.takeIf { it > 0 }
    return when (stage.name) {
        "preamp" -> PathRow(stringResource(R.string.nag_eq_preamp), decibels(stage.db ?: 0.0, locale), true)
        "auto_preamp" -> PathRow(stringResource(R.string.nag_eq_auto_preamp), decibels(stage.db ?: 0.0, locale), true)
        "graphic_eq", "parametric_eq" -> {
            val label = stringResource(if (stage.name == "graphic_eq") R.string.nag_eq_graphic else R.string.nag_eq_parametric)
            val count = bands(stage.bands) ?: return PathRow(label, "", true)
            PathRow(label, pluralStringResource(R.plurals.nag_sp_bands, count, count), true)
        }
        "profile" -> {
            val parts = listOfNotNull(stage.profileName?.takeIf { it.isNotEmpty() }, bands(stage.bands)?.let { pluralStringResource(R.plurals.nag_sp_bands, it, it) })
            PathRow(stringResource(R.string.nag_eq_profile), parts.joinToString(SEPARATOR), true)
        }
        "limiter" -> {
            val ms = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(stage.lookaheadMs ?: 0.0)
            PathRow(stringResource(R.string.nag_eq_limiter), decibels(stage.db ?: 0.0, locale) + "FS" + SEPARATOR + stringResource(R.string.nag_sp_lookahead, ms), true)
        }
        else -> null
    }
}

@Composable
private fun channelsText(channels: Int): String = when (channels) {
    1 -> stringResource(R.string.nag_sp_mono)
    2 -> stringResource(R.string.nag_sp_stereo)
    else -> pluralStringResource(R.plurals.nag_sp_channels, channels, channels)
}

private const val SEPARATOR = " · "

/** FFmpeg's names as people know them; PCM and DSD by their file. */
internal fun formatName(codec: String, container: String): String = when {
    codec.startsWith("pcm_") -> CONTAINER_NAMES[container] ?: "PCM"
    codec.startsWith("dsd_") -> CONTAINER_NAMES[container] ?: "DSD"
    else -> CODEC_NAMES[codec] ?: codec.uppercase(Locale.ROOT)
}

private val CODEC_NAMES = mapOf(
    "flac" to "FLAC", "alac" to "ALAC", "mp3" to "MP3", "mp3float" to "MP3", "mp2" to "MP2", "aac" to "AAC", "aac_latm" to "AAC",
    "opus" to "Opus", "libopus" to "Opus", "vorbis" to "Vorbis", "libvorbis" to "Vorbis", "wavpack" to "WavPack", "ape" to "APE",
    "tta" to "TTA", "tak" to "TAK", "wmav1" to "WMA", "wmav2" to "WMA", "wmapro" to "WMA Pro", "wmalossless" to "WMA Lossless",
    "ac3" to "AC-3", "eac3" to "E-AC-3", "dts" to "DTS", "truehd" to "TrueHD", "mlp" to "MLP", "musepack7" to "Musepack",
    "musepack8" to "Musepack", "speex" to "Speex",
)

/** FFmpeg's demuxers by their files' names: "iff" reads DFF. */
private val CONTAINER_NAMES = mapOf(
    "wav" to "WAV", "w64" to "W64", "aiff" to "AIFF", "caf" to "CAF", "dsf" to "DSF", "iff" to "DFF",
    "matroska,webm" to "MKA", "mov,mp4,m4a,3gp,3g2,mj2" to "M4A",
)
