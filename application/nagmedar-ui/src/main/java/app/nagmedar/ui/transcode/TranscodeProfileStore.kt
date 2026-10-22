package app.nagmedar.ui.transcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.TranscodeProfileDao
import app.nagmedar.data.TranscodeProfileEntry
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.shell.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION

/**
 * S-1303: the user's conversion profiles in Nağmedar's database, after the presets. A profile is
 * saved only when it keeps the rules ([TranscodeProfiles.validate]); a name is used once.
 */
class TranscodeProfileStore(private val dao: TranscodeProfileDao, private val now: () -> Long = System::currentTimeMillis) {

    /** The presets, then the user's profiles by name. */
    fun observe(): Flow<List<TranscodeProfile>> = dao.observe().map { rows -> TranscodeProfiles.presets + rows.mapNotNull(::toProfile) }

    /** Saves [profile] (a new one when its id is 0); its id, or the problems that keep it from being saved. */
    suspend fun save(profile: TranscodeProfile): Result<Long> = withContext(Dispatchers.IO) {
        val others = dao.all().filter { it.id != profile.id }.map { it.name } + TranscodeProfiles.presets.map { it.name }
        val problems = TranscodeProfiles.validate(profile, others)
        if (problems.isNotEmpty()) return@withContext Result.failure(ProfileRejected(problems))
        val existing = profile.id.takeIf { it > 0 }?.let(dao::get)
        val entry = toEntry(profile.copy(name = profile.name.trim()), createdAt = existing?.createdAt ?: now(), updatedAt = now())
        if (existing == null) Result.success(dao.insert(entry.copy(id = 0))) else Result.success(existing.id.also { dao.update(entry) })
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { if (id > 0) dao.delete(id) }

    /** The problems [save] found. */
    class ProfileRejected(val problems: List<ProfileProblem>) : IllegalArgumentException(problems.joinToString())

    companion object {
        fun get(context: Context) = TranscodeProfileStore(NagmedarDatabase.get(context).transcodeProfiles())

        /** A row as a profile; null when it names what this build does not know. */
        fun toProfile(entry: TranscodeProfileEntry): TranscodeProfile? = runCatching {
            TranscodeProfile(
                id = entry.id,
                name = entry.name,
                container = TranscodeContainer.valueOf(entry.container),
                video = entry.videoCodec?.let { VideoSettings(VideoCodec.valueOf(it), entry.videoKbps, entry.maxHeight.takeIf { h -> h > 0 }, entry.fps.takeIf { f -> f > 0 }) },
                audio = entry.audioCodec?.let { AudioSettings(AudioCodec.valueOf(it), entry.audioKbps, entry.channels, entry.sampleRate.takeIf { r -> r > 0 }) },
                subtitles = SubtitleMode.valueOf(entry.subtitles),
            )
        }.getOrNull()

        fun toEntry(profile: TranscodeProfile, createdAt: Long, updatedAt: Long) = TranscodeProfileEntry(
            id = profile.id.coerceAtLeast(0),
            name = profile.name,
            container = profile.container.name,
            videoCodec = profile.video?.codec?.name,
            videoKbps = profile.video?.bitrateKbps ?: 0,
            maxHeight = profile.video?.maxHeight ?: 0,
            fps = profile.video?.fps ?: 0,
            audioCodec = profile.audio?.codec?.name,
            audioKbps = profile.audio?.bitrateKbps ?: 0,
            channels = profile.audio?.channels ?: 2,
            sampleRate = profile.audio?.sampleRate ?: 0,
            subtitles = profile.subtitles.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

/** S-1303: a medium to convert or stream: its address, the name shown, its size when known. */
@Immutable
data class TranscodeMedia(val uri: Uri, val name: String, val size: Long = 0) {
    /** The name without its extension: the output's suggested name. */
    val baseName: String get() = name.substringBeforeLast('.').ifBlank { name }

    companion object {
        /** A document the system's picker gave: its display name and size from the provider. */
        fun ofDocument(context: Context, uri: Uri): TranscodeMedia {
            var name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
            var size = 0L
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            }
            return TranscodeMedia(uri, name, size)
        }
    }
}

/**
 * S-1303: what the player, the library's menus and the keys ask the convert page for: a media to
 * convert (Ctrl+R) or to stream on the network (Ctrl+Shift+H), none to pick one. The page takes it when
 * it shows.
 */
object TranscodeRequests {
    enum class Kind { Convert, Stream }

    @Immutable
    data class Request(val kind: Kind, val media: TranscodeMedia?)

    // made in the global snapshot: first read inside a window's composition
    var pending by Snapshot.global { mutableStateOf<Request?>(null) }

    /** Asks for [kind] of [media] and shows the convert page (in a window of the library's, from the player). */
    fun open(context: Context, kind: Kind, media: TranscodeMedia?) {
        pending = Request(kind, media)
        val show = Intent(context, NagmedarActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.Convert.route)
        if (context !is android.app.Activity) show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(show) }
    }
}
