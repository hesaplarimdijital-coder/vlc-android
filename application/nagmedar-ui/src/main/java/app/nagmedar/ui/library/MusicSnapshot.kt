package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 1.0.3: the Music's list as it was last shown, kept on the device (the app's no-backup files) so
 * that the next start draws it in its first frames instead of waiting for the medialibrary:
 * "opening the app shows the music list at once, as it was last time". The real read replaces it
 * as soon as it comes, and is kept in turn. Each track keeps what the list and its playback need
 * (its medialibrary id, address, tags, length, artwork); a list kept for other folders ([scope])
 * or by another version of this format is not used.
 */
object MusicSnapshot {

    /** Bump when a track keeps more or less: a file of another version is read as nothing. */
    private const val VERSION = 1

    private const val MAGIC = 0x4e4d5331 // "NMS1"

    private const val FILE = "nagmedar_music_snapshot"

    private const val TAG = "MusicSnapshot"

    /** A list that size or larger is not kept (a file of a few MB at most). */
    private const val MAX_TRACKS = 200_000

    /** The list read for this process (by [preload] or the first [read]), and its scope; null until then. */
    @Volatile
    private var loaded: Pair<String, MusicRead>? = null

    /** What the file holds now ([signature]), to write it again only when the list changed. */
    @Volatile
    private var savedSignature: Long? = null

    private val lock = Any()

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE)

    /**
     * The kept list when it was kept for [scope] and has been read already; null otherwise. Never
     * reads the disk: the window's first frame may ask (see [preload]).
     */
    fun cached(scope: String): MusicRead? = loaded?.takeIf { it.first == scope }?.second

    /** Reads the kept list into memory, on the calling (background) thread, so that [cached] has it for the first frame. */
    @WorkerThread
    fun preload(context: Context) {
        synchronized(lock) {
            if (loaded != null) return
            val (scope, tracks) = runCatching { file(context).takeIf { it.isFile }?.inputStream()?.use(::decode) }
                .onFailure { Log.w(TAG, "the kept music list could not be read: $it") }
                .getOrNull() ?: return
            val read = MusicRead(tracks)
            loaded = scope to read
            savedSignature = signature(scope, read)
        }
    }

    /** The kept list for [scope], read from the disk when it is not in memory yet; null when none was kept for it. */
    @WorkerThread
    fun read(context: Context, scope: String): MusicRead? {
        preload(context)
        return cached(scope)
    }

    /**
     * [read], just made for [scope], kept for the next start (written only when it differs from
     * what is kept). On the calling (background) thread; a failed write leaves the old file.
     */
    @WorkerThread
    fun save(context: Context, scope: String, read: MusicRead) {
        if (read.media.size > MAX_TRACKS) return
        val signature = signature(scope, read)
        synchronized(lock) {
            loaded = scope to read
            if (savedSignature == signature) return
            val target = file(context)
            val temporary = File(target.parentFile, "$FILE.new")
            val written = runCatching {
                temporary.outputStream().use { encode(it, scope, read.media) }
                temporary.renameTo(target)
            }.onFailure { Log.w(TAG, "the music list could not be kept: $it") }.getOrDefault(false)
            if (written) savedSignature = signature else temporary.delete()
        }
    }

    /** What [save] compares: the scope and what the list shows. */
    private fun signature(scope: String, read: MusicRead): Long = 31L * scope.hashCode() + read.fingerprint

    // ------------------------------------------------------------------ the format

    /** The list in the file's form: magic, version, scope, count, then each track's fields. */
    internal fun encode(output: OutputStream, scope: String, tracks: List<MediaWrapper>) {
        val out = DataOutputStream(BufferedOutputStream(output, 64 * 1024))
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeUTF(scope)
        out.writeInt(tracks.size)
        for (media in tracks) {
            out.writeLong(media.id)
            out.writeString(media.uri?.toString())
            out.writeInt(media.type)
            out.writeString(media.title)
            out.writeString(media.fileName)
            out.writeString(media.artistName)
            out.writeString(media.albumArtistName)
            out.writeString(media.albumName)
            out.writeString(media.genre)
            out.writeLong(media.artistId)
            out.writeLong(media.albumArtistId)
            out.writeLong(media.albumId)
            out.writeLong(media.length)
            out.writeInt(media.trackNumber)
            out.writeInt(media.discNumber)
            out.writeInt(media.releaseYear)
            out.writeLong(media.lastModified)
            out.writeLong(media.insertionDate)
            // the artwork's path, kept as a file address: read back, the constructor turns it into the same path
            out.writeString(media.artworkMrl?.let { if (it.startsWith("/")) Uri.fromFile(File(it)).toString() else it })
            out.writeBoolean(media.isPresent)
            out.writeBoolean(media.isFavorite)
        }
        out.flush()
    }

    /** The scope and the tracks of [input]; null when it is not this version's file or it ends early. */
    internal fun decode(input: InputStream): Pair<String, List<MediaWrapper>>? {
        val data = DataInputStream(BufferedInputStream(input, 64 * 1024))
        if (data.readInt() != MAGIC || data.readInt() != VERSION) return null
        val scope = data.readUTF()
        val count = data.readInt()
        if (count !in 0..MAX_TRACKS) return null
        val tracks = ArrayList<MediaWrapper>(count)
        repeat(count) {
            val id = data.readLong()
            val uri = data.readString()
            val type = data.readInt()
            val title = data.readString()
            val fileName = data.readString()
            val artist = data.readString()
            val albumArtist = data.readString()
            val album = data.readString()
            val genre = data.readString()
            val artistId = data.readLong()
            val albumArtistId = data.readLong()
            val albumId = data.readLong()
            val length = data.readLong()
            val trackNumber = data.readInt()
            val discNumber = data.readInt()
            val releaseYear = data.readInt()
            val lastModified = data.readLong()
            val insertionDate = data.readLong()
            val artwork = data.readString()
            val present = data.readBoolean()
            val favorite = data.readBoolean()
            if (uri.isNullOrEmpty()) return@repeat
            tracks += MLServiceLocator.getAbstractMediaWrapper(
                id, uri, 0L, 0f, length, type, title, fileName, artistId, albumArtistId, artist, genre, albumId, album, albumArtist,
                0, 0, artwork, -2, -2, trackNumber, discNumber, lastModified, 0L, false, favorite, releaseYear, present, insertionDate,
            )
        }
        return scope to tracks
    }

    /** A string or null: a flag, then modified UTF-8 (a value longer than its 64 kB limit is kept cut). */
    private fun DataOutputStream.writeString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(if (value.length > 16_000) value.substring(0, 16_000) else value)
    }

    private fun DataInputStream.readString(): String? = if (readBoolean()) readUTF() else null

    /** Forgets the list of this process (tests). */
    internal fun forget() {
        synchronized(lock) {
            loaded = null
            savedSignature = null
        }
    }
}

/**
 * What the Music's list shows of a track, as one number: two reads of the library whose tracks have
 * the same fingerprints show the same list. [MediaWrapper]'s own equality is its id, which a track
 * keeps when its tags change, so a new album name would otherwise not reach the list. The file's
 * modification time is part of it: a file changed in place is probed again (TrackTechIndex).
 */
object MusicFingerprint {
    fun of(media: MediaWrapper): Int {
        var hash = media.id.hashCode()
        hash = 31 * hash + (media.uri?.toString()?.hashCode() ?: 0)
        hash = 31 * hash + media.type
        hash = 31 * hash + (media.title?.hashCode() ?: 0)
        hash = 31 * hash + (media.artistName?.hashCode() ?: 0)
        hash = 31 * hash + (media.albumArtistName?.hashCode() ?: 0)
        hash = 31 * hash + (media.albumName?.hashCode() ?: 0)
        hash = 31 * hash + (media.genre?.hashCode() ?: 0)
        hash = 31 * hash + media.length.hashCode()
        hash = 31 * hash + media.trackNumber
        hash = 31 * hash + media.discNumber
        hash = 31 * hash + media.releaseYear
        hash = 31 * hash + media.insertionDate.hashCode()
        hash = 31 * hash + media.lastModified.hashCode()
        hash = 31 * hash + (media.artworkMrl?.hashCode() ?: 0)
        hash = 31 * hash + (if (media.isPresent) 1 else 0)
        return hash
    }

    /** One number for a whole list, in its order. */
    fun of(list: List<MediaWrapper>): Long {
        var hash = 1L
        for (media in list) hash = 31 * hash + of(media)
        return 31 * hash + list.size
    }
}
