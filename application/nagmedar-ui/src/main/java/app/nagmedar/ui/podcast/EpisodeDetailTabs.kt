package app.nagmedar.ui.podcast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.transcribe.WhisperModel
import app.nagmedar.ui.transcribe.WhisperWorker

// ---------------------------------------------------------------- the show notes

internal fun LazyListScope.notesItems(ui: EpisodeDetailUi, images: Boolean, onImages: (Boolean) -> Unit, actions: PodcastActions) {
    if (ui.notes.isEmpty()) {
        item(key = "notes-none") {
            Frame {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.Info,
                    title = stringResource(R.string.nag_pod_notes_none_title),
                    description = stringResource(R.string.nag_pod_notes_none_text),
                    modifier = Modifier.testTag("episode-notes-none"),
                )
            }
        }
        return
    }
    if (ui.notes.any { it is NoteBlock.Picture }) {
        item(key = "notes-images") {
            Frame {
                NagButton(
                    stringResource(if (images) R.string.nag_pod_notes_hide_pictures else R.string.nag_pod_notes_show_pictures),
                    { onImages(!images) },
                    modifier = Modifier.testTag("episode-notes-pictures"),
                )
            }
        }
    }
    itemsIndexed(ui.notes, key = { index, _ -> "note-$index" }) { _, block ->
        Frame(Modifier.testTag("episode-note")) { NoteBlockView(block, images, actions.onOpenLink) }
    }
    val links = ShowNotes.links(ui.notes)
    if (links.isNotEmpty()) {
        item(key = "notes-links") {
            Frame {
                Text(stringResource(R.string.nag_pod_notes_links), style = NagTheme.type.bodyStrong, color = NagTheme.colors.text)
            }
        }
        items(links, key = { "link-$it" }) { url -> Frame { LinkRow(url, actions.onOpenLink) } }
    }
}

@Composable
private fun NoteBlockView(block: NoteBlock, images: Boolean, onLink: (String) -> Unit) {
    val colors = NagTheme.colors
    when (block) {
        is NoteBlock.Paragraph -> Text(annotated(block.runs, onLink), style = NagTheme.type.body, color = colors.text)
        is NoteBlock.Heading -> Text(
            annotated(block.runs, onLink),
            style = if (block.level <= 2) NagTheme.type.title else NagTheme.type.bodyStrong,
            color = colors.text,
        )
        is NoteBlock.Item -> Row(Modifier.padding(start = (block.depth * ITEM_INDENT).dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(block.marker, style = NagTheme.type.body, color = colors.textSecondary, modifier = Modifier.width(MARKER_WIDTH))
            Text(annotated(block.runs, onLink), style = NagTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
        }
        is NoteBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(colors.borderStrong))
            Text(annotated(block.runs, onLink), style = NagTheme.type.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        }
        is NoteBlock.Picture -> NotePicture(block, images)
    }
}

private const val ITEM_INDENT = 16
private val MARKER_WIDTH = 28.dp

/** A note's runs as one text: bold, italic, code as they were; a link in the accent colour, underlined, opening where [onLink] says. */
@Composable
private fun annotated(runs: List<NoteRun>, onLink: (String) -> Unit): AnnotatedString {
    val accent = NagTheme.colors.accent
    return buildAnnotatedString {
        runs.forEach { run ->
            val style = SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                fontFamily = if (run.code) FontFamily.Monospace else null,
            )
            val link = run.link
            if (link == null) {
                withStyle(style) { append(run.text) }
            } else {
                withLink(LinkAnnotation.Url(link, TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)), linkInteractionListener = { onLink(link) })) {
                    withStyle(style) { append(run.text) }
                }
            }
        }
    }
}

/** A picture of the note: loaded only when the person asked for pictures (and then only as the row scrolls into view); else its description stands in. */
@Composable
private fun NotePicture(block: NoteBlock.Picture, images: Boolean) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    val label = if (block.description.isBlank()) stringResource(R.string.nag_pod_notes_picture) else stringResource(R.string.nag_pod_notes_picture_of, block.description)
    val image = if (images) rememberPodcastCover(block.url, with(LocalDensity.current) { PICTURE_WIDTH.roundToPx() }) else null
    if (image != null) {
        Image(
            image,
            contentDescription = block.description.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(shape),
        )
    } else {
        Text(
            label,
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.border, shape).padding(12.dp),
        )
    }
}

private val PICTURE_WIDTH = 720.dp

@Composable
private fun LinkRow(url: String, onOpen: (String) -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, { onOpen(url) }, role = Role.Button, onClickLabel = stringResource(R.string.nag_pod_notes_open_link))
            .padding(horizontal = 8.dp)
            .testTag("episode-link"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(NagIcons.Network, contentDescription = null, tint = colors.accent, modifier = Modifier.size(dimens.icon))
        Text(url, style = NagTheme.type.label, color = colors.accent, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- the chapters

internal fun LazyListScope.chapterItems(ui: EpisodeDetailUi, now: Listening?, actions: PodcastActions) {
    when (val chapters = ui.chapters) {
        ExtraUi.Loading -> item(key = "chapters-loading") {
            Frame { LoadingState(stringResource(R.string.nag_pod_chapters_loading), posters = 0, modifier = Modifier.testTag("episode-chapters-loading")) }
        }
        ExtraUi.None -> item(key = "chapters-none") {
            Frame {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.Info,
                    title = stringResource(R.string.nag_pod_chapters_none_title),
                    description = stringResource(R.string.nag_pod_chapters_none_text),
                    modifier = Modifier.testTag("episode-chapters-none"),
                )
            }
        }
        ExtraUi.Offline -> item(key = "chapters-offline") { Frame { ExtraOffline(R.string.nag_pod_chapters_offline, actions.onRetryExtras, "episode-chapters-offline") } }
        is ExtraUi.Ready -> {
            val current = now?.let { Chapters.indexAt(chapters.value, it.positionMs) }
            itemsIndexed(chapters.value, key = { index, _ -> "chapter-$index" }) { index, chapter ->
                Frame { ChapterRow(index, chapter, index == current) { actions.onPlayAt(ui.feed, ui.episode, chapter.startMs) } }
            }
        }
    }
}

@Composable
private fun ChapterRow(index: Int, chapter: Chapter, current: Boolean, onPlay: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val title = chapter.title.ifBlank { stringResource(R.string.nag_pod_chapter_n, index + 1) }
    val from = stringResource(R.string.nag_pod_chapter_play_from, clock(chapter.startMs))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .focusRing(state, shape)
            .background(stateBackground(state, current), shape)
            .nagClickable(interaction, onPlay, role = Role.Button, onClickLabel = from)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("episode-chapter"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(clock(chapter.startMs), style = NagTheme.type.mono, color = if (current) colors.accent else colors.textSecondary, modifier = Modifier.width(TIME_WIDTH))
        Text(
            title,
            style = if (current) NagTheme.type.bodyStrong else NagTheme.type.body,
            color = if (current) colors.accent else colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (current) Icon(NagIcons.Play, contentDescription = stringResource(R.string.nag_pod_chapter_now), tint = colors.accent, modifier = Modifier.size(14.dp))
    }
}

private val TIME_WIDTH = 64.dp

@Composable
private fun ExtraOffline(text: Int, onRetry: () -> Unit, tag: String) {
    StatePanel(
        tone = StateTone.Neutral,
        icon = NagIcons.NetworkOff,
        title = stringResource(R.string.nag_pod_search_offline_title),
        description = stringResource(text),
        primary = StateAction(stringResource(R.string.nag_pod_retry), onRetry),
        modifier = Modifier.testTag(tag),
    )
}

// ---------------------------------------------------------------- the transcript

internal fun LazyListScope.transcriptItems(
    ui: EpisodeDetailUi,
    query: String,
    onQuery: (String) -> Unit,
    current: Int,
    actions: PodcastActions,
) {
    when (val transcript = ui.transcript) {
        ExtraUi.Loading -> item(key = "transcript-loading") {
            Frame { LoadingState(stringResource(R.string.nag_pod_transcript_loading), posters = 0, modifier = Modifier.testTag("episode-transcript-loading")) }
        }
        ExtraUi.None -> item(key = "transcript-none") {
            Frame {
                when (val job = ui.transcribe) {
                    is TranscribeUi.Idle -> StatePanel(
                        tone = StateTone.Neutral,
                        icon = NagIcons.Subtitle,
                        title = stringResource(R.string.nag_pod_transcript_none_title),
                        description = stringResource(R.string.nag_pod_transcript_none_whisper),
                        primary = StateAction(stringResource(R.string.nag_pod_whisper_create)) { actions.onTranscribe(ui.episode) },
                        modifier = Modifier.testTag("episode-transcript-none"),
                    ) { TranscribeNotes(job, downloaded = ui.download is DownloadUi.Done) }
                    is TranscribeUi.Queued, is TranscribeUi.Running -> TranscribeCard(job, ui, actions)
                    TranscribeUi.Unavailable -> StatePanel(
                        tone = StateTone.Neutral,
                        icon = NagIcons.Subtitle,
                        title = stringResource(R.string.nag_pod_transcript_none_title),
                        description = stringResource(R.string.nag_pod_transcript_none_text),
                        modifier = Modifier.testTag("episode-transcript-none"),
                    )
                }
            }
        }
        ExtraUi.Offline -> item(key = "transcript-offline") { Frame { ExtraOffline(R.string.nag_pod_transcript_offline, actions.onRetryExtras, "episode-transcript-offline") } }
        is ExtraUi.Ready -> {
            val value = transcript.value
            val found = if (query.isBlank()) null else value.find(query)
            item(key = "transcript-search") {
                Frame {
                    // a transcript made again: its job above the one shown until it is done
                    val job = ui.transcribe
                    if (job is TranscribeUi.Queued || job is TranscribeUi.Running) {
                        TranscribeCard(job, ui, actions)
                        Spacer(Modifier.height(NagTheme.dimens.gapS))
                    }
                    TranscriptSearch(query, onQuery, found?.size, value.timed)
                    value.whisperModel?.let { model ->
                        Text(
                            stringResource(R.string.nag_pod_transcript_whisper, stringResource(WhisperModel.of(model).label)),
                            style = NagTheme.type.labelSmall,
                            color = NagTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 6.dp).testTag("episode-transcript-whisper"),
                        )
                    }
                }
            }
            val shown = found ?: value.cues.indices.toList()
            if (shown.isEmpty()) {
                item(key = "transcript-no-match") {
                    Frame {
                        StatePanel(
                            tone = StateTone.Neutral,
                            icon = NagIcons.SearchOff,
                            title = stringResource(R.string.nag_pod_transcript_no_match, query.trim()),
                            modifier = Modifier.testTag("episode-transcript-no-match"),
                        )
                    }
                }
            } else {
                items(shown, key = { "cue-$it" }) { index ->
                    val cue = value.cues[index]
                    Frame {
                        CueRow(cue, index == current, query) { actions.onPlayAt(ui.feed, ui.episode, cue.startMs) }
                    }
                }
            }
        }
    }
}

/**
 * Under the "no transcript" panel's words: the model Whisper works with (and its size when it is downloaded first),
 * that the audio of an episode not downloaded comes into the cache for the job, why the last try gave none.
 */
@Composable
private fun TranscribeNotes(job: TranscribeUi.Idle, downloaded: Boolean) {
    val colors = NagTheme.colors
    val model = stringResource(job.model.label)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val size = job.downloadMb
        Text(
            if (size != null) stringResource(R.string.nag_pod_whisper_model_download, model, size) else stringResource(R.string.nag_pod_whisper_model_ready, model),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("episode-transcribe-model"),
        )
        if (!downloaded) Text(stringResource(R.string.nag_pod_whisper_fetch_note), style = NagTheme.type.labelSmall, color = colors.textSecondary, textAlign = TextAlign.Center)
        job.failure?.let { reason ->
            Text(
                stringResource(R.string.nag_pod_whisper_failed, stringResource(WhisperWorker.failureText(reason))),
                style = NagTheme.type.labelSmall,
                color = toneColors(StateTone.Warning).content,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("episode-transcribe-failed"),
            )
        }
    }
}

/** Whisper's job for the episode: waiting (after the job before it, or for the charger), or under way with how far it is, what it runs on and how fast; Cancel. */
@Composable
private fun TranscribeCard(job: TranscribeUi, ui: EpisodeDetailUi, actions: PodcastActions) {
    val context = LocalContext.current
    val cancel = StateAction(stringResource(R.string.nag_whisper_cancel)) { actions.onCancelTranscribe(ui.episode.id) }
    when (job) {
        is TranscribeUi.Queued -> StatusCard(
            StateTone.Neutral,
            NagIcons.Subtitle,
            stringResource(R.string.nag_pod_whisper_queued_title),
            stringResource(if (job.auto) R.string.nag_pod_whisper_queued_auto else R.string.nag_pod_whisper_queued_text),
            "episode-transcribe-queued",
            action = cancel,
        )
        is TranscribeUi.Running -> {
            val status = job.status
            val title = stringResource(R.string.nag_pod_whisper_running_title)
            StatusCard(StateTone.Info, NagIcons.Subtitle, title, WhisperWorker.progressLine(context, status), "episode-transcribe-running", action = cancel) {
                NagProgressBar(status.fraction, title, Modifier.padding(top = 6.dp))
                status.engine?.let { engine ->
                    Text(
                        WhisperWorker.engineLine(context, engine),
                        style = NagTheme.type.labelSmall,
                        color = NagTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp).testTag("episode-transcribe-engine"),
                    )
                }
            }
        }
        is TranscribeUi.Idle, TranscribeUi.Unavailable -> Unit
    }
}

@Composable
private fun TranscriptSearch(query: String, onQuery: (String) -> Unit, matches: Int?, timed: Boolean) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(dimens.radiusControl)
    val hint = stringResource(R.string.nag_pod_transcript_search)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(44.dp))
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(hint, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .tabMovesFocus(focusManager)
                    .semantics { contentDescription = hint }
                    .testTag("episode-transcript-search"),
            )
        }
        if (matches != null) Text(stringResource(R.string.nag_pod_transcript_matches, matches), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        if (query.isNotEmpty()) NagIconButton(NagIcons.Close, stringResource(R.string.nag_search_clear), { onQuery("") })
    }
    if (!timed) Text(stringResource(R.string.nag_pod_transcript_untimed), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun CueRow(cue: TranscriptCue, current: Boolean, query: String, onSeek: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val seek = stringResource(R.string.nag_pod_chapter_play_from, clock(cue.startMs))
    val highlight = colors.selection
    val text = remember(cue.text, query, highlight) { highlighted(cue.text, query, highlight) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .focusRing(state, shape)
            .background(stateBackground(state, current), shape)
            .then(if (cue.timed) Modifier.nagClickable(interaction, onSeek, role = Role.Button, onClickLabel = seek) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("episode-cue"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (cue.timed) Text(clock(cue.startMs), style = NagTheme.type.mono, color = if (current) colors.accent else colors.textSecondary, modifier = Modifier.width(TIME_WIDTH))
        Column(Modifier.weight(1f)) {
            cue.speaker?.let { Text(it, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(text, style = if (current) NagTheme.type.bodyStrong else NagTheme.type.body, color = if (current) colors.accent else colors.text)
        }
    }
}

/** [text] with the places [term] is found in marked, ignoring case. */
private fun highlighted(text: String, term: String, mark: Color): AnnotatedString {
    val needle = term.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var from = 0
        while (true) {
            val at = text.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            append(text.substring(from, at))
            withStyle(SpanStyle(background = mark)) { append(text.substring(at, at + needle.length)) }
            from = at + needle.length
        }
        append(text.substring(from))
    }
}
