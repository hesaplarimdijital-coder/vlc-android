package app.nagmedar.ui.player.navigate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.contextMenuGesture
import app.nagmedar.ui.player.ChapterMark
import app.nagmedar.ui.player.FramePreviews
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelSection
import app.nagmedar.ui.player.panel.SectionTitle
import app.nagmedar.ui.player.playerClock
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/**
 * The panel's bookmarks tab (S-608): the film's bookmarks in time order, each with a frame of its
 * moment, its name and its time; a click goes there, the "⋯" button or a right click renames or
 * removes it. "Add" (Ctrl+D) marks where the film is. They are kept in the medialibrary.
 */
@Composable
fun BookmarksPanel(
    marks: List<BookmarkMark>,
    refused: Boolean,
    previews: FramePreviews?,
    onAdd: () -> Unit,
    onSeek: (Long) -> Unit,
    onRename: (BookmarkMark) -> Unit,
    onRemove: (BookmarkMark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val addKeys = ShortcutRegistry.chordLabel(ShortcutId.AddBookmark, resources)
    Column(modifier.fillMaxSize().testTag("bookmarks-panel")) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            PanelSection(spacing = 3.dp) {
                SectionTitle(stringResource(R.string.nag_bm_title, marks.size))
                if (marks.isEmpty()) {
                    PanelCaption(
                        if (addKeys != null) stringResource(R.string.nag_bm_empty_keys, addKeys) else stringResource(R.string.nag_bm_empty),
                        Modifier.padding(vertical = 6.dp).testTag("bookmarks-empty"),
                    )
                }
                marks.forEach { mark -> BookmarkRow(mark, previews, onSeek, onRename, onRemove) }
            }
            if (refused) {
                Text(
                    stringResource(R.string.nag_bm_refused),
                    style = NagTheme.type.labelSmall,
                    color = NagTheme.colors.warning,
                    modifier = Modifier.testTag("bookmarks-refused"),
                )
            }
            PanelCaption(stringResource(R.string.nag_bm_kept))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NagButton(
                stringResource(R.string.nag_bm_add),
                onClick = onAdd,
                icon = NagIcons.Plus,
                primary = true,
                modifier = Modifier.weight(1f).testTag("bookmarks-add"),
            )
            if (addKeys != null) KeyCap(addKeys)
        }
    }
}

/** A bookmark: a frame of its moment, its name and time; a click goes there, "⋯" and a right click show what else. */
@Composable
private fun BookmarkRow(
    mark: BookmarkMark,
    previews: FramePreviews?,
    onSeek: (Long) -> Unit,
    onRename: (BookmarkMark) -> Unit,
    onRemove: (BookmarkMark) -> Unit,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val entries = listOf(
        MenuEntry.Item(stringResource(R.string.nag_bm_rename), { onRename(mark) }),
        MenuEntry.Item(stringResource(R.string.nag_bm_remove), { onRemove(mark) }, destructive = true),
    )
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .background(stateBackground(state), RoundedCornerShape(7.dp))
                .contextMenuGesture { at -> menuAt = IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                .nagClickable(interaction, { onSeek(mark.timeMs) }, role = Role.Button, onClickLabel = stringResource(R.string.nag_bm_go, mark.name))
                .padding(start = 8.dp, end = 2.dp)
                .testTag("bookmark-row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FrameThumb(previews, mark.timeMs)
            Text(
                mark.name,
                style = NagTheme.type.label,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(playerClock(mark.timeMs), style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
            NagIconButton(
                NagIcons.MoreHorizontal,
                stringResource(R.string.nag_bm_more, mark.name),
                { menuAt = IntOffset.Zero },
                size = 28.dp,
                iconSize = 14.dp,
            )
        }
        MenuPopup(
            visible = menuAt != null,
            onDismiss = { menuAt = null },
            entries = if (menuAt != null) entries else emptyList(),
            offset = menuAt ?: IntOffset.Zero,
            minWidth = 180.dp,
        )
    }
}

/**
 * The film's chapters, as the Info tab lists them (S-608, after the mockups' "Panel · Info"): a
 * frame of each one's start, its number and name, its time; the one playing lit. A click goes to
 * its start; Shift+N and Shift+P go to the next and the one before.
 */
@Composable
fun ChaptersSection(chapters: List<ChapterMark>, nowMs: Long, previews: FramePreviews?, onSeek: (Long) -> Unit) {
    if (chapters.isEmpty()) return
    val colors = NagTheme.colors
    val resources = LocalContext.current.resources
    val current = chapters.indexOfLast { it.startMs <= nowMs }
    PanelSection(spacing = 3.dp) {
        SectionTitle(stringResource(R.string.nag_chapters_title, chapters.size)) {
            val next = ShortcutRegistry.chordLabel(ShortcutId.NextChapter, resources)
            val previous = ShortcutRegistry.chordLabel(ShortcutId.PreviousChapter, resources)
            if (next != null && previous != null) {
                KeyCap(next)
                PanelCaption(" / ")
                KeyCap(previous)
                PanelCaption(stringResource(R.string.nag_chapters_keys), Modifier.padding(start = 4.dp))
            }
        }
        chapters.forEachIndexed { index, chapter ->
            val lit = index == current
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val title = if (chapter.name.isBlank()) {
                stringResource(R.string.nag_player_chapter, index + 1)
            } else {
                stringResource(R.string.nag_chapters_row, index + 1, chapter.name)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .background(if (lit) colors.accent.copy(alpha = 0.14f) else stateBackground(state), RoundedCornerShape(7.dp))
                    .nagClickable(interaction, { onSeek(chapter.startMs) }, role = Role.Button, onClickLabel = title)
                    .padding(horizontal = 8.dp)
                    .testTag("chapter-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // a moment into the chapter: its first frame is often black
                FrameThumb(previews, chapter.startMs + FramePreviews.STEP_MS)
                Text(
                    title,
                    style = NagTheme.type.label.copy(fontWeight = if (lit) FontWeight.Medium else FontWeight.Normal),
                    color = if (lit) Color.White else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(playerClock(chapter.startMs), style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** A frame of the film at [ms], small (44 × 25 dp); a dark gradient while it is read, or when the file gives none. */
@Composable
fun FrameThumb(previews: FramePreviews?, ms: Long, modifier: Modifier = Modifier) {
    var frame by remember(previews, ms / FramePreviews.STEP_MS) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(previews, ms / FramePreviews.STEP_MS) {
        frame = previews?.frameAt(ms)
    }
    val shape = RoundedCornerShape(4.dp)
    val image = frame
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(44.dp, 25.dp).clip(shape))
    } else {
        Box(modifier.size(44.dp, 25.dp).background(Brush.verticalGradient(listOf(Color(0xFF1D2A3A), Color(0xFF3B4A5C))), shape))
    }
}
