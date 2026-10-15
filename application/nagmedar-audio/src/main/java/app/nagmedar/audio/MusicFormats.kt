package app.nagmedar.audio

import android.net.Uri
import java.util.Locale

/**
 * What the engine plays (S-901, S-911): the formats its FFmpeg build demuxes and decodes, and
 * the tracker modules libopenmpt renders for it. Anything else, and every stream, stays with
 * libVLC.
 */
object MusicFormats {

    /** What the engine calls a tracker module's codec: the module plays, it is not decoded; its kind (mod, xm, it, …) is the container. */
    const val TRACKER = "tracker"

    /**
     * S-911: the tracker modules, by their extensions: what libopenmpt 0.8 lists
     * (openmpt_get_supported_extensions), MOD, XM, S3M, IT and MPTM first.
     */
    val trackerExtensions: Set<String> = setOf(
        "mod", "xm", "s3m", "it", "mptm", "669", "667", "amf", "ams", "c67", "cba", "dbm", "digi", "dmf", "dsm", "dsym", "dtm",
        "etx", "far", "fc", "fc13", "fc14", "fmt", "fst", "ftm", "imf", "ims", "ice", "j2b", "m15", "mdl", "med", "mms", "mt2",
        "mtm", "mus", "nst", "okt", "plm", "psm", "pt36", "ptm", "puma", "rtm", "sfx", "sfx2", "smod", "st26", "stk", "stm",
        "stx", "stp", "symmod", "tcb", "gmc", "gtk", "gt2", "ult", "unic", "wow", "xmf", "gdm", "mo3", "oxm", "umx", "xpk",
        "mmcmp",
    )

    /** File extensions, lower case, without the dot. */
    val extensions: Set<String> = setOf(
        "flac", "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "wave", "aif", "aiff", "aifc", "w64",
        "ape", "wv", "mpc", "tta", "dsf", "dff", "wma", "mka", "caf",
    ) + trackerExtensions

    val mimeTypes: Set<String> = setOf(
        "audio/flac", "audio/x-flac", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/ogg",
        "audio/opus", "audio/vorbis", "audio/wav", "audio/x-wav", "audio/wave", "audio/aiff", "audio/x-aiff",
        "audio/x-ape", "audio/x-wavpack", "audio/x-musepack", "audio/x-tta", "audio/x-dsf", "audio/x-dff", "audio/x-ms-wma",
        "audio/x-matroska",
        "audio/x-mod", "audio/mod", "audio/x-xm", "audio/xm", "audio/x-s3m", "audio/s3m", "audio/x-it", "audio/it",
    )

    /** Whether the engine takes [uri] (a local file or document) with [mimeType], when known. */
    fun canPlay(uri: Uri, mimeType: String?): Boolean {
        if (uri.scheme != null && uri.scheme != "file" && uri.scheme != "content") return false
        val extension = extensionOf(uri)
        if (extension != null && extension in extensions) return true
        if (extension != null) return false
        return mimeType != null && mimeType.lowercase(Locale.ROOT) in mimeTypes
    }

    /** S-911: whether [uri] names a tracker module, by its extension. */
    fun isTracker(uri: Uri): Boolean = extensionOf(uri)?.let { it in trackerExtensions } ?: false

    /** S-1302: whether [uri] names a DSD file (DSF, DSDIFF), by its extension: libVLC sends it to a renderer as PCM. */
    fun isDsd(uri: Uri): Boolean = extensionOf(uri)?.let { it == "dsf" || it == "dff" } ?: false

    /** The extension of the last path segment, or null when it has none. */
    fun extensionOf(uri: Uri): String? {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return null
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }
}
