package app.nagmedar.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
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
import app.nagmedar.ui.input.hardwareKeyboard
import app.nagmedar.ui.input.keyHint
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.components.animateStateColor
import app.nagmedar.ui.components.ignoreSecondaryButtons
import app.nagmedar.ui.components.verticalScrollbar
import app.nagmedar.ui.theme.NagTheme

/**
 * The command palette (Ctrl+K, S-305): one field that finds commands, screens, settings and
 * media, Turkish-folded (commands, screens and settings fuzzily; media as the search page finds
 * them, 1.0.7). The arrows move through the hits and Enter runs one, Tab changes what is searched
 * ("> ", "/", "#", "@" do it from the field), Escape closes. With no query it lists what was run
 * lately. A wide window shows the selected hit beside the list.
 *
 * 1.0.7: a kind that shows its best only ends with a line that says how many it has and shows them
 * all (a click, or Enter on it); [reading] says the library is still being read for the first time,
 * so its media are not among [items] yet: the list says so instead of "nothing matches". The selected line is
 * kept whole in the list as the arrows move it ([showWhole]), and the pointer takes the selection by a move of
 * its own only ([PointerChoice]).
 */
@Composable
fun CommandPalette(
    items: List<PaletteItem>,
    recent: List<String>,
    onRun: (PaletteItem) -> Unit,
    onDismiss: () -> Unit,
    onSearchAll: ((String) -> Unit)? = null,
    reading: Boolean = false,
) {
    NagDialog(onDismissRequest = onDismiss) {
        PaletteSurface(items, recent, onRun, onDismiss, onSearchAll = onSearchAll, reading = reading)
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
    reading: Boolean = false,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val keyboard = hardwareKeyboard()
    var query by remember { mutableStateOf(initialQuery) }
    var scope by remember { mutableStateOf<PaletteKind?>(null) }
    var selected by remember { mutableIntStateOf(0) }
    val results = remember(query, scope, items, recent) { paletteResults(query, scope, items, recent) }
    // 1.0.7: what the arrows stop at: the hits, and the "show all" line under a kind that shows its best only
    val stops = results.stops
    val chosenStop = stops.getOrNull(selected.coerceIn(0, (stops.size - 1).coerceAtLeast(0)))
    val chosen = (chosenStop as? PaletteRow.Hit)?.hit
    val field = remember { FocusRequester() }
    val list = rememberLazyListState()
    // 1.0.7: whether the pointer chooses the selected line: after a move of its own, not when the list moved under it
    val pointer = remember { PointerChoice() }
    val shown by rememberUpdatedState(results)
    LaunchedEffect(query, scope) {
        selected = 0
        pointer.byKeys()
    }
    // the selected line stays in view, whole, with its group's title when it is the group's first (1.0.7: by pixels,
    // showWhole); when the keys chose it or what is listed changed, not when the pointer did: the list would move under it
    LaunchedEffect(selected, results) {
        if (stops.isEmpty() || pointer.chooses) return@LaunchedEffect
        val row = results.rowOfStop[selected.coerceIn(0, stops.size - 1)]
        list.showWhole(row, titled = row > 0 && results.rows[row - 1] is PaletteRow.Header)
    }
    val run = { hit: PaletteHit ->
        onDismiss()
        onRun(hit.item)
    }
    // Enter: the selected hit runs; "show all" narrows the search to its kind, which lists every one
    val enter: () -> Unit = {
        when (chosenStop) {
            is PaletteRow.Hit -> run(chosenStop.hit)
            is PaletteRow.More -> {
                scope = chosenStop.group.kind
            }
            else -> Unit
        }
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
                        keyboardActions = KeyboardActions(onGo = { enter() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(field)
                            .semantics { contentDescription = placeholder }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        pointer.byKeys()
                                        if (stops.isNotEmpty()) selected = (selected + 1).mod(stops.size)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        pointer.byKeys()
                                        if (stops.isNotEmpty()) selected = (selected - 1).mod(stops.size)
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        val all = onSearchAll
                                        when {
                                            // 1.0.7: a held Enter runs once: on "show all" its repeats would run the first of what that lists
                                            event.nativeKeyEvent.repeatCount > 0 -> Unit
                                            event.isShiftPressed && all != null -> {
                                                onDismiss()
                                                all(parsePaletteQuery(query, null).first)
                                            }
                                            else -> enter()
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
                // 1.0.1: keys with a keyboard only; by touch a tap beside the palette closes it
                if (keyboard) {
                    Spacer(Modifier.width(8.dp))
                    KeyCap("Esc")
                }
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
                    // 1.0.8 (Desktop Kit 2.8.0): a scrollbar for the mouse
                    modifier = Modifier.weight(1f).heightIn(min = 120.dp).testTag("palette-list").verticalScrollbar(list)
                        // 1.0.7: the line under the pointer is selected here, for the whole list, by where the pointer is in it
                        // (until then each line selected itself when the pointer came over it, also when the list brought it there)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    val hovers = (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) && !change.pressed
                                    when {
                                        event.type == PointerEventType.Exit -> Unit
                                        // its wheel, a press, a drag: the pointer's own doing
                                        !hovers -> pointer.byPointer()
                                        pointer.hover(change.position) -> shown.stopAt(list, change.position.y)?.let { selected = it }
                                    }
                                }
                            }
                        },
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    // 1.0.7: by count, not a call for each hit: the library's search can find thousands
                    val rows = results.rows
                    items(
                        count = rows.size,
                        key = { rows[it].key },
                        contentType = { at ->
                            when (rows[at]) {
                                is PaletteRow.Header -> 0
                                is PaletteRow.Hit -> 1
                                is PaletteRow.More -> 2
                            }
                        },
                    ) { at ->
                        when (val row = rows[at]) {
                            is PaletteRow.Header -> GroupHeader(row.group)
                            is PaletteRow.Hit -> HitRow(row.hit, selected = row.stop == selected, onClick = { run(row.hit) })
                            is PaletteRow.More -> MoreRow(row.group, selected = row.stop == selected, onClick = { scope = row.group.kind })
                        }
                    }
                    // 1.0.7: while the library is read for the first time its media are not here yet: said, where they would be listed
                    val typed = parsePaletteQuery(query, scope).first.isNotEmpty()
                    val awaited = reading && (results.scope == PaletteKind.Media || (results.scope == null && typed))
                    if (awaited) {
                        item(key = "reading") {
                            Text(stringResource(R.string.nag_palette_reading), style = NagTheme.type.body, color = colors.textTertiary,
                                modifier = Modifier.padding(16.dp))
                        }
                    } else if (stops.isEmpty()) {
                        item(key = "none") {
                            Text(stringResource(R.string.nag_palette_nothing), style = NagTheme.type.body, color = colors.textTertiary,
                                modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                if (wide && chosenStop != null) {
                    VerticalRule(Modifier.fillMaxHeight())
                    // 1.0.7: the pane keeps its place while "show all" is selected: the list beside it would grow wide and narrow again
                    if (chosen != null) Preview(chosen, Modifier.width(300.dp)) else Spacer(Modifier.width(300.dp))
                }
            }
            // keys to press: with a keyboard at hand, while it drives the palette (not touch)
            if (NagTheme.inputMode != InputMode.Touch && keyboard) {
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

/**
 * 1.0.7: scrolls this list as little as shows its line [index] whole, by pixels; with [titled] the line above it
 * too (a group's title above its first hit). Until then the lines were counted, and the first line a list reports
 * as visible can be one that shows in the list's own padding only: all the way down a long list the selected line
 * was the cut one at its bottom, and from the first line up to the last it could stay under the list's edge.
 */
internal suspend fun LazyListState.showWhole(index: Int, titled: Boolean = false) {
    // laid out again where it is: what it lists can be newer than its last layout, and the lines are looked for by their place
    scrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    val top = if (titled && index > 0) index - 1 else index
    val lines = layoutInfo.visibleItemsInfo
    if (lines.isEmpty()) return
    val upper = lines.firstOrNull { it.index == top }
    if (if (upper != null) upper.offset < 0 else top < lines.first().index) {
        // above what is shown, or its top cut: its top to the list's
        scrollToItem(top)
        return
    }
    // below what is shown: to the list's top first (how tall it is is known once it is laid out), then, as one whose
    // bottom is cut, down to the list's bottom
    val below = lines.none { it.index == index }
    if (below) scrollToItem(index)
    val info = layoutInfo
    val line = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val over = line.offset + line.size - (info.viewportEndOffset - info.afterContentPadding)
    if (over > 0 || (below && over < 0)) scrollBy(over.toFloat())
}

/** 1.0.7: which of the arrows' stops is drawn at [y] of [list] (from the list's top edge), if one is. */
private fun PaletteResults.stopAt(list: LazyListState, y: Float): Int? {
    val info = list.layoutInfo
    // the lines' places are counted from under the list's padding
    val at = y - info.beforeContentPadding
    val line = info.visibleItemsInfo.firstOrNull { at >= it.offset && at < it.offset + it.size } ?: return null
    return when (val row = rows.getOrNull(line.index)) {
        is PaletteRow.Hit -> row.stop
        is PaletteRow.More -> row.stop
        else -> null
    }
}

/**
 * 1.0.7: whether the pointer chooses the selected line. After a layout Compose gives the pointer's last move again to
 * what is under it now (for the hover states under a resting pointer), so each line the arrows scrolled under a
 * resting pointer took the selection back: the arrows never got past the list's edge. The pointer chooses by a move
 * of its own (or its wheel, a press); after a key chose a line or changed what is listed, not until it moves again.
 */
private class PointerChoice {
    var chooses = false
        private set
    private var last = Offset.Unspecified
    private var rested = Offset.Unspecified

    /** A key chose a line, or changed what is listed. */
    fun byKeys() {
        chooses = false
        rested = last
    }

    /** The pointer's wheel, a press, a drag. */
    fun byPointer() {
        chooses = true
    }

    /** The pointer is at [at] with nothing pressed: whether it chooses, which it does once it is away from where it rested. */
    fun hover(at: Offset): Boolean {
        last = at
        if (!chooses) {
            if (rested.isUnspecified) rested = at else if ((at - rested).getDistance() > MOVED_PX) chooses = true
        }
        return chooses
    }

    private companion object {
        /** A move given again comes back at its place, to a rounding; a pointer that moved is a pixel away or more. */
        const val MOVED_PX = 0.5f
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
private fun HitRow(hit: PaletteHit, selected: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val item = hit.item
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .background(animateStateColor(if (selected) colors.selection else colors.selection.copy(alpha = 0f), "hitRow"), RoundedCornerShape(dimens.radiusControl))
            .pointerHoverIcon(PointerIcon.Hand)
            // the field moves through the rows (↑↓); Tab changes the scope, so rows take no focus
            .focusProperties { canFocus = false }
            .ignoreSecondaryButtons()
            // no ripple, as everywhere: the selection follows the pointer instead (the list sees to that)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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
        keyHint(item.shortcut)?.let {
            Spacer(Modifier.width(8.dp))
            KeyCap(it)
        }
    }
}

/**
 * 1.0.7: under a kind that shows its best only: how many it has. A click (or Enter, the arrows stop at it)
 * narrows the search to the kind, which lists every one of them.
 */
@Composable
private fun MoreRow(group: PaletteGroup, selected: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .background(animateStateColor(if (selected) colors.selection else colors.selection.copy(alpha = 0f), "moreRow"), RoundedCornerShape(dimens.radiusControl))
            .pointerHoverIcon(PointerIcon.Hand)
            // as the hits: the field moves through the rows, rows take no focus
            .focusProperties { canFocus = false }
            .ignoreSecondaryButtons()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .testTag("palette-more")
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // under the hits' labels
        Spacer(Modifier.width(dimens.iconLarge + 10.dp))
        Text(stringResource(R.string.nag_palette_show_all, group.total), style = NagTheme.type.body,
            color = if (selected) colors.onSelection else colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        keyHint(item.shortcut)?.let { keys ->
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
