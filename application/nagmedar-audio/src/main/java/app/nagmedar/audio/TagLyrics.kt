package app.nagmedar.audio

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * S-1003: the lyrics a file carries in its tags: [plain] from USLT, LYRICS, UNSYNCEDLYRICS or
 * ©lyr (whose text may itself be LRC), [synced] from ID3v2's SYLT frame, in milliseconds.
 */
data class TagLyrics(val plain: String?, val synced: List<Line>) {

    /** A SYLT line: its text from [timeMs] on. */
    data class Line(val timeMs: Long, val text: String)

    companion object {
        /** The lyrics in [uri]'s tags; null when it has none, cannot be read, or the engine is missing. Reads the file: off the main thread. */
        fun read(context: Context, uri: Uri): TagLyrics? = FileTags.read(context, uri) { fd, name -> parse(NativeAudio.readLyrics(fd, name)) }

        /**
         * S-1005: writes lyrics into [uri]'s tags: [text] as the plain lyrics (USLT, LYRICS, ©lyr; LRC text when timed),
         * [synced] as ID3v2's SYLT, JSON `[[ms, "text"], ...]` (MP3, AIFF, WAV only). Null or empty removes what the file
         * had of the kind. False when the file cannot be opened for writing or its format takes no tags. Writes the
         * file: off the main thread.
         */
        fun write(context: Context, uri: Uri, text: String?, synced: String?): Boolean =
            FileTags.write(context, uri) { fd, name -> NativeAudio.writeLyrics(fd, name, text, synced) == 0 } ?: false

        /** The engine's JSON read; null for none (null, "{}", blank text and no lines). */
        fun parse(json: String?): TagLyrics? {
            if (json.isNullOrBlank()) return null
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val plain = if (o.isNull("plain")) null else o.optString("plain").takeIf { it.isNotBlank() }
            val synced = o.optJSONArray("synced")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val pair = array.optJSONArray(i) ?: return@mapNotNull null
                    val time = pair.optLong(0, -1).takeIf { it >= 0 } ?: return@mapNotNull null
                    Line(time, pair.optString(1))
                }
            }.orEmpty()
            return if (plain == null && synced.isEmpty()) null else TagLyrics(plain, synced)
        }
    }
}
