package app.nagmedar.ui.player.equalizer

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.EqCard
import app.nagmedar.ui.audio.decibels
import app.nagmedar.ui.audio.frequencyText
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.GainSlider
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.gainText
import app.nagmedar.ui.player.effects.EqualizerPreset
import app.nagmedar.ui.player.effects.PlayerEffects
import app.nagmedar.ui.player.panel.DropButton
import app.nagmedar.ui.prefs.ConfirmDialog
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.theme.NagTheme
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The player's equaliser window (Ctrl+Shift+E, the player's menu, the effects tab's button):
 * VLC's equaliser, which the film, the radio and the streams play through. On or off, one of
 * libVLC's presets or one of the user's own (a preset copied to change it), its preamp and its
 * ten bands; a film with an equaliser of its own from the effects tab says so, with the way back
 * to the player's setting. Tab goes through it; a band takes the arrows (Page Up and Down six
 * steps, Home and End to the ends, Delete back to 0 dB).
 */
@Composable
fun PlayerEqualizerScreen(controller: PlayerEqualizerController, filmChoice: String?) {
    val state = controller.state
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("player-equalizer")) {
        PageHeader(title = stringResource(R.string.nag_eq_title), subtitle = stringResource(R.string.nag_peq_subtitle))
        Column(Modifier.padding(horizontal = dimens.pagePadding), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
            if (filmChoice != null) FilmOwnCard(filmChoice, controller::followPlayer)
            when {
                state.loading -> Unit
                state.failed -> Text(stringResource(R.string.nag_peq_failed), style = NagTheme.type.body, color = NagTheme.colors.danger)
                else -> {
                    ChoiceBar(state, controller)
                    BandsCard(state, controller)
                }
            }
        }
        Spacer(Modifier.height(dimens.gapXl))
    }
}

/** [equalizer]'s name as the window shows it: a preset by VLC's name for it in the interface's language ("Güçlü bas"), the user's own by theirs. */
internal fun equalizerName(resources: Resources, equalizer: VlcEqualizer): String =
    if (equalizer.own) equalizer.name else EqualizerPreset.entries.getOrNull(equalizer.presetIndex)?.let { resources.getString(it.label) } ?: equalizer.name

/** The film plays with its own equaliser from the effects tab: what it is, and the way back to the player's. */
@Composable
private fun FilmOwnCard(choice: String, onFollow: () -> Unit) {
    val name = if (choice == PlayerEffects.EQUALIZER_OFF) {
        stringResource(R.string.nag_fx_off)
    } else {
        EqualizerPreset.of(choice)?.let { stringResource(it.label) } ?: choice
    }
    EqCard(stringResource(R.string.nag_peq_film_title), Modifier.testTag("player-equalizer-film")) {
        Text(stringResource(R.string.nag_peq_film_own, name), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
        NagButton(stringResource(R.string.nag_peq_film_follow), onFollow)
    }
}

/** On or off, the equaliser chosen, and what can be done with it: a preset copied, the user's own renamed or deleted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceBar(state: PlayerEqualizerState, controller: PlayerEqualizerController) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    var deleting by remember { mutableStateOf(false) }
    val current = state.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = dimens.gapL, vertical = dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapM), verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
            // each the bar's height, so they line up whatever their own
            val item = Modifier.heightIn(min = 48.dp)
            Box(item, contentAlignment = Alignment.CenterStart) {
                NagSwitch(checked = state.enabled, onCheckedChange = controller::setEnabled, label = stringResource(R.string.nag_peq_on))
            }
            Box(item, contentAlignment = Alignment.CenterStart) { Choice(state, controller::choose) }
            if (current != null && !current.own) {
                Box(item, contentAlignment = Alignment.CenterStart) {
                    NagButton(stringResource(R.string.nag_peq_copy), controller::copy, icon = NagIcons.Plus, enabled = !state.copying)
                }
            }
            if (current != null && current.own) {
                Box(item.width(240.dp), contentAlignment = Alignment.CenterStart) {
                    NagTextField(value = state.name, onValueChange = controller::rename, label = stringResource(R.string.nag_peq_name))
                }
                Box(item, contentAlignment = Alignment.CenterStart) {
                    NagIconButton(NagIcons.Delete, stringResource(R.string.nag_peq_delete_own), { deleting = true })
                }
            }
        }
        if (state.nameTaken) Text(stringResource(R.string.nag_peq_name_taken), style = NagTheme.type.labelSmall, color = colors.danger)
        if (!state.enabled) Text(stringResource(R.string.nag_peq_off_note), style = NagTheme.type.labelSmall, color = colors.warning)
    }
    if (deleting && current != null && current.own) {
        ConfirmDialog(
            title = stringResource(R.string.nag_peq_delete_title, current.name),
            message = stringResource(R.string.nag_peq_delete_message),
            button = stringResource(R.string.nag_peq_delete),
            onConfirm = controller::delete,
            onDismiss = { deleting = false },
        )
    }
}

/** The equaliser chosen: libVLC's presets by VLC's names, then the user's own. */
@Composable
private fun Choice(state: PlayerEqualizerState, onChoose: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val label = stringResource(R.string.nag_eq_preset)
    val chosen = state.current?.id
    val presets = state.presets.map { eq -> MenuEntry.Item(equalizerName(resources, eq), { onChoose(eq.id) }, checked = eq.id == chosen) }
    val own = state.own.map { eq -> MenuEntry.Item(eq.name, { onChoose(eq.id) }, checked = eq.id == chosen) }
    val entries = if (own.isEmpty()) presets else presets + MenuEntry.Separator + own
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NagTheme.type.label, color = NagTheme.colors.textSecondary)
        Spacer(Modifier.width(NagTheme.dimens.gapS))
        Box(Modifier.width(200.dp)) {
            DropButton(state.current?.let { equalizerName(resources, it) }.orEmpty(), enabled = true, label = label) { open = true }
            MenuPopup(visible = open, onDismiss = { open = false }, entries = entries, offset = IntOffset(0, 0), minWidth = 220.dp)
        }
    }
}

/** The preamp and the ten bands, ±20 dB as libVLC takes them; a preset's are shown as they are, not moved. */
@Composable
private fun BandsCard(state: PlayerEqualizerState, controller: PlayerEqualizerController) {
    val current = state.current ?: return
    val locale = LocalContext.current.resources.locale()
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val editable = state.editable
    val hint = stringResource(if (current.own) R.string.nag_peq_own_hint else R.string.nag_peq_preset_hint)
    EqCard(stringResource(R.string.nag_peq_bands), Modifier.alpha(if (state.enabled) 1f else DIMMED), hint = hint) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BandColumn(
                PREAMP_WIDTH, current.preampDb, stringResource(R.string.nag_eq_preamp), stringResource(R.string.nag_eq_preamp),
                separator, locale, editable, controller::setPreamp,
            )
            VerticalRule(Modifier.height(BAND_HEIGHT).padding(horizontal = NagTheme.dimens.gapXs))
            BoxWithConstraints(Modifier.weight(1f)) {
                // as many equal columns as bands, each at least as wide as its slider and labels; scrolled on a narrow window
                val least = maxOf(NagTheme.dimens.minTarget, BAND_WIDTH)
                val share = maxWidth / current.bandsDb.size.coerceAtLeast(1)
                val fits = share >= least
                Row(
                    if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = if (fits) Arrangement.Start else Arrangement.spacedBy(2.dp),
                ) {
                    current.bandsDb.forEachIndexed { band, db ->
                        val hz = state.frequencies.getOrNull(band)?.toDouble() ?: 0.0
                        BandColumn(
                            if (fits) share else least, db, frequencyText(hz, locale, compact = true),
                            stringResource(R.string.nag_eq_band, frequencyText(hz, locale, compact = false)),
                            separator, locale, editable,
                        ) { controller.setBand(band, it) }
                    }
                }
            }
        }
    }
}

/** One slider of the card: its value above it, its name ([caption]) under it. */
@Composable
private fun BandColumn(
    width: Dp,
    db: Float,
    caption: String,
    label: String,
    separator: Char,
    locale: Locale,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val colors = NagTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width)) {
        Text(
            gainText(db.toDouble(), separator), style = NagTheme.type.caption, color = if (db == 0f) colors.textTertiary else colors.text,
            maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true),
        )
        GainSlider(
            value = db.toDouble(),
            onValueChange = { onChange(it.toFloat()) },
            label = label,
            valueText = decibels(db.toDouble(), locale),
            range = RANGE_DB,
            step = STEP_DB,
            enabled = enabled,
            modifier = Modifier.height(BAND_HEIGHT),
        )
        Text(
            caption, style = NagTheme.type.caption, color = colors.textTertiary,
            maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}

/** libVLC's range for the preamp and the bands, and the sliders' step. */
private const val RANGE_DB = 20.0
private const val STEP_DB = 0.5

private val BAND_HEIGHT = 170.dp

/** A band's column at the least: its labels ("14k", "+12,5"). */
private val BAND_WIDTH = 34.dp

/** The preamp's column: room for its name under it. */
private val PREAMP_WIDTH = 76.dp

/** The bands while the equaliser is off. */
private const val DIMMED = 0.55f
