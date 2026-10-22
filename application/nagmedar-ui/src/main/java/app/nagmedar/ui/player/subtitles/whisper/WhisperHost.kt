package app.nagmedar.ui.player.subtitles.whisper

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.R
import app.nagmedar.ui.library.TechRepository
import app.nagmedar.ui.library.audioLine
import app.nagmedar.ui.library.mainAudio
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.displayName
import app.nagmedar.ui.player.subtitles.tools.DocumentSubtitleToolsIo
import app.nagmedar.ui.player.subtitles.tools.ToolsFile
import app.nagmedar.ui.player.subtitles.tools.WriteOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import java.io.File
import java.util.Locale

/**
 * S-709: "Generate subtitles…" for a film: its audio tracks (as the media probe reads them, in
 * the file's order, the one playing or the main one chosen), the language, whether into English,
 * the model and whether it must be downloaded first; [starting] once the job is asked for.
 */
@Immutable
data class WhisperDialogState(
    val film: String,
    val name: String,
    val language: WhisperLanguage,
    val model: WhisperModel,
    /** The audio tracks' labels, in the file's order; null while the film is read. */
    val tracks: List<String>? = null,
    /** The track chosen: its place among [tracks]; -1 for the one FFmpeg finds best. */
    val track: Int = -1,
    val translate: Boolean = false,
    /** The models on the device. */
    val ready: Set<WhisperModel> = emptySet(),
    /** Whether this build has the native library; true until it is known. */
    val native: Boolean = true,
    val starting: Boolean = false,
) {
    val needsDownload: Boolean get() = model !in ready

    fun request(): WhisperRequest = WhisperRequest(
        film = film,
        name = name,
        audioTrack = track,
        audioLabel = tracks?.getOrNull(track),
        language = language,
        translate = translate,
        model = model,
    )
}

/**
 * S-709: the dialog's state, for the player and the library's window: opened on a film, its
 * choices changed, the job started (the model downloaded first when the user agreed to its
 * size). The film's tracks and the models on the device are read off the main thread.
 */
@Stable
class WhisperController(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext

    var state by mutableStateOf<WhisperDialogState?>(null)
        private set

    private var reading: Job? = null

    /** The dialog on [film] ([name], else its document's); [track] the audio track playing, its place among the file's (-1: not known). */
    fun open(film: Uri, name: String? = null, track: Int = -1) {
        val prefs = WhisperPrefs(Settings.getInstance(app))
        val key = film.toString()
        state = WhisperDialogState(key, name ?: film.lastPathSegment.orEmpty(), prefs.language, prefs.model, track = track)
        reading?.cancel()
        reading = scope.launch {
            val read = withContext(Dispatchers.IO) { read(film, name, track) }
            state = state?.takeIf { it.film == key }?.copy(name = read.name, tracks = read.tracks, track = read.track, ready = read.ready, native = read.native)
        }
    }

    private class Read(val name: String, val tracks: List<String>, val track: Int, val ready: Set<WhisperModel>, val native: Boolean)

    private suspend fun read(film: Uri, name: String?, track: Int): Read {
        val native = NativeLab.available
        val tech = if (native) runCatching { TechRepository.get(app).probeNow(film.toString()) }.getOrNull() else null
        val audio = tech?.audio.orEmpty()
        val locale = ConfigurationCompat.getLocales(app.resources.configuration)[0] ?: Locale.getDefault()
        val commentary = app.getString(R.string.nag_tech_commentary)
        val labels = audio.mapIndexed { index, it ->
            listOf((index + 1).toString(), audioLine(it, locale, commentary), it.title).filter { part -> part.isNotBlank() }.joinToString(" · ")
        }
        val chosen = when {
            track in audio.indices -> track
            audio.isEmpty() -> -1
            else -> tech?.mainAudio()?.let { audio.indexOf(it) } ?: 0
        }
        val shown = name ?: displayName(app.contentResolver, film) ?: film.lastPathSegment.orEmpty()
        return Read(shown, labels, chosen, WhisperModel.entries.filter { WhisperModels.ready(app, it) }.toSet(), native)
    }

    fun setLanguage(language: WhisperLanguage) {
        state = state?.copy(language = language)
    }

    fun setModel(model: WhisperModel) {
        state = state?.copy(model = model)
    }

    fun setTrack(track: Int) {
        state = state?.copy(track = track)
    }

    fun setTranslate(translate: Boolean) {
        state = state?.copy(translate = translate)
    }

    /** The job started with the choices made; the dialog stays, showing it under way. */
    fun start() {
        val current = state ?: return
        if (current.starting || !current.native) return
        state = current.copy(starting = true)
        WhisperJobs.start(app, current.request())
    }

    /** The job under way stopped. */
    fun stop() {
        WhisperJobs.cancel(app)
        state = state?.copy(starting = false)
    }

    /** The job's outcome put away, and the form shown again. */
    fun dismiss(id: String) {
        WhisperJobs.dismiss(app, id)
        state = state?.copy(starting = false)
    }

    /** A job under way or finished: the form may be used again. */
    fun settled() {
        if (state?.starting == true) state = state?.copy(starting = false)
    }

    fun close() {
        reading?.cancel()
        state = null
    }
}

/** The job under way or its outcome, as every window shows it. */
@Composable
fun rememberWhisperStatus(): State<WhisperStatus?> {
    val context = LocalContext.current
    val flow = remember(context) { WhisperJobs.observe(context) }
    return flow.collectAsState(null)
}

/**
 * "Save as…" for subtitles the job could not write beside the film (they are in the app's
 * folder): the system's picker makes a new document, and the file is copied into it, never into
 * the film or a file that has something in it. [onSaved] is told whether it went through.
 */
@Composable
fun rememberWhisperSaveAs(beforePicker: () -> Unit = {}, onSaved: (Boolean) -> Unit): (SavedSubtitle, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<SavedSubtitle, String>?>(null) }
    val creator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SRT_TYPE)) { target ->
        val (saved, film) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                val bytes = Uri.parse(saved.uri).path?.let { runCatching { File(it).readBytes() }.getOrNull() } ?: return@withContext false
                DocumentSubtitleToolsIo(context.applicationContext).write(target.toString(), ToolsFile(film, saved.name), bytes) == WriteOutcome.Written
            }
            onSaved(written)
        }
    }
    return { saved, film ->
        pending = saved to film
        beforePicker()
        if (runCatching { creator.launch(saved.name) }.isFailure) pending = null
    }
}

/** A film from the library whose subtitles are asked for. */
class WhisperLaunch(val film: String, val name: String?)

/**
 * "Generate subtitles…" in a film's menu in the library (S-709): asked here, answered by
 * [WhisperHost], which the app's window shows above its screens.
 */
object WhisperLauncher {
    // process-wide state: made in the global snapshot by ProcessState.warmUp, before any window composes
    var request by mutableStateOf<WhisperLaunch?>(null)
        private set

    fun open(film: Uri, name: String? = null) {
        request = WhisperLaunch(film.toString(), name)
    }

    fun clear() {
        request = null
    }
}

/**
 * The library window's part of the subtitles from speech: the dialog a film's menu asked for, and
 * the outcome of a job once it is done (with "Save as…" when the folder could not take the file),
 * in the corner [modifier] places it.
 */
@Composable
fun WhisperHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context, scope) { WhisperController(context, scope) }
    val status by rememberWhisperStatus()
    val launch = WhisperLauncher.request
    if (launch != null) {
        LaunchedEffect(launch) {
            controller.open(Uri.parse(launch.film), launch.name)
            WhisperLauncher.clear()
        }
    }
    val dialog = controller.state
    if (dialog != null) {
        WhisperDialog(controller, dialog, status, onLoad = null, onDismiss = controller::close)
    } else {
        WhisperOutcomeCorner(status, onLoad = null, modifier = modifier)
    }
}

private const val SRT_TYPE = "application/x-subrip"
