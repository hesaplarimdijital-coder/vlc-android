package app.nagmedar.ui.guide

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.input.Shortcut
import app.nagmedar.ui.input.ShortcutGroup
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutPreset
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.screens.FilterField
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.search.foldForSearch
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme

/** One line of the shortcut card: what a shortcut does, its keys now (null: none), where it works, and whether the user changed them. */
@Immutable
data class ShortcutCardRow(
    val id: ShortcutId,
    val label: String,
    val keys: String?,
    val scope: ShortcutScope,
    val changed: Boolean,
)

/** A menu's shortcuts: the card's section for [group]. */
@Immutable
data class ShortcutCardSection(val group: ShortcutGroup, val title: String, val rows: List<ShortcutCardRow>)

/**
 * The shortcut card's content (S-1504), made from the table in use (the shipped keys with the
 * user's, [ShortcutRegistry]), in the order of the menus. [query] keeps the rows whose name, keys
 * or menu hold every word of it, folded the Turkish way.
 */
object ShortcutCard {

    fun sections(res: Resources, table: List<Shortcut> = ShortcutRegistry.shortcuts, query: String = ""): List<ShortcutCardSection> {
        val words = foldForSearch(query).split(' ', '\t').filter { it.isNotBlank() }
        return ShortcutGroup.entries.mapNotNull { group ->
            val title = res.getString(group.title)
            val rows = table.filter { it.group == group }.map { shortcut ->
                ShortcutCardRow(
                    id = shortcut.id,
                    label = res.getString(shortcut.label),
                    keys = shortcut.chord?.label(res),
                    scope = shortcut.scope,
                    changed = ShortcutRegistry.default(shortcut.id).chord != shortcut.chord,
                )
            }.filter { row ->
                val text = foldForSearch("${row.label} ${row.keys.orEmpty()} $title")
                words.all { it in text }
            }
            if (rows.isEmpty()) null else ShortcutCardSection(group, title, rows)
        }
    }

    /** The card as plain text, to share: a title, the preset, then each menu with "keys · name" lines. */
    fun text(res: Resources, sections: List<ShortcutCardSection>, presetName: String): String = buildString {
        appendLine(res.getString(R.string.nag_card_text_title, res.getString(R.string.nagmedar_shell_title), res.getString(R.string.nag_shortcut_card_title)))
        appendLine(res.getString(R.string.nag_card_preset, presetName))
        for (section in sections) {
            appendLine()
            appendLine(section.title)
            for (row in section.rows) {
                val keys = row.keys ?: res.getString(R.string.nag_card_unassigned)
                val where = scopeNote(res, row.scope)?.let { " ($it)" }.orEmpty()
                appendLine("$keys · ${row.label}$where")
            }
        }
    }

    /** Where a shortcut works when that is not everywhere; null for the global ones. */
    fun scopeNote(res: Resources, scope: ShortcutScope): String? = when (scope) {
        ShortcutScope.Global -> null
        ShortcutScope.Editor -> res.getString(R.string.nag_card_scope_editor)
    }
}

/**
 * The shortcut card (S-1504, Ctrl+/): a read-only overview of every keyboard shortcut by menu, as
 * the table has them now, the preset's and the user's own, with a search field and a button that
 * shares it as text. The editor stays in Settings ([onEdit]); the guide is [onOpenGuide]. In
 * columns on wide windows, one on a phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutCardScreen(onEdit: () -> Unit, onOpenGuide: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val res = context.resources
    val dimens = NagTheme.dimens
    // the table's state: the card follows a change of keys at once
    val overrides = ShortcutRegistry.overrides
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(overrides) { ShortcutCard.sections(res) }
    val shown = remember(overrides, query) { if (query.isBlank()) all else ShortcutCard.sections(res, query = query) }
    val presetName = ShortcutPreset.of(overrides)?.let { stringResource(it.label) } ?: stringResource(R.string.nag_card_preset_custom)
    val count = shown.sumOf { it.rows.size }
    val columns = when (currentWidthClass()) {
        WidthClass.Compact -> 1
        WidthClass.Medium, WidthClass.Expanded -> 2
        WidthClass.Large, WidthClass.ExtraLarge -> 3
    }
    Column(modifier.fillMaxSize().testTag("shortcut-card")) {
        PageHeader(
            title = stringResource(R.string.nag_shortcut_card_title),
            subtitle = pluralStringResource(R.plurals.nag_card_count, count, count),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.gapM),
        ) {
            Text(
                stringResource(R.string.nag_card_hint, stringResource(R.string.nag_card_preset, presetName)),
                style = NagTheme.type.body,
                color = NagTheme.colors.textSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                NagButton(stringResource(R.string.nag_card_edit), onEdit, primary = true, icon = NagIcons.Keyboard)
                NagButton(stringResource(R.string.nag_guide_title), onOpenGuide, icon = NagIcons.Info)
                NagButton(stringResource(R.string.nag_card_share), { shareCard(context, ShortcutCard.text(res, all, presetName)) }, icon = NagIcons.OpenWindow)
            }
            FilterField(query, { query = it }, placeholder = stringResource(R.string.nag_card_filter), modifier = Modifier.testTag("shortcut-card-filter"))
            if (shown.isEmpty()) {
                NoResultsState(stringResource(R.string.nag_card_none_found), detail = stringResource(R.string.nag_card_none_found_hint))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                    for (column in balanced(shown, columns)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                            column.forEach { section -> SectionCard(section) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(dimens.gapXl))
        }
    }
}

/** [sections] in [columns] columns, each next section under the shortest, so the columns end near each other. */
internal fun balanced(sections: List<ShortcutCardSection>, columns: Int): List<List<ShortcutCardSection>> {
    val result = List(columns) { mutableListOf<ShortcutCardSection>() }
    val heights = IntArray(columns)
    for (section in sections) {
        val target = heights.indices.minBy { heights[it] }
        result[target] += section
        heights[target] += section.rows.size + 2
    }
    return result.filter { it.isNotEmpty() }
}

@Composable
private fun SectionCard(section: ShortcutCardSection) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(vertical = dimens.gapS, horizontal = dimens.gapS),
    ) {
        Text(
            section.title,
            style = NagTheme.type.title,
            color = colors.text,
            modifier = Modifier.padding(horizontal = dimens.gapS, vertical = dimens.gapXs).semantics { heading() },
        )
        HorizontalRule(Modifier.padding(bottom = dimens.gapXs))
        section.rows.forEach { row -> CardRow(row) }
    }
}

/** A shortcut: its name (and where it works), its keys. Focusable, so the keyboard can walk and scroll the card. */
@Composable
private fun CardRow(row: ShortcutCardRow) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val note = ShortcutCard.scopeNote(res, row.scope)
    val keys = row.keys ?: stringResource(R.string.nag_card_unassigned)
    val changed = stringResource(R.string.nag_card_changed)
    val spoken = listOfNotNull(row.label, note, keys, changed.takeIf { row.changed }).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .focusable(interactionSource = interaction)
            .padding(horizontal = dimens.gapS, vertical = dimens.gapXs)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.label, style = NagTheme.type.body, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(note, changed.takeIf { row.changed }).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = NagTheme.type.labelSmall, color = if (row.changed) colors.accentText else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (row.keys != null) {
            KeyCap(row.keys)
        } else {
            Text(keys, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Offers [text] to the apps that take plain text; nothing happens when there is none. */
private fun shareCard(context: Context, text: String) {
    val title = context.getString(R.string.nag_shortcut_card_title)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}
