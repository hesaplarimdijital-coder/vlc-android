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
import app.nagmedar.tags.TagSnapshot
import app.nagmedar.tags.applying
import app.nagmedar.tags.batch.AlbumMerge
import app.nagmedar.tags.batch.BatchEdit
import app.nagmedar.tags.batch.BatchItem
import app.nagmedar.tags.batch.BatchProgress
import app.nagmedar.tags.batch.FileNamePattern
import app.nagmedar.tags.batch.FilePositions
import app.nagmedar.tags.batch.PositionEdit
import app.nagmedar.tags.batch.TrackNumbering
import app.nagmedar.tags.batch.changesFor
import app.nagmedar.tags.cover.CoverRequest
import app.nagmedar.tags.editor.MultiValueMode
import app.nagmedar.tags.editor.Position
import app.nagmedar.tags.editor.PositionStyle
import app.nagmedar.tags.editor.TagDraft
import app.nagmedar.tags.editor.TrackPositions
import app.nagmedar.tags.editor.withMultiValueMode
import app.nagmedar.tags.lookup.AlbumProposal
import app.nagmedar.tags.lookup.AlbumQuery
import app.nagmedar.tags.lookup.AlbumSong
import app.nagmedar.tags.lookup.FieldProposal
import app.nagmedar.tags.lookup.LookupCandidate
import app.nagmedar.tags.lookup.LookupQuery
import app.nagmedar.tags.lookup.positionsFor
import app.nagmedar.tags.lookup.proposalsFor
import app.nagmedar.tags.quality.LosslessCheckResult
import app.nagmedar.tags.quality.LosslessReport
import app.nagmedar.tags.save.RestoreOutcome
import app.nagmedar.tags.save.SaveOutcome
import app.nagmedar.tags.text.textRepairs
import app.nagmedar.ui.music.Track
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
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
data class TagTarget(
    val ref: ContentRef,
    val name: String,
    val title: String? = null,
    val subtitle: String? = null,
    /** 1.3.0: how long the library says it is, for finding its album; null where it does not know. */
    val durationMs: Long? = null,
) {
    val extension: String? get() = name.substringAfterLast('.', "").lowercase().ifEmpty { null }

    companion object {
        /** [track]'s file; null for a CUE sheet's track, whose tags are its sheet's, and for what is not a file. */
        fun of(track: Track): TagTarget? =
            of(track.uri, track.title, listOfNotNull(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "), track.durationMs.takeIf { it > 0 })

        fun of(uri: String, title: String? = null, subtitle: String? = null, durationMs: Long? = null): TagTarget? {
            val parsed = Uri.parse(uri)
            if (CueUri.isTrack(parsed)) return null
            if (parsed.scheme != null && parsed.scheme != "file" && parsed.scheme != "content") return null
            val name = parsed.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: title ?: uri
            return TagTarget(ContentRef(uri), name, title?.takeIf { it.isNotBlank() }, subtitle?.takeIf { it.isNotBlank() }, durationMs)
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

/** 1.4.0: the lossless check of the file the editor has open (Metamatik's card): none for a file it does not read. */
sealed interface LosslessCheckState {
    data object None : LosslessCheckState

    data object Checking : LosslessCheckState

    data object Failed : LosslessCheckState

    data class Done(val report: LosslessReport) : LosslessCheckState
}

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

    /** 1.4.0: whether one FLAC or WAV file's audio is what it says, as far as the check has got. */
    var lossless: LosslessCheckState by mutableStateOf(LosslessCheckState.None)
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

    /** 1.3.0: the tracks numbered in the order they are listed, from [TrackNumbering.startAt]. */
    var numbering: TrackNumbering? by mutableStateOf(null)
        private set

    /** 1.3.0: the pattern the files' names are read by, and whether they are. */
    var pattern: String by mutableStateOf(FileNamePattern.Suggestions.first())
        private set

    var usePattern: Boolean by mutableStateOf(false)
        private set

    /** 1.3.0: whether each file's garbled text is put right, each its own way. */
    var repairEach: Boolean by mutableStateOf(false)
        private set

    /** 1.3.0: each file's own track and disc: found on a release, or the disc a merged album's name said. */
    var filePositions: Map<ContentRef, FilePositions> by mutableStateOf(emptyMap())
        private set

    /** 1.3.0: the albums the files are on, when they are on more than one. */
    var merge: AlbumMerge? by mutableStateOf(null)
        private set

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
        get() = if (many) changeCount > 0 else draft?.isDirty == true

    /** How many changes are waiting: fields, positions, the cover. */
    val changeCount: Int
        get() = if (many) {
            edits.size + positionEdits.size + (if (coverChange != ArtworkChange.Unchanged) 1 else 0) +
                (if (numbering != null) 1 else 0) + (if (patternInUse != null) 1 else 0) + (if (repairEach && garbledFiles > 0) 1 else 0) +
                (if (filePositions.values.any { !it.isEmpty }) 1 else 0)
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
                checkLossless(target)
            }
            TagReadResult.NotFound -> phase = EditorPhase.Problem(TagProblem.NotFound)
            TagReadResult.AccessDenied -> phase = EditorPhase.Problem(TagProblem.NeedsAccess)
            TagReadResult.Unsupported -> phase = EditorPhase.Problem(TagProblem.Unsupported)
            is TagReadResult.Failed -> phase = EditorPhase.Problem(TagProblem.Failed, read.message)
        }
    }

    /**
     * 1.4.0 (Metamatik's): a FLAC or WAV file's audio looked at, after its tags are on screen: some twenty seconds of it,
     * decoded here. The verdict goes to the library too ([TagFiles.verdicts]).
     */
    private suspend fun checkLossless(target: TagTarget) {
        val check = files.lossless ?: return
        if (target.extension !in LOSSLESS_EXTENSIONS) return
        lossless = LosslessCheckState.Checking
        val result = try {
            check(target.ref)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LosslessCheckResult.Unreadable
        }
        lossless = when (result) {
            is LosslessCheckResult.Checked -> LosslessCheckState.Done(result.report)
            LosslessCheckResult.NotApplicable -> LosslessCheckState.None
            LosslessCheckResult.Unreadable -> LosslessCheckState.Failed
        }
        when (result) {
            is LosslessCheckResult.Checked -> files.verdicts(target.ref, result.report.verdict)
            LosslessCheckResult.Unreadable -> files.verdicts(target.ref, null)
            LosslessCheckResult.NotApplicable -> Unit
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
        merge = AlbumMerge.of(read.map { it.tags })
        val repairs = read.map { it to it.tags.textRepairs() }
        garbledFiles = repairs.count { it.second.isNotEmpty() }
        garbledExamples = repairs.asSequence().flatMap { (file, fixes) ->
            fixes.map { (key, values) -> Triple(key, file.tags[key].joinToString(", "), values.joinToString(", ")) }.asSequence()
        }.distinct().take(3).toList()
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
        clearTools()
    }

    private fun clearTools() {
        numbering = null
        usePattern = false
        repairEach = false
        filePositions = emptyMap()
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
        val edit = batchEdit()
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
            clearTools()
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

    // ------------------------------------------------------------------ 1.3.0: the batch's tools

    /** What the batch writes: the fields set for all, the cover, the numbers, the names read, each file's own. */
    internal fun batchEdit(): BatchEdit {
        val forAll = positionEdits
        val positions = if (forAll.isEmpty()) filePositions else loaded.associate { file ->
            val own = filePositions[file.target.ref] ?: FilePositions()
            file.target.ref to (own + FilePositions(track = forAll[TagField.TrackNumber], disc = forAll[TagField.DiscNumber]))
        }
        return BatchEdit(
            fields = edits,
            artwork = coverChange,
            numbering = numbering,
            fileNamePattern = patternInUse,
            positions = positions,
            repairText = repairEach && garbledFiles > 0,
        )
    }

    /** The tracks numbered in their order (from [startAt], with their total), or not. */
    fun number(numbering: TrackNumbering?) {
        this.numbering = numbering
    }

    fun choosePattern(pattern: String) {
        this.pattern = pattern
    }

    fun readNames(on: Boolean) {
        usePattern = on
    }

    /** The pattern the names are read by, when it is used and is one. */
    val patternInUse: FileNamePattern? get() = FileNamePattern(pattern).takeIf { usePattern && it.isValid }

    /** How the first files' names read with the pattern: a name and its tags, or null where it does not fit. */
    val patternPreview: List<Pair<String, Map<String, List<String>>?>>
        get() {
            val parsed = FileNamePattern(pattern).takeIf { it.isValid } ?: return emptyList()
            return loaded.take(3).map { it.target.name to parsed.parse(it.target.name) }
        }

    fun fixGarbledEach(on: Boolean) {
        repairEach = on
    }

    /** How many files have text garbled in a way it can be put right from (read with them). */
    var garbledFiles: Int by mutableStateOf(0)
        private set

    /** A few of the fields that would be put right, as they read and as they would. */
    var garbledExamples: List<Triple<String, String, String>> by mutableStateOf(emptyList())
        private set

    /** The files as they will be once the batch is written: what a cover or an album is looked for by. */
    fun editedTags(): List<TagMap> {
        val edit = batchEdit()
        return loaded.mapIndexed { index, file ->
            val changes = edit.changesFor(BatchItem(file.target.ref, file.target.name), index, loaded.size, TagSnapshot(file.tags, emptyList()))
            file.tags.applying(changes.fields)
        }
    }

    /** The songs as the album search sees them, as they will be. */
    private fun albumSongs(edited: List<TagMap>): List<AlbumSong> = loaded.zip(edited) { file, tags ->
        AlbumSong(
            title = tags.first(TagField.Title.key)?.ifBlank { null } ?: file.target.title ?: file.target.name,
            durationMs = file.target.durationMs,
            trackNumber = TrackPositions.read(tags, TagField.TrackNumber).number.toIntOrNull(),
            discNumber = TrackPositions.read(tags, TagField.DiscNumber).number.toIntOrNull(),
        )
    }

    /** What the album search asks: the album most of the files will be on, its artist, the songs. */
    fun albumQuery(): AlbumQuery {
        val edited = editedTags()
        val artists = edited.mostCommon(TagField.AlbumArtist.key) ?: edited.mostCommon(TagField.Artist.key)
        return AlbumQuery(album = edited.mostCommon(TagField.Album.key).orEmpty(), artist = artists.orEmpty(), songs = albumSongs(edited))
    }

    /**
     * The fields [proposal] knows set for every file, and the songs found on it numbered: to be checked, and written with
     * the rest. Its title is not taken: the songs keep theirs.
     */
    fun useAlbum(proposal: AlbumProposal) {
        val found = proposal.positionsFor(albumSongs(editedTags()))
        set(TagField.Album.key, listOf(proposal.album))
        if (proposal.albumArtists.isNotEmpty()) set(TagField.AlbumArtist.key, proposal.albumArtists)
        proposal.date?.let { set(TagField.Date.key, listOf(it)) }
        if (proposal.genres.isNotEmpty()) set(TagField.Genre.key, proposal.genres)
        val numbers = loaded.zip(found).mapNotNull { (file, position) ->
            position?.let {
                file.target.ref to FilePositions(
                    track = PositionEdit(it.track, it.trackTotal).takeUnless { edit -> edit.isEmpty },
                    disc = PositionEdit(it.disc, it.discTotal).takeUnless { edit -> edit.isEmpty },
                )
            }
        }.filterNot { (_, positions) -> positions.isEmpty }.toMap()
        filePositions = filePositions + numbers.mapValues { (ref, positions) -> (filePositions[ref] ?: FilePositions()) + positions }
    }

    /** The files made one album, called [album], by [albumArtists]; with [discNumbers], each its album's name's disc. */
    fun mergeAlbums(album: String, albumArtists: List<String>, discNumbers: Boolean) {
        val title = album.trim()
        val artists = albumArtists.map { it.trim() }.filter { it.isNotEmpty() }
        if (title.isEmpty() || artists.isEmpty()) return
        set(TagField.Album.key, listOf(title))
        set(TagField.AlbumArtist.key, artists)
        if (discNumbers) {
            val discs = merge?.discs.orEmpty().mapNotNull { (index, disc) ->
                loaded.getOrNull(index)?.let { it.target.ref to FilePositions(disc = PositionEdit(number = disc)) }
            }.toMap()
            filePositions = filePositions + discs.mapValues { (ref, positions) -> (filePositions[ref] ?: FilePositions()) + positions }
        }
    }

    /** Each file's numbers back as they are. */
    fun resetFilePositions() {
        filePositions = emptyMap()
    }

    // ------------------------------------------------------------------ 1.3.0: looking up

    /** What one file is looked up by: its title, artist and album as edited, how long it is. */
    fun lookupQuery(): LookupQuery {
        val target = targets.first()
        return LookupQuery(
            title = field(TagField.Title.key).text.ifBlank { target.title.orEmpty() },
            artist = field(TagField.Artist.key).values.firstOrNull(),
            album = field(TagField.Album.key).text.ifBlank { null },
            durationMs = audio?.durationMs ?: target.durationMs,
        )
    }

    /** What [candidate] would set in the file, field by field, against its tags as edited. */
    fun proposals(candidate: LookupCandidate): List<FieldProposal> = proposalsFor(candidate, draft?.tags ?: TagMap.Empty, positionStyle)

    /** The fields a lookup found, as chosen: changes to save, as typed ones are. */
    fun applyFields(fields: Map<String, List<String>>) {
        if (!many) {
            val d = draft ?: return
            draft = d.setAll(fields)
        } else {
            fields.forEach { (key, values) -> set(key, values) }
        }
    }

    /** What a cover is looked for by: the file (or the files, as they will be) and the cover it has now. */
    fun coverRequest(): CoverRequest {
        if (!many) {
            val d = draft
            val target = targets.first()
            return CoverRequest.forSongs(
                songs = listOfNotNull(d?.tags),
                titleFallback = target.title ?: target.name,
                durationMs = audio?.durationMs ?: target.durationMs,
                currentCover = d?.cover?.data,
            )
        }
        val first = loaded.firstOrNull()
        return CoverRequest.forSongs(
            songs = editedTags(),
            titleFallback = first?.target?.title ?: first?.target?.name.orEmpty(),
            durationMs = first?.target?.durationMs,
            currentCover = (coverChange as? ArtworkChange.Replace)?.picture?.data,
        )
    }

    companion object {
        /** At most this many files are edited together. */
        const val MAX_FILES = 1000

        /** 1.4.0: the files whose audio the lossless check reads. */
        private val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "wave")

        private const val PARALLEL_READS = 4

        /** Keys with a place of their own; everything else is under "Tüm etiketler". */
        val KnownKeys: Set<String> = TagField.entries.map { it.key }.toSet() + TagField.TrackTotalKeys + TagField.DiscTotalKeys

        internal fun positionKeys(field: TagField): List<String> =
            listOf(field.key) + if (field == TagField.TrackNumber) TagField.TrackTotalKeys else TagField.DiscTotalKeys

        /** The value most of these files have in [key], the first of them on a tie; null where none has one. */
        internal fun List<TagMap>.mostCommon(key: String): String? =
            mapNotNull { tags -> tags.first(key)?.trim()?.ifEmpty { null } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

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

/**
 * Whether a field a lookup found is checked at first: it adds to the file. One that would take from it is offered but
 * left unchecked: fewer artists than the file has, a date less precise than its own, the same names in other letters.
 */
internal val FieldProposal.takenAtFirst: Boolean
    get() {
        if (!differs) return false
        val now = current.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        if (now.isEmpty()) return true
        val found = proposed.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        if (now.containsAll(found)) return false
        val date = field == TagField.Date && now.size == 1 && found.size == 1 && now[0].startsWith(found[0])
        return !date
    }
