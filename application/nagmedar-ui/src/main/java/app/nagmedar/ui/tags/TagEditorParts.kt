/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: after Metamatik (feature/editor/src/main/kotlin/com/bobbyesp/metadator/feature/editor/EditorScreen.kt),
 * a fork of Metadator, under the AGPL-3.0; written again on Nağmedar's components.
 */
package app.nagmedar.ui.tags

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.tags.TagField
import app.nagmedar.tags.editor.MultiValueMode
import app.nagmedar.ui.R
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.onboarding.MediaAccess
import app.nagmedar.ui.theme.NagTheme
import org.videolan.tools.Notices
import org.videolan.tools.Settings

// ------------------------------------------------------------------ positions

/** A track's or a disc's number and total side by side under one label: "3 / 12". */
@Composable
internal fun PositionInput(model: TagEditorModel, field: TagField, labels: Map<String, String>) {
    val label = labels[field.key] ?: field.key
    val state = model.position(field)
    val mixed = stringResource(R.string.nag_tag_mixed_short)
    val total = stringResource(R.string.nag_tag_total)
    val editable = model.numberEditable(field)
    Column(Modifier.fillMaxWidth()) {
        PositionLabel(label, state.changed) { model.revert(TagEditorModel.positionKeys(field)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                NagTextField(
                    value = state.number,
                    onValueChange = { model.setPosition(field, it, state.total) },
                    label = label,
                    showLabel = false,
                    enabled = editable,
                    placeholder = if (state.numberMixed || !editable) mixed else null,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.testTag("tag-number-${field.key}"),
                )
            }
            Text("/", style = NagTheme.type.body, color = NagTheme.colors.textTertiary)
            Box(Modifier.weight(1f)) {
                NagTextField(
                    value = state.total,
                    onValueChange = { model.setPosition(field, state.number, it) },
                    label = "$label · $total",
                    showLabel = false,
                    placeholder = if (state.totalMixed) mixed else null,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.testTag("tag-total-${field.key}"),
                )
            }
        }
    }
}

@Composable
private fun PositionLabel(label: String, changed: Boolean, onRevert: () -> Unit) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NagTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary, maxLines = 1)
        if (changed) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.nag_tag_changed), style = NagTheme.type.labelSmall, color = colors.accentText, maxLines = 1)
            Spacer(Modifier.weight(1f))
            NagIconButton(NagIcons.Resume, stringResource(R.string.nag_tag_revert, label), onRevert, size = 28.dp, iconSize = 14.dp)
        }
    }
}

// ------------------------------------------------------------------ all tags

/** "Tüm etiketler": every other tag of the files by its name (MusicBrainz ids, ReplayGain…), and a new one to add. */
@Composable
internal fun AllTags(model: TagEditorModel, separator: String, chips: @Composable (key: String) -> Unit) {
    val colors = NagTheme.colors
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("tag-all")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.nag_tag_section_all).uppercase(currentLocale()),
                style = NagTheme.type.overline,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            NagButton(stringResource(R.string.nag_tag_add_field), { adding = true }, icon = NagIcons.Plus, modifier = Modifier.testTag("tag-add-field"))
        }
        Text(stringResource(R.string.nag_tag_all_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        model.otherKeys.forEach { key -> chips(key) }
    }
    if (adding) {
        AddFieldDialog(
            onAdd = { key, value ->
                adding = false
                model.set(key, listOf(value))
            },
            onDismiss = { adding = false },
        )
    }
}

/** A new tag: its name (as TagLib names them: upper case, no spaces) and its value. */
@Composable
private fun AddFieldDialog(onAdd: (key: String, value: String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val normalized = normalizeTagKey(key)
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_tag_add_field_title), Modifier.testTag("tag-add-dialog")) {
                NagTextField(key, { key = it }, stringResource(R.string.nag_tag_add_field_name), imeAction = ImeAction.Next, modifier = Modifier.testTag("tag-add-key"))
                NagTextField(value, { value = it }, stringResource(R.string.nag_tag_add_field_value), onDone = {
                    if (normalized.isNotEmpty() && value.isNotEmpty()) onAdd(normalized, value)
                }, modifier = Modifier.testTag("tag-add-value"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_tag_cancel), onDismiss)
                    NagButton(
                        stringResource(R.string.nag_tag_add),
                        { onAdd(normalized, value) },
                        primary = true,
                        enabled = normalized.isNotEmpty() && value.isNotEmpty(),
                        modifier = Modifier.testTag("tag-add-confirm"),
                    )
                }
            }
        }
    }
}

/** A tag's name as TagLib's property map has them: upper case, spaces as underscores, nothing that is not a name. */
internal fun normalizeTagKey(key: String): String =
    key.trim().uppercase(java.util.Locale.ROOT).replace(' ', '_').filter { it.isLetterOrDigit() || it == '_' || it == ':' || it == '-' }

// ------------------------------------------------------------------ the foot

/**
 * The editor's foot: how many changes wait, and the two things to do with them; a batch's progress and its Stop while
 * it writes. Always in sight, however far the fields are scrolled.
 */
@Composable
internal fun Footer(model: TagEditorModel, onSave: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth().background(colors.surface).testTag("tag-footer")) {
        HorizontalRule()
        model.progress?.takeIf { it.isRunning }?.let { progress ->
            val words = stringResource(R.string.nag_tag_batch_progress, progress.done, progress.total)
            Row(Modifier.fillMaxWidth().padding(start = dimens.gapL, end = dimens.gapS, top = dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(words, style = NagTheme.type.labelSmall, color = colors.textSecondary)
                    NagProgressBar(progress.fraction, words, Modifier.padding(top = 4.dp))
                }
                NagButton(stringResource(R.string.nag_tag_stop), model::stop, modifier = Modifier.padding(start = 8.dp).testTag("tag-stop"))
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = dimens.gapL, vertical = dimens.gapS),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val count = model.changeCount
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (count > 0) colors.accent else colors.textTertiary.copy(alpha = 0.4f), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (count > 0) pluralStringResource(R.plurals.nag_tag_changes, count, count) else stringResource(R.string.nag_tag_no_changes),
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("tag-count"),
                )
            }
            NagButton(stringResource(R.string.nag_tag_cancel), model::revertAll, enabled = model.dirty && !model.saving, modifier = Modifier.testTag("tag-discard"))
            NagButton(
                stringResource(if (model.saving) R.string.nag_tag_saving else R.string.nag_tag_save),
                onSave,
                primary = true,
                enabled = model.dirty && !model.saving,
                modifier = Modifier.testTag("tag-save"),
            )
        }
    }
}

// ------------------------------------------------------------------ the page

/**
 * 1.2.0: the editor as a page over the window: a phone's (a track's menu, the selection's), a playlist's, another app's
 * file. Back (or the arrow) with changes not saved asks first.
 */
@Composable
fun TagEditorPage(targets: List<TagTarget>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val model = if (targets.isEmpty() || targets.size > TagEditorModel.MAX_FILES) null else rememberTagEditor(targets)
    var confirm by remember { mutableStateOf(false) }
    val close = { if (model?.dirty == true) confirm = true else onClose() }
    BackHandler(onBack = close)
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("tag-page"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.gapXs, vertical = dimens.gapXs), verticalAlignment = Alignment.CenterVertically) {
            NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_tag_close), close, modifier = Modifier.testTag("tag-page-close"))
            Text(
                stringResource(R.string.nag_tag_page_title),
                style = NagTheme.type.title,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = dimens.gapXs),
            )
        }
        if (model == null) {
            TagEditorPane(targets, Modifier.weight(1f), page = true)
        } else {
            TagEditor(model, Modifier.weight(1f), held = model.targets != targets, page = true)
        }
    }
    if (confirm) {
        NagDialog(onDismissRequest = { confirm = false }) {
            DialogFrame({ confirm = false }) {
                DialogCard(stringResource(R.string.nag_tag_discard_title), Modifier.testTag("tag-discard-dialog")) {
                    Text(stringResource(R.string.nag_tag_discard_body), style = NagTheme.type.body, color = colors.textSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        NagButton(stringResource(R.string.nag_tag_discard_keep), { confirm = false }, modifier = Modifier.testTag("tag-discard-keep"))
                        NagButton(stringResource(R.string.nag_tag_discard_drop), {
                            confirm = false
                            model?.revertAll()
                            onClose()
                        }, primary = true, modifier = Modifier.testTag("tag-discard-drop"))
                    }
                }
            }
        }
    }
}

/**
 * 1.2.0: "Etiketleri düzenle…" asked for from a menu: on a tablet's and a DeX window the music view takes [inspector]'s
 * tracks into its panel's "Düzenle"; elsewhere (a phone, a playlist) the editor's [page] opens over the window.
 */
object TagEditRequest {
    /** Tracks for the music view's panel, until it takes them. */
    var inspector: List<Track>? by mutableStateOf(null)
        private set

    /** The files of the page over the window, while it is open. */
    var page: List<TagTarget>? by mutableStateOf(null)
        private set

    fun toInspector(tracks: List<Track>) {
        inspector = tracks
    }

    internal fun taken() {
        inspector = null
    }

    /** The page for [tracks]; a word instead when none of them can be edited (CUE sheets' tracks). */
    fun openPage(context: Context, tracks: List<Track>) {
        val targets = tracks.mapNotNull(TagTarget::of)
        if (targets.isEmpty()) {
            Notices.show(context, context.getString(R.string.nag_tag_cue), long = true)
            return
        }
        page = targets
    }

    fun openPage(targets: List<TagTarget>) {
        page = targets.takeIf { it.isNotEmpty() }
    }

    fun closePage() {
        page = null
    }
}

// ------------------------------------------------------------------ settings and messages

/** 1.2.0: the tag editor's settings, in the app's settings (Settings › Etiket düzenleyici). */
object TagSettings {
    private const val KEY_MODE = "nagmedar_tags_multi_mode"
    private const val KEY_SEPARATOR = "nagmedar_tags_separator"

    fun style(context: Context): MultiValueStyle {
        val prefs = Settings.getInstance(context)
        val mode = runCatching { MultiValueMode.valueOf(prefs.getString(KEY_MODE, null) ?: MultiValueMode.Separate.name) }.getOrDefault(MultiValueMode.Separate)
        val separator = prefs.getString(KEY_SEPARATOR, null)?.takeIf { it.isNotBlank() } ?: MultiValueStyle.DEFAULT_SEPARATOR
        return MultiValueStyle(mode, separator)
    }

    fun update(context: Context, style: MultiValueStyle) {
        Settings.getInstance(context).edit()
            .putString(KEY_MODE, style.mode.name)
            .putString(KEY_SEPARATOR, style.separator.takeIf { it.isNotBlank() } ?: MultiValueStyle.DEFAULT_SEPARATOR)
            .apply()
    }
}

/** The system's page that gives the app access to all files: what writing tags needs. */
internal fun openAllFilesAccess(context: Context) {
    val intent = if (MediaAccess.canAskAllFiles) MediaAccess.allFilesSettings(context) else MediaAccess.appSettings(context)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** [message] as a notice: a save's with its "Geri al", a problem in the danger colour. */
internal fun showTagMessage(context: Context, message: TagMessage) {
    val res = context.resources
    val undo = res.getString(R.string.nag_tag_undo)
    when (message) {
        is TagMessage.Saved -> {
            val text = when {
                message.total == 1 && message.notStored.isNotEmpty() -> res.getString(R.string.nag_tag_saved_some, message.notStored.joinToString(", "))
                message.total == 1 -> res.getString(R.string.nag_tag_saved)
                message.failed > 0 -> res.getQuantityString(R.plurals.nag_tag_saved_failed, message.failed, message.saved, message.total, message.failed)
                else -> res.getQuantityString(R.plurals.nag_tag_saved_many, message.saved, message.saved)
            }
            Notices.show(context, text, long = message.failed > 0 || message.notStored.isNotEmpty(), error = message.failed > 0, actionLabel = message.undo?.let { undo }, action = message.undo)
        }
        TagMessage.NothingToWrite -> Notices.show(context, res.getString(R.string.nag_tag_nothing))
        TagMessage.NeedsAccess -> Notices.show(
            context, res.getString(R.string.nag_tag_needs_access), long = true, error = true,
            actionLabel = res.getString(R.string.nag_tag_grant), action = { openAllFilesAccess(context) },
        )
        TagMessage.FileGone -> Notices.show(context, res.getString(R.string.nag_tag_not_found), long = true, error = true)
        TagMessage.Unsupported -> Notices.show(context, res.getString(R.string.nag_tag_format), long = true, error = true)
        is TagMessage.Failed -> Notices.show(
            context,
            res.getString(if (message.restored) R.string.nag_tag_save_failed_restored else R.string.nag_tag_save_failed, message.message),
            long = true,
            error = true,
        )
        is TagMessage.Undone -> Notices.show(
            context,
            when {
                message.total == 1 && message.restored == 1 -> res.getString(R.string.nag_tag_undone)
                message.restored == message.total -> res.getQuantityString(R.plurals.nag_tag_undone_many, message.restored, message.restored)
                else -> res.getString(R.string.nag_tag_undone_partly, message.restored, message.total)
            },
            error = message.restored < message.total,
        )
        TagMessage.UndoGone -> Notices.show(context, res.getString(R.string.nag_tag_undo_gone), error = true)
        TagMessage.UndoFailed -> Notices.show(context, res.getString(R.string.nag_tag_undo_failed), error = true)
        TagMessage.Busy -> Notices.show(context, res.getString(R.string.nag_tag_busy))
        TagMessage.CoverUnreadable -> Notices.show(context, res.getString(R.string.nag_tag_cover_unreadable), error = true)
        is TagMessage.Stopped -> Notices.show(
            context, res.getString(R.string.nag_tag_stopped, message.saved, message.total), long = true,
            actionLabel = message.undo?.let { undo }, action = message.undo,
        )
    }
}
