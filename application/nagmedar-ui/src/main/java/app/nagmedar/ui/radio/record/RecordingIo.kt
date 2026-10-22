package app.nagmedar.ui.radio.record

import java.io.Closeable
import java.io.IOException

/** S-1107: the bytes of a station's stream, as they come. */
interface StreamSource : Closeable {
    /** Reads up to [buffer]'s size into it, blocking; the count, or -1 at the end. Throws [IOException]; [close] from another thread ends a read that waits. */
    fun read(buffer: ByteArray): Int
}

/**
 * A stream that was opened: what it is ([contentType], the `icy-name` the server gave), how often
 * its metadata comes ([metaInt], 0: it sends none) and where its bytes come from. An HLS stream
 * has no bytes to read: its [playlist] is read by an [HlsReader].
 */
class OpenedStream(
    val contentType: String?,
    val metaInt: Int,
    val source: StreamSource,
    val serverName: String? = null,
    val playlist: HlsStart? = null,
)

/** The stream cannot be recorded: see [reason]. */
class StreamUnsupported(val reason: UnsupportedStream) : IOException("unsupported stream: $reason")

/** The server answered with this HTTP [status] instead of a stream. */
class StreamRefused(val status: Int) : IOException("HTTP $status")

/** S-1107: opens a station's stream with a connection of its own (the player's is never touched): a real one in the app, a fake in the tests. */
fun interface StreamOpener {
    /** Opens the stream at [url]; throws [StreamUnsupported], [StreamRefused] or another [IOException]. */
    suspend fun open(url: String): OpenedStream
}

/** A file being written. */
interface TrackOutput {
    /** The file's address. */
    val uri: String

    /** The file's name as the storage gave it. */
    val name: String

    fun write(data: ByteArray, offset: Int, length: Int)

    /** Bytes written. */
    val size: Long

    /** Room left where the file is, bytes; negative when not known. */
    fun freeBytes(): Long

    /** Finishes the file. */
    fun close()

    /** Takes the file away (it was too short). */
    fun delete()
}

/** S-1107: where recordings are written: the folder chosen, or the app's own; fakes in the tests. */
interface TrackStorage {
    /** Whether files can be written there now (the folder exists and the permission is still held). */
    fun writable(): Boolean

    /** The lower-cased names of the files already in [folder] (a station's folder). */
    fun names(folder: String): Set<String>

    /** Creates the file [name] in [folder], which is made if it is not there; throws [IOException]. */
    fun create(folder: String, name: String, mime: String): TrackOutput

    /** Room left where recordings go, bytes; negative when not known. */
    fun freeBytes(): Long
}

/** S-1107: what a session needs of the recordings kept: to add a saved track and to know what the others take. */
interface SessionLibrary {
    suspend fun add(track: RecordedTrack): Long

    /** What the tracks kept take together, bytes. */
    suspend fun usedBytes(): Long

    /** Deletes the oldest tracks (other than those of [keep]'s sessions) to free [bytes]; returns what was freed. */
    suspend fun prune(bytes: Long, keep: Set<String>): Long
}
