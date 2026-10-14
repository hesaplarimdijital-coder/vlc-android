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
