@file:OptIn(ExperimentalLayoutApi::class)

package app.nagmedar.ui.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.theme.NagTheme

/** The window width from which the ways in are chips side by side; narrower, a menu button. */
private val SEGMENTS_FROM: Dp = 1000.dp

/** The window width from which the buttons carry their names; narrower, icons only. */
private val LABELS_FROM: Dp = 520.dp

/**
 * S-1001: the music library's one toolbar row. At the left the way in (chips side by side in a wide
 * window, a menu button showing the current one in a narrow one); at the right Play and Shuffle
 * for [tracks] (the open group's, as the table shows them), the [showColumns] menu and the filter.
 */
@Composable
internal fun MusicToolbar(
    state: MusicBrowserState,
    actions: MusicActions,
    tracks: List<Track>,
    showColumns: Boolean,
    probing: Int,
    modifier: Modifier = Modifier,
) {
    val dimens = NagTheme.dimens
    val views = MusicView.entries.map { it to stringResource(viewLabel(it)) }
    val playLabel = stringResource(R.string.nag_play)
    val shuffleLabel = stringResource(R.string.nag_shuffle)
    val columnsLabel = stringResource(R.string.nag_music_columns)
    val labels = MusicColumn.entries.associateWith { stringResource(it.label) }
    val ready = tracks.isNotEmpty()
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs).testTag("music-toolbar")) {
        val segments = maxWidth >= SEGMENTS_FROM
        val named = maxWidth >= LABELS_FROM
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (segments) {
                SegmentedChoice(views, state.view, state::open)
            } else {
                MenuButton(
                    views.first { it.first == state.view }.second,
                    { views.map { (view, label) -> MenuEntry.Item(label, { state.open(view) }, checked = view == state.view) } },
                    modifier = Modifier.testTag("music-views"),
                )
            }
            Spacer(Modifier.weight(1f))
            if (named) {
                NagButton(playLabel, { actions.play(tracks, 0) }, icon = NagIcons.Play, primary = true, enabled = ready)
                Spacer(Modifier.width(dimens.gapS))
                NagButton(shuffleLabel, { actions.play(tracks.shuffled(), 0) }, icon = NagIcons.Shuffle, enabled = ready)
                if (showColumns) {
                    Spacer(Modifier.width(dimens.gapS))
                    MenuButton(columnsLabel, { columnMenu(state, labels) })
                }
            } else {
                NagIconButton(NagIcons.Play, playLabel, { actions.play(tracks, 0) }, enabled = ready, tint = NagTheme.colors.accent)
                NagIconButton(NagIcons.Shuffle, shuffleLabel, { actions.play(tracks.shuffled(), 0) }, enabled = ready)
            }
            Spacer(Modifier.width(dimens.gapS))
            FilterButton(state, probing)
        }
    }
}

// ------------------------------------------------------------------- the filter

/** One choice of a filter group: a chip that is ticked while the filter holds it. */
@Immutable
internal class FilterOption(val label: String, val selected: Boolean, val onToggle: (Boolean) -> Unit)

/** One heading of the filter panel with its choices; a later filter (rating, plays, format, year) is one more entry in [musicFilterGroups]. */
@Immutable
internal class FilterGroup(val title: String, val options: List<FilterOption>)

/** The groups of the filter panel, in the order they show. */
@Composable
internal fun musicFilterGroups(state: MusicBrowserState): List<FilterGroup> {
    val locale = LocalContext.current.resources.locale()
    return listOf(
        FilterGroup(
            stringResource(R.string.nag_music_filter_depth),
            Depth.entries.map { depth ->
                FilterOption(depthLabel(depth), depth in state.filter.depths) { on ->
                    state.filter = state.filter.copy(depths = if (on) state.filter.depths + depth else state.filter.depths - depth)
                }
            },
        ),
        FilterGroup(
            stringResource(R.string.nag_music_filter_rate),
            RateBand.entries.map { band ->
                FilterOption(rateLabel(band, locale), band in state.filter.rates) { on ->
                    state.filter = state.filter.copy(rates = if (on) state.filter.rates + band else state.filter.rates - band)
                }
            },
        ),
    )
}

/** The filter's button, a dot on it while a filter holds; it opens [FilterPanel] under itself. */
@Composable
private fun FilterButton(state: MusicBrowserState, probing: Int) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.nag_music_filter)
    val on = stringResource(R.string.nag_music_filter_on)
    val active = state.filter.active
    Box(Modifier.testTag("music-filter")) {
        NagIconButton(
            NagIcons.Filter,
            label,
            { open = !open },
            modifier = Modifier.semantics { if (active) stateDescription = on },
            selected = open,
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 9.dp)
                    .size(10.dp)
                    .background(NagTheme.colors.accent, CircleShape)
                    .border(BorderStroke(1.5.dp, NagTheme.colors.background), CircleShape),
            )
        }
        if (open) FilterPanel(state, probing, onDismiss = { open = false })
    }
}

/**
 * The filters in a popover under the filter button: a heading and chips per group of
 * [musicFilterGroups], "clear" at the top, the note that files are still being read at the bottom.
 * Escape, Back and a click outside close it; Tab walks the chips.
 */
@Composable
private fun FilterPanel(state: MusicBrowserState, probing: Int, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard.coerceAtMost(10.dp))
    val groups = musicFilterGroups(state)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Popup(
        popupPositionProvider = remember(rtl) { BelowEndPositionProvider(rtl) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val panel = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .width(PANEL_WIDTH)
                .shadow(12.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        // acted on when released, so the release does not reach the window below
                        Key.Escape, Key.Back -> {
                            if (event.type == KeyEventType.KeyUp) onDismiss()
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(panel)
                .focusTarget()
                .verticalScroll(rememberScrollState())
                .padding(dimens.gapM)
                .testTag("music-filter-panel"),
            verticalArrangement = Arrangement.spacedBy(dimens.gapM),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.nag_music_filter),
                    style = NagTheme.type.bodyStrong,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                NagButton(stringResource(R.string.nag_clear_filters), { state.filter = TechFilter() }, enabled = state.filter.active)
            }
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    SectionCaption(group.title)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.options.forEach { option -> FilterChip(option.label, option.selected, option.onToggle) }
                    }
                }
            }
            if (probing > 0 && state.filter.active) {
                Text(
                    pluralStringResource(R.plurals.nag_music_probing, probing, probing),
                    style = NagTheme.type.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
        LaunchedEffect(Unit) { panel.requestFocus() }
    }
}

private val PANEL_WIDTH = 320.dp

/** Under the anchor, its end edge flush with the anchor's, kept inside the window (above it near the bottom). */
private class BelowEndPositionProvider(private val rtl: Boolean) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = 4
        val x = if (rtl) anchorBounds.left else anchorBounds.right - popupContentSize.width
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height) {
            val above = anchorBounds.top - gap - popupContentSize.height
            y = if (above >= 0) above else windowSize.height - popupContentSize.height
        }
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y.coerceAtLeast(0))
    }
}
