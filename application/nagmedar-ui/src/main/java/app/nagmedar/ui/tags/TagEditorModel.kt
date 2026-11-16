/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: after Metamatik (feature/editor/src/main/kotlin/com/bobbyesp/metadator/feature/editor/EditorViewModel.kt and feature/batch/src/main/kotlin/com/bobbyesp/metadator/feature/batch/BatchViewModel.kt),
 * a fork of Metadator, under the AGPL-3.0; written again on Nağmedar's components.
 */
package app.nagmedar.ui.tags

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.tags.ArtworkChange
import app.nagmedar.tags.AudioProperties
import app.nagmedar.tags.BackupId
import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.PictureType
import app.nagmedar.tags.TagField
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagReadResult
import app.nagmedar.tags.batch.BatchEdit
import app.nagmedar.tags.batch.BatchItem
import app.nagmedar.tags.batch.BatchProgress
import app.nagmedar.tags.batch.FilePositions
import app.nagmedar.tags.batch.PositionEdit
import app.nagmedar.tags.editor.MultiValueMode
import app.nagmedar.tags.editor.Position
import app.nagmedar.tags.editor.PositionStyle
import app.nagmedar.tags.editor.TagDraft
import app.nagmedar.tags.editor.TrackPositions
import app.nagmedar.tags.editor.withMultiValueMode
import app.nagmedar.tags.save.RestoreOutcome
import app.nagmedar.tags.save.SaveOutcome
import app.nagmedar.tags.text.textRepairs
import app.nagmedar.ui.music.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 1.2.0: a file for the tag editor: where it is ([ref], a library track's `file://` address or another app's
 * `content://` one), its file's [name] (the format's way of keeping a track's total goes by it), and what the library
 * calls it ([title], [subtitle]) until its tags are read.
 */
@Immutable
data class TagTarget(val ref: ContentRef, val name: String, val title: String? = null, val subtitle: String? = null) {
    val extension: String? get() = name.substringAfterLast('.', "").lowercase().ifEmpty { null }

    companion object {
        /** [track]'s file; null for a CUE sheet's track, whose tags are its sheet's, and for what is not a file. */
        fun of(track: Track): TagTarget? = of(track.uri, track.title, listOfNotNull(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "))

        fun of(uri: String, title: String? = null, subtitle: String? = null): TagTarget? {
            val parsed = Uri.parse(uri)
            if (CueUri.isTrack(parsed)) return null
            if (parsed.scheme != null && parsed.scheme != "file" && parsed.scheme != "content") return null
            val name = parsed.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: title ?: uri
            return TagTarget(ContentRef(uri), name, title?.takeIf { it.isNotBlank() }, subtitle?.takeIf { it.isNotBlank() })
        }
    }
}

/** 1.2.0: how fields of several values are written (Settings › Etiket düzenleyici), Metamatik's choice. */
@Immutable
data class MultiValueStyle(val mode: MultiValueMode = MultiValueMode.Separate, val separator: String = DEFAULT_SEPARATOR) {
    companion object {
        const val DEFAULT_SEPARATOR = "; "
    }
}

/** What the editor shows: the tags, or why there are none. */
sealed interface EditorPhase {
    data object Loading : EditorPhase

    data object Ready : EditorPhase

    data class Problem(val problem: TagProblem, val message: String? = null) : EditorPhase
}

enum class TagProblem { NotFound, NeedsAccess, Unsupported, Failed }

/** What the selected files have in a field: one value for all, different ones, or none. */
sealed interface CommonValue {
    data class Same(val values: List<String>) : CommonValue

    data object Mixed : CommonValue

    data object Empty : CommonValue
}

/** A field as the editor shows it: its [values], whether they differ between the files ([mixed]), whether it was changed. */
@Immutable
data class FieldState(val values: List<String>, val mixed: Boolean = false, val changed: Boolean = false) {
    val text: String get() = values.firstOrNull().orEmpty()

    companion object {
        val Empty = FieldState(emptyList())
    }
}

/** A track or disc position as the editor shows it: its number and total, and which of them differ between the files. */
@Immutable
data class PositionState(
    val number: String,
    val total: String,
    val numberMixed: Boolean = false,
    val totalMixed: Boolean = false,
    val changed: Boolean = false,
)

/** The cover as it will be once saved: [picture] (null: none), whether it was changed, whether the files keep their own. */
@Immutable
data class CoverState(val picture: EmbeddedPicture?, val changed: Boolean, val own: Boolean = false, val hadOne: Boolean = false)

/** What the editor has to say: a save's or an undo's end, a picture that did not read. */
sealed interface TagMessage {
    /** [saved] of [total] files written; [notStored] keys the format could not keep; [undo] puts them back. */
    data class Saved(val saved: Int, val total: Int, val notStored: Set<String> = emptySet(), val failed: Int = 0, val undo: (() -> Unit)? = null) : TagMessage

    data object NothingToWrite : TagMessage

    data object NeedsAccess : TagMessage

    data object FileGone : TagMessage

    data object Unsupported : TagMessage

    data class Failed(val message: String, val restored: Boolean) : TagMessage

    data class Undone(val restored: Int, val total: Int) : TagMessage

    data object UndoGone : TagMessage

    data object UndoFailed : TagMessage

    data object Busy : TagMessage

    data object CoverUnreadable : TagMessage

    data class Stopped(val saved: Int, val total: Int, val undo: (() -> Unit)?) : TagMessage
}

/** One of several files, its tags as read (without its pictures). */
@Immutable
data class LoadedFile(val target: TagTarget, val tags: TagMap)

/**
 * 1.2.0, Metamatik's editor in Nağmedar: the tags of one file ([TagDraft]: what was read and what was made of it, only
 * the difference written) or of several ([CommonValue]s; a field set is set on each, the rest of each file kept as it
 * is, through [app.nagmedar.tags.batch.BatchRunner]). Every save is backed up first and undone from its message
 * ([say]). Its work goes on in [TagFiles.scope] (the app's): a save carries on when the editor's panel goes.
 */
@Stable
class TagEditorModel(
    val targets: List<TagTarget>,
    internal val files: TagFiles,
    private val style: () -> MultiValueStyle = { MultiValueStyle() },
) {
    /** What the editor says ([TagMessage]); the screen's notices. */
    var say: (TagMessage) -> Unit = {}

    /** Whether several files are edited together. */
    val many: Boolean get() = targets.size > 1

    private val scope: CoroutineScope get() = files.scope

    var phase: EditorPhase by mutableStateOf(EditorPhase.Loading)
        private set

    // one file

    var draft: TagDraft? by mutableStateOf(null)
        private set

    var audio: AudioProperties? by mutableStateOf(null)
        private set

    var positionStyle: PositionStyle by mutableStateOf(PositionStyle.Combined)
        private set

    // several files

    var loaded: List<LoadedFile> by mutableStateOf(emptyList())
        private set

    /** How many of [targets] could not be read, and are left out. */
    var skipped: Int by mutableStateOf(0)
        private set

    private var common: Map<String, CommonValue> by mutableStateOf(emptyMap())

    /** The fields set for every file: untouched ones keep each file's own. */
    var edits: Map<String, List<String>> by mutableStateOf(emptyMap())
        private set

    /** The positions set for every file (a disc, a track total): the parts left null keep each file's own. */
    var positionEdits: Map<TagField, PositionEdit> by mutableStateOf(emptyMap())
        private set

    private var coverChange: ArtworkChange by mutableStateOf(ArtworkChange.Unchanged)

    /** The batch writing the files, until it ends. */
    var progress: BatchProgress? by mutableStateOf(null)
        private set

    // both

    var saving: Boolean by mutableStateOf(false)
        private set

    var loadingCover: Boolean by mutableStateOf(false)
        private set

    private var loadJob: Job? = null

    val ready: Boolean get() = phase == EditorPhase.Ready

    val dirty: Boolean
        get() = if (many) edits.isNotEmpty() || positionEdits.isNotEmpty() || coverChange != ArtworkChange.Unchanged else draft?.isDirty == true

    /** How many changes are waiting: fields, positions, the cover. */
    val changeCount: Int
        get() = if (many) {
            edits.size + positionEdits.size + if (coverChange != ArtworkChange.Unchanged) 1 else 0
        } else {
            val d = draft
            if (d == null) 0 else d.changedKeys.size + if (d.isCoverChanged) 1 else 0
        }

    /** Reads the files' tags (again). */
    fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            phase = EditorPhase.Loading
            if (many) loadMany() else loadOne()
        }
    }

    private suspend fun loadOne() {
        val target = targets.single()
        when (val read = files.reader.read(target.ref, includePictures = true)) {
            is TagReadResult.Success -> {
                draft = TagDraft(read.snapshot)
                positionStyle = TrackPositions.detectStyle(read.snapshot.tags, target.extension)
                phase = EditorPhase.Ready
                audio = runCatching { files.reader.readAudioProperties(target.ref) }.getOrNull()
            }
            TagReadResult.NotFound -> phase = EditorPhase.Problem(TagProblem.NotFound)
            TagReadResult.AccessDenied -> phase = EditorPhase.Problem(TagProblem.NeedsAccess)
            TagReadResult.Unsupported -> phase = EditorPhase.Problem(TagProblem.Unsupported)
            is TagReadResult.Failed -> phase = EditorPhase.Problem(TagProblem.Failed, read.message)
        }
    }

    private suspend fun loadMany() {
        val gate = Semaphore(PARALLEL_READS)
        val reads = coroutineScope {
            targets.map { target -> async { gate.withPermit { target to files.reader.read(target.ref, includePictures = false) } } }.awaitAll()
        }
        val read = reads.mapNotNull { (target, result) -> (result as? TagReadResult.Success)?.let { LoadedFile(target, it.snapshot.tags) } }
        if (read.isEmpty()) {
            phase = EditorPhase.Problem(problemOf(reads.firstOrNull()?.second), (reads.firstOrNull()?.second as? TagReadResult.Failed)?.message)
            return
        }
        loaded = read
        skipped = targets.size - read.size
        common = commonValues(read.map { it.tags })
        phase = EditorPhase.Ready
    }

    private fun problemOf(result: TagReadResult?): TagProblem = when (result) {
        TagReadResult.NotFound, null -> TagProblem.NotFound
        TagReadResult.AccessDenied -> TagProblem.NeedsAccess
        TagReadResult.Unsupported -> TagProblem.Unsupported
        else -> TagProblem.Failed
    }

    // ------------------------------------------------------------------ fields

    /** [key] as the editor shows it. */
    fun field(key: String): FieldState {
        if (!many) {
            val d = draft ?: return FieldState.Empty
            return FieldState(d.tags[key], changed = d.isChanged(key))
        }
        edits[key]?.let { return FieldState(it, changed = true) }
        return when (val value = common[TagMap.normalizeKey(key)]) {
            is CommonValue.Same -> FieldState(value.values)
            CommonValue.Mixed -> FieldState(emptyList(), mixed = true)
            else -> FieldState.Empty
        }
    }

    /**
     * [key] set to [values]. Several files: set for each; set back to what they all have, it is no change; emptied where
     * they differ, each keeps its own (a field cleared where they share a value is cleared in each).
     */
    fun set(key: String, values: List<String>) {
        if (!many) {
            val d = draft ?: return
            draft = d.set(key, values)
            return
        }
        val normalized = TagMap.normalizeKey(key)
        val cleaned = values.filter { it.isNotEmpty() }
        val now = common[normalized]
        edits = when {
            now is CommonValue.Same && now.values == cleaned -> edits - normalized
            (now == null || now == CommonValue.Empty) && cleaned.isEmpty() -> edits - normalized
            now == CommonValue.Mixed && cleaned.isEmpty() -> edits - normalized
            else -> edits + (normalized to cleaned)
        }
    }

    /** [keys] back as they were read. */
    fun revert(keys: List<String>) {
        if (!many) {
            val d = draft ?: return
            draft = keys.fold(d) { draft, key -> draft.revert(key) }
            return
        }
        val normalized = keys.map(TagMap::normalizeKey).toSet()
        edits = edits - normalized
        val reverted = positionEdits.keys.filter { field -> positionKeys(field).any { it in normalized } } +
            positionTyped.keys.filter { field -> positionKeys(field).any { it in normalized } }
        positionEdits = positionEdits - reverted.toSet()
        positionTyped = positionTyped - reverted.toSet()
    }

    /** Every change taken back. */
    fun revertAll() {
        if (!many) {
            draft = draft?.revertAll()
            return
        }
        edits = emptyMap()
        positionEdits = emptyMap()
        positionTyped = emptyMap()
        coverChange = ArtworkChange.Unchanged
    }

    /** The keys with a place of their own in a section: every other key of the files is under "Tüm etiketler". */
    val otherKeys: List<String>
        get() {
            val keys = if (many) common.keys + edits.keys else draft?.let { it.tags.keys + it.original.tags.keys }.orEmpty()
            return keys.filter { it !in KnownKeys }.distinct().sorted()
        }

    // ------------------------------------------------------------------ positions

    fun position(field: TagField): PositionState {
        if (!many) {
            val d = draft ?: return PositionState("", "")
            val position = TrackPositions.read(d.tags, field)
            return PositionState(position.number, position.total, changed = positionKeys(field).any(d::isChanged))
        }
        val (numbers, totals) = commonPosition(field)
        // what was typed stays as it was typed, an empty cell too (each file keeps its own there)
        val typed = positionTyped[field]
        return PositionState(
            number = typed?.first ?: numbers.singleOrNull().orEmpty(),
            total = typed?.second ?: totals.singleOrNull().orEmpty(),
            numberMixed = typed?.first.isNullOrEmpty() && numbers.size > 1,
            totalMixed = typed?.second.isNullOrEmpty() && totals.size > 1,
            changed = field in positionEdits,
        )
    }

    /** The files' numbers and totals in [field], each once. */
    private fun commonPosition(field: TagField): Pair<List<String>, List<String>> {
        val positions = loaded.map { TrackPositions.read(it.tags, field) }
        return positions.map { it.number }.distinct() to positions.map { it.total }.distinct()
    }

    /** The positions typed for every file, as typed: what the cells show. */
    private var positionTyped: Map<TagField, Pair<String, String>> by mutableStateOf(emptyMap())

    /** Whether the files' [field] numbers can be set: each file keeps its own track; one disc for all. */
    fun numberEditable(field: TagField): Boolean = !many || field != TagField.TrackNumber

    /**
     * [field]'s [number] and [total] as typed. Several files: a number set for each where it is one and not what they
     * all have already; an empty cell, or a track's number, keeps each file's own.
     */
    fun setPosition(field: TagField, number: String, total: String) {
        if (!many) {
            val d = draft ?: return
            draft = d.setAll(TrackPositions.write(d.tags, field, Position(number, total), positionStyle))
            return
        }
        val (numbers, totals) = commonPosition(field)
        positionTyped = positionTyped + (field to (number to total))
        val edit = PositionEdit(
            number = number.trim().toIntOrNull()?.takeIf { numberEditable(field) && it > 0 && numbers.singleOrNull() != it.toString() },
            total = total.trim().toIntOrNull()?.takeIf { it > 0 && totals.singleOrNull() != it.toString() },
        )
        positionEdits = if (edit.isEmpty) positionEdits - field else positionEdits + (field to edit)
    }

    // ------------------------------------------------------------------ the cover

    val cover: CoverState
        get() {
            if (!many) {
                val d = draft ?: return CoverState(null, false)
                return CoverState(d.cover, d.isCoverChanged, hadOne = d.original.frontCover != null)
            }
            return when (val change = coverChange) {
                ArtworkChange.Unchanged -> CoverState(null, changed = false, own = true)
                ArtworkChange.Remove -> CoverState(null, changed = true)
                is ArtworkChange.Replace -> CoverState(change.picture, changed = true)
            }
        }

    /** The picture [read] gives as the cover; said when it gives none. */
    fun chooseCover(read: suspend () -> Pair<ByteArray, String>?) {
        scope.launch {
            loadingCover = true
            val picked = runCatching { read() }.getOrNull()
            loadingCover = false
            if (picked == null) {
                say(TagMessage.CoverUnreadable)
                return@launch
            }
            val (bytes, mime) = picked
            setCover(EmbeddedPicture(bytes, mime, type = PictureType.FrontCover))
        }
    }

    fun setCover(picture: EmbeddedPicture) {
        if (many) coverChange = ArtworkChange.Replace(picture) else draft = draft?.replaceCover(picture)
    }

    fun removeCover() {
        if (many) coverChange = ArtworkChange.Remove else draft = draft?.removeCover()
    }

    fun revertCover() {
        if (many) coverChange = ArtworkChange.Unchanged else draft = draft?.revertCover()
    }

    // ------------------------------------------------------------------ garbled text

    /** The fields whose text looks garbled in a way it can be put right from, as they would be: one file's. */
    val repairs: Map<String, List<String>>
        get() = if (many) emptyMap() else draft?.tags?.textRepairs().orEmpty()

    fun repairText() {
        val d = draft ?: return
        draft = d.setAll(d.tags.textRepairs())
    }

    // ------------------------------------------------------------------ saving

    fun save() {
        if (!ready || saving || !dirty) return
        if (many) saveMany() else saveOne()
    }

    private fun saveOne() {
        val target = targets.single()
        val d = draft ?: return
        val now = style()
        saving = true
        scope.launch {
            val outcome = try {
                files.save(target.ref, d.changes.withMultiValueMode(now.mode, now.separator))
            } catch (e: Exception) {
                saving = false
                say(TagMessage.Failed(e.message ?: e.javaClass.simpleName, restored = true))
                return@launch
            }
            when (outcome) {
                is SaveOutcome.Saved -> {
                    reloadAfterWrite(since = d)
                    if (d.isCoverChanged) files.covers(listOf(target.ref), listOf(d.original.tags), listOf(draft?.original?.tags ?: d.tags))
                    say(TagMessage.Saved(1, 1, outcome.notStored, undo = { undo(outcome.backupId) }))
                }
                SaveOutcome.NothingToSave -> {
                    reloadAfterWrite(since = d)
                    say(TagMessage.NothingToWrite)
                }
                SaveOutcome.NeedsAccess -> {
                    saving = false
                    say(TagMessage.NeedsAccess)
                }
                SaveOutcome.FileGone -> {
                    saving = false
                    phase = EditorPhase.Problem(TagProblem.NotFound)
                    say(TagMessage.FileGone)
                }
                SaveOutcome.Unsupported -> {
                    saving = false
                    say(TagMessage.Unsupported)
                }
                is SaveOutcome.Failed -> {
                    saving = false
                    say(TagMessage.Failed(outcome.message, outcome.restored))
                }
            }
        }
    }

    /**
     * After a write the editor shows what the file holds now, with what was typed since [since] (the fields stay
     * editable while a slow save writes) made on it again.
     */
    private suspend fun reloadAfterWrite(since: TagDraft?) {
        val target = targets.single()
        val read = files.reader.read(target.ref, includePictures = true)
        if (read is TagReadResult.Success) {
            val typed = since?.let { draft?.changesSince(it) }
            val fresh = TagDraft(read.snapshot)
            draft = typed?.let { fresh.applying(it) } ?: fresh
        }
        saving = false
    }

    private fun undo(id: BackupId) {
        scope.launch {
            val message = when (files.restore(id)) {
                RestoreOutcome.Restored -> {
                    if (!many) reloadAfterWrite(since = null) else loadMany()
                    TagMessage.Undone(1, 1)
                }
                RestoreOutcome.BackupGone -> TagMessage.UndoGone
                RestoreOutcome.NeedsAccess -> TagMessage.NeedsAccess
                RestoreOutcome.FileGone -> TagMessage.FileGone
                is RestoreOutcome.Failed -> TagMessage.UndoFailed
            }
            say(message)
        }
    }

    private fun saveMany() {
        val runner = files.batch
        if (runner.isRunning) {
            say(TagMessage.Busy)
            return
        }
        runner.clear()
        val items = loaded.map { BatchItem(it.target.ref, it.target.name) }
        val positions = positionEdits
        val edit = BatchEdit(
            fields = edits,
            artwork = coverChange,
            positions = if (positions.isEmpty()) emptyMap() else items.associate { item ->
                item.ref to FilePositions(track = positions[TagField.TrackNumber], disc = positions[TagField.DiscNumber])
            },
        )
        val before = loaded.associate { it.target.ref to it.tags }
        val coverWritten = coverChange != ArtworkChange.Unchanged
        val now = style()
        saving = true
        runner.start(items, edit) { it.withMultiValueMode(now.mode, now.separator) }
        scope.launch {
            val done = runner.progress.onEach { progress = it }.first { it == null || !it.isRunning }
            saving = false
            progress = null
            if (done == null) return@launch
            val backups = done.backups
            val undo: (() -> Unit)? = if (backups.isEmpty()) null else ({ undoAll(backups) })
            loadMany()
            edits = emptyMap()
            positionEdits = emptyMap()
            positionTyped = emptyMap()
            coverChange = ArtworkChange.Unchanged
            if (coverWritten) {
                val written = done.saved.map { it.item.ref }
                files.covers(written, written.map { before[it] ?: TagMap.Empty }, written.map { ref -> loaded.firstOrNull { it.target.ref == ref }?.tags ?: TagMap.Empty })
            }
            say(
                if (done.wasCancelled) {
                    TagMessage.Stopped(done.saved.size, done.total, undo)
                } else {
                    TagMessage.Saved(done.saved.size, done.total, done.notStored.keys, failed = done.failed.size, undo = undo)
                },
            )
        }
    }

    /** The batch stopped where it is: what it wrote stays written, and can be undone. */
    fun stop() {
        if (many) files.batch.cancel()
    }

    private fun undoAll(backups: List<BackupId>) {
        scope.launch {
            val restored = backups.count { runCatching { files.restore(it) }.getOrNull() == RestoreOutcome.Restored }
            // the batch's backups are no longer held from the saves' pruning
            if (!files.batch.isRunning) files.batch.clear()
            loadMany()
            say(TagMessage.Undone(restored, backups.size))
        }
    }

    companion object {
        /** At most this many files are edited together. */
        const val MAX_FILES = 1000

        private const val PARALLEL_READS = 4

        /** Keys with a place of their own; everything else is under "Tüm etiketler". */
        val KnownKeys: Set<String> = TagField.entries.map { it.key }.toSet() + TagField.TrackTotalKeys + TagField.DiscTotalKeys

        internal fun positionKeys(field: TagField): List<String> =
            listOf(field.key) + if (field == TagField.TrackNumber) TagField.TrackTotalKeys else TagField.DiscTotalKeys

        /** What [files] have in each of their keys. */
        internal fun commonValues(files: List<TagMap>): Map<String, CommonValue> {
            val keys = files.flatMapTo(LinkedHashSet()) { it.keys } + TagField.entries.map { it.key }
            return keys.associateWith { key ->
                val values = files.map { it[key] }.distinct()
                when {
                    values.size == 1 && values.single().isEmpty() -> CommonValue.Empty
                    values.size == 1 -> CommonValue.Same(values.single())
                    else -> CommonValue.Mixed
                }
            }
        }
    }
}
