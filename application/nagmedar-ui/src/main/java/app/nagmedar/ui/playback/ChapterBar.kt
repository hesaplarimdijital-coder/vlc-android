package app.nagmedar.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SliderMark
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberMenuState
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.podcast.Chapter
import app.nagmedar.ui.podcast.ChapterSteps
import app.nagmedar.ui.podcast.Chapters
import app.nagmedar.ui.podcast.PodcastCenter
import app.nagmedar.ui.podcast.clock
import app.nagmedar.ui.theme.NagTheme
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** S-1104: the chapters of the podcast episode that plays (empty for any other media, and while they are read). */
@Composable
fun rememberPlayingChapters(): List<Chapter> {
    val playing by PodcastCenter.playing.collectAsState()
    return playing?.chapters.orEmpty()
}

/** S-1104: the chapters as marks on a seek bar of a media [lengthMs] long; none when the length is not known. */
@Composable
fun rememberChapterMarks(chapters: List<Chapter>, lengthMs: Long): List<SliderMark> {
    val res = LocalContext.current.resources
    return remember(chapters, lengthMs, res) {
        if (lengthMs <= 0 || chapters.isEmpty()) {
            emptyList()
        } else {
            chapters.mapIndexed { index, chapter -> SliderMark((chapter.startMs.toFloat() / lengthMs).coerceIn(0f, 1f), ChapterSteps.title(res, index, chapter)) }
        }
    }
}

/** The name of the chapter playing at [fraction] of a media [lengthMs] long; null before the first one. */
@Composable
fun chapterNameAt(chapters: List<Chapter>, lengthMs: Long, fraction: Float): String? {
    val res = LocalContext.current.resources
    val index = Chapters.indexAt(chapters, (lengthMs * fraction.coerceIn(0f, 1f)).toLong()) ?: return null
    return ChapterSteps.title(res, index, chapters[index])
}

/** The two buttons of the bars that go to the previous and the next chapter of the playing episode. */
@Composable
fun ChapterButtons(onStep: (forward: Boolean) -> Unit, modifier: Modifier = Modifier, size: Dp = NagTheme.dimens.minTarget.coerceAtLeast(NagTheme.dimens.controlHeight)) {
    Row(modifier.testTag("chapter-buttons")) {
        NagIconButton(NagIcons.ChapterPrevious, stringResource(R.string.nag_pod_chapter_previous), { onStep(false) }, size = size, shortcut = ShortcutId.PodcastPreviousChapter, modifier = Modifier.testTag("chapter-previous"))
        NagIconButton(NagIcons.ChapterNext, stringResource(R.string.nag_pod_chapter_next), { onStep(true) }, size = size, shortcut = ShortcutId.PodcastNextChapter, modifier = Modifier.testTag("chapter-next"))
    }
}

/**
 * S-1104: the chapters under a player's timeline: the previous and the next chapter, between them the name of the one
 * playing, which opens the list of them all (a choice goes there). Reachable by keyboard: every part takes the focus,
 * and the shortcuts (Ctrl+Alt+→ and ←) do the same from anywhere. Nothing without chapters.
 */
@Composable
fun ChapterStrip(
    chapters: List<Chapter>,
    lengthMs: Long,
    progress: () -> Float,
    onStep: (forward: Boolean) -> Unit,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chapters.isEmpty()) return
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val current = Chapters.indexAt(chapters, (lengthMs * progress().coerceIn(0f, 1f)).toLong())
    val title = current?.let { ChapterSteps.title(res, it, chapters[it]) } ?: stringResource(R.string.nag_pod_tab_chapters)
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val menu = rememberMenuState()
    var height by remember { mutableIntStateOf(0) }
    val inputModes = LocalInputModeManager.current
    var byKeys by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(modifier.fillMaxWidth().testTag("chapter-strip"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
        NagIconButton(NagIcons.ChapterPrevious, stringResource(R.string.nag_pod_chapter_previous), { onStep(false) }, shortcut = ShortcutId.PodcastPreviousChapter, modifier = Modifier.testTag("chapter-previous"))
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                    .onGloballyPositioned { height = it.size.height }
                    .focusRing(state, shape)
                    .background(stateBackground(state, menu.isOpen), shape)
                    .nagClickable(
                        interaction,
                        {
                            byKeys = inputModes.inputMode == ComposeInputMode.Keyboard
                            menu.open(IntOffset(0, height + 4))
                        },
                        role = Role.DropdownList,
                        onClickLabel = stringResource(R.string.nag_pod_tab_chapters),
                    )
                    .padding(horizontal = 10.dp)
                    .testTag("chapter-title"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(NagIcons.Chapters, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
                Text(title, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(NagIcons.ChevronDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            }
            MenuPopup(
                visible = menu.isOpen,
                onDismiss = menu::close,
                entries = if (menu.isOpen) {
                    chapters.mapIndexed { index, chapter ->
                        MenuEntry.Item("${clock(chapter.startMs)}  ${ChapterSteps.title(res, index, chapter)}", { onSeekToMs(chapter.startMs) }, checked = index == current)
                    }
                } else {
                    emptyList()
                },
                offset = menu.offset ?: IntOffset.Zero,
                minWidth = 220.dp,
                focusFirst = byKeys,
            )
        }
        NagIconButton(NagIcons.ChapterNext, stringResource(R.string.nag_pod_chapter_next), { onStep(true) }, shortcut = ShortcutId.PodcastNextChapter, modifier = Modifier.testTag("chapter-next"))
    }
}
