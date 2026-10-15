package app.nagmedar.ui.player.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.panel.LabelledRow
import app.nagmedar.ui.player.panel.PanelBadge
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelSection
import app.nagmedar.ui.player.panel.SectionTitle
import app.nagmedar.ui.player.panel.SwitchRow
import app.nagmedar.ui.player.panel.TextAction
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat

/** What the advanced effects' section shows (S-610). */
@Immutable
data class AdvancedVideoView(
    val video: AdvancedVideo,
    /** S-505's mirror and quarter turns, drawn in the preview too. */
    val transform: VideoTransform = VideoTransform(),
    /** A still frame of the film; null draws the sample picture. */
    val frame: ImageBitmap? = null,
    /** Where [frame] comes from: the file before any effect, or the screen while none shows. */
    val source: PreviewSource = if (frame != null) PreviewSource.Raw else PreviewSource.Sample,
    val logo: ImageBitmap? = null,
    val pictureHeight: Int = AdvancedVideoOptions.DEFAULT_HEIGHT,
    /** What is set is not what libVLC shows yet. */
    val waiting: Boolean = false,
    /** "Apply now" can reopen the film; else why not ([recording]). */
    val canApplyNow: Boolean = false,
    val recording: Boolean = false,
    val applying: Boolean = false,
    val logoFailed: Boolean = false,
    /** The clock the preview's text shows. */
    val nowMs: Long = 0L,
)

/** What the section does (S-610). */
class AdvancedVideoActions(
    val onChange: ((AdvancedVideo) -> AdvancedVideo) -> Unit,
    val onPickLogo: () -> Unit = {},
    val onNewFrame: () -> Unit = {},
    val onApplyNow: () -> Unit = {},
)

/**
 * The effects tab's advanced video section (S-610): a preview of them on a still frame, the
 * warning when one works on the CPU, then the logo, the text, the free angle and anaglyph 3D,
 * and "Apply now". They last for the session, for every film ([AdvancedVideoSession]).
 */
@Composable
internal fun AdvancedVideoSection(view: AdvancedVideoView, actions: AdvancedVideoActions) {
    val video = view.video
    val onChange = actions.onChange
    val locale = currentLocale()
    val percent = NumberFormat.getPercentInstance(locale)
    PanelSection(spacing = 6.dp) {
        SectionTitle(stringResource(R.string.nag_fx_advanced)) {
            if (!video.isNeutral) {
                PanelBadge(stringResource(if (video.onCpu) R.string.nag_fx_cost_cpu else R.string.nag_fx_cost_light), Modifier.testTag("advanced-cost"))
            }
        }
        val description = stringResource(
            when {
                view.frame == null -> R.string.nag_fx_preview_sample
                view.source == PreviewSource.Screen -> R.string.nag_fx_preview_frame
                else -> R.string.nag_fx_preview_raw
            },
        )
        EffectPreview(view.frame, video, view.transform, view.logo, view.pictureHeight, view.nowMs, description)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelCaption(description, Modifier.weight(1f))
            TextAction(stringResource(R.string.nag_fx_preview_refresh), enabled = true, onClick = actions.onNewFrame)
        }
        if (video.onCpu) Warning(stringResource(R.string.nag_fx_cpu_warning), "advanced-warning")
        if (!view.transform.isNeutral) PanelCaption(stringResource(R.string.nag_fx_gl_note))

        // the logo
        val logo = video.logo
        SwitchRow(
            stringResource(R.string.nag_fx_logo),
            if (view.logoFailed) stringResource(R.string.nag_fx_logo_failed) else logo.name ?: stringResource(R.string.nag_fx_logo_none),
            logo.enabled,
            enabled = true,
        ) { on -> if (on && logo.file == null) actions.onPickLogo() else onChange { it.copy(logo = it.logo.copy(enabled = on)) } }
        if (logo.enabled) {
            Box(Modifier.padding(start = 2.dp)) { TextAction(stringResource(R.string.nag_fx_logo_pick), enabled = true, onClick = actions.onPickLogo) }
            SelectRow(stringResource(R.string.nag_fx_position), logo.position, OverlayPosition.entries, enabled = true) { p -> onChange { it.copy(logo = it.logo.copy(position = p)) } }
            LevelRow(stringResource(R.string.nag_fx_opacity), logo.opacityPercent, 0, 100, percent.format(logo.opacityPercent / 100.0), enabled = true, centred = false, labelWidth = 96.dp, step = 5, neutralAt = 100) { v ->
                onChange { it.copy(logo = it.logo.copy(opacityPercent = v)) }
            }
        }

        // the text
        val text = video.text
        SwitchRow(stringResource(R.string.nag_fx_text), stringResource(R.string.nag_fx_text_hint), text.enabled, enabled = true) { on ->
            onChange { it.copy(text = it.text.copy(enabled = on)) }
        }
        if (text.enabled) {
            TextField(text.text) { value -> onChange { it.copy(text = it.text.copy(text = value.take(MAX_TEXT))) } }
            SelectRow(stringResource(R.string.nag_fx_clock), text.clock, TextClock.entries, enabled = true) { c -> onChange { it.copy(text = it.text.copy(clock = c)) } }
            SelectRow(stringResource(R.string.nag_fx_position), text.position, OverlayPosition.entries, enabled = true) { p -> onChange { it.copy(text = it.text.copy(position = p)) } }
            SelectRow(stringResource(R.string.nag_fx_text_size), text.size, TextSize.entries, enabled = true) { s -> onChange { it.copy(text = it.text.copy(size = s)) } }
            LabelledRow(stringResource(R.string.nag_fx_colour)) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OVERLAY_COLOURS.forEachIndexed { i, colour ->
                        Swatch(colour, stringResource(COLOUR_NAMES[i]), selected = text.colour == colour) { onChange { it.copy(text = it.text.copy(colour = colour)) } }
                    }
                }
            }
            LevelRow(stringResource(R.string.nag_fx_opacity), text.opacityPercent, 0, 100, percent.format(text.opacityPercent / 100.0), enabled = true, centred = false, labelWidth = 96.dp, step = 5, neutralAt = 100) { v ->
                onChange { it.copy(text = it.text.copy(opacityPercent = v)) }
            }
        }

        // the free angle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                LevelRow(
                    stringResource(R.string.nag_fx_angle),
                    video.angle,
                    0,
                    359,
                    stringResource(R.string.nag_fx_value_degrees, NumberFormat.getIntegerInstance(locale).format(video.angle)),
                    enabled = true,
                    centred = false,
                    labelWidth = 96.dp,
                ) { v -> onChange { it.copy(angle = v) } }
            }
            TextAction(stringResource(R.string.nag_fx_reset), enabled = video.angle != 0) { onChange { it.copy(angle = 0) } }
        }

        // anaglyph 3D
        SwitchRow(stringResource(R.string.nag_fx_3d), stringResource(R.string.nag_fx_3d_hint), video.anaglyph, enabled = true) { on -> onChange { it.copy(anaglyph = on) } }
        if (video.anaglyph) {
            SelectRow(stringResource(R.string.nag_fx_3d_scheme), video.anaglyphScheme, AnaglyphScheme.entries, enabled = true) { s -> onChange { it.copy(anaglyphScheme = s) } }
        }

        // when they show
        PanelCaption(
            stringResource(if (view.waiting) R.string.nag_fx_advanced_waiting else R.string.nag_fx_session),
            Modifier.testTag("advanced-when"),
        )
        if (view.waiting) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NagButton(
                    stringResource(if (view.applying) R.string.nag_fx_applying else R.string.nag_fx_apply_now),
                    actions.onApplyNow,
                    enabled = view.canApplyNow && !view.applying,
                    modifier = Modifier.testTag("advanced-apply"),
                )
                if (view.recording) PanelCaption(stringResource(R.string.nag_fx_apply_recording), Modifier.weight(1f))
            }
        }
    }
}

/** A warning: an icon in the warning colour and what it costs. */
@Composable
private fun Warning(text: String, tag: String) {
    val colors = NagTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
            .border(1.dp, colors.warning.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(NagIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
        Text(text, style = NagTheme.type.labelSmall, color = colors.text)
    }
}

/** The text over the video, typed in: one line. */
@Composable
private fun TextField(value: String, onValue: (String) -> Unit) {
    val colors = NagTheme.colors
    val label = stringResource(R.string.nag_fx_text)
    val placeholder = stringResource(R.string.nag_fx_text_placeholder)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .border(1.dp, colors.borderStrong, RoundedCornerShape(7.dp))
            .background(colors.background, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = NagTheme.type.label, color = colors.textTertiary)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = NagTheme.type.label.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.testTag("advanced-text"),
        )
    }
}

/** One of the text's colours, a round swatch; the chosen one ringed. */
@Composable
private fun Swatch(colour: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    Box(
        Modifier
            .size(26.dp)
            .focusRing(state, CircleShape)
            .border(2.dp, if (selected) colors.accent else Color.Transparent, CircleShape)
            .padding(3.dp)
            .background(Color(0xFF000000.toInt() or colour), CircleShape)
            .border(1.dp, colors.borderStrong, CircleShape)
            .nagClickable(interaction, onClick, role = Role.RadioButton, onClickLabel = name)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
    )
}

/** The names of [OVERLAY_COLOURS], in their order. */
private val COLOUR_NAMES = listOf(
    R.string.nag_fx_colour_white,
    R.string.nag_fx_colour_yellow,
    R.string.nag_fx_colour_cyan,
    R.string.nag_fx_colour_green,
    R.string.nag_fx_colour_red,
    R.string.nag_fx_colour_black,
)

private const val MAX_TEXT = 120
