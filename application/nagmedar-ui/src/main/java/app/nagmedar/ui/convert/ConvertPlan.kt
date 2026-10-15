package app.nagmedar.ui.convert

import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.music.Track
import java.util.Locale

/**
 * S-1010: one file to make: the source ([uri], a CUE sheet's track too), the folders under the
 * destination and the new file's name, and the tags put over the source's ([tags]: a CUE
 * track's own, which its file's tags do not hold).
 */
data class ConvertItem(
    val uri: String,
    val title: String,
    val folders: List<String>,
    val name: String,
    val tags: Map<String, String> = emptyMap(),
) {
    /** "Artist/Album/03 Title.opus": where it goes, under the destination. */
    val path: String get() = (folders + name).joinToString("/")

    /** A CUE sheet's track: a part of its file. */
    val isCuePart: Boolean get() = CueUri.isTrack(uri)
}

/**
 * S-1010: the names of the new files. In folders: "Album artist/Album/NN Title.ext" ("D-NN" on
 * an album of several discs); flat: "Artist - Title.ext". A name holds nothing a file system
 * refuses (/ \ : * ? " < > | and control characters become "_"), does not start with a dot nor
 * end with one or a space, and stays within [MAX_NAME_BYTES] bytes of UTF-8; two tracks that would
 * get the same name (letter case aside, as on a memory card) are told apart by " (2)", " (3)"….
 */
object ConvertPlanner {
    /** Under the 255 bytes most file systems allow, with room for " (99)" and the extension. */
    const val MAX_NAME_BYTES = 200

    /**
     * The files for [tracks] in their order. [unknownArtist] and [unknownAlbum] name the folders
     * of tracks without those tags; [extension] is the format's.
     */
    fun plan(tracks: List<Track>, naming: ConvertNaming, extension: String, unknownArtist: String, unknownAlbum: String): List<ConvertItem> {
        // an album spread over discs numbers its tracks with the disc
        val multiDisc = tracks.groupBy { albumKey(it) }.filterValues { list -> list.map { it.discNumber.coerceAtLeast(1) }.distinct().size > 1 }.keys
        val taken = HashSet<String>()
        return tracks.map { track ->
            val title = track.title.ifBlank { fileStem(track.uri) }
            val artist = track.filedArtist?.takeIf { it.isNotBlank() }
            val (folders, stem) = when (naming) {
                ConvertNaming.Folders -> {
                    val number = number(track, albumKey(track) in multiDisc)
                    listOf(clean(artist ?: unknownArtist), clean(track.album?.takeIf { it.isNotBlank() } ?: unknownAlbum)) to
                        listOfNotNull(number, title).joinToString(" ")
                }
                ConvertNaming.Flat -> {
                    val performer = track.artist?.takeIf { it.isNotBlank() } ?: artist
                    emptyList<String>() to listOfNotNull(performer, title).joinToString(" - ")
                }
            }
            val base = clean(stem)
            var name = "$base.$extension"
            var copy = 2
            while (!taken.add((folders + name).joinToString("/").lowercase(Locale.ROOT))) {
                name = "$base ($copy).$extension"
                copy++
            }
            ConvertItem(track.uri, title, folders, name, cueTags(track))
        }
    }

    /** "03", "2-07"; null for a track without a number. */
    internal fun number(track: Track, multiDisc: Boolean): String? {
        if (track.trackNumber <= 0) return null
        val number = "%02d".format(Locale.ROOT, track.trackNumber)
        return if (multiDisc && track.discNumber > 0) "${track.discNumber}-$number" else number
    }

    /** A name a file system takes; "_" for one with nothing left. */
    fun clean(text: String): String {
        val replaced = buildString {
            text.forEach { c -> append(if (c < ' ' || c == '\u007f' || c in FORBIDDEN) '_' else c) }
        }
        var name = replaced.trim().trimEnd('.', ' ').trimStart('.', ' ')
        name = cut(name, MAX_NAME_BYTES).trimEnd('.', ' ')
        return name.ifEmpty { "_" }
    }

    /** [text] within [maxBytes] bytes of UTF-8, not in the middle of a character. */
    internal fun cut(text: String, maxBytes: Int): String {
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        var bytes = 0
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > maxBytes) break
            out.appendCodePoint(codePoint)
            bytes += size
            i += Character.charCount(codePoint)
        }
        return out.toString()
    }

    /** A CUE track's tags, from the sheet as the library read it: its file's tags are the whole album's. */
    internal fun cueTags(track: Track): Map<String, String> {
        if (!CueUri.isTrack(track.uri)) return emptyMap()
        return buildMap {
            put("TITLE", track.title)
            track.artist?.takeIf { it.isNotBlank() }?.let { put("ARTIST", it) }
            track.albumArtist?.takeIf { it.isNotBlank() }?.let { put("ALBUMARTIST", it) }
            track.album?.takeIf { it.isNotBlank() }?.let { put("ALBUM", it) }
            track.genre?.takeIf { it.isNotBlank() }?.let { put("GENRE", it) }
            track.year?.let { put("DATE", it.toString()) }
            if (track.trackNumber > 0) put("TRACKNUMBER", track.trackNumber.toString())
        }
    }

    private fun albumKey(track: Track): String =
        (track.filedArtist.orEmpty() + "\u0000" + track.album.orEmpty()).lowercase(Locale.ROOT)

    /** The file's name without its extension, from a uri ("…/primary%3AM%C3%BCzik%2F03%20Gece.flac" → "03 Gece"). */
    internal fun fileStem(uri: String): String {
        val name = percentDecoded(CueUri.file(uri)).substringAfterLast('/')
        return name.substringBeforeLast('.').ifBlank { name }
    }

    /** %XX sequences as the UTF-8 bytes they are; anything else as it is. */
    private fun percentDecoded(text: String): String {
        if ('%' !in text) return text
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val hex = if (c == '%' && i + 2 <= text.lastIndex) text.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private const val FORBIDDEN = "/\\:*?\"<>|"
}
