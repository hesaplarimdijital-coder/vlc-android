package app.nagmedar.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.nagmedar.audio.cue.CueUri
import java.io.File

/**
 * What a file's tags hold besides what the medialibrary reads, through TagLib: its lyrics
 * (S-1003, [TagLyrics]), its front cover and any tag by name (S-1006). Each read opens the file:
 * off the main thread. Null when the engine is missing or the file cannot be read.
 */
object FileTags {
    private const val TAG = "NagmedarAudio"

    /** The front cover in [uri]'s tags (else its first picture), as stored: JPEG, PNG… */
    fun cover(context: Context, uri: Uri): ByteArray? = read(context, uri) { fd, name -> NativeAudio.readCover(fd, name) }

    /** The first value of [uri]'s tag [key], by TagLib's property name ("MUSICBRAINZ_ALBUMID"). */
    fun tag(context: Context, uri: Uri, key: String): String? = read(context, uri) { fd, name -> NativeAudio.readTag(fd, name, key) }

    /**
     * [block] on a read-write descriptor of [track] and its name (S-1005: the lyrics editor writes tags), guarded by
     * [TagBackups]: a copy of the file is kept while it is written and put back if the file does not read as it was.
     * Only a file of its own: a CUE track (S-808) shares its file with the others and is not written. [TagWrite.Failed]
     * when the engine is missing or the file cannot be opened for writing.
     */
    internal fun write(context: Context, track: Uri, block: (fd: Int, name: String) -> Boolean): TagWrite {
        if (!NativeAudio.available || CueUri.isTrack(track)) return TagWrite.Failed
        val app = context.applicationContext
        val descriptor = runCatching { openWritable(app, track) }
            .onFailure { Log.w(TAG, "cannot write the tags of $track: $it") }.getOrNull() ?: return TagWrite.Failed
        return descriptor.use { TagBackups.guarded(app, track, it, LoudnessScanner.displayName(app, track), block) }
    }

    /** A read-write descriptor of [track]'s own file; null for a CUE track or an address that is not a file. Throws when it cannot be opened. */
    internal fun openWritable(context: Context, track: Uri): ParcelFileDescriptor? {
        if (CueUri.isTrack(track)) return null
        return when (track.scheme) {
            null, "file" -> track.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_WRITE) }
            "content" -> context.contentResolver.openFileDescriptor(track, "rw")
            else -> null
        }
    }

    /**
     * S-1005: what a tag write left when the process died during it: the copy deleted when the file reads as it was, put
     * back when that write left the file damaged; the copy of a file changed elsewhere since, or not found for a week,
     * moved into [keepIn] (a folder the user sees). Returns the copies moved there, for the user to be told. Called at
     * the app's start; reads, decodes and writes files: off the main thread.
     */
    fun recover(context: Context, keepIn: File?): List<File> = TagBackups.recover(context, keepIn)

    /** [block] on a read-only descriptor of [uri] and its name (the type detection's); a CUE track's file (S-808) is its own. */
    internal fun <T> read(context: Context, track: Uri, block: (fd: Int, name: String) -> T?): T? {
        if (!NativeAudio.available) return null
        val app = context.applicationContext
        val uri = CueUri.file(track)
        val descriptor = runCatching {
            when (uri.scheme) {
                null, "file" -> uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
                "content" -> app.contentResolver.openFileDescriptor(uri, "r")
                else -> null
            }
        }.onFailure { Log.w(TAG, "cannot read the tags of $uri: $it") }.getOrNull() ?: return null
        return descriptor.use { block(it.fd, LoudnessScanner.displayName(app, uri)) }
    }
}
