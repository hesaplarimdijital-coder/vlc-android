package app.nagmedar.ui.radio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import app.nagmedar.ui.R
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.radio.record.RadioRecordings
import app.nagmedar.ui.radio.record.RecordedTrack
import app.nagmedar.ui.radio.record.forgetRadioRecordingFolder
import app.nagmedar.ui.radio.record.keepRadioRecordingFolder
import app.nagmedar.ui.sleep.Alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.vlc.BuildConfig
import java.io.ByteArrayOutputStream

/**
 * S-1105: the app's one radio: the directory client, the stations kept, the player and the
 * controller the screen reads. It outlives the screen, so a search and its place in the list stay
 * while another screen shows.
 */
object RadioCenter {

    /**
     * The directory (DNS and HTTP) and the logos are asked only in the app; tests and previews are
     * offline: the directory answers [RadioFailure.Offline] and no logo is fetched. It is set
     * before the first screen asks for [controller].
     */
    @Volatile
    var active = false
        set(value) {
            field = value
            RadioLogos.active = value
        }

    @Volatile
    private var controller: RadioController? = null

    fun controller(context: Context): RadioController = controller ?: synchronized(this) {
        controller ?: build(context.applicationContext).also { controller = it }
    }

    /** The directory to ask: the real one in the app, one that is never reachable otherwise. */
    internal fun directory(active: Boolean, version: String?): RadioDirectory =
        if (active) OkHttpRadioBrowser.create(version) else OfflineRadioDirectory

    @Volatile
    private var shared: Parts? = null

    /** The directory, the stations kept and the player the app has one of. */
    private class Parts(val directory: RadioDirectory, val library: RadioLibrary, val player: RadioPlayer)

    private fun parts(context: Context): Parts = shared ?: synchronized(this) {
        shared ?: run {
            RadioLogos.identify(BuildConfig.VLC_VERSION_NAME)
            val directory = directory(active, BuildConfig.VLC_VERSION_NAME)
            val library = DatabaseRadioLibrary(context.applicationContext)
            Parts(directory, library, RadioPlayer(directory, library, ServiceStationSink(context.applicationContext)))
        }.also { shared = it }
    }

    /** The player: S-1107 asks it for the address a station plays from, as the recordings must read the same stream. */
    internal fun player(context: Context): RadioPlayer = parts(context).player

    private fun build(context: Context): RadioController {
        val parts = parts(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        return RadioController(parts.directory, parts.library, parts.player, scope, playingNow = RadioNowPlaying.flow())
    }
}

/** A directory that cannot be reached: what tests and previews get, so nothing touches the network. */
internal object OfflineRadioDirectory : RadioDirectory {
    override suspend fun search(query: StationQuery): List<Station> = throw RadioFailure.Offline()

    override suspend fun countries(): List<DirectoryCountry> = throw RadioFailure.Offline()

    override suspend fun languages(): List<DirectoryLanguage> = throw RadioFailure.Offline()

    override suspend fun tags(): List<DirectoryTag> = throw RadioFailure.Offline()

    override suspend fun click(uuid: String): StationClick = throw RadioFailure.Offline()

    override suspend fun vote(uuid: String): VoteResult = throw RadioFailure.Offline()
}

/** The radio screen in the app: its controller, the clipboard for a stream's address, the picker for playlist files and play or pause. */
@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    searchRequested: Boolean = false,
    onSearchHandled: () -> Unit = {},
    recordingsRequested: Boolean = false,
    onRecordingsHandled: () -> Unit = {},
    undo: UndoBarState? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RadioCenter.controller(context) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { readPlaylist(context, uri) }
            if (file == null) controller.importUnreadable() else controller.import(file.first, file.second)
        }
    }
    val prefs = rememberNagmedarPreferences()
    val recordings = remember(context) { RadioRecordings.controller(context) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            keepRadioRecordingFolder(context, prefs, uri)
            recordings.refresh()
        }
    }
    val actions = remember(context) {
        RecordingActions(
            onShare = { track -> shareTrack(context, track) },
            onChooseFolder = { runCatching { folderPicker.launch(null) } },
            onUseDefaultFolder = {
                forgetRadioRecordingFolder(context, prefs)
                recordings.refresh()
            },
            onAllowExact = { Alarms.requestExact(context) },
        )
    }
    RadioPage(
        controller = controller,
        modifier = modifier,
        searchRequested = searchRequested,
        onSearchHandled = onSearchHandled,
        recordings = recordings,
        recordingActions = actions,
        recordingsRequested = recordingsRequested,
        onRecordingsHandled = onRecordingsHandled,
        onCopyAddress = { station ->
            clipboard.setText(AnnotatedString(station.streamUrl))
            Toast.makeText(context, R.string.nag_radio_copied, Toast.LENGTH_SHORT).show()
        },
        onImport = { importer.launch(arrayOf("*/*")) },
        onTogglePlay = { PlaybackActions.togglePlayPause() },
        undo = undo,
    )
}

/** S-207: the track's file offered to other apps, as audio. */
private fun shareTrack(context: Context, track: RecordedTrack) {
    val uri = RadioRecordings.shareable(context, track) ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(if (track.format == "mp3") "audio/mpeg" else "audio/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, track.displayTitle)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, track.displayTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The bytes (at most one more than a station list may have) and the name of the file at [uri]; null when it cannot be read. The file is only read. */
private fun readPlaylist(context: Context, uri: Uri): Pair<ByteArray, String>? = try {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (total <= StationPlaylists.MAX_BYTES) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        out.toByteArray() to name
    }
} catch (e: Exception) {
    // a provider may fail in more ways than the I/O ones (a revoked grant, a call it does not support)
    null
}
