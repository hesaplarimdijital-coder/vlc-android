/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: after Metamatik (feature/editor/src/main/kotlin/com/bobbyesp/metadator/feature/editor/EditorScreen.kt and feature/editor/src/main/kotlin/com/bobbyesp/metadator/feature/editor/components/Fields.kt),
 * a fork of Metadator, under the AGPL-3.0; written again on Nağmedar's components.
 */
package app.nagmedar.ui.tags

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.FieldKind
import app.nagmedar.tags.TagField
import app.nagmedar.tags.editor.splitValues
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.onboarding.MediaAccess
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.tools.Notices

/**
 * 1.2.0, Metamatik's editor in Nağmedar: the tags of [wanted] (one file, or several edited together), from the panel's
 * "Düzenle", the phone's page or another app's file. The editor of the files last asked for stays while the app runs
 * ([TagSessions]): one whose changes are not saved stays when other files are asked for, and says so. [empty] is said
 * when there is nothing to edit.
 */
@Composable
fun TagEditorPane(
    wanted: List<TagTarget>,
    modifier: Modifier = Modifier,
    empty: String? = null,
    page: Boolean = false,
    /** 1.3.0: where "Vazgeç" leaves to (the panel's view before, the page's close); null: it only drops the changes. */
    onCancel: (() -> Unit)? = null,
) {
    if (wanted.isEmpty()) {
        EditorNote(empty ?: stringResource(R.string.nag_tag_none_selected), modifier)
        return
    }
    if (wanted.size > TagEditorModel.MAX_FILES) {
        EditorNote(stringResource(R.string.nag_tag_too_many, TagEditorModel.MAX_FILES), modifier)
        return
    }
    val model = rememberTagEditor(wanted)
    TagEditor(model, modifier, held = model.targets != wanted, page = page, onCancel = onCancel)
}

/** The editor of [targets]: the one kept for them, else a new one (unless the one kept has changes not saved). */
@Composable
fun rememberTagEditor(targets: List<TagTarget>): TagEditorModel {
    val files = rememberTagFiles()
    val context = LocalContext.current
    val style by rememberUpdatedState(TagSettings.style(context))
    val model = TagSessions.open(files, targets) { style }
    val app = context.applicationContext
    DisposableEffect(model) {
        // a save that ends after the screen went is said too: the notices are the app's
        model.say = { message -> showTagMessage(app, message) }
        onDispose { }
    }
    return model
}

/** A word in place of the editor: nothing selected, too many. */
@Composable
private fun EditorNote(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(NagTheme.dimens.gapL).testTag("tag-editor-note"), contentAlignment = Alignment.TopStart) {
        Text(text, style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
    }
}

/**
 * The editor of [model]: what the files are and their cover at its head, the fields in sections ("Tüm etiketler" last,
 * every other tag of the files by its name), and at its foot how many changes wait and the buttons that save or drop
 * them, always in sight. [held]: other files were asked for, and these have changes not saved yet. Ctrl+S saves.
 */
@Composable
internal fun TagEditor(model: TagEditorModel, modifier: Modifier = Modifier, held: Boolean = false, page: Boolean = false, onCancel: (() -> Unit)? = null) {
    val dimens = NagTheme.dimens
    val focusManager = LocalFocusManager.current
    // 1.3.0: the lookup open over the editor
    var tool by remember(model) { mutableStateOf<TagTool?>(null) }
    // a value being typed into a field of several is one once the field lets go of the focus: let go before saving
    val save = {
        focusManager.clearFocus()
        model.save()
    }
    Column(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.S) {
                    save()
                    true
                } else {
                    false
                }
            }
            .testTag("tag-editor"),
    ) {
        when (val phase = model.phase) {
            EditorPhase.Loading -> LoadingState(
                stringResource(R.string.nag_tag_loading),
                Modifier.fillMaxWidth(),
                progressText = if (model.many) pluralStringResource(R.plurals.nag_tag_files, model.targets.size, model.targets.size) else null,
                posters = 0,
                dense = true,
            )
            is EditorPhase.Problem -> ProblemView(phase, model)
            EditorPhase.Ready -> {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (page) dimens.pagePadding else dimens.gapL, vertical = dimens.gapM),
                    verticalArrangement = Arrangement.spacedBy(dimens.gapL),
                ) {
                    if (held) HeldNote()
                    Head(model)
                    // 1.3.0: looked up online
                    ToolButtons(model) { tool = it }
                    Notes(model)
                    if (model.many) BatchTools(model)
                    Fields(model)
                }
                Footer(model, save, onCancel)
                tool?.let { open -> ToolDialog(model, open) { tool = null } }
            }
        }
    }
}

/** Other files were asked for: these are kept until their changes are saved or dropped. */
@Composable
private fun HeldNote() {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.warning, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("tag-held"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.nag_tag_held), style = NagTheme.type.labelSmall, color = colors.text)
    }
}

/** Why there are no tags to show, and what to do. */
@Composable
private fun ProblemView(phase: EditorPhase.Problem, model: TagEditorModel) {
    val context = LocalContext.current
    val retry = StateAction(stringResource(R.string.nag_tag_retry)) { model.load() }
    val (title, description, primary) = when (phase.problem) {
        TagProblem.NotFound -> Triple(stringResource(R.string.nag_tag_not_found), null, retry)
        TagProblem.NeedsAccess -> Triple(
            stringResource(R.string.nag_tag_needs_access),
            null,
            StateAction(stringResource(R.string.nag_tag_grant)) { openAllFilesAccess(context) },
        )
        TagProblem.Unsupported -> Triple(stringResource(R.string.nag_tag_unsupported), null, null)
        TagProblem.Failed -> Triple(stringResource(R.string.nag_tag_read_failed), phase.message, retry)
    }
    Box(Modifier.fillMaxWidth().testTag("tag-problem"), contentAlignment = Alignment.TopCenter) {
        StatePanel(
            tone = if (phase.problem == TagProblem.Unsupported) StateTone.Neutral else StateTone.Warning,
            icon = if (phase.problem == TagProblem.NeedsAccess) NagIcons.Lock else NagIcons.Warning,
            title = title,
            description = description,
            primary = primary,
            secondary = if (phase.problem == TagProblem.NeedsAccess) retry else null,
            dense = true,
        )
    }
}

// ------------------------------------------------------------------ the head

/** The cover (a menu: choose, remove, undo), what the files are, and what their audio is. */
@Composable
private fun Head(model: TagEditorModel) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
        CoverButton(model)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val (title, line) = headWords(model)
            Text(
                title,
                style = NagTheme.type.title,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }.testTag("tag-head-title"),
            )
            if (line.isNotEmpty()) Text(line, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val facts = facts(model)
            if (facts.isNotEmpty()) Text(facts, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 2)
            if (model.many) {
                // what the files' covers will be
                val cover = model.cover
                val text = when {
                    !cover.changed -> stringResource(R.string.nag_tag_covers_own)
                    cover.picture == null -> stringResource(R.string.nag_tag_covers_removed)
                    else -> stringResource(R.string.nag_tag_covers_replaced)
                }
                Text(text, style = NagTheme.type.labelSmall, color = if (cover.changed) colors.accentText else colors.textTertiary, modifier = Modifier.testTag("tag-covers"))
            }
        }
    }
}

/** The head's title and line: one file's title and artist, or how many files and their names. */
@Composable
private fun headWords(model: TagEditorModel): Pair<String, String> {
    if (model.many) {
        val count = model.loaded.size
        val names = model.loaded.take(3).joinToString(", ") { it.target.name } + if (count > 3) ", …" else ""
        return pluralStringResource(R.plurals.nag_tag_files, count, count) to names
    }
    val target = model.targets.single()
    val title = model.field(TagField.Title.key).text.ifBlank { target.title ?: target.name }
    val artist = model.field(TagField.Artist.key).values.joinToString(", ")
    val album = model.field(TagField.Album.key).text
    return title to listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")
}

/** "FLAC · 44,1 kHz · 1.024 kb/s": one file's format and audio. */
@Composable
private fun facts(model: TagEditorModel): String {
    if (model.many) return ""
    val locale = LocalContext.current.resources.locale()
    val audio = model.audio
    val target = model.targets.single()
    return listOfNotNull(
        target.extension?.uppercase(Locale.ROOT),
        audio?.sampleRateHz?.takeIf { it > 0 }?.let { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it / 1000.0) + " kHz" },
        audio?.bitrateKbps?.takeIf { it > 0 }?.let { NumberFormat.getIntegerInstance(locale).format(it) + " kb/s" },
    ).joinToString(" · ")
}

/** The cover, 72 dp: pressed, its menu (choose one, remove it, undo the change). */
@Composable
private fun CoverButton(model: TagEditorModel) {
    val colors = NagTheme.colors
    val cover = model.cover
    val context = LocalContext.current
    val app = context.applicationContext
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) chooseCover(model, uri, app)
    }
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(8.dp)
    val label = stringResource(R.string.nag_tag_cover)
    val changed = stringResource(R.string.nag_tag_changed)
    Box(Modifier.onSizeChanged { height = it.height }) {
        Box(
            Modifier
                .size(72.dp)
                .clip(shape)
                .background(colors.surfaceRaised, shape)
                .border(if (cover.changed) 2.dp else 1.dp, if (cover.changed) colors.accent else colors.border, shape)
                .semantics {
                    contentDescription = label
                    if (cover.changed) stateDescription = changed
                }
                .testTag("tag-cover"),
            contentAlignment = Alignment.Center,
        ) {
            val image = rememberPicture(cover.picture)
            when {
                image != null -> Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().testTag("tag-head-picture"))
                else -> Icon(if (model.many && cover.own) NagIcons.Albums else NagIcons.Music, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(28.dp))
            }
            NagIconButton(
                NagIcons.MoreHorizontal,
                stringResource(R.string.nag_tag_cover_menu),
                { open = true },
                modifier = Modifier.align(Alignment.BottomEnd).testTag("tag-cover-menu"),
                size = 28.dp,
                iconSize = 14.dp,
                tint = colors.text,
            )
        }
        val entries = buildList {
            add(MenuEntry.Item(context.getString(R.string.nag_tag_cover_choose), {
                runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }, icon = NagIcons.FolderAdd))
            if (model.many || cover.picture != null) {
                add(MenuEntry.Item(context.getString(if (model.many) R.string.nag_tag_covers_remove else R.string.nag_tag_cover_remove), model::removeCover, icon = NagIcons.Delete, destructive = true))
            }
            if (cover.changed) add(MenuEntry.Item(context.getString(R.string.nag_tag_cover_revert), model::revertCover, icon = NagIcons.Resume))
        }
        MenuPopup(visible = open, onDismiss = { open = false }, entries = if (open) entries else emptyList(), offset = IntOffset(0, height))
    }
}

/** [picture] decoded for the head, off the main thread. */
@Composable
private fun rememberPicture(picture: EmbeddedPicture?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, picture) {
        value = picture?.let { decodePicture(it.data, 256) }
    }
    return image
}

private suspend fun decodePicture(bytes: ByteArray, px: Int): ImageBitmap? = withContext(Dispatchers.Default) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

/** The picture at [uri] as the cover: read whole (20 MB at most), its type the provider's or its first bytes'. */
internal fun chooseCover(model: TagEditorModel, uri: Uri, context: Context) {
    model.chooseCover {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val read = input.readBytes()
                read.takeIf { it.size <= MAX_COVER_BYTES }
            } ?: return@withContext null
            val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: sniffImage(bytes) ?: return@withContext null
            bytes to mime
        }
    }
}

private const val MAX_COVER_BYTES = 20 * 1024 * 1024

/** A JPEG's, a PNG's or a WebP's type by its first bytes. */
internal fun sniffImage(bytes: ByteArray): String? = when {
    bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() -> "image/png"
    bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
    else -> null
}

// ------------------------------------------------------------------ notes

/** What the editor has noticed: files it could not read; text garbled in a way it can put right. */
@Composable
private fun Notes(model: TagEditorModel) {
    val colors = NagTheme.colors
    if (model.skipped > 0) {
        Text(pluralStringResource(R.plurals.nag_tag_skipped, model.skipped, model.skipped), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("tag-skipped"))
    }
    val repairs = model.repairs
    if (repairs.isNotEmpty()) {
        val labels = fieldLabels()
        val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, shape)
                .padding(12.dp)
                .testTag("tag-garbled"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.nag_tag_garbled), style = NagTheme.type.bodyStrong, color = colors.text)
            repairs.entries.take(4).forEach { (key, values) ->
                Text(
                    (labels[key] ?: key) + ": " + values.joinToString(", ") { it.lineSequence().first() },
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NagButton(stringResource(R.string.nag_tag_garbled_fix), model::repairText, modifier = Modifier.testTag("tag-garbled-fix"))
        }
    }
}

// ------------------------------------------------------------------ the fields

/** The fields of the sections, in order; several files leave out what each file has of its own. */
@Composable
private fun Fields(model: TagEditorModel) {
    val labels = fieldLabels()
    val separator = TagSettings.style(LocalContext.current).separator
    val many = model.many
    Section(stringResource(R.string.nag_tag_section_main)) {
        if (!many) FieldInput(model, TagField.Title, labels, separator)
        FieldInput(model, TagField.Artist, labels, separator)
        FieldInput(model, TagField.Album, labels, separator)
        FieldInput(model, TagField.AlbumArtist, labels, separator)
        SideBySide({ FieldInput(model, TagField.Date, labels, separator) }, { FieldInput(model, TagField.Genre, labels, separator) })
        SideBySide({ PositionInput(model, TagField.TrackNumber, labels) }, { PositionInput(model, TagField.DiscNumber, labels) })
    }
    Section(stringResource(R.string.nag_tag_section_credits)) {
        CreditFields.forEach { FieldInput(model, it, labels, separator) }
    }
    Section(stringResource(R.string.nag_tag_section_more)) {
        FieldInput(model, TagField.Comment, labels, separator)
        if (!many) SideBySide({ FieldInput(model, TagField.Bpm, labels, separator) }, { FieldInput(model, TagField.Isrc, labels, separator) })
        FieldInput(model, TagField.Copyright, labels, separator)
        SideBySide({ FieldInput(model, TagField.Label, labels, separator) }, { FieldInput(model, TagField.Grouping, labels, separator) })
    }
    if (!many) {
        Section(stringResource(R.string.nag_tag_section_lyrics)) {
            FieldInput(model, TagField.Lyrics, labels, separator)
        }
    }
    AllTags(model, separator) { key ->
        ChipsField(key, model.field(key), separator, onChange = { model.set(key, it) }, onRevert = { model.revert(listOf(key)) }, tag = key)
    }
}

private val CreditFields = listOf(TagField.Composer, TagField.Lyricist, TagField.Conductor, TagField.Remixer, TagField.Performer)

/** The labels of the fields the editor names. */
@Composable
internal fun fieldLabels(): Map<String, String> {
    val context = LocalContext.current
    return remember(context.resources.configuration) {
        TagField.entries.associate { field -> field.key to context.getString(fieldLabel(field)) }
    }
}

private fun fieldLabel(field: TagField): Int = when (field) {
    TagField.Title -> R.string.nag_tag_title
    TagField.Artist -> R.string.nag_tag_artists
    TagField.Album -> R.string.nag_tag_album
    TagField.AlbumArtist -> R.string.nag_tag_album_artists
    TagField.Date -> R.string.nag_tag_date
    TagField.Genre -> R.string.nag_tag_genres
    TagField.TrackNumber -> R.string.nag_tag_track
    TagField.DiscNumber -> R.string.nag_tag_disc
    TagField.Composer -> R.string.nag_tag_composers
    TagField.Lyricist -> R.string.nag_tag_lyricists
    TagField.Conductor -> R.string.nag_tag_conductors
    TagField.Remixer -> R.string.nag_tag_remixers
    TagField.Performer -> R.string.nag_tag_performers
    TagField.Comment -> R.string.nag_tag_comment
    TagField.Lyrics -> R.string.nag_tag_lyrics
    TagField.Bpm -> R.string.nag_tag_bpm
    TagField.Isrc -> R.string.nag_tag_isrc
    TagField.Copyright -> R.string.nag_tag_copyright
    TagField.Label -> R.string.nag_tag_label
    TagField.Grouping -> R.string.nag_tag_grouping
}

/** A titled group of fields. */
@Composable
private fun Section(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(currentLocale()),
                style = NagTheme.type.overline,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            trailing?.invoke()
        }
        content()
    }
}

/** Two fields side by side, sharing the width. */
@Composable
private fun SideBySide(start: @Composable () -> Unit, end: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) { start() }
        Box(Modifier.weight(1f)) { end() }
    }
}

/** [field] as its kind asks: values one by one for a field of several, else a line (or lines) of text. */
@Composable
private fun FieldInput(model: TagEditorModel, field: TagField, labels: Map<String, String>, separator: String) {
    val label = labels[field.key] ?: field.key
    val state = model.field(field.key)
    val revert = { model.revert(listOf(field.key)) }
    if (field.multiValue) {
        ChipsField(label, state, separator, onChange = { model.set(field.key, it) }, onRevert = revert, tag = field.key)
        return
    }
    val long = field.kind == FieldKind.LongText
    TextInput(
        label = label,
        state = state,
        onChange = { model.set(field.key, listOf(it)) },
        onRevert = revert,
        lines = when (field) {
            TagField.Lyrics -> 6
            TagField.Comment -> 2
            else -> 1
        },
        keyboardType = if (field.kind == FieldKind.Number) KeyboardType.Number else KeyboardType.Text,
        placeholder = if (field.kind == FieldKind.Date) stringResource(R.string.nag_tag_date_hint) else null,
        tag = field.key,
        long = long,
    )
}

/** A field's name over it, "değişti" after it when it was changed, and its undo. */
@Composable
private fun FieldLabel(label: String, changed: Boolean, onRevert: (() -> Unit)?) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NagTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (changed) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.nag_tag_changed), style = NagTheme.type.labelSmall, color = colors.accentText, maxLines = 1)
            }
        }
        if (changed && onRevert != null) {
            NagIconButton(NagIcons.Resume, stringResource(R.string.nag_tag_revert, label), onRevert, size = 28.dp, iconSize = 14.dp)
        }
    }
}

@Composable
private fun TextInput(
    label: String,
    state: FieldState,
    onChange: (String) -> Unit,
    onRevert: () -> Unit,
    lines: Int,
    keyboardType: KeyboardType,
    placeholder: String?,
    tag: String,
    long: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label, state.changed, onRevert)
        NagTextField(
            // a field of one value shows its first; a long one (a comment, the lyrics) all of its lines
            value = state.text,
            onValueChange = onChange,
            label = label,
            showLabel = false,
            placeholder = if (state.mixed) stringResource(R.string.nag_tag_mixed) else placeholder,
            lines = lines,
            keyboardType = keyboardType,
            imeAction = if (long) ImeAction.Default else ImeAction.Next,
            modifier = Modifier.testTag("tag-field-$tag"),
        )
    }
}

/**
 * A field of several values (artists, genres): one chip a value, so "Tyler, The Creator" is one artist and not two.
 * What is typed becomes one with Enter, the [separator] or the field letting go of the focus ("A; B" pasted: two);
 * a chip pressed goes back into the input to be edited; its cross takes it away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsField(label: String, state: FieldState, separator: String, onChange: (List<String>) -> Unit, onRevert: () -> Unit, tag: String) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    var input by remember(tag) { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val values = state.values
    val latestValues by rememberUpdatedState(values)
    val commit = {
        val added = splitValues(input, separator)
        if (added.isNotEmpty()) onChange(latestValues + added)
        input = ""
    }
    val shape = RoundedCornerShape(dimens.radiusControl)
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label, state.changed, onRevert)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.controlBorder, shape)
                .padding(start = if (values.isEmpty()) 14.dp else 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .testTag("tag-field-$tag"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            values.forEachIndexed { index, value ->
                ValueChip(
                    value,
                    onEdit = {
                        commit()
                        input = value
                        onChange(latestValues.filterIndexed { i, _ -> i != index })
                    },
                    onRemove = { onChange(latestValues.filterIndexed { i, _ -> i != index }) },
                )
            }
            val end = separator.trim()
            BasicTextField(
                value = input,
                onValueChange = { typed ->
                    if (end.isNotEmpty() && typed.endsWith(end)) {
                        input = typed.removeSuffix(end)
                        commit()
                    } else {
                        input = typed
                    }
                },
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                interactionSource = interaction,
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .padding(vertical = 4.dp)
                    .onFocusChanged {
                        if (focused && !it.isFocused) commit()
                        focused = it.isFocused
                    }
                    .semantics { contentDescription = label }
                    .testTag("tag-input-$tag"),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            val hint = when {
                                state.mixed -> stringResource(R.string.nag_tag_mixed)
                                values.isEmpty() -> stringResource(R.string.nag_tag_add_value)
                                else -> ""
                            }
                            if (hint.isNotEmpty()) Text(hint, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** One of a field's values: pressed to edit it, its cross to take it away. */
@Composable
private fun ValueChip(value: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .heightIn(min = 30.dp)
            .clip(shape)
            .background(colors.hover, shape)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = NagTheme.type.body,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .nagClickable(interaction, onEdit, onClickLabel = stringResource(R.string.nag_tag_edit_value)),
        )
        NagIconButton(NagIcons.Close, stringResource(R.string.nag_tag_remove_value, value), onRemove, size = 28.dp, iconSize = 12.dp)
    }
}
