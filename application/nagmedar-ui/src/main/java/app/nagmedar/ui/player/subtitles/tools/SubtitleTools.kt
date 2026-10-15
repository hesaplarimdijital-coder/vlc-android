package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.SubtitleEncoding
import app.nagmedar.ui.player.subtitles.decodeSubtitle
import app.nagmedar.ui.player.subtitles.detectEncoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A subtitle file the tools work on: where it is and what it is called. */
@Immutable
data class ToolsFile(val uri: String, val name: String)

/** Why a file could not be opened. */
enum class ToolsProblem { Unreadable, Unsupported }

/** The three tools, as tabs (S-708). */
enum class ToolsTab { Clean, FrameRate, Edit }

/** What the tools read and write: files as bytes (the app's own implementation uses the system's documents). */
interface SubtitleToolsIo {
    /** The whole file; null when it cannot be read. */
    suspend fun read(file: ToolsFile): ByteArray?

    /** [bytes] as a new file called [name] beside [file]; null when the app may not write there. Never replaces a file. */
    suspend fun saveBeside(file: ToolsFile, name: String, bytes: ByteArray): SavedSubtitle?

    /** [bytes] into the document [uri], which the system's "save as" made new. */
    suspend fun write(uri: String, bytes: ByteArray): Boolean
}

/** The playing film, when the tools are opened from the player. */
interface ToolsPlayback {
    /** Where the film is on the subtitles' clock: its position less the subtitles' delay. */
    fun positionMs(): Long

    /** The film at [ms] on the subtitles' clock. */
    fun seekTo(ms: Long)

    /** The saved file into the film, showing. */
    suspend fun load(saved: SavedSubtitle): Boolean
}

/** What the frame rate tab would do: the first and last times before and after, and the scale. */
@Immutable
data class RatePreview(val firstBefore: Long, val firstAfter: Long, val lastBefore: Long, val lastAfter: Long, val factor: Double, val dropped: Int)

/** The window's state (S-708). */
@Immutable
data class SubtitleToolsState(
    /** The files to choose from: the film's subtitle files. */
    val files: List<ToolsFile> = emptyList(),
    val file: ToolsFile? = null,
    val loading: Boolean = false,
    val problem: ToolsProblem? = null,
    /** The file as it is being worked on; null until one is read. */
    val document: SubtitleDocument? = null,
    /** The character set the file was read in. */
    val encoding: SubtitleEncoding? = null,
    val tab: ToolsTab = ToolsTab.Clean,
    val sdh: SdhOptions = SdhOptions(),
    val sdhResult: SdhResult? = null,
    val from: FrameRate = FrameRate.Fps23976,
    val to: FrameRate = FrameRate.Fps25,
    val shiftMs: Long = 0,
    val ratePreview: RatePreview? = null,
    val issues: CueIssues = CueIssues(emptyMap()),
    /** The cue the editor's fields show. */
    val focus: Long? = null,
    val selected: Set<Long> = emptySet(),
    /** The cue playing now, when opened from the player. */
    val playing: Long? = null,
    /** How many steps can be taken back. */
    val steps: Int = 0,
    /** The new file's name, as the user edits it, and the format it is written in. */
    val name: String = "",
    val format: SubtitleFormat? = null,
    val bom: Boolean = false,
    val loadAfter: Boolean = false,
    val saving: Boolean = false,
    val saved: SavedSubtitle? = null,
    /** Whether the saved file is showing in the film. */
    val loaded: Boolean = false,
    val saveFailed: Boolean = false,
    /** The system's "save as" is wanted, with this name: the folder beside the file is not the app's to write in. */
    val pickName: String? = null,
) {
    val changed: Boolean get() = steps > 0
}

/**
 * The subtitle tools (S-708): cleaning for the hard of hearing, frame rate conversion and shift,
 * and the line timing editor, on one file at a time. The tools work on a copy, one after another;
 * every step can be taken back. The result is always a new file, written beside the original
 * with a name of its own, or wherever the system's "save as" puts it; the original is not touched.
 */
@Stable
class SubtitleToolsController(
    private val io: SubtitleToolsIo,
    private val scope: CoroutineScope,
    /** The word that marks a new file: "film.tr.duzenlendi.srt". */
    private val editedWord: String,
    private val cpu: CoroutineDispatcher = Dispatchers.Default,
) {
    var state by mutableStateOf<SubtitleToolsState?>(null)
        private set

    /** The film, when the tools were opened from the player. */
    var playback: ToolsPlayback? = null
        private set

    private var original: SubtitleDocument? = null
    private val history = ArrayList<Pair<SubtitleDocument, String?>>()
    private var anchor: Long? = null
    private var pending: ByteArray? = null
    private var job: Job? = null

    /** The window opens on [files] (the first is read), with [playback] when a film plays. */
    fun open(files: List<ToolsFile>, playback: ToolsPlayback? = null) {
        this.playback = playback
        state = SubtitleToolsState(files = files, loadAfter = playback != null)
        files.firstOrNull()?.let { choose(it) }
    }

    fun close() {
        job?.cancel()
        state = null
        original = null
        history.clear()
        pending = null
    }

    /** [file] is read; what was being worked on is let go. */
    fun choose(file: ToolsFile) {
        job?.cancel()
        val base = state ?: SubtitleToolsState()
        history.clear()
        original = null
        anchor = null
        state = base.copy(
            files = if (base.files.any { it.uri == file.uri }) base.files else base.files + file,
            file = file,
            loading = true,
            problem = null,
            document = null,
            sdhResult = null,
            ratePreview = null,
            issues = CueIssues(emptyMap()),
            focus = null,
            selected = emptySet(),
            steps = 0,
            saved = null,
            saveFailed = false,
            pickName = null,
        )
        job = scope.launch {
            val bytes = try {
                io.read(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (bytes == null) {
                state = state?.copy(loading = false, problem = ToolsProblem.Unreadable)
                return@launch
            }
            val read = withContext(cpu) {
                val encoding = detectEncoding(bytes)
                SubtitleCodec.parse(file.name, decodeSubtitle(bytes, encoding))?.takeIf { it.cues.isNotEmpty() }?.let { it to encoding }
            }
            if (read == null) {
                state = state?.copy(loading = false, problem = ToolsProblem.Unsupported)
                return@launch
            }
            val (document, encoding) = read
            original = document
            val current = state ?: return@launch
            state = refreshed(
                current.copy(
                    loading = false,
                    document = document,
                    encoding = encoding,
                    format = document.format,
                    bom = document.bom,
                    name = suggestName(file.name, editedWord, document.format),
                    focus = document.cues.firstOrNull()?.id,
                ),
            )
        }
    }

    fun setTab(tab: ToolsTab) {
        state = state?.copy(tab = tab)
    }

    // ---------------------------------------------------------------- cleaning

    fun setSdh(options: SdhOptions) {
        state = state?.let { refreshed(it.copy(sdh = options)) }
    }

    /** The cleaning done on the working copy. */
    fun applySdh() {
        val current = state ?: return
        val result = current.sdhResult ?: return
        val document = current.document ?: return
        if (result.changes.isEmpty()) return
        commit(document.withCues(result.cues), "sdh")
    }

    // ---------------------------------------------------------------- frame rate

    fun setRates(from: FrameRate, to: FrameRate) {
        state = state?.let { refreshed(it.copy(from = from, to = to)) }
    }

    fun setShift(ms: Long) {
        state = state?.let { refreshed(it.copy(shiftMs = ms.coerceIn(-MAX_SHIFT_MS, MAX_SHIFT_MS))) }
    }

    /** The scale and the shift done on the working copy. */
    fun applyRates() {
        val current = state ?: return
        val document = current.document ?: return
        if (current.from == current.to && current.shiftMs == 0L) return
        val moved = FrameRateConversion.apply(document.cues, current.from, current.to, current.shiftMs)
        commit(document.withCues(moved.cues), "rates")
        // the shift is done: it does not ride along into the next conversion
        state = state?.let { refreshed(it.copy(shiftMs = 0)) }
    }

    // ---------------------------------------------------------------- editor

    /** A click on a cue, as a list takes it: one, or with Ctrl one more, or with Shift the range from the last. */
    fun click(id: Long, ctrl: Boolean = false, shift: Boolean = false) {
        val current = state ?: return
        val order = current.document?.cues?.map { it.id }.orEmpty()
        val from = anchor?.let(order::indexOf) ?: -1
        val to = order.indexOf(id)
        val selected = when {
            shift && from >= 0 && to >= 0 -> {
                val range = order.subList(minOf(from, to), maxOf(from, to) + 1).toSet()
                if (ctrl) current.selected + range else range
            }
            ctrl -> if (id in current.selected) current.selected - id else current.selected + id
            else -> setOf(id)
        }
        if (!shift) anchor = id
        lastKey = null
        state = current.copy(focus = id, selected = selected)
    }

    fun selectAll() {
        val current = state ?: return
        state = current.copy(selected = current.document?.cues?.map { it.id }?.toSet().orEmpty())
    }

    /** The next cue (or the one before), selected and shown in the fields; [seek], the film goes to it. */
    fun step(forward: Boolean, seek: Boolean = false) {
        val current = state ?: return
        val order = current.document?.cues?.map { it.id }.orEmpty()
        if (order.isEmpty()) return
        val at = order.indexOf(current.focus)
        val target = order[(if (at < 0) 0 else (at + if (forward) 1 else -1).coerceIn(0, order.lastIndex))]
        anchor = target
        lastKey = null
        state = current.copy(focus = target, selected = setOf(target))
        if (seek) seekToFocus()
    }

    fun focusIssue() {
        val current = state ?: return
        val order = current.document?.cues?.map { it.id }.orEmpty()
        val target = current.issues.next(order, current.focus) ?: return
        anchor = target
        state = current.copy(focus = target, selected = setOf(target))
    }

    /** A cue starts at [ms]; typed, the end stays where it is (a check marks a cue that ends before it starts); from the film, [keepLength] carries the end along. */
    fun setStart(id: Long, ms: Long, keepLength: Boolean = false) = edit("start:$id") { CueOps.setStart(it, id, ms.coerceAtLeast(0), keepLength) }

    fun setEnd(id: Long, ms: Long) = edit("end:$id") { CueOps.setEnd(it, id, ms.coerceAtLeast(0)) }

    fun setText(id: Long, text: String) = edit("text:$id") { CueOps.setText(it, id, text) }

    /** The selected cues [deltaMs] later or earlier. */
    fun shiftSelected(deltaMs: Long) {
        val ids = state?.selected.orEmpty().ifEmpty { listOfNotNull(state?.focus).toSet() }
        if (ids.isEmpty()) return
        edit(null) { CueOps.shift(it, ids, deltaMs) }
    }

    fun deleteSelected() {
        val current = state ?: return
        val ids = current.selected.ifEmpty { listOfNotNull(current.focus).toSet() }
        if (ids.isEmpty()) return
        val order = current.document?.cues?.map { it.id }.orEmpty()
        val first = order.indexOfFirst { it in ids }
        // the cue after the deleted ones, else the one before
        val next = order.drop(first).firstOrNull { it !in ids } ?: order.take(first).lastOrNull { it !in ids }
        edit(null) { CueOps.delete(it, ids) }
        state = state?.copy(focus = next, selected = listOfNotNull(next).toSet())
    }

    /** The focused cue in two, at the film's position when it is inside the cue, else in the middle. */
    fun split() {
        val current = state ?: return
        val document = current.document ?: return
        val id = current.focus ?: return
        val cue = document.cues.firstOrNull { it.id == id } ?: return
        val now = playback?.positionMs()?.takeIf { it > cue.startMs && it < cue.endMs }
        val edit = CueOps.split(document, id, now) ?: return
        commit(edit.document, null)
        state = state?.copy(focus = edit.focus, selected = listOfNotNull(edit.focus).toSet())
    }

    /** The selected cues as one; a lone one with the one after it. */
    fun merge() {
        val current = state ?: return
        val document = current.document ?: return
        val ids = current.selected.ifEmpty { listOfNotNull(current.focus).toSet() }
        val edit = CueOps.merge(document, ids) ?: return
        commit(edit.document, null)
        state = state?.copy(focus = edit.focus, selected = listOfNotNull(edit.focus).toSet())
    }

    /** The focused cue starts where the film is. */
    fun setStartHere() {
        val id = state?.focus ?: return
        val ms = playback?.positionMs() ?: return
        setStart(id, ms, keepLength = true)
    }

    /** The focused cue ends where the film is. */
    fun setEndHere() {
        val id = state?.focus ?: return
        val ms = playback?.positionMs() ?: return
        setEnd(id, ms)
    }

    /** The film at the focused cue's start. */
    fun seekToFocus() {
        val current = state ?: return
        val cue = current.document?.cues?.firstOrNull { it.id == current.focus } ?: return
        playback?.seekTo(cue.startMs)
    }

    /** The cue playing now is told to the window (a list marks it); called as the film plays. */
    fun follow() {
        val current = state ?: return
        val ms = playback?.positionMs() ?: return
        val now = current.document?.cues?.firstOrNull { ms >= it.startMs && ms < it.endMs }?.id
        if (now != current.playing) state = current.copy(playing = now)
    }

    /** The last step taken back. */
    fun undo() {
        val last = history.removeLastOrNull() ?: return
        val current = state ?: return
        lastKey = null
        state = refreshed(current.copy(document = last.first, steps = history.size, saved = null, selected = current.selected.filterTo(HashSet()) { id -> last.first.cues.any { it.id == id } }))
    }

    /** Everything taken back: the file as it was read. */
    fun revert() {
        val first = original ?: return
        val current = state ?: return
        history.clear()
        state = refreshed(current.copy(document = first, steps = 0, saved = null, focus = first.cues.firstOrNull()?.id, selected = emptySet()))
    }

    // ---------------------------------------------------------------- saving

    fun setName(name: String) {
        state = state?.copy(name = name)
    }

    fun setBom(on: Boolean) {
        state = state?.copy(bom = on)
    }

    fun setLoadAfter(on: Boolean) {
        state = state?.copy(loadAfter = on)
    }

    /** SRT or WebVTT as the file to write; the name's extension follows. */
    fun setFormat(format: SubtitleFormat) {
        val current = state ?: return
        val document = current.document ?: return
        if (document.format.styled || current.format == format) return
        state = current.copy(format = format, name = current.name.substringBeforeLast('.', current.name) + "." + format.extension)
    }

    /** The result as a new file beside the original; where the app may not write, the system's "save as" is asked for. */
    fun save() {
        val current = state ?: return
        val document = current.document ?: return
        val file = current.file ?: return
        if (current.saving || !current.issues.saveable) return
        val output = document.asFormat(current.format ?: document.format) ?: document
        val bytes = output.toBytes(current.bom)
        val name = namedFor(current.name, output.format)
        state = current.copy(saving = true, saved = null, saveFailed = false, pickName = null, name = name)
        job = scope.launch {
            val saved = try {
                io.saveBeside(file, name, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (saved != null) {
                finish(saved)
            } else {
                pending = bytes
                state = state?.copy(saving = false, pickName = name)
            }
        }
    }

    /** The system's "save as" answered: the new document's [uri], or null when it was dismissed. */
    fun savedTo(uri: String?) {
        val bytes = pending
        val current = state ?: return
        val name = current.pickName ?: return
        pending = null
        if (uri == null || bytes == null) {
            state = current.copy(pickName = null)
            return
        }
        state = current.copy(pickName = null, saving = true)
        job = scope.launch {
            val written = try {
                io.write(uri, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (written) finish(SavedSubtitle(uri, name, beside = false)) else state = state?.copy(saving = false, saveFailed = true)
        }
    }

    private suspend fun finish(saved: SavedSubtitle) {
        val current = state ?: return
        val loaded = current.loadAfter && playback?.let { runCatching { it.load(saved) }.getOrDefault(false) } == true
        state = current.copy(saving = false, saved = saved, loaded = loaded, saveFailed = false)
    }

    // ---------------------------------------------------------------- shortcuts

    /** The editor's shortcuts; whether [id] is one of them (and so was used). */
    fun perform(id: ShortcutId): Boolean {
        val current = state ?: return false
        if (current.document == null) return false
        when (id) {
            ShortcutId.CueSave -> save()
            ShortcutId.CueSplit -> split()
            ShortcutId.CueMerge -> merge()
            ShortcutId.CueSetStart -> setStartHere()
            ShortcutId.CueSetEnd -> setEndHere()
            ShortcutId.CueNext -> step(forward = true, seek = true)
            ShortcutId.CuePrevious -> step(forward = false, seek = true)
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- internals

    private var lastKey: String? = null

    /** [change] done on the working copy; steps with the same [key] in a row (typing) are one step. */
    private fun edit(key: String?, change: (SubtitleDocument) -> SubtitleDocument) {
        val document = state?.document ?: return
        val next = change(document)
        if (next == document) return
        commit(next, key)
    }

    private fun commit(next: SubtitleDocument, key: String?) {
        val current = state ?: return
        val document = current.document ?: return
        if (key == null || key != lastKey || history.isEmpty()) {
            history.add(document to key)
            if (history.size > MAX_STEPS) history.removeAt(0)
        }
        lastKey = key
        state = refreshed(current.copy(document = next, steps = history.size, saved = null))
    }

    /** The previews and the issues of [value]'s document, and a focus that is still there. */
    private fun refreshed(value: SubtitleToolsState): SubtitleToolsState {
        val document = value.document ?: return value
        val cues = document.cues
        val result = SdhCleaner.clean(cues, value.sdh)
        val focus = value.focus?.takeIf { id -> cues.any { it.id == id } } ?: cues.firstOrNull()?.id
        return value.copy(
            sdhResult = if (value.sdh.any) result else SdhResult(cues, emptyList(), cues.size),
            ratePreview = ratePreview(cues, value),
            issues = CueOps.validate(cues),
            focus = focus,
        )
    }

    private fun ratePreview(cues: List<TimedCue>, value: SubtitleToolsState): RatePreview? {
        val first = cues.firstOrNull() ?: return null
        val lastEnd = cues.maxOf { it.endMs }
        val moved = FrameRateConversion.apply(cues, value.from, value.to, value.shiftMs).cues
        return RatePreview(
            firstBefore = first.startMs,
            firstAfter = moved.firstOrNull()?.startMs ?: 0,
            lastBefore = lastEnd,
            lastAfter = moved.maxOfOrNull { it.endMs } ?: 0,
            factor = FrameRateConversion.factor(value.from, value.to),
            dropped = cues.size - moved.size,
        )
    }

    companion object {
        /** A shift of more than a day is not one. */
        const val MAX_SHIFT_MS = 86_400_000L

        private const val MAX_STEPS = 100

        /** "film.tr.srt" → "film.tr.duzenlendi.srt": the same name with [word] before the extension. */
        fun suggestName(original: String, word: String, format: SubtitleFormat): String {
            val base = original.substringBeforeLast('.', original)
            val named = if (base.lowercase(Locale.ROOT).endsWith(".${word.lowercase(Locale.ROOT)}")) base else "$base.$word"
            return "$named.${format.extension}"
        }

        /** [typed] as a file name of [format]: without path separators, ending in the format's extension. */
        fun namedFor(typed: String, format: SubtitleFormat): String {
            val clean = typed.trim().replace(Regex("""[/\\:*?"<>|]"""), "_").ifEmpty { "subtitles" }
            val base = if (clean.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions) clean.substringBeforeLast('.') else clean
            return "$base.${format.extension}"
        }

        private val extensions = SubtitleFormat.entries.map { it.extension }.toSet()
    }
}
