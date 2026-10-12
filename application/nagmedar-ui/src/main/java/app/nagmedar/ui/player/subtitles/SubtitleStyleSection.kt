package app.nagmedar.ui.player.subtitles

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.effects.LevelRow
import app.nagmedar.ui.player.panel.ChoiceRow
import app.nagmedar.ui.player.panel.DropButton
import app.nagmedar.ui.player.panel.LabelledRow
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelChip
import app.nagmedar.ui.player.panel.PanelDisabledAlpha
import app.nagmedar.ui.player.panel.PendingNote
import app.nagmedar.ui.player.panel.SectionTitle
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat

/**
 * The subtitles' look (S-705), after the mockups' player: a preset, a preview that follows each
 * change, the font, the size, the outline, the shadow, the box behind the text, the colour; where
 * the subtitles stand, the black bars and the ASS files' own styles, which wait for the player's
 * native side. A change reaches the subtitles when the player opens again ([waiting]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleStyleSection(
    style: SubtitleStyle,
    preset: SubtitlePreset?,
    waiting: Boolean,
    layoutSupported: Boolean,
    onStyle: (SubtitleStyle) -> Unit,
    onPreset: (SubtitlePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val integers = NumberFormat.getIntegerInstance(locale)
    Column(modifier.testTag("subtitles-style"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.nag_style_title))
        StylePreview(style)
        LabelledRow(stringResource(R.string.nag_style_preset)) {
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitlePreset.entries.forEach { choice ->
                    PanelChip(stringResource(choice.label), selected = choice == preset, enabled = true) { onPreset(choice) }
                }
            }
        }
        FontRow(style, onStyle)
        LevelRow(
            label = stringResource(R.string.nag_style_size),
            value = style.sizePx,
            min = SubtitleStyle.MIN_SIZE_PX,
            max = SubtitleStyle.MAX_SIZE_PX,
            text = integers.format(style.sizePx),
            enabled = true,
            centred = false,
            labelWidth = LABEL_WIDTH,
            step = SubtitleStyle.SIZE_STEP_PX,
            neutralAt = SubtitleStyle.VLC_SIZE_PX,
        ) { onStyle(style.copy(sizePx = it)) }
        LevelRow(
            label = stringResource(R.string.nag_style_outline),
            value = style.outlinePx,
            min = SubtitleStyle.OUTLINES.first(),
            max = SubtitleStyle.OUTLINES.last(),
            text = stringResource(R.string.nag_style_px, integers.format(style.outlinePx)),
            enabled = true,
            centred = false,
            labelWidth = LABEL_WIDTH,
            step = SubtitleStyle.OUTLINES[1],
            neutralAt = SubtitleStyle.VLC_OUTLINE_PX,
        ) { onStyle(style.copy(outlinePx = it)) }
        NagCheckbox(style.shadow, { onStyle(style.copy(shadow = it)) }, stringResource(R.string.nag_style_shadow), Modifier.testTag("style-shadow"))
        LevelRow(
            label = stringResource(R.string.nag_style_background),
            value = style.backgroundPercent,
            min = 0,
            max = 100,
            text = NumberFormat.getPercentInstance(locale).format(style.backgroundPercent / 100.0),
            enabled = true,
            centred = false,
            labelWidth = LABEL_WIDTH,
            step = BACKGROUND_STEP,
        ) { onStyle(style.copy(backgroundPercent = it)) }
        ColourRow(style, onStyle)
        // the native side's: where the subtitles stand, the black bars, the ASS files' styles
        LevelRow(
            label = stringResource(R.string.nag_style_raise),
            value = style.raisePx,
            min = 0,
            max = SubtitleStyle.MAX_RAISE_PX,
            text = stringResource(R.string.nag_style_px, integers.format(style.raisePx)),
            enabled = layoutSupported,
            centred = false,
            labelWidth = LABEL_WIDTH,
            step = RAISE_STEP,
        ) { onStyle(style.copy(raisePx = it)) }
        NagCheckbox(
            style.inBlackBars,
            { onStyle(style.copy(inBlackBars = it)) },
            stringResource(R.string.nag_style_black_bars),
            Modifier.alpha(if (layoutSupported) 1f else PanelDisabledAlpha).testTag("style-black-bars"),
            enabled = layoutSupported,
        )
        LabelledRow(stringResource(R.string.nag_style_ass)) {
            ChoiceRow(
                options = AssStyles.entries.map { it to stringResource(it.label) },
                selected = style.ass,
                enabled = layoutSupported,
                onSelect = { onStyle(style.copy(ass = it)) },
                mono = false,
            )
        }
        if (waiting) PanelCaption(stringResource(R.string.nag_style_waiting), Modifier.testTag("style-waiting"))
        if (!layoutSupported) PendingNote("style-pending")
    }
}

/** The labels' column, as the tracks' rows have it. */
private val LABEL_WIDTH = 96.dp

private const val BACKGROUND_STEP = 5
private const val RAISE_STEP = 10

/** The preview's text: a 1080p picture's pixels in each of its sp, so that sizes compare. */
private const val PX_PER_SP = 3.4f

/**
 * A line as the look draws it, over a night like the film's: the font, its size against the
 * others, the colour, the outline, the shadow and the box.
 */
@Composable
private fun StylePreview(style: SubtitleStyle) {
    val sample = stringResource(R.string.nag_style_sample)
    val description = stringResource(R.string.nag_style_preview)
    val size = (style.sizePx / PX_PER_SP).sp
    val outline = with(LocalDensity.current) { (style.outlinePx / PX_PER_SP).sp.toPx() * 2 }
    val text = TextStyle(
        fontFamily = style.font.previewFamily(),
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = size,
        lineHeight = size * LINE_HEIGHT,
        textAlign = TextAlign.Center,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(NIGHT_TOP), Color(NIGHT_BOTTOM))))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = description }
            .testTag("style-preview"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val box = if (style.backgroundPercent > 0) {
            Modifier.background(Color.Black.copy(alpha = style.backgroundPercent / 100f), RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
        } else {
            Modifier
        }
        Box(box) {
            if (style.outlinePx > 0) {
                Text(sample, style = text.copy(color = Color.Black, drawStyle = Stroke(width = outline, join = StrokeJoin.Round)))
            }
            Text(
                sample,
                style = text.copy(
                    color = Color(style.colourArgb),
                    shadow = if (style.shadow) Shadow(Color.Black.copy(alpha = SHADOW_ALPHA), Offset(2f, 2f), blurRadius = 3f) else null,
                ),
            )
        }
    }
}

private const val LINE_HEIGHT = 1.25f
private const val SHADOW_ALPHA = 0.5f
private const val NIGHT_TOP = 0xFF0B1A2A
private const val NIGHT_BOTTOM = 0xFF15333A

/** How the preview draws a font: the system's family of the same name. */
private fun SubtitleFont.previewFamily(): FontFamily = when (this) {
    SubtitleFont.Sans -> FontFamily.SansSerif
    SubtitleFont.Serif -> FontFamily.Serif
    SubtitleFont.Mono -> FontFamily.Monospace
    SubtitleFont.Condensed -> FontFamily(Typeface.create(family, Typeface.NORMAL))
}

/** "Sans serif · Regular": the font and its weight, chosen from one list. */
@Composable
private fun FontRow(style: SubtitleStyle, onStyle: (SubtitleStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.nag_style_font)
    val regular = stringResource(R.string.nag_style_weight_regular)
    val bold = stringResource(R.string.nag_style_weight_bold)
    val names = SubtitleFont.entries.associateWith { stringResource(it.label) }
    fun named(font: SubtitleFont, heavy: Boolean) = listOf(names.getValue(font), if (heavy) bold else regular).joinToString(" · ")
    LabelledRow(label) {
        Box(Modifier.testTag("style-font")) {
            DropButton(named(style.font, style.bold), enabled = true, label = label) { open = true }
            MenuPopup(
                visible = open,
                onDismiss = { open = false },
                entries = SubtitleFont.entries.flatMap { font ->
                    listOf(false, true).map { heavy ->
                        MenuEntry.Item(named(font, heavy), { onStyle(style.copy(font = font, bold = heavy)) }, checked = style.font == font && style.bold == heavy)
                    }
                },
                offset = IntOffset(0, 0),
                minWidth = 220.dp,
            )
        }
    }
}

/** The colours, as round swatches; the chosen one ringed. */
@Composable
private fun ColourRow(style: SubtitleStyle, onStyle: (SubtitleStyle) -> Unit) {
    LabelledRow(stringResource(R.string.nag_style_colour)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SubtitleColour.entries.forEach { colour ->
                Swatch(colour, selected = style.colourArgb == colour.argb) { onStyle(style.copy(colourArgb = colour.argb)) }
            }
        }
    }
}

@Composable
private fun Swatch(colour: SubtitleColour, selected: Boolean, onSelect: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val name = stringResource(colour.label)
    Box(
        Modifier
            .size(28.dp)
            .border(2.dp, if (selected) colors.accent else Color.Transparent, CircleShape)
            .padding(4.dp)
            .background(Color(colour.argb), CircleShape)
            .border(1.dp, colors.borderStrong, CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = name }
            .testTag("style-colour-${colour.key}"),
    )
}
