package app.nagmedar.ui.lyrics.editor

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.Lyrics
import app.nagmedar.ui.lyrics.LyricsTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The player the editor times against: the playback service in the app, a fake in the tests. */
interface EditorPlayer {
    val positionMs: Long
    val playing: Boolean

    /** The track being edited is the one playing: stamps are made against it. */
    val onTrack: Boolean
    fun seekTo(ms: Long)
    fun togglePlay()
}

/** Where the lyrics are saved. */
enum class SaveTarget { File, Tag }

/** What the editor says after an action. */
sealed interface EditorMessage {
    /** Saved to the .lrc [name], or the tags (null). */
    data class Saved(val target: SaveTarget, val name: String?) : EditorMessage

    /** Saving needs every line timed: [left] are not. */
    data class Incomplete(val left: Int) : EditorMessage

    /** The folder beside the file cannot be written: the user picks one. */
    data object NeedsFolder : EditorMessage

    data object SaveFailed : EditorMessage

    /** LRCLIB needs the track's title, artist and length, and every line timed. */
    data object CannotPublish : EditorMessage

    data object NothingToImport : EditorMessage
}

/**
 * S-1005: the lyrics editor. It holds the lyrics being timed ([stamps]), turns the editor's
 * shortcuts ([perform]) into stamps against the [player], and saves ([save]) or publishes
 * ([askPublish], [confirmPublish]) the result. Saving needs every line timed; publishing needs the
 * user's confirmation and runs its proof of work off the main thread, with progress and cancel.
 */
@Stable
class LyricsEditorController(
    val track: LyricsTrack,
    /** The lyrics the track has now, to start from (a delay of theirs is baked into the times). */
    val current: Lyrics?,
    private val player: EditorPlayer,
    private val writer: LyricsWriter?,
    private val publisher: LyricsPublisher?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val workers: Int = ProofOfWork.defaultWorkers(),
    private val onSaved: () -> Unit = {},
) {
    private val start: StampState = current?.let { StampState.of(baked(it)) } ?: StampState(emptyList())

    var stamps: StampState by mutableStateOf(start)
        private set

    /** The lines as last saved or loaded: what [dirty] compares with. */
    private var baseline: List<EditLine> = start.lines

    var preview: Boolean by mutableStateOf(false)
    var target: SaveTarget by mutableStateOf(SaveTarget.File)
    var importing: Boolean by mutableStateOf(current == null)
    var message: EditorMessage? by mutableStateOf(null)
        private set
    var saving: Boolean by mutableStateOf(false)
        private set
    var publish: PublishState by mutableStateOf(PublishState.Idle)
        private set

    /** The lyrics the editor replaced before, to bring back. */
    var originals: List<OriginalLyrics> by mutableStateOf(emptyList())
        private set

    /** A text box has the keyboard: the editor's keys are its own. */
    var typing: Boolean by mutableStateOf(false)

    private var publishing: Job? = null

    /** Stamps or lines were changed since the lyrics were loaded or last saved. */
    val dirty: Boolean get() = stamps.lines != baseline

    val canPublish: Boolean get() = publisher != null

    /** Looks up what was replaced before. Reads the cache: off the main thread. */
    fun loadOriginals() {
        val writer = writer ?: return
        scope.launch {
            val found = withContext(io) { runCatching { writer.originals(track) }.getOrDefault(emptyList()) }
            originals = found
        }
    }

    // ------------------------------------------------------------------ stamping

    fun stamp() {
        if (!player.onTrack) return
        stamps = stamps.stamp(player.positionMs)
        message = null
    }

    fun unstamp() {
        stamps = stamps.undo()
    }

    fun moveBy(delta: Int) {
        stamps = stamps.moveBy(delta)
    }

    fun nudge(deltaMs: Long) {
        stamps = stamps.nudge(deltaMs)
    }

    fun setMode(mode: StampMode) {
        stamps = stamps.withMode(mode)
    }

    fun toggleMode() = setMode(if (stamps.mode == StampMode.Line) StampMode.Word else StampMode.Line)

    /** A click on a line: the cursor goes there and the track to its time, when it has one. */
    fun select(line: Int) {
        stamps = stamps.moveTo(line)
        stamps.lines.getOrNull(line)?.takeIf { it.stamped }?.let { player.seekTo(it.startMs) }
    }

    fun togglePlay() = player.togglePlay()

    /** The editor's shortcut [id]; false when it is not one of its own. */
    fun perform(id: ShortcutId): Boolean {
        when (id) {
            ShortcutId.LrcStamp -> stamp()
            ShortcutId.LrcUnstamp -> unstamp()
            ShortcutId.LrcLineUp -> moveBy(-1)
            ShortcutId.LrcLineDown -> moveBy(1)
            ShortcutId.LrcNudgeEarlier -> nudge(-NUDGE_MS)
            ShortcutId.LrcNudgeLater -> nudge(NUDGE_MS)
            ShortcutId.LrcWordMode -> toggleMode()
            ShortcutId.PlayPause -> togglePlay()
            else -> return false
        }
        return true
    }

    // ------------------------------------------------------------------ text in

    /** Sets the lines from [text]: plain lyrics, or LRC whose stamps are kept. */
    fun import(text: String) {
        val next = StampState.parse(text, stamps.mode)
        if (next.lines.isEmpty()) {
            message = EditorMessage.NothingToImport
            return
        }
        stamps = next
        importing = false
        message = null
    }

    /** Starts over from the lyrics the track has now. */
    fun useCurrent(): Boolean {
        val lyrics = current ?: return false
        stamps = StampState.of(baked(lyrics), stamps.mode)
        importing = false
        return true
    }

    private fun baked(lyrics: Lyrics): List<LyricLine> =
        if (lyrics.offsetMs == 0L) lyrics.lines else lyrics.lines.map { if (it.startMs >= 0) it.shifted(lyrics.offsetMs) else it }

    // ------------------------------------------------------------------ saving

    /** Saves to [target]; beside the file, into [folder] when the user picked one. */
    fun save(folder: Uri? = null) {
        val writer = writer ?: return
        if (saving) return
        val state = stamps
        if (!state.complete) {
            message = EditorMessage.Incomplete(state.lines.size - state.stampedLines)
            return
        }
        val chosen = target
        val lines = state.lyrics()
        saving = true
        scope.launch {
            val result = withContext(io) {
                runCatching {
                    when (chosen) {
                        SaveTarget.File -> writer.toFile(track, lines, LrcHeader(track.title, track.artist, track.album, track.lengthMs), folder)
                        SaveTarget.Tag -> writer.toTag(track, lines)
                    }
                }.getOrDefault(SaveResult.Failed)
            }
            saving = false
            message = when (result) {
                is SaveResult.Saved -> {
                    baseline = state.lines
                    onSaved()
                    EditorMessage.Saved(chosen, result.name)
                }
                SaveResult.NeedsFolder -> EditorMessage.NeedsFolder
                SaveResult.Failed -> EditorMessage.SaveFailed
            }
            loadOriginals()
        }
    }

    /** Brings back the lyrics the editor replaced, to time or save again. */
    fun restore(original: OriginalLyrics) {
        stamps = StampState.parse(original.text, stamps.mode)
        importing = false
    }

    // ------------------------------------------------------------------ LRCLIB

    /** Asks the user whether to send the lyrics to LRCLIB, saying what is sent. */
    fun askPublish() {
        if (publisher == null) return
        val state = stamps
        val request = if (state.complete) PublishRequest.of(track, state.lyrics()) else null
        if (request == null) {
            message = EditorMessage.CannotPublish
            return
        }
        publish = PublishState.Confirming(request)
    }

    /** The user said yes: the proof of work and the sending run in the background. */
    fun confirmPublish() {
        val publisher = publisher ?: return
        val confirming = publish as? PublishState.Confirming ?: return
        publishing = scope.launch {
            publishLyrics(publisher, confirming.request, { publish = it }, workers)
        }
    }

    /** Stops the proof of work or the sending. */
    fun cancelPublish() {
        publishing?.cancel()
        publishing = null
        publish = PublishState.Idle
    }

    /** Closes the publication's dialog (after it ended, or before it began). */
    fun dismissPublish() {
        if (publish is PublishState.Challenging || publish is PublishState.Solving || publish is PublishState.Sending) return cancelPublish()
        publish = PublishState.Idle
    }

    companion object {
        /** A nudge moves a stamp a tenth of a second. */
        const val NUDGE_MS = 100L
    }
}
