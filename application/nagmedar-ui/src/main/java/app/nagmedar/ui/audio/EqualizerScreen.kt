package app.nagmedar.ui.audio

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.DspMeter
import app.nagmedar.audio.DspResponse
import app.nagmedar.audio.DspSettings
import app.nagmedar.audio.EqBand
import app.nagmedar.audio.EqType
import app.nagmedar.audio.GraphicPresets
import app.nagmedar.audio.HeadphoneProfile
import app.nagmedar.audio.NativeAudio
import app.nagmedar.audio.withProfile
import app.nagmedar.ui.R
import app.nagmedar.ui.components.GainSlider
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.gainText
import app.nagmedar.ui.player.effects.EqualizerPreset
import app.nagmedar.ui.player.panel.DropButton
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import org.videolan.tools.NAGMEDAR_DSP
import org.videolan.tools.NAGMEDAR_DSP_PROFILES
import org.videolan.tools.Settings
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * S-906: the equaliser's state: the chain's settings and the headphone profiles kept, read from
 * the settings and written back at every change, which the engine takes at its next block.
 */
@Stable
class EqualizerState(private val settings: SharedPreferences) {

    var dsp by mutableStateOf(DspSettings.fromJson(settings.getString(NAGMEDAR_DSP, null)))
        private set

    var profiles by mutableStateOf(HeadphoneProfile.listFromJson(settings.getString(NAGMEDAR_DSP_PROFILES, null)))
        private set

    fun update(transform: (DspSettings) -> DspSettings) {
        val next = transform(dsp)
        if (next == dsp) return
        dsp = next
        settings.edit().putString(NAGMEDAR_DSP, next.toJson()).apply()
    }

    fun setGraphicBand(index: Int, db: Double) = update { d -> d.copy(graphicDb = d.graphicDb.mapIndexed { i, v -> if (i == index) db else v }) }

    fun setParametricBand(index: Int, band: EqBand) = update { d -> d.copy(parametric = d.parametric.mapIndexed { i, b -> if (i == index) band else b }) }

    fun addParametricBand() = update { d ->
        if (d.parametric.size >= NativeAudio.EQ_BANDS) d else d.copy(parametricEnabled = true, parametric = d.parametric + EqBand(1000.0, 0.0, 1.0))
    }

    fun removeParametricBand(index: Int) = update { d -> d.copy(parametric = d.parametric.filterIndexed { i, _ -> i != index }) }

    /** [profile] kept (replacing one of its name) and put in the chain. */
    fun addProfile(profile: HeadphoneProfile) {
        profiles = profiles.filter { it.name != profile.name } + profile
        settings.edit().putString(NAGMEDAR_DSP_PROFILES, HeadphoneProfile.listToJson(profiles)).apply()
        update { it.withProfile(profile) }
    }

    fun useProfile(profile: HeadphoneProfile) = update { it.withProfile(profile) }

    /** The profile in the chain forgotten, and taken out of the chain. */
    fun removeProfile(name: String) {
        profiles = profiles.filter { it.name != name }
        settings.edit().putString(NAGMEDAR_DSP_PROFILES, HeadphoneProfile.listToJson(profiles)).apply()
        update { d -> if (d.profileName == name) d.copy(profileEnabled = false, profileName = "", profilePreampDb = 0.0, profile = emptyList()) else d }
    }

    fun reset() = update { DspSettings() }
}

@Composable
fun rememberEqualizerState(settings: SharedPreferences? = null): EqualizerState {
    val context = LocalContext.current
    return remember(settings) { EqualizerState(settings ?: Settings.getInstance(context)) }
}

/** The clip meter while the engine plays music, every quarter second; null otherwise. */
@Composable
fun rememberDspMeter(): DspMeter? = produceState<DspMeter?>(null) {
    while (true) {
        value = if (MusicEngineAccess.active) MusicEngineAccess.controller?.dspMeter() else null
        delay(250)
    }
}.value

/**
 * S-906: the music engine's DSP chain, from the preamp to the limiter: its response drawn, the
 * graphic and the parametric equaliser, a headphone profile, the limiter, the one key that
 * bypasses all of it, and the clip meter. [side] holds the signal path and the A/B test on a
 * wide window (beside the chain) or under it.
 */
@Composable
fun EqualizerScreen(
    state: EqualizerState = rememberEqualizerState(),
    meter: DspMeter? = rememberDspMeter(),
    side: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val dimens = NagTheme.dimens
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1000.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageHeader(title = stringResource(R.string.nag_eq_title), subtitle = stringResource(R.string.nag_eq_subtitle))
            Column(Modifier.padding(horizontal = dimens.pagePadding), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                ChainBar(state, meter)
                if (wide && side != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                        Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) { ChainCards(state) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) { side() }
                    }
                } else {
                    ChainCards(state)
                    if (side != null) side()
                }
            }
            Spacer(Modifier.height(dimens.gapXl))
        }
    }
}

@Composable
private fun ColumnScope.ChainCards(state: EqualizerState) {
    val bypassed = state.dsp.bypass
    val dim = Modifier.alpha(if (bypassed) 0.55f else 1f)
    ResponseCard(state.dsp, dim)
    PreampCard(state, dim)
    GraphicCard(state, dim)
    ParametricCard(state, dim)
    ProfileCard(state, dim)
    LimiterCard(state, dim)
}

/** A card of the chain: its switch as its title, a hint, its controls. */
@Composable
private fun EqCard(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        if (enabled != null && onEnabledChange != null) {
            NagSwitch(checked = enabled, onCheckedChange = onEnabledChange, label = title)
        } else {
            Text(title, style = NagTheme.type.title, color = colors.text)
        }
        if (hint != null) Text(hint, style = NagTheme.type.labelSmall, color = colors.textTertiary)
        content()
    }
}

// ----------------------------------------------------------------- the bar

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChainBar(state: EqualizerState, meter: DspMeter?) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = dimens.gapL, vertical = dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
            verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
        ) {
            // each the bar's height, so they line up whatever their own
            val item = Modifier.heightIn(min = 48.dp)
            Box(item, contentAlignment = Alignment.CenterStart) {
                NagSwitch(checked = state.dsp.bypass, onCheckedChange = { on -> state.update { it.copy(bypass = on) } }, label = stringResource(R.string.nag_eq_bypass))
            }
            Box(item, contentAlignment = Alignment.CenterStart) { PresetChoice(state) }
            Box(item, contentAlignment = Alignment.CenterStart) { NagButton(stringResource(R.string.nag_eq_reset_chain), { state.reset() }) }
            Box(item, contentAlignment = Alignment.CenterStart) { MeterView(meter) }
        }
        if (state.dsp.bypass) {
            Text(stringResource(R.string.nag_eq_bypass_on), style = NagTheme.type.labelSmall, color = colors.warning)
        }
    }
}

/** The graphic EQ's preset: VLC's, by VLC's names; "Custom" once a band moved. */
@Composable
private fun PresetChoice(state: EqualizerState) {
    var open by remember { mutableStateOf(false) }
    val presets = EqualizerPreset.entries.filter { it.key in GraphicPresets.keys }
    val current = GraphicPresets.keyOf(state.dsp.graphicDb)?.let { key -> presets.firstOrNull { it.key == key } }
    val label = stringResource(R.string.nag_eq_preset)
    val shown = current?.let { stringResource(it.label) } ?: stringResource(R.string.nag_eq_preset_custom)
    val entries = presets.map { preset ->
        MenuEntry.Item(stringResource(preset.label), {
            GraphicPresets.bands(preset.key)?.let { bands -> state.update { it.copy(graphicEnabled = true, graphicDb = bands) } }
        }, checked = preset == current)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NagTheme.type.label, color = NagTheme.colors.textSecondary)
        Spacer(Modifier.width(NagTheme.dimens.gapS))
        Box(Modifier.width(170.dp)) {
            DropButton(shown, enabled = true, label = label) { open = true }
            MenuPopup(visible = open, onDismiss = { open = false }, entries = entries, offset = IntOffset(0, 0), minWidth = 200.dp)
        }
    }
}

/** The clip meter: the peak before the limiter, the samples clipped, the limiter's work. */
@Composable
private fun MeterView(meter: DspMeter?) {
    val colors = NagTheme.colors
    val context = LocalContext.current
    val locale = context.resources.locale()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(stringResource(R.string.nag_eq_meter), style = NagTheme.type.label, color = colors.textSecondary)
            if (meter == null || meter.frames == 0L) {
                Text(stringResource(R.string.nag_eq_meter_idle), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            } else {
                val parts = buildList {
                    if (meter.peak > 0) add(stringResource(R.string.nag_eq_meter_peak, decibels(meter.peakDb, locale) + "FS"))
                    add(
                        if (meter.clipping) pluralStringResource(R.plurals.nag_eq_meter_clipped, meter.clipped.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), meter.clipped.toInt())
                        else stringResource(R.string.nag_eq_meter_clean),
                    )
                    if (meter.reductionDb > 0.05) add(stringResource(R.string.nag_eq_meter_limiter, decibels(-meter.reductionDb, locale)))
                }
                Text(parts.joinToString(" · "), style = NagTheme.type.labelSmall, color = if (meter.clipping) colors.danger else colors.textSecondary)
            }
        }
        if (meter != null && meter.frames > 0) {
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_eq_meter_reset), { MusicEngineAccess.controller?.dspMeter(reset = true) })
        }
    }
}

// --------------------------------------------------------------- the cards

/** The chain's response, 20 Hz to 20 kHz, ±15 dB, at 48 kHz. */
@Composable
private fun ResponseCard(dsp: DspSettings, modifier: Modifier) {
    val colors = NagTheme.colors
    val frequencies = remember { DspResponse.logFrequencies(240) }
    val curve = remember(dsp) { DspResponse.curve(dsp, frequencies) }
    val measurer = rememberTextMeasurer()
    val labelStyle = NagTheme.type.caption.copy(color = colors.textTertiary)
    val context = LocalContext.current
    val locale = context.resources.locale()
    val description = stringResource(R.string.nag_eq_response)
    EqCard(stringResource(R.string.nag_eq_response), modifier, hint = stringResource(R.string.nag_eq_response_hint)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription = description },
        ) {
            val left = 34.dp.toPx()
            val bottom = 18.dp.toPx()
            val w = size.width - left
            val h = size.height - bottom
            fun x(hz: Double) = left + (log10(hz / 20.0) / 3.0).toFloat() * w
            fun y(db: Double) = (h / 2f) - (db.coerceIn(-RESPONSE_DB, RESPONSE_DB) / RESPONSE_DB).toFloat() * (h / 2f)
            // the grid: decibels across, frequencies up
            for (db in listOf(-12.0, -6.0, 0.0, 6.0, 12.0)) {
                val yy = y(db)
                drawLine(
                    if (db == 0.0) colors.textTertiary else colors.border, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f,
                    pathEffect = if (db == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
                drawText(measurer, gainText(db, DecimalFormatSymbols.getInstance(locale).decimalSeparator), Offset(0f, yy - 7.dp.toPx()), labelStyle)
            }
            for ((hz, text) in listOf(50.0 to "50", 100.0 to "100", 200.0 to "200", 500.0 to "500", 1_000.0 to "1k", 2_000.0 to "2k", 5_000.0 to "5k", 10_000.0 to "10k")) {
                val xx = x(hz)
                drawLine(colors.border, Offset(xx, 0f), Offset(xx, h), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                drawText(measurer, text, Offset(xx - 8.dp.toPx(), h + 2.dp.toPx()), labelStyle)
            }
            // the curve
            val path = Path()
            frequencies.forEachIndexed { i, hz ->
                val point = Offset(x(hz), y(curve[i]))
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, colors.accent, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** The response's height each way, dB. */
private const val RESPONSE_DB = 15.0

@Composable
private fun PreampCard(state: EqualizerState, modifier: Modifier) {
    val context = LocalContext.current
    val locale = context.resources.locale()
    val dsp = state.dsp
    val auto = remember(dsp) { DspResponse.autoPreampDb(dsp.copy(autoPreamp = true, bypass = false), 48_000) }
    EqCard(stringResource(R.string.nag_eq_preamp), modifier) {
        LabelledSlider(
            label = stringResource(R.string.nag_eq_preamp),
            valueText = decibels(dsp.preampDb, locale),
            fraction = ((dsp.preampDb + 12) / 24).toFloat(),
            onFraction = { f -> state.update { it.copy(preampDb = halfStep(f * 24.0 - 12)) } },
            keyStep = 0.5f / 24,
        )
        NagSwitch(checked = dsp.autoPreamp, onCheckedChange = { on -> state.update { it.copy(autoPreamp = on) } }, label = stringResource(R.string.nag_eq_auto_preamp))
        Text(stringResource(R.string.nag_eq_auto_preamp_hint, decibels(auto, locale)), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
    }
}

@Composable
private fun GraphicCard(state: EqualizerState, modifier: Modifier) {
    val context = LocalContext.current
    val locale = context.resources.locale()
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val dsp = state.dsp
    val colors = NagTheme.colors
    EqCard(
        stringResource(R.string.nag_eq_graphic), modifier,
        hint = stringResource(R.string.nag_eq_graphic_hint),
        enabled = dsp.graphicEnabled,
        onEnabledChange = { on -> state.update { it.copy(graphicEnabled = on) } },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // spread over a wide card, scrolled on a narrow one
            val fits = maxWidth >= BAND_WIDTH * NativeAudio.GRAPHIC_BANDS
            Row(
                if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = if (fits) Arrangement.SpaceBetween else Arrangement.spacedBy(2.dp),
            ) {
                DspSettings.graphicFrequencies.forEachIndexed { i, hz ->
                    val db = dsp.graphicDb.getOrElse(i) { 0.0 }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 30.dp)) {
                        Text(gainText(db, separator), style = NagTheme.type.caption, color = if (db == 0.0) colors.textTertiary else colors.text, maxLines = 1)
                        GainSlider(
                            value = db,
                            onValueChange = { state.setGraphicBand(i, it) },
                            label = stringResource(R.string.nag_eq_band, frequencyText(hz, locale, compact = false)),
                            valueText = decibels(db, locale),
                            enabled = dsp.graphicEnabled,
                            modifier = Modifier.height(170.dp),
                        )
                        Text(frequencyText(hz, locale, compact = true), style = NagTheme.type.caption, color = colors.textTertiary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A graphic band's column: its slider and labels. */
private val BAND_WIDTH = 34.dp

@Composable
private fun ParametricCard(state: EqualizerState, modifier: Modifier) {
    val dsp = state.dsp
    EqCard(
        stringResource(R.string.nag_eq_parametric), modifier,
        hint = stringResource(R.string.nag_eq_parametric_hint),
        enabled = dsp.parametricEnabled,
        onEnabledChange = { on -> state.update { it.copy(parametricEnabled = on) } },
    ) {
        if (dsp.parametric.isEmpty()) {
            Text(stringResource(R.string.nag_eq_parametric_empty), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        }
        dsp.parametric.forEachIndexed { i, band ->
            BandEditor(i, band, enabled = dsp.parametricEnabled, onChange = { state.setParametricBand(i, it) }, onRemove = { state.removeParametricBand(i) })
        }
        if (dsp.parametric.size < NativeAudio.EQ_BANDS) {
            NagButton(stringResource(R.string.nag_eq_add_band), { state.addParametricBand() }, icon = NagIcons.Plus)
        }
    }
}

/** One band of the parametric EQ: on or off, its filter, frequency, gain and Q. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandEditor(index: Int, band: EqBand, enabled: Boolean, onChange: (EqBand) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val locale = context.resources.locale()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    var open by remember { mutableStateOf(false) }
    val typeLabel = stringResource(R.string.nag_eq_type)
    val passes = band.type == EqType.LowPass || band.type == EqType.HighPass
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, shape)
            .padding(dimens.gapS)
            .alpha(if (enabled && band.enabled) 1f else 0.6f),
        verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NagCheckbox(checked = band.enabled, onCheckedChange = { onChange(band.copy(enabled = it)) }, label = stringResource(R.string.nag_eq_band_number, index + 1))
            Spacer(Modifier.width(dimens.gapS))
            Box(Modifier.width(160.dp)) {
                DropButton(stringResource(typeName(band.type)), enabled = enabled, label = typeLabel) { open = true }
                MenuPopup(
                    visible = open,
                    onDismiss = { open = false },
                    entries = EqType.entries.map { type -> MenuEntry.Item(stringResource(typeName(type)), { onChange(band.copy(type = type)) }, checked = type == band.type) },
                    minWidth = 180.dp,
                )
            }
            Spacer(Modifier.weight(1f))
            NagIconButton(NagIcons.Delete, stringResource(R.string.nag_eq_remove_band), onRemove)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapL), verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
            LabelledSlider(
                label = stringResource(R.string.nag_eq_frequency),
                valueText = frequencyText(band.freq, locale, compact = false),
                fraction = (log10(band.freq.coerceIn(20.0, 20_000.0) / 20.0) / 3.0).toFloat(),
                onFraction = { f -> onChange(band.copy(freq = significant(20.0 * 1000.0.pow(f.toDouble())))) },
                keyStep = 1f / 120,
                enabled = enabled,
                modifier = Modifier.width(220.dp),
            )
            LabelledSlider(
                label = stringResource(R.string.nag_eq_gain),
                valueText = decibels(band.gainDb, locale),
                fraction = ((band.gainDb + 15) / 30).toFloat(),
                onFraction = { f -> onChange(band.copy(gainDb = halfStep(f * 30.0 - 15))) },
                keyStep = 0.5f / 30,
                enabled = enabled && !passes,
                modifier = Modifier.width(180.dp),
            )
            LabelledSlider(
                label = stringResource(R.string.nag_eq_q),
                valueText = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; minimumFractionDigits = 2 }.format(band.q),
                fraction = (log10(band.q.coerceIn(0.1, 10.0) / 0.1) / 2.0).toFloat(),
                onFraction = { f -> onChange(band.copy(q = (0.1 * 100.0.pow(f.toDouble()) * 100).roundToInt() / 100.0)) },
                keyStep = 1f / 100,
                enabled = enabled,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

private fun typeName(type: EqType): Int = when (type) {
    EqType.Peak -> R.string.nag_eq_type_peak
    EqType.LowShelf -> R.string.nag_eq_type_low_shelf
    EqType.HighShelf -> R.string.nag_eq_type_high_shelf
    EqType.LowPass -> R.string.nag_eq_type_low_pass
    EqType.HighPass -> R.string.nag_eq_type_high_pass
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(state: EqualizerState, modifier: Modifier) {
    val context = LocalContext.current
    val locale = context.resources.locale()
    val dsp = state.dsp
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val pastedName = stringResource(R.string.nag_eq_profile_pasted)
    val bad = stringResource(R.string.nag_eq_profile_bad)
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val profile = text?.let { HeadphoneProfile.parse(it, displayName(context, uri), pastedName) }
        if (profile == null) Toast.makeText(context, bad, Toast.LENGTH_SHORT).show() else state.addProfile(profile)
    }
    var choosing by remember { mutableStateOf(false) }
    EqCard(
        stringResource(R.string.nag_eq_profile), modifier,
        hint = stringResource(R.string.nag_eq_profile_hint),
        enabled = dsp.profileEnabled,
        onEnabledChange = { on ->
            if (on && dsp.profile.isEmpty()) state.profiles.firstOrNull()?.let(state::useProfile) else state.update { it.copy(profileEnabled = on) }
        },
    ) {
        if (state.profiles.isEmpty()) {
            Text(stringResource(R.string.nag_eq_profile_none), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        } else {
            val label = stringResource(R.string.nag_eq_profile)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(260.dp)) {
                    DropButton(dsp.profileName.ifEmpty { state.profiles.first().name }, enabled = true, label = label) { choosing = true }
                    MenuPopup(
                        visible = choosing,
                        onDismiss = { choosing = false },
                        entries = state.profiles.map { p -> MenuEntry.Item(p.name, { state.useProfile(p) }, checked = p.name == dsp.profileName) },
                        minWidth = 260.dp,
                    )
                }
                if (dsp.profileName.isNotEmpty()) {
                    NagIconButton(NagIcons.Delete, stringResource(R.string.nag_eq_profile_remove), { state.removeProfile(dsp.profileName) })
                }
            }
            if (dsp.profile.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.nag_eq_profile_summary, dsp.profile.size, dsp.profile.size, decibels(dsp.profilePreampDb, locale)),
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                )
                val types = EqType.entries.associateWith { stringResource(typeName(it)) }
                Column {
                    dsp.profile.forEach { band ->
                        val gain = if (band.type == EqType.LowPass || band.type == EqType.HighPass) "" else " · " + decibels(band.gainDb, locale)
                        Text(
                            types.getValue(band.type) + " · " + frequencyText(band.freq, locale, compact = false) + gain + " · Q " +
                                NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; minimumFractionDigits = 2 }.format(band.q),
                            style = NagTheme.type.mono,
                            color = if (band.enabled) colors.textSecondary else colors.textTertiary,
                        )
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            NagButton(stringResource(R.string.nag_eq_profile_open), { open.launch(arrayOf("text/*")) }, icon = NagIcons.Folder)
            NagButton(stringResource(R.string.nag_eq_profile_paste), {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                val profile = text?.let { HeadphoneProfile.parse(it, null, uniqueName(pastedName, state.profiles)) }
                if (profile == null) Toast.makeText(context, bad, Toast.LENGTH_SHORT).show() else state.addProfile(profile)
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimiterCard(state: EqualizerState, modifier: Modifier) {
    val context = LocalContext.current
    val locale = context.resources.locale()
    val dsp = state.dsp
    val ms = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    EqCard(
        stringResource(R.string.nag_eq_limiter), modifier,
        hint = stringResource(R.string.nag_eq_limiter_hint),
        enabled = dsp.limiterEnabled,
        onEnabledChange = { on -> state.update { it.copy(limiterEnabled = on) } },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapL), verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapXs)) {
            LabelledSlider(
                label = stringResource(R.string.nag_eq_threshold),
                valueText = decibels(dsp.limiterThresholdDb, locale) + "FS",
                fraction = ((dsp.limiterThresholdDb + 12) / 12).toFloat(),
                onFraction = { f -> state.update { it.copy(limiterThresholdDb = (f * 12.0 - 12).let { db -> Math.round(db * 10) / 10.0 }) } },
                keyStep = 0.1f / 12,
                enabled = dsp.limiterEnabled,
                modifier = Modifier.width(200.dp),
            )
            LabelledSlider(
                label = stringResource(R.string.nag_eq_release),
                valueText = ms.format(dsp.limiterReleaseMs) + " ms",
                fraction = (log10(dsp.limiterReleaseMs.coerceIn(10.0, 1000.0) / 10.0) / 2.0).toFloat(),
                onFraction = { f -> state.update { it.copy(limiterReleaseMs = significant(10.0 * 100.0.pow(f.toDouble()))) } },
                keyStep = 1f / 80,
                enabled = dsp.limiterEnabled,
                modifier = Modifier.width(200.dp),
            )
            LabelledSlider(
                label = stringResource(R.string.nag_eq_lookahead),
                valueText = ms.format(dsp.limiterLookaheadMs) + " ms",
                fraction = ((dsp.limiterLookaheadMs - 1) / 9).toFloat(),
                onFraction = { f -> state.update { it.copy(limiterLookaheadMs = halfStep(1 + f * 9.0)) } },
                keyStep = 0.5f / 9,
                enabled = dsp.limiterEnabled,
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ helpers

/** A horizontal slider under its label, its value on the right. */
@Composable
private fun LabelledSlider(
    label: String,
    valueText: String,
    fraction: Float,
    onFraction: (Float) -> Unit,
    keyStep: Float,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    val colors = NagTheme.colors
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NagTheme.type.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(valueText, style = NagTheme.type.mono, color = colors.text)
        }
        NagSlider(value = fraction.coerceIn(0f, 1f), onValueChange = onFraction, keyStep = keyStep, wheelStep = keyStep, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}

/** To the half decibel, without a −0. */
private fun halfStep(db: Double): Double = Math.round(db * 2) / 2.0 + 0.0

/** To three significant figures: 1 234 Hz → 1 230 Hz, 63,7 → 63,7. */
private fun significant(value: Double): Double {
    if (value <= 0) return value
    val scale = 10.0.pow(2 - kotlin.math.floor(log10(value)))
    return Math.round(value * scale) / scale
}

/** "125 Hz", "1,6 kHz"; compact, the graphic EQ's axis: "125", "1,6k". */
internal fun frequencyText(hz: Double, locale: Locale, compact: Boolean): String {
    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return if (hz >= 1000) {
        format.format(hz / 1000) + if (compact) "k" else " kHz"
    } else {
        format.format(hz) + if (compact) "" else " Hz"
    }
}

/** [base], or "[base] 2", "[base] 3"… when a profile has that name already. */
private fun uniqueName(base: String, profiles: List<HeadphoneProfile>): String {
    if (profiles.none { it.name == base }) return base
    var n = 2
    while (profiles.any { it.name == "$base $n" }) n++
    return "$base $n"
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment
