package app.nagmedar.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.BodoniHeadline
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.SemiBoldPlus
import app.nagmedar.ui.theme.rememberNagHaptics

// 1.1.2 (the design's tablet page, Tablet.dc.html and TabletListe.dc.html): a wide window's Music page. The band across
// the page's top with the library's search field over it, the masthead with Oynat and Karıştır beside it, the ways in as
// tabs, the quick filters with the order, the filter and the covers or the rows; the covers five across under their
// letters, the letters' wave beside them; in rows, the groups beside the open group's tracks.

/** The band's height on a tall window (a tablet on its side: the design's 168 dp). */
internal val TABLET_BAND = 168.dp

/** The band's height on a lower window (1.1.0's). */
internal val WIDE_BAND = 104.dp

/** The panel of what plays beside the page until it is dragged (the design's). */
internal val MUSIC_PANEL_WIDTH = 376.dp

/** The page's margin on a wide window. */
internal val WIDE_MARGIN = 32.dp

/** The letters' wave down a wide grid's end, and the room the covers leave it (the wave, 12 dp, the margin). */
internal val WIDE_WAVE_WIDTH = 48.dp
internal val WIDE_WAVE_ROOM = WIDE_MARGIN + WIDE_WAVE_WIDTH + 12.dp

/** From this height a window has the design's tall band; a lower one 1.1.0's; a short one (a phone on its side) none. */
private const val TALL_FROM_DP = 800

/** The band's height on a window [heightDp] tall. */
internal fun wideBandHeight(heightDp: Int, short: Boolean): Dp = when {
    short -> 0.dp
    heightDp >= TALL_FROM_DP -> TABLET_BAND
    else -> WIDE_BAND
}

/**
 * The top of a wide page: the band in the inks of what plays ([band] 0: none), the search field over it ([onSearch]),
 * the masthead with [counts], Oynat and Karıştır ([play], [shuffle]: the view's tracks), [trailing] beside them (the
 * panel's button while the panel is hidden); [below] under it (a scan going on, the converter's job).
 */
@Composable
internal fun WideMusicTop(
    inks: EbruInks,
    band: Dp,
    onSearch: (() -> Unit)?,
    counts: String?,
    play: (() -> Unit)?,
    shuffle: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().testTag("music-header")) {
        if (band > 0.dp) WideBand(inks, band, onSearch)
        WideMasthead(counts, play, shuffle, large = band >= TABLET_BAND, trailing = trailing)
        below()
    }
}

/** The band across a wide page's top, the library's search field over it ([onSearch]: a tap opens the search page). */
@Composable
internal fun WideBand(inks: EbruInks, height: Dp, onSearch: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height)) {
        EbruBand(inks, height)
        if (onSearch != null) {
            BandSearchField(onSearch, Modifier.padding(start = WIDE_MARGIN, end = WIDE_MARGIN, top = 24.dp).widthIn(max = 380.dp).fillMaxWidth())
        }
    }
}

/** "Albüm, sanatçı ya da parça" in a field of the chips' colour over the band; a tap opens the search page. */
@Composable
private fun BandSearchField(onSearch: () -> Unit, modifier: Modifier) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(24.dp)
    // the design's 86 % of the page's colour: the band's inks show through at its edges, its words read on it
    val field = if (colors.isDark) colors.background.copy(alpha = 0.86f) else colors.surface.copy(alpha = 0.90f)
    val label = stringResource(R.string.nag_music_search)
    Row(
        modifier
            .height(48.dp)
            .focusRing(state, shape)
            .clip(shape)
            .background(field)
            .background(stateBackground(state))
            .nagClickable(interaction, onSearch, onClickLabel = label)
            .semantics { contentDescription = label }
            .padding(start = 16.dp, end = 10.dp)
            .testTag("music-search"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.nag_music_search_hint),
            style = designText(15f, 20f),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The wide page's masthead: "Müzikler" (1.1.3: another page's [title]) in Bodoni's italic (60 sp under a tall band,
 * [large]), the counts under it; Oynat and Karıştır beside it ([play], [shuffle]; none without [pills]), then [trailing].
 */
@Composable
internal fun WideMasthead(
    counts: String?,
    play: (() -> Unit)?,
    shuffle: (() -> Unit)?,
    large: Boolean,
    title: String? = null,
    pills: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val colors = NagTheme.colors
    val type = NagTheme.type
    Row(
        Modifier.fillMaxWidth().padding(start = WIDE_MARGIN, end = WIDE_MARGIN, top = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title ?: stringResource(R.string.nag_music_masthead),
                style = if (large) type.masthead.copy(fontSize = type.size(60f), lineHeight = type.size(64f), letterSpacing = (-0.015).em) else type.masthead.copy(letterSpacing = (-0.015).em),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (counts != null) Text(counts, style = designText(14f, 20f), color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (pills) {
            PlayPill(play, Modifier.padding(bottom = 4.dp))
            ShufflePill(shuffle, Modifier.padding(bottom = 4.dp))
        }
        Box(Modifier.padding(bottom = 4.dp)) { trailing() }
    }
}

/** How long the library plays, for the masthead's counts: "2 gün 20 saat", "20 saat 14 dakika", "14 dakika". */
@Composable
internal fun libraryLength(ms: Long): String? {
    if (ms <= 0) return null
    val minutes = ms / 60_000
    val days = (minutes / (60 * 24)).toInt()
    val hours = ((minutes / 60) % 24).toInt()
    val rest = (minutes % 60).toInt()
    return when {
        days > 0 -> pluralStringResource(R.plurals.nag_music_length_days, days, days) + " " + pluralStringResource(R.plurals.nag_music_length_hours, hours, hours)
        hours > 0 -> pluralStringResource(R.plurals.nag_music_length_hours, hours, hours) + " " + pluralStringResource(R.plurals.nag_music_length_minutes, rest, rest)
        else -> pluralStringResource(R.plurals.nag_music_length_minutes, rest, rest)
    }
}

// ------------------------------------------------------------------- the tabs and the toolbar

/** The ways in as the design's tabs, inside the page's margins. */
@Composable
internal fun WideTabs(state: MusicBrowserState) {
    ViewTabs(state, Modifier.padding(start = WIDE_MARGIN - 12.dp, end = WIDE_MARGIN, top = 10.dp), start = 0.dp, ruleStart = 12.dp)
}

/**
 * The row under the tabs: the quick filters over a way in's groups; the order of the covers ([orderShown]: "Ada göre"),
 * the table's columns ([columnsShown]: "Sütunlar"), the filter, the covers or the rows; 1.1.3: [trailing] (the panel's
 * button while the masthead it stands in has folded away).
 */
@Composable
internal fun WideToolbar(state: MusicBrowserState, probing: Int, orderShown: Boolean, columnsShown: Boolean, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = WIDE_MARGIN, end = WIDE_MARGIN, top = 12.dp)
            .height(44.dp)
            .testTag("music-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // the chips start at the margin and take the room the buttons leave, scrolling sideways in a narrow window
        if (state.view.grouped) QuickChips(state, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        if (orderShown) SortButton(state, named = true, height = 44.dp)
        if (columnsShown) ColumnsButton(state)
        WideFilterButton(state, probing)
        if (state.view.grouped) LayoutSegment(state, button = 44.dp, inset = 2.dp)
        trailing()
    }
}

/**
 * The quick filters as the design's chips (44 dp, the chosen one on the selection's colour), radio buttons for TalkBack;
 * in a window too narrow for them they scroll sideways, the page's colour over those past the end.
 */
@Composable
internal fun QuickChips(state: MusicBrowserState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val page = NagTheme.colors.background
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier) {
        Row(
            Modifier.horizontalScroll(scroll).selectableGroup().testTag("music-quick"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickFilter.entries.forEach { filter -> QuickChip(stringResource(filter.label), state.quick == filter) { state.quick = filter } }
            // room past the last chip to scroll it clear of the fade
            if (scroll.maxValue > 0) Spacer(Modifier.width(24.dp))
        }
        if (scroll.value < scroll.maxValue) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(48.dp)
                    .height(44.dp)
                    .drawBehind {
                        val clear = page.copy(alpha = 0f)
                        drawRect(if (rtl) Brush.horizontalGradient(0f to page, 1f to clear) else Brush.horizontalGradient(0f to clear, 1f to page))
                    },
            )
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(22.dp)
    val haptics = rememberNagHaptics()
    val edge = if (selected) colors.selection else colors.borderStrong
    Box(
        Modifier
            .height(44.dp)
            .focusRing(state, shape)
            .clip(shape)
            .background(if (selected) colors.selection else Color.Transparent)
            .background(stateBackground(state))
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(edge, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(size.height / 2), style = Stroke(w))
            }
            .semantics { this.selected = selected }
            .nagClickable(interaction, {
                if (!selected) haptics.tick()
                onClick()
            }, role = Role.RadioButton)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = designText(14f, 20f, FontWeight.SemiBold), color = if (selected) colors.onSelection else colors.textSecondary, maxLines = 1)
    }
}

/** "Sütunlar": the table's columns, shown or not, moved, the table's own again. */
@Composable
private fun ColumnsButton(state: MusicBrowserState) {
    val colors = NagTheme.colors
    val labels = MusicColumn.entries.associateWith { stringResource(it.label) }
    val words = rememberColumnMenuWords()
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableIntStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val st = interaction.collectState()
    val shape = RoundedCornerShape(22.dp)
    val tint = if (open) colors.onSelection else colors.textSecondary
    Box(Modifier.onSizeChanged { anchor = it.height }) {
        Row(
            Modifier
                .height(44.dp)
                .focusRing(st, shape)
                .clip(shape)
                .background(stateBackground(st, open))
                .nagClickable(interaction, { open = true })
                .padding(start = 10.dp, end = 12.dp)
                .testTag("music-columns"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(NagIcons.Columns, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.nag_music_columns), style = designText(14f, 20f, FontWeight.SemiBold), color = tint, maxLines = 1)
        }
        MenuPopup(visible = open, onDismiss = { open = false }, entries = if (open) columnMenu(state, labels, words) else emptyList(), offset = IntOffset(0, anchor))
    }
}

/** The filter's round button, a dot on it while a filter holds; it opens [FilterPanel] under itself. */
@Composable
private fun WideFilterButton(state: MusicBrowserState, probing: Int) {
    var open by remember { mutableStateOf(false) }
    val colors = NagTheme.colors
    val on = stringResource(R.string.nag_music_filter_on)
    val active = state.filter.active
    Box(Modifier.testTag("music-filter")) {
        NagIconButton(
            NagIcons.Filter,
            stringResource(R.string.nag_music_filter),
            { open = !open },
            modifier = Modifier.semantics { if (active) stateDescription = on },
            selected = open,
            size = 44.dp,
            iconSize = 20.dp,
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(9.dp)
                    .background(colors.accent, CircleShape),
            )
        }
        if (open) FilterPanel(state, probing, onDismiss = { open = false })
    }
}

// ------------------------------------------------------------------- the open group's header

/**
 * An open group's header over its tracks (the design's album pane): its name in Bodoni's italic, its line with its
 * length, its listening trace with how many of its tracks were played, [extra] (the conversion) and the ink's play button.
 */
@Composable
internal fun WideGroupHeader(
    group: MusicGroup,
    view: MusicView,
    stats: Map<String, TrackStats>,
    playing: String?,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    extra: @Composable RowScope.() -> Unit = {},
) {
    val colors = NagTheme.colors
    val type = NagTheme.type
    val title = groupTitle(group, view)
    val minutes = remember(group) { (group.tracks.sumOf { it.durationMs.coerceAtLeast(0) } / 60_000).toInt() }
    val line = groupLine(group, view) + if (minutes > 0) " · " + stringResource(R.string.nag_duration_minutes, minutes) else ""
    val played = remember(group, stats) { group.tracks.count { (stats[it.uri]?.plays ?: 0) > 0 } }
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, bottom = 14.dp).testTag("music-group-header"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = TextStyle(fontFamily = BodoniHeadline, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = type.size(28f), lineHeight = type.size(34f)),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(line, style = designText(13f, 18f), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (view == MusicView.Albums && group.tracks.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListeningTrace(group.tracks, stats, playing, Modifier.width(traceWidth(group.tracks.size, 8.dp, 2.dp)), tick = 8.dp, gap = 2.dp, height = 6.dp)
                    Text(stringResource(R.string.nag_music_played_of, played, group.tracks.size), style = designText(12f, 16f), color = colors.textTertiary, maxLines = 1)
                }
            }
        }
        extra()
        if (onPlay != null) InkPlayButton(stringResource(R.string.nag_play) + ": " + title, onPlay)
    }
}

/** How wide a trace of [count] ticks of [tick] is, [gap] apart (at most its ticks). */
internal fun traceWidth(count: Int, tick: Dp, gap: Dp): Dp {
    val n = count.coerceIn(1, 24)
    return tick * n + gap * (n - 1)
}

/** The ink's round play button (48 dp), its shadow under it. */
@Composable
internal fun InkPlayButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    side: Dp = 48.dp,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = NagTheme.colors
    val state = interaction.collectState()
    Box(
        modifier
            .size(side)
            .pressScale(state.pressed)
            .focusRing(state, CircleShape)
            .shadow(10.dp, CircleShape)
            .background(colors.accent, CircleShape)
            .background(if (state.pressed) colors.onAccent.copy(alpha = 0.16f) else if (state.hovered) colors.onAccent.copy(alpha = 0.08f) else Color.Transparent, CircleShape)
            .nagClickable(interaction, onClick, onClickLabel = label)
            .semantics { contentDescription = label }
            .testTag("music-group-play"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(NagIcons.Play, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(side * 0.46f))
    }
}

/** A wide list's letter over its groups (the design's): the letter in the ink, 22 on 30, and a rule. */
@Composable
internal fun SmallLetterHeading(letter: String, said: String, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val type = NagTheme.type
    Row(
        modifier.fillMaxWidth().height(30.dp).padding(horizontal = 8.dp).clearAndSetSemantics {
            heading()
            contentDescription = "$letter, $said"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            letter,
            style = TextStyle(fontFamily = BodoniHeadline, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = type.size(22f), lineHeight = type.size(30f)),
            color = colors.accentText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.borderStrong))
    }
}

/** The weight of a wide row's title: the design's 650. */
internal val WideTitleWeight = SemiBoldPlus
