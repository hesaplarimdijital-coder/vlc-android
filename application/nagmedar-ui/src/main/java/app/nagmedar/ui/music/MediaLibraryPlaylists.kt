package app.nagmedar.ui.music

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.resources.util.getFromMl
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * S-804: the playlists the medialibrary keeps, the classic interface's and the playlist files a
 * scan found among them. Missing files are listed too, so that positions are the medialibrary's.
 */
class MediaLibraryPlaylists(private val context: Context) : PlaylistStore {

    private val changeCount = MutableStateFlow(0)
    override val changes: StateFlow<Int> get() = changeCount

    /** The medialibrary's items of the tracks read, by uri: what playing a line needs. */
    val media: MutableMap<String, MediaWrapper> = ConcurrentHashMap()

    private val listener = object : Medialibrary.PlaylistsCb {
        override fun onPlaylistsAdded() = changeCount.update { it + 1 }

        override fun onPlaylistsModified() = changeCount.update { it + 1 }

        override fun onPlaylistsDeleted() = changeCount.update { it + 1 }
    }

    /** Listens to the medialibrary's playlist changes; the returned call stops. */
    fun listen(): () -> Unit {
        val library = Medialibrary.getInstance()
        library.addPlaylistCb(listener)
        return { library.removePlaylistCb(listener) }
    }

    private suspend inline fun <reified T> playlist(id: Long, crossinline block: Playlist.() -> T): T? = context.getFromMl { getPlaylist(id, true, false)?.block() }

    override suspend fun playlists(): List<PlaylistSummary> = context.getFromMl {
        getPlaylists(Playlist.Type.All, Medialibrary.SORT_INSERTIONDATE, false, true, false)
            ?.map { PlaylistSummary(it.id, it.title.orEmpty(), it.tracksCount, it.duration) }
            .orEmpty()
    }

    override suspend fun tracks(id: Long): List<Track> {
        val items = playlist(id) { getTracks(true, false)?.toList() }.orEmpty()
        val tech = TrackTechIndex.tech.value
        return items.map { item ->
            val uri = item.uri?.toString().orEmpty()
            media[uri] = item
            item.toTrack(tech[uri])
        }
    }

    override suspend fun create(name: String, trackIds: List<Long>): Long? = context.getFromMl {
        createPlaylist(name, true, false)?.let { created ->
            if (trackIds.isNotEmpty()) created.append(trackIds)
            created.id
        }
    }

    override suspend fun rename(id: Long, name: String) {
        playlist(id) { setName(name) }
    }

    override suspend fun delete(id: Long) {
        playlist(id) { delete() }
    }

    /** Out of its place and in at the new one: the medialibrary's own move counts its target otherwise. */
    override suspend fun move(id: Long, from: Int, to: Int) {
        playlist(id) {
            val moved = getTracks(true, false)?.getOrNull(from) ?: return@playlist
            if (remove(from)) add(moved.id, to)
        }
    }

    override suspend fun remove(id: Long, positions: List<Int>) {
        playlist(id) { positions.sortedDescending().forEach { remove(it) } }
    }

    override suspend fun insert(id: Long, position: Int, trackId: Long) {
        playlist(id) { add(trackId, position) }
    }

    override suspend fun append(id: Long, trackIds: List<Long>) {
        playlist(id) { append(trackIds) }
    }

    // ------------------------------------------------------------- files

    /** What reading a playlist file gave: the new playlist, how many lines it found and how many not. */
    class Imported(val id: Long, val name: String, val found: Int, val missing: Int)

    /**
     * The playlist file at [uri] read into a new playlist, named after the file ([taken] names
     * avoided); its lines found in the library ([PlaylistMatcher]), a file outside it or a stream
     * added to the medialibrary as the classic interface does. Null when the file cannot be read.
     */
    suspend fun import(uri: Uri, taken: Collection<String>): Imported? {
        val fileName = withContext(Dispatchers.IO) { displayName(context, uri) } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
        val bytes = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes(MAX_FILE_BYTES) } }.getOrNull()
        } ?: return null
        val format = PlaylistFormat.of(fileName) ?: PlaylistFiles.sniff(bytes) ?: return null
        val entries = PlaylistFiles.parse(PlaylistFiles.decode(bytes, format), format, localFolder(uri).orEmpty())
        val ids = context.getFromMl {
            val library = getAudio(Medialibrary.SORT_DEFAULT, false, true, false).orEmpty().toList() +
                getVideos(Medialibrary.SORT_DEFAULT, false, true, false).orEmpty().toList()
            val matcher = PlaylistMatcher(library.map { it.uri?.takeIf { u -> u.scheme == "file" }?.path.orEmpty() })
            entries.map { entry ->
                val location = entry.location
                val found = matcher.match(location)?.let { library[it] }
                when {
                    found != null -> found.id
                    location.startsWith("/") && File(location).isFile -> {
                        val mrl = Uri.fromFile(File(location))
                        (getMedia(mrl) ?: addMedia(mrl.toString(), entry.durationMs.takeIf { it > 0 } ?: -1L))?.id
                    }
                    Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""").containsMatchIn(location) && !location.startsWith("file:", ignoreCase = true) ->
                        (getMedia(location) ?: addStream(location, entry.title ?: location))?.id
                    else -> null
                }?.takeIf { it > 0 }
            }
        }
        val name = PlaylistsState.uniqueName(fileName.substringBeforeLast('.').ifBlank { fileName }, taken)
        val found = ids.filterNotNull()
        val id = create(name, found) ?: return null
        return Imported(id, name, found.size, ids.size - found.size)
    }

    /** The playlist of [id] written to [uri] as [format]; relative paths where [uri] is a folder's file. */
    suspend fun export(id: Long, name: String, uri: Uri, format: PlaylistFormat): Boolean {
        val items = playlist(id) { getTracks(true, false)?.toList() }.orEmpty()
        val entries = items.map { item ->
            val location = item.uri?.let { if (it.scheme == "file") it.path else it.toString() }.orEmpty()
            PlaylistEntry(location, item.title, item.artistName, item.albumName, item.length)
        }
        val text = PlaylistFiles.write(entries, format, localFolder(uri), name)
        return withContext(Dispatchers.IO) {
            runCatching {
                val stream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: context.contentResolver.openOutputStream(uri)
                stream?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
        }
    }

    /** The folder of a file picked or made with the system's picker, where it says one: its own storage's files. */
    private fun localFolder(uri: Uri): String? = when {
        uri.scheme == "file" -> uri.path?.substringBeforeLast('/')
        uri.authority == EXTERNAL_STORAGE -> runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()?.let { id ->
            val volume = id.substringBefore(':')
            val root = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
            "$root/${id.substringAfter(':', "")}".trimEnd('/').substringBeforeLast('/')
        }
        else -> null
    }

    companion object {
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

        /** A playlist file is text: past this it is something else. */
        private const val MAX_FILE_BYTES = 8 * 1024 * 1024

        private fun displayName(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

        private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > limit) error("too large")
            }
            return out.toByteArray()
        }
    }
}
