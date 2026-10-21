package app.nagmedar.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.screens.FilterField
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The user guide (S-1504): short, task-oriented sections about what the app does, bundled as a
 * Markdown file per language and read offline. The sections are searched with the field above them.
 * On wide windows the contents are a list beside the open section; on a phone each section opens
 * under its title. [onOpenShortcutCard] leads to the card of keys, which the guide's {key:…}
 * words always agree with.
 */
@Composable
fun GuideScreen(onOpenShortcutCard: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val asset = stringResource(R.string.nag_guide_asset)
    // null while it is read; empty when it cannot be
    val sections by produceState<List<GuideSection>?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { GuideParser.parse(context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }) }.getOrDefault(emptyList())
        }
    }
    Column(modifier.fillMaxSize().testTag("guide")) {
        PageHeader(
            title = stringResource(R.string.nag_guide_title),
            actions = { NagButton(stringResource(R.string.nag_shortcut_card_title), onOpenShortcutCard, icon = NagIcons.Keyboard) },
        )
        val loaded = sections
        when {
            loaded == null -> Box(Modifier.fillMaxSize())
            loaded.isEmpty() -> Box(Modifier.fillMaxSize().padding(NagTheme.dimens.pagePadding)) {
                StatePanel(StateTone.Error, NagIcons.ErrorCircle, stringResource(R.string.nag_guide_unavailable), description = stringResource(R.string.nag_guide_unavailable_hint))
            }
            else -> GuideContent(loaded)
        }
    }
}

@Composable
private fun GuideContent(sections: List<GuideSection>) {
    val dimens = NagTheme.dimens
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf("") }
    val visible = remember(sections, query) { sections.filter { it.matches(query) } }
    val filter: @Composable () -> Unit = {
        FilterField(query, { query = it }, placeholder = stringResource(R.string.nag_guide_filter), modifier = Modifier.testTag("guide-filter"))
    }
    if (currentWidthClass() >= WidthClass.Expanded) {
        // the open section, or the first of those that match
        val current = visible.firstOrNull { it.id == openId } ?: visible.firstOrNull()
        Row(Modifier.fillMaxSize().padding(horizontal = dimens.pagePadding)) {
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                Box(Modifier.padding(bottom = dimens.gapS)) { filter() }
                if (visible.isEmpty()) {
                    NoResultsState(stringResource(R.string.nag_guide_none_found), dense = true)
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag("guide-contents"), contentPadding = PaddingValues(bottom = dimens.gapL)) {
                        items(visible, key = { it.id }) { section ->
                            ContentsRow(section, selected = section.id == current?.id) { openId = section.id }
                        }
                    }
                }
            }
            VerticalRule(Modifier.padding(horizontal = dimens.gapL).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (current != null) {
                    // a new section starts at its top
                    key(current.id) {
                        Column(
                            Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = dimens.gapXl),
                            verticalArrangement = Arrangement.spacedBy(dimens.gapM),
                        ) {
                            Text(current.title, style = NagTheme.type.headline, color = NagTheme.colors.text, modifier = Modifier.semantics { heading() })
                            GuideBlocks(current.blocks)
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().testTag("guide-contents"),
            contentPadding = PaddingValues(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.gapXl),
        ) {
            item(key = "filter") { Box(Modifier.padding(bottom = dimens.gapS)) { filter() } }
            if (visible.isEmpty()) item(key = "none") { NoResultsState(stringResource(R.string.nag_guide_none_found)) }
            items(visible, key = { it.id }) { section ->
                // a search with one answer shows it
                val open = section.id == openId || (query.isNotBlank() && visible.size == 1)
                OpenableSection(section, open) { openId = if (section.id == openId) "" else section.id }
            }
        }
    }
}

/** A section in the wide window's list of contents: its title and the start of its first paragraph. */
@Composable
private fun ContentsRow(section: GuideSection, selected: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .nagClickable(interaction, onClick, onClickLabel = section.title)
            .semantics { this.selected = selected }
            .padding(horizontal = dimens.gapM, vertical = dimens.gapS),
    ) {
        Text(section.title, style = NagTheme.type.bodyStrong, color = if (selected) colors.onSelection else colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (section.summary.isNotBlank()) {
            Text(
                section.summary,
                style = NagTheme.type.labelSmall,
                color = if (selected) colors.onSelection else colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A section on a phone: its title and first words, and under them the whole section once it is open. */
@Composable
private fun OpenableSection(section: GuideSection, open: Boolean, onToggle: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val opened = stringResource(R.string.nag_about_opened)
    val closed = stringResource(R.string.nag_about_closed)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
                .focusRing(state, shape)
                .background(stateBackground(state, open), shape)
                .nagClickable(interaction, onToggle, onClickLabel = section.title)
                .semantics { stateDescription = if (open) opened else closed }
                .padding(horizontal = dimens.gapM, vertical = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(section.title, style = NagTheme.type.bodyStrong, color = if (open) colors.onSelection else colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!open && section.summary.isNotBlank()) {
                    Text(section.summary, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (open) NagIcons.ChevronUp else NagIcons.ChevronDown,
                contentDescription = null,
                tint = if (open) colors.onSelection else colors.textTertiary,
                modifier = Modifier.padding(start = dimens.gapS).size(dimens.icon),
            )
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(horizontal = dimens.gapM, vertical = dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                GuideBlocks(section.blocks)
            }
        }
        HorizontalRule(Modifier.padding(horizontal = dimens.gapM))
    }
}

/** The blocks of a section, one under the other. */
@Composable
private fun GuideBlocks(blocks: List<GuideBlock>) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    blocks.forEach { block ->
        when (block) {
            is GuideBlock.Paragraph -> Text(rememberGuideText(block.spans), style = NagTheme.type.body, color = colors.text)
            is GuideBlock.Heading -> Text(
                block.text,
                style = NagTheme.type.bodyStrong,
                color = colors.text,
                modifier = Modifier.padding(top = dimens.gapXs).semantics { heading() },
            )
            is GuideBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
                block.items.forEach { item -> GuideListItem("•", item) }
            }
            is GuideBlock.Steps -> Column(verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
                block.items.forEachIndexed { index, item -> GuideListItem("${index + 1}.", item) }
            }
            is GuideBlock.Tip -> Tip(block.spans)
        }
    }
}

@Composable
private fun GuideListItem(marker: String, spans: List<GuideSpan>) {
    val colors = NagTheme.colors
    Row {
        Text(marker, style = NagTheme.type.bodyStrong, color = colors.accentText, modifier = Modifier.width(28.dp))
        Text(rememberGuideText(spans), style = NagTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Tip(spans: List<GuideSpan>) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.border, shape)
            .padding(dimens.gapM),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        Icon(NagIcons.Info, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(dimens.icon))
        Column {
            Text(stringResource(R.string.nag_guide_tip), style = NagTheme.type.labelSmall, color = colors.accentText)
            Spacer(Modifier.height(2.dp))
            Text(rememberGuideText(spans), style = NagTheme.type.body, color = colors.text)
        }
    }
}

/**
 * A line's text with its styling: bold words, and the keys of a shortcut as they are in the table
 * now, set like a keycap; the line follows a change of keys at once.
 */
@Composable
private fun rememberGuideText(spans: List<GuideSpan>): AnnotatedString {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val mono = NagTheme.type.mono
    val overrides = ShortcutRegistry.overrides
    return remember(spans, overrides, res, colors) {
        buildAnnotatedString {
            for (span in spans) {
                when (span) {
                    is GuideSpan.Text -> append(span.text)
                    is GuideSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
                    is GuideSpan.Keys -> {
                        val keys = ShortcutRegistry.chordLabel(span.id, res)
                        if (keys == null) {
                            append(res.getString(R.string.nag_guide_key_unassigned))
                        } else {
                            withStyle(SpanStyle(fontFamily = mono.fontFamily, background = colors.surfaceRaised)) { append(" $keys ") }
                        }
                    }
                }
            }
        }
    }
}
