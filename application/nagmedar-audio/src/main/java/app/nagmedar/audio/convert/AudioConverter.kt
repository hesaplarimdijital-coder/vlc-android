package app.nagmedar.audio.convert

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.nagmedar.audio.LoudnessScanner
import app.nagmedar.audio.NativeAudio
import app.nagmedar.audio.cue.CueUri
import org.json.JSONObject
import java.io.File

/** S-1010: what the converter writes: the codec and its file's extension. */
enum class ConvertCodec(val native: Int, val extension: String) {
    /** libopus in Ogg, always at 48 kHz. */
    Opus(NativeAudio.CONVERT_OPUS, "opus"),

    /** LAME, with the Xing/LAME frame that makes it gapless. */
    Mp3(NativeAudio.CONVERT_MP3, "mp3"),

    /** FFmpeg's AAC-LC encoder in an M4A. */
    Aac(NativeAudio.CONVERT_AAC, "m4a"),
}

/**
 * S-1010: how a file is encoded: [bitrate] in bits per second (Opus's target, AAC's, MP3's
 * constant rate; 0 for MP3's VBR at [vbrQuality], LAME's -V 0..9; for more than two channels the
 * rate of a stereo pair, which the engine grows with the channels), at [rate] Hz (0: the
 * source's where the codec has it; Opus is always 48 kHz). [stereo]: mixed down to stereo
 * instead of keeping the source's channels up to 7.1 (MP3 is always stereo at most).
 */
data class ConvertSettings(
    val codec: ConvertCodec,
    val bitrate: Int,
    val vbrQuality: Int = 0,
    val rate: Int = 0,
    val stereo: Boolean = false,
)

/** S-808: the part of a file a CUE sheet's track is, in CUE frames (1/75 s); [to] null for the file's end. */
data class ConvertPart(val from: Long, val to: Long?)

/** Why a conversion did not come to a file. */
enum class ConvertFailure {
    /** This build has no music engine. */
    NoEngine,

    /** The source could not be opened. */
    Input,

    /** The new file could not be opened for writing. */
    Output,

    /** The engine stopped: a damaged source, a full drive; [ConvertResult.Failed.detail] says. */
    Engine,
}

/** What a conversion came to. */
sealed interface ConvertResult {
    /** Written: [frames] at [rate] Hz, [bytes] long; [tags] tags and maybe a [cover] along; [gainDb] measured again, null when the source had none. */
    data class Done(
        val frames: Long,
        val rate: Int,
        val channels: Int,
        val bytes: Long,
        val tags: Int,
        val cover: Boolean,
        val gainDb: Double?,
    ) : ConvertResult

    data class Failed(val failure: ConvertFailure, val detail: String = "") : ConvertResult

    /** Stopped from the progress callback. */
    data object Cancelled : ConvertResult

    companion object {
        /** The engine's JSON (NativeAudio.convert); a missing or broken answer is the engine's failure. */
        fun parse(json: String?): ConvertResult {
            val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return Failed(ConvertFailure.Engine, "no answer")
            if (o.has("error")) {
                return if (o.optBoolean("cancelled")) Cancelled else Failed(ConvertFailure.Engine, o.optString("error"))
            }
            return Done(
                frames = o.optLong("frames"),
                rate = o.optInt("rate"),
                channels = o.optInt("channels"),
                bytes = o.optLong("bytes"),
                tags = o.optInt("tags"),
                cover = o.optBoolean("cover"),
                gainDb = if (o.isNull("gain_db")) null else o.optDouble("gain_db").takeIf { !it.isNaN() },
            )
        }
    }
}

/**
 * S-1010: the music engine's converter. A file (or a CUE sheet's part of it) is decoded and
 * encoded anew into a document of the caller's, its tags and front cover copied over with
 * [tags] on top. Slow: a worker thread's; [progress] (0..1) answers false to stop.
 */
object AudioConverter {
    private const val TAG = "NagmedarAudio"

    val available: Boolean get() = NativeAudio.available

    /**
     * Converts [input] (a CUE track's uri reads its file; [part] bounds it) into [output], a new
     * and empty document (the engine refuses one that holds anything, or is the source), named
     * [outputName] (its extension tells the tag writer the type).
     */
    fun convert(
        context: Context,
        input: Uri,
        part: ConvertPart?,
        output: Uri,
        outputName: String,
        settings: ConvertSettings,
        tags: Map<String, String>,
        progress: (Double) -> Boolean,
    ): ConvertResult {
        if (!NativeAudio.available) return ConvertResult.Failed(ConvertFailure.NoEngine)
        val app = context.applicationContext
        val source = CueUri.file(input)
        val inputName = LoudnessScanner.displayName(app, source)
        val inDescriptor = open(app, source, "r") ?: return ConvertResult.Failed(ConvertFailure.Input)
        return inDescriptor.use { inFd ->
            val outDescriptor = open(app, output, "rw") ?: return@use ConvertResult.Failed(ConvertFailure.Output)
            outDescriptor.use { outFd ->
                ConvertResult.parse(
                    NativeAudio.convert(
                        inFd.fd, inputName, outFd.fd, outputName,
                        settings.codec.native, settings.bitrate, settings.vbrQuality, settings.rate,
                        if (settings.stereo) NativeAudio.CONVERT_STEREO else NativeAudio.CONVERT_SOURCE_CHANNELS,
                        part?.from ?: 0L, part?.to ?: -1L,
                        tagLines(tags), NativeAudio.ConvertListener { fraction -> progress(fraction) },
                    ),
                )
            }
        }
    }

    /** "KEY=value" lines, UTF-8; a value's line breaks become spaces, an empty value removes the tag. */
    fun tagLines(tags: Map<String, String>): ByteArray? {
        if (tags.isEmpty()) return null
        return tags.entries
            .filter { (key, _) -> key.isNotBlank() && '=' !in key && '\n' !in key }
            .joinToString("") { (key, value) -> "$key=${value.replace('\r', ' ').replace('\n', ' ')}\n" }
            .toByteArray(Charsets.UTF_8)
    }

    private fun open(context: Context, uri: Uri, mode: String): ParcelFileDescriptor? = runCatching {
        when (uri.scheme) {
            null, "file" -> uri.path?.let {
                ParcelFileDescriptor.open(File(it), if (mode == "r") ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_READ_WRITE)
            }
            "content" -> context.contentResolver.openFileDescriptor(uri, mode)
            else -> null
        }
    }.onFailure { Log.w(TAG, "cannot open $uri ($mode): $it") }.getOrNull()
}
