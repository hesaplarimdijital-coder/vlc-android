package app.nagmedar.ui.library

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.File

/**
 * 1.0.3: the tracks played last (the phone's shelf over the Music's list), kept on the device by their
 * medialibrary ids, newest first, beside the kept list ([MusicSnapshot]). The next start then shows the
 * shelf in its first frames with the list, instead of the history's read coming after the rows are on
 * screen (where it would either push them down or stay out of sight above them). The real read replaces
 * it as soon as it comes, and is kept in turn.
 */
object RecentKept {

    private const val FILE = "nagmedar_recent_kept"

    private const val TAG = "RecentKept"

    /** The ids read for this process (by [preload] or [read]) or kept last; null until then. */
    @Volatile
    private var loaded: List<Long>? = null

    private val lock = Any()

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE)

    /** The kept ids once they have been read; null before. Never reads the disk: the window's first frame may ask. */
    fun cached(): List<Long>? = loaded

    /** Reads the kept ids into memory, on the calling (background) thread, so that [cached] has them for the first frame. */
    @WorkerThread
    fun preload(context: Context) {
        synchronized(lock) {
            if (loaded != null) return
            loaded = runCatching { file(context).takeIf { it.isFile }?.readText()?.let(::decode) }
                .onFailure { Log.w(TAG, "the kept history could not be read: $it") }
                .getOrNull() ?: emptyList()
        }
    }

    /** The kept ids, read from the disk when they are not in memory yet; empty when none were kept. */
    @WorkerThread
    fun read(context: Context): List<Long> {
        preload(context)
        return loaded.orEmpty()
    }

    /**
     * [ids], just read from the history, kept for the next start (written only when they changed). On the
     * calling (background) thread; a failed write leaves the old file.
     */
    @WorkerThread
    fun save(context: Context, ids: List<Long>) {
        synchronized(lock) {
            if (loaded == ids) return
            val target = file(context)
            val temporary = File(target.parentFile, "$FILE.new")
            val written = runCatching {
                temporary.writeText(encode(ids))
                temporary.renameTo(target)
            }.onFailure { Log.w(TAG, "the history could not be kept: $it") }.getOrDefault(false)
            if (written) loaded = ids else temporary.delete()
        }
    }

    /** For the tests: the next [read] reads the disk again. */
    internal fun forget() {
        synchronized(lock) { loaded = null }
    }

    /** The ids in the file's form: one line, comma separated. */
    internal fun encode(ids: List<Long>): String = ids.joinToString(",")

    /** The ids of [text]; what is not a number is left out. */
    internal fun decode(text: String): List<Long> = text.split(',').mapNotNull { it.trim().toLongOrNull() }

    /**
     * The tracks of [music] (the kept list) that have the kept [ids], in the ids' order; a track that is
     * no longer in the list is left out, one that is there twice comes once.
     */
    fun tracks(ids: List<Long>, music: List<MediaWrapper>): List<MediaWrapper> {
        if (ids.isEmpty() || music.isEmpty()) return emptyList()
        val wanted = ids.toHashSet()
        val found = HashMap<Long, MediaWrapper>(wanted.size * 2)
        for (media in music) {
            val id = media.id
            if (id in wanted && id !in found) found[id] = media
        }
        return ids.distinct().mapNotNull(found::get)
    }
}
