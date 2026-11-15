package app.nagmedar.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.listAppear
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberListAppear
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.OnReselect
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch

/**
 * 1.1.2 (the design's tablet page, TabletListe.dc.html): a wide window's groups as rows beside the open group's tracks:
 * under their letters while they are in order by a name ([words]), each a 56 dp row with its cover (in the ink's ring
 * while it plays), its name, its line and an album's trace, the open one ([openKey]) on the raised colour; the letters'
 * thin wave beside them.
 */
@Composable
internal fun WideGroupList(
    groups: List<MusicGroup>,
    state: MusicBrowserState,
    actions: MusicActions,
    listState: LazyListState,
    openKey: String?,
    modifier: Modifier,
    stats: Map<String, TrackStats>,
    playing: String?,
    words: GroupGridWords,
) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val nameless = words.nameless()
    val reduced = NagTheme.motion.reduced
    val entries = remember(groups, words.letters, words.sortName, locale, nameless) {
        letteredItems(groups, words.sortName.takeIf { words.letters }, { it.key }, locale, nameless)
    }
    val alphabet = rememberAlphabet()
    val letters = remember(entries, alphabet) { if (words.letters) waveLetters(runsOf(entries, 0), alphabet) else emptyList() }
    val wave = letters.count { it.count > 0 } >= 2
    // 1.0.8 (Desktop Kit 2.8.0): Müzik's entry tapped again: the groups go to their top
    OnReselect { if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
    val appear = rememberListAppear(state.view)
    LaunchedEffect(state.reveal, entries) {
        val key = state.reveal ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it is Lettered.Item && it.item.key == key }
        if (index >= 0) {
            if (reduced) listState.scrollToItem(index) else listState.animateScrollToItem(index)
            state.reveal = null
        }
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bottom = LocalShellBottomPadding.current
    Row(modifier) {
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight().testTag("music-groups"),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp) + bottom,
        ) {
            itemsIndexed(entries, key = { _, e -> if (e is Lettered.Letter) e.key else (e as Lettered.Item).item.key }, contentType = { _, e -> if (e is Lettered.Letter) 0 else 1 }) { index, entry ->
                when (entry) {
                    is Lettered.Letter -> SmallLetterHeading(entry.letter, words.count(entry.count), Modifier.padding(top = if (index == 0) 0.dp else 8.dp))
                    is Lettered.Item -> {
                        val group = entry.item
                        val title = if (state.view == MusicView.Folders) group.title?.substringAfterLast('/') ?: groupTitle(group, state.view) else groupTitle(group, state.view)
                        // the menu plays the group's tracks, as the table's rows do one track
                        val menu = { groupMenu(res, group, actions.playingFrom(groupSource(state.view, group))) }
                        ContextMenuArea(
                            entries = menu,
                            modifier = Modifier.listAppear(appear, index),
                            accessibilityLabel = stringResource(R.string.nag_more_actions),
                        ) {
                            WideGroupRow(group, state.view, title, stats, playing, selected = group.key == openKey) { state.group = group.key }
                        }
                    }
                }
            }
        }
        if (wave) {
            val gap = with(density) { 8.dp.roundToPx() }
            Spacer(Modifier.width(4.dp))
            LetterWave(
                letters,
                current = { currentWaveLetter(letters, listState.firstVisibleItemIndex) },
                onJump = { letter -> scope.launch { listState.scrollToItem(letter.index) } },
                top = { 0 },
                bottom = { listState.layoutInfo.viewportSize.height - with(density) { bottom.calculateBottomPadding().roundToPx() } - gap },
                style = WaveStyle.Thin,
                range = {
                    val info = listState.layoutInfo
                    val first = listState.firstVisibleItemIndex
                    val last = info.visibleItemsInfo.lastOrNull { it.offset < info.viewportEndOffset - info.afterContentPadding }?.index ?: first
                    currentWaveLetter(letters, first)..currentWaveLetter(letters, last)
                },
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** A wide window's group row (the design's): 56 dp, its cover or its sign, its name and line, an album's thin trace. */
@Composable
private fun WideGroupRow(
    group: MusicGroup,
    view: MusicView,
    title: String,
    stats: Map<String, TrackStats>,
    playing: String?,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(10.dp)
    val playsHere = remember(group, playing) { playing != null && group.tracks.any { it.uri == playing } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusRing(state, shape)
            .clip(shape)
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .background(stateBackground(state))
            .semantics { this.selected = selected }
            .nagClickable(interaction, onOpen)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (view == MusicView.Albums || view == MusicView.Recent) {
            Box(Modifier.size(40.dp)) {
                GroupCover(group, Modifier.size(40.dp).coverRing(playsHere, colors.accent, 4.dp, gap = 2.dp), corner = 4.dp, read = 56.dp)
            }
        } else {
            GroupSign(view, group.title, size = 40.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = designText(14f, 19f, WideTitleWeight),
                color = when {
                    playsHere -> colors.accentText
                    group.title == null -> colors.textSecondary
                    else -> colors.text
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(groupLine(group, view), style = designText(12f, 17f), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (view == MusicView.Albums) {
                ListeningTrace(group.tracks, stats, playing, Modifier.padding(top = 4.dp).fillMaxWidth(), tick = 5.dp, gap = 2.dp, height = 3.dp, low = 3.dp)
            }
        }
    }
}
