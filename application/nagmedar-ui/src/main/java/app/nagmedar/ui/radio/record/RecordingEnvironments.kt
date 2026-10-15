package app.nagmedar.ui.radio.record

import android.content.Context
import android.net.Uri
import app.nagmedar.ui.player.snapshot.snapshotFolderLabel
import app.nagmedar.ui.sleep.Alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.vlc.media.MediaUtils

/** The device as the Recordings tab sees it: the room, the folder, the alarm permission, the alarms. */
internal class AndroidRecordingEnvironment(private val context: Context) : RecordingEnvironment {

    private val storage = AndroidTrackStorage(context) { RadioRecordings.settings(context).folder }

    override fun freeBytes(): Long = storage.freeBytes()

    override fun folder(): FolderState {
        val tree = RadioRecordings.settings(context).folder ?: return FolderState.Default
        val label = snapshotFolderLabel(tree, tree)
        val uri = Uri.parse(tree)
        val held = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return when {
            !held -> FolderState.NoPermission(label)
            !storage.writable() -> FolderState.Missing(label)
            else -> FolderState.Chosen(label)
        }
    }

    override fun exactAlarms(): Boolean = Alarms.exact(context)

    override fun scheduleSaved(schedule: RecordingSchedule) = RecordingAlarms.set(context, schedule)

    override fun scheduleRemoved(id: Long) = RecordingAlarms.cancel(context, id)

    override suspend fun catchUp() = RecordingAlarms.catchUp(context)
}

/** Tracks played in the normal player, as the library plays files. */
internal class AndroidTrackPlayer(private val context: Context) : TrackPlayer {
    override fun play(tracks: List<RecordedTrack>, index: Int) {
        val list = tracks.map { track ->
            MLServiceLocator.getAbstractMediaWrapper(Uri.parse(track.fileUri)).also {
                it.title = track.displayTitle
            }
        }
        MediaUtils.openList(context, list, index)
    }
}

/** The scope the tab's controller works in. */
internal fun recordingsScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
