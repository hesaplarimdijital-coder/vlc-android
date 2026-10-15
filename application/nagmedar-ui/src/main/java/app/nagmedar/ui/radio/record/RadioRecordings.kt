package app.nagmedar.ui.radio.record

import android.content.Context
import android.net.Uri
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.radio.RadioCenter
import app.nagmedar.ui.radio.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig

/**
 * S-1107: the app's one radio recorder: the recordings going on, the tracks and schedules kept.
 * It outlives every screen (the recordings run in [RecordingService], not in a window) and is
 * built on first use, by a screen, the service or an alarm.
 */
object RadioRecordings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var kept: RecordingStore? = null

    @Volatile
    private var running: RecordingCenter? = null

    /** The database of the tracks and schedules. */
    fun store(context: Context): RecordingStore = kept ?: synchronized(this) {
        kept ?: RecordingStore(NagmedarDatabase.get(context).radioRecordings(), AndroidTrackFiles(context.applicationContext)).also { kept = it }
    }

    /** An address another app may read [track]'s file at; null when there is none. */
    fun shareable(context: Context, track: RecordedTrack): Uri? = AndroidTrackFiles(context.applicationContext).shareable(track.fileUri)

    /** The settings as they are now. */
    fun settings(context: Context): RecordingSettings = RecordingPrefs.read(Settings.getInstance(context.applicationContext))

    fun center(context: Context): RecordingCenter = running ?: synchronized(this) {
        running ?: build(context.applicationContext).also { running = it }
    }

    private fun build(context: Context): RecordingCenter {
        val library = store(context)
        val storage = AndroidTrackStorage(context) { settings(context).folder }
        val opener = OkHttpStreamOpener.create(BuildConfig.VLC_VERSION_NAME)
        val hub = RecordingHub(
            create = { request -> RecordingSession(request, opener, storage, library, settings = { settings(context) }) },
            scope = scope,
        )
        return RecordingCenter(
            hub = hub,
            scope = scope,
            resolve = { station -> RadioCenter.player(context).resolve(Station(uuid = station.uuid, name = station.name, streamUrl = station.streamUrl)) },
            hold = { RecordingService.start(context) },
            alerts = RecordingNotifications.alerts(context),
        )
    }
}
