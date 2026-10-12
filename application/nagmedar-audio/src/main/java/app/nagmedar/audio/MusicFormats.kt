package app.nagmedar.audio

import android.net.Uri
import java.util.Locale

/**
 * What the engine plays (S-901, S-911): the formats its FFmpeg build demuxes and decodes.
 * Anything else, and every stream, stays with libVLC.
 */
object MusicFormats {

    /** File extensions, lower case, without the dot. */
    val extensions: Set<String> = setOf(
        "flac", "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "wave", "aif", "aiff", "aifc", "w64",
        "ape", "wv", "mpc", "tta", "dsf", "dff", "wma", "mka", "caf",
    )

    val mimeTypes: Set<String> = setOf(
        "audio/flac", "audio/x-flac", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/ogg",
        "audio/opus", "audio/vorbis", "audio/wav", "audio/x-wav", "audio/wave", "audio/aiff", "audio/x-aiff",
        "audio/x-ape", "audio/x-wavpack", "audio/x-musepack", "audio/x-tta", "audio/x-dsf", "audio/x-dff", "audio/x-ms-wma",
        "audio/x-matroska",
    )

    /** Whether the engine takes [uri] (a local file or document) with [mimeType], when known. */
    fun canPlay(uri: Uri, mimeType: String?): Boolean {
        if (uri.scheme != null && uri.scheme != "file" && uri.scheme != "content") return false
        val extension = extensionOf(uri)
        if (extension != null && extension in extensions) return true
        if (extension != null) return false
        return mimeType != null && mimeType.lowercase(Locale.ROOT) in mimeTypes
    }

    /** The extension of the last path segment, or null when it has none. */
    fun extensionOf(uri: Uri): String? {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return null
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }
}
