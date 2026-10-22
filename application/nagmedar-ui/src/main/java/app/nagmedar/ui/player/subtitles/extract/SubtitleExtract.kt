package app.nagmedar.ui.player.subtitles.extract

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.lab.MediaProbe
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.library.TechRepository
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsIo
import app.nagmedar.ui.player.subtitles.tools.ToolsFile
import app.nagmedar.ui.player.subtitles.tools.WriteOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** The extraction as the tools' window shows it. */
sealed interface ExtractRun {
    data object Idle : ExtractRun

    /** Asked for; WorkManager has not begun it yet. */
    data class Starting(val track: EmbeddedTrack) : ExtractRun

    /** [fraction] of the film read (null: not yet told). */
    data class Running(val track: EmbeddedTrack?, val fraction: Float?) : ExtractRun

    /** Written as [saved]: [lines] lines; [partial], the film's file ended early. */
    data class Done(val track: EmbeddedTrack?, val saved: SavedSubtitle, val lines: Int, val partial: Boolean) : ExtractRun

    data class Failed(val track: EmbeddedTrack?, val failure: ExtractFailure) : ExtractRun

    data object Cancelled : ExtractRun

    /** Another extraction runs; this one was not started. */
    data object Busy : ExtractRun
}

/** What the "save elsewhere" of an extracted file came to. */
enum class CopyOutcome { Saved, SameFile, Failed }

/** The film's embedded subtitles in the tools' window. */
@Immutable
data class ExtractState(
    /** The film; null when the window was opened on a subtitle file alone. */
    val film: ToolsFile? = null,
    /** This build has the native extraction. */
    val available: Boolean = true,
    /** The film's tracks are being read. */
    val loading: Boolean = false,
    val tracks: List<EmbeddedTrack> = emptyList(),
    /** Opened for the extraction itself: the window says so even when the film has nothing to extract. */
    val asked: Boolean = false,
    val run: ExtractRun = ExtractRun.Idle,
    /** The system's "save as" is wanted for the extracted file, with this name. */
    val pickName: String? = null,
    /** Where "save elsewhere" put the file, or why it did not. */
    val copied: SavedSubtitle? = null,
    val copyProblem: CopyOutcome? = null,
) {
    val running: Boolean get() = run is ExtractRun.Starting || run is ExtractRun.Running
}

/** What the extraction works with (the app's: the media probe and WorkManager). */
interface ExtractService {
    val available: Boolean

    /** The film's subtitle tracks as the media probe lists them; empty when it cannot be read. */
    suspend fun tracks(film: ToolsFile): List<EmbeddedTrack>

    /** The extraction started as the job [id]; false while another one runs. */
    suspend fun start(id: String, request: ExtractRequest): Boolean

    fun cancel()

    /** The last extraction job, as it goes. */
    fun job(): Flow<ExtractJob?>
}

/**
 * "Extract embedded subtitles…": the film's text subtitle tracks (SubRip, ASS/SSA, WebVTT, MP4
 * timed text) listed with their language, title and format; the one chosen is written by a
 * background job as a NEW file beside the film ("Film.tr.srt", ASS kept as ASS), and the tools
 * go on with it as with any subtitle file. Picture tracks are listed, and refused with why.
 * Where the film's folder cannot be written the file goes into the app's folder, and "save
 * elsewhere" puts a copy wherever the system's picker says (never over a file that is there).
 */
@Stable
class SubtitleExtractController(
    private val service: ExtractService,
    private val io: SubtitleToolsIo,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(ExtractState(available = service.available))
        private set

    private var watcher: Job? = null
    private var loader: Job? = null
    private var onReady: (ToolsFile) -> Unit = {}

    /** The job started from this window, and whether its outcome was taken in. */
    private var started: String? = null
    private var handled: String? = null
    private var copyJob: Job? = null

    /**
     * The window opened on [film] ([asked]: for the extraction itself): its tracks are read and
     * the job is followed; a file this window's job writes is handed to [onReady].
     */
    fun attach(film: ToolsFile?, asked: Boolean, onReady: (ToolsFile) -> Unit) {
        detach()
        this.onReady = onReady
        started = null
        state = ExtractState(film = film, available = service.available, loading = film != null && service.available, asked = asked)
        if (film == null) return
        watcher = scope.launch { service.job().collect { onJob(it) } }
        if (!service.available) return
        loader = scope.launch {
            val tracks = try {
                service.tracks(film)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            state = state.copy(loading = false, tracks = tracks)
        }
    }

    /** The window closed: nothing is followed; an extraction under way goes on in the background. */
    fun detach() {
        watcher?.cancel()
        loader?.cancel()
        copyJob?.cancel()
        watcher = null
        loader = null
        onReady = {}
        state = ExtractState(available = service.available)
    }

    /** [track] written into a new file; a picture track is refused with why, a job under way is not disturbed. */
    fun extract(track: EmbeddedTrack) {
        val current = state
        val film = current.film ?: return
        if (current.running) return
        if (!current.available) {
            state = current.copy(run = ExtractRun.Failed(track, ExtractFailure.NoNative))
            return
        }
        if (track.picture) {
            state = current.copy(run = ExtractRun.Failed(track, ExtractFailure.Picture))
            return
        }
        if (!track.text) {
            state = current.copy(run = ExtractRun.Failed(track, ExtractFailure.Unsupported))
            return
        }
        val request = ExtractRequest(film.uri, film.name, track, extractedName(film.name, track))
        // the job's id is known before it starts: an outcome that comes at once is this window's
        val id = UUID.randomUUID().toString()
        started = id
        state = current.copy(run = ExtractRun.Starting(track), copied = null, copyProblem = null, pickName = null)
        scope.launch {
            val queued = try {
                service.start(id, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(run = ExtractRun.Failed(track, ExtractFailure.Engine))
                return@launch
            }
            if (!queued) state = state.copy(run = ExtractRun.Busy)
        }
    }

    fun cancel() = service.cancel()

    /** "Save elsewhere…": the system's "save as" for a copy of the extracted file. */
    fun saveElsewhere() {
        val done = state.run as? ExtractRun.Done ?: return
        state = state.copy(pickName = done.saved.name, copyProblem = null)
    }

    /** The system's "save as" answered: the new document's [uri], or null when it was dismissed. */
    fun savedElsewhere(uri: String?) {
        val current = state
        val done = current.run as? ExtractRun.Done
        val film = current.film
        val name = current.pickName
        state = current.copy(pickName = null)
        if (uri == null || done == null || film == null || name == null) return
        copyJob = scope.launch {
            val outcome = try {
                // never into the film itself, nor into a document that is not new and empty
                io.read(ToolsFile(done.saved.uri, done.saved.name))?.let { bytes -> io.write(uri, film, bytes) } ?: WriteOutcome.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WriteOutcome.Failed
            }
            state = when (outcome) {
                WriteOutcome.Written -> {
                    val copy = SavedSubtitle(uri, name, beside = false)
                    onReady(ToolsFile(copy.uri, copy.name))
                    state.copy(copied = copy, copyProblem = null)
                }
                WriteOutcome.SameFile -> state.copy(copyProblem = CopyOutcome.SameFile)
                WriteOutcome.Failed -> state.copy(copyProblem = CopyOutcome.Failed)
            }
        }
    }

    private fun onJob(job: ExtractJob?) {
        if (job == null) return
        val track = job.request?.track
        if (!job.finished) {
            // a job of another film is told as running, without its track; one of this film's is this window's
            val ours = job.request?.film == state.film?.uri
            if (ours && started == null) started = job.id
            state = state.copy(run = ExtractRun.Running(if (ours) track else null, job.fraction))
            return
        }
        if (job.id == handled) return
        // a job of an earlier window, or of another film, is not this one's to report
        if (job.id != started) {
            if (state.running) state = state.copy(run = ExtractRun.Idle)
            return
        }
        handled = job.id
        val saved = job.saved
        state = when {
            job.cancelled -> state.copy(run = ExtractRun.Cancelled)
            job.failure != null || saved == null -> state.copy(run = ExtractRun.Failed(track, job.failure ?: ExtractFailure.Engine))
            else -> {
                onReady(ToolsFile(saved.uri, saved.name))
                state.copy(run = ExtractRun.Done(track, saved, job.lines, job.partial))
            }
        }
    }
}

/** The app's extraction: the library's media probe for the tracks, a WorkManager job for the file. */
class WorkExtractService(context: Context) : ExtractService {
    private val app = context.applicationContext

    override val available: Boolean get() = NativeLab.available

    override suspend fun tracks(film: ToolsFile): List<EmbeddedTrack> = withContext(Dispatchers.IO) {
        // the library's probe of the film; a film it does not keep (a document that does not tell its size) is probed here
        val tech = TechRepository.get(app).probeNow(film.uri) ?: MediaProbe.probe(app, Uri.parse(film.uri))?.let(MediaProbe::parse)
        embeddedTracks(tech)
    }

    override suspend fun start(id: String, request: ExtractRequest): Boolean =
        withContext(Dispatchers.IO) { ExtractJobs.start(app, UUID.fromString(id), request) }

    override fun cancel() = ExtractJobs.cancel(app)

    override fun job(): Flow<ExtractJob?> = ExtractJobs.observe(app)
}
