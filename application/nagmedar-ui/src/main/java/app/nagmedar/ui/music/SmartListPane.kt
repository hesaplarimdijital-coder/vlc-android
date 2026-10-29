package app.nagmedar.ui.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ActionMenuButton
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme

/** S-805: a field's name in a rule: the table's column names, and the folder. */
@Composable
internal fun smartFieldLabel(field: SmartField): String = stringResource(
    when (field) {
        SmartField.Title -> R.string.nag_column_title
        SmartField.Artist -> R.string.nag_column_artist
        SmartField.AlbumArtist -> R.string.nag_column_album_artist
        SmartField.Album -> R.string.nag_column_album
        SmartField.Genre -> R.string.nag_column_genre
        SmartField.Composer -> R.string.nag_column_composer
        SmartField.Folder -> R.string.nag_smart_field_folder
        SmartField.Format -> R.string.nag_column_format
        SmartField.Depth -> R.string.nag_column_depth
        SmartField.Rate -> R.string.nag_column_rate
        SmartField.Year -> R.string.nag_column_year
        SmartField.Duration -> R.string.nag_column_duration
        SmartField.Rating -> R.string.nag_column_rating
        SmartField.Plays -> R.string.nag_column_plays
        SmartField.Skips -> R.string.nag_column_skips
        SmartField.LastPlayed -> R.string.nag_column_last_played
        SmartField.Added -> R.string.nag_column_added
    },
)

/** An operator's words: a date's "in the last N days" reads "at most N days ago". */
@Composable
internal fun smartOpLabel(op: SmartOp): String = stringResource(
    when (op) {
        SmartOp.Contains -> R.string.nag_smart_op_contains
        SmartOp.NotContains -> R.string.nag_smart_op_not_contains
        SmartOp.Is -> R.string.nag_smart_op_is
        SmartOp.IsNot -> R.string.nag_smart_op_is_not
        SmartOp.StartsWith -> R.string.nag_smart_op_starts_with
        SmartOp.AtLeast, SmartOp.NotInLast -> R.string.nag_smart_op_at_least
        SmartOp.AtMost, SmartOp.InLast -> R.string.nag_smart_op_at_most
        SmartOp.Never -> R.string.nag_smart_op_never
    },
)

/** What follows a rule's value: "kHz", "dk", "kez", "gün önce"; null for none. */
@Composable
internal fun smartUnit(rule: SmartRule): String? = when {
    rule.op == SmartOp.Never -> null
    rule.field == SmartField.Rate -> stringResource(R.string.nag_smart_unit_khz)
    rule.field == SmartField.Duration -> stringResource(R.string.nag_smart_unit_minutes)
    rule.field == SmartField.Plays || rule.field == SmartField.Skips -> stringResource(R.string.nag_smart_unit_times)
    rule.field.kind == SmartKind.Date -> stringResource(R.string.nag_smart_unit_days_ago)
    else -> null
}

/** A template's name. */
@Composable
internal fun smartTemplateLabel(template: SmartTemplate): String = stringResource(
    when (template) {
        SmartTemplate.Favourites -> R.string.nag_smart_t_favourites
        SmartTemplate.MostPlayed -> R.string.nag_smart_t_most_played
        SmartTemplate.NeverPlayed -> R.string.nag_smart_t_never_played
        SmartTemplate.RecentlyAdded -> R.string.nag_smart_t_recently_added
        SmartTemplate.HiRes -> R.string.nag_smart_t_hi_res
        SmartTemplate.Forgotten -> R.string.nag_smart_t_forgotten
    },
)

/** The list's rules in a line: "Puan en az ★★★★ · Son çalınma en az 90 gün önce". */
@Composable
internal fun smartSummary(list: SmartList): String {
    val parts = list.rules.filter { it.complete }.map { rule ->
        val value = when {
            rule.op == SmartOp.Never -> null
            rule.field.kind == SmartKind.Stars -> "★".repeat(rule.value.toDoubleOrNull()?.toInt()?.coerceIn(0, 5) ?: 0)
            rule.field.kind == SmartKind.Depth -> runCatching { Depth.valueOf(rule.value) }.getOrNull()?.let { depthLabel(it) }
            rule.field.kind == SmartKind.Text -> "“${rule.value.trim()}”"
            else -> rule.value.trim()
        }
        listOfNotNull(smartFieldLabel(rule.field), smartOpLabel(rule.op), value, smartUnit(rule)).joinToString(" ")
    }
    if (parts.isEmpty()) return stringResource(R.string.nag_smart_all_tracks)
    return parts.joinToString(if (list.matchAll) " · " else " ${stringResource(R.string.nag_smart_or)} ")
}

/**
 * S-805: a smart list open in the playlists screen: its head (cover, name, what it asks, play and
 * shuffle, its rules to edit, its menu), its tracks (null: the library still being read). The
 * tracks are the library's, in the list's order; a click plays (a pointer's: selects, a double
 * click plays). S-1601: the name is changed where it stands ([renaming]); with [onBack] a button
 * leads back to the lists.
 */
@Composable
internal fun SmartPane(
    list: SmartList,
    tracks: List<Track>?,
    stats: Map<String, TrackStats>,
    actions: MusicActions,
    onEdit: () -> Unit,
    menu: () -> List<MenuEntry>,
    onBack: (() -> Unit)?,
    onRename: (String) -> Unit,
    renaming: Boolean,
    onRenaming: (Boolean) -> Unit,
) {
    val compact = currentWidthClass() == WidthClass.Compact
    val pointer = NagTheme.inputMode == InputMode.Pointer
    Column(Modifier.fillMaxSize().testTag("smart-pane")) {
        val playable = tracks.orEmpty()
        val covers = remember(tracks) { PlaylistCards.coversOf(playable) }
        ListDetailHeader(
            name = list.name,
            onRename = onRename,
            editing = renaming,
            onEditing = onRenaming,
            covers = covers,
            seed = "s:${list.id}",
            smart = true,
            lines = listOfNotNull(tracks?.let { playlistMeta(it.size, it.sumOf { track -> track.durationMs.coerceAtLeast(0) }) }, smartSummary(list)),
            onBack = onBack,
            actions = { stacked ->
                NagButton(stringResource(R.string.nag_play), { actions.play(playable, 0) }, icon = NagIcons.Play, primary = true, enabled = playable.isNotEmpty())
                NagButton(stringResource(R.string.nag_shuffle), { actions.play(playable.shuffled(), 0) }, icon = NagIcons.Shuffle, enabled = playable.size > 1)
                // beside the name there is room for the rules' button; under it they are in the menu
                if (!stacked) NagButton(stringResource(R.string.nag_smart_edit), onEdit, icon = NagIcons.SmartList)
                ActionMenuButton(NagIcons.MoreHorizontal, stringResource(R.string.nag_more_actions), menu, sheetTitle = list.name)
            },
        )
        HorizontalRule()
        when {
            tracks == null -> Unit
            tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.SmartList,
                    title = stringResource(R.string.nag_smart_empty_title),
                    description = stringResource(R.string.nag_smart_empty_desc),
                    primary = StateAction(stringResource(R.string.nag_smart_edit), onEdit),
                )
            }
            else -> {
                var selected by remember(list.id) { mutableStateOf<String?>(null) }
                val columns = if (compact) {
                    listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Duration)
                } else {
                    listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Album, MusicColumn.Format, MusicColumn.Duration, MusicColumn.Rating, MusicColumn.Plays)
                }
                val tableColumns = rememberMusicColumns(columns, stats, onRate = if (actions.askRating != null) ({ track, stars -> actions.rate(listOf(track), stars) }) else null)
                // the list's own order: the header does not sort it
                val fixed = remember(tableColumns) {
                    tableColumns.map { column -> TableColumn(column.title, column.width, column.alignEnd, column.mono, sortable = false, content = column.content, value = column.value) }
                }
                DenseTable(
                    columns = fixed,
                    rows = tracks,
                    key = { it.statsKey },
                    selectedKey = selected,
                    onRowClick = { track, index -> if (pointer) selected = track.statsKey else actions.play(tracks, index) },
                    onRowDoubleClick = if (pointer) ({ _, index -> actions.play(tracks, index) }) else null,
                    rowMenu = { track, index -> actions.menu(track, play = { actions.play(tracks, index) }, select = null) },
                    rowMenuLabel = stringResource(R.string.nag_more_actions),
                    rowMenuAsSheet = true,
                    rowModifier = { track -> actions.rowModifier(track) { listOf(track) } },
                    contentPadding = PaddingValues(bottom = 8.dp) + LocalShellBottomPadding.current,
                )
            }
        }
    }
}
