package app.nagmedar.ui.player.subtitles

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.player.panel.ChoiceRow
import app.nagmedar.ui.player.panel.DropButton
import app.nagmedar.ui.player.panel.LabelledRow
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelDisabledAlpha
import app.nagmedar.ui.player.panel.PanelRule
import app.nagmedar.ui.player.panel.PanelSection
import app.nagmedar.ui.player.panel.PendingNote
import app.nagmedar.ui.player.panel.SectionTitle
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.NagTheme

/** Where the secondary subtitles show (S-703): at the top of the picture, or right above the primary ones. */
enum class SecondaryPosition(val key: String, @StringRes val label: Int) {
    Top("top", R.string.nag_subs_top),
    AbovePrimary("above", R.string.nag_subs_above),
}

/**
 * The native side of the secondary subtitles' own place (VLC 4's secondary subtitle alignment
 * and margin, through the JNI work of S-503); this build's libVLC does not have it
 * ([PendingSecondaryLayout]).
 */
interface SecondaryLayoutTarget {
    val supported: Boolean

    fun setPosition(position: SecondaryPosition)
}

object PendingSecondaryLayout : SecondaryLayoutTarget {
    override val supported: Boolean = false

    override fun setPosition(position: SecondaryPosition) = Unit
}

/**
 * The subtitles' tab of the player's panel (S-703), after the mockups' player: its tracks, the
 * primary and the secondary one shown with it (a switch turns it on and off, as Alt+V does), the
 * file the subtitles came from; the secondary's place, and the forced subtitles, which wait for
 * the player's native side.
 */
@Composable
fun SubtitlesPanel(
    state: DualSubtitles,
    /** "Kuzey Işığı.tr.srt · matched from the folder": where the last subtitles added came from. */
    note: String?,
    layoutSupported: Boolean,
    position: SecondaryPosition,
    onPrimary: (String?) -> Unit,
    onSecondary: (String?) -> Unit,
    onSecondaryOn: (Boolean) -> Unit,
    onPosition: (SecondaryPosition) -> Unit,
    modifier: Modifier = Modifier,
    /** Under the video in a wide window: two columns of sections. */
    columns: Int = 1,
) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("subtitles-panel")) {
        val tracks = @Composable { TracksSection(state, note, onPrimary, onSecondary, onSecondaryOn) }
        val secondary = @Composable { SecondarySection(layoutSupported, position, onPosition) }
        if (columns >= 2) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) { tracks() }
                Column(Modifier.weight(1f)) { secondary() }
            }
        } else {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                tracks()
                PanelRule()
                secondary()
            }
        }
    }
}

@Composable
private fun TracksSection(
    state: DualSubtitles,
    note: String?,
    onPrimary: (String?) -> Unit,
    onSecondary: (String?) -> Unit,
    onSecondaryOn: (Boolean) -> Unit,
) {
    val off = stringResource(R.string.nag_player_subtitles_off)
    val none = stringResource(R.string.nag_subs_none)
    PanelSection(spacing = 6.dp) {
        SectionTitle(stringResource(R.string.nag_subs_tracks))
        val primaryLabel = stringResource(R.string.nag_subs_primary)
        TrackRow(
            label = primaryLabel,
            shown = state.primaryTrack?.name ?: off,
            entries = listOf(MenuEntry.Item(off, { onPrimary(null) }, checked = state.primary == null)) +
                state.tracks.map { track -> MenuEntry.Item(track.name, { onPrimary(track.id) }, checked = track.id == state.primary) },
            enabled = state.tracks.isNotEmpty(),
            tag = "subtitles-primary",
        )
        val secondaryLabel = stringResource(R.string.nag_subs_secondary)
        val others = state.tracks.filter { it.id != state.primary }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TrackRow(
                    label = secondaryLabel,
                    shown = state.secondaryTrack?.takeIf { state.secondary != state.primary }?.name ?: none,
                    entries = listOf(MenuEntry.Item(none, { onSecondary(null) }, checked = !state.secondaryOn)) +
                        others.map { track -> MenuEntry.Item(track.name, { onSecondary(track.id) }, checked = state.secondaryOn && track.id == state.secondary) },
                    enabled = state.primary != null && others.isNotEmpty(),
                    tag = "subtitles-secondary",
                )
            }
            PanelSwitch(
                checked = state.secondaryOn,
                enabled = state.primary != null && others.isNotEmpty(),
                label = stringResource(R.string.nag_subs_secondary_switch),
                onToggle = onSecondaryOn,
            )
        }
        if (note != null) PanelCaption(note, Modifier.testTag("subtitles-note"))
        // S-503: the forced subtitles of the language chosen, the native side's to show
        NagCheckbox(false, {}, stringResource(R.string.nag_subs_forced), Modifier.alpha(PanelDisabledAlpha), enabled = false)
    }
}

/** A track to choose from a list: its name on the left, the button showing the choice. */
@Composable
private fun TrackRow(label: String, shown: String, entries: List<MenuEntry>, enabled: Boolean, tag: String) {
    var open by remember { mutableStateOf(false) }
    LabelledRow(label) {
        Box(Modifier.testTag(tag)) {
            DropButton(shown, enabled, label) { open = true }
            MenuPopup(visible = open, onDismiss = { open = false }, entries = entries, offset = IntOffset(0, 0), minWidth = 220.dp)
        }
    }
}

/** The secondary subtitles: where they show, and the key that turns them on and off. */
@Composable
private fun SecondarySection(supported: Boolean, position: SecondaryPosition, onPosition: (SecondaryPosition) -> Unit) {
    val resources = LocalContext.current.resources
    PanelSection(spacing = 8.dp) {
        SectionTitle(stringResource(R.string.nag_subs_secondary_title))
        LabelledRow(stringResource(R.string.nag_subs_position)) {
            ChoiceRow(
                options = SecondaryPosition.entries.map { it to stringResource(it.label) },
                selected = position,
                enabled = supported,
                onSelect = onPosition,
                mono = false,
            )
        }
        ShortcutRegistry.chordLabel(ShortcutId.SecondarySubtitles, resources)?.let { keys ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyCap(keys)
                PanelCaption(stringResource(R.string.nag_subs_keys))
            }
        }
        if (!supported) PendingNote("subtitles-pending")
    }
}

/** A switch alone, its name for the screen reader: the secondary subtitles on or off. */
@Composable
private fun PanelSwitch(checked: Boolean, enabled: Boolean, label: String, onToggle: (Boolean) -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .alpha(if (enabled) 1f else PanelDisabledAlpha)
            .size(width = 38.dp, height = 24.dp)
            .background(if (checked) colors.accent else colors.borderStrong, RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = label }
            .testTag("subtitles-secondary-switch"),
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
