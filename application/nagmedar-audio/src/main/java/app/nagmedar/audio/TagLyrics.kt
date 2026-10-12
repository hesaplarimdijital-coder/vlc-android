package app.nagmedar.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * S-1003: the lyrics a file carries in its tags: [plain] from USLT, LYRICS, UNSYNCEDLYRICS or
 * ©lyr (whose text may itself be LRC), [synced] from ID3v2's SYLT frame, in milliseconds.
 */
data class TagLyrics(val plain: String?, val synced: List<Line>) {

    /** A SYLT line: its text from [timeMs] on. */
    data class Line(val timeMs: Long, val text: String)

    companion object {
        private const val TAG = "NagmedarAudio"

        /** The lyrics in [uri]'s tags; null when it has none, cannot be read, or the engine is missing. Reads the file: off the main thread. */
        fun read(context: Context, uri: Uri): TagLyrics? {
            if (!NativeAudio.available) return null
            val app = context.applicationContext
            val descriptor = runCatching {
                when (uri.scheme) {
                    null, "file" -> uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
                    "content" -> app.contentResolver.openFileDescriptor(uri, "r")
                    else -> null
                }
            }.onFailure { Log.w(TAG, "cannot read the lyrics of $uri: $it") }.getOrNull() ?: return null
            return descriptor.use { parse(NativeAudio.readLyrics(it.fd, LoudnessScanner.displayName(app, uri))) }
        }

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
