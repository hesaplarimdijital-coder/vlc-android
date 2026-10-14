package app.nagmedar.audio.cue

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.nagmedar.audio.FolderFiles
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * S-808: how a CUE sheet's track is told apart from its file: the file's uri with the fragment
 * "cue=<track number>" ("file:///…/album.flac#cue=3"). The library, the queue, the ratings and
 * the covers all know the track by this; the engine plays the part the sheet gives it.
 */
object CueUri {
    private const val KEY = "cue="

    /** The uri of track [number] of the sheet beside [file]: the fragment as written, "cue=3", not "cue%3D3". */
    fun of(file: Uri, number: Int): Uri = file.buildUpon().encodedFragment("$KEY$number").build()

    /** The track's number when [uri] names a CUE sheet's track; null otherwise. */
    fun trackNumber(uri: Uri?): Int? = number(uri?.fragment)

    fun trackNumber(uri: String?): Int? = number(uri?.substringAfter('#', ""))

    /** "cue=3" (or "cue%3D3", as a uri written by other hands may encode it) → 3. */
    private fun number(fragment: String?): Int? =
        fragment?.replace("%3D", "=", ignoreCase = true)?.takeIf { it.startsWith(KEY) }?.substring(KEY.length)?.toIntOrNull()

    fun isTrack(uri: Uri?): Boolean = trackNumber(uri) != null

    fun isTrack(uri: String?): Boolean = trackNumber(uri) != null

    /** The file the track is in: [uri] without its fragment; [uri] itself when it has none. */
    fun file(uri: Uri): Uri = if (uri.fragment == null) uri else uri.buildUpon().fragment(null).build()

    fun file(uri: String): String = uri.substringBefore('#')
}

/** A CUE sheet read from a file, with where it was found. */
data class FoundSheet(val uri: String, val sheet: CueSheet)

/**
 * S-808: the sheets beside the music files, read once each and kept until the file changes; and
 * the track a uri with "cue=<n>" names. A sheet is found in the file's folder: the one named as
 * the file is ("album.cue" for "album.flac") first, else any sheet whose FILE line names it.
 */
object CueSheets {

    private class Kept(val stamp: String, val sheet: CueSheet?)

    private val kept = ConcurrentHashMap<String, Kept>()

    /** How large a sheet may be: anything larger is not one. */
    private const val MAX_BYTES = 2 * 1024 * 1024

    /** The sheet at [uri], read or kept; null when it is not a sheet or cannot be read. */
    fun read(context: Context, uri: Uri): CueSheet? {
        val key = uri.toString()
        val stamp = stamp(context, uri)
        kept[key]?.let { if (it.stamp == stamp) return it.sheet }
        val sheet = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_BYTES) null else CueSheet.parse(CueSheet.decode(bytes))
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
        kept[key] = Kept(stamp, sheet)
        return sheet
    }

    /**
     * The sheet beside [file] that lists it, and that file's tracks in it; null when there is
     * none, or the sheet gives the file one track at most (the file is a track of its own then).
     */
    fun forFile(context: Context, file: Uri): Pair<FoundSheet, CueFile>? {
        val fileName = fileName(context, file) ?: return null
        return among(context, fileName, FolderFiles.siblings(context, file))
    }

    /**
     * Among a folder's [siblings] (names and uris), the sheet listing [fileName] with the file's
     * tracks; null when no sheet gives it two tracks or more. The sheet named as the file
     * ("album.cue" for "album.flac") lists it even when its FILE line says another name (the
     * sheet of a renamed rip); any other sheet must name the file.
     */
    fun among(context: Context, fileName: String, siblings: List<Pair<String, String>>): Pair<FoundSheet, CueFile>? {
        val sheets = siblings.filter { (name, _) -> name.substringAfterLast('.', "").equals("cue", ignoreCase = true) }
        if (sheets.isEmpty()) return null
        val stem = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
        fun sameStem(name: String) = name.substringBeforeLast('.').lowercase(Locale.ROOT) == stem
        // the sheet named as the file first
        val ordered = sheets.sortedBy { (name, _) -> if (sameStem(name)) 0 else 1 }
        for ((name, uri) in ordered) {
            val sheet = read(context, Uri.parse(uri)) ?: continue
            val cueFile = sheet.fileNamed(fileName, orOnly = sameStem(name)) ?: continue
            if (cueFile.tracks.size >= 2) return FoundSheet(uri, sheet) to cueFile
        }
        return null
    }

    /** The track [uri] names ("…#cue=3"), from the sheet beside its file; null when there is none. */
    fun track(context: Context, uri: Uri): CueTrack? {
        val number = CueUri.trackNumber(uri) ?: return null
        val found = forFile(context, CueUri.file(uri)) ?: return null
        return found.second.tracks.firstOrNull { it.number == number }
    }

    /** What is kept is forgotten: the sheets are read again when asked for. */
    fun clear() = kept.clear()

    private fun fileName(context: Context, file: Uri): String? = when (file.scheme) {
        null, ContentResolver.SCHEME_FILE -> file.lastPathSegment
        ContentResolver.SCHEME_CONTENT -> runCatching {
            context.contentResolver.query(file, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: file.lastPathSegment?.substringAfterLast('/')
        else -> null
    }

    /** When the sheet last changed and how large it is: what tells a kept copy is stale. */
    private fun stamp(context: Context, uri: Uri): String = when (uri.scheme) {
        null, ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.let { "${it.lastModified()}:${it.length()}" } ?: ""
        ContentResolver.SCHEME_CONTENT -> runCatching {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) "${c.getLong(0)}:${c.getLong(1)}" else ""
            }
        }.getOrNull() ?: ""
        else -> ""
    }
}
