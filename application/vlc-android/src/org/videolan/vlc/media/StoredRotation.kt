package org.videolan.vlc.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * The turn a film's picture is stored with, as its video track's matrix says: a phone recording
 * in portrait keeps landscape pictures and asks for a quarter turn clockwise ("rotate 90"). Read
 * by Android's own extractor before libVLC opens the film, from local files and content URIs of
 * the MP4 family (MP4, MOV, 3GP), the containers that carry it. A turned film's pictures go to
 * libVLC's OpenGL output, the one that turns them in this libVLC
 * ([org.videolan.resources.NagmedarVlcOptions.mediaOptions]).
 */
object StoredRotation {

    /**
     * The clockwise degrees the picture of the film at [uri] is turned by to show: 90, 180 or 270;
     * 0 when it shows as stored, or when it is not a local MP4-family file Android can read. Read
     * off the main thread.
     */
    suspend fun degrees(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) { read(context, uri) }

    @WorkerThread
    private fun read(context: Context, uri: Uri): Int {
        if (!isoMedia(context, uri)) return 0
        var retriever: MediaMetadataRetriever? = null
        return try {
            val reader = MediaMetadataRetriever().also { retriever = it }
            if (uri.scheme == "content") reader.setDataSource(context, uri) else reader.setDataSource(uri.path ?: return 0)
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.trim()?.toIntOrNull()
                ?.mod(FULL_TURN)?.takeIf { it in TURNS } ?: 0
        } catch (e: RuntimeException) {
            // IllegalArgumentException and friends: Android cannot read this file
            0
        } finally {
            runCatching { retriever?.release() }
        }
    }

    /** Whether [uri] is a local file or a content URI whose first box is one an MP4-family file starts with. */
    private fun isoMedia(context: Context, uri: Uri): Boolean = try {
        open(context, uri)?.use { input ->
            val head = ByteArray(BOX_HEADER)
            var got = 0
            while (got < head.size) {
                val n = input.read(head, got, head.size - got)
                if (n < 0) break
                got += n
            }
            got == head.size && String(head, TYPE_AT, TYPE_LENGTH, Charsets.ISO_8859_1) in ISO_BOXES
        } ?: false
    } catch (e: IOException) {
        false
    } catch (e: RuntimeException) {
        // SecurityException, IllegalArgumentException: a URI this app may not read
        false
    }

    private fun open(context: Context, uri: Uri): InputStream? = when (uri.scheme) {
        null, "file" -> uri.path?.let(::FileInputStream)
        "content" -> context.contentResolver.openInputStream(uri)
        else -> null
    }

    /** A box starts with its size and its type, four bytes each. */
    private const val BOX_HEADER = 8
    private const val TYPE_AT = 4
    private const val TYPE_LENGTH = 4

    /** The box types an MP4-family file starts with (QuickTime's older files with "wide" or "mdat"). */
    private val ISO_BOXES = setOf("ftyp", "moov", "mdat", "free", "skip", "wide", "pnot")

    private const val FULL_TURN = 360
    private val TURNS = setOf(90, 180, 270)
}
