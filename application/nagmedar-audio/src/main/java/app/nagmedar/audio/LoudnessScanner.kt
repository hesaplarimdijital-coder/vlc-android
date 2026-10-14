package app.nagmedar.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import app.nagmedar.audio.cue.CueUri
import java.io.Closeable
import java.io.File

/** S-907: a file's loudness as the EBU R128 scanner measured it, and its ReplayGain 2.0 gain. */
data class Loudness(
    /** Integrated, LUFS; -inf for silence. */
    val lufs: Double,
    /** Four times oversampled, 1.0 = full scale. */
    val truePeak: Double,
    val samplePeak: Double,
    /** ReplayGain 2.0: -18 LUFS minus the loudness. */
    val gainDb: Double,
    val frames: Long,
    val rate: Int,
) {
    val silent: Boolean get() = lufs == Double.NEGATIVE_INFINITY

    companion object {
        internal fun of(values: DoubleArray?): Loudness? =
            values?.takeIf { it.size >= 6 }?.let { Loudness(it[0], it[1], it[2], it[3], it[4].toLong(), it[5].toInt()) }
    }
}

/**
 * S-907: the EBU R128 scanner for one album (or one file): every file scanned [forAlbum]
 * counts for the album's gain. Slow: call it off the main thread; [cancel] from any thread
 * makes the scan under way return null. The tags go back into the files with [writeTags].
 */
class LoudnessScanner(context: Context) : Closeable {

    private val context = context.applicationContext
    private var handle = if (NativeAudio.available) NativeAudio.loudnessNew() else 0L

    val available: Boolean get() = handle != 0L

    /** Scans [uri] whole; null when it cannot be read, or the scan was cancelled. */
    fun scan(uri: Uri, forAlbum: Boolean = false): Loudness? {
        if (!available) return null
        return withReadPath(context, uri) { path -> Loudness.of(NativeAudio.loudnessScan(handle, path, forAlbum)) }
    }

    /** The album over everything scanned for it; null when nothing was. */
    fun album(): Loudness? = if (available) Loudness.of(NativeAudio.loudnessAlbum(handle)) else null

    fun cancel() {
        if (available) NativeAudio.loudnessCancel(handle)
    }

    override fun close() {
        if (handle != 0L) {
            NativeAudio.loudnessFree(handle)
            handle = 0L
        }
    }

    companion object {
        private const val TAG = "NagmedarAudio"

        /** The file as the engine reads it, with its ReplayGain tags and composer; null when it cannot be opened. */
        fun probe(context: Context, uri: Uri): SourceInfo? =
            if (!NativeAudio.available) null else withReadPath(context.applicationContext, uri) { path -> SourceInfo.parse(NativeAudio.probe(path)) }

        /**
         * S-1001: the waveform of [uri] in [points] buckets, decoding the whole file (seconds for a
         * long one: off the main thread); null when the engine is missing or the file does not decode.
         */
        fun waveform(context: Context, uri: Uri, points: Int): Waveform? =
            if (!NativeAudio.available) null else withReadPath(context.applicationContext, uri) { path -> NativeAudio.waveform(path, points)?.let(::Waveform) }

        /**
         * Writes [track] (and [album], when given) as ReplayGain tags into [uri], which must be
         * writable: a file of the app's reach, or a document opened read-write. False when the
         * file could not be opened for writing or its format takes no tags.
         */
        fun writeTags(context: Context, uri: Uri, track: Loudness, album: Loudness?): Boolean {
            if (!NativeAudio.available) return false
            val descriptor = openReadWrite(context.applicationContext, uri) ?: return false
            return descriptor.use {
                NativeAudio.writeReplayGain(
                    it.fd, displayName(context, uri),
                    track.gainDb, track.truePeak,
                    album?.gainDb ?: Double.NaN, album?.truePeak ?: Double.NaN,
                    track.lufs, album?.lufs ?: Double.NaN,
                ) == 0
            }
        }

        /** The file's name with its extension, for the tag writer's type detection; a CUE track's (S-808) is its file's. */
        fun displayName(context: Context, track: Uri): String {
            val uri = CueUri.file(track)
            if (uri.scheme == "content") {
                runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0)?.let { return it }
                    }
                }
            }
            return uri.lastPathSegment?.substringAfterLast('/') ?: ""
        }

        private fun openReadWrite(context: Context, uri: Uri): ParcelFileDescriptor? = runCatching {
            when (uri.scheme) {
                null, "file" -> ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_WRITE)
                "content" -> context.contentResolver.openFileDescriptor(uri, "rw")
                else -> null
            }
        }.onFailure { Log.w(TAG, "cannot write $uri: $it") }.getOrNull()

        private inline fun <T> withReadPath(context: Context, track: Uri, block: (String) -> T?): T? {
            val uri = CueUri.file(track)
            return when (uri.scheme) {
                null, "file" -> uri.path?.let(block)
                "content" -> {
                    val descriptor = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
                    if (descriptor == null) {
                        Log.w(TAG, "cannot open $uri")
                        null
                    } else {
                        descriptor.use { block("/proc/self/fd/${it.fd}") }
                    }
                }
                else -> null
            }
        }
    }
}
