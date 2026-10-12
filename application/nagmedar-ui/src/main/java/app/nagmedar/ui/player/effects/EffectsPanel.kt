package app.nagmedar.ui.player.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HoverTooltip
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The effects tab of the player's panel (S-603), after the mockups' "Panel · Effects": the
 * picture's colours, its shape, crop and rotation, HDR and scaling, the audio effects, then
 * "Save for this file" and "Make default". Every change applies at once ([EffectsController]);
 * what this build's player cannot do yet ([supported]) shows faded, with a note.
 */
@Composable
fun EffectsPanel(
    effects: PlayerEffects,
    supported: Set<Effect>,
    remembered: Boolean,
    onChange: ((PlayerEffects) -> PlayerEffects) -> Unit,
    onRemember: (Boolean) -> Unit,
    onMakeDefault: () -> Unit,
    onEqualizerWindow: () -> Unit,
    modifier: Modifier = Modifier,
    /** Under the video in a wide window: two columns of sections. */
    columns: Int = 1,
) {
    Column(modifier.fillMaxSize().testTag("effects-panel")) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (columns >= 2) {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        PictureSection(effects, supported, onChange)
                        ShapeSection(effects, supported, onChange)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        HdrSection(effects, supported, onChange)
                        Rule()
                        AudioSection(effects, supported, onChange, onEqualizerWindow)
                    }
                }
            } else {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    PictureSection(effects, supported, onChange)
                    ShapeSection(effects, supported, onChange)
                    Rule()
                    HdrSection(effects, supported, onChange)
                    Rule()
                    AudioSection(effects, supported, onChange, onEqualizerWindow)
                }
            }
        }
        Footer(remembered, onRemember, onMakeDefault)
    }
}

@Composable
private fun PictureSection(effects: PlayerEffects, supported: Set<Effect>, onChange: ((PlayerEffects) -> PlayerEffects) -> Unit) {
    val enabled = Effect.ColorAdjust in supported
    val color = effects.color
    val locale = currentLocale()
    Section {
        SectionTitle(stringResource(R.string.nag_fx_picture)) {
            TextAction(stringResource(R.string.nag_fx_reset), enabled = enabled && !color.isNeutral) { onChange { it.copy(color = ColorAdjust()) } }
        }
        LevelRow(stringResource(R.string.nag_fx_brightness), color.brightness, ColorAdjust.MIN, ColorAdjust.MAX, signed(color.brightness, locale), enabled) { v ->
            onChange { it.copy(color = it.color.copy(brightness = v)) }
        }
        LevelRow(stringResource(R.string.nag_fx_contrast), color.contrast, ColorAdjust.MIN, ColorAdjust.MAX, signed(color.contrast, locale), enabled) { v ->
            onChange { it.copy(color = it.color.copy(contrast = v)) }
        }
        LevelRow(stringResource(R.string.nag_fx_saturation), color.saturation, ColorAdjust.MIN, ColorAdjust.MAX, signed(color.saturation, locale), enabled) { v ->
            onChange { it.copy(color = it.color.copy(saturation = v)) }
        }
        LevelRow(
            stringResource(R.string.nag_fx_hue),
            color.hue,
            ColorAdjust.HUE_MIN,
            ColorAdjust.HUE_MAX,
            stringResource(R.string.nag_fx_value_degrees, signed(color.hue, locale)),
            enabled,
        ) { v -> onChange { it.copy(color = it.color.copy(hue = v)) } }
        // gamma in hundredths, 1,00 in the middle
        LevelRow(
            stringResource(R.string.nag_fx_gamma),
            (color.gamma * 100).roundToInt(),
            (ColorAdjust.GAMMA_MIN * 100).roundToInt(),
            (ColorAdjust.GAMMA_MAX * 100).roundToInt(),
            decimal(color.gamma, locale),
            enabled,
        ) { v -> onChange { it.copy(color = it.color.copy(gamma = v / 100f)) } }
        if (!enabled) PendingNote()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeSection(effects: PlayerEffects, supported: Set<Effect>, onChange: ((PlayerEffects) -> PlayerEffects) -> Unit) {
    val transform = effects.transform
    val canTransform = Effect.Transform in supported
    Section(spacing = 8.dp) {
        SectionTitle(stringResource(R.string.nag_fx_shape))
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AspectChoice.entries.forEach { aspect ->
                Chip(stringResource(aspect.label), selected = effects.aspect == aspect, enabled = Effect.Aspect in supported) {
                    onChange { it.copy(aspect = aspect) }
                }
            }
        }
        SwitchRow(
            stringResource(R.string.nag_fx_auto_crop),
            stringResource(R.string.nag_fx_auto_crop_hint),
            effects.autoCrop,
            enabled = Effect.AutoCrop in supported,
        ) { on -> onChange { it.copy(autoCrop = on) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // a quarter turn draws as the player's own "back" and "forward" arrows
            TransformButton(NagIcons.Resume, stringResource(R.string.nag_fx_rotate_left), selected = false, canTransform) {
                onChange { it.copy(transform = it.transform.rotatedLeft()) }
            }
            TransformButton(NagIcons.Forward, stringResource(R.string.nag_fx_rotate_right), selected = false, canTransform) {
                onChange { it.copy(transform = it.transform.rotatedRight()) }
            }
            TransformButton(NagIcons.FlipHorizontal, stringResource(R.string.nag_fx_flip_horizontal), transform.flipHorizontal, canTransform) {
                onChange { it.copy(transform = it.transform.flippedHorizontally()) }
            }
            TransformButton(NagIcons.FlipVertical, stringResource(R.string.nag_fx_flip_vertical), transform.flipVertical, canTransform) {
                onChange { it.copy(transform = it.transform.flippedVertically()) }
            }
        }
        if (transform.rotation != 0) Caption(stringResource(R.string.nag_fx_rotated, transform.rotation))
        if (Effect.AutoCrop !in supported || !canTransform) PendingNote()
    }
}

@Composable
private fun HdrSection(effects: PlayerEffects, supported: Set<Effect>, onChange: ((PlayerEffects) -> PlayerEffects) -> Unit) {
    val video = setOf(Effect.HdrOutput, Effect.ToneMapping, Effect.Scaler, Effect.Debanding)
    val passthrough = effects.hdrOutput == HdrOutput.Passthrough
    Section(spacing = 6.dp) {
        SectionTitle(stringResource(R.string.nag_fx_hdr_section)) {
            if (supported.any { it in video }) Badge(stringResource(R.string.nag_fx_gpu_load, stringResource(gpuLoad(effects))))
        }
        SelectRow(stringResource(R.string.nag_fx_hdr_output), effects.hdrOutput, HdrOutput.entries, Effect.HdrOutput in supported) { option ->
            onChange { it.copy(hdrOutput = option) }
        }
        SelectRow(
            stringResource(R.string.nag_fx_tone_mapping),
            effects.toneMapping,
            ToneMapping.entries,
            enabled = Effect.ToneMapping in supported && !passthrough,
            shown = if (passthrough) stringResource(R.string.nag_fx_tone_unused) else null,
        ) { option -> onChange { it.copy(toneMapping = option) } }
        SelectRow(stringResource(R.string.nag_fx_scaler), effects.scaler, Scaler.entries, Effect.Scaler in supported) { option ->
            onChange { it.copy(scaler = option) }
        }
        SelectRow(stringResource(R.string.nag_fx_deinterlace), effects.deinterlace, Deinterlace.entries, Effect.Deinterlace in supported) { option ->
            onChange { it.copy(deinterlace = option) }
        }
        SwitchRow(stringResource(R.string.nag_fx_debanding), null, effects.debanding, enabled = Effect.Debanding in supported) { on ->
            onChange { it.copy(debanding = on) }
        }
        if (!supported.containsAll(video + Effect.Deinterlace)) PendingNote()
    }
}

@Composable
private fun AudioSection(
    effects: PlayerEffects,
    supported: Set<Effect>,
    onChange: ((PlayerEffects) -> PlayerEffects) -> Unit,
    onEqualizerWindow: () -> Unit,
) {
    val locale = currentLocale()
    Section(spacing = 6.dp) {
        SectionTitle(stringResource(R.string.nag_fx_audio))
        SwitchRow(
            stringResource(R.string.nag_fx_night_mode),
            stringResource(R.string.nag_fx_night_mode_hint),
            effects.nightMode,
            enabled = Effect.NightMode in supported,
        ) { on -> onChange { it.copy(nightMode = on) } }
        LevelRow(
            stringResource(R.string.nag_fx_dialogue),
            effects.dialogueDb,
            0,
            PlayerEffects.DIALOGUE_MAX_DB,
            stringResource(R.string.nag_fx_value_db, signed(effects.dialogueDb, locale)),
            enabled = Effect.Dialogue in supported,
            centred = false,
            labelWidth = 96.dp,
        ) { v -> onChange { it.copy(dialogueDb = v) } }
        LevelRow(
            stringResource(R.string.nag_fx_gain),
            effects.gainPercent,
            0,
            PlayerEffects.GAIN_MAX_PERCENT,
            NumberFormat.getPercentInstance(locale).format(effects.gainPercent / 100.0),
            enabled = Effect.Gain in supported,
            centred = false,
            labelWidth = 96.dp,
            step = 5,
            neutralAt = 100,
        ) { v -> onChange { it.copy(gainPercent = v) } }
        SelectRow(stringResource(R.string.nag_fx_channels), effects.channelLayout, ChannelLayout.entries, Effect.ChannelLayout in supported) { option ->
            onChange { it.copy(channelLayout = option) }
        }
        EqualizerRow(effects.equalizer, Effect.Equalizer in supported) { choice -> onChange { it.copy(equalizer = choice) } }
        WindowButton(NagIcons.Equalizer, stringResource(R.string.nag_sc_equalizer), ShortcutId.Equalizer, onEqualizerWindow)
        if (!supported.containsAll(setOf(Effect.NightMode, Effect.Dialogue, Effect.ChannelLayout))) PendingNote()
    }
}

/** "Save for this file" and "Make default", under the sections. */
@Composable
private fun Footer(remembered: Boolean, onRemember: (Boolean) -> Unit, onMakeDefault: () -> Unit) {
    var madeDefault by remember { mutableStateOf(false) }
    LaunchedEffect(madeDefault) {
        if (madeDefault) {
            delay(2_000)
            madeDefault = false
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NagCheckbox(remembered, onRemember, stringResource(R.string.nag_fx_remember), Modifier.weight(1f).testTag("effects-remember"))
        NagButton(
            stringResource(if (madeDefault) R.string.nag_fx_made_default else R.string.nag_fx_make_default),
            onClick = {
                onMakeDefault()
                madeDefault = true
            },
            icon = if (madeDefault) NagIcons.Check else null,
            modifier = Modifier.testTag("effects-make-default"),
        )
    }
}

@Composable
private fun Section(spacing: Dp = 2.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/** A section's name in capitals, with what acts on the whole section at its end. */
@Composable
private fun SectionTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(currentLocale()),
            style = NagTheme.type.caption.copy(letterSpacing = 0.08.em),
            color = NagTheme.colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NagTheme.colors.border))
}

@Composable
private fun Caption(text: String) {
    Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
}

/** Under faded controls: they wait for the native side of the player. */
@Composable
private fun PendingNote() {
    Text(
        stringResource(R.string.nag_fx_pending),
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.textTertiary,
        modifier = Modifier.padding(top = 2.dp).testTag("effects-pending"),
    )
}

/** A text button in the accent colour: "Reset". */
@Composable
private fun TextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    Box(
        Modifier
            .heightIn(min = 24.dp)
            .background(stateBackground(state), RoundedCornerShape(5.dp))
            .nagClickable(interaction, onClick, enabled = enabled)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NagTheme.type.label, color = if (enabled) colors.accentText else colors.textTertiary)
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .background(NagTheme.colors.surfaceRaised, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/**
 * A level: its name, a slider and its value. [centred] sliders start from their middle (0 or 1,00),
 * lit from there to the knob; the others from their start. The value is grey while it is the film's own.
 */
@Composable
private fun LevelRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    text: String,
    enabled: Boolean,
    centred: Boolean = true,
    labelWidth: Dp = 76.dp,
    step: Int = 1,
    /** The film's own value, shown grey. */
    neutralAt: Int = if (centred) (min + max) / 2 else min,
    onValue: (Int) -> Unit,
) {
    val colors = NagTheme.colors
    val neutral = value == neutralAt
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(labelWidth))
        LevelSlider(
            fraction = (value - min).toFloat() / (max - min),
            centred = centred,
            enabled = enabled,
            label = label,
            valueText = text,
            modifier = Modifier.weight(1f),
        ) { fraction ->
            val raw = min + fraction * (max - min)
            onValue(((raw / step).roundToInt() * step).coerceIn(min, max))
        }
        Text(
            text,
            style = NagTheme.type.mono,
            color = if (neutral) colors.textTertiary else colors.text,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(46.dp),
        )
    }
}

/** The panel's slider: a thin track with a round knob that always shows; a centre mark on [centred] ones. */
@Composable
private fun LevelSlider(
    fraction: Float,
    centred: Boolean,
    enabled: Boolean,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    onFraction: (Float) -> Unit,
) {
    val colors = NagTheme.colors
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onFraction)
    val latestFraction by rememberUpdatedState(fraction)
    val track = colors.hover
    val mark = colors.textTertiary.copy(alpha = 0.6f)
    val fill = colors.accent
    val knob = colors.text
    Canvas(
        modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .semantics {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        latest(target.coerceIn(0f, 1f))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val at = (change.position.x / widthPx).coerceIn(0f, 1f)
                        when (event.type) {
                            PointerEventType.Press -> {
                                dragging = true
                                latest(at)
                                change.consume()
                            }
                            PointerEventType.Move -> if (dragging && change.pressed) {
                                latest(at)
                                change.consume()
                            }
                            PointerEventType.Release -> dragging = false
                            PointerEventType.Scroll -> {
                                val delta = change.scrollDelta.y.takeIf { it != 0f } ?: change.scrollDelta.x
                                if (delta != 0f) {
                                    latest((latestFraction - delta * WHEEL_STEP).coerceIn(0f, 1f))
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val y = size.height / 2
        val thickness = 4.dp.toPx()
        val radius = CornerRadius(thickness / 2, thickness / 2)
        val at = fraction.coerceIn(0f, 1f) * size.width
        drawRoundRect(track, Offset(0f, y - thickness / 2), Size(size.width, thickness), radius)
        if (centred) {
            val middle = size.width / 2
            drawRect(mark, Offset(middle - 1.dp.toPx(), y - 6.dp.toPx()), Size(2.dp.toPx(), 12.dp.toPx()))
            val from = minOf(at, middle)
            val to = maxOf(at, middle)
            if (to - from > 0.5f) drawRoundRect(fill, Offset(from, y - thickness / 2), Size(to - from, thickness), radius)
        } else if (at > 0.5f) {
            drawRoundRect(fill, Offset(0f, y - thickness / 2), Size(at, thickness), radius)
        }
        drawCircle(knob, 7.dp.toPx(), Offset(at.coerceIn(7.dp.toPx(), size.width - 7.dp.toPx()), y))
    }
}

/** A chip of a group where one is chosen: the aspect ratios. */
@Composable
private fun Chip(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .heightIn(min = 28.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.borderStrong, shape)
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else stateBackground(state), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .selectable(selected = selected, interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NagTheme.type.labelSmall, color = if (selected) colors.text else colors.textSecondary, maxLines = 1)
    }
}

/** An on/off setting: its name (and what it does) with the switch at the end; the whole row toggles it. */
@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NagTheme.type.label, color = colors.text)
            if (hint != null) Text(hint, style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
        Box(
            Modifier
                .size(width = 38.dp, height = 24.dp)
                .background(if (checked) colors.accent else colors.borderStrong, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = if (checked) 18.dp else 4.dp)
                    .size(16.dp)
                    .background(if (checked) colors.onAccent else colors.textSecondary, RoundedCornerShape(8.dp)),
            )
        }
    }
}

/** A rotation or a mirroring, by its icon; a mirroring stays lit while it applies. */
@Composable
private fun RowScope.TransformButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .weight(1f)
            .height(30.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.borderStrong, shape)
            .background(stateBackground(state, selected, rest = colors.surfaceRaised.copy(alpha = 0.5f)), shape)
            .nagClickable(interaction, onClick, enabled = enabled, onClickLabel = label)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        HoverTooltip(state.hovered && !state.pressed, label)
    }
}

/** A setting chosen from a list: its name and a button showing the choice, which opens the list. */
@Composable
private fun <T : EffectOption> SelectRow(
    label: String,
    value: T,
    options: List<T>,
    enabled: Boolean,
    /** What the button says instead of the choice: "Not used when passed through". */
    shown: String? = null,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val entries = options.map { option -> MenuEntry.Item(stringResource(option.label), { onSelect(option) }, checked = option == value) }
    LabelledRow(label) {
        DropButton(shown ?: stringResource(value.label), enabled, label) { open = true }
        MenuPopup(visible = open, onDismiss = { open = false }, entries = entries, offset = IntOffset(0, 0), minWidth = 200.dp)
    }
}

/** The equaliser: the player's own, off, or one of VLC's presets. */
@Composable
private fun EqualizerRow(choice: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val players = stringResource(R.string.nag_fx_equalizer_players)
    val off = stringResource(R.string.nag_fx_off)
    val preset = EqualizerPreset.of(choice)
    val shown = when {
        choice == null -> players
        choice == PlayerEffects.EQUALIZER_OFF -> off
        preset != null -> stringResource(preset.label)
        else -> players
    }
    val entries = listOf(
        MenuEntry.Item(players, { onSelect(null) }, checked = choice == null),
        MenuEntry.Item(off, { onSelect(PlayerEffects.EQUALIZER_OFF) }, checked = choice == PlayerEffects.EQUALIZER_OFF),
        MenuEntry.Separator,
    ) + EqualizerPreset.entries.map { MenuEntry.Item(stringResource(it.label), { onSelect(it.key) }, checked = it == preset) }
    val label = stringResource(R.string.nag_fx_equalizer)
    LabelledRow(label) {
        DropButton(shown, enabled, label) { open = true }
        MenuPopup(visible = open, onDismiss = { open = false }, entries = entries, offset = IntOffset(0, 0), minWidth = 200.dp)
    }
}

@Composable
private fun LabelledRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary, maxLines = 2, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) { control() }
    }
}

@Composable
private fun DropButton(text: String, enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(7.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(1.dp, colors.borderStrong, shape)
            .background(stateBackground(state, rest = colors.background), shape)
            .nagClickable(interaction, onClick, enabled = enabled, onClickLabel = label)
            .semantics {
                contentDescription = label
                stateDescription = text
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = NagTheme.type.labelSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(NagIcons.ChevronDown, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(12.dp))
    }
}

/** A button that opens a window, its keys at its end: "Equaliser… Ctrl+Shift+E". */
@Composable
private fun WindowButton(icon: ImageVector, text: String, shortcut: ShortcutId, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(7.dp)
    val keys = ShortcutRegistry.chordLabel(shortcut, LocalContext.current.resources)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .heightIn(min = 32.dp)
            .border(1.dp, colors.borderStrong, shape)
            .background(stateBackground(state, rest = colors.surfaceRaised), shape)
            .nagClickable(interaction, onClick)
            .padding(horizontal = 10.dp)
            .testTag("effects-equalizer-window"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
        Text(text, style = NagTheme.type.label, color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
        if (keys != null) Text(keys, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
    }
}

/** A rough load on the graphics chip of the chosen scaling, tone mapping, deinterlacing and debanding. */
private fun gpuLoad(effects: PlayerEffects): Int {
    var load = 0
    load += when (effects.scaler) {
        Scaler.EwaLanczos -> 2
        Scaler.Spline36 -> 1
        else -> 0
    }
    if (effects.debanding) load += 1
    if (effects.hdrOutput == HdrOutput.ToneMap) load += 1
    if (effects.deinterlace == Deinterlace.Yadif || effects.deinterlace == Deinterlace.Yadif2x) load += 1
    return when {
        load >= 4 -> R.string.nag_fx_gpu_high
        load >= 2 -> R.string.nag_fx_gpu_medium
        else -> R.string.nag_fx_gpu_low
    }
}

/** "+6", "0", "−20": the plus and the minus sign of the typography rules. */
private fun signed(value: Int, locale: Locale): String {
    val number = NumberFormat.getIntegerInstance(locale).format(abs(value))
    return when {
        value > 0 -> "+$number"
        value < 0 -> "−$number"
        else -> number
    }
}

/** "1,06" in Turkish, "1.06" in English. */
private fun decimal(value: Float, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value.toDouble())

private const val DISABLED_ALPHA = 0.45f
private const val WHEEL_STEP = 0.02f
