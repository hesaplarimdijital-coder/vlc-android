package app.nagmedar.ui.player.record

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaybackObserver
import org.videolan.vlc.media.PlaybackRecorder
import org.videolan.vlc.media.RecordingHook

/**
 * S-607: the recordings follow the playback service, whatever plays in it (a film, a stream, a
 * radio, the classic audio player's): libVLC's RecordChanged and the media's end reach the
 * [Recorder], and the classic players' options can start and stop it ([RecordingHook]).
 */
class RecordingObserver : PlaybackObserver {
    private var service: PlaybackService? = null

    private val callback = object : PlaybackService.Callback {
        override fun update() = Unit

        override fun onMediaEvent(event: IMedia.Event) = Unit

        override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.RecordChanged -> Recordings.onRecordChanged(event.recording, event.recordPath)
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped, MediaPlayer.Event.EncounteredError, MediaPlayer.Event.MediaChanged ->
                    Recordings.onPlaybackEnded()
            }
        }
    }

    override fun attach(service: PlaybackService) {
        this.service = service
        Recordings.attach(service)
        service.addCallback(callback)
        RecordingHook.recorder = Recordings.hook
    }

    override fun detach() {
        // the recording stops while the service still hears libVLC say where it wrote it
        Recordings.detach()
        service?.removeCallback(callback)
        service = null
        RecordingHook.recorder = null
    }
}

/**
 * The app's recorder (S-607), one for the whole process: it outlives the player's window, and the
 * service too, for as long as a recording is being kept. The player's chrome reads its state.
 */
object Recordings {
    private const val TAG = "Nagmedar/Recording"

    @Volatile
    private var service: PlaybackService? = null
    private var instance: Recorder? by mutableStateOf(null)
    private var ticking: Job? = null
    private val scope = MainScope()

    /** Whether the new interface's player shows the outcomes on its card; else they are said. */
    @Volatile
    var cardShown = false

    /** The recorder, made with the app's context the first time the playback service starts. */
    val recorder: Recorder? get() = instance

    private val engine = object : RecordingEngine {
        override val available: Boolean get() = service?.let { runCatching { it.canRecord }.getOrDefault(false) } ?: false

        override val playing: Boolean get() = service?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false

        override fun title(): String? = service?.let { s -> runCatching { s.title ?: s.currentMediaWrapper?.title }.getOrNull() }

        override fun start(directory: String): Boolean = service?.let { runCatching { it.record(directory) }.getOrDefault(false) } ?: false

        override fun stop(): Boolean = service?.let { runCatching { it.record(null) }.getOrDefault(false) } ?: false
    }

    /** The classic players' "Record" and "Stop recording". */
    val hook = object : PlaybackRecorder {
        override val recording: Boolean get() = instance?.recording == true

        override fun label(context: Context): String = context.getString(if (recording) R.string.nag_rec_stop else R.string.nag_rec_start)

        override val icon: Int get() = R.drawable.nagmedar_record

        override fun toggle(service: PlaybackService) {
            val wasRecording = recording
            toggle()
            // the classic player has no indicator: a start is said; a stop is said by the outcome
            if (!wasRecording && recording) Toast.makeText(service, R.string.nag_rec_started, Toast.LENGTH_SHORT).show()
        }
    }

    fun attach(service: PlaybackService) {
        this.service = service
        val context = service.applicationContext
        val recorder = instance ?: Recorder(engine, RecordingFiles(context), scope, context.getString(R.string.nagmedar_shell_title)) { outcome ->
            if (!cardShown) say(context, outcome)
        }.also {
            instance = it
            // what an earlier run left: kept now
            it.recoverLeftovers(System.currentTimeMillis())
        }
        if (recorder.busy) tick()
    }

    /** The service goes: a recording stops with it and is kept. */
    fun detach() {
        instance?.let { if (it.recording) it.stop(now()) }
        service = null
        if (instance?.busy == true) tick()
    }

    /** Shift+R, the menus' "Record": starts or stops. */
    fun toggle() {
        val recorder = instance ?: return
        recorder.toggle(now(), System.currentTimeMillis())
        tick()
    }

    fun stop() {
        instance?.stop(now())
        tick()
    }

    /** Keeps again, in the folder chosen now, the recording that could not be kept. */
    fun retry() {
        instance?.retry()
        tick()
    }

    internal fun onRecordChanged(recording: Boolean, path: String?) {
        instance?.onRecordChanged(recording, path, now())
        tick()
    }

    internal fun onPlaybackEnded() {
        instance?.onPlaybackEnded(now())
        tick()
    }

    /** The recorder's clock, while it has something to do. */
    private fun tick() {
        val recorder = instance ?: return
        if (ticking?.isActive == true || !recorder.busy) return
        ticking = scope.launch {
            while (recorder.busy) {
                delay(Recorder.TICK_MS)
                recorder.tick(now())
            }
        }
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    /** An outcome said without the card: saved and where, or why not. */
    private fun say(context: Context, outcome: RecordingOutcome) {
        val text = when (outcome) {
            is RecordingOutcome.Saved -> context.getString(R.string.nag_rec_saved_in, outcome.folder)
            is RecordingOutcome.Failed -> context.getString(R.string.nag_rec_failed_because, context.getString(failureText(outcome.reason)))
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** Plays the recording in what the system offers for it (Nağmedar too). */
    fun play(context: Context, saved: RecordingOutcome.Saved) {
        val uri = saved.uri ?: return
        start(context, Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), saved.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Opens the recording's folder in the files app; says so when none opens folders. */
    fun openFolder(context: Context, saved: RecordingOutcome.Saved) {
        val folder = folderUri(saved)
        val opened = folder != null && (
            start(context, Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) ||
                start(context, Intent(Intent.ACTION_VIEW, folder).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            )
        if (!opened) Toast.makeText(context, context.getString(R.string.nag_rec_no_folder_app, saved.folder), Toast.LENGTH_LONG).show()
    }

    /** The folder as a document: the chosen tree's, or the internal storage's Movies/Nağmedar. */
    private fun folderUri(saved: RecordingOutcome.Saved): Uri? = runCatching {
        saved.tree?.let { tree ->
            val uri = Uri.parse(tree)
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } ?: DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "primary:" + saved.folder)
    }.getOrNull()

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.i(TAG, "nothing opens ${intent.data}", e)
        false
    } catch (e: SecurityException) {
        Log.i(TAG, "not allowed to open ${intent.data}", e)
        false
    }

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
}

/** The words for why a recording was not kept. */
fun failureText(reason: RecordingFailure): Int = when (reason) {
    RecordingFailure.NotSupported -> R.string.nag_rec_not_supported
    RecordingFailure.NoSpace -> R.string.nag_rec_no_space
    RecordingFailure.Folder -> R.string.nag_rec_folder_failed
    RecordingFailure.Write -> R.string.nag_rec_write_failed
    RecordingFailure.Empty -> R.string.nag_rec_empty
}
