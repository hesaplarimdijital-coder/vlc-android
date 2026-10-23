package app.nagmedar.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.ChipRow
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.components.animateStateColor
import app.nagmedar.ui.components.ignoreSecondaryButtons
import app.nagmedar.ui.theme.NagTheme

/**
 * The command palette (Ctrl+K, S-305): one field that finds commands, screens, settings and
 * media, fuzzy and Turkish-folded. The arrows move through the hits and Enter runs one, Tab
 * changes what is searched ("> ", "/", "#", "@" do it from the field), Escape closes. With no
 * query it lists what was run lately. A wide window shows the selected hit beside the list.
 */
@Composable
fun CommandPalette(
    items: List<PaletteItem>,
    recent: List<String>,
    onRun: (PaletteItem) -> Unit,
    onDismiss: () -> Unit,
    onSearchAll: ((String) -> Unit)? = null,
) {
    NagDialog(onDismissRequest = onDismiss) {
        PaletteSurface(items, recent, onRun, onDismiss, onSearchAll = onSearchAll)
    }
}

/** The palette itself, without its window: the dialog's content and the screenshots'. */
@Composable
fun PaletteSurface(
    items: List<PaletteItem>,
    recent: List<String>,
    onRun: (PaletteItem) -> Unit,
    onDismiss: () -> Unit,
    initialQuery: String = "",
    /** Shift+Enter (S-311): the search page with the query, for everything the library holds. */
    onSearchAll: ((String) -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    var query by remember { mutableStateOf(initialQuery) }
    var scope by remember { mutableStateOf<PaletteKind?>(null) }
    var selected by remember { mutableIntStateOf(0) }
    val results = remember(query, scope, items, recent) { paletteResults(query, scope, items, recent) }
    val hits = results.hits
    val chosen = hits.getOrNull(selected.coerceIn(0, (hits.size - 1).coerceAtLeast(0)))
    val field = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(query, scope) { selected = 0 }
    // the selected row stays in view; rows count the group headers too
    LaunchedEffect(selected, results) {
        if (hits.isEmpty()) return@LaunchedEffect
        var row = 0
        var seen = 0
        for (group in results.groups) {
            row++
            if (selected < seen + group.hits.size) {
                row += selected - seen
                break
            }
            seen += group.hits.size
            row += group.hits.size
        }
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        val first = visible.first().index
        val last = visible.last().index
        when {
            row <= first -> list.scrollToItem((row - 1).coerceAtLeast(0))
            row >= last -> list.scrollToItem((row - (last - first) + 1).coerceAtLeast(0))
        }
    }
    val run = { hit: PaletteHit ->
        onDismiss()
        onRun(hit.item)
    }
    val shape = RoundedCornerShape(dimens.radiusCard.coerceAtMost(12.dp))
    BoxWithConstraints(
        // a tap beside the palette closes it
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth >= 760.dp
        val width = if (wide) maxWidth.coerceAtMost(840.dp) else maxWidth - 16.dp
        Column(
            Modifier
                .padding(top = if (wide) 40.dp else 8.dp)
                .width(width)
                .heightIn(max = maxHeight * 0.82f)
                .shadow(24.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                // taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("palette"),
        ) {
            // the field
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(NagIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.iconLarge))
                Spacer(Modifier.width(12.dp))
                val placeholder = stringResource(R.string.nag_palette_placeholder)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = NagTheme.type.title, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = NagTheme.type.title.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { chosen?.let(run) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(field)
                            .semantics { contentDescription = placeholder }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (hits.isNotEmpty()) selected = (selected + 1).mod(hits.size)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (hits.isNotEmpty()) selected = (selected - 1).mod(hits.size)
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        val all = onSearchAll
                                        if (event.isShiftPressed && all != null) {
                                            onDismiss()
                                            all(parsePaletteQuery(query, null).first)
                                        } else {
                                            chosen?.let(run)
                                        }
                                        true
                                    }
                                    Key.Tab -> {
                                        val kinds = listOf<PaletteKind?>(null) + PaletteKind.entries
                                        val step = if (event.isShiftPressed) -1 else 1
                                        scope = kinds[(kinds.indexOf(results.scope) + step).mod(kinds.size)]
                                        // a typed prefix gives way to the chosen scope
                                        query = parsePaletteQuery(query, null).first
                                        true
                                    }
                                    Key.Escape -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(pluralStringResource(R.plurals.nag_palette_count, results.count, results.count), style = NagTheme.type.labelSmall,
                    color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                KeyCap("Esc")
            }
            HorizontalRule()
            // what is searched
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // one line that scrolls sideways where they do not fit, its ends fading into the palette
                ChipRow(Modifier.weight(1f), wrap = false, fadeColor = colors.surfaceRaised) {
                    FilterChip(stringResource(R.string.nag_palette_all), results.scope == null, { scope = null })
                    PaletteKind.entries.forEach { kind ->
                        FilterChip(stringResource(kind.title), results.scope == kind, { on -> scope = if (on) kind else null })
                    }
                }
            }
            HorizontalRule()
            Row(Modifier.weight(1f, fill = false)) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).heightIn(min = 120.dp).testTag("palette-list"),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    var index = 0
                    results.groups.forEach { group ->
                        val first = index
                        item(key = "header-${group.kind}") { GroupHeader(group) }
                        group.hits.forEachIndexed { i, hit ->
                            val at = first + i
                            item(key = "${group.kind}-${hit.item.id}") {
                                HitRow(hit, selected = at == selected, onHover = { selected = at }, onClick = { run(hit) })
                            }
                        }
                        index += group.hits.size
                    }
                    if (hits.isEmpty()) {
                        item(key = "none") {
                            Text(stringResource(R.string.nag_palette_nothing), style = NagTheme.type.body, color = colors.textTertiary,
                                modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                if (wide && chosen != null) {
                    VerticalRule(Modifier.fillMaxHeight())
                    Preview(chosen, Modifier.width(300.dp))
                }
            }
            // keys to press: a pointer comes with a keyboard, a touch screen seldom does
            if (NagTheme.inputMode != InputMode.Touch) {
                HorizontalRule()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Hint("↑↓", stringResource(R.string.nag_palette_hint_move))
                    Hint("Enter", stringResource(R.string.nag_palette_hint_run))
                    Hint("Tab", stringResource(R.string.nag_palette_hint_scope))
                    if (onSearchAll != null) Hint("Shift+Enter", stringResource(R.string.nag_palette_hint_all))
                }
            }
        }
        // here, not beside the state: the field is composed when the box is measured
        LaunchedEffect(Unit) { field.requestFocus() }
    }
}

@Composable
private fun GroupHeader(group: PaletteGroup) {
    val colors = NagTheme.colors
    val title = if (group.kind == null) stringResource(R.string.nag_palette_recent) else stringResource(group.kind.title)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)) {
        Text(title.uppercase(currentLocale()), style = NagTheme.type.caption, color = colors.textTertiary,
            modifier = Modifier.weight(1f).semantics { heading() })
        Text("${group.total}", style = NagTheme.type.mono, color = colors.textTertiary)
    }
}

@Composable
private fun HitRow(hit: PaletteHit, selected: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val item = hit.item
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .background(animateStateColor(if (selected) colors.selection else colors.selection.copy(alpha = 0f), "hitRow"), RoundedCornerShape(dimens.radiusControl))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            // the field moves through the rows (↑↓); Tab changes the scope, so rows take no focus
            .focusProperties { canFocus = false }
            .ignoreSecondaryButtons()
            // no ripple, as everywhere: the selection follows the pointer instead
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.iconLarge), contentAlignment = Alignment.Center) {
            item.icon?.let { Icon(it, contentDescription = null, tint = if (selected) colors.onSelection else colors.textSecondary, modifier = Modifier.size(dimens.icon)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(highlighted(item.label, hit.matched), style = NagTheme.type.body, color = if (selected) colors.onSelection else colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.detail.isNotEmpty()) {
                Text(item.detail, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item.shortcut?.let {
            Spacer(Modifier.width(8.dp))
            KeyCap(it)
        }
    }
}

/** The label with its matched characters in full colour and weight. */
@Composable
private fun highlighted(label: String, matched: IntArray): AnnotatedString {
    val strong = NagTheme.colors.text
    return buildAnnotatedString {
        append(label)
        if (matched.isEmpty()) {
            addStyle(SpanStyle(color = strong), 0, label.length)
        } else {
            matched.filter { it < label.length }.forEach { addStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold), it, it + 1) }
        }
    }
}

/** The selected hit, larger: its name, what it is and its keys. */
@Composable
private fun Preview(hit: PaletteHit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val item = hit.item
    Column(modifier.fillMaxHeight().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let {
                Box(Modifier.size(36.dp).background(colors.surface, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(it, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(item.label, style = NagTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(stringResource(item.kind.title), item.detail).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = NagTheme.type.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 2,
                )
            }
        }
        item.shortcut?.let { keys ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nag_palette_shortcut), style = NagTheme.type.labelSmall, color = colors.textTertiary,
                    modifier = Modifier.width(80.dp))
                KeyCap(keys)
            }
        }
    }
}

@Composable
private fun KeyCap(text: String) {
    val colors = NagTheme.colors
    Text(
        text,
        style = NagTheme.type.mono,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(4.dp))
            .border(1.dp, colors.borderStrong, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Hint(keys: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KeyCap(keys)
        Spacer(Modifier.width(6.dp))
        Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
    }
}
