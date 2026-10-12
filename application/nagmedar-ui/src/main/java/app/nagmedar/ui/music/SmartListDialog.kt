package app.nagmedar.ui.music

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.RatingStars
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SuggestionChip
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.NagTheme

/** The columns a smart list can be ordered by. */
private val SMART_SORTS = listOf(
    MusicColumn.Title, MusicColumn.Artist, MusicColumn.Album, MusicColumn.Year, MusicColumn.Duration,
    MusicColumn.Rating, MusicColumn.Plays, MusicColumn.Skips, MusicColumn.LastPlayed, MusicColumn.Added,
)

/**
 * S-805: a smart list made or changed: its name, its rules ([initial] null: a new one, which can
 * start from a template), whether all of them or any must hold, its order and its limit; how many
 * of the [library]'s tracks it takes is counted as the rules change.
 */
@Composable
fun SmartListDialog(
    initial: SmartList?,
    library: List<Track>?,
    stats: Map<String, TrackStats>,
    now: Long,
    onSave: (SmartList) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { SmartListSurface(initial, library, stats, now, onSave, onDismiss) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartListSurface(
    initial: SmartList?,
    library: List<Track>?,
    stats: Map<String, TrackStats>,
    now: Long,
    onSave: (SmartList) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagTheme.colors
    val locale = LocalContext.current.resources.locale()
    var draft by remember { mutableStateOf(initial ?: SmartList(name = "", rules = listOf(SmartRule(SmartField.Rating, SmartOp.AtLeast, "4")))) }
    // the template chosen last: its name gives way to the next one's, a name typed stays
    var templateName by remember { mutableStateOf<String?>(null) }
    val matching = remember(draft, library, stats, now, locale) { library?.let { draft.select(it, stats, now, locale).size } }
    val templates = SmartTemplate.entries.associateWith { smartTemplateLabel(it) }
    DialogCard(
        stringResource(if (initial == null) R.string.nag_smart_new_title else R.string.nag_smart_edit_title),
        Modifier.testTag("smart-dialog"),
        cardWidth = 600.dp,
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (initial == null) {
                Text(stringResource(R.string.nag_smart_start_from), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartTemplate.entries.forEach { template ->
                        val label = templates.getValue(template)
                        SuggestionChip(label, {
                            val built = template.build(label)
                            draft = built.copy(name = if (draft.name.isBlank() || draft.name == templateName) label else draft.name)
                            templateName = label
                        })
                    }
                }
            }
            SmallField(
                value = draft.name,
                onChange = { draft = draft.copy(name = it) },
                label = stringResource(R.string.nag_smart_name),
                width = null,
                modifier = Modifier.testTag("smart-name"),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.nag_smart_match), style = NagTheme.type.label, color = colors.textSecondary)
                SegmentedChoice(
                    options = listOf(true to stringResource(R.string.nag_smart_match_all), false to stringResource(R.string.nag_smart_match_any)),
                    selected = draft.matchAll,
                    onSelect = { draft = draft.copy(matchAll = it) },
                )
            }
            draft.rules.forEachIndexed { index, rule ->
                RuleRow(
                    rule = rule,
                    onChange = { changed -> draft = draft.copy(rules = draft.rules.toMutableList().also { it[index] = changed }) },
                    onRemove = { draft = draft.copy(rules = draft.rules.filterIndexed { i, _ -> i != index }) },
                )
            }
            NagButton(
                stringResource(R.string.nag_smart_add_rule),
                { draft = draft.copy(rules = draft.rules + SmartRule(SmartField.Artist)) },
                icon = NagIcons.Plus,
                modifier = Modifier.testTag("smart-add-rule"),
            )
            OrderRow(draft) { draft = it }
            Text(
                matching?.let { pluralStringResource(R.plurals.nag_smart_matching, it, it) }.orEmpty(),
                style = NagTheme.type.label,
                color = colors.textSecondary,
                modifier = Modifier.testTag("smart-count"),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(
                stringResource(if (initial == null) R.string.nag_pl_create else R.string.nag_bm_save),
                {
                    onSave(draft.copy(name = draft.name.trim(), rules = draft.rules.filter { it.complete }))
                    onDismiss()
                },
                primary = true,
                enabled = draft.name.isNotBlank(),
                modifier = Modifier.testTag("smart-save"),
            )
        }
    }
}

/** One rule: its field, its operator, its value and unit, and a button that takes it out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleRow(rule: SmartRule, onChange: (SmartRule) -> Unit, onRemove: () -> Unit) {
    val colors = NagTheme.colors
    val fields = SmartField.entries.associateWith { smartFieldLabel(it) }
    val ops = SmartOp.entries.associateWith { smartOpLabel(it) }
    FlowRow(
        Modifier.fillMaxWidth().testTag("smart-rule"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MenuButton(
            fields.getValue(rule.field),
            {
                SmartField.entries.map { field ->
                    MenuEntry.Item(fields.getValue(field), {
                        // a field of the same kind keeps the operator and the value
                        onChange(if (field.kind == rule.field.kind && rule.op in field.ops) rule.copy(field = field) else SmartRule(field))
                    }, checked = field == rule.field)
                }
            },
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        MenuButton(
            ops.getValue(rule.op),
            { rule.field.ops.map { op -> MenuEntry.Item(ops.getValue(op), { onChange(rule.copy(op = op)) }, checked = op == rule.op) } },
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Box(Modifier.align(Alignment.CenterVertically)) { RuleValue(rule, onChange, fields.getValue(rule.field)) }
        smartUnit(rule)?.let { unit ->
            Text(unit, style = NagTheme.type.label, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
        }
        NagIconButton(NagIcons.Close, stringResource(R.string.nag_smart_remove_rule), onRemove, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

/** A rule's value as its kind asks: words, a number, stars, a depth; nothing for "never". */
@Composable
private fun RuleValue(rule: SmartRule, onChange: (SmartRule) -> Unit, label: String) {
    when {
        rule.op == SmartOp.Never -> Unit
        rule.field.kind == SmartKind.Text -> SmallField(rule.value, { onChange(rule.copy(value = it)) }, label, width = 180.dp, modifier = Modifier.testTag("smart-value"))
        rule.field.kind == SmartKind.Stars -> RatingStars(
            rating = rule.value.toDoubleOrNull()?.toInt() ?: 0,
            onRate = { onChange(rule.copy(value = it.toString())) },
            starSize = 18.dp,
            target = 26.dp,
            focusable = true,
            modifier = Modifier.testTag("smart-value"),
        )
        rule.field.kind == SmartKind.Depth -> {
            val depths = Depth.entries.associateWith { depthLabel(it) }
            val chosen = runCatching { Depth.valueOf(rule.value) }.getOrNull()
            MenuButton(
                chosen?.let { depths.getValue(it) } ?: stringResource(R.string.nag_smart_choose),
                { Depth.entries.map { depth -> MenuEntry.Item(depths.getValue(depth), { onChange(rule.copy(value = depth.name)) }, checked = depth == chosen) } },
                modifier = Modifier.testTag("smart-value"),
            )
        }
        else -> SmallField(
            rule.value,
            { typed -> onChange(rule.copy(value = typed.filter { it.isDigit() || it == '.' || it == ',' })) },
            label,
            width = 72.dp,
            number = true,
            modifier = Modifier.testTag("smart-value"),
        )
    }
}

/** The list's order (the library's, or a column either way) and its limit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderRow(draft: SmartList, onChange: (SmartList) -> Unit) {
    val colors = NagTheme.colors
    val columns = SMART_SORTS.associateWith { stringResource(it.label) }
    val libraryOrder = stringResource(R.string.nag_smart_sort_library)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.nag_smart_sort), style = NagTheme.type.label, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
        val sort = draft.sort
        MenuButton(
            sort?.let { columns[it.column] } ?: libraryOrder,
            {
                listOf(MenuEntry.Item(libraryOrder, { onChange(draft.copy(sort = null)) }, checked = sort == null), MenuEntry.Separator) +
                    SMART_SORTS.map { column ->
                        MenuEntry.Item(columns.getValue(column), {
                            // counts and stars most first, words from A
                            val descending = column in setOf(MusicColumn.Rating, MusicColumn.Plays, MusicColumn.Skips, MusicColumn.LastPlayed, MusicColumn.Added)
                            onChange(draft.copy(sort = MusicSort(column, sort?.takeIf { it.column == column }?.ascending ?: !descending)))
                        }, checked = sort?.column == column)
                    }
            },
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        if (sort != null) {
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.nag_smart_ascending), false to stringResource(R.string.nag_smart_descending)),
                selected = sort.ascending,
                onSelect = { onChange(draft.copy(sort = sort.copy(ascending = it))) },
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NagCheckbox(
            checked = draft.limit != null,
            onCheckedChange = { on -> onChange(draft.copy(limit = if (on) draft.limit ?: 50 else null)) },
            label = stringResource(R.string.nag_smart_limit),
        )
        draft.limit?.let { limit ->
            SmallField(
                limit.toString(),
                { typed -> onChange(draft.copy(limit = typed.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0)) },
                stringResource(R.string.nag_smart_limit),
                width = 72.dp,
                number = true,
                modifier = Modifier.testTag("smart-limit"),
            )
            Text(stringResource(R.string.nag_smart_limit_tracks), style = NagTheme.type.label, color = colors.textSecondary)
        }
    }
}

/** A one-line field of the window: [width] null takes the row. */
@Composable
private fun SmallField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    width: Dp?,
    modifier: Modifier = Modifier,
    number: Boolean = false,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = NagTheme.type.body.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Next),
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .border(1.dp, colors.borderStrong, RoundedCornerShape(dimens.radiusControl))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = label }
            .tabMovesFocus(focusManager),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && width == null) Text(label, style = NagTheme.type.body, color = colors.textTertiary)
                inner()
            }
        },
    )
}
